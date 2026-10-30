package de.needanapp.myzel.ui.categories

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation3.runtime.NavKey
import de.needanapp.design.components.Led
import de.needanapp.design.components.designCard
import de.needanapp.design.theme.DesignType
import de.needanapp.design.theme.LocalDesignColors
import de.needanapp.design.theme.emphasizedSpec
import de.needanapp.design.theme.isNeedanappDesign
import de.needanapp.design.theme.labelCase
import de.needanapp.myzel.ui.compat.OutlinedCard
import de.needanapp.myzel.watch.WATCH_CATEGORY_ID
import de.needanapp.myzel.watch.rememberHasWatchApps
import java.text.Collator
import org.fdroid.R
import org.fdroid.ui.categories.CategoryGroup
import org.fdroid.ui.categories.CategoryItem
import org.fdroid.ui.lists.AppListType
import org.fdroid.ui.navigation.NavigationKey

/**
 * Ersatz für org.fdroid.ui.categories.CategoryList mit derselben Signatur, aufgerufen vom
 * Entdecken-Inhalt von Myzel (de.needanapp.myzel.ui.discover.DiscoverContent). Statt einer Liste
 * zum Aufklappen ein flaches Bedienfeld: alle Gruppen auf einen Blick, vier je Reihe, getrennt von
 * feinen Linien, mit Kurznamen und alphabetisch, „Sonstiges“ am Ende. Ein Tipp öffnet unter der
 * Reihe ein kleines Display mit den Kategorien der Gruppe und „Alle“ (alle Apps der Gruppe, siehe
 * [groupCategoryId]); höchstens eine Gruppe ist offen. Die Rubrik „Uhr“ steht mit darin, wenn es
 * Uhr-Apps gibt, und öffnet gleich ihre Liste. Im Design needanapp steht das Bedienfeld in einer
 * Karte mit Titel, drei Tasten je Reihe in einem Raster mit Haarlinien ([NeedanappCategoryPanel]).
 */
@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun CategoryList(
  categoryMap: Map<CategoryGroup, List<CategoryItem>>?,
  onNav: (NavKey) -> Unit,
  modifier: Modifier = Modifier,
) {
  // die Kennung der offenen Gruppe, null: alle zu
  var openId by rememberSaveable { mutableStateOf<String?>(null) }
  val onKey = { key: PanelKey ->
    if (key.categories == null) {
      onNav(NavigationKey.AppList(AppListType.Category(key.name, WATCH_CATEGORY_ID)))
    } else {
      openId = if (key.id == openId) null else key.id
    }
  }
  if (isNeedanappDesign) {
    AnimatedVisibility(!categoryMap.isNullOrEmpty(), modifier) {
      Column(
        Modifier.padding(horizontal = 12.dp)
          .designCard()
          .padding(start = 8.dp, end = 8.dp, top = 12.dp, bottom = 8.dp)
      ) {
        Text(
          text = stringResource(R.string.main_menu__categories),
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.padding(start = 8.dp, bottom = 10.dp).semantics { heading() },
        )
        val keys = rememberPanelKeys(categoryMap.orEmpty())
        NeedanappCategoryPanel(keys, openId, onKey, onNav)
      }
    }
    return
  }
  AnimatedVisibility(!categoryMap.isNullOrEmpty()) {
    Column(modifier = modifier) {
      Text(
        text = stringResource(R.string.main_menu__categories),
        style = MaterialTheme.typography.titleMediumEmphasized,
        fontSize = 20.sp,
        modifier = Modifier.padding(horizontal = 16.dp),
      )
      val keys = rememberPanelKeys(categoryMap.orEmpty())
      CategoryPanel(
        keys = keys,
        openId = openId,
        onKey = onKey,
        onNav = onNav,
        modifier = Modifier.padding(16.dp),
      )
    }
  }
}

/**
 * Eine Taste im Bedienfeld: eine Gruppe mit ihren [categories] oder die Rubrik „Uhr“ (ohne).
 *
 * @param label der Kurzname auf der Taste, [name] der volle Name der Gruppe
 */
