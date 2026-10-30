package de.needanapp.myzel.ui.compat

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.TooltipScope
import androidx.compose.material3.TooltipState
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupPositionProvider
import de.needanapp.design.components.KeyStyle
import de.needanapp.design.components.OptionsSheet
import de.needanapp.design.components.RoundKey
import de.needanapp.design.components.keyColors
import de.needanapp.design.components.keyEdge
import de.needanapp.design.theme.LocalDesignColors
import de.needanapp.design.theme.isNeedanappDesign
import org.fdroid.R

/*
 * Ersatz für die Knöpfe und Menüs der Kopfleisten von F-Droid (org.fdroid.ui.utils.TopAppBarButton,
 * BackButton, TopAppBarOverflowButton, dazu DropdownMenu, DropdownMenuItem, FloatingActionButton und
 * TooltipBox aus Material 3) mit denselben Signaturen, über myzel/tools/apply-imports.py
 * eingebunden. Wie in needafile: Knöpfe ohne Tooltip, Menüs als Blatt von unten (OptionsSheet des
 * Design-Pakets) mit Zeilen wie dessen SheetAction, die Hauptaktion als runder Knopf.
 */

/**
 * Ein Knopf der Kopfleiste: eine runde Alu-Taste im Stil „Myzel“, ein schlichtes Symbol bei
 * NeedAnApp.
 */
@Composable
fun TopAppBarButton(
  imageVector: ImageVector,
  contentDescription: String,
  enabled: Boolean = true,
  onClick: () -> Unit,
) {
  RoundKey(imageVector, contentDescription, onClick, enabled = enabled, diameter = TopBarKeySize)
}

/** Durchmesser der Alu-Tasten in den Kopfleisten. */
internal val TopBarKeySize = 44.dp

/**
 * Ersatz für androidx.compose.material3.IconButton in den Kopfleisten von F-Droid (Paketquellen,
 * Paketquelle hinzufügen, App-Listen, Über), über myzel/tools/apply-imports.py eingebunden: der
 * Inhalt, meist ein Symbol, auf einer runden Alu-Taste wie [TopAppBarButton]; im Design needanapp
 * schlicht ohne Taste.
 */
@Composable
fun IconButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  content: @Composable () -> Unit,
) {
  if (isNeedanappDesign) {
    PlainIconButton(onClick, modifier, enabled, content)
    return
  }
  val (container, contentColor) = keyColors(KeyStyle.ALU, enabled)
  val design = LocalDesignColors.current
  Box(
    contentAlignment = Alignment.Center,
    modifier =
      modifier
        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
        .clickable(
          enabled = enabled,
          role = Role.Button,
          interactionSource = null,
          indication = ripple(bounded = false, radius = TopBarKeySize / 2),
          onClick = onClick,
        ),
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier =
        Modifier.size(TopBarKeySize)
          .clip(CircleShape)
          .background(container)
          .then(if (enabled) Modifier.border(1.dp, design.aluKeyBorder, CircleShape) else Modifier)
          .keyEdge(KeyStyle.ALU, enabled),
    ) {
      // so groß wie die Symbole der übrigen Alu-Tasten
      CompositionLocalProvider(LocalContentColor provides contentColor) {
        Box(Modifier.size(20.dp), contentAlignment = Alignment.Center) { content() }
      }
    }
  }
}

/** [IconButton] im Design needanapp: das Symbol in der Farbe der Nebentexte, ohne Taste. */
@Composable
private fun PlainIconButton(
  onClick: () -> Unit,
  modifier: Modifier,
  enabled: Boolean,
  content: @Composable () -> Unit,
) {
  val color = MaterialTheme.colorScheme.onSurfaceVariant
  Box(
    contentAlignment = Alignment.Center,
    modifier =
      modifier
        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
        .clickable(
          enabled = enabled,
          role = Role.Button,
          interactionSource = null,
          indication = ripple(bounded = false, radius = 20.dp),
          onClick = onClick,
        ),
  ) {
    CompositionLocalProvider(
      LocalContentColor provides if (enabled) color else color.copy(alpha = DISABLED_ALPHA)
    ) {
      Box(Modifier.size(22.dp), contentAlignment = Alignment.Center) { content() }
    }
  }
}

/** Zurück in der Kopfleiste. */
@Composable
fun BackButton(onClick: () -> Unit) =
  TopAppBarButton(
    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
    contentDescription = stringResource(R.string.back),
    onClick = onClick,
  )

/** ⋮ in der Kopfleiste; die Einträge erscheinen als Blatt von unten. */
@Composable
fun TopAppBarOverflowButton(
  menuItems: @Composable (ColumnScope.(onDismissRequest: () -> Unit) -> Unit)
) {
  var menuExpanded by remember { mutableStateOf(false) }
  TopAppBarButton(
    Icons.Outlined.MoreVert,
    contentDescription = stringResource(R.string.menu_overflow_description),
    onClick = { menuExpanded = true },
  )
  DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
    menuItems { menuExpanded = false }
  }
}

/** Ein Menü als Blatt von unten statt als aufklappende Liste. */
@Composable
fun DropdownMenu(
  expanded: Boolean,
  onDismissRequest: () -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable ColumnScope.() -> Unit,
) {
  if (expanded) {
    OptionsSheet(onDismiss = onDismissRequest) {
      Column(modifier.fillMaxWidth(), content = content)
    }
  }
}

/** Ein Eintrag des Menüs, aufgebaut wie SheetAction des Design-Pakets. */
@Composable
fun DropdownMenuItem(
  text: @Composable () -> Unit,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  leadingIcon: @Composable (() -> Unit)? = null,
  trailingIcon: @Composable (() -> Unit)? = null,
  enabled: Boolean = true,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      modifier
        .fillMaxWidth()
        .heightIn(min = 56.dp)
        .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
        .padding(horizontal = 24.dp, vertical = 8.dp)
        .alpha(if (enabled) 1f else DISABLED_ALPHA),
  ) {
    if (leadingIcon != null) {
      CompositionLocalProvider(
        LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant
      ) {
        Box(Modifier.size(24.dp), contentAlignment = Alignment.Center) { leadingIcon() }
      }
      Spacer(Modifier.width(20.dp))
    }
    Box(Modifier.weight(1f)) { ProvideTextStyle(MaterialTheme.typography.bodyLarge) { text() } }
    if (trailingIcon != null) {
      Spacer(Modifier.width(12.dp))
      trailingIcon()
    }
  }
}

/** Die Hauptaktion eines Bildschirms als runder Knopf in der Hauptfarbe, etwa „Hinzufügen“. */
@Composable
fun FloatingActionButton(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit = { Icon(Icons.Rounded.Add, contentDescription = null) },
) {
  Surface(
    onClick = onClick,
    shape = CircleShape,
    color = MaterialTheme.colorScheme.primary,
    contentColor = MaterialTheme.colorScheme.onPrimary,
    shadowElevation = 6.dp,
    modifier = modifier.size(56.dp),
  ) {
    Box(contentAlignment = Alignment.Center) { content() }
  }
}

/** Knöpfe ohne Tooltip: zeigt nur den Inhalt, etwa den Filterknopf von „Alle Apps“. */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TooltipBox(
  positionProvider: PopupPositionProvider,
  tooltip: @Composable TooltipScope.() -> Unit,
  state: TooltipState,
  modifier: Modifier = Modifier,
  content: @Composable () -> Unit,
) {
  Box(modifier) { content() }
}

private const val DISABLED_ALPHA = 0.38f
