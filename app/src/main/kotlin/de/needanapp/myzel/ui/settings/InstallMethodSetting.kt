package de.needanapp.myzel.ui.settings

import android.widget.Toast
import android.widget.Toast.LENGTH_LONG
import android.widget.Toast.LENGTH_SHORT
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.InstallMobile
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import dagger.hilt.android.EntryPointAccessors
import de.needanapp.design.components.ListRow
import de.needanapp.design.components.OptionsSheet
import de.needanapp.design.components.RowPosition
import de.needanapp.design.components.SheetAction
import de.needanapp.myzel.install.InstallAccess
import de.needanapp.myzel.install.InstallEntryPoint
import de.needanapp.myzel.install.InstallMethod
import kotlinx.coroutines.launch
import me.zhanghai.compose.preference.rememberPreferenceState
import org.fdroid.R

/**
 * Wie Myzel installiert: Paketinstaller, Root oder Shizuku. Root und Shizuku werden erst
 * eingestellt, wenn der Zugriff klappt; dabei erscheint die Abfrage von Magisk bzw. Shizuku.
 */
@Composable
internal fun InstallMethodRow(position: RowPosition) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  var key by rememberPreferenceState(InstallMethod.PREF_KEY, InstallMethod.DEFAULT.key)
  val current = InstallMethod.fromKey(key)
  var choosing by rememberSaveable { mutableStateOf(false) }
  // die Methode, deren Zugriff gerade geprüft wird
  var checking by remember { mutableStateOf<InstallMethod?>(null) }

  ListRow(
    position,
    stringResource(R.string.myzel_install_method),
    summary =
      if (checking != null) stringResource(R.string.myzel_install_checking)
      else stringResource(current.label),
    icon = Icons.Outlined.InstallMobile,
    enabled = checking == null,
    onClick = { choosing = true },
  )

  if (choosing) {
    OptionsSheet(
      onDismiss = { choosing = false },
      title = stringResource(R.string.myzel_install_method),
    ) {
      InstallMethod.entries.forEach { method ->
        val selected = method == current
        SheetAction(
          icon =
            if (selected) Icons.Outlined.RadioButtonChecked
            else Icons.Outlined.RadioButtonUnchecked,
          label = stringResource(method.label),
          summary = stringResource(method.summary),
          tint =
            if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
          onClick = {
            choosing = false
            if (method == InstallMethod.SESSION || selected) {
              key = method.key
              return@SheetAction
            }
            checking = method
            scope.launch {
              val installer =
                EntryPointAccessors.fromApplication(context, InstallEntryPoint::class.java)
                  .privilegedInstaller()
              when (val access = installer.requestAccess(method)) {
                InstallAccess.Granted -> {
                  key = method.key
                  val message =
                    context.getString(
                      R.string.myzel_install_enabled,
                      context.getString(method.label),
                    )
                  Toast.makeText(context, message, LENGTH_SHORT).show()
                }
                is InstallAccess.Denied ->
                  Toast.makeText(context, access.reason, LENGTH_LONG).show()
              }
              checking = null
            }
          },
        )
      }
    }
  }
}