private class PanelKey(
  val id: String,
  val label: String,
  val name: String,
  val icon: ImageVector,
  val categories: List<CategoryItem>?,
)

@Composable
private fun rememberPanelKeys(categoryMap: Map<CategoryGroup, List<CategoryItem>>): List<PanelKey> {
  val res = LocalResources.current
  val hasWatchApps = rememberHasWatchApps()
  val watchName = stringResource(R.string.myzel_category_watch)
  return remember(categoryMap, hasWatchApps, watchName, res) {
    val groups = categoryMap.map { (group, categories) ->
      PanelKey(
        id = group.id,
        label = ShortNames[group.id]?.let(res::getString) ?: res.getString(group.name),
        name = res.getString(group.name),
        icon = group.imageVector,
        categories = categories,
      )
    }
    val watch =
      if (hasWatchApps) listOf(PanelKey(WATCH_KEY, watchName, watchName, Icons.Rounded.Watch, null))
      else emptyList()
    val collator = Collator.getInstance()
    (groups + watch).sortedWith(
      compareBy<PanelKey> { it.id == MISC_GROUP }.thenBy(collator) { it.label }
    )
  }
}

@Composable
private fun CategoryPanel(
  keys: List<PanelKey>,
  openId: String?,
  onKey: (PanelKey) -> Unit,
  onNav: (NavKey) -> Unit,
  modifier: Modifier = Modifier,
) {
  val line = LocalDesignColors.current.divider
  OutlinedCard(modifier = modifier) {
    val rows = keys.chunked(COLUMNS)
    rows.forEachIndexed { index, row ->
      if (index > 0) Hairline(line)
      Row(Modifier.fillMaxWidth().height(KeyHeight)) {
        row.forEachIndexed { column, key ->
          if (column > 0) VerticalHairline(line)
          PanelKeyCell(
            key = key,
            open = key.id == openId,
            onClick = { onKey(key) },
            modifier = Modifier.weight(1f).fillMaxHeight(),
          )
        }
        // eine kürzere letzte Reihe bleibt rechts leer
        val rest = COLUMNS - row.size
        if (rest > 0) {
          VerticalHairline(line)
          Spacer(Modifier.weight(rest.toFloat()))
        }
      }
      GroupDrawer(row.firstOrNull { it.id == openId && it.categories != null }, line, onNav)
    }
  }
}

/**
 * Das Bedienfeld im Design needanapp: drei Tasten je Reihe; die Linien sind die Lücken zwischen den
 * Tasten auf der Linienfarbe. Die Kategorien einer offenen Gruppe klappen unter ihrer Reihe auf.
 */
@Composable
private fun NeedanappCategoryPanel(
  keys: List<PanelKey>,
  openId: String?,
  onKey: (PanelKey) -> Unit,
  onNav: (NavKey) -> Unit,
) {
  val design = LocalDesignColors.current
  val line = design.divider
  Column(
    verticalArrangement = Arrangement.spacedBy(1.dp),
    modifier = Modifier.fillMaxWidth().clip(NeedanappGridShape).background(line).padding(1.dp),
  ) {
    keys.chunked(NEEDANAPP_COLUMNS).forEach { row ->
      // Reihe und Schublade ohne Lücke, damit eine zugeklappte Schublade keine Linie verdoppelt
      Column {
        Row(
          horizontalArrangement = Arrangement.spacedBy(1.dp),
          modifier = Modifier.fillMaxWidth().height(NeedanappKeyHeight),
        ) {
          row.forEach { key ->
            NeedanappKeyCell(
              key = key,
              open = key.id == openId,
              onClick = { onKey(key) },
              modifier = Modifier.weight(1f).fillMaxHeight(),
            )
          }
          val rest = NEEDANAPP_COLUMNS - row.size
          if (rest > 0) {
            Spacer(Modifier.weight(rest.toFloat()).fillMaxHeight().background(design.card))
          }
        }
        GroupDrawer(row.firstOrNull { it.id == openId && it.categories != null }, line, onNav)
      }
    }
  }
}

