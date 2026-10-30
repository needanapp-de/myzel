package de.needanapp.myzel.ui.discover

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fdroid.database.FDroidDatabase
import org.fdroid.ui.discover.AppDiscoverItem

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DatabaseEntryPoint {
  fun db(): FDroidDatabase
}

/**
 * Namen der neuesten stabilen, mit dem Gerät verträglichen Versionen der [apps] aus allen
 * eingeschalteten Paketquellen, für die App-Karten eines Karussells: eine Abfrage für alle Karten.
 * Sie läuft neu, wenn sich die Apps oder ihr Stand ändern; bis dahin bleiben die alten Namen
 * stehen.
 */
@Composable
fun rememberLatestVersionNames(apps: List<AppDiscoverItem>): Map<String, String> {
  if (LocalInspectionMode.current) return emptyMap()
  val context = LocalContext.current
  val db = remember {
    EntryPointAccessors.fromApplication(context, DatabaseEntryPoint::class.java).db()
  }
  val key = apps.map { it.packageName to it.lastUpdated }
  val names by
    produceState(emptyMap<String, String>(), key) {
      value =
        withContext(Dispatchers.IO) {
          db.getVersionDao().getLatestVersionNames(key.map { it.first })
        }
    }
  return names
}
