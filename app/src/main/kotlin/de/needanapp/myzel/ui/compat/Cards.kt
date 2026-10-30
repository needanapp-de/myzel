package de.needanapp.myzel.ui.compat

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardColors
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CardElevation
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import de.needanapp.design.components.designCard
import de.needanapp.design.theme.LocalDesignColors
import de.needanapp.design.theme.LocalDesignShapes
import de.needanapp.design.theme.isNeedanappDesign

// Ersatz für Karte und Trennlinie der Kategorien auf „Entdecken“ (categories/CategoryList.kt), über
// den Import eingebunden (myzel/tools/apply-imports.py): eine Gruppe wie in den Einstellungen statt
// einer umrandeten Karte mit Linien.

/**
 * Ersatz für androidx.compose.material3.OutlinedCard mit derselben Signatur: ohne Rand, in der
 * Kartenfarbe und mit dem äußeren Radius des Design-Pakets (im Design needanapp mit Rand und
 * Schatten). Zeilen darin, die mit der Flächenfarbe gemalt werden (ListItem), übernehmen die
 * Kartenfarbe.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun OutlinedCard(
  modifier: Modifier = Modifier,
  shape: Shape = CardDefaults.outlinedShape,
  colors: CardColors = CardDefaults.outlinedCardColors(),
  elevation: CardElevation = CardDefaults.outlinedCardElevation(),
  border: BorderStroke = CardDefaults.outlinedCardBorder(),
  content: @Composable ColumnScope.() -> Unit,
) {
  val scheme = MaterialTheme.colorScheme
  if (isNeedanappDesign) {
    val card = LocalDesignColors.current.card
    Column(modifier.designCard()) {
      MaterialTheme(colorScheme = scheme.copy(surface = card)) { content() }
    }
    return
  }
  Card(
    modifier = modifier,
    shape = RoundedCornerShape(LocalDesignShapes.current.group),
    colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainer),
  ) {
    val scope = this
    MaterialTheme(colorScheme = scheme.copy(surface = scheme.surfaceContainer)) { scope.content() }
  }
}

/**
 * Ersatz für androidx.compose.material3.HorizontalDivider mit derselben Signatur: statt einer Linie
 * der kleine Abstand zwischen den Zeilen einer Gruppe, in der Hintergrundfarbe.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun HorizontalDivider(
  modifier: Modifier = Modifier,
  thickness: Dp = DividerDefaults.Thickness,
  color: Color = DividerDefaults.color,
) {
  Spacer(
    modifier
      .fillMaxWidth()
      .height(LocalDesignShapes.current.rowGap)
      .background(MaterialTheme.colorScheme.background)
  )
}
