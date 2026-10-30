package de.needanapp.myzel.ui.nav

import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.currentWindowAdaptiveInfo
import androidx.compose.material3.adaptive.layout.calculatePaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.ListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.EntryProviderScope
import androidx.navigation3.runtime.NavKey
import de.needanapp.myzel.favorites.Favorites
import de.needanapp.myzel.favorites.FavoritesScreen
import de.needanapp.myzel.favorites.FavoritesViewModel
import de.needanapp.myzel.recent.RecentApps
import de.needanapp.myzel.recent.RecentAppsScreen
import de.needanapp.myzel.recent.RecentAppsViewModel
import de.needanapp.myzel.watch.ui.WatchPage
import de.needanapp.myzel.watch.ui.WatchScreen
import org.fdroid.ui.navigation.NavigationKey
import org.fdroid.ui.navigation.Navigator
import org.fdroid.ui.navigation.extraNavigationEntries as fdroidExtraNavigationEntries

/**
 * Ersatz für org.fdroid.ui.navigation.extraNavigationEntries in Main.kt, über den Import
 * eingebunden (myzel/tools/apply-imports.py): die Ziele der Variante von F-Droid und dazu die
 * Favoriten, „Neu und aktualisiert“ und die Seite „Uhr“ von Myzel.
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun EntryProviderScope<NavKey>.extraNavigationEntries(navigator: Navigator) {
  fdroidExtraNavigationEntries(navigator)
  // wie die App-Listen von F-Droid (org.fdroid.ui.lists.appListEntry): auf großen Bildschirmen
  // stehen die App-Details daneben
  val openDetails: (String) -> Unit = { packageName ->
    val new = NavigationKey.AppDetails(packageName)
    if (navigator.last is NavigationKey.AppDetails) {
      navigator.replaceLast(new)
    } else {
      navigator.navigate(new)
    }
  }
  entry(key = Favorites, metadata = ListDetailSceneStrategy.listPane("appdetails")) {
    val viewModel = hiltViewModel<FavoritesViewModel>()
    FavoritesScreen(
      model = viewModel.model.collectAsStateWithLifecycle().value,
      currentPackageName = currentDetailsPackageName(navigator),
      onBackClicked = { navigator.goBack() },
      onItemClick = openDetails,
    )
  }
  entry<RecentApps>(metadata = ListDetailSceneStrategy.listPane("appdetails")) { key ->
    val viewModel = hiltViewModel<RecentAppsViewModel>()
    RecentAppsScreen(
      model = viewModel.model.collectAsStateWithLifecycle().value,
      initialRepoId = key.repoId,
      currentPackageName = currentDetailsPackageName(navigator),
      onBackClicked = { navigator.goBack() },
      onItemClick = openDetails,
    )
  }
  entry(key = WatchPage) { WatchScreen(hiltViewModel(), onBackClicked = { navigator.goBack() }) }
}

/** Auf großen Bildschirmen die App, deren Details neben der Liste offen sind. */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
private fun currentDetailsPackageName(navigator: Navigator): String? {
  val isBigScreen =
    calculatePaneScaffoldDirective(currentWindowAdaptiveInfo()).maxHorizontalPartitions > 1
  return if (isBigScreen) (navigator.last as? NavigationKey.AppDetails)?.packageName else null
}
