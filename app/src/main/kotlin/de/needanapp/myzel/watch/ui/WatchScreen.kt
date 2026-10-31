package de.needanapp.myzel.watch.ui

import android.Manifest.permission.BLUETOOTH_CONNECT
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build.VERSION.SDK_INT
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.LinkOff
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import de.needanapp.design.components.ContentDialog
import de.needanapp.design.components.ContentRow
import de.needanapp.design.components.ListRow
import de.needanapp.design.components.PillButton
import de.needanapp.design.components.RowPosition
import de.needanapp.design.components.SettingsGroup
import de.needanapp.design.components.SignalCircularProgressIndicator
import de.needanapp.design.components.SwitchRow
import de.needanapp.design.components.TextFieldRow
import de.needanapp.myzel.ui.compat.BackButton
import de.needanapp.myzel.ui.compat.Scaffold
import de.needanapp.myzel.ui.compat.TopAppBar
import de.needanapp.myzel.watch.PairedWatch
import de.needanapp.myzel.watch.WatchException
import de.needanapp.myzel.watch.WearUpdateState
import de.needanapp.myzel.watch.setup.AdbDevice
import de.needanapp.myzel.watch.setup.SetupProblem
import de.needanapp.myzel.watch.setup.SetupState
import de.needanapp.myzel.watch.setup.SetupStep
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import org.fdroid.R

/** Die Rechte für Suche und Verbindung: Bluetooth und, ab Android 17, das lokale Netz. */
private val permissions: Array<String> =
  if (SDK_INT >= 37) arrayOf(BLUETOOTH_CONNECT, "android.permission.ACCESS_LOCAL_NETWORK")
  else arrayOf(BLUETOOTH_CONNECT)

private fun Context.hasPermissions() = permissions.all {
  checkSelfPermission(it) == PERMISSION_GRANTED
}

/**
 * Die Seite „Uhr“: ohne Uhr der Assistent zum Einrichten (Umbauplan Phase 1), mit Uhr ihr Stand,
 * ein Verbindungstest und „Neu einrichten“ oder „Uhr entfernen“.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun WatchScreen(viewModel: WatchViewModel, onBackClicked: () -> Unit) {
  val watch = viewModel.watch.collectAsStateWithLifecycle().value
  val assistant = viewModel.assistant.collectAsStateWithLifecycle().value
  BackHandler(enabled = assistant && watch != null) {
    if (!viewModel.closeAssistant()) onBackClicked()
  }
  Scaffold(
    topBar = {
      TopAppBar(
        navigationIcon = {
          BackButton(onClick = { if (!viewModel.closeAssistant()) onBackClicked() })
        },
        title = { Text(stringResource(R.string.myzel_watch), maxLines = 1) },
      )
    }
  ) { paddingValues ->
    // je eine Seite mit eigener Scrollposition
    if (assistant || watch == null) {
      Page(paddingValues) { Assistant(viewModel) }
    } else {
      Page(paddingValues) { Overview(viewModel, watch) }
    }
  }
}

/**
 * Scrollt unter der Kopfleiste und endet über der Tastatur, damit das Feld für den Kopplungscode
 * und die laufenden Schritte der Einrichtung in den sichtbaren Bereich gescrollt werden können.
 */
@Composable
private fun Page(paddingValues: PaddingValues, content: @Composable ColumnScope.() -> Unit) {
  val direction = LocalLayoutDirection.current
  Column(
    Modifier.fillMaxSize()
      .padding(
        start = paddingValues.calculateStartPadding(direction),
        top = paddingValues.calculateTopPadding(),
        end = paddingValues.calculateEndPadding(direction),
      )
      .imePadding()
      .verticalScroll(rememberScrollState())
      .padding(bottom = paddingValues.calculateBottomPadding()),
    content = content,
  )
}

