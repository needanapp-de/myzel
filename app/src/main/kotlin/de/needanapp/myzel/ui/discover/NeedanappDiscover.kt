package de.needanapp.myzel.ui.discover

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import de.needanapp.design.components.designCard
import de.needanapp.design.theme.LocalDesignColors
import de.needanapp.design.theme.LocalDesignEffects
import de.needanapp.myzel.ui.apps.IconCircle
import de.needanapp.myzel.ui.apps.faded
import de.needanapp.myzel.ui.apps.fadedIcon
import de.needanapp.myzel.ui.apps.rememberAppBadgeState
import de.needanapp.myzel.ui.apps.updatedAgoText
import de.needanapp.myzel.ui.categories.CategoryList
import org.fdroid.R
import org.fdroid.ui.categories.CategoryGroup
import org.fdroid.ui.categories.CategoryItem
import org.fdroid.ui.discover.AppDiscoverItem
import org.fdroid.ui.lists.AppListType

/**
 * Entdecken im Design needanapp, wie in der Vorschau: oben die Anzeige in einer Karte, darunter die
 * Bereiche (Neue Apps, Kürzlich aktualisiert) als Chips und das Karussell des gewählten in einer
 * Reihe kleiner Karten, darunter „Neu und aktualisiert“ zum Aufklappen ([RecentExpander]), dann die
 * Kategorien. Die Daten bereitet [DiscoverContent] auf.
 */
@Composable
internal fun NeedanappDiscoverColumn(
  sections: List<CarouselSection>,
  waiting: Boolean,
  expandedId: String?,
  onOpen: (String) -> Unit,
  onListTap: (AppListType) -> Unit,
  onAppTap: (AppDiscoverItem) -> Unit,
  recentApps: List<AppDiscoverItem>,
  recentCount: Int,
  onShowAllRecent: () -> Unit,
  categories: Map<CategoryGroup, List<CategoryItem>>?,
  onNav: (NavKey) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier) {
    Box(
      Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp)
        .fillMaxWidth()
        .designCard()
        .padding(8.dp)
    ) {
      DiscoverDisplay(Modifier, onNav)
    }
    val open = sections.firstOrNull { it.id == expandedId }
    if (waiting) {
      NeedanappLoadingCarousel(Modifier.padding(top = 14.dp))
    } else if (open != null) {
      SectionChips(sections, open.id, onOpen, Modifier.padding(top = 14.dp))
      Crossfade(open, label = "carousel") { section ->
        NeedanappCarousel(section.apps, onAppTap, onShowAll = { onListTap(section.list) })
      }
    }
    if (recentApps.isNotEmpty()) {
      RecentExpander(
        apps = recentApps,
        count = recentCount,
        onAppTap = onAppTap,
        onShowAll = onShowAllRecent,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
      )
    }
    CategoryList(
      categoryMap = categories,
      onNav = onNav,
      // unter dem Karussell reicht dessen Platz für den Schatten als Abstand
      modifier =
        Modifier.fillMaxWidth().padding(top = if (waiting || open != null) 0.dp else 12.dp),
    )
  }
}

/** Die Bereiche als Chips; der gewählte steht auf der Farbe der aktiven Einträge. */
@Composable
private fun SectionChips(
  sections: List<CarouselSection>,
  selectedId: String,
  onOpen: (String) -> Unit,
  modifier: Modifier = Modifier,
) {
  val scheme = MaterialTheme.colorScheme
  val line = LocalDesignColors.current.cardBorder
  Row(
    horizontalArrangement = spacedBy(8.dp),
    modifier =
      modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
  ) {
    sections.forEach { section ->
      val selected = section.id == selectedId
      Box(
        contentAlignment = Alignment.Center,
        modifier =
          Modifier.height(36.dp)
            .clip(ChipShape)
            .then(
              if (selected) Modifier.background(scheme.primaryContainer)
              else Modifier.border(1.dp, line, ChipShape)
            )
            .selectable(selected = selected, role = Role.Tab) { onOpen(section.id) }
            .padding(horizontal = 14.dp),
      ) {
        Text(
          section.list.title,
          style = MaterialTheme.typography.labelLarge,
          color = if (selected) scheme.onPrimaryContainer else scheme.onSurfaceVariant,
          maxLines = 1,
        )
      }
    }
  }
}

/**
 * Die Apps eines Bereichs als kleine Karten in einer Reihe, am Ende „Alle anzeigen“. Wie das
 * Karussell des Stils „Myzel“ blendet es aus, wo es weitergeht, und springt nach dem Aktualisieren
 * der Paketquellen und bei einer neuen ersten App zum Anfang.
 */
