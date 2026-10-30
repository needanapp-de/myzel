package de.needanapp.myzel.ui.nav

import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.navigation3.rememberViewModelStoreNavEntryDecorator
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import androidx.navigation3.runtime.rememberDecoratedNavEntries
import androidx.navigation3.runtime.rememberSaveableStateHolderNavEntryDecorator
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.rememberNavigationEventDispatcherOwner
import de.needanapp.design.theme.LocalDesignColors
import de.needanapp.design.theme.designBackground
import de.needanapp.myzel.ui.LocalBackgroundCover
import dev.chrisbanes.haze.hazeSource
import kotlin.math.abs
import kotlinx.coroutines.delay
import org.fdroid.ui.navigation.NavigationState
import org.fdroid.ui.navigation.topLevelRoutes

/**
 * Ersatz für org.fdroid.ui.navigation.toEntries in Main.kt, über den Import eingebunden
 * (myzel/tools/apply-imports.py): dieselben Einträge, dazu die Hauptseite jedes Stapels für
 * [TopLevelPages], auch der Stapel, die gerade nicht in Gebrauch sind.
 */
@Composable
fun NavigationState.toEntries(entryProvider: (NavKey) -> NavEntry<NavKey>): List<NavEntry<NavKey>> {
  val decoratedEntries = backStacks.mapValues { (_, stack) ->
    val decorators =
      listOf(
        rememberSaveableStateHolderNavEntryDecorator<NavKey>(),
        rememberViewModelStoreNavEntryDecorator(),
      )
    rememberDecoratedNavEntries(
      backStack = stack,
      entryDecorators = decorators,
      entryProvider = entryProvider,
    )
  }
  val pages =
    decoratedEntries
      .mapNotNull { (route, entries) ->
        // nur wenn der Stapel noch mit seiner Hauptseite beginnt
        entries.firstOrNull()?.takeIf { it.contentKey == route.toString() }?.let { route to it }
      }
      .toMap()
  return TopLevelNavEntries(
    entries = stacksInUse.flatMap { decoratedEntries[it] ?: emptyList() },
    pages = pages,
    current = topLevelRoute,
  )
}

/** Die Einträge für NavDisplay, dazu die Hauptseiten ([pages]) und welche gerade dran ist. */
class TopLevelNavEntries(
  entries: List<NavEntry<NavKey>>,
  val pages: Map<NavKey, NavEntry<NavKey>>,
  val current: NavKey,
) : List<NavEntry<NavKey>> by entries {
  /** Inhaltsschlüssel der Hauptseiten, die [TopLevelPages] zeigt. */
  val pageKeys: Set<Any> = pages.values.map { it.contentKey }.toSet()
}

/**
 * Auf kleinen Bildschirmen bleiben die Bereiche der Pille (Entdecken, Suche, Meine Apps) dauerhaft
 * zusammengesetzt und liegen nebeneinander unter NavDisplay. Ein Wechsel baut so keine Seite neu
 * auf (gemessen: Suche 136–300 ms, Meine Apps 66–93 ms in einem Bild, die Kategorien der Suche
 * kamen erst danach), und beim Wischen folgen die Seiten dem Finger ([TopLevelSwipe]). Die Szene
 * einer Hauptseite bleibt leer und trägt nur die Pille ([TopLevelPagesState.Placeholder]); solange
 * keine solche Szene da ist, etwa unter einer App-Seite, sind die Seiten unsichtbar und nicht
 * antippbar.
 *
 * Seiten, die gerade nicht dran sind, bekommen keine Zurück-Ereignisse und verlieren den Fokus (die
 * Tastatur der Suche schließt sich). Die anderen Seiten setzt [TopLevelPages] erst kurz nach dem
 * Start zusammen, damit der Start nicht länger dauert.
 */
@Stable
class TopLevelPagesState {
  private var placeholders by mutableIntStateOf(0)

  /** Ob gerade die Szene einer Hauptseite da ist, also die Seiten zu sehen sind. */
  val isVisible: Boolean
    get() = placeholders > 0

  internal var pageKeys: Set<Any> = emptySet()

