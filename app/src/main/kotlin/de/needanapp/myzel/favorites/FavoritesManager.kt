package de.needanapp.myzel.favorites

import android.content.Context
import androidx.core.content.edit
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.updateAndGet

/**
 * Die Favoriten von Myzel: eine Menge von Paketnamen in eigenen SharedPreferences. Bewusst nicht in
 * der Datenbank von F-Droid (libs/database bleibt unverändert) und nicht als Datei, weil Androids
 * Sicherung Dateien der App ausschließt, Prefs aber mitnimmt.
 */
@Singleton
class FavoritesManager @Inject constructor(@ApplicationContext context: Context) {

  private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
  private val _favorites =
    MutableStateFlow(prefs.getStringSet(KEY_FAVORITES, null)?.toSet() ?: emptySet())

  /** Paketnamen der Favoriten. */
  val favorites: StateFlow<Set<String>> = _favorites.asStateFlow()

  fun isFavorite(packageName: String): Boolean = packageName in _favorites.value

  /**
   * Nimmt die App in die Favoriten auf oder entfernt sie wieder.
   *
   * @return ob die App danach ein Favorit ist
   */
  fun toggle(packageName: String): Boolean =
    packageName in save { if (packageName in it) it - packageName else it + packageName }

  /** Ersetzt alle Favoriten, etwa beim Einspielen einer Sicherung. */
  fun replaceAll(packageNames: Set<String>) {
    save { packageNames }
  }

  private fun save(change: (Set<String>) -> Set<String>): Set<String> {
    val new = _favorites.updateAndGet(change)
    // eine neue Menge schreiben: SharedPreferences erkennen Änderungen an der alten nicht
    prefs.edit { putStringSet(KEY_FAVORITES, HashSet(new)) }
    return new
  }

  private companion object {
    const val PREFS_NAME = "myzel_favorites"
    const val KEY_FAVORITES = "favorites"
  }
}

/** Zugang zum [FavoritesManager] für Composables ohne eigenes ViewModel. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface FavoritesEntryPoint {
  fun favoritesManager(): FavoritesManager
}
