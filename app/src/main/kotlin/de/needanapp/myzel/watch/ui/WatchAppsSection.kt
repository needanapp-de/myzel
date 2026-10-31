package de.needanapp.myzel.watch.ui

import android.content.pm.PackageManager
import android.text.format.DateUtils
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import de.needanapp.design.components.SignalButton
import de.needanapp.design.components.SignalCircularProgressIndicator
import de.needanapp.design.components.SignalLinearProgressIndicator
import de.needanapp.myzel.ui.apps.NameWithPills
import de.needanapp.myzel.ui.apps.RowIcon
import de.needanapp.myzel.ui.apps.SectionTitle
import de.needanapp.myzel.ui.apps.UpdatePill
import de.needanapp.myzel.ui.apps.selectedColors
import de.needanapp.myzel.ui.compat.ListItem
import de.needanapp.myzel.watch.WatchAppItem
import de.needanapp.myzel.watch.WatchAppsHandle
import de.needanapp.myzel.watch.WatchInstallProblem
import de.needanapp.myzel.watch.WatchInstallState
import de.needanapp.myzel.watch.WatchReach
import de.needanapp.myzel.watch.WearUpdateState
import de.needanapp.myzel.watch.isRunning
import org.fdroid.R
import org.fdroid.ui.utils.VersionLineWithSize

/**
 * Der Abschnitt „Auf der Uhr“ unter den installierten Apps in „Meine Apps“: die Apps aus den
 * Paketquellen, die zuletzt auf der Uhr waren, mit Updates. Myzel fragt die Uhr nur auf Wunsch
 * („Abfragen“), denn Myzel Wear antwortet nur, solange es auf der Uhr offen ist, und jede Anfrage
 * steht im Verlauf der Uhr.
 */
fun LazyListScope.watchAppsSection(handle: WatchAppsHandle, onAppItemClick: (String) -> Unit) {
  item(key = "W", contentType = "header") { WatchAppsHeader(handle) }
  item(key = "W-note", contentType = "watchNote") { WatchAppsNote(handle) }
  items(items = handle.items, key = { "W-${it.packageName}" }, contentType = { "W" }) { app ->
    WatchAppRow(
      app,
      handle.states[app.packageName],
      Modifier.animateItem().clickable { onAppItemClick(app.packageName) },
    )
  }
}

@Composable
private fun WatchAppsHeader(handle: WatchAppsHandle) {
  val busy = handle.checking || handle.states.values.any { it.isRunning }
  SectionTitle(
    stringResource(R.string.myzel_my_apps_watch),
    note =
      if (handle.checkedAt > 0) {
        stringResource(
          R.string.myzel_my_apps_watch_checked,
          handle.watch.name,
          checkedTime(handle.checkedAt),
        )
      } else {
        stringResource(R.string.myzel_my_apps_watch_never, handle.watch.name)
      },
    noteIsError = false,
  ) {
    if (busy) {
      SignalCircularProgressIndicator(Modifier.padding(end = 12.dp).size(20.dp), strokeWidth = 2.dp)
    } else {
      TextButton(onClick = handle.check) {
        Text(stringResource(R.string.myzel_my_apps_watch_check))
      }
    }
  }
}

/** Uhrzeit der letzten Abfrage, an einem anderen Tag mit Datum (auf Deutsch TT.MM.JJJJ). */
@Composable
private fun checkedTime(millis: Long): String {
  val flags =
    if (DateUtils.isToday(millis)) DateUtils.FORMAT_SHOW_TIME
    else
      DateUtils.FORMAT_SHOW_DATE or
        DateUtils.FORMAT_SHOW_TIME or
        DateUtils.FORMAT_NUMERIC_DATE or
        DateUtils.FORMAT_SHOW_YEAR
  return DateUtils.formatDateTime(LocalContext.current, millis, flags)
}

/** Hinweis unter der Überschrift und der Knopf für alle Updates. */
@Composable
private fun WatchAppsNote(handle: WatchAppsHandle) {
  val scheme = MaterialTheme.colorScheme
  val reach = handle.reach
  val updates = handle.items.count { it.update != null }
  val busy = handle.checking || handle.states.values.any { it.isRunning }
  Column(Modifier.padding(horizontal = 16.dp)) {
    val update = handle.wearUpdate
    if (update != null && (update !is WearUpdateState.Failed || reach is WatchReach.Unreachable)) {
      WearUpdateNote(update, Modifier.padding(bottom = 8.dp))
    }
    val note =
      when {
        handle.checking -> null
        reach is WatchReach.Unreachable -> stringResource(reach.reason.text())
        handle.checkedAt == 0L -> stringResource(R.string.myzel_my_apps_watch_hint)
        handle.items.isEmpty() -> stringResource(R.string.myzel_my_apps_watch_empty)
        else -> null
      }
    if (note != null) {
      Text(
        text = note,
        style = MaterialTheme.typography.bodyMedium,
        color = if (reach is WatchReach.Unreachable) scheme.error else scheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
      )
    }
    if (updates > 0 && !busy) {
      SignalButton(onClick = handle.updateAll, modifier = Modifier.padding(bottom = 8.dp)) {
        Text(stringResource(R.string.myzel_my_apps_watch_update_all))
      }
    }
  }
}

