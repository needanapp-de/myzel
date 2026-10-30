package de.needanapp.myzel.ui.compat

import android.text.format.Formatter
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SignalCellularAlt
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import de.needanapp.design.components.ContentDialog
import org.fdroid.R

/**
 * Ersatz für org.fdroid.ui.utils.MeteredConnectionDialog mit derselben Signatur, über
 * myzel/tools/apply-imports.py eingebunden: der Dialog des Design-Pakets mit Symbol, „Nicht mehr
 * anzeigen“ als ganze antippbare Zeile und „Trotzdem laden“ statt eines roten „Download“.
 */
@Composable
fun MeteredConnectionDialog(
  numBytes: Long?,
  onConfirm: (Boolean) -> Unit,
  onDismiss: () -> Unit,
) {
  var dontShowAgain by remember { mutableStateOf(false) }
  ContentDialog(
    title = stringResource(R.string.dialog_metered_title),
    confirmLabel = stringResource(R.string.myzel_metered_confirm),
    onConfirm = {
      onDismiss()
      onConfirm(dontShowAgain)
    },
    onDismiss = onDismiss,
    icon = Icons.Rounded.SignalCellularAlt,
  ) {
    Text(
      if (numBytes == null) {
        stringResource(R.string.dialog_metered_text_no_size)
      } else {
        val size = Formatter.formatFileSize(LocalContext.current, numBytes)
        stringResource(R.string.dialog_metered_text, size)
      }
    )
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier =
        Modifier.padding(top = 12.dp).fillMaxWidth().toggleable(
          value = dontShowAgain,
          role = Role.Checkbox,
        ) {
          dontShowAgain = it
        },
    ) {
      Checkbox(checked = dontShowAgain, onCheckedChange = null)
      Spacer(Modifier.width(12.dp))
      Text(stringResource(R.string.dialog_metered_dont_show))
    }
  }
}
