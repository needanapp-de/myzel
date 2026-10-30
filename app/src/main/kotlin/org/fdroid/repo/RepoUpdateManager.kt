package org.fdroid.repo

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.annotation.WorkerThread
import androidx.core.os.LocaleListCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import de.needanapp.myzel.repo.RepoUpdateRun
import de.needanapp.myzel.repo.inUpdateOrder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import mu.KotlinLogging
import org.fdroid.CompatibilityChecker
import org.fdroid.CompatibilityCheckerImpl
import org.fdroid.NotificationManager
import org.fdroid.R
import org.fdroid.database.FDroidDatabase
import org.fdroid.database.Repository
import org.fdroid.download.DownloaderFactory
import org.fdroid.index.IndexUpdateResult
import org.fdroid.index.RepoManager
import org.fdroid.index.RepoUpdater
import org.fdroid.settings.SettingsConstants
import org.fdroid.settings.SettingsManager
import org.fdroid.updates.UpdatesManager

private const val MIN_UPDATE_INTERVAL_MILLIS = 15_000
private const val MAX_UPDATE_INTERVAL_MILLIS = 12 * 60 * 60 * 1000L // 12 hours

// Myzel: vorgeladene Indexe (SHA-256 als Name) und ihre Zwischendateien (dl-…)
private const val ABANDONED_PREFETCH_MILLIS = 30 * 60 * 1000L
private val PREFETCH_FILE = Regex("[0-9a-f]{64}|dl-[0-9]+")

