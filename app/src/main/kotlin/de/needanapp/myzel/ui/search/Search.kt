package de.needanapp.myzel.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.placeCursorAtEnd
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import de.needanapp.design.components.DeviceDisplay
import de.needanapp.design.components.EmptyState
import de.needanapp.design.components.StatusTone
import de.needanapp.myzel.ui.apps.SectionTitle
import de.needanapp.myzel.ui.apps.selectedColors
import de.needanapp.myzel.ui.compat.BackButton
import de.needanapp.myzel.ui.compat.DropdownMenuItem
import de.needanapp.myzel.ui.compat.ListItem
import de.needanapp.myzel.ui.compat.Scaffold
import de.needanapp.myzel.ui.compat.Switch
import de.needanapp.myzel.ui.compat.TopAppBar
import de.needanapp.myzel.ui.compat.TopAppBarOverflowButton
import de.needanapp.myzel.ui.lists.AppListRow
import de.needanapp.myzel.ui.nav.LocalTopLevelPageActive
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.fdroid.R
import org.fdroid.search.SavedSearch
import org.fdroid.search.SearchHelper.isSearchable
import org.fdroid.ui.categories.CategoryChip
import org.fdroid.ui.categories.CategoryItem
import org.fdroid.ui.categories.ChipFlowRow
import org.fdroid.ui.lists.AppListType
import org.fdroid.ui.navigation.NavigationKey
import org.fdroid.ui.search.SearchInfo
import org.fdroid.ui.search.SearchResults
import org.fdroid.ui.utils.BigLoadingIndicator

/*
 * Ersatz für GlobalSearch und TopSearchBar aus org.fdroid.ui.search mit denselben Signaturen, über
 * myzel/tools/apply-imports.py eingebunden: das Suchfeld als Pille mit Lupe, ohne Strich darunter;
 * „Zuletzt gesucht“, „Kategorien“ und „Apps“ als Abschnittstitel wie in „Meine Apps“, der Verlauf
 * als Karten und ohne Treffer ein ruhiger Leerzustand. Die Suche selbst (Verzögerung, Verlauf,
 * Tastatur) arbeitet wie bei F-Droid.
 *
 * Bei einem Update von F-Droid mit GlobalSearch.kt, TopSearchBar.kt, AppSearchInputField.kt,
 * SearchResults.kt und PastSearches.kt vergleichen.
 */

@Composable
fun GlobalSearch(
  info: SearchInfo,
  textFieldState: TextFieldState = rememberTextFieldState(),
  onNav: (NavigationKey) -> Unit,
  onBack: () -> Unit,
) {
  LaunchedEffect(info.showKeyboard) {
    // showKeyboard wie ein Ereignis gleich wieder zurücksetzen
    if (info.showKeyboard) info.onKeyboardShown()
  }
  val searchResults = info.searchResults
  val keyboardController = LocalSoftwareKeyboardController.current
  // die Suche bleibt zusammengesetzt (TopLevelPages): die Tastatur nur, wenn sie dran ist
  val showKeyboard =
    LocalTopLevelPageActive.current &&
      (info.showKeyboard ||
        (info.autoShowKeyboard &&
          (searchResults == null ||
            searchResults.apps.isEmpty() && searchResults.categories.isEmpty())))
  Scaffold(
    topBar = {
      TopSearchBar(
        searchFieldState = textFieldState,
        shouldRequestFocus = showKeyboard,
        onSearch = info.actions::onSearch,
        onSearchCleared = info.actions::onSearchCleared,
        onHideSearch = onBack,
        actions = {
          if (textFieldState.text.isEmpty()) {
            TopAppBarOverflowButton { onDismiss ->
              DropdownMenuItem(
                leadingIcon = { Icon(Icons.Default.Keyboard, null) },
                text = { Text(stringResource(R.string.search_auto_keyboard)) },
                trailingIcon = { Switch(checked = info.autoShowKeyboard, onCheckedChange = null) },
                onClick = {
                  info.actions.setAutoShowKeyboard(!info.autoShowKeyboard)
                  onDismiss()
                },
              )
            }
          }
        },
      )
    }
  ) { paddingValues ->
    SearchContent(
      searchResults = searchResults,
      textFieldState = textFieldState,
      savedSearches = info.savedSearches,
      onClearSavedSearches = info.actions::onClearSearchHistory,
      onNav = { navKey ->
        // Tastatur vor dem Wechsel selbst schließen, sonst schließt sie spät und wirkt träge
        keyboardController?.hide()
        onNav(navKey)
      },
      paddingValues = paddingValues,
    )
  }
}

