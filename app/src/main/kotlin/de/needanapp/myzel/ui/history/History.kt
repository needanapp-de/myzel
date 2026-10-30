package de.needanapp.myzel.ui.history

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults.enterAlwaysScrollBehavior
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import de.needanapp.design.components.ContentDialog
import de.needanapp.myzel.ui.compat.BackButton
import de.needanapp.myzel.ui.compat.Scaffold
import de.needanapp.myzel.ui.compat.TopAppBar
import de.needanapp.myzel.ui.compat.TopAppBarButton
import org.fdroid.R
import org.fdroid.ui.history.HistoryItem
import org.fdroid.ui.utils.BigLoadingIndicator

/**
 * Ersatz für org.fdroid.ui.history.History mit derselben Signatur, über den Import in Main.kt
 * eingebunden (myzel/tools/apply-imports.py): Kopfleiste mit den Knöpfen von Myzel, die Liste aus
 * [HistoryList] und „Verlauf löschen?“ als Dialog des Design-Pakets.
 *
 * Bei einem Update von F-Droid mit org.fdroid.ui.history.History vergleichen.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun History(
  items: List<HistoryItem>?,
  enabled: Boolean?,
  onEnabled: (Boolean) -> Unit,
  onDeleteAll: () -> Unit,
  onBackClicked: (() -> Unit)?,
) {
  var deleteAllDialogShown by remember { mutableStateOf(false) }
  val scrollBehavior = enterAlwaysScrollBehavior(rememberTopAppBarState())
  Scaffold(
    topBar = {
      TopAppBar(
        navigationIcon = { if (onBackClicked != null) BackButton(onClick = onBackClicked) },
        title = { Text(stringResource(R.string.install_history), maxLines = 1) },
        actions = {
          if (!items.isNullOrEmpty())
            TopAppBarButton(
              imageVector = Icons.Outlined.DeleteSweep,
              contentDescription = stringResource(R.string.install_history_delete_ally),
              onClick = { deleteAllDialogShown = true },
            )
        },
        scrollBehavior = scrollBehavior,
      )
    },
    modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
  ) { paddingValues ->
    if (items == null) BigLoadingIndicator(modifier = Modifier.padding(paddingValues))
    else HistoryList(items, enabled, onEnabled, paddingValues)
  }
  if (deleteAllDialogShown) {
    ContentDialog(
      title = stringResource(R.string.myzel_history_delete_title),
      confirmLabel = stringResource(R.string.delete),
      onConfirm = {
        onDeleteAll()
        deleteAllDialogShown = false
      },
      onDismiss = { deleteAllDialogShown = false },
      isDestructive = true,
      icon = Icons.Outlined.Delete,
    ) {
      Text(stringResource(R.string.myzel_history_delete_text))
    }
  }
}
