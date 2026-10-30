package de.needanapp.myzel.ui.compat

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.SwitchColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import de.needanapp.design.components.deviceSwitchColors

/**
 * Ersatz für androidx.compose.material3.Switch mit derselben Signatur, in den Dateien von F-Droid
 * über myzel/tools/apply-imports.py eingebunden: Schalter im Stil „Myzel“ (Signalfarbe, heller
 * Daumen) wie die Schalter des Design-Pakets.
 */
@Composable
fun Switch(
  checked: Boolean,
  onCheckedChange: ((Boolean) -> Unit)?,
  modifier: Modifier = Modifier,
  thumbContent: (@Composable () -> Unit)? = null,
  enabled: Boolean = true,
  colors: SwitchColors = deviceSwitchColors(),
  interactionSource: MutableInteractionSource? = null,
) =
  androidx.compose.material3.Switch(
    checked,
    onCheckedChange,
    modifier,
    thumbContent,
    enabled,
    colors,
    interactionSource,
  )
