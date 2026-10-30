package de.needanapp.myzel.ui.discover

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import de.needanapp.design.components.RefreshBox
import de.needanapp.myzel.ui.compat.LocalScaffoldPadding
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import org.fdroid.repo.RepoUpdateManager
import org.fdroid.repo.RepoUpdateWorker

/** So lange wartet der Kreis, ob die Aktualisierung startet. */
private const val START_TIMEOUT_MS = 5_000L

/**
 * Wie oft der Nutzer die Paketquellen in diesem [RepoRefreshBox] schon per Ziehen aktualisiert hat;
 * zählt hoch, sobald der Kreis verschwindet. Das Karussell springt dann zum Anfang.
 */
internal val LocalRepoRefreshes = compositionLocalOf { 0 }

/**
 * Ziehen nach unten aktualisiert alle eingeschalteten Paketquellen, wie „Jetzt aktualisieren“ von
 * F-Droid. Der Kreis dreht sich, bis die Aktualisierung fertig ist. Startet keine (F-Droid
 * überspringt sie in den ersten 15 s nach der letzten), verschwindet er nach kurzer Zeit.
 * Aktualisierungen im Hintergrund zeigen ihn nicht.
 *
 * [content] muss die scrollende Liste sein, damit das Ziehen hier ankommt.
 */
@Composable
internal fun RepoRefreshBox(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
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
  LaunchedEffect(refreshing) {
    if (!refreshing) return@LaunchedEffect
    val started =
      withTimeoutOrNull(START_TIMEOUT_MS) { repoUpdateManager.isUpdating.first { it } } != null
    if (started) repoUpdateManager.isUpdating.first { !it }
    refreshing = false
    refreshes++
  }
  RefreshBox(
    isRefreshing = refreshing,
    onRefresh = {
      refreshing = true
      RepoUpdateWorker.updateNow(context)
    },
    modifier = modifier,
    // unter der Kopfleiste, sonst verdeckt sie den Kreis
    indicatorTop = LocalScaffoldPadding.current.calculateTopPadding(),
  ) {
    CompositionLocalProvider(LocalRepoRefreshes provides refreshes, content = content)
  }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RepoUpdateEntryPoint {
  fun repoUpdateManager(): RepoUpdateManager
}
