package de.needanapp.myzel.watch

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.pm.PackageInfoCompat
import androidx.lifecycle.asFlow
import dagger.hilt.android.qualifiers.ApplicationContext
import de.needanapp.myzel.link.sha256Of
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.fdroid.database.AppVersion
import org.fdroid.database.FDroidDatabase
import org.fdroid.download.DownloaderFactory
import org.fdroid.download.getUri
import org.fdroid.index.RepoManager
import org.fdroid.index.v2.PackageVersion
import org.fdroid.utils.sha256

/** Name der Beilage in den Assets von Debug-Builds (app/myzel.gradle). */
private const val BUNDLED_WEAR = "myzel-wear.apk"

/** Eine Myzel Wear für die Uhr, geladen und geprüft. */
class WearApk(
  val file: File,
  val packageName: String,
  val versionCode: Long,
  val sha256: String,
  /** aus der Beilage, nicht aus einer Paketquelle; dann zählt der SHA-256 statt der Version */
  val bundled: Boolean,
)

/** Warum Myzel keine Myzel Wear für die Uhr hat. */
class WearApkException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Woher Myzel Wear für die Uhr kommt. Myzel Wear ist eine eigene APK mit dem Paketnamen von Myzel
 * und dem Merkmal `android.hardware.type.watch`, also die Uhr-Version von Myzel in der Paketquelle
 * (Fall A im Umbauplan). Myzel nimmt die neueste, die wie es selbst signiert ist und auf die Uhr
 * passt; so passt sie auch dann, wenn später F-Droid beide baut und signiert. Debug-Builds legen
 * ihre eigene Myzel Wear als Asset bei ([bundled]); die hat dann Vorrang.
 */
@Singleton
class MyzelWearSource
@Inject
constructor(
  @ApplicationContext private val context: Context,
  private val db: FDroidDatabase,
  private val repoManager: RepoManager,
  private val downloaderFactory: DownloaderFactory,
) {
  private val log = KotlinLogging.logger {}
  private val dir
    get() = File(context.cacheDir, "watch").apply { mkdirs() }

  private var bundledLoaded = false
  private var bundledCache: WearApk? = null

  /** SHA-256 der Signaturzertifikate von Myzel selbst. */
  private val ownSigners: Set<String> by lazy {
    val info =
      context.packageManager.getPackageInfo(
        context.packageName,
        PackageManager.GET_SIGNING_CERTIFICATES,
      )
    info.signingInfo?.apkContentsSigners.orEmpty().mapTo(HashSet()) { sha256(it.toByteArray()) }
  }

  /** Die Myzel Wear aus der Beilage, einmal je Prozess in den Cache kopiert; null ohne. */
  suspend fun bundled(): WearApk? =
    withContext(Dispatchers.IO) {
      if (!bundledLoaded) {
        bundledLoaded = true
        bundledCache =
          try {
            val file = File(dir, BUNDLED_WEAR)
            context.assets.open(BUNDLED_WEAR).use { input ->
              file.outputStream().use { input.copyTo(it) }
            }
            val info = context.packageManager.getPackageArchiveInfo(file.path, 0)
            info?.let {
              WearApk(
                file,
                it.packageName,
                PackageInfoCompat.getLongVersionCode(it),
                file.inputStream().use { input -> sha256Of(input) },
                bundled = true,
              )
            }
          } catch (_: FileNotFoundException) {
            null
          } catch (e: IOException) {
            log.warn(e) { "Beilage Myzel Wear" }
            null
          }
      }
      bundledCache
    }

  /**
   * Die neueste Myzel Wear aus den eingeschalteten Paketquellen für eine Uhr mit API-Stufe [sdk]
   * (0: unbekannt); null, wenn keine passt.
   */
  suspend fun latest(sdk: Int): AppVersion? =
    withContext(Dispatchers.IO) {
      val versions = db.getVersionDao().getAppVersions(context.packageName).asFlow().first()
      versions.newestWear(ownSigners, sdk)
    }

  /**
   * Lädt [version] in den Cache (der Downloader prüft den SHA-256 aus dem Index) und prüft
   * Paketname und Signatur. [onProgress] meldet den Anteil.
   */
  suspend fun download(version: AppVersion, onProgress: (Float) -> Unit = {}): WearApk =
    withContext(Dispatchers.IO) {
      val repo =
        repoManager.getRepository(version.repoId)
          ?: throw WearApkException("Paketquelle ${version.repoId} fehlt")
      val file = File(dir, "${version.file.sha256}.apk")
      try {
        val downloader =
          downloaderFactory.create(repo, getUri(repo.address, version.file), version.file, file)
        downloader.setListener { read, total -> if (total > 0) onProgress(read.toFloat() / total) }
        downloader.download()
      } catch (e: CancellationException) {
        file.delete()
        throw e
      } catch (e: Exception) {
        // auch NotFoundException, die keine IOException ist
        file.delete()
        throw WearApkException("Download: ${e.message}", e)
      }
      val info =
        context.packageManager.getPackageArchiveInfo(
          file.path,
          PackageManager.GET_SIGNING_CERTIFICATES,
        )
      val signers = info?.signingInfo?.apkContentsSigners.orEmpty().map { sha256(it.toByteArray()) }
      if (info?.packageName != context.packageName || signers.none { it in ownSigners }) {
        file.delete()
        throw WearApkException("Falsches Paket oder andere Signatur: ${info?.packageName}")
      }
      // selbst gerechnet wie auf der Uhr, die ihn nach dem Update meldet
      WearApk(
        file,
        info.packageName,
        PackageInfoCompat.getLongVersionCode(info),
        file.inputStream().use { sha256Of(it) },
        bundled = false,
      )
    }

  /**
   * Die Myzel Wear für die Einrichtung einer Uhr mit API-Stufe [sdk]: die Beilage oder die neueste
   * aus den Paketquellen, geladen. Wirft [WearApkException], wenn es keine gibt.
   */
  suspend fun forSetup(sdk: Int, onProgress: (Float) -> Unit = {}): WearApk =
    bundled()
      ?: download(
        latest(sdk) ?: throw WearApkException("Keine Myzel Wear in den Paketquellen"),
        onProgress,
      )

  /** Räumt eine geladene APK weg; die Beilage bleibt. */
  fun discard(apk: WearApk) {
    if (!apk.bundled) apk.file.delete()
  }
}

/**
 * Die neueste Uhr-Version aus dieser Liste, die mit einem von [signers] signiert ist und auf eine
 * Uhr mit API-Stufe [sdk] passt; null, wenn keine.
 */
internal fun <T : PackageVersion> List<T>.newestWear(signers: Set<String>, sdk: Int): T? =
  filter { version ->
    version.isForWatch &&
      version.fitsWatch(sdk) &&
      version.signer?.sha256.orEmpty().any { it in signers }
  }
  .maxByOrNull { it.versionCode }