@Composable
private fun NeedanappCarousel(
  apps: List<AppDiscoverItem>,
  onAppTap: (AppDiscoverItem) -> Unit,
  onShowAll: () -> Unit,
) {
  val listState = rememberLazyListState()
  val firstApp = apps.firstOrNull()?.packageName
  val refreshes = LocalRepoRefreshes.current
  val richMotion = LocalDesignEffects.current.richMotion
  var shown by remember { mutableStateOf(firstApp to refreshes) }
  LaunchedEffect(firstApp, refreshes) {
    if (shown == (firstApp to refreshes)) return@LaunchedEffect
    shown = firstApp to refreshes
    if (richMotion) listState.animateScrollToItem(0) else listState.scrollToItem(0)
  }
  val startFade by
    animateFloatAsState(if (listState.canScrollBackward) 1f else 0f, label = "startFade")
  val endFade by animateFloatAsState(if (listState.canScrollForward) 1f else 0f, label = "endFade")
  val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  val versionNames = rememberLatestVersionNames(apps)
  LazyRow(
    state = listState,
    // unten Platz für den Schatten der Karten, den die Reihe sonst abschneidet
    contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 16.dp),
    horizontalArrangement = spacedBy(SmallCardGap),
    modifier =
      Modifier.fillMaxWidth()
        .fadingEdges(
          left = { if (isRtl) endFade else startFade },
          right = { if (isRtl) startFade else endFade },
        ),
  ) {
    items(apps, key = { it.packageName }) { app ->
      SmallAppCard(app, versionNames[app.packageName], onAppTap)
    }
    item(key = SHOW_ALL_KEY) { SmallShowAllCard(onShowAll) }
  }
}

/**
 * Eine App als kleine Karte: Symbol, darunter Name und neueste Version, bei kürzlich aktualisierten
 * Apps davor das Alter des Updates („↑ heute · 2.2.1“). Hat die App eine Uhr-Version oder ist sie
 * nur für die Uhr, steht rechts oben eine kleine Uhr.
 */
@Composable
private fun SmallAppCard(
  app: AppDiscoverItem,
  versionName: String?,
  onAppTap: (AppDiscoverItem) -> Unit,
) {
  val badges = rememberAppBadgeState(app.packageName, app.isInstalled, app.lastUpdated)
  Column(
    verticalArrangement = spacedBy(8.dp),
    modifier = Modifier.smallCard { onAppTap(app) },
  ) {
    Row(Modifier.fillMaxWidth()) {
      Box(Modifier.fadedIcon(badges.isFaded)) {
        IconCircle(app.imageModel, SmallIconSize, shape = SmallIconShape)
      }
      if (badges.isWatch) {
        Spacer(Modifier.weight(1f))
        Icon(
          Icons.Rounded.Watch,
          contentDescription = stringResource(R.string.myzel_tag_watch),
          tint = MaterialTheme.colorScheme.primary,
          modifier = Modifier.size(WatchIconSize).faded(badges.isFaded),
        )
      }
    }
    Column(Modifier.faded(badges.isFaded)) {
      Text(
        app.name,
        style = MaterialTheme.typography.titleSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      // für eine Pille fehlt der Platz: das Alter des Updates steht vor der Version
      val age = badges.updatedDays?.let { updatedAgoText(it) }
      val primary = MaterialTheme.colorScheme.primary
      Text(
        buildAnnotatedString {
          if (age != null) {
            withStyle(SpanStyle(color = primary)) { append(age) }
            if (!versionName.isNullOrEmpty()) append(" · ")
          }
          append(versionName.orEmpty())
        },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/** Die letzte kleine Karte: öffnet die ganze Liste. */
@Composable
private fun SmallShowAllCard(onClick: () -> Unit) {
  val scheme = MaterialTheme.colorScheme
  Column(verticalArrangement = spacedBy(8.dp), modifier = Modifier.smallCard(onClick)) {
    Box(
      contentAlignment = Alignment.Center,
      modifier =
        Modifier.size(SmallIconSize).clip(CircleShape).background(scheme.secondaryContainer),
    ) {
      Icon(Icons.Default.Apps, contentDescription = null, tint = scheme.onSecondaryContainer)
    }
    Text(
      stringResource(R.string.myzel_show_all),
      style = MaterialTheme.typography.titleSmall,
      maxLines = 2,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun Modifier.smallCard(onClick: (() -> Unit)?): Modifier =
  width(SmallCardWidth)
    .heightIn(min = SmallCardHeight)
    .designCard()
    .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    .padding(horizontal = 10.dp, vertical = 12.dp)

/** Leere kleine Karten mit der Ladeanzeige darüber, solange die Apps laden. */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun NeedanappLoadingCarousel(modifier: Modifier = Modifier) {
  val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxWidth()) {
    Row(
      horizontalArrangement = spacedBy(SmallCardGap),
      modifier =
        Modifier.fillMaxWidth()
          .fadingEdges(left = { if (isRtl) 1f else 0f }, right = { if (isRtl) 0f else 1f })
          .horizontalScroll(rememberScrollState(), enabled = false)
          .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 16.dp),
    ) {
      repeat(PLACEHOLDER_CARDS) { Box(Modifier.smallCard(null)) }
    }
    LoadingIndicator()
  }
}

private val ChipShape = RoundedCornerShape(12.dp)
private val SmallCardWidth = 112.dp
private val SmallCardHeight = 118.dp
private val SmallCardGap = 10.dp
private val SmallIconSize = 48.dp
private val SmallIconShape = RoundedCornerShape(14.dp)
private val WatchIconSize = 18.dp
private const val PLACEHOLDER_CARDS = 4
private const val SHOW_ALL_KEY = "myzel_show_all"
