package de.needanapp.myzel.wear

import android.os.Bundle
import android.text.format.DateUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.TransformingLazyColumnItemScope
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.CircularProgressIndicatorDefaults
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.TransformationSpec
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import de.needanapp.myzel.link.android.hasLinkPermissions
import de.needanapp.myzel.link.android.linkPermissions

/** Das Türkis von Myzel (#0097A7), aufgehellt für den schwarzen Hintergrund der Uhr. */
private val MyzelColors =
  ColorScheme(
    primary = Color(0xFF4FD8EB),
    primaryDim = Color(0xFF2BB8CA),
    primaryContainer = Color(0xFF004F58),
    onPrimary = Color(0xFF00363D),
    onPrimaryContainer = Color(0xFF97F0FF),
  )

class MainActivity : ComponentActivity() {

  override fun onResume() {
    super.onResume()
    WatchLink.inForeground = true
    // Bei jedem Öffnen lauschen, auch wenn die App nur im Hintergrund lag und der Dienst sich nach
    // der Leerlaufzeit beendet hat
    if (hasLinkPermissions()) LinkService.start(this)
    // Kam eine Installation im Hintergrund an, jetzt bestätigen lassen
    WatchLink.takeConfirm()?.let { Installer.showConfirm(this, it) }
  }

  override fun onPause() {
    WatchLink.inForeground = false
    super.onPause()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    setContent { MaterialTheme(colorScheme = MyzelColors) { WatchScreen() } }
  }
}

@Composable
private fun WatchScreen() {
  val context = LocalContext.current
  val state by WatchLink.state.collectAsState()
  val permissions =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
      if (context.hasLinkPermissions()) LinkService.start(context)
    }
  // Das Lauschen startet onResume; ohne das Recht erst danach fragen
  LaunchedEffect(Unit) { if (!context.hasLinkPermissions()) permissions.launch(linkPermissions) }
  val canInstall = remember { context.packageManager.canRequestPackageInstalls() }
  AppScaffold {
    val listState = rememberTransformingLazyColumnState()
    val spec = rememberTransformationSpec()
    Box(Modifier.fillMaxSize()) {
      ScreenScaffold(
        scrollState = listState,
        edgeButton = {
          EdgeButton(
            onClick = {
              if (state.running) LinkService.stop(context) else LinkService.start(context)
            }
          ) {
            Text(
              stringResource(if (state.running) R.string.button_pause else R.string.button_start),
              maxLines = 1,
            )
          }
        },
      ) { contentPadding ->
        TransformingLazyColumn(state = listState, contentPadding = contentPadding) {
          item {
            ListHeader(Modifier.transformedHeight(this, spec)) {
              Text(stringResource(R.string.app_name))
            }
          }
          item { StatusPill(state, Modifier.edge(this, spec)) }
          // ohne Verbindung: wann das Handy zuletzt da war
          val lastPhone = state.lastPhone
          if (state.peer == null && lastPhone != null) {
            item {
              Note(
                stringResource(R.string.status_last_phone, lastPhone, shortTime(state.lastPhoneAt)),
                Modifier.edge(this, spec),
                small = true,
              )
            }
          }
          state.task?.let { task -> item { TaskNote(task, Modifier.edge(this, spec)) } }
          // zum Vergleich mit der Seite „Uhr“ in Myzel auf dem Handy
          item {
            val version = remember {
              context.packageManager.getPackageInfo(context.packageName, 0).versionName.orEmpty()
            }
            val about =
              listOfNotNull(
                  stringResource(R.string.about_version, version),
                  state.keyId?.let { stringResource(R.string.about_key, it) },
                )
                .joinToString("\n")
            Note(about, Modifier.edge(this, spec), small = true)
          }
          if (!canInstall) {
            item {
              Note(stringResource(R.string.status_cannot_install), Modifier.edge(this, spec), true)
            }
          }
          state.confirm?.let { confirm ->
            item {
              FilledTonalButton(
                onClick = {
                  WatchLink.takeConfirm(confirm.requestId)?.let {
                    Installer.showConfirm(context, it)
                  }
                },
                modifier = Modifier.fillMaxWidth().transformedHeight(this, spec),
                transformation = SurfaceTransformation(spec),
              ) {
                Text(
                  stringResource(
                    if (confirm.removal) R.string.confirm_uninstall else R.string.confirm_install,
                    confirm.name,
                  ),
                  maxLines = 2,
                )
              }
            }
          }
          if (state.events.isNotEmpty()) {
            item {
              ListHeader(Modifier.transformedHeight(this, spec)) {
                Text(stringResource(R.string.history))
              }
            }
            state.events.forEachIndexed { i, event ->
              item(key = "e$i$event") { Note(event, Modifier.edge(this, spec), small = true) }
            }
          }
        }
      }
      // solange die Uhr auf eine Bestätigung wartet, zeigt der Knopf den Stand
      state.task?.takeIf { state.confirm == null }?.let { TaskRing(it) }
    }
  }
}

