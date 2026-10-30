package de.needanapp.myzel.ui.discover

import androidx.compose.foundation.MarqueeSpacing
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import de.needanapp.design.components.designCard
import de.needanapp.design.theme.LocalDesignEffects
import de.needanapp.myzel.ui.apps.AppBadgePills
import de.needanapp.myzel.ui.apps.IconCircle
import de.needanapp.myzel.ui.apps.faded
import de.needanapp.myzel.ui.apps.fadedIcon
import de.needanapp.myzel.ui.apps.rememberAppBadgeState
import org.fdroid.R
import org.fdroid.ui.discover.AppDiscoverItem

/** Abstand zwischen den Karten im Karussell, waagrecht und senkrecht. */
internal val CardGap = 12.dp

// Maße einer Karte, wie die Karten der Apps auf der Website des Repos, nur ohne Kurzbeschreibung
private val CardWidth = 228.dp
private val CardHeight = 76.dp
private val CardIconSize = 52.dp

/**
 * Rahmen einer Karte im Stil des Design-Pakets, ohne Schatten: Das Karussell schneidet alles
 * außerhalb ab und würde ihn kappen.
 */
@Composable
private fun Modifier.card(onClick: () -> Unit): Modifier =
  width(CardWidth)
    .height(CardHeight)
    .designCard(elevated = false)
    .clickable(onClick = onClick)
    .padding(horizontal = 12.dp)

/**
 * Eine App als Karte: Symbol im weißen Kreis, daneben Name und neueste Version ([versionName] aus
 * [rememberLatestVersionNames]), am rechten Rand die Pillen ([AppBadgePills]) übereinander, damit
 * die Version daneben Platz hat; kürzlich aktualisierte Apps zeigen dort, wie alt das Update ist.
 * Eine zu lange Version läuft durch. Installierte Apps ohne Update sind ausgegraut.
 */
@Composable
internal fun AppCard(
  app: AppDiscoverItem,
  versionName: String?,
  onAppTap: (AppDiscoverItem) -> Unit,
) {
  val badges = rememberAppBadgeState(app.packageName, app.isInstalled, app.lastUpdated)
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = spacedBy(12.dp),
    modifier = Modifier.card { onAppTap(app) },
  ) {
    Box(Modifier.fadedIcon(badges.isFaded)) { IconCircle(app.imageModel, CardIconSize) }
    Column(verticalArrangement = spacedBy(2.dp), modifier = Modifier.weight(1f)) {
      Text(
        text = app.name,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.faded(badges.isFaded),
      )
      if (!versionName.isNullOrEmpty()) {
        VersionText(versionName, badges.isFaded)
      }
    }
    // höchstens drei Pillen, die passen auch bei größerer Schrift in die Höhe der Karte
    if (badges.isInstalled || badges.isNew || badges.isWatch || badges.updatedDays != null) {
      PillStack { AppBadgePills(badges) }
    }
  }
}

/** Die Pillen übereinander, von der schmalsten oben bis zur breitesten unten. */
@Composable
internal fun PillStack(content: @Composable () -> Unit) {
  Layout(content) { measurables, constraints ->
    val gap = PillGap.roundToPx()
    val pills =
      measurables
        .map { it.measure(constraints.copy(minWidth = 0, minHeight = 0)) }
        .sortedBy { it.width }
    val width = pills.maxOfOrNull { it.width } ?: 0
    val height = pills.sumOf { it.height } + gap * (pills.size - 1).coerceAtLeast(0)
    layout(width, height) {
      var y = 0
      pills.forEach {
        it.placeRelative(0, y)
        y += it.height + gap
      }
    }
  }
}

private val PillGap = 3.dp

/**
 * Die Version auf der Karte. Passt sie nicht in den Platz neben Symbol und Pillen, läuft sie ein
 * paar Runden durch, hält dazwischen am Anfang kurz an und blendet am Ende aus, damit sie nicht
 * abgeschnitten wirkt (wie MarqueeText im Design-Paket ab 0.22.0, das Myzel nicht übernimmt). Ohne
 * Bewegung, also bei reduzierten Effekten oder ausgeschalteten Animationen, wird sie gekürzt: Für
 * eine zweite Zeile fehlt der Platz.
 */
@Composable
private fun VersionText(text: String, faded: Boolean, modifier: Modifier = Modifier) {
  val moves = LocalDesignEffects.current.richMotion
  var boxWidth by remember { mutableIntStateOf(0) }
  var textWidth by remember { mutableIntStateOf(0) }
  val overflows = moves && textWidth > boxWidth && boxWidth > 0
  Box(
    modifier
      .onSizeChanged { boxWidth = it.width }
      .then(if (overflows) Modifier.fadingEnd() else Modifier)
      .faded(faded)
  ) {
    Text(
      text = text,
      style = MaterialTheme.typography.bodySmall,
      fontFamily = FontFamily.Monospace,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      maxLines = 1,
      softWrap = false,
      overflow = if (moves) TextOverflow.Clip else TextOverflow.Ellipsis,
      onTextLayout = { textWidth = it.size.width },
      modifier =
        if (moves) {
          Modifier.basicMarquee(
            iterations = MARQUEE_RUNS,
            repeatDelayMillis = MARQUEE_PAUSE_MILLIS,
            initialDelayMillis = MARQUEE_PAUSE_MILLIS,
            spacing = MarqueeSpacing.fractionOfContainer(MARQUEE_GAP),
          )
        } else {
          Modifier
        },
    )
  }
}

private fun Modifier.fadingEnd() = graphicsLayer {
  compositingStrategy = CompositingStrategy.Offscreen
}
  .drawWithContent {
    drawContent()
    val fade = MARQUEE_FADE.toPx().coerceAtMost(size.width / 2)
    val ltr = layoutDirection == LayoutDirection.Ltr
    drawRect(
      Brush.horizontalGradient(
        listOf(Color.Black, Color.Transparent),
        startX = if (ltr) size.width - fade else fade,
        endX = if (ltr) size.width else 0f,
      ),
      blendMode = BlendMode.DstIn,
    )
  }

// Endlos laufende Versionen ließen Entdecken auch im Leerlauf ständig neu zeichnen (gemessen
// ~56 Bilder pro Sekunde). Nach ein paar Runden bleibt die Version am Anfang stehen.
private const val MARQUEE_RUNS = 3
private const val MARQUEE_PAUSE_MILLIS = 2000
private const val MARQUEE_GAP = 1f / 4f
private val MARQUEE_FADE = 12.dp

/** Leere Karte in der Größe einer App-Karte, solange die Apps laden. */
@Composable
internal fun PlaceholderCard() {
  Box(Modifier.width(CardWidth).height(CardHeight).designCard(elevated = false))
}

/** Letzte Karte im Karussell: öffnet die ganze Liste. */
@Composable
internal fun ShowAllCard(onClick: () -> Unit) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = spacedBy(12.dp),
    modifier = Modifier.card(onClick),
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier =
        Modifier.size(CardIconSize)
          .clip(CircleShape)
          .background(MaterialTheme.colorScheme.secondaryContainer),
    ) {
      Icon(
        Icons.Default.Apps,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSecondaryContainer,
      )
    }
    Text(
      text = stringResource(R.string.myzel_show_all),
      style = MaterialTheme.typography.titleMedium,
      fontWeight = FontWeight.Bold,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
  }
}
