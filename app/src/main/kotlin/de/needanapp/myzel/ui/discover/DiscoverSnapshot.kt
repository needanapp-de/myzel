package de.needanapp.myzel.ui.discover

import android.content.Context
import android.content.pm.PackageInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.core.os.LocaleListCompat
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import org.fdroid.database.AppOverviewItem
import org.fdroid.database.Repository
import org.fdroid.download.DownloadRequest
import org.fdroid.download.PackageName
import org.fdroid.download.getImageModel
import org.fdroid.index.RepoManager
import org.fdroid.index.v2.FileV2
import org.fdroid.install.InstalledAppsCache
import org.fdroid.settings.SettingsManager
import org.fdroid.ui.discover.AppDiscoverItem
import org.fdroid.utils.IoDispatcher

/** So viele Apps je Bereich hebt Myzel auf; mehr sieht man vor dem Laden nicht. */
private const val MAX_APPS = 60

/** Eine App, wie sie beim letzten Mal auf Entdecken stand. */
@Serializable
internal data class SnapshotApp(
  val packageName: String,
  val name: String,
  val lastUpdated: Long,
  val repoId: Long,
  val icon: FileV2? = null,
)

@Serializable
internal data class SnapshotLists(
  val newApps: List<SnapshotApp> = emptyList(),
  val recentlyUpdatedApps: List<SnapshotApp> = emptyList(),
)

/** Die Bereiche vom letzten Mal, fertig zum Anzeigen. */
class SnapshotItems(
  val newApps: List<AppDiscoverItem>,
  val recentlyUpdatedApps: List<AppDiscoverItem>,
)

/**
 * Neu und Kürzlich aktualisiert vom letzten Start. Die Datenbank braucht für diese beiden Listen
 * nach dem Start gut eine halbe Sekunde länger als für die Kategorien (Pixel 6); bis dahin zeigt
 * Entdecken die Apps vom letzten Mal und tauscht sie dann still gegen die aktuellen. Gespeichert
 * werden nur Name, Paketquelle und Symbol, in `files/myzel/discover.json`.
 */
@OptIn(FlowPreview::class)
@Singleton
class DiscoverSnapshot
@Inject
constructor(
  @ApplicationContext context: Context,
  @IoDispatcher private val scope: CoroutineScope,
  private val settingsManager: SettingsManager,
  repoManager: RepoManager,
  installedAppsCache: InstalledAppsCache,
) {
  private val log = KotlinLogging.logger {}
  private val file = File(context.filesDir, "myzel/discover.json")
  private val json = Json { ignoreUnknownKeys = true }

  /** Was beim Start in der Datei stand; ändert sich danach nicht mehr. */
  private val saved = MutableStateFlow<SnapshotLists?>(null)

  /** Was die Datenbank zuletzt geliefert hat, zum Speichern. */
  private val currentNew = MutableStateFlow<List<SnapshotApp>?>(null)
  private val currentRecentlyUpdated = MutableStateFlow<List<SnapshotApp>?>(null)

  /** Die gespeicherten Bereiche als Apps für Entdecken; null, solange nichts da ist. */
  val items: StateFlow<SnapshotItems?> =
    combine(
        saved.filterNotNull(),
        installedAppsCache.installedApps,
        repoManager.repositoriesState.filterNotNull(),
      ) { lists, installed, repos ->
        SnapshotItems(
          lists.newApps.toItems(installed, repos),
          lists.recentlyUpdatedApps.toItems(installed, repos),
        )
      }
      .stateIn(scope, SharingStarted.Eagerly, null)

  init {
    scope.launch {
      saved.value =
        try {
          if (file.exists()) json.decodeFromString<SnapshotLists>(file.readText()) else null
        } catch (e: Exception) {
          log.warn(e) { "Could not read $file" }
          null
        }
    }
    // Erst speichern, wenn beide Listen da sind; während einer Aktualisierung der Paketquellen
    // liefern sie mehrmals hintereinander
    scope.launch {
      combine(currentNew.filterNotNull(), currentRecentlyUpdated.filterNotNull(), ::SnapshotLists)
        .distinctUntilChanged()
        .debounce(2_000)
        .filter { it != saved.value }
        .collect { lists ->
          try {
            file.parentFile?.mkdirs()
            val tmp = File(file.path + ".tmp")
            tmp.writeText(json.encodeToString(SnapshotLists.serializer(), lists))
            tmp.renameTo(file)
          } catch (e: Exception) {
            log.warn(e) { "Could not write $file" }
          }
        }
    }
  }

  /** Merkt sich die neuen Apps aus der Datenbank. */
  fun rememberNewApps(apps: List<AppOverviewItem>) {
    currentNew.value = apps.toSnapshot()
  }

  /** Merkt sich die kürzlich aktualisierten Apps aus der Datenbank. */
  fun rememberRecentlyUpdatedApps(apps: List<AppOverviewItem>) {
    currentRecentlyUpdated.value = apps.toSnapshot()
  }

  private fun List<AppOverviewItem>.toSnapshot(): List<SnapshotApp> {
    val localeList = LocaleListCompat.getDefault()
    return take(MAX_APPS).map {
      SnapshotApp(
        packageName = it.packageName,
        name = it.getName(localeList) ?: it.packageName,
        lastUpdated = it.lastUpdated,
        repoId = it.repoId,
        icon = it.getIcon(localeList),
      )
    }
  }

  /** Wie im DiscoverPresenter von F-Droid. */
  private fun List<SnapshotApp>.toItems(
    installed: Map<String, PackageInfo>,
    repos: List<Repository>,
  ): List<AppDiscoverItem> {
    val proxyConfig = settingsManager.proxyConfig
    return mapNotNull { app ->
      val repository =
        repos.firstOrNull { it.repoId == app.repoId && it.enabled } ?: return@mapNotNull null
      val isInstalled = installed.contains(app.packageName)
      val imageModel = app.icon?.getImageModel(repository, proxyConfig)
      AppDiscoverItem(
        packageName = app.packageName,
        name = app.name,
        lastUpdated = app.lastUpdated,
        isInstalled = isInstalled,
        imageModel =
          if (isInstalled) PackageName(app.packageName, imageModel as? DownloadRequest)
          else imageModel,
      )
    }
  }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DiscoverSnapshotEntryPoint {
  fun discoverSnapshot(): DiscoverSnapshot
}

/** Die Bereiche vom letzten Start (siehe [DiscoverSnapshot]); null, solange nichts da ist. */
@Composable
internal fun rememberSnapshotItems(): SnapshotItems? {
  if (LocalInspectionMode.current) return null
  val context = LocalContext.current
  val snapshot = remember {
    EntryPointAccessors.fromApplication(context, DiscoverSnapshotEntryPoint::class.java)
      .discoverSnapshot()
  }
  return snapshot.items.collectAsState().value
}
