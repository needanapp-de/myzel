package de.needanapp.myzel.ui.nav

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dagger.hilt.android.EntryPointAccessors
import de.needanapp.design.components.DeviceDisplay
import de.needanapp.design.components.StatusTone
import de.needanapp.design.components.displayColor
import de.needanapp.design.theme.DesignType
import de.needanapp.design.theme.LocalDesignColors
import de.needanapp.design.theme.LocalDesignEffects
import de.needanapp.design.theme.NeedanappMotion
import de.needanapp.myzel.repo.RepoOutcome
import de.needanapp.myzel.repo.RepoUpdateRun
import de.needanapp.myzel.repo.RepoUpdateStep
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import org.fdroid.R

/** So lange hält die Anzeige jede neue Phase mindestens, damit man sie lesen kann. */
private const val STEP_MILLIS = 400L

/** So oft folgt sie höchstens einem neuen Prozentstand. */
private const val FRAME_MILLIS = 80L

/**
 * Ein Ergebnis, das beim Öffnen der Seite schon länger zurückliegt, zeigt die Anzeige nicht mehr;
 * sonst steht es, bis jemand tippt oder scrollt.
 */
private const val RECENT_MILLIS = 3_000L

/** Höchstens so viele Paketquellen stehen untereinander, bei mehr läuft die Liste mit. */
private const val MAX_ROWS = 6

private const val ROW_MILLIS = 320
private const val ROW_STAGGER_MILLIS = 55

/** So lange dauert das Aus- oder Einrollen aller Zeilen. */
private const val ROLL_MILLIS = 650L

/** In dieser Zeit läuft der Punkt beim Prüfen einmal durch die Reihe. */
private const val SCAN_MILLIS = 1_400
private const val BLINK_MILLIS = 500

private val RowHeight = 16.dp
private val RowGap = 6.dp
private val SymbolWidth = 44.dp
private val DotSize = 5.dp
private val DotGap = 2.dp
private const val NAME_WEIGHT = 0.38f

/**
 * Was die Anzeige von einer Aktualisierung zeigt: [run] ist die laufende oder beendete, oder null.
 * Jede neue Phase einer Paketquelle bleibt mindestens [STEP_MILLIS] stehen (die Aktualisierung
 * selbst wartet nicht, dazwischen fallen Zwischenstände weg). Das Ergebnis steht, bis [dismiss] es
 * wegnimmt oder ein neuer Lauf beginnt.
 */
@Stable
internal class RepoBoardState {
  var run by mutableStateOf<RepoUpdateRun?>(null)
    internal set

  internal val dismissals = Channel<Unit>(Channel.CONFLATED)

  /** Nimmt das Ergebnis weg; während eines Laufs ohne Wirkung. */
  fun dismiss() {
    if (run?.isFinished == true) dismissals.trySend(Unit)
  }
}

@Composable
internal fun rememberRepoBoard(): RepoBoardState {
  val state = remember { RepoBoardState() }
  if (LocalInspectionMode.current) return state
  val context = LocalContext.current
  val manager = remember {
    EntryPointAccessors.fromApplication(context, RepoIssuesEntryPoint::class.java)
      .repoUpdateManager()
  }
  LaunchedEffect(manager) {
    var last: RepoUpdateRun? = null
    manager.updateRun.collect { run ->
      val finishedAt = run?.finishedAt
      if (run == null || finishedAt != null && last == null && isOld(finishedAt)) {
        state.run = null
        return@collect
      }
      state.run = run
      delay(if (run.phaseChangedFrom(last)) STEP_MILLIS else FRAME_MILLIS)
      last = run
      if (run.isFinished) {
        state.dismissals.tryReceive()
        // steht, bis jemand tippt oder scrollt; ein neuer Lauf löst es sofort ab
        val newRun =
          merge(
              manager.updateRun.filter { it !== run }.map { true },
              state.dismissals.receiveAsFlow().map { false },
            )
            .first()
        if (!newRun) {
          state.run = null
          last = null
        }
      }
    }
  }
  return state
}

