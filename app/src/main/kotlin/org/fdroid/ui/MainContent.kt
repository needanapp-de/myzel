package org.fdroid.ui

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.material3.adaptive.layout.PaneScaffoldDirective
import androidx.compose.material3.adaptive.navigation3.rememberListDetailSceneStrategy
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.NavKey
import com.viktormykhailiv.compose.hints.hintAnchor
import com.viktormykhailiv.compose.hints.rememberHintAnchorState
import com.viktormykhailiv.compose.hints.rememberHintController
import de.needanapp.myzel.ui.compat.HintOverlayContainer
import de.needanapp.myzel.ui.nav.BottomBar
import de.needanapp.myzel.ui.nav.NavDisplay
import de.needanapp.myzel.ui.nav.NavigationRail
import de.needanapp.myzel.ui.nav.rememberResponsiveNavigationSceneDecoratorStrategy
import de.needanapp.myzel.ui.nav.topLevelSwipe

@Composable
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
fun MainContent(
  model: MainModel,
  navEntries: List<NavEntry<NavKey>>,
  directive: PaneScaffoldDirective,
  isBigScreen: Boolean,
  showBottomBar: Boolean,
  currentNavKey: NavKey,
  onOnboardingSeen: () -> Unit,
  onNav: (NavKey) -> Unit,
  onBack: () -> Unit,
) =
  FDroidContent(dynamicColors = model.dynamicColors) {
    val listDetailStrategy = rememberListDetailSceneStrategy<NavKey>(directive = directive)
    SharedTransitionLayout {
      HintOverlayContainer {
        val hintController = rememberHintController()
        val onOnboardingSeen = {
          hintController.dismiss()
          onOnboardingSeen()
        }
        val hintAnchor = rememberHintAnchorState(getMainOnboardingHint(onOnboardingSeen, onNav))
        LaunchedEffect(model.showOnboarding) {
          if (model.showOnboarding) {
            hintController.show(hintAnchor)
          }
        }
        val responsiveNavigationSceneDecoratorStrategy =
          rememberResponsiveNavigationSceneDecoratorStrategy<NavKey>(
            isBigScreen = isBigScreen,
            navBar = {
              if (showBottomBar) {
                BottomBar(model = model, currentNavKey = currentNavKey, onNav = onNav)
              }
            },
            navRail = {
              NavigationRail(
                numUpdates = model.numUpdates,
                hasIssues = model.hasAppIssues,
                currentNavKey = currentNavKey,
                onNav = onNav,
              )
            },
            sharedTransitionScope = this,
          )
        NavDisplay(
          entries = navEntries,
          sceneDecoratorStrategies = listOf(responsiveNavigationSceneDecoratorStrategy),
          sceneStrategies = listOf(listDetailStrategy),
          onBack = onBack,
          modifier =
            Modifier.hintAnchor(hintAnchor, fullScreen = true)
              // Myzel: zwischen den Bereichen der Pille wischen
              .topLevelSwipe(currentNavKey, enabled = showBottomBar, onNav = onNav),
        )
      }
    }
  }
