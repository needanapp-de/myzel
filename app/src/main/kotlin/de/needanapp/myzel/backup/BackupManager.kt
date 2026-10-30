package de.needanapp.myzel.backup

import android.content.Context
import android.net.Uri
import android.widget.Toast
import android.widget.Toast.LENGTH_LONG
import androidx.core.os.LocaleListCompat
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.needanapp.design.theme.ThemeSettingsStore
import de.needanapp.myzel.favorites.FavoritesManager
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.jsonObject
import mu.KotlinLogging
import org.fdroid.BuildConfig
import org.fdroid.R
import org.fdroid.database.FDroidDatabase
import org.fdroid.database.InitialRepository
import org.fdroid.index.RepoManager
import org.fdroid.repo.RepoUpdateWorker
import org.fdroid.settings.SettingsManager
import org.fdroid.settings.toAutoUpdateValue
import org.fdroid.updates.AppUpdateWorker
import org.json.JSONObject

/**
 * Sichert Paketquellen, Favoriten, Einstellungen und Darstellung in eine Datei und spielt sie
 * wieder ein. Das Format steht in BackupFormat.kt.
 */
@Singleton
class BackupManager
@Inject
constructor(
  @param:ApplicationContext private val context: Context,
  private val db: FDroidDatabase,
  private val repoManager: RepoManager,
  private val settingsManager: SettingsManager,
  private val favoritesManager: FavoritesManager,
) {

  private val log = KotlinLogging.logger {}

  /**
   * Das Einspielen läuft hier statt im Dialog weiter: Eine andere Sprache in der Darstellung baut
   * die Activities neu auf und beendet dabei deren Coroutines.
   */
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  /** Schreibt die gewählten [sections] als Sicherung nach [uri]. */
  suspend fun export(sections: Set<BackupSection>, uri: Uri) {
    val backup =
      BackupFile(
        created = System.currentTimeMillis(),
        appVersion = BuildConfig.VERSION_NAME,
        repositories = if (BackupSection.REPOSITORIES in sections) exportRepositories() else null,
        favorites =
          if (BackupSection.FAVORITES in sections) favoritesManager.favorites.value.sorted()
          else null,
        settings =
          if (BackupSection.SETTINGS in sections) {
            BackupSettings.fromPreferences(settingsManager.prefsFlow.value.asMap())
          } else null,
        appearance =
          if (BackupSection.APPEARANCE in sections) {
            val json = ThemeSettingsStore(context).exportBackup().toString()
            backupJson.parseToJsonElement(json).jsonObject
          } else null,
      )
    val text = encodeBackup(backup)
    withContext(Dispatchers.IO) {
      // „wt“ kürzt eine vorhandene Datei, sonst bliebe ihr Ende stehen
      val stream =
        context.contentResolver.openOutputStream(uri, "wt")
          ?: throw IOException("Cannot write to $uri")
      stream.use { it.write(text.toByteArray()) }
    }
  }

  private suspend fun exportRepositories(): List<BackupRepository> =
    withContext(Dispatchers.IO) {
      val localeList = LocaleListCompat.getDefault()
      db
        .getRepositoryDao()
        .getRepositories()
        .filter { !it.isArchiveRepo && isWebAddress(it.address) }
        .map { repo ->
          BackupRepository(
            address = repo.address,
            certificate = repo.certificate,
            name = repo.getName(localeList).orEmpty(),
            description = repo.getDescription(localeList).orEmpty(),
            enabled = repo.enabled,
            userMirrors = repo.userMirrors,
            disabledMirrors = repo.disabledMirrors,
          )
        }
    }

  /**
   * Liest die Sicherung in [uri].
   *
   * @throws BackupException wenn sie sich nicht lesen lässt
   */
  suspend fun read(uri: Uri): BackupFile =
    withContext(Dispatchers.IO) {
      val text =
        try {
          context.contentResolver.openInputStream(uri)?.use { it.readLimited() }
            ?: throw IOException("Cannot read $uri")
        } catch (e: IOException) {
          throw BackupException(R.string.myzel_backup_error_read, e)
        }
      decodeBackup(text)
    }

  /**
   * Spielt die gewählten [sections] der Sicherung ein: Paketquellen und Favoriten kommen zu den
   * vorhandenen dazu, Einstellungen und Darstellung ersetzen die jetzigen. Am Ende sagt eine
   * Meldung, wie es ausgegangen ist.
   *
   * @param beforeAppearance läuft auf dem Main-Thread, bevor die Darstellung eingespielt wird: Eine
   *   andere Sprache baut die Activity neu auf, ein noch offener Dialog käme dann wieder.
   * @return ob alles eingespielt wurde
   */
  suspend fun restore(
    backup: BackupFile,
    sections: Set<BackupSection>,
    beforeAppearance: () -> Unit,
  ): Boolean =
    scope
      .async {
        try {
          val skipped = restoreSections(backup, sections, beforeAppearance)
          val message = buildString {
            append(context.getString(R.string.myzel_backup_restored))
            if (skipped > 0) {
              append(' ')
              append(
                context.resources.getQuantityString(
                  R.plurals.myzel_backup_repos_skipped,
                  skipped,
                  skipped,
                )
              )
            }
          }
          toast(message)
          true
        } catch (e: Exception) {
          log.error(e) { "Error restoring backup" }
          toast(context.getString(R.string.myzel_backup_error_restore))
          false
        }
      }
      .await()

  /** @return wie viele Paketquellen übersprungen wurden */
  private suspend fun restoreSections(
    backup: BackupFile,
    sections: Set<BackupSection>,
    beforeAppearance: () -> Unit,
  ): Int {
    var skipped = 0
    val repositories = backup.repositories
    if (BackupSection.REPOSITORIES in sections && repositories != null) {
      skipped = restoreRepositories(repositories)
      RepoUpdateWorker.updateNow(context)
    }
    val favorites = backup.favorites
    if (BackupSection.FAVORITES in sections && favorites != null) {
      favoritesManager.replaceAll(favoritesManager.favorites.value + favorites)
    }
    val settings = backup.settings
    if (BackupSection.SETTINGS in sections && settings != null) restoreSettings(settings)
    // zuletzt: eine andere Sprache baut die Activities neu auf
    val appearance = backup.appearance
    if (BackupSection.APPEARANCE in sections && appearance != null) {
      withContext(Dispatchers.Main) { beforeAppearance() }
      ThemeSettingsStore(context).importBackup(JSONObject(appearance.toString()))
    }
    return skipped
  }

  /** @return wie viele Paketquellen übersprungen wurden */
  private suspend fun restoreRepositories(repositories: List<BackupRepository>): Int {
    val dao = db.getRepositoryDao()
    val existing =
      dao
        .getRepositories()
        .filter { !it.isArchiveRepo }
        .map { ExistingRepo(it.repoId, it.address, it.certificate) }
    val plan = planRepoRestore(repositories, existing)
    val wanted =
      plan.steps.map { step ->
        val entry = step.entry
        val repoId =
          when (step) {
            is RepoStep.Update -> {
              repoManager.setRepositoryEnabled(step.repoId, entry.enabled)
              step.repoId
            }
            is RepoStep.Add ->
              dao.insert(
                InitialRepository(
                  name = entry.name.ifBlank { entry.address },
                  address = entry.address,
                  description = entry.description,
                  certificate = entry.certificate,
                  // wie bei den vorab eingetragenen Quellen (RepoPreLoader)
                  version = 1,
                  enabled = entry.enabled,
                )
              )
          }
        dao.updateUserMirrors(repoId, entry.userMirrors)
        dao.updateDisabledMirrors(repoId, entry.disabledMirrors)
        repoId
      }
    reorder(wanted)
    return plan.skipped.size
  }

  /**
   * Bringt die Paketquellen in die Reihenfolge [wanted]. Das Umsortieren der Datenbank selbst ist
   * intern; RepoManager.reorderRepositories startet es nur, deshalb wartet jeder Schritt, bis die
   * Quelle an ihrem neuen Platz steht.
   */
  private suspend fun reorder(wanted: List<Long>) {
    val dao = db.getRepositoryDao()
    val current = dao.getRepositories().filter { !it.isArchiveRepo }.map { it.repoId }
    for ((moveId, targetId) in reorderSteps(current, wanted)) {
      val move = dao.getRepository(moveId) ?: return
      val target = dao.getRepository(targetId) ?: return
      repoManager.reorderRepositories(move, target)
      val moved =
        withTimeoutOrNull(REORDER_TIMEOUT_MS) {
          while (dao.getRepository(moveId)?.weight != target.weight) delay(REORDER_POLL_MS)
          true
        }
      if (moved == null) {
        log.warn { "Reordering repository $moveId timed out, keeping the rest of the order" }
        return
      }
    }
  }

  /**
   * Schreibt die Einstellungen wie F-Droid selbst über den prefsFlow: compose-preference schreibt
   * bei jeder Änderung die ganze Map zurück und würde Werte überschreiben, die an ihm vorbei in den
   * SharedPreferences landen.
   */
  private suspend fun restoreSettings(settings: BackupSettings) {
    val values = settings.toPreferences()
    withContext(Dispatchers.Main) {
      settingsManager.prefsFlow.update {
        it.toMutablePreferences().apply { values.forEach { (key, value) -> this[key] = value } }
      }
    }
    // Werte, die SettingsManager zusätzlich in eigenen Flows hält
    settings.keepInstallHistory?.let { settingsManager.useInstallHistory = it }
    settings.showSearchKeyboard?.let { settingsManager.setShowSearchKeyboard(it) }
    settings.repoAutoUpdates?.let {
      RepoUpdateWorker.scheduleOrCancel(context, it.toAutoUpdateValue())
    }
    settings.appAutoUpdates?.let {
      AppUpdateWorker.scheduleOrCancel(context, it.toAutoUpdateValue())
    }
  }

  private suspend fun toast(message: String) =
    withContext(Dispatchers.Main) { Toast.makeText(context, message, LENGTH_LONG).show() }

  private companion object {
    const val REORDER_TIMEOUT_MS = 5_000L
    const val REORDER_POLL_MS = 20L
  }
}

/** Liest höchstens [MAX_BACKUP_SIZE] Bytes; größere Dateien sind keine Sicherung von Myzel. */
private fun InputStream.readLimited(): String {
  val out = ByteArrayOutputStream()
  val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
  while (true) {
    val read = read(buffer)
    if (read < 0) break
    out.write(buffer, 0, read)
    if (out.size() > MAX_BACKUP_SIZE)
      throw BackupException(R.string.myzel_backup_error_not_a_backup)
  }
  return out.toString(Charsets.UTF_8.name())
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BackupEntryPoint {
  fun backupManager(): BackupManager
}

/** Der Teil einer Sicherung, den der Dialog zum Einspielen zeigt. */
internal val BackupFile.repositoryCount: Int
  get() = repositories?.size ?: 0

internal val BackupFile.favoriteCount: Int
  get() = favorites?.size ?: 0