/** Eine Taste des Bedienfelds im Design needanapp; offen auf der Farbe der aktiven Einträge. */
@Composable
private fun NeedanappKeyCell(
  key: PanelKey,
  open: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val scheme = MaterialTheme.colorScheme
  val design = LocalDesignColors.current
  Box(
    contentAlignment = Alignment.Center,
    modifier =
      modifier
        .background(if (open) scheme.primaryContainer else design.card)
        .clickable(role = Role.Button, onClick = onClick)
        .semantics {
          contentDescription = key.name
          selected = open
        },
  ) {
    Column(
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
      Icon(
        imageVector = key.icon,
        contentDescription = null,
        tint = scheme.primary,
        modifier = Modifier.size(22.dp),
      )
      BasicText(
        text = key.label,
        style =
          TextStyle(
            color = if (open) scheme.onPrimaryContainer else scheme.onSurface,
            fontSize = 13.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.Medium,
          ),
        maxLines = 1,
        autoSize =
          TextAutoSize.StepBased(minFontSize = 9.sp, maxFontSize = 13.sp, stepSize = 0.5.sp),
        modifier = Modifier.padding(horizontal = 4.dp),
      )
    }
    if (open) {
      Led(
        LocalDesignColors.current.signal,
        Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 9.dp),
        diameter = 5.dp,
      )
    }
  }
}

@Composable
private fun PanelKeyCell(
  key: PanelKey,
  open: Boolean,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val scheme = MaterialTheme.colorScheme
  val accent = scheme.primary
  val signal = LocalDesignColors.current.signal
  Box(
    contentAlignment = Alignment.Center,
    modifier =
      modifier
        .then(if (open) Modifier.background(scheme.onSurface.copy(alpha = 0.06f)) else Modifier)
        .drawBehind {
          // die offene Taste trägt unten einen Strich in der Signalfarbe
          if (open) {
            val bar = 2.dp.toPx()
            drawRect(signal, Offset(0f, size.height - bar), Size(size.width, bar))
          }
        }
        .clickable(role = Role.Button, onClick = onClick)
        .semantics {
          contentDescription = key.name
          selected = open
        },
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Icon(
        imageVector = key.icon,
        contentDescription = null,
        tint = if (open) accent else accent.copy(alpha = 0.75f),
        modifier = Modifier.size(20.dp),
      )
      Spacer(Modifier.height(5.dp))
      BasicText(
        text = key.label,
        style =
          TextStyle(
            color = if (open) scheme.onSurface else scheme.onSurfaceVariant,
            fontSize = 11.sp,
            lineHeight = 13.sp,
            fontWeight = if (open) FontWeight.SemiBold else FontWeight.Medium,
          ),
        maxLines = 1,
        autoSize =
          TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 11.sp, stepSize = 0.5.sp),
        modifier = Modifier.padding(horizontal = 4.dp),
      )
    }
    if (open) {
      Led(signal, Modifier.align(Alignment.TopEnd).padding(top = 7.dp, end = 8.dp), diameter = 5.dp)
    }
  }
}

/**
 * Unter einer Reihe: die Kategorien der offenen Gruppe [key] wie auf einem kleinen Display. Gleitet
 * auf und zu; wechselt die offene Gruppe innerhalb der Reihe, passt es nur seine Höhe an.
 */
