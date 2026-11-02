package de.needanapp.myzel.ui

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.fdroid.R

/*
 * Das Logo auf der Seite „Über Myzel“ hat ein Geheimnis, wie in Hypha und auf kiez.dev, mit Ablauf
 * und Zahlen von dort: 26 Tipps, ohne dass man sieht, dass sie zählen, lassen 8 s lang Regenbogen
 * regnen (jeder 20. ein Einhorn), während sich an der Stelle des Logos eine Plakette dreht; dann
 * kommt das Logo zurück, ein Banner schwebt von oben ein, bleibt 4 s und schwebt hinaus, während die
 * Seite ausblendet und sich schließt. Verlassen der Seite zählt neu. Die Bilder heißen absichtlich
 * nichtssagend: s4 die Plakette, s2 das Banner.
 */

private const val TAPS = 26

private const val SWAP_MS = 300
private const val RAIN_MS = 8_000L

/**
 * Vom Ende des Regens, bis seine Fläche geht; lang genug, dass die letzten Tropfen hinausfallen.
 */
private const val RAIN_GONE_MS = 5_000L
private const val FLOAT_MS = 1_400
private const val STAY_MS = 4_000L
private const val TURN_MS = 2_400

/**
 * Alle [BATCH_MS] ms [BATCH] neue Tropfen; nach längerer Pause (Bildschirm aus) holt der Takt nicht
 * auf.
 */
private const val BATCH_MS = 120L
private const val BATCH = 4
private const val CATCH_UP_MS = 500L
private const val RARE = 20

/** ease-in-out wie in CSS und der Bogen, auf dem das Banner schwebt. */
private val EaseInOut = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)
private val Arc = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)

private val Rainbow = String(Character.toChars(0x1F308))
private val Unicorn = String(Character.toChars(0x1F984))

/**
 * Größe eines Tropfens als Anteil von [Rem], 0,7 bis 2 (kleine öfter); sein Bild hat Rand
 * drumherum.
 */
private val Rem = 16.dp
private const val MIN_SIZE = 0.7f
private const val SIZE_RANGE = 1.3f
private const val SPRITE_ROOM = 1.4f

/** Wie weit Tropfen seitlich schwingen und wie weit sie sich dabei neigen. */
private val Swing = 10.dp
private const val TILT = 8f

/** Die Drehung der Plakette aus 600 dp Abstand; die Kamera rechnet in Zoll zu 72 Pixeln. */
private val Perspective = 600.dp
private const val PIXELS_PER_INCH = 72f

private val BannerHeight = 260.dp
private const val BANNER_WIDTH = 0.9f

/** Wo das Banner über seinem Platz wartet. */
private val BannerFrom = 300.dp

/**
 * Was das Logo in Gang setzt; lebt so lange wie die Seite und läuft in ihrem [scope], auch wenn das
 * Logo aus dem Bild scrollt. [onClose] schließt sie am Ende; ohne (große Bildschirme, wo „Über
 * Myzel“ neben der Liste steht) blendet sie wieder ein.
 */
