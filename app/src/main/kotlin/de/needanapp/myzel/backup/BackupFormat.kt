package de.needanapp.myzel.backup

import androidx.annotation.StringRes
import de.needanapp.myzel.ui.settings.isProxyValid
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import org.fdroid.R
import org.fdroid.settings.SettingsConstants.AutoUpdateValues
import org.fdroid.settings.SettingsConstants.MirrorChooserValues
import org.fdroid.settings.SettingsConstants.PREF_DEFAULT_APP_AUTO_UPDATES
import org.fdroid.settings.SettingsConstants.PREF_DEFAULT_APP_LIST_SORT_ORDER
import org.fdroid.settings.SettingsConstants.PREF_DEFAULT_INSTALL_HISTORY
import org.fdroid.settings.SettingsConstants.PREF_DEFAULT_MIRROR_CHOOSER
import org.fdroid.settings.SettingsConstants.PREF_DEFAULT_MY_APPS_SORT_ORDER
import org.fdroid.settings.SettingsConstants.PREF_DEFAULT_PREVENT_SCREENSHOTS
import org.fdroid.settings.SettingsConstants.PREF_DEFAULT_PROXY
import org.fdroid.settings.SettingsConstants.PREF_DEFAULT_REPO_AUTO_UPDATES
import org.fdroid.settings.SettingsConstants.PREF_DEFAULT_SHOW_INCOMPATIBLE
import org.fdroid.settings.SettingsConstants.PREF_DEFAULT_SHOW_SEARCH_KEYBOARD
import org.fdroid.settings.SettingsConstants.PREF_DEFAULT_WARN_WHEN_METERED
import org.fdroid.settings.SettingsConstants.PREF_KEY_APP_AUTO_UPDATES
import org.fdroid.settings.SettingsConstants.PREF_KEY_APP_LIST_SORT_ORDER
import org.fdroid.settings.SettingsConstants.PREF_KEY_INSTALL_HISTORY
import org.fdroid.settings.SettingsConstants.PREF_KEY_MIRROR_CHOOSER
import org.fdroid.settings.SettingsConstants.PREF_KEY_MY_APPS_SORT_ORDER
import org.fdroid.settings.SettingsConstants.PREF_KEY_PREVENT_SCREENSHOTS
import org.fdroid.settings.SettingsConstants.PREF_KEY_PROXY
import org.fdroid.settings.SettingsConstants.PREF_KEY_REPO_AUTO_UPDATES
import org.fdroid.settings.SettingsConstants.PREF_KEY_SHOW_INCOMPATIBLE
import org.fdroid.settings.SettingsConstants.PREF_KEY_SHOW_SEARCH_KEYBOARD
import org.fdroid.settings.SettingsConstants.PREF_KEY_WARN_WHEN_METERED
import org.fdroid.settings.SettingsConstants.PREF_USE_DNS_CACHE
import org.fdroid.settings.SettingsConstants.PREF_USE_DNS_CACHE_DEFAULT

// Die Sicherung von Myzel: eine unverschlüsselte JSON-Datei. Sie enthält keine Passwörter, die
// Zugangsdaten von Paketquellen bleiben bewusst draußen. Dieser Teil kommt ohne Android aus und
// ist in BackupFormatTest geprüft; Lesen und Einspielen auf dem Gerät macht BackupManager.

internal const val BACKUP_FORMAT = "de.needanapp.myzel.backup"
internal const val BACKUP_VERSION = 1

/** Größer wird eine Sicherung nur mit einer großen eigenen Schrift in der Darstellung. */
internal const val MAX_BACKUP_SIZE = 32L * 1024 * 1024

/** Die Bereiche, die man beim Sichern und Einspielen einzeln wählen kann. */
enum class BackupSection(@StringRes val label: Int, @StringRes val summary: Int) {
  REPOSITORIES(R.string.myzel_backup_section_repos, R.string.myzel_backup_section_repos_summary),
  FAVORITES(R.string.myzel_favorites, R.string.myzel_backup_section_favorites_summary),
  SETTINGS(R.string.menu_settings, R.string.myzel_backup_section_settings_summary),
  APPEARANCE(
    de.needanapp.design.R.string.nd_appearance,
    R.string.myzel_backup_section_appearance_summary,
  ),
}

