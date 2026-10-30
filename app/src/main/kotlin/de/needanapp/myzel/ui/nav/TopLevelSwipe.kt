package de.needanapp.myzel.ui.nav

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import de.needanapp.design.theme.LocalDesignEffects
import kotlin.math.abs
import kotlin.math.sqrt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.fdroid.ui.navigation.topLevelRoutes

/**
 * Wischen zwischen den Bereichen der Pille ([topLevelRoutes]) wie bei einem Seitenwechsler: Die
 * Seiten folgen dem Finger ([TopLevelSwipe.offset], gezeichnet von [TopLevelPages]), beim Loslassen
 * geht es zum Nachbarn, wenn mehr als ein Drittel gezogen oder schnell gewischt wurde, sonst federt
 * die Seite zurück. Am Rand gibt sie nur ein Stück nach. Was selbst waagrecht scrollt, etwa das
 * Karussell in Entdecken, geht vor: Es bekommt die Bewegung zuerst und verbraucht sie. Nur mit
 * [enabled], also wenn die Pille zu sehen ist.
 */
@Composable
fun Modifier.topLevelSwipe(
  currentNavKey: NavKey,
  enabled: Boolean,
  onNav: (NavKey) -> Unit,
): Modifier {
  val index = topLevelRoutes.indexOf(currentNavKey)
  if (!enabled || index < 0) return this
  val currentIndex by rememberUpdatedState(index)
  val nav by rememberUpdatedState(onNav)
  val moves by rememberUpdatedState(LocalDesignEffects.current.richMotion)
  val scope = rememberCoroutineScope()
  val density = LocalDensity.current
  val velocity = with(density) { SWIPE_VELOCITY.toPx() }
  val edge = with(density) { EDGE_GIVE.toPx() }
  return pointerInput(Unit) {
    val tracker = VelocityTracker()
    detectHorizontalDragGestures(
      onDragStart = {
        tracker.resetTracking()
        TopLevelSwipe.grab()
      },
      onHorizontalDrag = { change, amount ->
        tracker.addPosition(change.uptimeMillis, change.position)
        TopLevelSwipe.drag(
          amount = amount,
          width = size.width.toFloat(),
          edge = edge,
          hasPrevious = currentIndex > 0,
          hasNext = currentIndex < topLevelRoutes.lastIndex,
        )
      },
      onDragEnd = {
        val width = size.width.toFloat()
        val speed = tracker.calculateVelocity().x
        val offset = TopLevelSwipe.offset
        // ein schneller Wisch zählt in seiner Richtung, sonst ein Drittel der Breite; nach links
        // gezogen heißt weiter
        val step =
          when {
            abs(speed) >= velocity && speed * offset > 0 -> if (speed < 0) 1 else -1
            abs(offset) >= width / 3 -> if (offset < 0) 1 else -1
            else -> 0
          }
        val target = topLevelRoutes.getOrNull(currentIndex + step)
        if (step != 0 && target != null) {
          // die neue Seite bleibt, wo sie gerade steht, und gleitet von dort an ihren Platz
          TopLevelSwipe.handOver(step * width)
          nav(target)
        }
        TopLevelSwipe.settle(scope, speed, animate = moves)
      },
      onDragCancel = { TopLevelSwipe.settle(scope, 0f, animate = moves) },
    )
  }
}

/**
 * Wie weit die Hauptseiten gerade aus ihrer Ruhelage verschoben sind, in Pixeln: negativ nach links
 * (der rechte Nachbar kommt herein), positiv nach rechts. Ein Tipp auf die Pille setzt es zurück
 * ([clear]).
 */
internal object TopLevelSwipe {
  var offset by mutableFloatStateOf(0f)
    private set

  private var settling: Job? = null

  /** Der Finger greift zu, auch mitten im Zurückgleiten. */
  fun grab() {
    settling?.cancel()
    settling = null
  }

  /** Folgt dem Finger um [amount]; hinter der ersten oder letzten Seite nur zäh und bis [edge]. */
  fun drag(amount: Float, width: Float, edge: Float, hasPrevious: Boolean, hasNext: Boolean) {
    val moved = offset + amount
    val beyond = (moved < 0 && !hasNext) || (moved > 0 && !hasPrevious)
    offset =
      (if (beyond) offset + amount * EDGE_RESISTANCE else moved).coerceIn(
        if (hasNext) -width else -edge,
        if (hasPrevious) width else edge,
      )
  }

  /** Beim Wechsel um [shift] zur Nachbarseite: Die bisherige Lage gilt nun für die neue Seite. */
  fun handOver(shift: Float) {
    offset += shift
  }

  /** Gleitet in die Ruhelage, mit der Geschwindigkeit des Fingers; ohne Bewegung sofort. */
  fun settle(scope: CoroutineScope, velocity: Float, animate: Boolean) {
    settling?.cancel()
    if (!animate || offset == 0f) {
      offset = 0f
      return
    }
    // eine kritisch gedämpfte Feder schießt nicht über, solange sie nicht schneller startet
    val start = offset
    val limit = sqrt(SETTLE_STIFFNESS) * abs(start)
    val initialVelocity = velocity.coerceIn(-limit, limit)
    settling = scope.launch {
      animate(
        initialValue = start,
        targetValue = 0f,
        initialVelocity = initialVelocity,
        animationSpec = spring(Spring.DampingRatioNoBouncy, SETTLE_STIFFNESS),
      ) { value, _ ->
        offset = value
      }
    }
  }

  fun clear() {
    settling?.cancel()
    settling = null
    offset = 0f
  }
}

/** Ab dieser Geschwindigkeit (je Sekunde) zählt auch ein kurzer Wisch. */
private val SWIPE_VELOCITY = 700.dp

/** So weit gibt die erste oder letzte Seite am Rand nach. */
private val EDGE_GIVE = 48.dp
private const val EDGE_RESISTANCE = 0.3f

private const val SETTLE_STIFFNESS = 500f
