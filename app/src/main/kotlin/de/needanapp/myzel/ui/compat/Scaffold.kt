package de.needanapp.myzel.ui.compat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.FabPosition
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScaffoldDefaults
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import de.needanapp.design.theme.LocalDesignColors
import de.needanapp.myzel.ui.nav.LocalBottomPillInset

/**
 * Die Innenabstände, die der [Scaffold] seinem Inhalt gibt, auch für Bausteine tief darin (etwa den
 * Kreis beim Ziehen auf Entdecken, der unter der Kopfleiste erscheinen soll).
 */
val LocalScaffoldPadding = compositionLocalOf<PaddingValues> { PaddingValues() }

/**
 * Ersatz für androidx.compose.material3.Scaffold mit derselben Signatur; die Bildschirme von
 * F-Droid binden ihn über den Import ein (myzel/tools/apply-imports.py). Hält unten Platz für die
 * schwebenden Pillen frei, solange sie gezeigt werden, und hebt einen schwebenden Knopf darüber.
 * Der Standard-Hintergrund ist durchsichtig, damit der Verlauf des Design-Pakets durchscheint; die
 * Kopfleiste geht dann nahtlos in ihn über.
 */
@Composable
fun Scaffold(
  modifier: Modifier = Modifier,
  topBar: @Composable () -> Unit = {},
  bottomBar: @Composable () -> Unit = {},
  snackbarHost: @Composable () -> Unit = {},
  floatingActionButton: @Composable () -> Unit = {},
  floatingActionButtonPosition: FabPosition = FabPosition.End,
  containerColor: Color = MaterialTheme.colorScheme.background,
  contentColor: Color = contentColorFor(containerColor),
  contentWindowInsets: WindowInsets = ScaffoldDefaults.contentWindowInsets,
  content: @Composable (PaddingValues) -> Unit,
) {
  val pillInset = LocalBottomPillInset.current
  val transparent = containerColor == MaterialTheme.colorScheme.background
  androidx.compose.material3.Scaffold(
    modifier = modifier,
    topBar = { if (transparent) OnGradient(topBar) else topBar() },
    bottomBar = bottomBar,
    snackbarHost = { Box(Modifier.padding(bottom = pillInset)) { snackbarHost() } },
    floatingActionButton = { Box(Modifier.padding(bottom = pillInset)) { floatingActionButton() } },
    floatingActionButtonPosition = floatingActionButtonPosition,
    containerColor = if (transparent) Color.Transparent else containerColor,
    contentColor = contentColor,
    contentWindowInsets = contentWindowInsets,
  ) { paddingValues ->
    val padding = paddingValues.plusBottom(pillInset)
    CompositionLocalProvider(LocalScaffoldPadding provides padding) { content(padding) }
  }
}

private fun PaddingValues.plusBottom(extra: Dp): PaddingValues {
  val base = this
  return object : PaddingValues {
    override fun calculateLeftPadding(layoutDirection: LayoutDirection) =
      base.calculateLeftPadding(layoutDirection)

    override fun calculateTopPadding() = base.calculateTopPadding()

    override fun calculateRightPadding(layoutDirection: LayoutDirection) =
      base.calculateRightPadding(layoutDirection)

    override fun calculateBottomPadding() = base.calculateBottomPadding() + extra
  }
}

/**
 * Die Kopfleisten von F-Droid haben die Farbe surface. Über dem Verlauf sind sie durchsichtig und
 * liegen auf dem Ausschnitt des Verlaufs, der hinter ihnen läge: Sie gehen nahtlos in ihn über und
 * verdecken trotzdem, was darunter scrollt.
 */
@Composable
private fun OnGradient(content: @Composable () -> Unit) {
  val colors = LocalDesignColors.current
  val height = LocalWindowInfo.current.containerSize.height.toFloat()
  Box(Modifier.drawBehind { drawRect(colors.backgroundBrush(height)) }) {
    MaterialTheme(
      colorScheme = MaterialTheme.colorScheme.copy(surface = Color.Transparent),
      content = content,
    )
  }
}
