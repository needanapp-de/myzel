package org.fdroid

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Composer
import androidx.compose.runtime.ExperimentalComposeRuntimeApi
import androidx.compose.runtime.tooling.ComposeStackTraceMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.key.Keyer
import coil3.memory.MemoryCache
import coil3.request.NullRequestDataException
import coil3.request.crossfade
import coil3.util.DebugLogger
import coil3.util.Logger
import dagger.hilt.android.HiltAndroidApp
import de.needanapp.myzel.ui.prepareMyzelAppearance
import javax.inject.Inject
import org.fdroid.download.DownloadRequest
import org.fdroid.download.LocalIconFetcher
import org.fdroid.download.PackageName
import org.fdroid.download.coil.DownloadRequestFetcher
import org.fdroid.repo.RepoUpdateWorker
import org.fdroid.settings.SettingsManager
import org.fdroid.ui.utils.applyNewTheme
import org.fdroid.updates.AppUpdateWorker
import org.fdroid.updates.SelfUpdateReceiver

@HiltAndroidApp
class App : Application(), Configuration.Provider, SingletonImageLoader.Factory {

  // careful with injecting too many dependencies here, especially DO NO inject anything that needs
  // the database, because this class gets instantiated in other contexts and that may lead to a
  // locked database crash

  @Inject lateinit var settingsManager: SettingsManager

  @Inject lateinit var workerFactory: HiltWorkerFactory

  @Inject lateinit var downloadRequestFetcherFactory: DownloadRequestFetcher.Factory

  override val workManagerConfiguration: Configuration
    get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

  @OptIn(ExperimentalComposeRuntimeApi::class)
  override fun onCreate() {
    super.onCreate()
    if (BuildConfig.DEBUG) {
      Composer.setDiagnosticStackTraceMode(ComposeStackTraceMode.SourceInformation)
    }
    applyNewTheme(settingsManager.theme)
    // Myzel: Myzel's defaults, read the appearance early, so the first screen doesn't wait for it
    prepareMyzelAppearance(this)

    // don't show self-update notification unless we enable it first
    SelfUpdateReceiver.disable(this)

    RepoUpdateWorker.scheduleOrCancel(applicationContext, settingsManager.repoUpdates)
    AppUpdateWorker.scheduleOrCancel(applicationContext, settingsManager.autoUpdateApps)
  }

  override fun newImageLoader(context: Context): ImageLoader {
    return ImageLoader.Builder(context)
      .crossfade(true)
      .components {
        val downloadRequestKeyer = Keyer<DownloadRequest> { data, _ -> data.getCacheKey() }
        add(downloadRequestKeyer)
        add(downloadRequestFetcherFactory)

        val packageNameKeyer = Keyer<PackageName> { data, _ -> data.packageName }
        add(packageNameKeyer)
        add(LocalIconFetcher.Factory(context, downloadRequestFetcherFactory))
      }
      .memoryCache { MemoryCache.Builder().maxSizePercent(context, 0.25).build() }
      .diskCache {
        DiskCache.Builder().directory(context.cacheDir.resolve("coil")).maxSizePercent(0.05).build()
      }
      .logger(if (BuildConfig.DEBUG) CoilLogger() else null)
      .build()
  }
}

fun DownloadRequest.getCacheKey() = indexFile.sha256 ?: (mirrors[0].baseUrl + indexFile.name)

/** The entire purpose of this class is to not spam the log with huge [NullRequestDataException]. */
private class CoilLogger : Logger {
  private val logger = DebugLogger()
  override var minLevel: Logger.Level = logger.minLevel

  override fun log(tag: String, level: Logger.Level, message: String?, throwable: Throwable?) {
    if (throwable is NullRequestDataException) logger.log(tag, Logger.Level.Info, message, null)
    else logger.log(tag, level, message, throwable)
  }
}
