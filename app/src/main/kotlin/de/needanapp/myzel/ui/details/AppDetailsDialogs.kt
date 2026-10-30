package de.needanapp.myzel.ui.details

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import de.needanapp.design.components.ContentDialog
import de.needanapp.design.components.PillButton
import de.needanapp.design.components.TagPill
import de.needanapp.design.theme.LocalDesignShapes
import org.fdroid.R
import org.fdroid.ui.utils.AsyncShimmerImage

/*
 * Dialoge der App-Seite im Stil des Design-Pakets: der Installationsfehler als Dialog mit Symbol
 * und die Screenshots mit eigener Vollbildansicht statt des Blatts von F-Droid (Screenshots.kt).
 * Die Optionen statt des Menüs von F-Droid (AppDetailsMenu) klappt der Myzel-Knopf der App-Seite
 * auf (AppDetailsPage.kt).
 */

/**
 * Die Installation ist fehlgeschlagen: kurzer Titel, der Name der App im Text, darunter die Meldung
 * von Android zum Markieren.
 */
@Composable
internal fun InstallErrorDialog(appName: String, message: String?, onDismiss: () -> Unit) {
  ContentDialog(
    title = stringResource(R.string.myzel_install_failed_title),
    confirmLabel = stringResource(R.string.myzel_close),
    onConfirm = onDismiss,
    onDismiss = onDismiss,
    dismissLabel = null,
    icon = Icons.Rounded.ErrorOutline,
  ) {
    Text(stringResource(R.string.myzel_install_failed_text, appName))
    if (message != null) {
      SelectionContainer(
        Modifier.padding(top = 12.dp)
          .fillMaxWidth()
          .clip(RoundedCornerShape(LocalDesignShapes.current.inner * 2))
          .background(MaterialTheme.colorScheme.surfaceContainerHighest)
          .padding(10.dp)
      ) {
        Text(message, style = MaterialTheme.typography.mono.copy(fontSize = 12.sp))
      }
    }
  }
}

/**
 * Ersatz für org.fdroid.ui.details.Screenshots mit derselben Signatur: eine Reihe gerundeter
 * Vorschaubilder, ein Tipp öffnet die Bilder im Vollbild zum Wischen. Über eine getaktete
 * Verbindung lädt die Reihe erst auf Wunsch.
 */
@Composable
fun Screenshots(isMetered: Boolean, phoneScreenshots: List<Any>) {
  var showEvenWhenMetered by rememberSaveable { mutableStateOf(false) }
  if (isMetered && !showEvenWhenMetered) {
    Column(Modifier.padding(horizontal = 16.dp)) {
      Text(
        stringResource(R.string.myzel_screenshots_metered),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(12.dp))
      PillButton(
        text = stringResource(R.string.myzel_screenshots_load),
        onClick = { showEvenWhenMetered = true },
      )
    }
    return
  }
  var fullscreenIndex by rememberSaveable { mutableStateOf<Int?>(null) }
  val shapes = LocalDesignShapes.current
  val shape = RoundedCornerShape((shapes.group + shapes.inner) / 2)
  LazyRow(
    horizontalArrangement = spacedBy(8.dp),
    contentPadding = PaddingValues(horizontal = 16.dp),
    modifier = Modifier.fillMaxWidth(),
  ) {
    itemsIndexed(phoneScreenshots) { index, screenshot ->
      AsyncShimmerImage(
        model = screenshot,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        placeholder = rememberVectorPainter(Icons.Default.Image),
        error = rememberVectorPainter(Icons.Default.Error),
        modifier =
          Modifier.size(120.dp, 240.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .clickable { fullscreenIndex = index },
      )
    }
  }
  fullscreenIndex?.let { start ->
    ScreenshotViewer(phoneScreenshots, start) { fullscreenIndex = null }
  }
}

/** Die Screenshots im Vollbild vor dunklem Grund, mit Seitenzahl und Schließen. */
@Composable
private fun ScreenshotViewer(screenshots: List<Any>, start: Int, onDismiss: () -> Unit) {
  Dialog(
    onDismissRequest = onDismiss,
    properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
  ) {
    // das Dialogfenster reicht sonst nicht unter die Statusleiste
    val window = (LocalView.current.parent as? DialogWindowProvider)?.window
    SideEffect { window?.let { it.attributes = it.attributes.apply { fitInsetsTypes = 0 } } }
    val pagerState = rememberPagerState(initialPage = start, pageCount = { screenshots.size })
    Box(Modifier.fillMaxSize().background(Color.Black)) {
      // ohne Nachfedern: es ließ die Bilder bei jedem Wischen hüpfen (wie bei F-Droid)
      HorizontalPager(
        state = pagerState,
        overscrollEffect = null,
        modifier = Modifier.fillMaxSize(),
      ) { page ->
        AsyncShimmerImage(
          model = screenshots[page],
          contentDescription = null,
          contentScale = ContentScale.Fit,
          placeholder = rememberVectorPainter(Icons.Default.Image),
          error = rememberVectorPainter(Icons.Default.Error),
          modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(vertical = 56.dp),
        )
      }
      IconButton(
        onClick = onDismiss,
        modifier =
          Modifier.align(Alignment.TopEnd)
            .safeDrawingPadding()
            .padding(8.dp)
            .background(Color.White.copy(alpha = 0.14f), CircleShape),
      ) {
        Icon(Icons.Rounded.Close, stringResource(R.string.myzel_close), tint = Color.White)
      }
      if (screenshots.size > 1) {
        TagPill(
          text = "${pagerState.currentPage + 1} / ${screenshots.size}",
          containerColor = Color.White.copy(alpha = 0.14f),
          contentColor = Color.White,
          modifier = Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(16.dp),
        )
      }
    }
  }
}
