package de.needanapp.myzel.ui.settings

import android.content.Intent
import android.net.Uri
import android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS
import android.provider.Settings.EXTRA_APP_PACKAGE
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Lan
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Screenshot
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.SystemSecurityUpdate
import androidx.compose.material.icons.outlined.SystemSecurityUpdateWarning
import androidx.compose.material.icons.outlined.Update
import androidx.compose.material.icons.outlined.UpdateDisabled
import androidx.compose.material.icons.outlined.Upload
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.VpnLock
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.needanapp.design.components.ListRow
import de.needanapp.design.components.OptionsSheet
import de.needanapp.design.components.RowPosition
import de.needanapp.design.components.SectionState
import de.needanapp.design.components.SettingsGroup
import de.needanapp.design.components.SheetAction
import de.needanapp.design.components.SwitchRow
import de.needanapp.design.components.rememberSectionState
import de.needanapp.design.settings.AppearanceSettings
import de.needanapp.design.theme.DesignStyle
import de.needanapp.design.theme.LocalThemeSettings
import de.needanapp.design.theme.ThemeMode
import de.needanapp.myzel.ui.MyzelAccent
import de.needanapp.myzel.ui.compat.AppIconDialog
import de.needanapp.myzel.ui.compat.BackButton
import de.needanapp.myzel.ui.compat.Scaffold
import de.needanapp.myzel.ui.compat.TopAppBar
import de.needanapp.myzel.watch.ui.HiddenDevicesSheet
import de.needanapp.myzel.watch.ui.rememberHiddenDevices
import java.lang.System.currentTimeMillis
import java.util.concurrent.TimeUnit.HOURS
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import me.zhanghai.compose.preference.MapPreferences
import me.zhanghai.compose.preference.ProvidePreferenceLocals
import me.zhanghai.compose.preference.rememberPreferenceState
import org.fdroid.R
import org.fdroid.settings.SettingsConstants.AutoUpdateValues
import org.fdroid.settings.SettingsConstants.AutoUpdateValues.Always
import org.fdroid.settings.SettingsConstants.AutoUpdateValues.Never
import org.fdroid.settings.SettingsConstants.AutoUpdateValues.OnlyWhenOpenApp
import org.fdroid.settings.SettingsConstants.AutoUpdateValues.OnlyWifi
import org.fdroid.settings.SettingsConstants.MirrorChooserValues
import org.fdroid.settings.SettingsConstants.PREF_DEFAULT_APP_AUTO_UPDATES
import org.fdroid.settings.SettingsConstants.PREF_DEFAULT_MIRROR_CHOOSER
import org.fdroid.settings.SettingsConstants.PREF_DEFAULT_PREVENT_SCREENSHOTS
import org.fdroid.settings.SettingsConstants.PREF_DEFAULT_PROXY
import org.fdroid.settings.SettingsConstants.PREF_DEFAULT_REPO_AUTO_UPDATES
import org.fdroid.settings.SettingsConstants.PREF_DEFAULT_THEME
import org.fdroid.settings.SettingsConstants.PREF_KEY_APP_AUTO_UPDATES
import org.fdroid.settings.SettingsConstants.PREF_KEY_MIRROR_CHOOSER
import org.fdroid.settings.SettingsConstants.PREF_KEY_PREVENT_SCREENSHOTS
import org.fdroid.settings.SettingsConstants.PREF_KEY_PROXY
import org.fdroid.settings.SettingsConstants.PREF_KEY_REPO_AUTO_UPDATES
import org.fdroid.settings.SettingsConstants.PREF_KEY_THEME
import org.fdroid.settings.SettingsConstants.PREF_USE_DNS_CACHE
import org.fdroid.settings.SettingsConstants.PREF_USE_DNS_CACHE_DEFAULT
import org.fdroid.settings.toAutoUpdateValue
import org.fdroid.settings.toMirrorChooserValue
import org.fdroid.ui.FDroidContent
import org.fdroid.ui.settings.AppIcon
import org.fdroid.ui.settings.SettingsModel
import org.fdroid.ui.utils.asRelativeTimeString
import org.fdroid.ui.utils.launchSafe
import org.fdroid.ui.utils.startActivitySafe
import org.fdroid.utils.getLogName

private val SECTION_IDS = listOf("updates", "network", "privacy", "watch", "backup")

