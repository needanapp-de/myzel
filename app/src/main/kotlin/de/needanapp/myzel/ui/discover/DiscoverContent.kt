package de.needanapp.myzel.ui.discover

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Update
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation3.runtime.NavKey
import de.needanapp.design.components.DeviceDisplay
import de.needanapp.design.components.StatusTone
import de.needanapp.design.theme.LocalDesignEffects
import de.needanapp.design.theme.LocalDesignShapes
import de.needanapp.design.theme.emphasizedSpec
import de.needanapp.design.theme.isNeedanappDesign
import de.needanapp.myzel.recent.RecentApps
import de.needanapp.myzel.recent.rememberRecentAppsCount
import de.needanapp.myzel.ui.apps.rememberNewAndUpdateCounts
import de.needanapp.myzel.ui.apps.rememberNewAndUpdateCountsByRepo
import de.needanapp.myzel.ui.categories.CategoryList
import de.needanapp.myzel.ui.nav.RepoState
import de.needanapp.myzel.ui.nav.RepoUpdateDisplay
import de.needanapp.myzel.ui.nav.rememberRepoState
import de.needanapp.myzel.ui.nav.repoTimeText
import kotlinx.coroutines.delay
import org.fdroid.R
import org.fdroid.ui.discover.AppDiscoverItem
import org.fdroid.ui.discover.LoadedDiscoverModel
import org.fdroid.ui.lists.AppListType
import org.fdroid.ui.navigation.NavigationKey

private const val SHOW_ALL_KEY = "myzel_show_all"

// so breit blendet das Karussell an einem Rand aus, an dem es weitergeht
private val FadeWidth = 56.dp

// Karten je Spalte des Karussells
private const val CAROUSEL_ROWS = 2

/** Eine Karte im Karussell: eine App oder am Ende „Alle anzeigen“. */
private sealed interface CarouselCell {
  val key: String

  class App(val app: AppDiscoverItem) : CarouselCell {
    override val key: String
      get() = app.packageName
  }

  data object ShowAll : CarouselCell {
    override val key: String = SHOW_ALL_KEY
  }
}

internal class CarouselSection(
  val id: String,
  val list: AppListType,
  val icon: ImageVector,
  val apps: List<AppDiscoverItem>,
)

/**
 * Ersatz für org.fdroid.ui.discover.DiscoverContent mit derselben Signatur; Discover.kt bindet ihn
 * über einen ergänzten Import ein (myzel/tools/apply-imports.py). Von den Bereichen Neu und
 * Kürzlich aktualisiert ist immer einer offen, zu Beginn der erste. Der offene zeigt nur seine Apps
 * in zwei Reihen, schwebend über dem Hintergrund und über die ganze Breite; die anderen stehen als
 * Karten wie die Gruppen der Einstellungen darüber oder darunter und öffnen sich beim Tippen. Statt
 * des Pfeils von F-Droid blendet das Karussell an den Rändern aus, an denen es weitergeht; die
 * ganze Liste öffnet die letzte Kachel „Alle anzeigen“. Ziehen nach unten aktualisiert die
 * Paketquellen ([RepoRefreshBox]).
 *
 * Nach dem Start kommen die Kategorien gut eine halbe Sekunde vor den beiden App-Listen aus der
 * Datenbank. Bis dahin zeigt das Karussell die Apps vom letzten Start ([DiscoverSnapshot]), beim
 * allerersten Start leere Karten mit Ladeanzeige ([LoadingCarousel]).
 *
 * Im Design needanapp sieht Entdecken aus wie in der Vorschau ([NeedanappDiscoverColumn]): die
 * Anzeige in einer Karte, die Bereiche als Chips, das Karussell in einer Reihe und darunter die
 * Kategorien.
 *
 * „Am häufigsten heruntergeladen“ von F-Droid fehlt: Die Liste ist fest eingebaut und zählt nur
 * Downloads von f-droid.org, Apps aus anderen Paketquellen kommen darin nie vor.
 *
 * Bei einem Update von F-Droid mit org.fdroid.ui.discover.DiscoverContent vergleichen.
 */
