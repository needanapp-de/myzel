package de.needanapp.myzel.favorites

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults.enterAlwaysScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.needanapp.myzel.ui.apps.AppListPanelCard
import de.needanapp.myzel.ui.apps.rememberAppPanelHost
import de.needanapp.myzel.ui.compat.BackButton
import de.needanapp.myzel.ui.compat.Scaffold
import de.needanapp.myzel.ui.compat.TopAppBar
import org.fdroid.R
import org.fdroid.ui.utils.BigLoadingIndicator

/**
 * Die Favoriten als Liste, jede App eine Karte wie in „Meine Apps“ mit Name, Pillen und
 * Kurzbeschreibung. Tippen öffnet die App, langes Drücken klappt darunter das Bedienfeld auf
 * ([AppPanel]); bei Apps, die nicht installiert sind, steht dort vorn „Installieren“. Auf großen
 * Bildschirmen ist [currentPackageName] die App, deren Details daneben offen sind.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FavoritesScreen(
  model: FavoritesModel?,
  currentPackageName: String?,
  onBackClicked: () -> Unit,
  onItemClick: (String) -> Unit,
) {
  val scrollBehavior = enterAlwaysScrollBehavior(rememberTopAppBarState())
  // die App, deren Bedienfeld offen ist (höchstens eine); Zurück klappt es zu
  var openPanel by rememberSaveable { mutableStateOf<String?>(null) }
  BackHandler(enabled = openPanel != null) { openPanel = null }
  val optionsLabel = stringResource(R.string.myzel_my_apps_options)
  Scaffold(
    topBar = {
      TopAppBar(
        title = { Text(stringResource(R.string.myzel_favorites), maxLines = 1) },
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
          model?.apps?.find { it.app.packageName == packageName }?.app?.iconModel
        },
        coveredBottom = paddingValues.calculateBottomPadding(),
      )
    when {
      model == null -> BigLoadingIndicator(Modifier.padding(paddingValues))
      model.apps.isEmpty() -> EmptyFavorites(model.unavailable, paddingValues)
      else ->
        LazyColumn(
          contentPadding = paddingValues + PaddingValues(top = 8.dp),
          modifier =
            Modifier.then(if (currentPackageName == null) Modifier else Modifier.selectableGroup()),
        ) {
          items(model.apps, key = { it.app.packageName }) { favorite ->
            val app = favorite.app
            val packageName = app.packageName
            val selection =
              if (currentPackageName == null) Modifier
              else Modifier.semantics { selected = packageName == currentPackageName }
            AppListPanelCard(
              app = app,
              installed = favorite.installed,
              isSelected = currentPackageName == packageName,
              open = openPanel == packageName,
              panelHost = panelHost,
              modifier = Modifier.animateItem(),
              clickModifier =
                Modifier.combinedClickable(
                    onClick = { onItemClick(packageName) },
                    onLongClick = {
                      openPanel = if (openPanel == packageName) null else packageName
                    },
                    onLongClickLabel = optionsLabel,
                  )
                  .then(selection),
            )
          }
          if (model.unavailable > 0)
            item(key = "myzel_unavailable") { UnavailableHint(model.unavailable) }
        }
    }
  }
}

/** Noch keine Favoriten: das Logo ausgegraut wie der Knopf in den App-Details und ein Hinweis. */
@Composable
private fun EmptyFavorites(unavailable: Int, paddingValues: PaddingValues) {
  Column(
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = spacedBy(16.dp, Alignment.CenterVertically),
    modifier = Modifier.fillMaxSize().padding(paddingValues).padding(32.dp),
  ) {
    Icon(
      painter = painterResource(R.drawable.myzel_logo),
      contentDescription = null,
      tint = MaterialTheme.colorScheme.onSurface.copy(alpha = FAVORITE_OFF_ALPHA),
      modifier = Modifier.size(64.dp),
    )
    Text(
      text = stringResource(R.string.myzel_favorites_empty),
      style = MaterialTheme.typography.bodyLarge,
      textAlign = TextAlign.Center,
    )
    if (unavailable > 0) UnavailableHint(unavailable)
  }
}

@Composable
private fun UnavailableHint(unavailable: Int) {
  Text(
    text = pluralStringResource(R.plurals.myzel_favorites_unavailable, unavailable, unavailable),
    style = MaterialTheme.typography.bodySmall,
    color = MaterialTheme.colorScheme.onSurfaceVariant,
    textAlign = TextAlign.Center,
    modifier = Modifier.fillMaxWidth().padding(16.dp),
  )
}

/** Deckkraft des Logos, wenn eine App kein Favorit ist: wie ein abgeschalteter Knopf. */
internal const val FAVORITE_OFF_ALPHA = 0.38f