  /** Ob [TopLevelPages] die Seite mit diesem Inhaltsschlüssel zeigt. */
  fun shows(contentKey: Any?): Boolean = contentKey in pageKeys

  /** Steht in der Szene einer Hauptseite, solange sie da ist. */
  @Composable
  fun Placeholder() {
    DisposableEffect(Unit) {
      placeholders++
      onDispose { placeholders-- }
    }
  }
}

/** Die Hauptseiten unter NavDisplay; null, wenn NavDisplay sie selbst zeigt (große Bildschirme). */
val LocalTopLevelPages = staticCompositionLocalOf<TopLevelPagesState?> { null }

/** Ob die Hauptseite gerade dran ist; außerhalb von [TopLevelPages] immer. */
val LocalTopLevelPageActive = compositionLocalOf { true }

@Composable
internal fun TopLevelPages(
  state: TopLevelPagesState,
  entries: TopLevelNavEntries,
  pillHost: PillHost,
) {
  state.pageKeys = entries.pageKeys
  val currentIndex = rememberUpdatedState(topLevelRoutes.indexOf(entries.current))
  Cover(state)
  val inset = if (pillHost.isShown) PillInset else 0.dp
  // der Hintergrund liegt innerhalb der Unschärfequelle, sonst wirkt die Pille deckend
  Box(
    Modifier.fillMaxSize()
      .graphicsLayer { alpha = if (state.isVisible) 1f else 0f }
      .hazeSource(pillHost.hazeState)
      .designBackground(LocalDesignColors.current)
      .nestedScroll(pillHost.nestedScrollConnection)
  ) {
    topLevelRoutes.forEachIndexed { index, route ->
      val entry = entries.pages[route] ?: return@forEachIndexed
      key(route) {
        TopLevelPage(
          entry = entry,
          index = index,
          currentIndex = currentIndex,
          state = state,
          active = index == currentIndex.value,
          inset = inset,
        )
      }
    }
  }
}

/** Solange die Seiten zu sehen sind, decken sie mit ihrem Verlauf das Fenster. */
@Composable
private fun Cover(state: TopLevelPagesState) {
  LocalBackgroundCover.current.Covering(state.isVisible)
}

@Composable
private fun TopLevelPage(
  entry: NavEntry<NavKey>,
  index: Int,
  currentIndex: State<Int>,
  state: TopLevelPagesState,
  active: Boolean,
  inset: Dp,
) {
  // die Seite, die dran ist, sofort, die anderen kurz nach dem Start; danach bleibt sie
  var composed by remember { mutableStateOf(active) }
  LaunchedEffect(active) {
    if (!active && !composed) delay(WARM_UP_MILLIS + index * WARM_UP_STEP_MILLIS)
    composed = true
  }
  if (!composed && !active) return

  var hasFocus by remember { mutableStateOf(false) }
  val focusManager = LocalFocusManager.current
  val keyboard = LocalSoftwareKeyboardController.current
  LaunchedEffect(active) {
    if (!active && hasFocus) {
      focusManager.clearFocus(force = true)
      keyboard?.hide()
    }
  }
  // Zurück-Ereignisse nur für die Seite, die dran ist (etwa die Suche in Meine Apps schließen)
  val backOwner = rememberNavigationEventDispatcherOwner(active)
  Box(
    Modifier.fillMaxSize()
      .graphicsLayer {
        val shift = (index - currentIndex.value) * size.width + TopLevelSwipe.offset
        val shown = state.isVisible && abs(shift) < size.width
        // was nicht zu sehen ist, liegt außerhalb und fängt keine Tipps
        translationX = if (shown) shift else OUTSIDE * size.width
        alpha = if (shown) 1f else 0f
      }
      .onFocusChanged { hasFocus = it.hasFocus }
      .focusGroup()
  ) {
    CompositionLocalProvider(
      LocalNavigationEventDispatcherOwner provides backOwner,
      LocalBottomPillInset provides inset,
      LocalTopLevelPageActive provides active,
    ) {
      entry.Content()
    }
  }
}

/** Nicht sichtbare Seiten liegen so viele Breiten links außerhalb. */
private const val OUTSIDE = -4f

private const val WARM_UP_MILLIS = 800L
private const val WARM_UP_STEP_MILLIS = 300L
