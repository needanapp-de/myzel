package de.needanapp.myzel.ui.compat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import de.needanapp.design.theme.LocalDesignShapes
import de.needanapp.design.theme.emphasizedSpec
import org.fdroid.R

/**
 * Ersatz für org.fdroid.ui.utils.ExpandableSection mit derselben Signatur; App-Details, Versionen
 * und Paketquellen-Details binden ihn über den Import ein (myzel/tools/apply-imports.py). Wie die
 * aufklappbaren Gruppen des Design-Pakets: die Kopfzeile ist eine Karte mit Symbol und Pfeil,
 * aufgeklappt schließt der Inhalt als Karte unten an.
 */
@Composable
fun ExpandableSection(
  icon: Painter?,
  title: String,
  modifier: Modifier = Modifier,
  initiallyExpanded: Boolean = LocalInspectionMode.current,
  content: @Composable () -> Unit,
) {
  var expanded by rememberSaveable { mutableStateOf(initiallyExpanded) }
  val shapes = LocalDesignShapes.current
  val progress by animateFloatAsState(if (expanded) 1f else 0f, emphasizedSpec(), label = "section")
  val bottom = lerp(shapes.group, shapes.inner, progress)
  val arrowDirection = if (LocalLayoutDirection.current == LayoutDirection.Rtl) 1f else -1f
  val actionLabel = stringResource(if (expanded) R.string.collapse else R.string.expand)
  Column(modifier = modifier.fillMaxWidth()) {
    Surface(
      shape =
        RoundedCornerShape(
          topStart = shapes.group,
          topEnd = shapes.group,
          bottomStart = bottom,
          bottomEnd = bottom,
        ),
      color = MaterialTheme.colorScheme.surfaceContainer,
      modifier = Modifier.fillMaxWidth(),
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier =
          Modifier.clickable(onClickLabel = actionLabel) { expanded = !expanded }
            .heightIn(min = 56.dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
      ) {
        if (icon != null) {
          Icon(
            painter = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp),
          )
          Spacer(Modifier.width(16.dp))
        }
        Text(
          text = title,
          style = MaterialTheme.typography.bodyLarge,
          modifier = Modifier.weight(1f).semantics { heading() },
        )
        Spacer(Modifier.width(12.dp))
        Icon(
          Icons.Filled.KeyboardArrowDown,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.rotate(arrowDirection * 90f * (1f - progress)),
        )
      }
    }
    AnimatedVisibility(
      visible = expanded,
      enter = expandVertically(emphasizedSpec()) + fadeIn(emphasizedSpec()),
      exit = shrinkVertically(emphasizedSpec()) + fadeOut(emphasizedSpec()),
      modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    ) {
      Surface(
        shape =
          RoundedCornerShape(
            topStart = shapes.inner,
            topEnd = shapes.inner,
            bottomStart = shapes.group,
            bottomEnd = shapes.group,
          ),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = Modifier.fillMaxWidth().padding(top = shapes.rowGap),
      ) {
        Box(Modifier.padding(horizontal = 8.dp, vertical = 12.dp)) { content() }
      }
    }
  }
}
