package de.needanapp.myzel.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import de.needanapp.design.theme.LocalDesignEffects
import kotlin.math.hypot

/** Farbe des Myzel-Logos wie im Symbol (myzel/icon/myzel-icon-geflecht.svg). */
val MyzelLogoColor = Color(0xFFC8772E)

/** Wie das Signal im Logo aussieht. */
enum class MyzelSignalStyle {
  /** Auf der Graphit-Taste: hell aufleuchtende Impulse über dem Logo in seiner Farbe. */
  GRAPHITE,

  /** Beim Favoriten auf hellem oder dunklem Grund: die Impulse in der Farbe des Logos. */
  FAVORITE,
}

/**
 * Das Myzel-Logo, gezeichnet aus denselben Pfaden wie R.drawable.myzel_logo, in [color]. Ändert
 * sich [signal], läuft einmal ein dezentes Signal wie auf myzel.dev hindurch: zwei Impulse vom Kern
 * zu den Spitzen, die Wege glimmen kurz nach, an den Enden blitzt ein kleiner Ring auf. Ist die
 * Bewegung ausgeschaltet, leuchten die Wege nur kurz auf.
 */
@Composable
fun MyzelLogo(
  modifier: Modifier = Modifier,
  color: Color = MyzelLogoColor,
  signal: Int = 0,
  style: MyzelSignalStyle = MyzelSignalStyle.GRAPHITE,
) {
  val richMotion = LocalDesignEffects.current.richMotion
  val time = remember { Animatable(IDLE) }
  // ein Signal nur nach dem Tippen, nicht schon beim ersten Zeichnen
  val firstSignal = remember { signal }
  LaunchedEffect(signal) {
    if (signal == firstSignal) return@LaunchedEffect
    val from = if (richMotion) 0f else MyzelGeometry.arrival
    val to = if (richMotion) MyzelGeometry.duration else MyzelGeometry.arrival + FLASH
    time.snapTo(from)
    time.animateTo(to, tween(((to - from) * 1000).toInt(), easing = LinearEasing))
    time.snapTo(IDLE)
  }
  Canvas(modifier) {
    val t = time.value
    val unit = size.minDimension / VIEW
    withTransform({
      translate((size.width - VIEW * unit) / 2, (size.height - VIEW * unit) / 2)
      scale(unit, unit, Offset.Zero)
      if (t != IDLE && style == MyzelSignalStyle.GRAPHITE) {
        val press = pressScale(t)
        scale(press, press, Offset(VIEW / 2, VIEW / 2))
      }
      translate(-ORIGIN_X, -ORIGIN_Y)
    }) {
      drawBase(color)
      if (t != IDLE) drawSignal(t, style)
    }
  }
}

/** Wann der letzte Impuls an seiner Spitze ankommt, in Millisekunden nach dem Tippen. */
val MyzelSignalArrivalMillis: Long
  get() = (MyzelGeometry.arrival * 1000).toLong()

