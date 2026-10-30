package de.needanapp.myzel.watch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.asFlow
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.withContext
import org.fdroid.database.AppVersion
import org.fdroid.database.FDroidDatabase
import org.fdroid.download.DownloadRequest
import org.fdroid.download.PackageName
import org.fdroid.download.getImageModel
import org.fdroid.index.RepoManager
import org.fdroid.settings.SettingsManager
import org.fdroid.utils.IoDispatcher

/**
 * Eine App aus den Paketquellen, die auf der Uhr installiert ist.
 *
 * @param update die neueste zur Uhr passende Uhr-Version, wenn sie neuer ist als [installed] und
 *   gleich signiert
 * @param otherSigner die App auf der Uhr ist anders signiert als die Uhr-Versionen im Katalog
 */
data class WatchAppItem(
  val packageName: String,
  val name: String,
  val iconModel: Any?,
  val installed: WatchPackage,
  val update: AppVersion?,
  val otherSigner: Boolean,
)

/**
 * Die Apps auf der Uhr für „Meine Apps“: alle aus dem zuletzt bekannten Stand
 * ([WatchInstaller.apps]), die es im [WatchCatalog] gibt, mit Name und Symbol aus dem Index und dem
 * Update, falls eins da ist.
 */
@Singleton
class WatchAppList
@Inject
constructor(
  private val db: FDroidDatabase,
  private val repoManager: RepoManager,
  private val settingsManager: SettingsManager,
  catalog: WatchCatalog,
  installer: WatchInstaller,
  @IoDispatcher scope: CoroutineScope,
) {
  private val localeList = LocaleListCompat.getDefault()

  /** Nach Namen sortiert; null ohne eingerichtete Uhr oder bis der Katalog da ist. */
  @OptIn(ExperimentalCoroutinesApi::class)
  val items: StateFlow<List<WatchAppItem>?> =
    combine(installer.watch, catalog.apps.filterNotNull(), installer.apps) { watch, apps, onWatch ->
        Triple(watch, apps, onWatch)
      }
      .mapLatest { (watch, apps, onWatch) -> watch?.let { load(it, apps, onWatch) } }
      .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)

  private suspend fun load(
    watch: PairedWatch,
    catalog: Map<String, WatchApp>,
    onWatch: Map<String, WatchPackage>,
  ): List<WatchAppItem> =
    onWatch.values
      .filter { it.installed }
      .mapNotNull { installed ->
        val watchApp = catalog[installed.packageName] ?: return@mapNotNull null
        val packageName = installed.packageName
        val app = db.getAppDao().getApp(watchApp.repoId, packageName)
        val backupIcon =
          app
            ?.getIcon(localeList)
            ?.getImageModel(repoManager.getRepository(watchApp.repoId), settingsManager.proxyConfig)
            as? DownloadRequest
        val watchCodes = watchApp.watchVersions.mapTo(HashSet()) { it.versionCode }
        val catalogSigners = watchApp.watchVersions.flatMapTo(HashSet()) { it.signer.orEmpty() }
        val otherSigner =
          installed.signers.isNotEmpty() &&
            catalogSigners.isNotEmpty() &&
            installed.signers.none { it in catalogSigners }
        val update =
          db
            .getVersionDao()
            .getAppVersions(watchApp.repoId, packageName)
            .asFlow()
            .first()
            .filter { it.isForWatch && it.versionCode in watchCodes && it.fitsWatch(watch.sdk) }
            .maxByOrNull { it.versionCode }
            ?.takeIf { it.versionCode > installed.versionCode && it.signedLike(installed) }
        WatchAppItem(
          packageName = packageName,
          name = app?.name ?: packageName,
          iconModel = PackageName(packageName, backupIcon),
          installed = installed,
          update = update,
          otherSigner = otherSigner,
        )
      }
      .sortedBy { it.name.lowercase() }

  /**
   * Die Namen der Paketquellen, die eine Uhr-Version von [packageName] mit einer von [signers]
   * signiert anbieten; leer, wenn keine.
   */
  suspend fun reposSignedLike(packageName: String, signers: List<String>): List<String> =
    withContext(Dispatchers.IO) {
      db
        .getVersionDao()
        .getAppVersions(packageName)
        .asFlow()
        .first()
        .filter { version ->
          version.isForWatch && version.signer?.sha256.orEmpty().any { it in signers }
        }
        .map { it.repoId }
        .distinct()
        .mapNotNull { repoManager.getRepository(it)?.getName(localeList) }
    }

  /** Ob [installed] dieses Update annimmt; ohne Angaben auf einer Seite gilt es als gleich. */
  private fun AppVersion.signedLike(installed: WatchPackage): Boolean {
    val expected = signer?.sha256.orEmpty()
    return expected.isEmpty() ||
      installed.signers.isEmpty() ||
      installed.signers.any { it in expected }
  }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WatchAppListEntryPoint {
  fun watchAppList(): WatchAppList

  fun watchInstaller(): WatchInstaller

  fun watchStore(): WatchStore
}

/** Was „Meine Apps“ für den Abschnitt „Auf der Uhr“ braucht. */
class WatchAppsHandle(
  val watch: PairedWatch,
  /** leer, solange nichts bekannt ist */
  val items: List<WatchAppItem>,
  /** wann Myzel zuletzt alle Uhr-Apps abgefragt hat; 0: noch nie */
  val checkedAt: Long,
  val checking: Boolean,
  val reach: WatchReach,
  /** siehe [WatchBridge.wearUpdate] */
  val wearUpdate: WearUpdateState?,
  /** laufende Aufträge und Fehler je Paketname */
  val states: Map<String, WatchInstallState>,
  val check: () -> Unit,
  val updateAll: () -> Unit,
)

/**
 * Die Paketquellen, die [app] so signiert anbieten wie auf der Uhr (siehe
 * [WatchAppList.reposSignedLike]); null, solange Myzel sucht, und in der Vorschau.
 */
@Composable
fun rememberReposSignedLike(app: WatchPackage): List<String>? {
  if (LocalInspectionMode.current) return null
  val context = LocalContext.current
  val list = remember {
    EntryPointAccessors.fromApplication(context, WatchAppListEntryPoint::class.java).watchAppList()
  }
  return produceState<List<String>?>(null, app.packageName, app.signers) {
      value = list.reposSignedLike(app.packageName, app.signers)
    }
    .value
}

/** Die Apps auf der Uhr; null ohne eingerichtete Uhr und in der Vorschau. */
@Composable
fun rememberWatchApps(): WatchAppsHandle? {
  if (LocalInspectionMode.current) return null
  val context = LocalContext.current
  val entryPoint = remember {
    EntryPointAccessors.fromApplication(context, WatchAppListEntryPoint::class.java)
  }
  val list = remember { entryPoint.watchAppList() }
  val installer = remember { entryPoint.watchInstaller() }
  val watch by installer.watch.collectAsState()
  val items by list.items.collectAsState()
  val checkedAt by installer.appsCheckedAt.collectAsState()
  val checking by installer.checkingAll.collectAsState()
  val reach by installer.reach.collectAsState()
  val wearUpdate by installer.wearUpdate.collectAsState()
  val states by installer.states.collectAsState()
  val paired = watch ?: return null
  val apps = items.orEmpty()
  return WatchAppsHandle(
    watch = paired,
    items = apps,
    checkedAt = checkedAt,
    checking = checking,
    reach = reach,
    wearUpdate = wearUpdate,
    states = states,
    check = installer::checkAll,
    updateAll = { installer.installAll(apps.mapNotNull { it.update }) },
  )
}