@Composable
private fun GroupDrawer(key: PanelKey?, line: Color, onNav: (NavKey) -> Unit) {
  // beim Zuklappen bleibt der Inhalt stehen, bis er verschwunden ist
  val shown = remember { ShownKey() }
  if (key != null) shown.key = key
  AnimatedVisibility(
    visible = key != null,
    enter = expandVertically(emphasizedSpec()) + fadeIn(emphasizedSpec()),
    exit = shrinkVertically(emphasizedSpec()) + fadeOut(emphasizedSpec()),
  ) {
    val current = shown.key ?: return@AnimatedVisibility
    val design = LocalDesignColors.current
    val categories = current.categories.orEmpty()
    Column {
      Hairline(line)
      Column(
        Modifier.fillMaxWidth()
          .background(design.display)
          .drawWithContent {
            drawContent()
            // die Kante oben wirft einen Schatten ins Display
            drawRect(
              Brush.verticalGradient(
                0f to Color.Black.copy(alpha = 0.45f),
                1f to Color.Transparent,
                endY = 6.dp.toPx(),
              )
            )
          }
          .padding(12.dp)
          .animateContentSize(emphasizedSpec())
      ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = current.name.uppercase(),
            style = DesignType.display.copy(fontSize = 14.sp, lineHeight = 18.sp),
            color = design.displayLit,
            maxLines = 1,
            modifier = Modifier.weight(1f),
          )
          Text(
            text =
              pluralStringResource(
                  R.plurals.myzel_category_count,
                  categories.size,
                  categories.size,
                )
                .uppercase(),
            style = DesignType.labelSmall,
            color = design.displayDim,
          )
        }
        Spacer(Modifier.height(9.dp))
        FlowRow(
          horizontalArrangement = Arrangement.spacedBy(6.dp),
          verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          categories.forEach { category ->
            DrawerChip(
              text = category.name,
              color = design.graphiteKey,
              textStyle =
                TextStyle(color = design.onGraphite, fontSize = 13.sp, lineHeight = 16.sp),
              onClick = {
                onNav(NavigationKey.AppList(AppListType.Category(category.name, category.id)))
              },
            )
          }
          DrawerChip(
            text = stringResource(R.string.myzel_category_group_all).labelCase(),
            color = design.signal,
            textStyle = DesignType.label.copy(color = design.onSignal),
            onClick = {
              val id = groupCategoryId(categories.map { it.id })
              onNav(NavigationKey.AppList(AppListType.Category(current.name, id)))
            },
          )
        }
      }
    }
  }
}

private class ShownKey {
  var key: PanelKey? = null
}

@Composable
private fun DrawerChip(text: String, color: Color, textStyle: TextStyle, onClick: () -> Unit) {
  Box(
    contentAlignment = Alignment.Center,
    modifier =
      Modifier.height(30.dp)
        .clip(ChipShape)
        .background(color)
        .clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = 11.dp),
  ) {
    BasicText(text = text, style = textStyle, maxLines = 1)
  }
}

@Composable
private fun Hairline(color: Color) {
  Spacer(Modifier.fillMaxWidth().height(1.dp).background(color))
}

@Composable
private fun VerticalHairline(color: Color) {
  Spacer(Modifier.fillMaxHeight().width(1.dp).background(color))
}

private const val COLUMNS = 4
private val KeyHeight = 54.dp
private const val NEEDANAPP_COLUMNS = 3
private val NeedanappKeyHeight = 78.dp
private val NeedanappGridShape = RoundedCornerShape(10.dp)
private val ChipShape = RoundedCornerShape(9.dp)

private const val WATCH_KEY = "myzel-watch"
private const val MISC_GROUP = "misc"

/** Kurznamen für die Tasten; unbekannte Gruppen behalten ihren vollen Namen. */
private val ShortNames =
  mapOf(
    "communication" to R.string.myzel_category_short_communication,
    "device" to R.string.myzel_category_short_device,
    "games" to R.string.myzel_category_short_games,
    "health" to R.string.myzel_category_short_health,
    "interests" to R.string.myzel_category_short_interests,
    "media" to R.string.myzel_category_short_media,
    "misc" to R.string.myzel_category_short_misc,
    "network" to R.string.myzel_category_short_network,
    "productivity" to R.string.myzel_category_short_productivity,
    "storage" to R.string.myzel_category_short_storage,
    "tools" to R.string.myzel_category_short_tools,
    "wallets" to R.string.myzel_category_short_wallets,
  )