/** Kopfleiste mit Zurück und dem Suchfeld als Pille; auch für die Suche in Listen. */
@Composable
fun TopSearchBar(
  searchFieldState: TextFieldState = rememberTextFieldState(),
  shouldRequestFocus: Boolean = true,
  actions: @Composable (RowScope.() -> Unit) = {},
  onSearch: suspend (String) -> Unit,
  onSearchCleared: () -> Unit,
  onHideSearch: () -> Unit,
) {
  val focusRequester = remember { FocusRequester() }
  val keyboardController = LocalSoftwareKeyboardController.current
  TopAppBar(
    navigationIcon = { BackButton(onClick = onHideSearch) },
    title = {
      SearchField(
        state = searchFieldState,
        onSearch = onSearch,
        onSearchCleared = {
          searchFieldState.setTextAndPlaceCursorAtEnd("")
          onSearchCleared()
        },
        modifier = Modifier.focusRequester(focusRequester),
      )
    },
    actions = actions,
  )
  LaunchedEffect(shouldRequestFocus) {
    if (shouldRequestFocus) {
      focusRequester.requestFocus()
      keyboardController?.show()
    }
  }
}

/** Das Suchfeld: sucht beim Tippen nach kurzer Pause und sofort mit der Suchtaste. */
@OptIn(FlowPreview::class)
@Composable
private fun SearchField(
  state: TextFieldState,
  onSearch: suspend (String) -> Unit,
  onSearchCleared: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val scope = rememberCoroutineScope()
  LaunchedEffect(Unit) {
    state.edit { placeCursorAtEnd() }
    snapshotFlow { state.text }
      .distinctUntilChanged()
      // kurz warten, ob noch getippt wird; eine laufende Suche bricht die nächste ab
      .debounce(SEARCH_DELAY_MILLIS)
      .collectLatest { query ->
        if (query.isEmpty()) onSearchCleared()
        else if (query.isSearchable()) onSearch(query.toString())
      }
  }
  val keyboardController = LocalSoftwareKeyboardController.current
  val scheme = MaterialTheme.colorScheme
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      Modifier.padding(end = 8.dp)
        .fillMaxWidth()
        .height(48.dp)
        .background(scheme.surfaceContainerHigh, CircleShape),
  ) {
    Icon(
      Icons.Rounded.Search,
      contentDescription = null,
      tint = scheme.onSurfaceVariant,
      modifier = Modifier.padding(start = 14.dp, end = 10.dp).size(22.dp),
    )
    BasicTextField(
      state = state,
      lineLimits = TextFieldLineLimits.SingleLine,
      textStyle = MaterialTheme.typography.bodyLarge.copy(color = scheme.onSurface),
      cursorBrush = SolidColor(scheme.primary),
      keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
      onKeyboardAction = {
        if (state.text.isSearchable()) {
          keyboardController?.hide()
          scope.launch { onSearch(state.text.toString()) }
        }
      },
      decorator = { innerTextField ->
        Box(contentAlignment = Alignment.CenterStart) {
          if (state.text.isEmpty()) {
            Text(
              stringResource(R.string.search_placeholder),
              style = MaterialTheme.typography.bodyLarge,
              color = scheme.onSurfaceVariant,
            )
          }
          innerTextField()
        }
      },
      modifier = modifier.weight(1f),
    )
    if (state.text.isNotEmpty()) {
      IconButton(onClick = onSearchCleared) {
        Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.clear_search))
      }
    } else {
      Spacer(Modifier.width(14.dp))
    }
  }
}

/**
 * Vor der Suche nur der Verlauf, damit die Seite sofort steht (die Kategorien gibt es in
 * Entdecken), danach passende Kategorien und Apps unter Abschnittstiteln.
 */
