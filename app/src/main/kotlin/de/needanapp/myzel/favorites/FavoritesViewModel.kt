package de.needanapp.myzel.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import de.needanapp.myzel.ui.apps.PanelItems
import java.text.Collator
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import org.fdroid.database.FDroidDatabase
import org.fdroid.install.InstalledAppsCache
import org.fdroid.ui.apps.MyAppItem
import org.fdroid.ui.lists.AppListItem
import org.fdroid.updates.UpdatesManager

/**
 * Die Favoriten als Liste.
 *
 * @property apps die Favoriten, die eine eingeschaltete Paketquelle anbietet, nach Namen sortiert
 * @property unavailable wie viele Favoriten gerade keine eingeschaltete Paketquelle anbietet
 */
data class FavoritesModel(val apps: List<FavoriteItem>, val unavailable: Int)

/**
 * Ein Favorit: [app] für die Zeile, [installed] für das Bedienfeld wie in „Meine Apps“ (mit Update,
 * mit Problem oder installiert); null, wenn die App nicht installiert ist.
 */
data class FavoriteItem(val app: AppListItem, val installed: MyAppItem?)

@HiltViewModel
class FavoritesViewModel
@Inject
constructor(
  private val db: FDroidDatabase,
  private val installedAppsCache: InstalledAppsCache,
  private val updatesManager: UpdatesManager,
  private val panelItems: PanelItems,
  favoritesManager: FavoritesManager,
) : ViewModel() {

  private val collator = Collator.getInstance(Locale.getDefault())

  /** null, solange die Liste lädt. */
  @OptIn(ExperimentalCoroutinesApi::class)
  val model: StateFlow<FavoritesModel?> =
    // neu laden, wenn sich Favoriten oder installierte Apps ändern
    combine(favoritesManager.favorites, installedAppsCache.installedApps) { favorites, _ ->
        favorites
      }
      .flatMapLatest { favorites ->
        if (favorites.isEmpty()) {
          flowOf(FavoritesModel(emptyList(), 0))
        } else {
          combine(
            db.getAppDao().getAppsFlow(favorites.toList()),
            updatesManager.updates,
            updatesManager.appsWithIssues,
          ) { apps, updates, issues ->
            val items =
              apps
                .mapNotNull { panelItems.listItem(it) }
                .sortedWith(compareBy(collator) { it.name })
                .map { item -> FavoriteItem(item, panelItems.panelItem(item, updates, issues)) }
            FavoritesModel(items, unavailable = favorites.size - items.size)
          }
        }
      }
      .flowOn(Dispatchers.IO)
      .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
