package org.fdroid.index.v2

import java.io.File
import mu.KotlinLogging
import org.fdroid.CompatibilityChecker
import org.fdroid.database.DbV2DiffStreamReceiver
import org.fdroid.database.DbV2StreamReceiver
import org.fdroid.database.FDroidDatabase
import org.fdroid.database.FDroidDatabaseInt
import org.fdroid.database.Repository
import org.fdroid.database.RepositoryDaoInt
import org.fdroid.download.DownloaderFactory
import org.fdroid.fdroid.ProgressListener
import org.fdroid.index.IndexFormatVersion
import org.fdroid.index.IndexFormatVersion.ONE
import org.fdroid.index.IndexFormatVersion.TWO
import org.fdroid.index.IndexParser
import org.fdroid.index.IndexUpdateListener
import org.fdroid.index.IndexUpdateResult
import org.fdroid.index.IndexUpdater
import org.fdroid.index.RepoUriBuilder
import org.fdroid.index.TempFileProvider
import org.fdroid.index.defaultRepoUriBuilder
import org.fdroid.index.parseEntry
import org.fdroid.index.setIndexUpdateListener

public const val SIGNED_FILE_NAME: String = "entry.jar"

public class IndexV2Updater(
  database: FDroidDatabase,
  private val tempFileProvider: TempFileProvider,
  private val downloaderFactory: DownloaderFactory,
  private val repoUriBuilder: RepoUriBuilder = defaultRepoUriBuilder,
  private val compatibilityChecker: CompatibilityChecker,
  private val listener: IndexUpdateListener? = null,
) : IndexUpdater() {

  private val log = KotlinLogging.logger {}
  public override val formatVersion: IndexFormatVersion = TWO
  private val db: FDroidDatabaseInt = database as FDroidDatabaseInt
  override val repoDao: RepositoryDaoInt = db.getRepositoryDao()

  override fun updateRepo(repo: Repository): IndexUpdateResult {
    val (_, entry) = getCertAndEntry(repo, repo.certificate)
    // don't process repos that we already did process in the past
    if (entry.timestamp <= repo.timestamp) return IndexUpdateResult.Unchanged
    // get diff, if available
    val diff = entry.getDiff(repo.timestamp)
    return if (diff == null || repo.formatVersion == ONE) {
      // no diff found (or this is upgrade from v1 repo), so do full index update
      val streamReceiver = DbV2StreamReceiver(db, repo.repoId, compatibilityChecker)
      val streamProcessor = IndexV2FullStreamProcessor(streamReceiver)
      processStream(repo, entry.index, entry.version, streamProcessor)
    } else {
      // use available diff
      val streamReceiver = DbV2DiffStreamReceiver(db, repo.repoId, compatibilityChecker)
      val streamProcessor = IndexV2DiffStreamProcessor(streamReceiver)
      processStream(repo, diff, entry.version, streamProcessor)
    }
  }

  /**
   * Myzel: downloads the file [update] would process for [repo] in advance and puts it where
   * [update] looks for it, so [update] only checks its hash. This lets the indexes of several repos
   * download while another one is written to the database. The download goes to a temporary name
   * first, so [update] never finds an incomplete file. Errors are only logged: [update] then
   * downloads again and reports them. Returns the file, or null if there is nothing to process.
   * [onProgress] gets the progress of that download, not of entry.jar before it.
   */
  public fun prefetch(repo: Repository, onProgress: ProgressListener? = null): File? {
    val part = tempFileProvider.createTempFile(null)
    return try {
      val (_, entry) = getCertAndEntry(repo, repo.certificate, reportProgress = false)
      if (entry.timestamp <= repo.timestamp) return null
      val diff = entry.getDiff(repo.timestamp)
      val entryFile = if (diff == null || repo.formatVersion == ONE) entry.index else diff
      downloaderFactory
        .createWithTryFirstMirror(
          repo = repo,
          uri = repoUriBuilder.getUri(repo, entryFile.name.trimStart('/')),
          indexFile = entryFile,
          destFile = part,
        )
        .apply { if (onProgress != null) setListener(onProgress) }
        .download()
      val file = tempFileProvider.createTempFile(entryFile.sha256)
      if (part.renameTo(file)) file else null
    } catch (e: Exception) {
      log.warn(e) { "Could not prefetch index of ${repo.address}" }
      null
    } finally {
      // after the rename there is nothing left under the temporary name
      part.delete()
    }
  }

  private fun getCertAndEntry(
    repo: Repository,
    certificate: String,
    reportProgress: Boolean = true,
  ): Pair<String, Entry> {
    val file = tempFileProvider.createTempFile(null)
    val downloader =
      downloaderFactory
        .createWithTryFirstMirror(
          repo = repo,
          uri = repoUriBuilder.getUri(repo, SIGNED_FILE_NAME),
          indexFile = FileV2.fromPath("/$SIGNED_FILE_NAME"),
          destFile = file,
        )
        .apply {
          if (listener != null && reportProgress)
            setListener { bytesRead, _ ->
              // don't report a total for entry.jar,
              // because we'll download another file afterwards
              // and progress reporting would jump to 100% two times.
              listener.onDownloadProgress(repo, bytesRead, -1)
            }
        }
    try {
      downloader.download()
      val verifier = EntryVerifier(file, certificate, null)
      return verifier.getStreamAndVerify { inputStream -> IndexParser.parseEntry(inputStream) }
    } finally {
      file.delete()
    }
  }

  private fun processStream(
    repo: Repository,
    entryFile: EntryFileV2,
    repoVersion: Long,
    streamProcessor: IndexV2StreamProcessor,
  ): IndexUpdateResult {
    val file = tempFileProvider.createTempFile(entryFile.sha256)
    val downloader =
      downloaderFactory
        .createWithTryFirstMirror(
          repo = repo,
          uri = repoUriBuilder.getUri(repo, entryFile.name.trimStart('/')),
          indexFile = entryFile,
          destFile = file,
        )
        .apply { setIndexUpdateListener(listener, repo) }
    try {
      downloader.download()
      file.inputStream().use { inputStream ->
        db.runInTransaction {
          // ensure somebody else hasn't updated the repo in the meantime
          val currentTimestamp = repoDao.getRepository(repo.repoId)?.timestamp
          if (currentTimestamp != repo.timestamp)
            throw ConcurrentModificationException(
              "Repo timestamp expected ${repo.timestamp}, but was $currentTimestamp"
            )
          // still the expected timestamp, so go on processing...
          streamProcessor.process(repoVersion, inputStream) { i ->
            listener?.onUpdateProgress(repo, i, entryFile.numPackages)
          }
          // update RepositoryPreferences with timestamp
          val repoPrefs =
            repoDao.getRepositoryPreferences(repo.repoId)
              ?: error("No repo prefs for ${repo.repoId}")
          val updatedPrefs =
            repoPrefs.copy(
              lastUpdated = System.currentTimeMillis(),
              errorCount = 0,
              lastError = null,
            )
          repoDao.updateRepositoryPreferences(updatedPrefs)
        }
      }
    } finally {
      file.delete()
    }
    return IndexUpdateResult.Processed
  }
}