@Stable
internal class AboutLogoState(
  private val onClose: State<(() -> Unit)?>,
  private val scope: CoroutineScope,
) {
  private var taps = 0
  private var running = false

  /** Die Deckkraft des Logos, während es der Plakette weicht und zurückkommt. */
  val shown = Animatable(1f)
  var plaque by mutableStateOf(false)
    private set

  var raining by mutableStateOf(false)
    private set

  /** Ob neue Tropfen kommen; einmal je Bild gelesen. */
  var pouring = false
    private set

  var bannerShown by mutableStateOf(false)
    private set

  /** 0 über seinem Platz, 1 darin. */
  val banner = Animatable(0f)
  val fade = Animatable(1f)

  /** Zählt einen Tipp und setzt es beim 26. in Gang, nie während es läuft. */
  fun tap() {
    if (running || ++taps < TAPS) return
    taps = 0
    running = true
    scope.launch { play() }
  }

  private suspend fun play() = coroutineScope {
    raining = true
    pouring = true
    launch { swap(toPlaque = true) }
    delay(RAIN_MS)
    pouring = false
    launch { swap(toPlaque = false) }
    launch {
      delay(RAIN_GONE_MS)
      raining = false
    }
    bannerShown = true
    banner.animateTo(1f, tween(FLOAT_MS, easing = Arc))
    delay(STAY_MS)
    launch { banner.animateTo(0f, tween(FLOAT_MS, easing = Arc)) }
    fade.animateTo(0f, tween(FLOAT_MS, easing = EaseInOut))
    val close = onClose.value
    if (close != null) {
      close()
    } else {
      bannerShown = false
      fade.animateTo(1f, tween(FLOAT_MS, easing = EaseInOut))
      running = false
    }
  }

  private suspend fun swap(toPlaque: Boolean) {
    shown.animateTo(0f, tween(SWAP_MS, easing = EaseInOut))
    plaque = toPlaque
    shown.animateTo(1f, tween(SWAP_MS, easing = EaseInOut))
  }
}

/**
 * Die Seite „Über Myzel“ mit Regen und Banner darüber; alles blendet mit [AboutLogoState.fade] aus.
 * [content] bekommt den Zustand für [AboutLogo].
 */
@Composable
internal fun AboutLogoFrame(
  onClose: (() -> Unit)?,
  modifier: Modifier = Modifier,
  content: @Composable (AboutLogoState) -> Unit,
) {
  val close = rememberUpdatedState(onClose)
  val scope = rememberCoroutineScope()
  val state = remember { AboutLogoState(close, scope) }
  Box(modifier.graphicsLayer { alpha = state.fade.value }) {
    content(state)
    if (state.raining) Rain(state, Modifier.matchParentSize())
    if (state.bannerShown) Banner(state)
  }
}

/**
 * Das Logo in [size]; Tipps darauf zählen ohne jede Spur, ohne Welle, nichts zum Vorlesen. [logo]
 * ist das Logo, wie es die Seite ohnehin zeigt.
 */
@Composable
internal fun AboutLogo(
  state: AboutLogoState,
  size: Dp,
  modifier: Modifier = Modifier,
  logo: @Composable BoxScope.() -> Unit,
) {
  Box(
    modifier
      .size(size)
      .graphicsLayer { alpha = state.shown.value }
      .pointerInput(state) { detectTapGestures { state.tap() } },
    contentAlignment = Alignment.Center,
  ) {
    if (state.plaque) Plaque() else logo()
  }
}

/**
 * Die Plakette an der Stelle des Logos, die sich um ihre senkrechte Achse dreht, mit weichem
 * Schatten.
 */
@Composable
private fun Plaque() {
  val turn =
    rememberInfiniteTransition(label = "plaque")
      .animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(TURN_MS, easing = LinearEasing)),
        label = "turn",
      )
  val picture = painterResource(R.drawable.s4)
  Box(
    Modifier.fillMaxSize().graphicsLayer {
      rotationY = turn.value
      cameraDistance = Perspective.toPx() / PIXELS_PER_INCH
    }
  ) {
    Image(
      picture,
      contentDescription = null,
      colorFilter = ColorFilter.tint(Color.Black.copy(alpha = 0.35f)),
      modifier = Modifier.fillMaxSize().offset(y = 6.dp).blur(8.dp, BlurredEdgeTreatment.Unbounded),
    )
    Image(picture, contentDescription = null, modifier = Modifier.fillMaxSize())
  }
}

/**
 * Ein Tropfen: wo quer (Anteil der Breite), wie groß, wann er begann, wie lange er fällt, wie er
 * schwingt.
 */
private class Drop(
  val x: Float,
  val size: Float,
  val start: Long,
  val duration: Float,
  val swing: Float,
  val phase: Float,
  val sprite: ImageBitmap,
)