@Singleton
class RepoUpdateManager
@VisibleForTesting
internal constructor(
  private val context: Context,
  private val db: FDroidDatabase,
  private val repoManager: RepoManager,
  private val updatesManager: UpdatesManager,
  private val settingsManager: SettingsManager,
  private val downloaderFactory: DownloaderFactory,
  private val notificationManager: NotificationManager,
  // Defaults keep the testing constructor lightweight while still mirroring production wiring.
  private val compatibilityChecker: CompatibilityChecker =
    CompatibilityCheckerImpl(packageManager = context.packageManager, forceTouchApps = false),
  private val repoUpdateListener: RepoUpdateListener =
    RepoUpdateListener(context, notificationManager),
  private val repoUpdater: RepoUpdater =
    RepoUpdater(
      tempDir = context.cacheDir, // FIXME this causes disk I/O
      db = db,
      downloaderFactory = downloaderFactory,
      compatibilityChecker = compatibilityChecker,
      listener = repoUpdateListener,
    ),
) {

  @Inject
  constructor(
    @ApplicationContext context: Context,
    db: FDroidDatabase,
    repositoryManager: RepoManager,
    updatesManager: UpdatesManager,
    settingsManager: SettingsManager,
    downloaderFactory: DownloaderFactory,
    notificationManager: NotificationManager,
  ) : this(
    context = context,
    db = db,
    repoManager = repositoryManager,
    updatesManager = updatesManager,
    settingsManager = settingsManager,
    downloaderFactory = downloaderFactory,
    notificationManager = notificationManager,
  )

  private val log = KotlinLogging.logger {}

  // Myzel: Vorladen der Indexe, höchstens drei gleichzeitig; läuft nach einem Abbruch zu Ende
  private val prefetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val prefetchPermits = Semaphore(3)
  private val _isUpdating = MutableStateFlow(false)
  val isUpdating = _isUpdating.asStateFlow()
  val repoUpdateState = repoUpdateListener.updateState

  // Myzel: welche Paketquelle gerade dran ist, als wievielte von wie vielen (Anzeige in Entdecken)
  private val _updateRun = MutableStateFlow<RepoUpdateRun?>(null)
  val updateRun = _updateRun.asStateFlow()

  /**
   * The time in milliseconds of the (earliest!) next automatic repo update check. This is
   * [Long.MAX_VALUE], if no time is known.
   */
  val nextUpdateFlow =
    RepoUpdateWorker.getAutoUpdateWorkInfo(context).map { workInfo ->
      workInfo?.nextScheduleTimeMillis ?: Long.MAX_VALUE
    }

  init {
    log.info { "RepoUpdateManager initialized" }
    if (settingsManager.repoUpdates == SettingsConstants.AutoUpdateValues.OnlyWhenOpenApp) {
      val now = System.currentTimeMillis()
      if (now - settingsManager.lastRepoUpdate > MAX_UPDATE_INTERVAL_MILLIS) {
        log.info { "Last repo update was more than 12h ago, triggering update..." }
        RepoUpdateWorker.updateNow(context)
      }
    }
  }

  /**
   * Updates all enabled repositories.
   *
   * The call is skipped when an update is already running or when the last update happened very
   * recently. When at least one repository is processed, app updates are loaded and notifications
   * are refreshed.
   */
  @WorkerThread
  suspend fun updateRepos() {
    currentCoroutineContext().ensureActive()
    if (shouldSkipUpdate()) return

    _isUpdating.value = true
    try {
      currentCoroutineContext().ensureActive()
      var anyReposProcessed = false
      // Always get repos fresh from DB, because:
      // * when an update is requested early at app start, the repos might not be available yet
      // * when an update is requested when adding a new repo, it might not be in the list yet
      // Myzel: kleine Paketquellen zuerst, damit Entdecken früher Apps zeigt
      val repositories =
        inUpdateOrder(db.getRepositoryDao().getRepositories()).filter {
          it.enabled // don't update disabled repos
        }
      // Myzel: die Indexe laden parallel vor, geschrieben wird weiter nacheinander
      deleteAbandonedPrefetches()
      val prefetched = repositories.associate { repository ->
        repository.repoId to
          prefetchScope.async { prefetchPermits.withPermit { repoUpdater.prefetch(repository) } }
      }
      repositories.forEachIndexed { index, repository ->
        currentCoroutineContext().ensureActive()
        _updateRun.value = RepoUpdateRun(repository.repoId, index, repositories.size) // Myzel
        // Vorladen und Update derselben Paketquelle dürfen nicht gleichzeitig in die Datei
        // schreiben
        val prefetchedFile = prefetched.getValue(repository.repoId).await()
        val result = updateRepositoryInternal(repository)
        if (result is IndexUpdateResult.Processed) {
          anyReposProcessed = true
        }
        // Myzel: vom Update nicht verbraucht, etwa nach einem Fehler
        prefetchedFile?.delete()
      }

      db.getRepositoryDao().walCheckpoint()
      updateLastCheckTimestamp(anyReposProcessed)

      if (anyReposProcessed) {
        checkAndNotifyAppUpdates()
      }
    } finally {
      notificationManager.cancelUpdateRepoNotification()
      _updateRun.value = null // Myzel
      _isUpdating.value = false
    }
  }

  /**
   * Updates a single repository by id.
   *
   * Returns [IndexUpdateResult.NotFound] when no repository exists for [repoId].
   */
  @WorkerThread
  fun updateRepo(repoId: Long): IndexUpdateResult {
    if (isUpdating.value) log.warn { "Already updating repositories: updateRepo($repoId)" }

    val repository = repoManager.getRepository(repoId) ?: return IndexUpdateResult.NotFound
    _isUpdating.value = true
    _updateRun.value = RepoUpdateRun(repoId, 0, 1) // Myzel
    return try {
      val result = updateRepositoryInternal(repository)
      if (result is IndexUpdateResult.Processed) {
        updatesManager.loadUpdates()
      }
      result
    } finally {
      notificationManager.cancelUpdateRepoNotification()
      _updateRun.value = null // Myzel
      _isUpdating.value = false
      db.getRepositoryDao().walCheckpoint()
    }
  }

  /**
   * Myzel: Vorgeladene Indexe eines abgebrochenen Updates bleiben im Cache liegen. Was dort seit
   * einer halben Stunde nicht mehr geschrieben wurde, braucht kein Download und kein Update mehr.
   */
  private fun deleteAbandonedPrefetches() {
    val oldest = System.currentTimeMillis() - ABANDONED_PREFETCH_MILLIS
    context.cacheDir
      .listFiles { file ->
        file.isFile && file.lastModified() < oldest && PREFETCH_FILE.matches(file.name)
      }
      ?.forEach { it.delete() }
  }

  private fun shouldSkipUpdate(): Boolean {
    if (isUpdating.value) {
      // This is a workaround for what looks like a WorkManager bug.
      // Sometimes it goes through scheduling/cancellation loops
      // and then ends up running the same worker more than once.
      log.warn { "Already updating repositories, skipping duplicate call" }
      return true
    }
    val timeSinceLastCheck = System.currentTimeMillis() - settingsManager.lastRepoUpdate
    if (timeSinceLastCheck in 1..<MIN_UPDATE_INTERVAL_MILLIS) {
      // This is a workaround for a similar issue as above.
      // We've seen WorkManager tell our worker to run in what looks like an endless loop.
      log.info { "Not updating, only $timeSinceLastCheck ms since last check." }
      return true
    }
    return false
  }

  private fun updateRepositoryInternal(repository: Repository): IndexUpdateResult {
    repoUpdateListener.onUpdateStarted(repository.repoId)
    showRepoUpdateNotification(repository)

    val result = repoUpdater.update(repository)
    log.info { "Update repo result: $result" }

    repoUpdateListener.onUpdateFinished(repository.repoId, result)

    if (result is IndexUpdateResult.Error) {
      log.error(result.e) { "Error updating repository ${repository.address}" }
    }
    return result
  }

  private fun showRepoUpdateNotification(repository: Repository) {
    val repositoryName = repository.getName(LocaleListCompat.getDefault())
    val notificationMessage =
      context.getString(R.string.notification_repo_update_default, repositoryName)
    notificationManager.showUpdateRepoNotification(notificationMessage, throttle = false)
  }

  private fun updateLastCheckTimestamp(anyReposProcessed: Boolean) {
    // Don't update time on first start when repos failed to update
    if (!settingsManager.isFirstStart || anyReposProcessed) {
      settingsManager.lastRepoUpdate = System.currentTimeMillis()
    }
  }

  private suspend fun checkAndNotifyAppUpdates() {
    updatesManager.loadUpdates().join() // wait here until we have finished loading updates
    val numUpdates = updatesManager.numUpdates.value
    if (numUpdates > 0) {
      val states = updatesManager.notificationStates
      notificationManager.showAppUpdatesAvailableNotification(states)
    }
  }
}