/**
 * Ersatz für org.fdroid.ui.settings.Settings mit derselben Signatur; Main.kt bindet ihn über den
 * Import ein (myzel/tools/apply-imports.py). Dieselben Einstellungen wie bei F-Droid, aber als
 * gruppierte Karten des Design-Pakets. Design, Dynamische Farben und Sprache von F-Droid sind durch
 * die Unterseite „Darstellung“ des Design-Pakets ersetzt.
 *
 * Bei einem Update von F-Droid mit org.fdroid.ui.settings.Settings vergleichen, ob Einstellungen
 * dazugekommen oder weggefallen sind.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun Settings(
  model: SettingsModel,
  onChangeAppIcon: (AppIcon) -> Unit,
  onSaveLogcat: (Uri?) -> Unit,
  onBackClicked: () -> Unit,
) {
  var showAppearance by rememberSaveable { mutableStateOf(false) }
  BackHandler(enabled = showAppearance) { showAppearance = false }
  Scaffold(
    topBar = {
      TopAppBar(
        navigationIcon = {
          BackButton(onClick = { if (showAppearance) showAppearance = false else onBackClicked() })
        },
        title = {
          val title =
            if (showAppearance) de.needanapp.design.R.string.nd_appearance
            else R.string.menu_settings
          Text(stringResource(title), maxLines = 1)
        },
      )
    }
  ) { paddingValues ->
    ProvidePreferenceLocals(model.prefsFlow) {
      SyncNightMode()
      if (showAppearance) {
        AppearanceSettings(
          LocalThemeSettings.current,
          contentPadding = paddingValues,
          modifier = Modifier.fillMaxSize(),
          defaultAccent = MyzelAccent,
          styles = listOf(DesignStyle.MYZEL, DesignStyle.NEEDANAPP),
        )
      } else {
        // eine Sektion für alle Gruppen, damit sie sich als Akkordeon gegenseitig schließen können
        val sections = rememberSectionState(SECTION_IDS)
        LazyColumn(contentPadding = paddingValues, modifier = Modifier.fillMaxSize()) {
          item { GeneralGroup(model, onChangeAppIcon) { showAppearance = true } }
          item { UpdatesGroup(model, sections) }
          item { NetworkGroup(sections) }
          item { PrivacyGroup(sections) }
          item { WatchGroup(sections) }
          item { BackupGroup(sections) }
          item { DebugGroup(onSaveLogcat) }
        }
      }
    }
  }
}

/**
 * Hell oder dunkel stellt das Design-Paket ein; F-Droid stellt danach den Nachtmodus von AppCompat
 * ein (beim Start in App.kt und bei Änderungen im SettingsViewModel). Damit Fensterhintergrund und
 * Systemleisten zum Design passen, folgt die Einstellung von F-Droid der des Design-Pakets.
 */
@Composable
private fun SyncNightMode() {
  val mode = LocalThemeSettings.current.mode
  var theme by rememberPreferenceState(PREF_KEY_THEME, PREF_DEFAULT_THEME)
  LaunchedEffect(mode) {
    val wanted =
      when (mode) {
        ThemeMode.SYSTEM -> "followSystem"
        ThemeMode.LIGHT -> "light"
        else -> "dark"
      }
    if (theme != wanted) theme = wanted
  }
}

@Composable
private fun GeneralGroup(
  model: SettingsModel,
  onChangeAppIcon: (AppIcon) -> Unit,
  onAppearance: () -> Unit,
) {
  val context = LocalContext.current
  var showAppIconDialog by rememberSaveable { mutableStateOf(false) }
  val currentAppIcon = model.currentAppIconFlow.collectAsStateWithLifecycle().value
  SettingsGroup(null) {
    row {
      ListRow(
        it,
        stringResource(de.needanapp.design.R.string.nd_appearance),
        summary = stringResource(R.string.myzel_appearance_summary),
        icon = Icons.Outlined.Palette,
        onClick = onAppearance,
      )
    }
    row {
      ListRow(
        it,
        stringResource(R.string.notification_title),
        summary = stringResource(R.string.notification_summary),
        icon = Icons.Outlined.Notifications,
        onClick = {
          val intent =
            Intent(ACTION_APP_NOTIFICATION_SETTINGS).apply {
              putExtra(EXTRA_APP_PACKAGE, context.packageName)
            }
          context.startActivitySafe(intent)
        },
      )
    }
    row {
      ListRow(
        it,
        stringResource(R.string.icon_title),
        summary = stringResource(currentAppIcon.labelRes),
        icon = Icons.Outlined.Apps,
        onClick = { showAppIconDialog = true },
      )
    }
  }
  if (showAppIconDialog)
    AppIconDialog(
      currentAppIcon = currentAppIcon,
      onChangeIcon = { appIcon ->
        onChangeAppIcon(appIcon)
        showAppIconDialog = false
      },
      onDismissRequest = { showAppIconDialog = false },
    )
}