/**
 * Fallende Regenbogen auf einer Fläche, jeder einmal als Bild gezeichnet und dann nur kopiert: etwa
 * 150 fallen zugleich. Die Bildschleife fügt sie hinzu und entfernt sie, das Zeichnen liest nur.
 */
@Composable
private fun Rain(state: AboutLogoState, modifier: Modifier) {
  val rem = with(LocalDensity.current) { Rem.toPx() }
  val rainbow = remember(rem) { sprite(Rainbow, rem) }
  val unicorn = remember(rem) { sprite(Unicorn, rem) }
  val drops = remember { ArrayList<Drop>() }
  var now by remember { mutableLongStateOf(0L) }
  LaunchedEffect(rem) {
    var next = -1L
    var count = 0
    while (true) {
      val time = withFrameMillis { it }
      if (next < 0 || time - next > CATCH_UP_MS) next = time
      while (state.pouring && time >= next) {
        repeat(BATCH) {
          count++
          drops +=
            Drop(
              x = Random.nextFloat(),
              size = (MIN_SIZE + Random.nextFloat() * Random.nextFloat() * SIZE_RANGE) * rem,
              start = next,
              duration = 2_000f + Random.nextFloat() * 2_500f,
              swing = 800f + Random.nextFloat() * 800f,
              phase = Random.nextFloat() * 2_000f,
              // genau jeder 20., nicht nur im Mittel
              sprite = if (count % RARE == 0) unicorn else rainbow,
            )
        }
        next += BATCH_MS
      }
      drops.removeAll { time - it.start >= it.duration }
      now = time
    }
  }
  Canvas(modifier) {
    val time = now
    val swing = Swing.toPx()
    for (drop in drops) {
      val part = ((time - drop.start) / drop.duration).coerceAtLeast(0f)
      val wave = -cos(2 * PI * ((time - drop.start + drop.phase) / drop.swing)).toFloat()
      val x = drop.x * size.width + drop.size / 2 + wave * swing
      val y = (-0.1f + 1.2f * part) * size.height + drop.size / 2
      val side = drop.size * SPRITE_ROOM
      rotate(wave * TILT, pivot = Offset(x, y)) {
        translate(x - side / 2, y - side / 2) {
          drawImage(drop.sprite, dstSize = IntSize(side.roundToInt(), side.roundToInt()))
        }
      }
    }
  }
}

/**
 * [text] einmal in der größten Tropfengröße, mit Rand für Emoji, die über die Schriftgröße reichen.
 */
private fun sprite(text: String, rem: Float): ImageBitmap {
  val side = ceil((MIN_SIZE + SIZE_RANGE) * rem * SPRITE_ROOM).toInt()
  val bitmap = Bitmap.createBitmap(side, side, Bitmap.Config.ARGB_8888)
  val paint =
    Paint(Paint.ANTI_ALIAS_FLAG).apply {
      textSize = side / SPRITE_ROOM
      textAlign = Paint.Align.CENTER
    }
  val metrics = paint.fontMetrics
  android.graphics
    .Canvas(bitmap)
    .drawText(text, side / 2f, side / 2f - (metrics.ascent + metrics.descent) / 2, paint)
  return bitmap.asImageBitmap()
}

/**
 * Das Banner oben unter der Statusleiste, 260 dp hoch und höchstens 90 % breit, schwebt ein und
 * aus.
 */
@Composable
private fun BoxScope.Banner(state: AboutLogoState) {
  val picture = painterResource(R.drawable.s2)
  val ratio = picture.intrinsicSize.width / picture.intrinsicSize.height
  BoxWithConstraints(Modifier.matchParentSize().windowInsetsPadding(WindowInsets.statusBars)) {
    val width = minOf(BannerHeight * ratio, maxWidth * BANNER_WIDTH)
    Image(
      picture,
      contentDescription = null,
      modifier =
        Modifier.align(Alignment.TopCenter)
          .offset { IntOffset(0, ((state.banner.value - 1f) * BannerFrom.toPx()).roundToInt()) }
          .size(width, width / ratio),
    )
  }
}
