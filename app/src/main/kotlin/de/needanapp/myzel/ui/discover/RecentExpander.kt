package de.needanapp.myzel.ui.discover

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import de.needanapp.design.components.KeyStyle
import de.needanapp.design.components.MonoLabel
import de.needanapp.design.components.PillButton
import de.needanapp.design.theme.LocalDesignShapes
import de.needanapp.design.theme.emphasizedSpec
import de.needanapp.myzel.ui.apps.AppBadgeRow
import de.needanapp.myzel.ui.apps.IconCircle
import de.needanapp.myzel.ui.apps.RECENT_DAYS
import de.needanapp.myzel.ui.apps.dayTitle
import de.needanapp.myzel.ui.apps.daysSince
import de.needanapp.myzel.ui.apps.faded
import de.needanapp.myzel.ui.apps.fadedIcon
import de.needanapp.myzel.ui.apps.rememberAppBadgeState
import java.util.concurrent.TimeUnit.DAYS
import org.fdroid.R
import org.fdroid.ui.discover.AppDiscoverItem

/** So viele Apps zeigt [RecentExpander] aufgeklappt, den Rest die Seite „Neu und aktualisiert“. */
private const val INLINE_APPS = 12

/**
 * Die neuen und kürzlich aktualisierten Apps beider Bereiche zusammen, nur die der letzten
 * [RECENT_DAYS] Tage, neueste zuerst, jede App einmal.
 */
internal fun recentAppsOf(
  newApps: List<AppDiscoverItem>,
  recentlyUpdatedApps: List<AppDiscoverItem>,
): List<AppDiscoverItem> {
  val since = System.currentTimeMillis() - DAYS.toMillis(RECENT_DAYS.toLong())
  return (newApps + recentlyUpdatedApps.filter { it.lastUpdated >= since })
    .sortedByDescending { it.lastUpdated }
    .distinctBy { it.packageName }
}

/**
 * Unter dem Karussell: „Neu und aktualisiert“ mit der Zahl der Apps auf der Seite ([count]) klappt
 * die neuesten [INLINE_APPS] aus [apps] ([recentAppsOf]) auf, nach Tagen gruppiert, mit der Pille
 * für das Alter des Updates. Darunter klappt „Weniger“ wieder zu, „Alle N“ öffnet die ganze Liste
 * ([onShowAll], die Seite „Neu und aktualisiert“).
 */
@Composable
internal fun RecentExpander(
  apps: List<AppDiscoverItem>,
  count: Int,
  onAppTap: (AppDiscoverItem) -> Unit,
  onShowAll: () -> Unit,
  modifier: Modifier = Modifier,
) {
  var expanded by rememberSaveable { mutableStateOf(false) }
  val shapes = LocalDesignShapes.current
  Column(modifier.fillMaxWidth()) {
    ExpanderHeader(count, expanded, onToggle = { expanded = !expanded })
    AnimatedVisibility(
      visible = expanded,
      enter = expandVertically(emphasizedSpec()) + fadeIn(emphasizedSpec()),
      exit = shrinkVertically(emphasizedSpec()) + fadeOut(emphasizedSpec()),
    ) {
      val shown = remember(apps) { apps.take(INLINE_APPS) }
      val days = remember(shown) { shown.groupBy { daysSince(it.lastUpdated) } }
      val versionNames = rememberLatestVersionNames(shown)
      Column {
        days.values.forEach { dayApps ->
          MonoLabel(
            "${dayTitle(dayApps.first().lastUpdated)} · ${dayApps.size}",
            modifier =
              Modifier.padding(start = 8.dp, top = 16.dp, bottom = 8.dp).semantics { heading() },
          )
          Column(verticalArrangement = spacedBy(shapes.rowGap)) {
            dayApps.forEachIndexed { index, app ->
              val top = if (index == 0) shapes.group else shapes.inner
              val bottom = if (index == dayApps.lastIndex) shapes.group else shapes.inner
              RecentRow(
                app = app,
                versionName = versionNames[app.packageName],
                shape = RoundedCornerShape(top, top, bottom, bottom),
                onAppTap = onAppTap,
              )
            }
          }
        }
        Row(horizontalArrangement = spacedBy(12.dp), modifier = Modifier.padding(top = 16.dp)) {
          PillButton(
            text = stringResource(R.string.myzel_recent_less),
            onClick = { expanded = false },
            style = KeyStyle.ALU,
            modifier = Modifier.weight(1f),
          )
          PillButton(
            text = pluralStringResource(R.plurals.myzel_recent_show_all, count, count),
            onClick = onShowAll,
            modifier = Modifier.weight(1f),
          )
        }
      }
    }
  }
}

/** Kopf zum Aufklappen wie die Bereichskarten: Symbol, Titel, Anzahl und Pfeil nach unten. */
@Composable
private fun ExpanderHeader(count: Int, expanded: Boolean, onToggle: () -> Unit) {
  val arrowRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "arrow")
  Surface(
    shape = RoundedCornerShape(LocalDesignShapes.current.group),
    color = MaterialTheme.colorScheme.surfaceContainer,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier =
        Modifier.clickable(
            onClickLabel =
              stringResource(if (expanded) R.string.myzel_recent_less else R.string.expand),
            onClick = onToggle,
          )
          .heightIn(min = 56.dp)
          .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
      Icon(
        Icons.Default.History,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(24.dp),
      )
      Spacer(Modifier.width(16.dp))
      Text(
        text = stringResource(R.string.myzel_recent_title),
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.weight(1f).semantics { heading() },
      )
      Spacer(Modifier.width(12.dp))
      Text(
        text = count.toString(),
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.width(8.dp))
      Icon(
        Icons.Filled.KeyboardArrowDown,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.rotate(arrowRotation),
      )
    }
  }
}

/**
 * Eine App in der aufgeklappten Liste: Symbol, Name und Version, rechts die Pillen. Installierte
 * Apps ohne Update sind ausgegraut wie auf den Karten.
 */
@Composable
private fun RecentRow(
  app: AppDiscoverItem,
  versionName: String?,
  shape: Shape,
  onAppTap: (AppDiscoverItem) -> Unit,
) {
  val badges = rememberAppBadgeState(app.packageName, app.isInstalled, app.lastUpdated)
  Surface(
    shape = shape,
    color = MaterialTheme.colorScheme.surfaceContainer,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = spacedBy(12.dp),
      modifier =
        Modifier.clickable { onAppTap(app) }
          .heightIn(min = 64.dp)
          .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
      Box(Modifier.fadedIcon(badges.isFaded)) { IconCircle(app.imageModel, RowIconSize) }
      Column(Modifier.weight(1f).faded(badges.isFaded)) {
        Text(
          text = app.name,
          style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        if (!versionName.isNullOrEmpty()) {
          Text(
            text = versionName,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
      }
      AppBadgeRow(badges)
    }
  }
}

private val RowIconSize = 40.dp