@Composable
private fun UpdatesGroup(model: SettingsModel, sections: SectionState) {
  val repoUpdates =
    rememberPreferenceState(PREF_KEY_REPO_AUTO_UPDATES, PREF_DEFAULT_REPO_AUTO_UPDATES)
  val appUpdates = rememberPreferenceState(PREF_KEY_APP_AUTO_UPDATES, PREF_DEFAULT_APP_AUTO_UPDATES)
  var choosing by rememberSaveable { mutableStateOf<String?>(null) }
  SettingsGroup(
    // hier steht auch, wie Myzel installiert
    stringResource(R.string.myzel_install_updates),
    id = "updates",
    sectionState = sections,
    icon = Icons.Outlined.Update,
    block = RowPosition.FIRST,
  ) {
    row {
      val value = repoUpdates.value.toAutoUpdateValue()
      ListRow(
        it,
        stringResource(R.string.pref_repo_updates_title),
        summary = repoUpdatesSummary(value, model.nextRepoUpdateFlow),
        icon =
          if (value != Never) Icons.Outlined.SystemSecurityUpdate
          else Icons.Outlined.SystemSecurityUpdateWarning,
        onClick = { choosing = PREF_KEY_REPO_AUTO_UPDATES },
      )
    }
    row {
      val value = appUpdates.value.toAutoUpdateValue()
      ListRow(
        it,
        stringResource(R.string.update_auto_install),
        summary = appUpdatesSummary(value, model.nextAppUpdateFlow),
        icon = if (value != Never) Icons.Outlined.Update else Icons.Outlined.UpdateDisabled,
        onClick = { choosing = PREF_KEY_APP_AUTO_UPDATES },
      )
    }
    row { InstallMethodRow(it) }
  }
  when (choosing) {
    PREF_KEY_REPO_AUTO_UPDATES ->
      ChoiceSheet(
        title = stringResource(R.string.pref_repo_updates_title),
        state = repoUpdates,
        values = AutoUpdateValues.entries.map { it.name },
        label = { autoUpdateLabel(it.toAutoUpdateValue()) },
        onDismiss = { choosing = null },
      )
    PREF_KEY_APP_AUTO_UPDATES ->
      ChoiceSheet(
        title = stringResource(R.string.update_auto_install),
        state = appUpdates,
        // wie bei F-Droid ohne „nur beim Öffnen der App“
        values = AutoUpdateValues.entries.filter { it != OnlyWhenOpenApp }.map { it.name },
        label = { autoUpdateLabel(it.toAutoUpdateValue()) },
        onDismiss = { choosing = null },
      )
  }
}

@Composable
private fun repoUpdatesSummary(value: AutoUpdateValues, nextUpdateFlow: Flow<Long>): String =
  when (value) {
    OnlyWifi ->
      stringResource(R.string.pref_repo_updates_summary_only_wifi) +
        "\n" +
        nextUpdateText(nextUpdateFlow)
    Always ->
      stringResource(R.string.pref_repo_updates_summary_always) +
        "\n" +
        nextUpdateText(nextUpdateFlow)
    OnlyWhenOpenApp -> stringResource(R.string.pref_repo_updates_summary_only_when_open_app)
    Never -> stringResource(R.string.pref_repo_updates_summary_never)
  }

@Composable
private fun appUpdatesSummary(value: AutoUpdateValues, nextUpdateFlow: Flow<Long>): String =
  when (value) {
    OnlyWifi ->
      stringResource(R.string.pref_auto_updates_summary_only_wifi) +
        "\n" +
        nextUpdateText(nextUpdateFlow)
    Always ->
      stringResource(R.string.pref_auto_updates_summary_always) +
        "\n" +
        nextUpdateText(nextUpdateFlow)
    OnlyWhenOpenApp,
    Never -> stringResource(R.string.pref_auto_updates_summary_never)
  }

