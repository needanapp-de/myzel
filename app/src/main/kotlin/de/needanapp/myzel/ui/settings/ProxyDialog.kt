package de.needanapp.myzel.ui.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import de.needanapp.design.components.ContentDialog
import java.net.InetSocketAddress
import org.fdroid.R

/**
 * Fragt nach dem SOCKS-Proxy im Format Host:Port, wie org.fdroid.ui.settings.preferenceProxy. Ein
 * leeres Feld schaltet den Proxy ab.
 */
@Composable
internal fun ProxyDialog(initialValue: String, onConfirm: (String) -> Unit, onDismiss: () -> Unit) {
  var value by rememberSaveable { mutableStateOf(initialValue) }
  val isValid = value.isBlank() || isProxyValid(value.trim())
  val focusRequester = remember { FocusRequester() }
  LaunchedEffect(Unit) { focusRequester.requestFocus() }
  fun confirm() {
    if (isValid) {
      onConfirm(value.trim())
      onDismiss()
    }
  }
  ContentDialog(
    title = stringResource(R.string.pref_proxy_title),
    confirmLabel = stringResource(R.string.ok),
    onConfirm = ::confirm,
    onDismiss = onDismiss,
    confirmEnabled = isValid,
  ) {
    OutlinedTextField(
      value = value,
      onValueChange = { value = it },
      singleLine = true,
      isError = !isValid,
      supportingText = {
        Text(stringResource(if (isValid) R.string.pref_proxy_hint else R.string.pref_proxy_error))
      },
      trailingIcon = {
        if (value.isNotBlank()) {
          IconButton(onClick = { value = "" }) {
            Icon(Icons.Default.Clear, stringResource(R.string.clear))
          }
        }
      },
      keyboardOptions =
        KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
      keyboardActions = KeyboardActions(onDone = { confirm() }),
      modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
    )
  }
}

// dieselbe Prüfung wie in org.fdroid.ui.settings.PreferenceProxy (dort privat)
internal fun isProxyValid(proxyStr: String): Boolean =
  try {
    val (host, port) = proxyStr.split(':')
    InetSocketAddress.createUnresolved(host, port.toInt())
    true
  } catch (_: Exception) {
    false
  }
