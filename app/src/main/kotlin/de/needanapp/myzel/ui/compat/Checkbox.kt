package de.needanapp.myzel.ui.compat

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.CheckboxColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import de.needanapp.design.components.signalCheckboxColors

/**
 * Ersatz für androidx.compose.material3.Checkbox mit derselben Signatur, in den Dateien von F-Droid
 * über myzel/tools/apply-imports.py eingebunden: das Kästchen in der Signalfarbe wie die Schalter.
 */
@Composable
fun Checkbox(
  checked: Boolean,
  onCheckedChange: ((Boolean) -> Unit)?,
  modifier: Modifier = Modifier,
  enabled: Boolean = true,
  colors: CheckboxColors = signalCheckboxColors(),
  interactionSource: MutableInteractionSource? = null,
) =
  androidx.compose.material3.Checkbox(
    checked,
    onCheckedChange,
    modifier,
    enabled,
    colors,
    interactionSource,
  )
