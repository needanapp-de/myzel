package de.needanapp.myzel.install

import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import androidx.annotation.StringRes
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import org.fdroid.R
import rikka.shizuku.Shizuku

/** Ob eine Installationsmethode gerade nutzbar ist, sonst warum nicht. */
sealed interface InstallAccess {
  data object Granted : InstallAccess

  data class Denied(@StringRes val reason: Int) : InstallAccess
}

/** Ergebnis einer Installation über Root oder Shizuku. */
internal sealed interface PrivilegedResult {
  data object Success : PrivilegedResult

  data class Failure(val message: String) : PrivilegedResult

  /** Kein Zugriff (mehr): Myzel installiert dann mit dem Paketinstaller. */
  data object Unavailable : PrivilegedResult
}

/** Installiert APKs ohne Nachfrage über Root oder Shizuku. */
@Singleton
class PrivilegedInstaller
@Inject
constructor(@param:ApplicationContext private val context: Context) {

  private val log = KotlinLogging.logger {}

  /** pm verträgt mehrere Installationen gleichzeitig schlecht, F-Droid startet aber mehrere. */
  private val mutex = Mutex()

  /**
   * Prüft den Zugriff und fragt ihn bei Bedarf an: Root zeigt dabei die Abfrage von Magisk oder
   * KernelSU, Shizuku seine eigene.
   */
  suspend fun requestAccess(method: InstallMethod): InstallAccess =
    when (method) {
      InstallMethod.SESSION -> InstallAccess.Granted
      InstallMethod.ROOT ->
        if (hasRoot(ROOT_PROMPT_TIMEOUT_MS)) InstallAccess.Granted
        else InstallAccess.Denied(R.string.myzel_install_root_denied)
      InstallMethod.SHIZUKU -> requestShizuku()
    }

  internal suspend fun install(method: InstallMethod, apkFile: File): PrivilegedResult {
    val shell =
      when (method) {
        InstallMethod.SESSION -> return PrivilegedResult.Unavailable
        InstallMethod.ROOT -> RootShell
        InstallMethod.SHIZUKU -> if (hasShizuku()) ShizukuShell else return unavailable(method)
      }
    return mutex.withLock {
      val command = pmInstallCommand(context.packageName, apkFile.length())
      log.info { "Installing ${apkFile.name} with $method" }
      try {
        val output = shell.execute(command, apkFile, INSTALL_TIMEOUT_MS)
        when (val result = parsePmInstall(output)) {
          PmResult.Success -> PrivilegedResult.Success
          is PmResult.Failure -> {
            log.warn { "pm install with $method failed: ${output.output}" }
            // su verweigert (etwa nach Entzug des Zugriffs): wieder über den Paketinstaller
            if (method == InstallMethod.ROOT && !hasRoot(ROOT_CHECK_TIMEOUT_MS)) {
              unavailable(method)
            } else {
              PrivilegedResult.Failure(result.message)
            }
          }
        }
      } catch (e: TimeoutCancellationException) {
        // kein Abbruch durch den Nutzer: als Fehler melden, nicht als Abbruch
        log.warn(e) { "pm install with $method timed out" }
        PrivilegedResult.Failure("pm install: timeout")
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        // su fehlt oder Shizuku.newProcess ist nicht erreichbar
        log.warn(e) { "Cannot run pm install with $method" }
        PrivilegedResult.Unavailable
      }
    }
  }

  private fun unavailable(method: InstallMethod): PrivilegedResult {
    log.warn { "$method is not available, falling back to the package installer" }
    return PrivilegedResult.Unavailable
  }

  private suspend fun hasRoot(timeoutMs: Long): Boolean =
    try {
      isRootId(RootShell.execute("id", timeoutMs = timeoutMs))
    } catch (_: TimeoutCancellationException) {
      false
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      log.info { "No root: ${e.message}" }
      false
    }

  private fun hasShizuku(): Boolean =
    try {
      Shizuku.pingBinder() &&
        !Shizuku.isPreV11() &&
        Shizuku.checkSelfPermission() == PERMISSION_GRANTED
    } catch (e: Exception) {
      log.info { "No Shizuku: ${e.message}" }
      false
    }

  private suspend fun requestShizuku(): InstallAccess =
    withContext(Dispatchers.Main) {
      when {
        !runCatching { Shizuku.pingBinder() }.getOrDefault(false) ->
          InstallAccess.Denied(R.string.myzel_install_shizuku_not_running)
        Shizuku.isPreV11() -> InstallAccess.Denied(R.string.myzel_install_shizuku_too_old)
        Shizuku.checkSelfPermission() == PERMISSION_GRANTED -> InstallAccess.Granted
        Shizuku.shouldShowRequestPermissionRationale() ->
          // „nicht mehr fragen“: nur in Shizuku selbst wieder freizugeben
          InstallAccess.Denied(R.string.myzel_install_shizuku_denied)
        awaitShizukuPermission() -> InstallAccess.Granted
        else -> InstallAccess.Denied(R.string.myzel_install_shizuku_denied)
      }
    }

  private suspend fun awaitShizukuPermission(): Boolean = suspendCancellableCoroutine { cont ->
    val listener =
      object : Shizuku.OnRequestPermissionResultListener {
        override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
          if (requestCode != SHIZUKU_REQUEST_CODE) return
          Shizuku.removeRequestPermissionResultListener(this)
          if (cont.isActive) cont.resume(grantResult == PERMISSION_GRANTED)
        }
      }
    Shizuku.addRequestPermissionResultListener(listener)
    cont.invokeOnCancellation { Shizuku.removeRequestPermissionResultListener(listener) }
    Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
  }

  private companion object {
    const val SHIZUKU_REQUEST_CODE = 4711
    /** Die Abfrage von Magisk wartet auf den Nutzer. */
    const val ROOT_PROMPT_TIMEOUT_MS = 60_000L
    const val ROOT_CHECK_TIMEOUT_MS = 10_000L
    const val INSTALL_TIMEOUT_MS = 5 * 60_000L
  }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface InstallEntryPoint {
  fun privilegedInstaller(): PrivilegedInstaller
}