@Composable
private fun Overview(viewModel: WatchViewModel, watch: PairedWatch) {
  val check = viewModel.check.collectAsStateWithLifecycle().value
  val wearUpdate = viewModel.wearUpdate.collectAsStateWithLifecycle().value
  val askAfterInstall = viewModel.askAfterInstall.collectAsStateWithLifecycle().value
  val justSetUp = viewModel.justSetUp.collectAsStateWithLifecycle().value
  var confirmForget by rememberSaveable { mutableStateOf(false) }
  Column {
    SettingsGroup(stringResource(R.string.myzel_watch_status)) {
      row {
        ContentRow(it, null) {
          Text(watch.name, style = MaterialTheme.typography.titleMedium)
          if (watch.sdk > 0) {
            Note(stringResource(R.string.myzel_watch_api, watch.sdk))
          }
          Note("${stringResource(R.string.myzel_watch_key)} ${watch.keyId}")
          if (justSetUp) {
            Text(
              stringResource(R.string.myzel_watch_set_up_done),
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.primary,
              modifier = Modifier.padding(top = 8.dp),
            )
          }
        }
      }
      row {
        ListRow(
          it,
          stringResource(R.string.myzel_watch_check),
          summary =
            when (check) {
              CheckState.Idle -> stringResource(R.string.myzel_watch_hint_open)
              CheckState.Running -> stringResource(R.string.myzel_watch_checking)
              is CheckState.Ok ->
                stringResource(
                  R.string.myzel_watch_check_ok,
                  check.info.name,
                  check.info.pingMillis.toInt(),
                )
              is CheckState.Failed -> stringResource(check.reason.text())
            },
          icon = Icons.Outlined.Bluetooth,
          enabled = check != CheckState.Running,
          onClick = viewModel::checkConnection,
        )
      }
      row {
        SwitchRow(
          it,
          stringResource(R.string.myzel_watch_ask_setting),
          checked = askAfterInstall,
          onCheckedChange = viewModel::setAskAfterInstall,
          summary = stringResource(R.string.myzel_watch_ask_setting_summary),
          icon = Icons.Outlined.Watch,
        )
      }
      // Myzel bringt seine Myzel Wear von selbst auf die Uhr; nach einem Fehlschlag hier erneut
      if (wearUpdate != null) {
        row {
          ListRow(
            it,
            stringResource(R.string.myzel_watch_wear_update),
            summary =
              wearUpdateText(wearUpdate) +
                if (wearUpdate is WearUpdateState.Failed) {
                  "\n" + stringResource(R.string.myzel_watch_wear_update_retry)
                } else "",
            icon = Icons.Outlined.SystemUpdate,
            enabled = wearUpdate is WearUpdateState.Failed && check != CheckState.Running,
            onClick = viewModel::retryWearUpdate,
          )
        }
      }
    }
    SettingsGroup(null) {
      row {
        ListRow(
          it,
          stringResource(R.string.myzel_watch_set_up_again),
          summary = stringResource(R.string.myzel_watch_set_up_again_summary),
          icon = Icons.Outlined.Refresh,
          onClick = viewModel::setUpAgain,
        )
      }
      row {
        ListRow(
          it,
          stringResource(R.string.myzel_watch_forget),
          summary = stringResource(R.string.myzel_watch_forget_summary),
          icon = Icons.Outlined.Delete,
          onClick = { confirmForget = true },
        )
      }
    }
  }
  if (confirmForget) {
    ContentDialog(
      title = stringResource(R.string.myzel_watch_forget),
      confirmLabel = stringResource(R.string.myzel_watch_forget),
      onConfirm = {
        confirmForget = false
        viewModel.forget()
      },
      onDismiss = { confirmForget = false },
      isDestructive = true,
      icon = Icons.Outlined.Watch,
      confirmIcon = Icons.Outlined.LinkOff,
    ) {
      Text(stringResource(R.string.myzel_watch_forget_text))
    }
  }
}