private fun isOld(finishedAt: Long) = System.currentTimeMillis() - finishedAt > RECENT_MILLIS

/**
 * [content], solange keine Aktualisierung läuft. Beginnt eine, blendet die Anzeige über zu
 * [RepoBoardDisplay], und die Zeilen der Paketquellen klappen nacheinander nach unten auf, wie eine
 * Punktmatrix, die sich Zeile für Zeile einschaltet. Das Ergebnis steht, bis jemand auf die Anzeige
 * tippt oder die Seite scrollt (die Anzeige bewegt sich dann auf dem Bildschirm); dann klappen die
 * Zeilen von unten her wieder ein, und [content] kommt zurück.
 */
@Composable
internal fun RepoBoardSwitch(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
  val board = rememberRepoBoard()
  val run = board.run
  var kept by remember { mutableStateOf<RepoUpdateRun?>(null) }
  var rowsVisible by remember { mutableStateOf(false) }
  LaunchedEffect(run) {
    if (run != null) {
      kept = run
      rowsVisible = true
    } else if (kept != null) {
      rowsVisible = false
      delay(ROLL_MILLIS)
      kept = null
    }
  }
  // rückt die Anzeige nach dem Ergebnis weiter als SCROLL_SLOP, hat jemand gescrollt
  val finished = run?.takeIf { it.isFinished }
  val position = remember { BoardPosition() }
  LaunchedEffect(finished) {
    position.resultTop = if (finished != null) position.top else Float.NaN
  }
  val slop = with(LocalDensity.current) { SCROLL_SLOP.toPx() }
  val fade: FiniteAnimationSpec<Float> =
    if (LocalDesignEffects.current.richMotion) tween(NeedanappMotion.MEDIUM_DURATION) else snap()
  // ein Überblenden ohne eigenes Wachsen, die Höhe gleitet mit den Zeilen
  AnimatedContent(
    targetState = run ?: kept,
    modifier =
      modifier.onGloballyPositioned { coordinates ->
        val top = coordinates.positionInWindow().y
        position.top = top
        if (finished == null) return@onGloballyPositioned
        if (position.resultTop.isNaN()) position.resultTop = top
        else if (abs(top - position.resultTop) > slop) board.dismiss()
      },
    transitionSpec = { (fadeIn(fade) togetherWith fadeOut(fade)).using(null) },
    contentKey = { it != null },
    label = "repoBoard",
  ) { shown ->
    when {
      shown == null -> content()
      shown.isFinished ->
        RepoBoardDisplay(
          shown,
          rowsVisible,
          Modifier.clip(BoardTapShape).clickable(
            onClickLabel = stringResource(R.string.myzel_board_dismiss)
          ) {
            board.dismiss()
          },
        )
      else -> RepoBoardDisplay(shown, rowsVisible)
    }
  }
}

private val SCROLL_SLOP = 8.dp

/** Wo die Anzeige zuletzt stand ([top]) und wo, als das Ergebnis kam; NaN: noch nicht gemessen. */
private class BoardPosition {
  var top = Float.NaN
  var resultTop = Float.NaN
}

// fängt den Tipp-Effekt in den Ecken der Anzeige
private val BoardTapShape = RoundedCornerShape(12.dp)

/**
 * Die Tafel einer Aktualisierung (Vorschlag 5a): oben, welche Paketquelle gerade prüft, lädt oder
 * speichert, als wievielte von wie vielen und mit wie viel Prozent; darunter eine Zeile je
 * Paketquelle mit Name, Punktreihe und Ergebnis. Am Ende sagt die obere Zeile, wie es ausging und
 * wie lange es dauerte. Die Zeilen zeigt sie nur mit [rowsVisible].
 */
