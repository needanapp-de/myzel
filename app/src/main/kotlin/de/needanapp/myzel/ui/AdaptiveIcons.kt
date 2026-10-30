package de.needanapp.myzel.ui

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import androidx.core.graphics.createBitmap
import androidx.core.graphics.drawable.toBitmap
import coil3.Image
import coil3.asImage

/**
 * Android zeichnet ein [AdaptiveIconDrawable] immer auf Schwarz. Ein durchsichtiger Hintergrund wie
 * der von Myzel (seit 0.13.0) erscheint dann in der App als schwarze Fläche, ebenso bei jeder
 * anderen App mit durchsichtigem Hintergrund. Hier kommen Hintergrund und Vordergrund auf ein
 * durchsichtiges Bitmap und werden mit der Maske des Systems ausgeschnitten, wie
 * AdaptiveIconDrawable es selbst tut, nur ohne das Schwarz. Andere Drawables bleiben, wie sie sind.
 */
fun Drawable.asIconImage(): Image =
  if (this is AdaptiveIconDrawable) toTransparentBitmap().asImage() else asImage()

/** Wie [asIconImage], als Bitmap. */
fun Drawable.toIconBitmap(): Bitmap =
  if (this is AdaptiveIconDrawable) toTransparentBitmap() else toBitmap()

private fun AdaptiveIconDrawable.toTransparentBitmap(): Bitmap {
  val size = intrinsicWidth.takeIf { it > 0 }?.coerceIn(MIN_SIZE, MAX_SIZE) ?: DEFAULT_SIZE
  // setzt auch die Grenzen der Ebenen (ein Viertel größer zu jeder Seite) und die Maske
  setBounds(0, 0, size, size)
  val layers = createBitmap(size, size)
  Canvas(layers).let { canvas ->
    background?.draw(canvas)
    foreground?.draw(canvas)
  }
  val icon = createBitmap(size, size)
  val paint =
    Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG).apply {
      shader = BitmapShader(layers, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP)
    }
  Canvas(icon).drawPath(iconMask, paint)
  layers.recycle()
  return icon
}

private const val MIN_SIZE = 48
private const val MAX_SIZE = 512
private const val DEFAULT_SIZE = 192
