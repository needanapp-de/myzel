package de.needanapp.myzel.repo

import android.content.Context
import androidx.annotation.WorkerThread
import androidx.core.content.edit
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.decodeFromStream
import org.fdroid.database.FDroidDatabase
import org.fdroid.database.InitialRepository
import org.fdroid.database.Repository
import org.fdroid.index.RepoManager
import org.fdroid.repo.DefaultRepository
import org.fdroid.utils.IoDispatcher

/**
 * Zusätzliche Repos von Myzel: an erster Stelle das Repo von needanapp.de ([NEEDANAPP_REPO]),
 * ausgeschaltet dessen Testing-Repo ([NEEDANAPP_TESTING_REPO]), danach IzzyOnDroid, eingeschaltet
 * wie F-Droid selbst. Es folgt ausgeschaltet die übrige Standardliste von Droid-ify (v0.7.8).
 * Anders als `default_repos.json` zählen sie nicht als Standard-Repos von F-Droid, die App-Details
 * zeigen bei ihren Apps also die Herkunft an.
 *
 * Die Datei liegt getrennt von `default_repos.json`, damit Updates von F-Droid ohne Konflikte
 * übernommen werden können.
 */
internal const val EXTRA_REPOS_ASSET = "needanapp_repos.json"

/**
 * Repos, die Myzel früher mitgebracht hat und nicht mehr anbietet: fdroid.pan1k.de mit den
 * Testversionen von Myzel (bis 0.19.0, ausgeschaltet).
 */
internal val REMOVED_REPOS = setOf("https://fdroid.pan1k.de/repo")

/** Das Repo von needanapp.de mit den stabilen Versionen, seit 2026-10-30. */
internal const val NEEDANAPP_REPO = "https://apps.needanapp.de/repo"

/**
 * Bis 2026-10-30 das Repo von needanapp.de, seitdem das Testing-Repo: Testversionen, nur mit
 * Benutzername und Passwort.
 */
internal const val NEEDANAPP_TESTING_REPO = "https://fdroid.needanapp.de/repo"

@WorkerThread
@OptIn(ExperimentalSerializationApi::class)
fun loadExtraRepos(context: Context): List<DefaultRepository> =
  context.assets.open(EXTRA_REPOS_ASSET).use { inputStream ->
    Json.decodeFromStream<List<DefaultRepository>>(inputStream)
  }

/** Die Repos aus [repos], die [RemovedRepoCleanup] löscht: früher mitgebracht und noch aus. */
internal fun removableRepos(repos: List<Repository>): List<Repository> = repos.filter {
  it.address in REMOVED_REPOS && !it.enabled
}

/**
 * Löscht beim ersten Start nach dem Update einmal die [REMOVED_REPOS], solange sie ausgeschaltet
 * sind. Wer eins davon selbst eingeschaltet hat, behält es; auch später fragt Myzel nicht mehr
 * nach.
 */
@Singleton
class RemovedRepoCleanup
@Inject
constructor(
  @param:ApplicationContext private val context: Context,
  private val repoManager: RepoManager,
  @param:IoDispatcher private val scope: CoroutineScope,
) {
  fun run() {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    if (prefs.getBoolean(KEY_DONE, false)) return
    scope.launch {
      removableRepos(repoManager.getRepositories()).forEach {
        repoManager.deleteRepository(it.repoId)
      }
      prefs.edit { putBoolean(KEY_DONE, true) }
    }
  }

  private companion object {
    const val PREFS = "myzel_repos"
    const val KEY_DONE = "removed_repos_1"
  }
}

/**
 * Was [NeedanappRepoMove] an den Repos ändert: [add] fehlt noch und kommt dazu, [disable] ist das
 * eingeschaltete Testing-Repo ohne Anmeldung, das nur noch Fehler brächte.
 */
internal data class NeedanappRepoChanges(val add: Boolean, val disable: Repository?)

internal fun needanappRepoChanges(repos: List<Repository>) =
  NeedanappRepoChanges(
    add = repos.none { it.address == NEEDANAPP_REPO },
    disable =
      repos.find {
        it.address == NEEDANAPP_TESTING_REPO && it.enabled && it.username.isNullOrEmpty()
      },
  )

/**
 * Der Umzug von needanapp.de auf [NEEDANAPP_REPO], einmal beim ersten Start nach dem Update: Fehlt
 * das neue Repo, kommt es eingeschaltet dazu (Neuinstallationen haben es aus [EXTRA_REPOS_ASSET]).
 * Das alte, jetzt das Testing-Repo, wird ausgeschaltet, wenn dort keine Anmeldung gespeichert ist;
 * es verlangt seitdem eine und brächte sonst nur „Anmeldung nötig“. Wer sich dort angemeldet hat,
 * behält es eingeschaltet.
 */
@Singleton
class NeedanappRepoMove
@Inject
constructor(
  @param:ApplicationContext private val context: Context,
  private val db: FDroidDatabase,
  private val repoManager: RepoManager,
  @param:IoDispatcher private val scope: CoroutineScope,
) {
  fun run() {
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    if (prefs.getBoolean(KEY_DONE, false)) return
    scope.launch {
      val changes = needanappRepoChanges(repoManager.getRepositories())
      if (changes.add) {
        loadExtraRepos(context)
          .find { it.address == NEEDANAPP_REPO }
          ?.let { repo ->
            db
              .getRepositoryDao()
              .insert(
                InitialRepository(
                  name = repo.name,
                  address = repo.address,
                  mirrors = repo.mirrors,
                  description = repo.description,
                  certificate = repo.certificate,
                  version = 1,
                  enabled = true,
                )
              )
          }
      }
      changes.disable?.let { repoManager.setRepositoryEnabled(it.repoId, false) }
      prefs.edit { putBoolean(KEY_DONE, true) }
    }
  }

  private companion object {
    const val PREFS = "myzel_repos"
    const val KEY_DONE = "needanapp_apps_1"
  }
}