@Composable
internal fun RepoBoardDisplay(
  run: RepoUpdateRun,
  rowsVisible: Boolean,
  modifier: Modifier = Modifier,
) {
  val names = rememberRepoState().names
  val (text, tone, detail) = boardHeader(run, names)
  DeviceDisplay(
    text,
    tone,
    modifier,
    detail = detail,
    footer = { RepoBoardRows(run, names, rowsVisible) },
    // das Ergebnis kommt herein wie eine neue Meldung
    contentKey = run.isFinished,
    blinking = !run.isFinished,
  )
}

private data class BoardHeader(val text: String, val tone: StatusTone, val detail: String?)

@Composable
private fun boardHeader(run: RepoUpdateRun, names: Map<Long, String>): BoardHeader {
  val update = run.toUpdate(names)
  if (update != null) {
    val detail =
      update.percent?.let {
        stringResource(R.string.myzel_display_repo_step, update.number, update.total, it)
      } ?: stringResource(R.string.myzel_display_repo_number, update.number, update.total)
    return BoardHeader(update.displayText(), StatusTone.ACCENT, detail)
  }
  val failed = run.repoIds.filter { run.outcome(it) == RepoOutcome.FAILED }
  val changed = run.repoIds.filter { run.outcome(it) == RepoOutcome.CHANGED }
  val open = run.repoIds.count { run.step(it) !is RepoUpdateStep.Done }
  val name = { ids: List<Long> -> ids.singleOrNull()?.let { names[it] }?.ifEmpty { null } }
  val failedName = name(failed)
  val changedName = name(changed)
  val (text, tone) =
    when {
      open > 0 -> stringResource(R.string.myzel_board_cancelled) to StatusTone.NEUTRAL
      failedName != null ->
        stringResource(R.string.myzel_board_failed_one, failedName) to StatusTone.ERROR
      failed.isNotEmpty() ->
        pluralStringResource(R.plurals.myzel_board_failed, failed.size, failed.size) to
          StatusTone.ERROR
      changedName != null ->
        stringResource(R.string.myzel_board_changed_one, changedName) to StatusTone.POSITIVE
      changed.isNotEmpty() ->
        pluralStringResource(R.plurals.myzel_board_changed, changed.size, changed.size) to
          StatusTone.POSITIVE
      else -> stringResource(R.string.myzel_display_repos_current) to StatusTone.POSITIVE
    }
  val seconds = seconds(run.durationMillis, LocalConfiguration.current.locales[0])
  val detail = pluralStringResource(R.plurals.myzel_board_summary, run.total, run.total, seconds)
  return BoardHeader(text, tone, detail)
}

private fun RepoUpdateRun.outcome(repoId: Long) = (step(repoId) as? RepoUpdateStep.Done)?.outcome

/** „0,8“ oder „12“: unter zehn Sekunden mit einer Nachkommastelle. */
private fun seconds(millis: Long, locale: Locale): String {
  val seconds = millis / 1000.0
  return String.format(locale, if (seconds < 10) "%.1f" else "%.0f", seconds)
}

/**
 * Eine Zeile je Paketquelle, bei mehr als [MAX_ROWS] nur ein Ausschnitt um die aktuelle, der
 * mitläuft. Beim Aus- und Einrollen kommen und gehen die Zeilen nacheinander, beim Mitlaufen
 * sofort.
 */
