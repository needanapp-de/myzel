package de.needanapp.myzel.ui.compat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ListItemColors
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import de.needanapp.design.components.designCard
import de.needanapp.design.theme.LocalDesignShapes
import de.needanapp.design.theme.isNeedanappDesign

/**
 * Ersatz für androidx.compose.material3.ListItem mit derselben Signatur; Listen von F-Droid
 * (Paketquellen, App-Listen, Meine Apps) binden ihn über den Import ein
 * (myzel/tools/apply-imports.py). Jede Zeile wird eine abgerundete Karte wie die Zeilen der
 * Einstellungen, mit dem kleinen Abstand des Design-Pakets dazwischen; im Design needanapp eine
 * Karte mit Rand und Schatten wie die Apps in „Meine Apps“.
 *
 * Von [colors] zählt nur, ob F-Droid die Zeile als ausgewählt hervorhebt (surfaceVariant, in der
 * zweispaltigen Ansicht großer Bildschirme); die Farben selbst kommen aus dem Design.
 */
@Composable
fun ListItem(
  headlineContent: @Composable () -> Unit,
  modifier: Modifier = Modifier,
  overlineContent: @Composable (() -> Unit)? = null,
  supportingContent: @Composable (() -> Unit)? = null,
  leadingContent: @Composable (() -> Unit)? = null,
  trailingContent: @Composable (() -> Unit)? = null,
  colors: ListItemColors = ListItemDefaults.colors(),
  tonalElevation: Dp = ListItemDefaults.Elevation,
  shadowElevation: Dp = ListItemDefaults.Elevation,
) {
  val shapes = LocalDesignShapes.current
  val scheme = MaterialTheme.colorScheme
  val isSelected = colors.containerColor == scheme.surfaceVariant
  // Zeilen kennen ihre Stellung in der Liste nicht, daher ein Radius zwischen außen und innen
  val shape = RoundedCornerShape((shapes.group + shapes.inner) / 2)
  val container = if (isSelected) scheme.secondaryContainer else scheme.surfaceContainer
  val card =
    if (isNeedanappDesign) {
      Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        .designCard()
        .then(if (isSelected) Modifier.background(container) else Modifier)
    } else {
      Modifier.padding(horizontal = 16.dp, vertical = shapes.rowGap / 2)
        .clip(shape)
        .background(container)
    }
  androidx.compose.material3.ListItem(
    headlineContent = headlineContent,
    // Karte vor den Modifiern von F-Droid: die Welle beim Tippen bleibt in ihr, und eigener
    // Innenabstand mancher Listen rückt nur den Inhalt ein
    modifier = card.then(modifier),
    overlineContent = overlineContent,
    supportingContent = supportingContent,
    leadingContent = leadingContent,
    trailingContent = trailingContent,
    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    tonalElevation = tonalElevation,
    shadowElevation = shadowElevation,
  )
}
