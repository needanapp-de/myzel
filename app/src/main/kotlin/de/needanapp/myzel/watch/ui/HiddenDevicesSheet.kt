package de.needanapp.myzel.watch.ui

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import dagger.hilt.android.EntryPointAccessors
import de.needanapp.design.components.OptionsSheet
import de.needanapp.design.components.SheetAction
import de.needanapp.myzel.watch.HiddenDevice
import de.needanapp.myzel.watch.WatchAppListEntryPoint
import org.fdroid.R

/** Für die Einstellungen: die Geräte, die die Einrichtung ausblendet, weil sie keine Uhr sind. */
class HiddenDevicesHandle(
  val devices: List<HiddenDevice>,
  /** zeigt das Gerät mit dieser Seriennummer wieder in der Einrichtung */
  val show: (String) -> Unit,
  val showAll: () -> Unit,
)

/** Siehe [de.needanapp.myzel.watch.WatchStore.hiddenDevices]; in der Vorschau leer. */
@Composable
fun rememberHiddenDevices(): HiddenDevicesHandle {
  if (LocalInspectionMode.current) return HiddenDevicesHandle(emptyList(), {}, {})
  val context = LocalContext.current
  val store = remember {
    EntryPointAccessors.fromApplication(context, WatchAppListEntryPoint::class.java).watchStore()
  }
  val devices by store.hiddenDevices.collectAsState()
  return HiddenDevicesHandle(devices, store::showDevice, store::showAllDevices)
}

/**
 * Blendet einzelne oder alle ausgeblendeten Geräte wieder ein; schließt sich, wenn keins mehr da
 * ist.
 */
@Composable
fun HiddenDevicesSheet(handle: HiddenDevicesHandle, onDismiss: () -> Unit) {
  OptionsSheet(
    onDismiss = onDismiss,
    title = stringResource(R.string.myzel_settings_watch_show_title),
  ) {
    handle.devices.forEach { device ->
      SheetAction(
        icon = Icons.Outlined.Visibility,
        label = device.name.ifEmpty { device.serial },
        summary = device.serial.takeIf { device.name.isNotEmpty() },
        onClick = {
          handle.show(device.serial)
          if (handle.devices.size <= 1) onDismiss()
        },
      )
    }
    if (handle.devices.size > 1) {
      SheetAction(
        icon = Icons.Outlined.Visibility,
        label = stringResource(R.string.myzel_settings_watch_show_all),
        onClick = {
          handle.showAll()
          onDismiss()
        },
      )
    }
  }
}
