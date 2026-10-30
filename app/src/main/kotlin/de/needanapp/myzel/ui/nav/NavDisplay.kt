package de.needanapp.myzel.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.scene.Scene
import androidx.navigation3.scene.SceneDecoratorStrategy
import androidx.navigation3.scene.SceneStrategy
import androidx.navigation3.scene.SinglePaneSceneStrategy
import de.needanapp.design.theme.LocalDesignEffects
import org.fdroid.ui.navigation.topLevelRoutes

/**
 * Ersatz für androidx.navigation3.ui.NavDisplay in MainContent.kt, über den Import eingebunden
 * (myzel/tools/apply-imports.py). Navigation3 blendet bei jedem Wechsel 700 ms lang beide
 * Bildschirme halbtransparent über; das Umschalten über die Pille wirkt träge, eine kurze
 * Überblendung stockt (gemessen auf dem Pixel 6, siehe myzel/README.md).
 * - Auf kleinen Bildschirmen liegen die Bereiche der Pille ([topLevelRoutes]) zusammengesetzt
 *   darunter ([TopLevelPages]); NavDisplay zeigt für sie nur die Pille. Zwischen ihnen wechselt
 *   Myzel ohne Überblendung, beim Wischen folgen die Seiten dem Finger. Jede Szene zeigt dann genau
 *   eine Seite: Die Aufteilung in Liste und Details (sceneStrategies) gibt es dort ohnehin nicht,
 *   und eine Hauptseite darf nicht zugleich in einer Szene stehen.
 * - Beim Öffnen einer Seite blendet nur die neue Seite über der alten ein, die deckend darunter
 *   stehen bleibt (eine halbtransparente Ebene statt zwei). Die Einblendung beginnt erst nach
 *   [FADE_DELAY_MILLIS]: Im ersten Bild baut Compose die Seite auf (30–45 ms), eine sofort laufende
 *   Einblendung spränge danach ein Stück vor. Zurück auf eine Hauptseite blendet umgekehrt die alte
 *   Seite aus, denn die Szene der Hauptseite ist leer.
 * - Ohne Bewegung (Effekte reduziert, Animationen aus) wechselt alles sofort. Das Zurückwischen
 *   behält die Animation von Navigation3.
 */
@Composable
fun <T : Any> NavDisplay(
  entries: List<NavEntry<T>>,
  sceneDecoratorStrategies: List<SceneDecoratorStrategy<T>>,
  sceneStrategies: List<SceneStrategy<T>>,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val moves = LocalDesignEffects.current.richMotion
  val pills = sceneDecoratorStrategies.firstNotNullOfOrNull {
    it as? PillNavigationSceneDecoratorStrategy<T>
  }
  val topLevel = (entries as? TopLevelNavEntries)?.takeIf { pills != null && !pills.isBigScreen }
  val pages = remember { TopLevelPagesState() }
  val transition: AnimatedContentTransitionScope<Scene<T>>.() -> ContentTransform = {
    val betweenTopLevel = initialState.isTopLevel() && targetState.isTopLevel()
    when {
      !moves || betweenTopLevel -> EnterTransition.None togetherWith ExitTransition.None
      topLevel != null && targetState.isTopLevel() ->
        (EnterTransition.None togetherWith
            fadeOut(
              tween(FADE_MILLIS, delayMillis = FADE_DELAY_MILLIS, easing = FastOutSlowInEasing)
            ))
          .apply { targetContentZIndex = -1f }
      else ->
        fadeIn(tween(FADE_MILLIS, delayMillis = FADE_DELAY_MILLIS, easing = FastOutSlowInEasing))
          .togetherWith(ExitTransition.KeepUntilTransitionsFinished)
    }
  }
  val singlePane = remember { listOf(SinglePaneSceneStrategy<T>()) }
  Box(modifier) {
    if (topLevel != null && pills != null) {
      TopLevelPages(pages, topLevel, pills.pillHost)
    }
    CompositionLocalProvider(LocalTopLevelPages provides pages.takeIf { topLevel != null }) {
      androidx.navigation3.ui.NavDisplay(
        entries = entries,
        modifier = Modifier.fillMaxSize(),
        sceneStrategies = if (topLevel != null) singlePane else sceneStrategies,
        sceneDecoratorStrategies = sceneDecoratorStrategies,
        transitionSpec = transition,
        popTransitionSpec = transition,
        onBack = onBack,
      )
    }
  }
}

/**
 * Ob die Szene einen Bereich der Pille zeigt. Navigation3 gibt den Schlüssel eines Eintrags nicht
 * heraus; der Inhaltsschlüssel ist ohne eigene Angabe aber sein Text, etwa „Discover“.
 */
private fun Scene<*>.isTopLevel(): Boolean = entries.lastOrNull()?.contentKey in TopLevelContentKeys

private val TopLevelContentKeys = topLevelRoutes.map { it.toString() }.toSet()

private const val FADE_MILLIS = 250
private const val FADE_DELAY_MILLIS = 50
