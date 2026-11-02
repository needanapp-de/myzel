package de.needanapp.myzel.install

import android.content.Context
import android.graphics.Bitmap
import android.os.Build.VERSION.SDK_INT
import androidx.annotation.WorkerThread
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import mu.KotlinLogging
import org.fdroid.database.AppMetadata
import org.fdroid.index.v2.PackageVersion
import org.fdroid.install.InstallConfirmationState
import org.fdroid.install.InstallState
import org.fdroid.install.InstallStateWithInfo
import org.fdroid.install.PreApprovalResult
import org.fdroid.settings.SettingsManager
import org.fdroid.utils.IoDispatcher

/**
 * Ersatz für org.fdroid.install.SessionInstallManager, über ergänzte Importe eingebunden
 * (myzel/tools/apply-imports.py): in install/AppInstallManager.kt, dessen Test und in
 * ui/details/AppDetailsItem.kt. Dieselben Funktionen wie bei F-Droid; mit Root oder Shizuku
 * (Einstellung [InstallMethod]) installiert Myzel selbst ohne Nachfrage, sonst und wenn der Zugriff
 * fehlt, macht es der Paketinstaller von F-Droid.
 *
 * Bei einem Update von F-Droid prüfen, ob AppInstallManager weitere Funktionen von
 * SessionInstallManager aufruft; sie müssen hier durchgereicht werden.
 */
@Singleton
class SessionInstallManager
@Inject
constructor(
  @param:ApplicationContext private val context: Context,
  private val session: org.fdroid.install.SessionInstallManager,
  private val privilegedInstaller: PrivilegedInstaller,
  private val settingsManager: SettingsManager,
  @IoDispatcher scope: CoroutineScope,
) {

  private val log = KotlinLogging.logger {}

  private val method: InstallMethod
    get() =
      InstallMethod.fromKey(settingsManager.prefsFlow.value.get<String>(InstallMethod.PREF_KEY))

  init {
    settingsManager.prefsFlow
      .map { InstallMethod.fromKey(it.get<String>(InstallMethod.PREF_KEY)) }
      .distinctUntilChanged()
      .onEach { privilegedMethod = it != InstallMethod.SESSION }
      .launchIn(scope)
  }

  /**
   * Mit Root oder Shizuku braucht es keine Vorabfreigabe; installiert wird ohne Nachfrage. Sonst
   * nur für installierte Apps, mit ihrem Namen ([InstalledLabel]): Bei einer neuen App kann Android
   * den Namen aus der Paketquelle erst nach dem Download prüfen, und weicht er ab, fragt es ein
   * zweites Mal. Neue Apps fragt Android so einmal, nach dem Download.
   */
  suspend fun requestPreapproval(
    app: AppMetadata,
    iconGetter: suspend () -> Bitmap?,
    isUpdate: Boolean,
    version: PackageVersion,
    canRequestUserConfirmationNow: Boolean,
  ): PreApprovalResult {
    if (method != InstallMethod.SESSION || SDK_INT < 34) return PreApprovalResult.NotSupported
    val label =
      InstalledLabel.of(context, app.packageName)
        ?: run {
          log.info { "${app.packageName} not installed, no pre-approval" }
          return PreApprovalResult.NotSupported
        }
    return session.requestPreapproval(
      app,
      iconGetter,
      isUpdate,
      version,
      canRequestUserConfirmationNow,
      label,
    )
  }

  @WorkerThread
  suspend fun install(
    sessionId: Int?,
    packageName: String,
    state: InstallStateWithInfo,
    apkFile: File,
  ): InstallState {
    // eine Session gibt es nur nach einer Vorabfreigabe des Paketinstallers
    val method = method
    if (sessionId == null && method != InstallMethod.SESSION) {
      when (val result = privilegedInstaller.install(method, apkFile)) {
        PrivilegedResult.Success ->
          return InstallState.Installed(
            name = state.name,
            versionName = state.versionName,
            currentVersionName = state.currentVersionName,
            lastUpdated = state.lastUpdated,
            iconModel = state.iconModel,
          )
        is PrivilegedResult.Failure -> return InstallState.Error(result.message, state)
        PrivilegedResult.Unavailable ->
          log.warn { "$method not available for $packageName, using the package installer" }
      }
    }
    return session.install(sessionId, packageName, state, apkFile)
  }

  suspend fun requestUserConfirmation(state: InstallConfirmationState): InstallState =
    session.requestUserConfirmation(state)

  /** Ob der Paketinstaller von F-Droid installiert, nicht Root oder Shizuku. */
  val usesPackageInstaller: Boolean
    get() = method == InstallMethod.SESSION

  /**
   * Wie bei F-Droid über den alten Installationsdialog, für Xiaomi/Redmi-Geräte, auf denen die
   * Paketinstaller-API nicht geht (AppInstallManager, nur mit [usesPackageInstaller]).
   */
  fun installLegacy(apkFile: File) = session.installLegacy(apkFile)

  companion object {
    /** Ob gerade Root oder Shizuku eingestellt ist. */
    @Volatile private var privilegedMethod = false

    /**
     * Wie bei F-Droid, nur sind mit Root oder Shizuku Updates aller Apps ohne Nachfrage möglich,
     * auch für ein altes targetSdk.
     */
    fun isAutoUpdateSupported(targetSdk: Int): Boolean =
      privilegedMethod || org.fdroid.install.SessionInstallManager.isAutoUpdateSupported(targetSdk)
  }
}
