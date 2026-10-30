/*
 * Copyright 2026 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Based on org.fdroid.ui.navigation.SceneDecorator (mostly copied from
 * https://github.com/android/nav3-recipes), changed for Myzel: on small screens the navigation
 * floats as pills above the content instead of a bar below it.
 */

package de.needanapp.myzel.ui.nav

import androidx.compose.animation.EnterExitState
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.movableContentOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneDecoratorStrategy
import androidx.navigation3.scene.SceneDecoratorStrategyScope
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import de.needanapp.design.components.PillScrollBehavior
import de.needanapp.design.components.rememberPillScrollBehavior
import de.needanapp.design.theme.LocalDesignColors
import de.needanapp.design.theme.designBackground
import de.needanapp.myzel.ui.LocalBackgroundCover
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState

/**
 * Zustand der schwebenden Pillen: die Unschärfequelle für ihren Hintergrund, das Ausblenden beim
 * Scrollen und ob die Pillen gerade gezeigt werden (dann brauchen Listen unten Platz).
 */
@Stable
class PillHost(val hazeState: HazeState, val scrollBehavior: PillScrollBehavior) {
  /**
   * Wie viele Pillen gerade zusammengesetzt sind. Früher riefen beim Wechsel zwischen Hauptseiten
   * beide Szenen die Pillen auf; mit einem Ja/Nein meldete die alte beim Verschwinden die neue mit
   * ab, danach fehlte Listen unten der Platz. Heute trägt nur die erscheinende Szene die Pille, der
   * Zähler bleibt als Absicherung.
   */
  private var shownCount by mutableIntStateOf(0)

  val isShown: Boolean
    get() = shownCount > 0

  /** Die Pillen sind erschienen; zu jedem Aufruf gehört später einer von [onPillGone]. */
  fun onPillShown() {
    shownCount++
  }

  fun onPillGone() {
    shownCount--
  }

  /** Nur Bildschirme mit Pillen blenden sie beim Scrollen aus. */
  val nestedScrollConnection =
    object : NestedScrollConnection {
      override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
        if (isShown) scrollBehavior.nestedScrollConnection.onPreScroll(available, source)
        else Offset.Zero
    }
}

/** Die Pillen der aktuellen Szene; null außerhalb der Hauptansicht. */
val LocalPillHost = staticCompositionLocalOf<PillHost?> { null }

/**
 * Platz, den Inhalte unten zusätzlich freihalten, damit die Pillen das Letzte nicht verdecken. Der
 * Scaffold von Myzel (de.needanapp.myzel.ui.compat.Scaffold) rechnet ihn ein.
 */
val LocalBottomPillInset = compositionLocalOf { 0.dp }

/** Höhe der Pillen samt Abstand zum unteren Rand und etwas Luft darüber. */
internal val PillInset: Dp = 80.dp

class PillNavigationScene<T : Any>(
  scene: Scene<T>,
  isBigScreen: Boolean,
  sharedTransitionScope: SharedTransitionScope,
  pillHost: PillHost,
  navBarContent: @Composable (() -> Unit),
  navRailContent: @Composable (() -> Unit),
) : Scene<T> by scene {
  override val key = scene::class to scene.key

  override val content =
    @Composable {
      val animatedContentScope = LocalNavAnimatedContentScope.current
      val isMovableContentCaller =
        animatedContentScope.transition.targetState == EnterExitState.Visible

      with(sharedTransitionScope) {
        if (isBigScreen) {
          Row(Modifier.fillMaxSize()) {
            Box(
              modifier =
                Modifier.sharedElement(rememberSharedContentState("nav-rail"), animatedContentScope)
            ) {
              if (isMovableContentCaller) {
                navRailContent()
              }
            }
            Box(modifier = Modifier.weight(1f)) { scene.content() }
          }
        } else {
          val pages = LocalTopLevelPages.current
          val showsPage = pages != null && entries.size == 1 && pages.shows(entries[0].contentKey)
          Box(Modifier.fillMaxSize()) {
            if (showsPage) {
              // die Hauptseite liegt zusammengesetzt darunter (TopLevelPages), die Szene bleibt
              // leer und trägt nur die Pille
              pages.Placeholder()
            } else {
              // Steht die Szene still, deckt ihr Verlauf das Fenster; während eines Übergangs ist
              // auch der der Szene daneben zu sehen, dann zeichnet ihn MyzelTheme.
              val transition = animatedContentScope.transition
              LocalBackgroundCover.current.Covering(
                transition.currentState == EnterExitState.Visible && isMovableContentCaller
              )
              // der Hintergrund liegt innerhalb der Unschärfequelle, sonst wirkt die Pille deckend
              Box(
                Modifier.fillMaxSize()
                  .hazeSource(pillHost.hazeState)
                  .designBackground(LocalDesignColors.current)
                  .nestedScroll(pillHost.nestedScrollConnection)
              ) {
                val inset = if (pillHost.isShown) PillInset else 0.dp
                CompositionLocalProvider(LocalBottomPillInset provides inset) { scene.content() }
              }
            }
            // nur die erscheinende Szene trägt die Pille: Sie zieht mit, statt dass die alte Szene
            // während des Übergangs eine zweite, frische Pille zeigt
            if (isMovableContentCaller) {
              Box(Modifier.align(Alignment.BottomCenter)) {
                CompositionLocalProvider(LocalPillHost provides pillHost) { navBarContent() }
              }
            }
          }
        }
      }
    }
}

/**
 * Ersatz für org.fdroid.ui.navigation.rememberResponsiveNavigationSceneDecoratorStrategy mit
 * derselben Signatur; MainContent bindet ihn über den Import ein.
 */
@Composable
fun <T : Any> rememberResponsiveNavigationSceneDecoratorStrategy(
  isBigScreen: Boolean,
  navBar: @Composable (() -> Unit)?,
  navRail: @Composable () -> Unit,
  sharedTransitionScope: SharedTransitionScope,
): SceneDecoratorStrategy<T> {
  val currentNavBar by rememberUpdatedState(navBar)
  val currentNavRail by rememberUpdatedState(navRail)

  val movableNavBar = remember(navBar) { movableContentOf { currentNavBar?.invoke() } }
  val movableNavRail = remember { movableContentOf { currentNavRail() } }

  val hazeState = rememberHazeState()
  val scrollBehavior = rememberPillScrollBehavior()
  val pillHost = remember { PillHost(hazeState, scrollBehavior) }

  return remember(isBigScreen, sharedTransitionScope) {
    PillNavigationSceneDecoratorStrategy(
      isBigScreen = isBigScreen,
      sharedTransitionScope = sharedTransitionScope,
      pillHost = pillHost,
      navBarContent = movableNavBar,
      navRailContent = movableNavRail,
    )
  }
}

class PillNavigationSceneDecoratorStrategy<T : Any>(
  internal val isBigScreen: Boolean,
  private val sharedTransitionScope: SharedTransitionScope,
  internal val pillHost: PillHost,
  private val navBarContent: @Composable (() -> Unit),
  private val navRailContent: @Composable () -> Unit,
) : SceneDecoratorStrategy<T> {

  override fun SceneDecoratorStrategyScope<T>.decorateScene(scene: Scene<T>): Scene<T> {
    return PillNavigationScene(
      scene = scene,
      isBigScreen = isBigScreen,
      sharedTransitionScope = sharedTransitionScope,
      pillHost = pillHost,
      navBarContent = navBarContent,
      navRailContent = navRailContent,
    )
  }
}