@Composable
fun DiscoverContent(
  discoverModel: LoadedDiscoverModel,
  onListTap: (AppListType) -> Unit,
  onAppTap: (AppDiscoverItem) -> Unit,
  onNav: (NavKey) -> Unit,
  modifier: Modifier = Modifier,
) {
  val newTitle = stringResource(R.string.app_list_new)
  val recentlyUpdatedTitle = stringResource(R.string.app_list_recently_updated)
  // Bis die Datenbank antwortet, stehen die Apps vom letzten Start da (DiscoverSnapshot)
  val loading = !discoverModel.newAppsLoaded || !discoverModel.recentlyUpdatedAppsLoaded
  val snapshot = if (loading) rememberSnapshotItems() else null
  val newApps = if (discoverModel.newAppsLoaded) discoverModel.newApps else snapshot?.newApps
  val recentlyUpdatedApps =
    if (discoverModel.recentlyUpdatedAppsLoaded) discoverModel.recentlyUpdatedApps
    else snapshot?.recentlyUpdatedApps
  // Fehlt eine Liste noch ganz, wartet das Karussell auf beide; sonst ginge erst die andere auf
  val waiting = newApps == null || recentlyUpdatedApps == null
  val sections = buildList {
    if (waiting) return@buildList
    if (newApps.isNotEmpty()) {
      add(CarouselSection("new", AppListType.New(newTitle), Icons.Default.NewReleases, newApps))
    }
    if (recentlyUpdatedApps.isNotEmpty()) {
      add(
        CarouselSection(
          "recently_updated",
          AppListType.RecentlyUpdated(recentlyUpdatedTitle),
          Icons.Default.Update,
          recentlyUpdatedApps,
        )
      )
    }
  }
  // alle neuen und aktualisierten der letzten 14 Tage, für das Aufklappen unter dem Karussell
  val recentApps =
    remember(newApps, recentlyUpdatedApps) {
      if (waiting) emptyList() else recentAppsOf(newApps, recentlyUpdatedApps)
    }
  // so viele zeigt die Seite; die Listen hier enthalten nur die 200 neuesten Updates
  val recentCount = rememberRecentAppsCount() ?: recentApps.size
  val onShowAllRecent = { onNav(RecentApps()) }
  var openId by rememberSaveable { mutableStateOf<String?>(null) }
  // fehlt der gewählte Bereich (noch), ist der erste offen
  val expandedId = sections.firstOrNull { it.id == openId }?.id ?: sections.firstOrNull()?.id
  val rowGap = LocalDesignShapes.current.rowGap

  // die scrollende Spalte (Modifier aus Discover.kt) liegt im Rahmen zum Ziehen
  RepoRefreshBox(Modifier.fillMaxSize()) {
    if (isNeedanappDesign) {
      NeedanappDiscoverColumn(
        sections = sections,
        waiting = waiting,
        expandedId = expandedId,
        onOpen = { openId = it },
        onListTap = onListTap,
        onAppTap = onAppTap,
        recentApps = recentApps,
        recentCount = recentCount,
        onShowAllRecent = onShowAllRecent,
        categories = discoverModel.categories,
        onNav = onNav,
        modifier = modifier,
      )
      return@RepoRefreshBox
    }
    Column(verticalArrangement = spacedBy(18.dp), modifier = modifier) {
      DiscoverDisplay(Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp), onNav)
      if (waiting) {
        LoadingCarousel(Modifier.padding(top = 8.dp))
      } else if (sections.isNotEmpty()) {
        Column(Modifier.padding(top = 8.dp)) {
          sections.forEachIndexed { index, section ->
            key(section.id) {
              val isOpen = section.id == expandedId
              // die Karten zwischen zwei Karussells bilden einen Block
              val isTop = index == 0 || sections[index - 1].id == expandedId
              val isBottom = index == sections.lastIndex || sections[index + 1].id == expandedId
              AnimatedVisibility(
                visible = !isOpen,
                enter = expandVertically(emphasizedSpec()) + fadeIn(emphasizedSpec()),
                exit = shrinkVertically(emphasizedSpec()) + fadeOut(emphasizedSpec()),
              ) {
                SectionCard(
                  icon = section.icon,
                  title = section.list.title,
                  isTop = isTop,
                  isBottom = isBottom,
                  onClick = { openId = section.id },
                  modifier = Modifier.padding(horizontal = 16.dp, vertical = rowGap / 2),
                )
              }
              AnimatedVisibility(
                visible = isOpen,
                enter = expandVertically(emphasizedSpec()) + fadeIn(emphasizedSpec()),
                exit = shrinkVertically(emphasizedSpec()) + fadeOut(emphasizedSpec()),
              ) {
                AppCarousel(
                  apps = section.apps,
                  onAppTap = onAppTap,
                  onShowAll = { onListTap(section.list) },
                )
              }
            }
          }
        }
      }
      if (recentApps.isNotEmpty()) {
        RecentExpander(
          apps = recentApps,
          count = recentCount,
          onAppTap = onAppTap,
          onShowAll = onShowAllRecent,
          modifier = Modifier.padding(horizontal = 16.dp),
        )
      }
      CategoryList(
        categoryMap = discoverModel.categories,
        onNav = onNav,
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}

/**
 * Die Anzeige oben in Entdecken: wie viele Apps neu sind und wie viele Updates es gibt, rechts wann
 * die Paketquellen zuletzt geladen wurden; ließen sich welche nicht laden, sagt sie das. Danach
 * schaltet sie durch die Paketquellen, die etwas zu melden haben ([NewsDisplay]); ein Tipp öffnet,
 * was die Meldung betrifft ([onNav]). Während einer Aktualisierung zeigt sie deren Fortschritt
 * ([RepoUpdateDisplay]).
 */
@Composable
internal fun DiscoverDisplay(modifier: Modifier, onNav: (NavKey) -> Unit) {
  val repos = rememberRepoState()
  // mit der Punktreihe wird die Anzeige höher; das gleitet, statt Entdecken springen zu lassen
  Box(modifier.animateContentSize(emphasizedSpec())) {
    val update = repos.update
    if (update != null) {
      // während einer Aktualisierung: welche Paketquelle gerade lädt und wie weit alle sind
      RepoUpdateDisplay(update)
    } else {
      NewsDisplay(repos, onNav)
    }
  }
}

/** Eine Meldung der Anzeige in Entdecken; ein Tipp öffnet [target]. */
private data class DisplayPage(
  val text: String,
  val tone: StatusTone,
  val detail: String?,
  val target: NavKey,
)

/**
 * Was es Neues gibt: zuerst alles zusammen, dann je Paketquelle, wie viele Apps dort neu sind und
 * wie viele Updates von dort kommen, rechts ihr Name. Paketquellen ohne Neues fallen weg; eine, die
 * sich nicht laden ließ, meldet das rot mit Ausrufezeichen, bei verweigerter Anmeldung als
 * „Anmeldung nötig“. Die Meldungen wechseln alle [PAGE_MILLIS]; wie viele es sind, hängt von den
 * eingeschalteten Paketquellen ab. Ein Tipp öffnet „Neu und aktualisiert“, bei einer Meldung zu
 * einer Paketquelle nur mit deren Apps; bei einer Störung die Paketquelle bzw. die Paketquellen.
 */
@Composable
private fun NewsDisplay(repos: RepoState, onNav: (NavKey) -> Unit) {
  val (new, updates) = rememberNewAndUpdateCounts()
  val byRepo = rememberNewAndUpdateCountsByRepo()
  val total =
    if (repos.issues > 0) {
      DisplayPage(
        pluralStringResource(R.plurals.myzel_display_repos_issues, repos.issues, repos.issues),
        StatusTone.ERROR,
        repos.lastUpdated?.let { repoTimeText(it) },
        NavigationKey.Repos,
      )
    } else {
      val (text, tone) =
        newsText(new, updates)
          ?: (stringResource(R.string.myzel_display_repos_current) to StatusTone.POSITIVE)
      DisplayPage(text, tone, repos.lastUpdated?.let { repoTimeText(it) }, RecentApps())
    }
  val issueText = stringResource(R.string.myzel_display_repo_issue)
  val loginText = stringResource(R.string.myzel_display_repo_login)
  val sourcePages =
    repos.sources.mapNotNull { source ->
      val name = source.name.ifEmpty { null }
      val count = byRepo[source.repoId]
      val details = NavigationKey.RepoDetails(source.repoId)
      when {
        source.needsLogin -> DisplayPage(loginText, StatusTone.ERROR, name, details)
        source.hasIssue -> DisplayPage(issueText, StatusTone.ERROR, name, details)
        count == null -> null
        else ->
          newsText(count.new, count.updates)?.let { (text, tone) ->
            DisplayPage(text, tone, name, RecentApps(source.repoId))
          }
      }
    }
  val pages = listOf(total) + sourcePages
  var index by remember { mutableIntStateOf(0) }
  LaunchedEffect(pages.size) {
    while (pages.size > 1) {
      delay(PAGE_MILLIS)
      index = (index + 1) % pages.size
    }
  }
  val current = index % pages.size
  val page = pages[current]
  val label =
    stringResource(
      if (page.target is RecentApps) R.string.myzel_display_open_recent
      else R.string.myzel_display_open_repo
    )
  DeviceDisplay(
    page.text,
    page.tone,
    detail = page.detail,
    contentKey = current,
    modifier =
      Modifier.clip(DisplayTapShape).clickable(onClickLabel = label) { onNav(page.target) },
  )
}

// fängt den Tipp-Effekt in den Ecken der Anzeige
private val DisplayTapShape = RoundedCornerShape(12.dp)

/** „3 neu · 2 Updates“ und der Ton dazu; null, wenn es weder Neues noch Updates gibt. */
@Composable
private fun newsText(new: Int, updates: Int): Pair<String, StatusTone>? {
  val newText = pluralStringResource(R.plurals.myzel_display_new, new, new)
  val updatesText = pluralStringResource(R.plurals.myzel_display_updates, updates, updates)
  return when {
    updates > 0 && new > 0 ->
      stringResource(R.string.myzel_display_joined, newText, updatesText) to StatusTone.ACCENT
    updates > 0 -> updatesText to StatusTone.ACCENT
    new > 0 -> newText to StatusTone.POSITIVE
    else -> null
  }
}

/** So lange steht eine Meldung der Anzeige in Entdecken, bevor die nächste kommt. */
private const val PAGE_MILLIS = 4_000L

/**
 * Ein zugeklappter Bereich als Karte wie die aufklappbaren Gruppen des Design-Pakets: Symbol, Titel
 * und Pfeil. Die Ecken richten sich danach, ob darüber oder darunter eine Karte anschließt.
 */
@Composable
private fun SectionCard(
  icon: ImageVector,
  title: String,
  isTop: Boolean,
  isBottom: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val shapes = LocalDesignShapes.current
  val top by
    animateDpAsState(if (isTop) shapes.group else shapes.inner, emphasizedSpec(), label = "top")
  val bottom by
    animateDpAsState(
      if (isBottom) shapes.group else shapes.inner,
      emphasizedSpec(),
      label = "bottom",
    )
  val arrowRotation = if (LocalLayoutDirection.current == LayoutDirection.Rtl) 90f else -90f
  Surface(
    shape =
      RoundedCornerShape(topStart = top, topEnd = top, bottomStart = bottom, bottomEnd = bottom),
    color = MaterialTheme.colorScheme.surfaceContainer,
    modifier = modifier.fillMaxWidth(),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier =
        Modifier.clickable(onClickLabel = stringResource(R.string.expand), onClick = onClick)
          .heightIn(min = 56.dp)
          .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
      Icon(
        icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(24.dp),
      )
      Spacer(Modifier.width(16.dp))
      Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge,
        modifier = Modifier.weight(1f).semantics { heading() },
      )
      Spacer(Modifier.width(12.dp))
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
 * Die Apps eines Bereichs als Karten mit Name und Version, je zwei übereinander: Die Spalten füllen
 * sich von oben nach unten, das Karussell scrollt zur Seite. Wo es weitergeht, blendet der Rand
 * aus; ganz am Anfang und am Ende bleibt er klar. Nach dem Aktualisieren der Paketquellen per
 * Ziehen und sobald vorne eine andere App steht, springt es zum Anfang.
 */
@Composable
private fun AppCarousel(
  apps: List<AppDiscoverItem>,
  onAppTap: (AppDiscoverItem) -> Unit,
  onShowAll: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val listState = rememberLazyListState()
  // Sonst hielte das Karussell die gerade sichtbare App fest, und neue Apps vorne lägen links
  // außer Sicht
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
  val columns =
    remember(apps) {
      (apps.map { CarouselCell.App(it) } + CarouselCell.ShowAll).chunked(CAROUSEL_ROWS)
    }
  LazyRow(
    state = listState,
    contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
    horizontalArrangement = spacedBy(CardGap),
    modifier =
      modifier
        .fillMaxWidth()
        .fadingEdges(
          left = { if (isRtl) endFade else startFade },
          right = { if (isRtl) startFade else endFade },
        ),
  ) {
    items(columns, key = { it.first().key }) { column ->
      Column(verticalArrangement = spacedBy(CardGap)) {
        column.forEach { cell ->
          when (cell) {
            is CarouselCell.App -> AppCard(cell.app, versionNames[cell.app.packageName], onAppTap)
            CarouselCell.ShowAll -> ShowAllCard(onShowAll)
          }
        }
      }
    }
  }
}

/**
 * Platzhalter für das Karussell, solange Neu und Kürzlich aktualisiert laden und nichts vom letzten
 * Start da ist (erster Start, gelöschte Daten): leere Karten in derselben Höhe wie das Karussell,
 * darüber die Ladeanzeige. So sieht Entdecken nicht kaputt aus, und die Kategorien rutschen nicht,
 * wenn die Apps kommen.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LoadingCarousel(modifier: Modifier = Modifier) {
  val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
  Box(contentAlignment = Alignment.Center, modifier = modifier.fillMaxWidth()) {
    Row(
      horizontalArrangement = spacedBy(CardGap),
      modifier =
        Modifier.fillMaxWidth()
          .fadingEdges(left = { if (isRtl) 1f else 0f }, right = { if (isRtl) 0f else 1f })
          // die Karten reichen über den Rand hinaus, wie im Karussell
          .horizontalScroll(rememberScrollState(), enabled = false)
          .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
      repeat(PLACEHOLDER_COLUMNS) {
        Column(verticalArrangement = spacedBy(CardGap)) {
          repeat(CAROUSEL_ROWS) { PlaceholderCard() }
        }
      }
    }
    LoadingIndicator()
  }
}

private const val PLACEHOLDER_COLUMNS = 3

/**
 * Blendet den Inhalt links und rechts aus, je nach Stärke [left] und [right] von 0 bis 1. Die
 * Zwischenebene, die das Ausblenden braucht, gibt es nur, solange ein Rand ausblendet.
 */
internal fun Modifier.fadingEdges(left: () -> Float, right: () -> Float): Modifier = graphicsLayer {
  compositingStrategy =
    if (left() > 0f || right() > 0f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
}
  .drawWithContent {
    drawContent()
    if (left() == 0f && right() == 0f) return@drawWithContent
    val fade = (FadeWidth.toPx() / size.width).coerceAtMost(0.5f)
    drawRect(
      brush =
        Brush.horizontalGradient(
          0f to Color.Black.copy(alpha = 1f - left()),
          fade to Color.Black,
          1f - fade to Color.Black,
          1f to Color.Black.copy(alpha = 1f - right()),
        ),
      blendMode = BlendMode.DstIn,
    )
  }