@Composable
private fun WatchAppRow(app: WatchAppItem, state: WatchInstallState?, modifier: Modifier) {
  val scheme = MaterialTheme.colorScheme
  val update = app.update
  ListItem(
    leadingContent = { RowIcon(app.iconModel) },
    headlineContent = { NameWithPills(app.name) { if (update != null) UpdatePill() } },
    supportingContent = {
      when {
        // die Gründe einer nicht erreichbaren Uhr stehen über der Liste
        state != null && state !is WatchInstallState.Unreachable ->
          Text(
            watchStateText(state),
            color = if (state is WatchInstallState.Failed) scheme.error else Color.Unspecified,
          )
        update != null ->
          VersionLineWithSize(app.installed.versionName, update.versionName, update.size)
        app.otherSigner ->
          Text(
            "${app.installed.versionName} · " +
              stringResource(R.string.myzel_my_apps_watch_other_signer),
            color = scheme.error,
          )
        else -> Text(app.installed.versionName)
      }
    },
    colors = selectedColors(isSelected = false),
    modifier = modifier,
  )
}

/**
 * Stand des Updates von Myzel Wear (siehe [de.needanapp.myzel.watch.WatchBridge.wearUpdate]), mit
 * Fortschrittsbalken, solange es läuft.
 */
@Composable
internal fun WearUpdateNote(update: WearUpdateState, modifier: Modifier = Modifier) {
  val scheme = MaterialTheme.colorScheme
  Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
    Text(
      wearUpdateText(update),
      style = MaterialTheme.typography.bodyMedium,
      color = if (update is WearUpdateState.Failed) scheme.error else scheme.onSurface,
    )
    when (update) {
      is WearUpdateState.Downloading ->
        SignalLinearProgressIndicator(
          progress = { update.fraction },
          trackColor = scheme.outlineVariant,
          modifier = Modifier.fillMaxWidth(),
        )
      is WearUpdateState.Sending ->
        SignalLinearProgressIndicator(
          progress = { update.fraction },
          trackColor = scheme.outlineVariant,
          modifier = Modifier.fillMaxWidth(),
        )
      is WearUpdateState.Confirm,
      WearUpdateState.Restarting ->
        SignalLinearProgressIndicator(
          trackColor = scheme.outlineVariant,
          modifier = Modifier.fillMaxWidth(),
        )
      is WearUpdateState.Failed -> {}
    }
  }
}

@Composable
internal fun wearUpdateText(update: WearUpdateState): String =
  when (update) {
    is WearUpdateState.Downloading ->
      stringResource(R.string.myzel_watch_wear_update_downloading, (update.fraction * 100).toInt())
    is WearUpdateState.Sending ->
      stringResource(R.string.myzel_watch_wear_update_sending, (update.fraction * 100).toInt())
    is WearUpdateState.Confirm ->
      stringResource(
        if (update.notification) R.string.myzel_watch_wear_update_confirm_notification
        else R.string.myzel_watch_wear_update_confirm
      )
    WearUpdateState.Restarting -> stringResource(R.string.myzel_watch_wear_update_restarting)
    is WearUpdateState.Failed ->
      listOfNotNull(stringResource(R.string.myzel_watch_wear_update_failed), update.message)
        .joinToString("\n")
  }

/**
 * Name der App, die eine App auf der Uhr installiert hat ([installer] ist ihr Paketname), so wie
 * das Handy sie kennt, etwa „Google Play Store“.
 */
@Composable
internal fun watchInstallerLabel(installer: String): String {
  val context = LocalContext.current
  return when {
    installer.isEmpty() -> stringResource(R.string.myzel_details_watch_installer_unknown)
    installer == context.packageName -> stringResource(R.string.app_name)
    else ->
      try {
        val pm = context.packageManager
        pm.getApplicationInfo(installer, 0).loadLabel(pm).toString()
      } catch (_: PackageManager.NameNotFoundException) {
        installer
      }
  }
}

/** Was [state] dem Nutzer sagt, für die App-Seite und „Meine Apps“. */
@Composable
internal fun watchStateText(state: WatchInstallState): String =
  when (state) {
    WatchInstallState.Connecting -> stringResource(R.string.myzel_details_watch_connecting)
    WatchInstallState.WaitingForWatch ->
      stringResource(R.string.myzel_details_watch_waiting_for_watch)
    is WatchInstallState.Downloading ->
      stringResource(R.string.myzel_details_status_downloading, (state.fraction * 100).toInt())
    is WatchInstallState.Sending ->
      stringResource(R.string.myzel_details_watch_sending, (state.fraction * 100).toInt())
    is WatchInstallState.Confirm ->
      stringResource(
        if (state.notification) R.string.myzel_details_watch_confirm_notification
        else R.string.myzel_details_watch_confirm
      )
    WatchInstallState.Removing -> stringResource(R.string.myzel_details_watch_removing)
    is WatchInstallState.ConfirmRemoval ->
      stringResource(
        if (state.notification) R.string.myzel_details_watch_confirm_removal_notification
        else R.string.myzel_details_watch_confirm_removal
      )
    is WatchInstallState.Unconfirmed ->
      stringResource(
        if (state.removal) R.string.myzel_details_watch_unconfirmed_removal
        else R.string.myzel_details_watch_unconfirmed
      )
    is WatchInstallState.Unreachable -> stringResource(state.reason.text())
    is WatchInstallState.Failed -> {
      val text =
        stringResource(
          when (state.problem) {
            WatchInstallProblem.DOWNLOAD -> R.string.myzel_details_watch_failed_download
            WatchInstallProblem.CHECK -> R.string.myzel_details_watch_failed_check
            WatchInstallProblem.INSTALL -> R.string.myzel_details_watch_failed_install
            WatchInstallProblem.UNINSTALL -> R.string.myzel_details_watch_failed_uninstall
          }
        )
      listOfNotNull(text, state.detail).joinToString("\n")
    }
  }