@Composable
private fun Assistant(viewModel: WatchViewModel) {
  val context = LocalContext.current
  val state = viewModel.setupState.collectAsStateWithLifecycle().value
  var granted by rememberSaveable { mutableStateOf(context.hasPermissions()) }
  val request =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
      granted = context.hasPermissions()
    }
  // erst erklären, dann fragen: das Recht fordert der Knopf „Erlauben“ an
  LaunchedEffect(granted) { if (granted) viewModel.startSearch() }
  var code by rememberSaveable { mutableStateOf("") }
  val focusManager = LocalFocusManager.current
  val codeView = remember { BringIntoViewRequester() }
  var codeFocused by remember { mutableStateOf(false) }
  KeepAboveKeyboard(codeFocused, codeView)
  Column {
    SettingsGroup(null) {
      row { ContentRow(it, null) { Note(stringResource(R.string.myzel_watch_intro)) } }
    }
    SettingsGroup(stringResource(R.string.myzel_watch_howto)) {
      row {
        ContentRow(it, null) {
          Text(
            stringResource(R.string.myzel_watch_howto_steps),
            style = MaterialTheme.typography.bodyMedium,
          )
        }
      }
    }
    if (!granted) {
      SettingsGroup(null) {
        row {
          ContentRow(it, null) {
            Note(stringResource(R.string.myzel_watch_permission))
            PillButton(
              stringResource(R.string.myzel_watch_permission_grant),
              onClick = { request.launch(permissions) },
              modifier = Modifier.padding(top = 12.dp),
            )
          }
        }
      }
      return@Column
    }
    Devices(state, viewModel::select)
    SettingsGroup(
      null,
      Modifier.bringIntoViewRequester(codeView).onFocusChanged { codeFocused = it.hasFocus },
    ) {
      row {
        TextFieldRow(
          it,
          value = code,
          onValueChange = { new -> code = new.filter(Char::isDigit).take(6) },
          label = stringResource(R.string.myzel_watch_code),
          supportingText = stringResource(R.string.myzel_watch_code_hint),
          keyboardOptions =
            KeyboardOptions(keyboardType = KeyboardType.NumberPassword, imeAction = ImeAction.Done),
          enabled = !state.running,
        )
      }
      row {
        ContentRow(it, null) {
          PillButton(
            stringResource(
              if (code.isEmpty()) R.string.myzel_watch_set_up
              else R.string.myzel_watch_pair_and_set_up
            ),
            onClick = {
              // die Tastatur geht weg, die Schritte bekommen den Platz
              focusManager.clearFocus()
              viewModel.setUp(code)
            },
            enabled = !state.running && state.selected != null && code.length in setOf(0, 6),
          )
        }
      }
    }
    if (state.steps.isNotEmpty()) Progress(state, viewModel::hideDevice)
  }
}

/**
 * Bringt [view] (das Codefeld samt Knopf) über die Tastatur, sobald sie steht, solange das Feld
 * [focused] ist. Compose selbst hielte nur das Feld sichtbar, den Knopf darunter nicht.
 */
@Composable
private fun KeepAboveKeyboard(focused: Boolean, view: BringIntoViewRequester) {
  val ime = WindowInsets.ime
  val density = LocalDensity.current
  LaunchedEffect(focused) {
    if (!focused) return@LaunchedEffect
    snapshotFlow { ime.getBottom(density) }
      .collectLatest { bottom ->
        if (bottom > 0) {
          delay(100)
          view.bringIntoView()
        }
      }
  }
}

@Composable
private fun Devices(state: SetupState, onSelect: (String) -> Unit) {
  SettingsGroup(stringResource(R.string.myzel_watch_found)) {
    if (state.devices.isEmpty()) {
      row {
        ListRow(
          it,
          stringResource(R.string.myzel_watch_searching),
          trailing = { SignalCircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) },
        )
      }
    }
    state.devices.forEach { device ->
      row {
        ListRow(
          it,
          device.serial,
          summary = stringResource(device.statusText()),
          icon = Icons.Outlined.Watch,
          enabled = !state.running,
          onClick = { onSelect(device.name) },
          trailing = {
            RadioButton(
              selected = device.name == state.selected,
              onClick = { onSelect(device.name) },
              enabled = !state.running,
            )
          },
        )
      }
    }
    if (state.hiddenCount > 0) {
      row {
        ListRow(
          it,
          pluralStringResource(
            R.plurals.myzel_watch_devices_hidden,
            state.hiddenCount,
            state.hiddenCount,
          ),
          summary = stringResource(R.string.myzel_watch_devices_hidden_summary),
          icon = Icons.Outlined.VisibilityOff,
        )
      }
    }
  }
}

@Composable
private fun Progress(state: SetupState, onHide: (String) -> Unit) {
  val groupView = remember { BringIntoViewRequester() }
  val stepViews = remember { SetupStep.entries.associateWith { BringIntoViewRequester() } }
  val problemView = remember { BringIntoViewRequester() }
  // mitlaufen: die ganze Liste zeigen und, passt sie nicht, den laufenden Schritt oder das Problem
  LaunchedEffect(state.current, state.problem, state.done.size) {
    withFrameNanos {} // erst, wenn die neuen Zeilen ihren Platz haben
    groupView.bringIntoView()
    if (state.problem != null) problemView.bringIntoView()
    else state.current?.let { stepViews.getValue(it).bringIntoView() }
  }
  SettingsGroup(
    stringResource(R.string.myzel_watch_progress),
    Modifier.bringIntoViewRequester(groupView),
  ) {
    state.steps.forEach { step ->
      row {
        val failed = state.problem != null && step == state.steps.firstOrNull { it !in state.done }
        Box(Modifier.bringIntoViewRequester(stepViews.getValue(step))) {
          StepRow(it, step, state, failed)
        }
      }
    }
    state.problem?.let { problem ->
      row {
        Box(Modifier.bringIntoViewRequester(problemView)) { ProblemRow(it, problem, state, onHide) }
      }
    }
  }
}

