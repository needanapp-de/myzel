package de.needanapp.myzel.ui.apps

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import org.fdroid.download.PackageName
import org.fdroid.ui.utils.AsyncShimmerImage

/** Anteil des weißen Kreises, den ein Symbol aus der Paketquelle einnimmt. */
private const val ICON_FRACTION = 0.74f

/** Eckenradius eines installierten Symbols: 16 dp bei 69,6 dp, anteilig zur Größe. */
private const val CORNER_FRACTION = 16f / 69.6f

/**
 * App-Symbol der Karten und des Kopfs der App-Details: Installierte Apps zeigen ihr Launcher-Symbol
 * abgerundet (bei Pixel-Geräten ist es schon rund). Symbole aus der Paketquelle sind meist
 * durchsichtig und stehen deshalb im weißen Kreis, wie auf der Website des Repos. Mit [shape] haben
 * beide diese Form, etwa die abgerundeten Quadrate des Designs needanapp.
 */
@Composable
fun IconCircle(model: Any?, size: Dp, modifier: Modifier = Modifier, shape: Shape? = null) {
  if (model is PackageName) {
    AsyncShimmerImage(
      model = model,
      contentDescription = null,
      contentScale = ContentScale.Crop,
      modifier =
        modifier.size(size).clip(shape ?: RoundedCornerShape(size * CORNER_FRACTION)).semantics {
          hideFromAccessibility()
        },
    )
    return
  }
  Box(
    contentAlignment = Alignment.Center,
    modifier =
      modifier.size(size).clip(shape ?: CircleShape).background(Color.White).semantics {
        hideFromAccessibility()
      },
  ) {
    AsyncShimmerImage(
      model = model,
      contentDescription = null,
      contentScale = ContentScale.Fit,
      modifier = Modifier.fillMaxSize(ICON_FRACTION),
    )
  }
}