@Composable
private fun nextUpdateText(nextUpdateFlow: Flow<Long>): String {
  val nextUpdate = nextUpdateFlow.collectAsState(Long.MAX_VALUE).value
  return if (nextUpdate == Long.MAX_VALUE) {
    stringResource(
      R.string.auto_update_time,
      stringResource(R.string.repositories_last_update_never),
    )
  } else if (nextUpdate - currentTimeMillis() <= 0) {
    stringResource(R.string.auto_update_time_past)
  } else {
    stringResource(R.string.auto_update_time, nextUpdate.asRelativeTimeString())
  }
}

@Composable
private fun autoUpdateLabel(value: AutoUpdateValues): String =
  stringResource(
    when (value) {
      OnlyWifi -> R.string.pref_auto_updates_only_wifi
      Always -> R.string.pref_auto_updates_only_always
      OnlyWhenOpenApp -> R.string.pref_auto_updates_only_only_when_open_app
      Never -> R.string.pref_auto_updates_only_never
    }
  )

@Composable
private fun NetworkGroup(sections: SectionState) {
  val mirrorChooser = rememberPreferenceState(PREF_KEY_MIRROR_CHOOSER, PREF_DEFAULT_MIRROR_CHOOSER)
  val proxy = rememberPreferenceState(PREF_KEY_PROXY, PREF_DEFAULT_PROXY)
  var showMirrorChooser by rememberSaveable { mutableStateOf(false) }
  var showProxy by rememberSaveable { mutableStateOf(false) }
  SettingsGroup(
    stringResource(R.string.pref_category_network),
    id = "network",
    sectionState = sections,
    icon = Icons.Outlined.Lan,
    block = RowPosition.MIDDLE,
  ) {
    row {
      ListRow(
        it,
        stringResource(R.string.pref_mirror_chooser_title),
        summary = stringResource(mirrorChooser.value.toMirrorChooserValue().res),
        icon = Icons.Outlined.CloudDownload,
        onClick = { showMirrorChooser = true },
      )
    }
    row {
      val value = proxy.value
      ListRow(
        it,
        stringResource(R.string.pref_proxy_title),
        summary =
          if (value.isBlank()) stringResource(R.string.pref_proxy_disabled)
          else stringResource(R.string.pref_proxy_enabled, value),
        icon = Icons.Outlined.VpnLock,
        onClick = { showProxy = true },
      )
    }
  }
  if (showMirrorChooser)
    ChoiceSheet(
      title = stringResource(R.string.pref_mirror_chooser_title),
      state = mirrorChooser,
      values = MirrorChooserValues.entries.map { it.name },
      label = { stringResource(it.toMirrorChooserValue().res) },
      onDismiss = { showMirrorChooser = false },
    )
  if (showProxy)
    ProxyDialog(
      initialValue = proxy.value,
      onConfirm = { proxy.value = it },
      onDismiss = { showProxy = false },
    )
}

@Composable
private fun PrivacyGroup(sections: SectionState) {
  var dnsCache by rememberPreferenceState(PREF_USE_DNS_CACHE, PREF_USE_DNS_CACHE_DEFAULT)
  var preventScreenshots by
    rememberPreferenceState(PREF_KEY_PREVENT_SCREENSHOTS, PREF_DEFAULT_PREVENT_SCREENSHOTS)
  SettingsGroup(
    stringResource(R.string.privacy),
    id = "privacy",
    sectionState = sections,
    icon = Icons.Outlined.Shield,
    block = RowPosition.MIDDLE,
  ) {
    row {
      SwitchRow(
        it,
        stringResource(R.string.useDnsCache),
        dnsCache,
        onCheckedChange = { value -> dnsCache = value },
        summary = stringResource(R.string.useDnsCacheSummary),
        icon = Icons.Outlined.Dns,
      )
    }
    row {
      SwitchRow(
        it,
        stringResource(R.string.preventScreenshots_title),
        preventScreenshots,
        onCheckedChange = { value -> preventScreenshots = value },
        summary = stringResource(R.string.preventScreenshots_summary),
        icon = Icons.Outlined.Screenshot,
      )
    }
  }
}

/**
 * Die Uhr: Geräte mit Debugging über WLAN, die sich bei der Einrichtung als keine Uhr zeigten,
 * blendet die Einrichtung aus; hier kommen sie zurück.
 */
