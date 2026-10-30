package de.needanapp.myzel.recent

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults.enterAlwaysScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.needanapp.design.components.ChipRow
import de.needanapp.design.components.DeviceDisplay
import de.needanapp.design.components.StatusTone
import de.needanapp.design.components.TabChip
import de.needanapp.myzel.ui.apps.AppListPanelCard
import de.needanapp.myzel.ui.apps.SectionTitle
import de.needanapp.myzel.ui.apps.dayTitle
import de.needanapp.myzel.ui.apps.daysSince
import de.needanapp.myzel.ui.apps.rememberAppPanelHost
import de.needanapp.myzel.ui.compat.BackButton
import de.needanapp.myzel.ui.compat.Scaffold
import de.needanapp.myzel.ui.compat.TopAppBar
import org.fdroid.R
import org.fdroid.ui.utils.BigLoadingIndicator

/** Was die Seite zeigt: alles, nur neue, nur aktualisierte oder nur installierte Apps. */
enum class RecentFilter(@StringRes val label: Int) {
  ALL(R.string.myzel_recent_filter_all),
  NEW(R.string.myzel_recent_filter_new),
  UPDATED(R.string.myzel_recent_filter_updated),
  INSTALLED(R.string.myzel_recent_filter_installed);

  fun matches(item: RecentItem): Boolean =
    when (this) {
      ALL -> true
      NEW -> item.isNew
      UPDATED -> !item.isNew
      INSTALLED -> item.app.isInstalled
    }
}

/**
 * „Neu und aktualisiert“: oben die Anzeige mit „6 NEU · 24 AKTUALISIERT“ und der gewählten
 * Paketquelle, darunter Chips für die Art ([RecentFilter]) und die Paketquellen, dann die Apps der
 * letzten 14 Tage nach Tagen („Heute“, „Gestern“, „Montag, 27. Oktober“). Die Apps sind Karten wie
 * die Favoriten ([AppListPanelCard]) mit der Pille für das Alter des Updates; langes Drücken klappt
 * das Bedienfeld auf. [initialRepoId] wählt zu Beginn eine Paketquelle, etwa nach einem Tipp auf
 * die Anzeige in Entdecken.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun RecentAppsScreen(
  model: RecentAppsModel?,
  initialRepoId: Long?,
  currentPackageName: String?,
  onBackClicked: () -> Unit,
  onItemClick: (String) -> Unit,
) {
  val scrollBehavior = enterAlwaysScrollBehavior(rememberTopAppBarState())
  // die App, deren Bedienfeld offen ist (höchstens eine); Zurück klappt es zu
  var openPanel by rememberSaveable { mutableStateOf<String?>(null) }
  BackHandler(enabled = openPanel != null) { openPanel = null }
  var filter by rememberSaveable { mutableStateOf(RecentFilter.ALL) }
  var repoId by rememberSaveable { mutableStateOf(initialRepoId) }
  val optionsLabel = stringResource(R.string.myzel_my_apps_options)
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.myzel_recent_title), maxLines = 1) },
        navigationIcon = { BackButton(onClick = onBackClicked) },
        scrollBehavior = scrollBehavior,
      )
    },
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
  ) { paddingValues ->
    val panelHost =
      rememberAppPanelHost(
        onOpenPage = onItemClick,
        onClose = { openPanel = null },
        iconModel = { packageName ->
          model?.items?.find { it.app.packageName == packageName }?.app?.iconModel
        },
        coveredBottom = paddingValues.calculateBottomPadding(),
      )
    if (model == null) {
      BigLoadingIndicator(Modifier.padding(paddingValues))
      return@Scaffold
    }
    // eine gewählte Paketquelle, aus der nichts mehr kommt, fällt als Filter weg
    val repo = model.repos.find { it.repoId == repoId }
    val shown =
      remember(model, filter, repo) {
        model.items
          .filter { (repo == null || it.app.repoId == repo.repoId) && filter.matches(it) }
          .distinctBy { it.app.packageName }
      }
    val days = remember(shown) { shown.groupBy { daysSince(it.app.lastUpdated) } }
    LazyColumn(
      contentPadding = paddingValues + PaddingValues(top = 8.dp, bottom = 16.dp),
      modifier =
        Modifier.then(if (currentPackageName == null) Modifier else Modifier.selectableGroup()),
    ) {
      item(key = "myzel_display", contentType = "display") {
        RecentDisplay(shown, repo, Modifier.padding(horizontal = 16.dp))
      }
      item(key = "myzel_filters", contentType = "chips") {
        ChipRow(RecentFilter.entries, Modifier.padding(top = 12.dp)) { entry ->
          TabChip(
            label = stringResource(entry.label),
            selected = entry == filter,
            onClick = { filter = entry },
          )
        }
      }
      if (model.repos.size > 1) {
        item(key = "myzel_repos", contentType = "chips") {
          val allRepos = stringResource(R.string.myzel_recent_all_repos)
          // eine Quelle aus der Anzeige von Entdecken steht gleich im Blick
          ChipRow(
            listOf(null) + model.repos,
            Modifier.padding(top = 4.dp),
            initialIndex = model.repos.indexOf(repo) + 1,
          ) { entry ->
            TabChip(
              label = entry?.name ?: allRepos,
              selected = entry?.repoId == repo?.repoId,
              onClick = { repoId = entry?.repoId },
            )
          }
        }
      }
      if (shown.isEmpty()) {
        item(key = "myzel_empty") {
          Text(
            text = stringResource(R.string.myzel_recent_empty),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(32.dp),
          )
        }
      }
      days.forEach { (day, apps) ->
        item(key = "myzel_day_$day", contentType = "day") {
          SectionTitle(
            dayTitle(apps.first().app.lastUpdated),
            count = apps.size,
            modifier = Modifier.animateItem(),
          )
        }
        items(apps, key = { it.app.packageName }, contentType = { "app" }) { recent ->
          val app = recent.app
          val packageName = app.packageName
          val selection =
            if (currentPackageName == null) Modifier
            else Modifier.semantics { selected = packageName == currentPackageName }
          AppListPanelCard(
            app = app,
            installed = recent.installed,
            isSelected = currentPackageName == packageName,
            open = openPanel == packageName,
            panelHost = panelHost,
            modifier = Modifier.animateItem(),
            clickModifier =
              Modifier.combinedClickable(
                  onClick = { onItemClick(packageName) },
                  onLongClick = { openPanel = if (openPanel == packageName) null else packageName },
                  onLongClickLabel = optionsLabel,
                )
                .then(selection),
            updatedAt = app.lastUpdated,
          )
        }
      }
    }
  }
}

/**
 * Die Anzeige oben: wie viele der gezeigten Apps neu und wie viele aktualisiert sind, rechts die
 * gewählte Paketquelle oder der Zeitraum.
 */
@Composable
private fun RecentDisplay(shown: List<RecentItem>, repo: RecentRepo?, modifier: Modifier) {
  val new = shown.count { it.isNew }
  val updated = shown.size - new
  val newText = pluralStringResource(R.plurals.myzel_display_new, new, new)
  val updatedText = pluralStringResource(R.plurals.myzel_display_updated, updated, updated)
  val text =
    when {
      new > 0 && updated > 0 -> stringResource(R.string.myzel_display_joined, newText, updatedText)
      updated > 0 -> updatedText
      else -> newText
    }
  DeviceDisplay(
    text = text,
    tone = if (shown.isEmpty()) StatusTone.NEUTRAL else StatusTone.POSITIVE,
    detail = repo?.name ?: stringResource(R.string.myzel_recent_days),
    modifier = modifier,
  )
}