/** Uhrzeit von [millis], an einem anderen Tag mit Datum. */
@Composable
private fun shortTime(millis: Long): String {
  val flags =
    if (DateUtils.isToday(millis)) DateUtils.FORMAT_SHOW_TIME
    else DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_ABBREV_MONTH
  return DateUtils.formatDateTime(LocalContext.current, millis, flags)
}

/** Wie gut das Handy die Uhr gerade erreicht. */
private enum class Reach {
  CONNECTED,
  READY,
  UNREACHABLE,
}

/** Grün mit Handy, neutral bereit, rot, wenn das Handy die Uhr nicht erreicht. */
private val Green = Color(0xFF8BDA9C)
private val GreenContainer = Color(0xFF173D22)

/**
 * Der Stand oben auf der Uhr: grün, solange ein Handy verbunden ist, neutral, solange Myzel Wear
 * lauscht (Myzel verbindet sich nur für einen Auftrag), rot, wenn das Handy die Uhr nicht erreicht.
 */
@Composable
private fun StatusPill(state: WatchState, modifier: Modifier) {
  val (reach, text) =
    when {
      state.problem == Problem.NO_PERMISSION ->
        Reach.UNREACHABLE to stringResource(R.string.status_no_permission)
      state.problem == Problem.BLUETOOTH_OFF ->
        Reach.UNREACHABLE to stringResource(R.string.status_bluetooth_off)
      state.keyId == null -> Reach.UNREACHABLE to stringResource(R.string.status_no_key)
      state.peer != null -> Reach.CONNECTED to stringResource(R.string.status_connected, state.peer)
      state.running && state.listening -> Reach.READY to stringResource(R.string.status_listening)
      state.running -> Reach.READY to stringResource(R.string.status_starting)
      else -> Reach.UNREACHABLE to stringResource(R.string.status_paused)
    }
  val scheme = MaterialTheme.colorScheme
  val (container, content, dot) =
    when (reach) {
      Reach.CONNECTED -> Triple(GreenContainer, Green, Green)
      Reach.READY -> Triple(scheme.surfaceContainer, scheme.onSurface, scheme.primary)
      Reach.UNREACHABLE -> Triple(scheme.errorContainer, scheme.onErrorContainer, scheme.error)
    }
  Row(
    modifier =
      modifier
        .fillMaxWidth()
        .padding(horizontal = 4.dp)
        .clip(RoundedCornerShape(50))
        .background(container)
        .padding(horizontal = 14.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.Center,
  ) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(dot))
    Text(
      text,
      style = MaterialTheme.typography.bodySmall,
      color = content,
      textAlign = TextAlign.Center,
      modifier = Modifier.padding(start = 8.dp),
    )
  }
}

/** Was die Uhr gerade für das Handy tut, mit Prozent beim Empfangen. */
@Composable
private fun TaskNote(task: Task, modifier: Modifier) {
  val text =
    when (task.kind) {
      TaskKind.TEST -> stringResource(R.string.task_test)
      TaskKind.RECEIVING -> stringResource(R.string.task_receiving, task.name)
      TaskKind.INSTALLING -> stringResource(R.string.task_installing, task.name)
      TaskKind.REMOVING -> stringResource(R.string.task_removing, task.name)
    }
  Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
    Text(
      text,
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurface,
      textAlign = TextAlign.Center,
      maxLines = 2,
      modifier = Modifier.padding(horizontal = 8.dp),
    )
    task.percent?.let {
      Text(
        stringResource(R.string.task_percent, it),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
      )
    }
  }
}

/**
 * Fortschritt am Rand des runden Displays; unten bleibt Platz für den Knopf. Ohne Prozente (beim
 * Installieren und Entfernen) läuft der Ring ohne Ende.
 */
@Composable
private fun TaskRing(task: Task) {
  val modifier = Modifier.fillMaxSize().padding(CircularProgressIndicatorDefaults.FullScreenPadding)
  val percent = task.percent
  if (percent != null) {
    CircularProgressIndicator(
      progress = { percent / 100f },
      modifier = modifier,
      startAngle = 120f,
      endAngle = 60f,
      strokeWidth = CircularProgressIndicatorDefaults.largeStrokeWidth,
    )
  } else {
    CircularProgressIndicator(
      modifier = modifier,
      strokeWidth = CircularProgressIndicatorDefaults.largeStrokeWidth,
    )
  }
}

@Composable
private fun Note(
  text: String,
  modifier: Modifier,
  warning: Boolean = false,
  small: Boolean = false,
) {
  Text(
    text,
    style =
      if (small) MaterialTheme.typography.bodyExtraSmall else MaterialTheme.typography.bodySmall,
    color =
      if (warning) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
    modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp),
  )
}

private fun Modifier.edge(scope: TransformingLazyColumnItemScope, spec: TransformationSpec) =
  transformedHeight(scope, spec).graphicsLayer {
    with(scope) { with(spec) { applyContainerTransformation(scrollProgress) } }
  }
