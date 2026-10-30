package de.needanapp.myzel.recent

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.needanapp.myzel.ui.apps.PanelItems
import de.needanapp.myzel.ui.apps.RECENT_DAYS
import de.needanapp.myzel.ui.nav.displayName
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import org.fdroid.index.RepoManager
import org.fdroid.install.InstalledAppsCache
import org.fdroid.ui.apps.MyAppItem
import org.fdroid.ui.lists.AppListItem
import org.fdroid.updates.UpdatesManager

/**
 * Die neuen und kürzlich aktualisierten Apps der letzten [RECENT_DAYS] Tage, neueste zuerst.
 *
 * @property items je App und Paketquelle ein Eintrag: Eine App aus mehreren Quellen steht mehrmals
 *   da, die Seite zeigt sie einmal, außer beim Filter auf eine Quelle
 * @property repos die Paketquellen, aus denen etwas kommt, in der Reihenfolge der Paketquellen
 */
data class RecentAppsModel(val items: List<RecentItem>, val repos: List<RecentRepo>)

/** Eine App: [app] für die Karte, [isNew] für den Filter, [installed] für das Bedienfeld. */
data class RecentItem(val app: AppListItem, val isNew: Boolean, val installed: MyAppItem?)

/** Eine Paketquelle für den Filter, mit dem Namen der Anzeigen. */
data class RecentRepo(val repoId: Long, val name: String)

@HiltViewModel
class RecentAppsViewModel
@Inject
constructor(
  repoManager: RepoManager,
  installedAppsCache: InstalledAppsCache,
  updatesManager: UpdatesManager,
  source: RecentAppsSource,
  private val panelItems: PanelItems,
) : ViewModel() {

  /** null, solange die Liste lädt. */
  val model: StateFlow<RecentAppsModel?> =
    combine(
        source.newApps,
        source.updatedApps,
        installedAppsCache.installedApps,
        updatesManager.updates,
        updatesManager.appsWithIssues,
      ) { new, updated, _, updates, issues ->
        val apps = new.map { it to true } + updated.map { it to false }
        val items =
          apps
            .sortedByDescending { (app, _) -> app.lastUpdated }
            .mapNotNull { (app, isNew) ->
              val item = panelItems.listItem(app) ?: return@mapNotNull null
              RecentItem(item, isNew, panelItems.panelItem(item, updates, issues))
            }
        val repoIds = items.mapTo(HashSet()) { it.app.repoId }
        val repos =
          repoManager.repositoriesState.value
            .orEmpty()
            .filter { it.repoId in repoIds }
            .map { RecentRepo(it.repoId, it.displayName()) }
        RecentAppsModel(items, repos)
      }
      .flowOn(Dispatchers.IO)
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