private fun DrawScope.drawBase(color: Color) {
  MyzelGeometry.strokes.forEach { (width, path) ->
    drawPath(path, color, style = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
  }
  MyzelGeometry.dots.forEach { drawCircle(color, it.radius, it.center) }
}

private fun DrawScope.drawSignal(t: Float, style: MyzelSignalStyle) {
  val graphite = style == MyzelSignalStyle.GRAPHITE
  val trail = MyzelLogoColor
  val trailMax = if (graphite) TRAIL_ALPHA else TRAIL_ALPHA_FAVORITE
  val head = if (graphite) Color(0xFFFFE6CC) else Color(0xFFF4A259)
  val blend = if (graphite) BlendMode.Screen else BlendMode.SrcOver
  val segment = Path()
  MyzelGeometry.signal.forEach { strand ->
    val a = strand.start
    val b = strand.end
    if (t < a) return@forEach
    // die Spur wächst mit dem Impuls und glimmt danach aus
    val reached = ((t - a) / (b - a)).coerceIn(0f, 1f) * strand.length
    val trailAlpha =
      when {
        t < a + 0.01f -> trailMax * (t - a) / 0.01f
        t <= b -> trailMax
        else -> trailMax * (1 - (t - b) / AFTERGLOW).coerceIn(0f, 1f)
      }
    if (trailAlpha > 0f && reached > 0f) {
      segment.reset()
      strand.measure.getSegment(0f, reached, segment, true)
      glowStroke(segment, trail, trailAlpha, strand.width, blend)
    }
    // der Kopf: ein kurzes helles Stück an der Spitze des Impulses
    if (t <= b + 0.02f) {
      val headAlpha = if (t <= b) HEAD_ALPHA else HEAD_ALPHA * (1 - (t - b) / 0.02f)
      val from = (reached - HEAD_LENGTH).coerceAtLeast(0f)
      if (reached > from) {
        segment.reset()
        strand.measure.getSegment(from, reached, segment, true)
        glowStroke(segment, head, headAlpha, strand.width * 1.25f, blend)
      }
    }
    // an der Spitze ein Ring, der aufblitzt und vergeht
    strand.tip?.let { tip ->
      val x = (t - b) / RING
      if (x in 0f..1f) {
        val alpha = if (x < 0.04f) 0.5f * x / 0.04f else 0.5f * (1 - easeOut((x - 0.04f) / 0.96f))
        val radius = tip.radius * (1.2f + 1.1f * easeOut(x))
        drawCircle(head, radius, tip.center, alpha = alpha, blendMode = blend)
      }
    }
  }
  // der Kern pulst einmal zu Beginn
  if (t < CORE) {
    val alpha =
      if (t < 0.07f) 0.45f * t / 0.07f else 0.45f * (1 - easeOut((t - 0.07f) / (CORE - 0.07f)))
    val radius =
      if (t < 0.07f) 6.2f + 0.8f * t / 0.07f else 7f + 4f * easeOut((t - 0.07f) / (CORE - 0.07f))
    drawCircle(head, radius, CORE_CENTER, alpha = alpha, blendMode = blend)
  }
}

/** Ein Strich mit weichem Schein darum, wie der Weichzeichner auf myzel.dev. */
private fun DrawScope.glowStroke(
  path: Path,
  color: Color,
  alpha: Float,
  width: Float,
  blend: BlendMode,
) {
  drawPath(
    path,
    color,
    alpha * 0.3f,
    Stroke(width + 2.6f, cap = StrokeCap.Round),
    blendMode = blend,
  )
  drawPath(path, color, alpha, Stroke(width, cap = StrokeCap.Round), blendMode = blend)
}

/** Das Logo gibt beim Tippen kurz nach wie eine gedrückte Taste. */
private fun pressScale(t: Float): Float =
  when {
    t < 0.09f -> 1 - 0.08f * t / 0.09f
    t < 0.29f -> 0.92f + 0.08f * (t - 0.09f) / 0.2f
    else -> 1f
  }

private fun easeOut(x: Float): Float = 1 - (1 - x) * (1 - x)

private class Dot(val center: Offset, val radius: Float)

private class Strand(
  val width: Float,
  val measure: PathMeasure,
  val length: Float,
  val start: Float,
  val end: Float,
  val tip: Dot?,
)

/** Die Pfade des Logos (Ansicht 108 × 108) und der Zeitplan des Signals, einmal berechnet. */
private object MyzelGeometry {
  private fun path(d: String): Path = PathParser().parsePathString(d).toPath()

  val strokes: List<Pair<Float, Path>> =
    listOf(
      4f to
        path(
          "M54 54 C49.1 47.9 40.6 43 32 36.9 M54 54 C61.3 47.9 68.6 40.6 73.5 29.6 " +
            "M54 54 C63.8 56.4 74.7 58.9 83.3 63.8 M54 54 C52.8 62.5 46.7 72.3 39.4 78.4"
        ),
      2.8f to
        path(
          "M42.4 43.6 C40.6 36.9 41.2 30.8 44.2 24.7 M66.2 44.2 C72.3 44.9 77.2 43 83.3 41.8 " +
            "M71.1 60.3 C74.1 66.2 73.5 72.3 69.9 78.4 M47.9 68.6 C41.8 65 34.5 65 27.2 66.2 " +
            "M33.9 38.1 C28.4 39.4 24.1 44.2 22.3 49.1 M74.7 58.9 C78.4 52.8 85.7 51.6 86.9 55.2"
        ),
      2f to
        path("M61.9 47.3 C60.1 41.8 58.9 38.1 59.5 34.5 M44.2 71.1 C46.7 76 50.3 80.8 55.2 83.3"),
      1.8f to
        path(
          "M44.2 24.7 C50 26.5 55.5 29.5 59.5 34.5 M27.2 66.2 C30 72 34 76 39.4 78.4 " +
            "M83.3 41.8 C86 46 87.5 50.5 86.9 55.2 M69.9 78.4 C65 81.5 60 83.5 55.2 83.3"
        ),
    )

  val dots: List<Dot> =
    listOf(
        Triple(54f, 54f, 6.2f),
        Triple(32f, 36.9f, 3.4f),
        Triple(73.5f, 29.6f, 3.4f),
        Triple(83.3f, 63.8f, 3.4f),
        Triple(39.4f, 78.4f, 3.4f),
        Triple(44.2f, 24.7f, 2.7f),
        Triple(83.3f, 41.8f, 2.7f),
        Triple(69.9f, 78.4f, 2.7f),
        Triple(27.2f, 66.2f, 2.7f),
        Triple(22.3f, 49.1f, 2.7f),
        Triple(86.9f, 55.2f, 2.7f),
        Triple(59.5f, 34.5f, 2.1f),
        Triple(55.2f, 83.3f, 2.1f),
      )
      .map { (x, y, r) -> Dot(Offset(x, y), r) }

  /**
   * Die beiden Wege des Signals: Kern → oben links mit Abzweig nach oben, Kern → rechts mit
   * Abzweig. Ein Abzweig startet, wenn der Impuls seines Fadens an ihm vorbeikommt.
   */
  val signal: List<Strand> = buildList {
    val starts = mutableMapOf<String, Pair<PathMeasure, Float>>()
    listOf(
        StrandDef(
          "m1",
          4f,
          "M54 54 C49.1 47.9 40.6 43 32 36.9",
          null,
          Dot(Offset(32f, 36.9f), 3.4f),
        ),
        StrandDef(
          "s1",
          2.8f,
          "M42.4 43.6 C40.6 36.9 41.2 30.8 44.2 24.7",
          "m1",
          Dot(Offset(44.2f, 24.7f), 2.7f),
        ),
        StrandDef("a1", 1.8f, "M44.2 24.7 C50 26.5 55.5 29.5 59.5 34.5", "s1", null),
        StrandDef(
          "m3",
          4f,
          "M54 54 C63.8 56.4 74.7 58.9 83.3 63.8",
          null,
          Dot(Offset(83.3f, 63.8f), 3.4f),
        ),
        StrandDef(
          "s6",
          2.8f,
          "M74.7 58.9 C78.4 52.8 85.7 51.6 86.9 55.2",
          "m3",
          Dot(Offset(86.9f, 55.2f), 2.7f),
        ),
      )
      .forEach { def ->
        val measure = PathMeasure().apply { setPath(path(def.d), false) }
        val start =
          def.parent?.let { parent ->
            val (parentMeasure, parentStart) = starts.getValue(parent)
            parentStart + nearestDistance(parentMeasure, measure.getPosition(0f)) / SPEED
          } ?: FIRST_START
        starts[def.name] = measure to start
        add(
          Strand(def.width, measure, measure.length, start, start + measure.length / SPEED, def.tip)
        )
      }
  }

  /** Wann der letzte Impuls ankommt und wann alles verglommen ist, in Sekunden. */
  val arrival: Float = signal.maxOf { it.end }
  val duration: Float = maxOf(arrival + AFTERGLOW, signal.maxOf { it.end + RING })

  private class StrandDef(
    val name: String,
    val width: Float,
    val d: String,
    val parent: String?,
    val tip: Dot?,
  )

  /** Wie weit entlang [measure] der Punkt liegt, der [point] am nächsten ist. */
  private fun nearestDistance(measure: PathMeasure, point: Offset): Float {
    val length = measure.length
    return (0..SAMPLES)
      .map { it * length / SAMPLES }
      .minBy { d ->
        val p = measure.getPosition(d)
        hypot(p.x - point.x, p.y - point.y)
      }
  }
}

/** Der sichtbare Ausschnitt des Logos wie in R.drawable.myzel_logo. */
private const val VIEW = 70f
private const val ORIGIN_X = 19.6f
private const val ORIGIN_Y = 19f
private val CORE_CENTER = Offset(54f, 54f)

/** Tempo der Impulse in Einheiten des Logos pro Sekunde, Zeiten in Sekunden. */
private const val SPEED = 70f
private const val FIRST_START = 0.06f
private const val AFTERGLOW = 0.9f
private const val RING = 0.7f
private const val CORE = 0.6f
private const val FLASH = 0.5f
private const val HEAD_LENGTH = 4.5f
private const val TRAIL_ALPHA = 0.5f
private const val TRAIL_ALPHA_FAVORITE = 0.6f
private const val HEAD_ALPHA = 0.85f
private const val SAMPLES = 200
private const val IDLE = -1f