@Serializable
data class BackupFile(
  val format: String = BACKUP_FORMAT,
  val version: Int = BACKUP_VERSION,
  /** Zeitpunkt der Sicherung in Millisekunden. */
  val created: Long = 0,
  val appVersion: String = "",
  /** In der Reihenfolge der Priorität, die oberste zuerst. */
  val repositories: List<BackupRepository>? = null,
  /** Paketnamen. */
  val favorites: List<String>? = null,
  val settings: BackupSettings? = null,
  /** Wie ThemeSettingsStore.exportBackup des Design-Pakets sie schreibt. */
  val appearance: JsonObject? = null,
) {
  /** Die Bereiche, die in dieser Sicherung stecken. */
  val sections: List<BackupSection>
    get() =
      listOfNotNull(
        BackupSection.REPOSITORIES.takeIf { repositories != null },
        BackupSection.FAVORITES.takeIf { favorites != null },
        BackupSection.SETTINGS.takeIf { settings != null },
        BackupSection.APPEARANCE.takeIf { appearance != null },
      )
}

/** Eine Paketquelle ohne Zugangsdaten. */
@Serializable
data class BackupRepository(
  val address: String,
  val certificate: String = "",
  val name: String = "",
  val description: String = "",
  val enabled: Boolean = true,
  val userMirrors: List<String> = emptyList(),
  val disabledMirrors: List<String> = emptyList(),
)

/**
 * Die Einstellungen von F-Droid, die Myzel sichert, unter ihren Schlüsseln in den Preferences.
 * Nicht dabei sind Zeitstempel, Zwischenspeicher und Werte, die zum Gerät gehören (App-Symbol,
 * DNS-Cache, ignorierte App-Probleme). Hell/dunkel und Sprache stehen in der Darstellung.
 */