@Composable
private fun SearchContent(
  searchResults: SearchResults?,
  textFieldState: TextFieldState,
  savedSearches: List<SavedSearch>?,
  onClearSavedSearches: () -> Unit,
  onNav: (NavigationKey) -> Unit,
  paddingValues: PaddingValues,
) {
  // für neue Ergebnisse wieder oben anfangen
  val listState =
    rememberSaveable(searchResults, saver = LazyListState.Saver) { LazyListState(0, 0) }
  if (searchResults == null && textFieldState.text.isSearchable()) {
    BigLoadingIndicator(Modifier.padding(paddingValues).imePadding())
    return
  }
  val keyboardController = LocalSoftwareKeyboardController.current
  LazyColumn(state = listState, contentPadding = paddingValues, modifier = Modifier.fillMaxSize()) {
    if (searchResults == null) {
      if (!savedSearches.isNullOrEmpty()) {
        pastSearches(
          savedSearches = savedSearches,
          onSearch = {
            textFieldState.edit { replace(0, length, it) }
            keyboardController?.hide()
          },
          onClearSavedSearches = onClearSavedSearches,
        )
      }
    } else {
      if (searchResults.categories.isNotEmpty()) categories(searchResults.categories, onNav)
      if (searchResults.apps.isEmpty() && textFieldState.text.isSearchable()) {
        item(key = "empty", contentType = "empty") {
          EmptyState(
            icon = Icons.Rounded.SearchOff,
            title = stringResource(R.string.myzel_search_no_results),
            message = stringResource(R.string.myzel_search_no_results_hint),
          )
        }
      } else if (searchResults.apps.isNotEmpty()) {
        item(key = "appsHeader", contentType = "header") {
          val count = searchResults.apps.size
          DeviceDisplay(
            pluralStringResource(R.plurals.myzel_display_hits, count, count),
            StatusTone.ACCENT,
            Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
          )
        }
        items(searchResults.apps, key = { it.packageName }, contentType = { "app" }) { item ->
          AppListRow(
            item = item,
            isSelected = false,
            modifier =
              Modifier.fillMaxWidth().animateItem().clickable {
                onNav(NavigationKey.AppDetails(item.packageName))
              },
          )
        }
      }
    }
    item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.systemBars)) }
    item { Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.ime)) }
  }
}

/** „Zuletzt gesucht“ mit „Löschen“, jede Suche eine Karte. */
private fun LazyListScope.pastSearches(
  savedSearches: List<SavedSearch>,
  onSearch: (String) -> Unit,
  onClearSavedSearches: () -> Unit,
) {
  item(key = "historyHeader", contentType = "header") {
    SectionTitle(stringResource(R.string.search_history)) {
      TextButton(onClick = onClearSavedSearches) { Text(stringResource(R.string.clear)) }
    }
  }
  items(savedSearches, key = { "history-${it.time}-${it.query}" }, contentType = { "history" }) {
    item ->
    ListItem(
      leadingContent = {
        Icon(
          Icons.Rounded.History,
          contentDescription = null,
          tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      },
      headlineContent = { Text(item.query) },
      colors = selectedColors(isSelected = false),
      modifier = Modifier.animateItem().clickable { onSearch(item.query) },
    )
  }
}

/** Kategorien als Chips unter einem Abschnittstitel. */
private fun LazyListScope.categories(
  categories: List<CategoryItem>,
  onNav: (NavigationKey) -> Unit,
) {
  item(key = "categoriesHeader", contentType = "header") {
    SectionTitle(stringResource(R.string.main_menu__categories))
  }
  item(key = "categories", contentType = "categories") {
    ChipFlowRow(Modifier.padding(horizontal = 16.dp)) {
      categories.forEach { category ->
        CategoryChip(
          categoryItem = category,
          onClick = {
            onNav(NavigationKey.AppList(AppListType.Category(category.name, category.id)))
          },
        )
      }
    }
  }
}

/** So lange nach dem letzten Tastendruck sucht das Suchfeld los (F-Droid: 500 ms). */
private const val SEARCH_DELAY_MILLIS = 150L
