package de.needanapp.myzel.watch

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.fdroid.database.AppOverviewItem
import org.fdroid.database.FDroidDatabase
import org.fdroid.database.WATCH_FEATURE
import org.fdroid.database.WatchAppVersion
import org.fdroid.index.v2.PackageVersion
import org.fdroid.utils.IoDispatcher

/**
 * Kennung der Rubrik „Uhr“ in [org.fdroid.ui.lists.AppListType.Category]. Keine Paketquelle vergibt
 * sie; Myzel bildet die Rubrik selbst aus dem [WatchCatalog].
 */
const val WATCH_CATEGORY_ID = "myzel.watch"

/**
 * Eine App mit Uhr-Version.
 *
 * @param watchOnly nur für die Uhr, ohne Version fürs Handy (etwa Telewatch)
 * @param watchVersions die Uhr-Versionen, höchste zuerst; bei einer Handy-App nur die mit derselben
 *   Signatur wie eine Handy-Version
 * @param own Myzel selbst; seine Uhr-Version ist Myzel Wear (siehe [WatchCatalog.shown])
 */
data class WatchApp(
  val packageName: String,
  val repoId: Long,
  val watchOnly: Boolean,
  val watchVersions: List<WatchAppVersion>,
  val own: Boolean = false,
)

/**
 * Alle Apps mit Uhr-Version aus den eingeschalteten Paketquellen (Umbauplan Phase 3). Eine Version
 * ist für die Uhr, wenn sie das Merkmal `android.hardware.type.watch` verlangt; F-Droid übernimmt
 * es aus dem Manifest in den Index.
 * - Reine Uhr-Apps haben nur solche Versionen. Myzel zeigt sie in der Rubrik „Uhr“ und in der
 *   Suche, sonst in keiner Liste.
 * - Handy-Apps mit Uhr-Version (gleicher Paketname, Fall A im Umbauplan) zählen nur, wenn eine
 *   Uhr-Version dieselbe Signatur hat wie eine Handy-Version; nur dann gehören beide zusammen. Bei
 *   Catima gilt das für die vom Entwickler signierte Handy-Version, nicht für die von F-Droid.
 *
 * Myzel selbst fehlt in [apps]: Seine Uhr-Version ist Myzel Wear, die die Einrichtung und danach
 * Myzel von selbst auf die Uhr bringt ([MyzelWearSource]). Gezeigt wird es trotzdem ([shown]).
 */
@Singleton
class WatchCatalog
@Inject
constructor(
  @ApplicationContext context: Context,
  db: FDroidDatabase,
  @IoDispatcher scope: CoroutineScope,
) {
  // die Testvariante .profiling (app/myzel.gradle) nimmt den Release als sich selbst, damit sie
  // zeigt, was er zeigt; eine eigene Myzel Wear gibt es für sie nicht
  private val ownPackage = context.packageName.removeSuffix(".profiling")

  /**
   * Die Apps nach Paketname, auch Myzel selbst ([WatchApp.own]): Es steht in der Rubrik „Uhr“, hat
   * die Pille „Uhr“ und auf seiner App-Seite eine eigene Karte. Null, bis die Datenbank geantwortet
   * hat.
   */
  val shown: StateFlow<Map<String, WatchApp>?> =
    db
      .getVersionDao()
      .getWatchAppVersionsFlow()
      .map(::pair)
      .stateIn(scope, SharingStarted.Eagerly, null)

  /** Wie [shown], ohne Myzel selbst: die Apps, die Myzel auf die Uhr bringt. */
  val apps: StateFlow<Map<String, WatchApp>?> =
    shown.map { it?.minus(ownPackage) }.stateIn(scope, SharingStarted.Eagerly, null)

  /** Die Apps aus [apps], oder mit [withOwn] die aus [shown], sobald sie da sind. */
  suspend fun current(withOwn: Boolean = false): Map<String, WatchApp> =
    (if (withOwn) shown else apps).filterNotNull().first()

  /**
   * Lässt reine Uhr-Apps aus [list] weg; sie erscheinen nur in der Rubrik „Uhr“ und in der Suche.
   * Wartet auf den Katalog, damit sie nicht kurz auftauchen.
   */
  fun withoutWatchOnly(list: Flow<List<AppOverviewItem>>): Flow<List<AppOverviewItem>> =
    combine(list, apps.filterNotNull()) { items, watchApps -> items.withoutWatchOnly(watchApps) }

  private fun pair(versions: List<WatchAppVersion>): Map<String, WatchApp> =
    versions
      .groupBy { it.packageName }
      .mapNotNull { (packageName, versions) ->
        val (watch, phone) = versions.partition { it.isWatch }
        val phoneSigners = phone.mapTo(HashSet()) { it.signer.orEmpty().toSet() }
        val paired =
          if (phone.isEmpty()) watch
          else watch.filter { it.signer.orEmpty().toSet() in phoneSigners }
        if (paired.isEmpty()) return@mapNotNull null
        packageName to
          WatchApp(
            packageName = packageName,
            repoId = versions.first().repoId,
            watchOnly = phone.isEmpty(),
            watchVersions = paired.sortedByDescending { it.versionCode },
            own = packageName == ownPackage,
          )
      }
      .toMap()
}

/** Ob diese Version für Uhren ist (Merkmal `android.hardware.type.watch`). */
val PackageVersion.isForWatch: Boolean
  get() = packageManifest.featureNames?.contains(WATCH_FEATURE) == true

/** Ob die Version auf einer Uhr mit API-Stufe [sdk] läuft; 0 heißt unbekannt. */
fun PackageVersion.fitsWatch(sdk: Int): Boolean =
  sdk <= 0 || (packageManifest.minSdkVersion ?: 1) <= sdk

/** [items] ohne die reinen Uhr-Apps aus [watchApps]. */
fun List<AppOverviewItem>.withoutWatchOnly(
  watchApps: Map<String, WatchApp>
): List<AppOverviewItem> =
  if (watchApps.values.none { it.watchOnly }) this
  else filterNot { watchApps[it.packageName]?.watchOnly == true }

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WatchCatalogEntryPoint {
  fun watchCatalog(): WatchCatalog
}

/** Die Uhr-Version zu [packageName] (siehe [WatchCatalog.shown]), oder null. */
@Composable
fun rememberWatchApp(packageName: String?): WatchApp? {
  if (packageName == null || LocalInspectionMode.current) return null
  val context = LocalContext.current
  val catalog = remember {
    EntryPointAccessors.fromApplication(context, WatchCatalogEntryPoint::class.java).watchCatalog()
  }
  return catalog.shown.collectAsState().value?.get(packageName)
}

/** Ob es Apps mit Uhr-Version gibt; dann zeigt Entdecken die Rubrik „Uhr“. */
@Composable
fun rememberHasWatchApps(): Boolean {
  if (LocalInspectionMode.current) return false
  val context = LocalContext.current
  val catalog = remember {
    EntryPointAccessors.fromApplication(context, WatchCatalogEntryPoint::class.java).watchCatalog()
  }
  return !catalog.shown.collectAsState().value.isNullOrEmpty()
}