@Composable
private fun StepRow(position: RowPosition, step: SetupStep, state: SetupState, failed: Boolean) {
  ListRow(
    position,
    stringResource(step.text()),
    trailing = {
      when {
        step == state.current ->
          SignalCircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
        step in state.done ->
          Icon(
            Icons.Rounded.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
          )
        failed ->
          Icon(
            Icons.Rounded.ErrorOutline,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
          )
        else ->
          Icon(
            Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.outline,
          )
      }
    },
  )
}

@Composable
private fun ProblemRow(
  position: RowPosition,
  problem: SetupProblem,
  state: SetupState,
  onHide: (String) -> Unit,
) {
  ContentRow(position, null) {
    Column(verticalArrangement = spacedBy(4.dp)) {
      Text(
        stringResource(problem.text()),
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.error,
      )
      state.linkProblem?.let { Note(stringResource(it.text())) }
      state.detail?.takeIf { problem != SetupProblem.LINK_FAILED }?.let { Note(it) }
      // ein Gerät, das sich nicht verbinden lässt, kann auch ein Handy sein
      state.failedDevice?.let { name ->
        TextButton(onClick = { onHide(name) }, enabled = !state.running) {
          Text(stringResource(R.string.myzel_watch_hide_device))
        }
      }
    }
  }
}

@Composable
private fun Note(text: String) {
  Text(
    text,
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
  )
}

private fun AdbDevice.statusText(): Int =
  when {
    pairing != null -> R.string.myzel_watch_device_pairing
    remembered -> R.string.myzel_watch_device_remembered
    else -> R.string.myzel_watch_device_ready
  }

private fun SetupStep.text(): Int =
  when (this) {
    SetupStep.PAIR -> R.string.myzel_watch_step_pair
    SetupStep.CONNECT -> R.string.myzel_watch_step_connect
    SetupStep.INSTALL -> R.string.myzel_watch_step_install
    SetupStep.PERMISSIONS -> R.string.myzel_watch_step_permissions
    SetupStep.KEY -> R.string.myzel_watch_step_key
    SetupStep.START -> R.string.myzel_watch_step_start
    SetupStep.BLUETOOTH -> R.string.myzel_watch_step_bluetooth
    SetupStep.TEST -> R.string.myzel_watch_step_test
    SetupStep.WIFI_OFF -> R.string.myzel_watch_step_wifi_off
  }

private fun SetupProblem.text(): Int =
  when (this) {
    SetupProblem.NO_WATCH -> R.string.myzel_watch_problem_no_watch
    SetupProblem.NOT_A_WATCH -> R.string.myzel_watch_problem_not_a_watch
    SetupProblem.PAIRING_CLOSED -> R.string.myzel_watch_problem_pairing_closed
    SetupProblem.PAIRING_FAILED -> R.string.myzel_watch_problem_pairing_failed
    SetupProblem.NOT_REACHABLE -> R.string.myzel_watch_problem_not_reachable
    SetupProblem.NOT_PAIRED -> R.string.myzel_watch_problem_not_paired
    SetupProblem.NO_WEAR_APK -> R.string.myzel_watch_problem_no_wear_apk
    SetupProblem.WEAR_DOWNLOAD_FAILED -> R.string.myzel_watch_problem_wear_download_failed
    SetupProblem.INSTALL_FAILED -> R.string.myzel_watch_problem_install_failed
    SetupProblem.KEY_FAILED -> R.string.myzel_watch_problem_key_failed
    SetupProblem.NOT_BONDED -> R.string.myzel_watch_problem_not_bonded
    SetupProblem.LINK_FAILED,
    SetupProblem.FAILED -> R.string.myzel_watch_problem_failed
  }

internal fun WatchException.Reason.text(): Int =
  when (this) {
    WatchException.Reason.NOT_SET_UP -> R.string.myzel_watch_reason_not_set_up
    WatchException.Reason.NO_PERMISSION -> R.string.myzel_watch_reason_no_permission
    WatchException.Reason.BLUETOOTH_OFF -> R.string.myzel_watch_reason_bluetooth_off
    WatchException.Reason.UNREACHABLE -> R.string.myzel_watch_reason_unreachable
    WatchException.Reason.REFUSED -> R.string.myzel_watch_reason_refused
    WatchException.Reason.VERSION -> R.string.myzel_watch_reason_version
  }