@Composable
private fun RepoBoardRows(run: RepoUpdateRun, names: Map<Long, String>, visible: Boolean) {
  val richMotion = LocalDesignEffects.current.richMotion
  val window = rowWindow(run)
  var settled by remember { mutableStateOf(visible) }
  val rolling = visible != settled
  LaunchedEffect(visible) {
    delay(ROLL_MILLIS)
    settled = visible
  }
  Column(Modifier.fillMaxWidth()) {
    run.repoIds.forEachIndexed { index, repoId ->
      key(repoId) {
        val enterDelay = if (rolling) (index - window.first) * ROW_STAGGER_MILLIS else 0
        val exitDelay = if (rolling) (window.last - index) * ROW_STAGGER_MILLIS / 2 else 0
        AnimatedVisibility(
          visible = visible && index in window,
          enter =
            if (!richMotion) EnterTransition.None
            else
              expandVertically(
                tween(ROW_MILLIS, enterDelay, NeedanappMotion.EmphasizedDecelerate),
                expandFrom = Alignment.Top,
              ) + fadeIn(tween(ROW_MILLIS, enterDelay + ROW_MILLIS / 3)),
          exit =
            if (!richMotion) ExitTransition.None
            else
              shrinkVertically(
                tween(ROW_MILLIS * 2 / 3, exitDelay, NeedanappMotion.EmphasizedAccelerate),
                shrinkTowards = Alignment.Top,
              ) + fadeOut(tween(ROW_MILLIS / 2, exitDelay)),
        ) {
          RepoBoardRow(
            name = names[repoId].orEmpty(),
            step = run.step(repoId),
            current = !run.isFinished && repoId == run.current,
          )
        }
      }
    }
  }
}

/**
 * Welche Zeilen zu sehen sind: alle, oder [MAX_ROWS] um die aktuelle Paketquelle, eine fertige
 * darüber. Am Ende um die erste, die sich nicht laden ließ, sonst die letzten.
 */
private fun rowWindow(run: RepoUpdateRun): IntRange {
  val total = run.total
  if (total <= MAX_ROWS) return 0 until total
  val focus =
    if (!run.isFinished) run.number - 1
    else
      run.repoIds.indexOfFirst { run.outcome(it) == RepoOutcome.FAILED }.takeIf { it >= 0 }
        ?: (total - 1)
  val first = (focus - 1).coerceIn(0, total - MAX_ROWS)
  return first until first + MAX_ROWS
}

/** Name, Punktreihe und Ergebnis einer Paketquelle; [current] leuchtet ihr Name. */
@Composable
private fun RepoBoardRow(name: String, step: RepoUpdateStep, current: Boolean) {
  val design = LocalDesignColors.current
  val lit = StatusTone.ACCENT.displayColor()
  val style = DesignType.value.copy(fontSize = 11.sp, lineHeight = 14.sp)
  val description = stringResource(R.string.myzel_board_row, name, stepDescription(step))
  val (symbol, symbolColor) =
    when (step) {
      RepoUpdateStep.Waiting -> "·" to design.displayDim
      is RepoUpdateStep.Loading -> "${step.percent} %" to lit
      is RepoUpdateStep.Saving -> (step.percent?.let { "$it %" } ?: "") to lit
      is RepoUpdateStep.Done ->
        when (step.outcome) {
          RepoOutcome.CHANGED -> (if (step.apps > 0) "+${step.apps}" else "+") to design.ledGreen
          RepoOutcome.UNCHANGED -> "=" to design.displayDim
          RepoOutcome.FAILED -> "!" to design.ledRed
        }
      else -> "" to lit
    }
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      Modifier.fillMaxWidth().padding(top = RowGap).height(RowHeight).clearAndSetSemantics {
        contentDescription = description
      },
  ) {
    Text(
      name.uppercase(),
      style = style,
      color = if (current) lit else design.displayDim,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(NAME_WEIGHT),
    )
    Spacer(Modifier.width(10.dp))
    StepDots(step, lit, Modifier.weight(1f - NAME_WEIGHT))
    Spacer(Modifier.width(10.dp))
    Text(
      symbol,
      style = style,
      color = symbolColor,
      textAlign = TextAlign.End,
      maxLines = 1,
      modifier = Modifier.width(SymbolWidth),
    )
  }
}