@Serializable
data class BackupSettings(
  @SerialName(PREF_KEY_REPO_AUTO_UPDATES) val repoAutoUpdates: String? = null,
  @SerialName(PREF_KEY_APP_AUTO_UPDATES) val appAutoUpdates: String? = null,
  @SerialName(PREF_KEY_MIRROR_CHOOSER) val mirrorChooser: String? = null,
  @SerialName(PREF_KEY_PROXY) val proxy: String? = null,
  @SerialName(PREF_USE_DNS_CACHE) val useDnsCache: Boolean? = null,
  @SerialName(PREF_KEY_PREVENT_SCREENSHOTS) val preventScreenshots: Boolean? = null,
  @SerialName(PREF_KEY_WARN_WHEN_METERED) val warnWhenMetered: Boolean? = null,
  @SerialName(PREF_KEY_SHOW_SEARCH_KEYBOARD) val showSearchKeyboard: Boolean? = null,
  @SerialName(PREF_KEY_SHOW_INCOMPATIBLE) val showIncompatible: Boolean? = null,
  @SerialName(PREF_KEY_APP_LIST_SORT_ORDER) val appListSortOrder: String? = null,
  @SerialName(PREF_KEY_MY_APPS_SORT_ORDER) val myAppsSortOrder: String? = null,
  @SerialName(PREF_KEY_INSTALL_HISTORY) val keepInstallHistory: Boolean? = null,
) {
  /**
   * Nur Werte, die F-Droid kennt: unbekannte fallen weg und behalten beim Einspielen ihren jetzigen
   * Wert.
   */
  fun checked(): BackupSettings =
    copy(
      repoAutoUpdates = repoAutoUpdates?.takeIf { it in AUTO_UPDATE_VALUES },
      // „nur beim Öffnen der App“ gibt es für App-Updates nicht
      appAutoUpdates =
        appAutoUpdates?.takeIf {
          it in AUTO_UPDATE_VALUES && it != AutoUpdateValues.OnlyWhenOpenApp.name
        },
      mirrorChooser =
        mirrorChooser?.takeIf { value -> MirrorChooserValues.entries.any { it.name == value } },
      proxy = proxy?.trim()?.takeIf { it.isEmpty() || isProxyValid(it) },
      appListSortOrder = appListSortOrder?.takeIf { it in SORT_ORDERS },
      myAppsSortOrder = myAppsSortOrder?.takeIf { it in SORT_ORDERS },
    )

  /** Die Werte zum Schreiben in die Preferences, ohne fehlende. */
  fun toPreferences(): Map<String, Any> =
    listOf(
        PREF_KEY_REPO_AUTO_UPDATES to repoAutoUpdates,
        PREF_KEY_APP_AUTO_UPDATES to appAutoUpdates,
        PREF_KEY_MIRROR_CHOOSER to mirrorChooser,
        PREF_KEY_PROXY to proxy,
        PREF_USE_DNS_CACHE to useDnsCache,
        PREF_KEY_PREVENT_SCREENSHOTS to preventScreenshots,
        PREF_KEY_WARN_WHEN_METERED to warnWhenMetered,
        PREF_KEY_SHOW_SEARCH_KEYBOARD to showSearchKeyboard,
        PREF_KEY_SHOW_INCOMPATIBLE to showIncompatible,
        PREF_KEY_APP_LIST_SORT_ORDER to appListSortOrder,
        PREF_KEY_MY_APPS_SORT_ORDER to myAppsSortOrder,
        PREF_KEY_INSTALL_HISTORY to keepInstallHistory,
      )
      .mapNotNull { (key, value) -> value?.let { key to it } }
      .toMap()

  companion object {
    private val AUTO_UPDATE_VALUES = AutoUpdateValues.entries.map { it.name }.toSet()
    // wie SettingsConstants.getAppListSortOrder und toSettings
    private val SORT_ORDERS = setOf("lastUpdated", "name")

    /** Die jetzigen Einstellungen aus den Preferences, mit den Standardwerten von F-Droid. */
    fun fromPreferences(prefs: Map<String, Any?>): BackupSettings {
      fun string(key: String, default: String) = prefs[key] as? String ?: default
      fun boolean(key: String, default: Boolean) = prefs[key] as? Boolean ?: default
      return BackupSettings(
        repoAutoUpdates = string(PREF_KEY_REPO_AUTO_UPDATES, PREF_DEFAULT_REPO_AUTO_UPDATES),
        appAutoUpdates = string(PREF_KEY_APP_AUTO_UPDATES, PREF_DEFAULT_APP_AUTO_UPDATES),
        mirrorChooser = string(PREF_KEY_MIRROR_CHOOSER, PREF_DEFAULT_MIRROR_CHOOSER),
        proxy = string(PREF_KEY_PROXY, PREF_DEFAULT_PROXY),
        useDnsCache = boolean(PREF_USE_DNS_CACHE, PREF_USE_DNS_CACHE_DEFAULT),
        preventScreenshots =
          boolean(PREF_KEY_PREVENT_SCREENSHOTS, PREF_DEFAULT_PREVENT_SCREENSHOTS),
        warnWhenMetered = boolean(PREF_KEY_WARN_WHEN_METERED, PREF_DEFAULT_WARN_WHEN_METERED),
        showSearchKeyboard =
          boolean(PREF_KEY_SHOW_SEARCH_KEYBOARD, PREF_DEFAULT_SHOW_SEARCH_KEYBOARD),
        showIncompatible = boolean(PREF_KEY_SHOW_INCOMPATIBLE, PREF_DEFAULT_SHOW_INCOMPATIBLE),
        appListSortOrder = string(PREF_KEY_APP_LIST_SORT_ORDER, PREF_DEFAULT_APP_LIST_SORT_ORDER),
        myAppsSortOrder = string(PREF_KEY_MY_APPS_SORT_ORDER, PREF_DEFAULT_MY_APPS_SORT_ORDER),
        keepInstallHistory = boolean(PREF_KEY_INSTALL_HISTORY, PREF_DEFAULT_INSTALL_HISTORY),
      )
    }
  }
}

/** Eine Sicherung ließ sich nicht lesen; [messageRes] sagt warum. */
class BackupException(@StringRes val messageRes: Int, cause: Throwable? = null) :
  Exception(null, cause)

internal val backupJson = Json {
  ignoreUnknownKeys = true
  explicitNulls = false
  encodeDefaults = true
  prettyPrint = true
  prettyPrintIndent = "  "
}

internal fun encodeBackup(backup: BackupFile): String =
  backupJson.encodeToString(BackupFile.serializer(), backup)

/**
 * Liest eine Sicherung und lässt nur brauchbare Werte durch.
 *
 * @throws BackupException wenn [text] keine Sicherung von Myzel ist, von einer neueren Version
 *   stammt oder beschädigt ist
 */
