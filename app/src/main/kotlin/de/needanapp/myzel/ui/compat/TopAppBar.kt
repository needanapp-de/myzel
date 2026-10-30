package de.needanapp.myzel.ui.compat

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp

/**
 * Ersatz für androidx.compose.material3.TopAppBar mit denselben Parametern; die Bildschirme von
 * F-Droid binden ihn über den Import ein (myzel/tools/apply-imports.py). Material 3 färbt die
 * Kopfleiste beim Scrollen in surfaceContainer. Über dem Verlauf ([Scaffold]) stand sie dann als
 * dunkles, flaches Band da. Hier behält sie beim Scrollen ihre Farbe und bleibt so ein Teil des
 * Verlaufs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBar(
  title: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  navigationIcon: @Composable () -> Unit = {},
  actions: @Composable RowScope.() -> Unit = {},
  expandedHeight: Dp = TopAppBarDefaults.TopAppBarExpandedHeight,
  windowInsets: WindowInsets = TopAppBarDefaults.windowInsets,
  colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
  scrollBehavior: TopAppBarScrollBehavior? = null,
) {
  androidx.compose.material3.TopAppBar(
    title = title,
    modifier = modifier,
    navigationIcon = navigationIcon,
    actions = actions,
    expandedHeight = expandedHeight,
    windowInsets = windowInsets,
    colors = colors.unchangedOnScroll(),
    scrollBehavior = scrollBehavior,
  )
}

/** Wie oben, mit Untertitel. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopAppBar(
  title: @Composable () -> Unit,
  subtitle: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  navigationIcon: @Composable () -> Unit = {},
  actions: @Composable RowScope.() -> Unit = {},
  colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(),
  scrollBehavior: TopAppBarScrollBehavior? = null,
) {
  androidx.compose.material3.TopAppBar(
    title = title,
    subtitle = subtitle,
    modifier = modifier,
    navigationIcon = navigationIcon,
    actions = actions,
    colors = colors.unchangedOnScroll(),
    scrollBehavior = scrollBehavior,
  )
}

private fun TopAppBarColors.unchangedOnScroll() = copy(scrolledContainerColor = containerColor)