@Composable
private fun stepDescription(step: RepoUpdateStep): String =
  when (step) {
    RepoUpdateStep.Waiting -> stringResource(R.string.myzel_board_waiting)
    RepoUpdateStep.Checking -> stringResource(R.string.myzel_board_checking)
    is RepoUpdateStep.Loading -> stringResource(R.string.myzel_board_loading, step.percent)
    RepoUpdateStep.Loaded -> stringResource(R.string.myzel_board_loaded)
    is RepoUpdateStep.Saving ->
      step.percent?.let { stringResource(R.string.myzel_board_saving, it) }
        ?: stringResource(R.string.myzel_board_saving_plain)
    is RepoUpdateStep.Done ->
      when (step.outcome) {
        RepoOutcome.CHANGED ->
          if (step.apps > 0)
            pluralStringResource(R.plurals.myzel_board_changed_apps, step.apps, step.apps)
          else stringResource(R.string.myzel_board_changed_plain)
        RepoOutcome.UNCHANGED -> stringResource(R.string.myzel_board_unchanged)
        RepoOutcome.FAILED -> stringResource(R.string.myzel_board_failed_row)
      }
  }

/**
 * Die Punktreihe einer Paketquelle: Die erste Hälfte füllt sich beim Laden, die zweite beim
 * Speichern, der letzte Punkt blinkt dabei. Beim Prüfen läuft ein einzelner Punkt durch (beim
 * Speichern ohne bekannte Zahl der Apps durch die zweite Hälfte). Fertig leuchtet die Reihe ganz,
 * unverändert gedämpft, nach einem Fehler bleibt sie aus.
 */
@Composable
private fun StepDots(step: RepoUpdateStep, lit: Color, modifier: Modifier) {
  val design = LocalDesignColors.current
  val richMotion = LocalDesignEffects.current.richMotion
  val fraction by
    animateFloatAsState(
      if (step is RepoUpdateStep.Done && step.outcome == RepoOutcome.FAILED) 0f else step.fraction,
      if (richMotion) tween(260, easing = NeedanappMotion.Standard) else snap(),
      label = "boardDots",
    )
  val scanning =
    step == RepoUpdateStep.Checking || step is RepoUpdateStep.Saving && step.percent == null
  val filling = step is RepoUpdateStep.Loading || step is RepoUpdateStep.Saving && !scanning
  var scan = 0f
  var blink = 1f
  if (richMotion && (scanning || filling)) {
    val transition = rememberInfiniteTransition(label = "boardRow")
    val scanValue by
      transition.animateFloat(
        0f,
        1f,
        infiniteRepeatable(tween(SCAN_MILLIS, easing = LinearEasing)),
        label = "boardScan",
      )
    val blinkValue by
      transition.animateFloat(
        1f,
        0.2f,
        infiniteRepeatable(tween(BLINK_MILLIS, easing = LinearEasing), RepeatMode.Reverse),
        label = "boardBlink",
      )
    scan = scanValue
    blink = blinkValue
  }
  val color =
    if (step is RepoUpdateStep.Done && step.outcome == RepoOutcome.UNCHANGED) design.displayDim
    else lit
  val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  Canvas(modifier.height(DotSize)) {
    val dot = DotSize.toPx()
    val gap = DotGap.toPx()
    val count = floor((size.width + gap) / (dot + gap)).toInt().coerceAtLeast(1)
    val litCount = (fraction * count).roundToInt()
    val scanAt =
      if (!scanning) -1
      else litCount + (scan * (count - litCount)).toInt().coerceAtMost(count - litCount - 1)
    val corner = CornerRadius(1.dp.toPx())
    for (i in 0 until count) {
      val x = if (rtl) size.width - dot - i * (dot + gap) else i * (dot + gap)
      val dotColor =
        when {
          i == scanAt -> lit
          i < litCount && filling && i == litCount - 1 -> color.copy(alpha = color.alpha * blink)
          i < litCount -> color
          else -> design.displayOff
        }
      drawRoundRect(dotColor, Offset(x, 0f), Size(dot, dot), corner)
    }
  }
}
