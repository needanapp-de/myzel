package de.needanapp.myzel.diagnose

import android.content.Context
import androidx.core.content.edit
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.fdroid.install.InstallState

/**
 * Ein gescheiterter Installationsversuch, wie [InstallState.Error] ihn meldet.
 *
 * @param message die Meldung von Android, etwa „INSTALL_FAILED_UPDATE_INCOMPATIBLE: …“
 */
data class InstallError(val time: Long, val versionName: String, val message: String?)

/**
 * Merkt sich je App den letzten gescheiterten Installationsversuch, damit die Update-Diagnose ihn
 * auch nach einem Neustart zeigt. Eine erfolgreiche Installation löscht ihn wieder. Gespeichert in
 * den SharedPreferences „myzel_install_errors“, je Paketname ein JSON-Objekt. Mit
 * kotlinx.serialization statt org.json: Das gibt es in den Unit-Tests nur als Android-Stub, der bei
 * jedem Aufruf wirft.
 */
class InstallErrorLog private constructor(context: Context) {
  private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

  /** Übernimmt Fehler und Erfolge aus den Zuständen der Installationsverwaltung. */
  fun record(states: Map<String, InstallState>) {
    states.forEach { (packageName, state) ->
      when (state) {
        is InstallState.Error -> save(packageName, state)
        is InstallState.Installed ->
          if (prefs.contains(packageName)) prefs.edit { remove(packageName) }
        else -> {}
      }
    }
  }

  /** Der letzte Fehler bei [packageName], oder null. */
  fun get(packageName: String): InstallError? {
    val json = prefs.getString(packageName, null) ?: return null
    return try {
      val stored = Json.decodeFromString<StoredError>(json)
      InstallError(
        time = stored.time,
        versionName = stored.versionName,
        message = stored.message.takeIf { it.isNotEmpty() },
      )
    } catch (_: IllegalArgumentException) {
      // auch SerializationException: kaputter oder fremder Eintrag
      null
    }
  }

  private fun save(packageName: String, state: InstallState.Error) {
    // dieselbe Meldung bleibt mit ihrer ersten Zeit stehen
    val old = get(packageName)
    if (old != null && old.versionName == state.versionName && old.message == state.msg) return
    val stored = StoredError(System.currentTimeMillis(), state.versionName, state.msg.orEmpty())
    prefs.edit { putString(packageName, Json.encodeToString(stored)) }
  }

  companion object {
    private const val PREFS = "myzel_install_errors"

    @Volatile private var instance: InstallErrorLog? = null

    fun get(context: Context): InstallErrorLog =
      instance
        ?: synchronized(this) {
          instance ?: InstallErrorLog(context.applicationContext).also { instance = it }
        }
  }
}

/** So steht ein Eintrag in den SharedPreferences, mit denselben Schlüsseln wie bisher. */
@Serializable
private data class StoredError(
  val time: Long,
  @SerialName("version") val versionName: String,
  val message: String = "",
)