@Composable
private fun WatchGroup(sections: SectionState) {
  val hidden = rememberHiddenDevices()
  var showHidden by rememberSaveable { mutableStateOf(false) }
  SettingsGroup(
    stringResource(R.string.myzel_watch),
    id = "watch",
    sectionState = sections,
    icon = Icons.Outlined.Watch,
    block = RowPosition.MIDDLE,
  ) {
    row {
      ListRow(
        it,
        stringResource(R.string.myzel_settings_watch_hidden),
        summary =
          if (hidden.devices.isEmpty()) stringResource(R.string.myzel_settings_watch_hidden_none)
          else
            hidden.devices.joinToString { device ->
              if (device.name.isEmpty()) device.serial else "${device.name} (${device.serial})"
            },
        icon = Icons.Outlined.VisibilityOff,
        enabled = hidden.devices.isNotEmpty(),
        onClick = { showHidden = true },
      )
    }
  }
  if (showHidden && hidden.devices.isNotEmpty()) {
    HiddenDevicesSheet(hidden, onDismiss = { showHidden = false })
  }
}

/** Sichern und Wiederherstellen von Paketquellen, Favoriten, Einstellungen und Darstellung. */
@Composable
private fun BackupGroup(sections: SectionState) {
  var showExport by rememberSaveable { mutableStateOf(false) }
  var showImport by rememberSaveable { mutableStateOf(false) }
  SettingsGroup(
    stringResource(R.string.myzel_backup_title),
    id = "backup",
    sectionState = sections,
    icon = Icons.Outlined.SettingsBackupRestore,
    block = RowPosition.LAST,
  ) {
    row {
      ListRow(
        it,
        stringResource(R.string.myzel_backup_export),
        summary = stringResource(R.string.myzel_backup_export_summary),
        icon = Icons.Outlined.Upload,
        onClick = { showExport = true },
      )
    }
    row {
      ListRow(
        it,
        stringResource(R.string.myzel_backup_import),
        summary = stringResource(R.string.myzel_backup_import_summary),
        icon = Icons.Outlined.Download,
        onClick = { showImport = true },
      )
    }
  }
  if (showExport) ExportBackupFlow(onDismiss = { showExport = false })
  if (showImport) ImportBackupFlow(onDismiss = { showImport = false })
}

@Composable
private fun DebugGroup(onSaveLogcat: (Uri?) -> Unit) {
  val context = LocalContext.current
  val launcher =
    rememberLauncherForActivityResult(CreateDocument("text/plain")) { onSaveLogcat(it) }
  SettingsGroup(null) {
    row {
      ListRow(
        it,
        stringResource(R.string.pref_export_log_title),
        summary = stringResource(R.string.pref_export_log_summary),
        icon = Icons.Outlined.BugReport,
        onClick = {
          if (!launcher.launchSafe("${getLogName(context)}.txt")) {
            Toast.makeText(context, R.string.no_handler_app_generic, LENGTH_SHORT).show()
          }
        },
      )
    }
  }
}

/** Auswahl eines Werts aus [values] im Blatt von unten, statt im Dialog wie bei F-Droid. */
@Composable
private fun ChoiceSheet(
  title: String,
  state: MutableState<String>,
  values: List<String>,
  label: @Composable (String) -> String,
  onDismiss: () -> Unit,
) {
  OptionsSheet(onDismiss = onDismiss, title = title) {
    values.forEach { value ->
      val selected = value == state.value
      SheetAction(
        icon =
          if (selected) Icons.Outlined.RadioButtonChecked else Icons.Outlined.RadioButtonUnchecked,
        label = label(value),
        tint =
          if (selected) MaterialTheme.colorScheme.primary
          else MaterialTheme.colorScheme.onSurfaceVariant,
        onClick = {
          state.value = value
          onDismiss()
        },
      )
    }
  }
}

@Preview
@Composable
private fun SettingsPreview() {
  FDroidContent {
    val model =
      SettingsModel(
        prefsFlow = MutableStateFlow(MapPreferences()),
        nextRepoUpdateFlow = MutableStateFlow(Long.MAX_VALUE),
        nextAppUpdateFlow = MutableStateFlow(currentTimeMillis() - HOURS.toMillis(12)),
        currentAppIconFlow = MutableStateFlow(AppIcon.Default),
      )
    Settings(model, {}, {}) {}
  }
}
