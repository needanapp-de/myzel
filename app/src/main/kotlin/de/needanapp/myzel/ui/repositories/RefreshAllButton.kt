package de.needanapp.myzel.ui.repositories

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.EntryPointAccessors
import de.needanapp.myzel.ui.discover.RepoUpdateEntryPoint
import org.fdroid.R
import org.fdroid.repo.RepoUpdateWorker

/**
 * Der Knopf oben in Paketquellen: aktualisiert alle eingeschalteten Paketquellen, auch kurz nach
 * dem letzten Mal, wie das Ziehen nach unten. Während einer Aktualisierung ist er aus; wie weit sie
 * ist, zeigt die Anzeige über der Liste.
 */
@Composable
internal fun RefreshAllButton() {
  val label = stringResource(R.string.myzel_repos_update_all)
  if (LocalInspectionMode.current) {
    IconButton(onClick = {}) { Icon(Icons.Default.Refresh, contentDescription = label) }
    return
  }
  val context = LocalContext.current
  val repoUpdateManager = remember {
    EntryPointAccessors.fromApplication(context, RepoUpdateEntryPoint::class.java)
      .repoUpdateManager()
  }
  val updating by repoUpdateManager.isUpdating.collectAsStateWithLifecycle()
  IconButton(
    onClick = { RepoUpdateWorker.updateNow(context, manual = true) },
    enabled = !updating,
  ) {
    Icon(Icons.Default.Refresh, contentDescription = label)
  }
}
