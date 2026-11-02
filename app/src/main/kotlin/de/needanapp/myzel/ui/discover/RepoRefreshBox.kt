package de.needanapp.myzel.ui.discover

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Dp
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import de.needanapp.design.components.RefreshBox
import de.needanapp.myzel.ui.compat.LocalScaffoldPadding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.fdroid.repo.RepoUpdateManager
import org.fdroid.repo.RepoUpdateWorker

/** So lange wartet der Kreis, ob die Aktualisierung startet. */
private const val START_TIMEOUT_MS = 5_000L

/**
 * Wie oft der Nutzer die Paketquellen in diesem [RepoRefreshBox] schon per Ziehen aktualisiert hat;
 * zählt hoch, sobald die Aktualisierung fertig ist. Das Karussell springt dann zum Anfang.
 */
internal val LocalRepoRefreshes = compositionLocalOf { 0 }

/**
 * Ziehen nach unten aktualisiert alle eingeschalteten Paketquellen, wie „Jetzt aktualisieren“ von
 * F-Droid, auch kurz nach der letzten Aktualisierung. Der Kreis dreht sich nur, bis sie anläuft;
 * dann zeigt die Anzeige oben, wie weit sie ist ([de.needanapp.myzel.ui.nav.RepoBoardSwitch]).
 * Läuft schon eine, verschwindet er sofort. Aktualisierungen im Hintergrund zeigen ihn nicht.
 *
 * [content] muss die scrollende Liste sein, damit das Ziehen hier ankommt. Der Kreis erscheint
 * [indicatorTop] unter dem oberen Rand, sonst unter der Kopfleiste, die ihn verdecken würde.
 */
@Composable
internal fun RepoRefreshBox(
  modifier: Modifier = Modifier,
  indicatorTop: Dp = LocalScaffoldPadding.current.calculateTopPadding(),
  content: @Composable () -> Unit,
) {
  if (LocalInspectionMode.current) {
    Box(modifier) { content() }
    return
  }
  val context = LocalContext.current
  val repoUpdateManager = remember {
    EntryPointAccessors.fromApplication(context, RepoUpdateEntryPoint::class.java)
      .repoUpdateManager()
  }
  var refreshing by remember { mutableStateOf(false) }
  var refreshes by remember { mutableIntStateOf(0) }
  val scope = rememberCoroutineScope()
  RefreshBox(
    isRefreshing = refreshing,
    onRefresh = {
      refreshing = true
      RepoUpdateWorker.updateNow(context, manual = true)
      scope.launch {
        val started =
          withTimeoutOrNull(START_TIMEOUT_MS) { repoUpdateManager.isUpdating.first { it } } != null
        refreshing = false
        // das Karussell springt erst zum Anfang, wenn die neuen Apps da sind
        if (started) repoUpdateManager.isUpdating.first { !it }
        refreshes++
      }
    },
    modifier = modifier,
    indicatorTop = indicatorTop,
  ) {
    CompositionLocalProvider(LocalRepoRefreshes provides refreshes, content = content)
  }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RepoUpdateEntryPoint {
  fun repoUpdateManager(): RepoUpdateManager
}
