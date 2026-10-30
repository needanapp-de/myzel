package de.needanapp.myzel.settings

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import me.zhanghai.compose.preference.MapPreferences
import me.zhanghai.compose.preference.Preferences

/**
 * Hält den Einstellungs-Flow von compose-preference mit den [prefs] gleich.
 *
 * `createPreferenceFlow` liest die SharedPreferences nur einmal beim Anlegen und schreibt bei jeder
 * Änderung über den Flow die ganze Map zurück (erst `clear()`, dann alle Werte). Was F-Droids
 * SettingsManager seitdem direkt in die SharedPreferences geschrieben hat, ging dabei verloren: der
 * Zeitpunkt der letzten Prüfung auf Updates, ignorierte App-Probleme, die Sortierung in „Meine
 * Apps“ und andere. Nach einer Änderung in der Darstellung stand dann etwa „Zuletzt nach Updates
 * gesucht: nie“, und F-Droid hielt die App für frisch installiert.
 *
 * Der Listener übernimmt jede Änderung von außen in den Flow. Schreibt der Flow selbst, stimmen die
 * Werte danach schon überein, und der Listener ändert nichts.
 */
fun MutableStateFlow<Preferences>.syncWith(
  prefs: SharedPreferences
): MutableStateFlow<Preferences> {
  val listener = SharedPreferences.OnSharedPreferenceChangeListener { changed, _ ->
    val current = changed.all
    if (value.asMap() != current) {
      @Suppress("UNCHECKED_CAST")
      value = MapPreferences(current as Map<String, Any>)
    }
  }
  // SharedPreferences hält Listener nur schwach
  synchronized(listeners) { listeners += listener }
  prefs.registerOnSharedPreferenceChangeListener(listener)
  return this
}

private val listeners = mutableListOf<SharedPreferences.OnSharedPreferenceChangeListener>()