internal fun decodeBackup(text: String): BackupFile {
  val root =
    try {
      backupJson.parseToJsonElement(text) as? JsonObject
    } catch (e: SerializationException) {
      throw BackupException(R.string.myzel_backup_error_not_a_backup, e)
    }
  val format = root?.get("format")?.jsonPrimitive?.contentOrNull
  if (format != BACKUP_FORMAT) throw BackupException(R.string.myzel_backup_error_not_a_backup)
  val version = root["version"]?.jsonPrimitive?.intOrNull ?: 0
  if (version > BACKUP_VERSION) throw BackupException(R.string.myzel_backup_error_newer_version)
  val backup =
    try {
      backupJson.decodeFromJsonElement(BackupFile.serializer(), root)
    } catch (e: IllegalArgumentException) {
      // SerializationException ist auch eine IllegalArgumentException
      throw BackupException(R.string.myzel_backup_error_damaged, e)
    }
  return backup.copy(
    repositories =
      backup.repositories?.map { it.copy(userMirrors = it.userMirrors.filter(::isWebAddress)) },
    favorites = backup.favorites?.filter { PACKAGE_NAME.matches(it) }?.distinct(),
    settings = backup.settings?.checked(),
  )
}

private val PACKAGE_NAME = Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)*")

internal fun isWebAddress(address: String): Boolean =
  address.startsWith("https://", ignoreCase = true) ||
    address.startsWith("http://", ignoreCase = true)

/** Eine vorhandene Paketquelle, so weit die Planung sie braucht. */
internal data class ExistingRepo(val repoId: Long, val address: String, val certificate: String)

/** Was mit einer Paketquelle der Sicherung passiert. */
internal sealed interface RepoStep {
  val entry: BackupRepository

  /** Eine vorhandene Quelle bekommt die Werte der Sicherung. */
  data class Update(override val entry: BackupRepository, val repoId: Long) : RepoStep

  /** Eine neue Quelle. */
  data class Add(override val entry: BackupRepository) : RepoStep
}

/**
 * Was mit den Paketquellen einer Sicherung passiert.
 *
 * @property steps in der Reihenfolge der Sicherung
 * @property skipped Quellen, die nicht eingespielt werden
 */
internal data class RepoPlan(val steps: List<RepoStep>, val skipped: List<BackupRepository>)

/**
 * Ordnet die Paketquellen einer Sicherung den vorhandenen zu: über das Zertifikat, sonst über die
 * Adresse. Übersprungen werden Quellen ohne Zertifikat oder ohne Web-Adresse (etwa von einem
 * USB-Stick), doppelte und solche, deren Adresse hier schon mit einem anderen Zertifikat
 * eingetragen ist: dort könnte jemand anderes signieren.
 */
internal fun planRepoRestore(
  backup: List<BackupRepository>,
  existing: List<ExistingRepo>,
): RepoPlan {
  val steps = mutableListOf<RepoStep>()
  val skipped = mutableListOf<BackupRepository>()
  val usedIds = mutableSetOf<Long>()
  val usedCertificates = mutableSetOf<String>()
  for (entry in backup) {
    val certificate = entry.certificate.lowercase()
    if (certificate.isBlank() || !isWebAddress(entry.address) || certificate in usedCertificates) {
      skipped += entry
      continue
    }
    usedCertificates += certificate
    val byCertificate = existing.firstOrNull {
      it.repoId !in usedIds && it.certificate.equals(certificate, true)
    }
    when {
      byCertificate != null -> {
        usedIds += byCertificate.repoId
        steps += RepoStep.Update(entry, byCertificate.repoId)
      }
      existing.any { sameAddress(it.address, entry.address) } -> skipped += entry
      else -> steps += RepoStep.Add(entry)
    }
  }
  return RepoPlan(steps, skipped)
}

private fun sameAddress(a: String, b: String): Boolean =
  a.trimEnd('/').equals(b.trimEnd('/'), ignoreCase = true)

/**
 * Die Schritte, mit denen [current] zu [wanted] wird, als Paare (verschieben, an die Stelle von).
 * Jeder Schritt nimmt eine Quelle von weiter unten und setzt sie an die Stelle des Ziels, das mit
 * allen dazwischen eins nach unten rückt; so arbeitet RepoManager.reorderRepositories. Quellen, die
 * in [wanted] fehlen, behalten ihre Reihenfolge unter den gewünschten.
 */
internal fun reorderSteps(current: List<Long>, wanted: List<Long>): List<Pair<Long, Long>> {
  val target = wanted.filter { it in current } + current.filter { it !in wanted }
  val list = current.toMutableList()
  val steps = mutableListOf<Pair<Long, Long>>()
  target.forEachIndexed { index, repoId ->
    if (list[index] != repoId) {
      steps += repoId to list[index]
      list.remove(repoId)
      list.add(index, repoId)
    }
  }
  return steps
}
