package de.needanapp.myzel.recent

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import de.needanapp.myzel.ui.apps.NewApps
import de.needanapp.myzel.ui.apps.RECENT_DAYS
import de.needanapp.myzel.watch.WatchCatalog
import java.util.concurrent.TimeUnit.DAYS
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import org.fdroid.database.AppOverviewItem
import org.fdroid.database.FDroidDatabase
import org.fdroid.utils.IoDispatcher

/**
 * Höchstens so viele aktualisierte Apps holt [RecentAppsSource]; F-Droid nimmt für „Kürzlich
 * aktualisiert“ nur 200, das reicht bei f-droid.org kaum für zwei Tage.
 */
private const val MAX_UPDATED = 2000

/**
 * Die neuen und kürzlich aktualisierten Apps der letzten [RECENT_DAYS] Tage, ohne Apps nur für die
 * Uhr: für die Seite „Neu und aktualisiert“ und die Zahl daneben auf Entdecken, damit beide gleich
 * zählen.
 */
@Singleton
class RecentAppsSource
@Inject
constructor(
  db: FDroidDatabase,
  newApps: NewApps,
  watchCatalog: WatchCatalog,
  @IoDispatcher scope: CoroutineScope,
) {
  val newApps: Flow<List<AppOverviewItem>> = watchCatalog.withoutWatchOnly(newApps.apps)

  val updatedApps: SharedFlow<List<AppOverviewItem>> =
    watchCatalog
      .withoutWatchOnly(db.getAppDao().getRecentlyUpdatedAppsFlow(limit = MAX_UPDATED))
      .map { apps ->
        val since = System.currentTimeMillis() - DAYS.toMillis(RECENT_DAYS.toLong())
        apps.filter { it.lastUpdated >= since }
      }
      .shareIn(scope, SharingStarted.WhileSubscribed(5_000), 1)

  /** Wie viele Apps die Seite ohne Filter zeigt, jede App einmal; null, solange es lädt. */
  val count: StateFlow<Int?> =
    combine(this.newApps, updatedApps) { new, updated ->
        (new.mapTo(HashSet()) { it.packageName } + updated.map { it.packageName }).size
      }
      .stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RecentAppsEntryPoint {
  fun recentAppsSource(): RecentAppsSource
}

/** [RecentAppsSource.count] für Entdecken; null, solange es lädt. */
@Composable
fun rememberRecentAppsCount(): Int? {
  if (LocalInspectionMode.current) return null
  val context = LocalContext.current
  val source = remember {
    EntryPointAccessors.fromApplication(context, RecentAppsEntryPoint::class.java)
      .recentAppsSource()
  }
  val count by source.count.collectAsState()
  return count
}
