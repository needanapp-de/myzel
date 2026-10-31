package de.needanapp.myzel.watch

import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import mu.KotlinLogging
import org.fdroid.database.AppVersion
import org.fdroid.download.DownloaderFactory
import org.fdroid.download.getUri
import org.fdroid.index.RepoManager
import org.fdroid.utils.IoDispatcher
import org.fdroid.utils.sha256

/** Woran eine Installation auf der Uhr scheiterte. */
enum class WatchInstallProblem {
  /** Die APK ließ sich nicht laden, oder ihr SHA-256 passte nicht zum Index. */
  DOWNLOAD,

  /** Die APK ist nicht die App aus dem Index: anderer Paketname oder andere Signatur. */
  CHECK,

  /** Die Uhr hat die App nicht installiert. */
  INSTALL,

  /** Die Uhr hat die App nicht entfernt, etwa weil der Nutzer abgelehnt hat. */
  UNINSTALL,
}

/** Ob Myzel Wear gerade antwortet; erst dann gibt die App-Seite den Knopf frei. */
sealed interface WatchReach {
  /** Noch nicht geprüft, oder die Uhr wurde neu eingerichtet. */
  data object Unknown : WatchReach

  /** Die erste Prüfung läuft. */
  data object Checking : WatchReach

  data class Ok(val info: WatchInfo) : WatchReach

  data class Unreachable(val reason: WatchException.Reason) : WatchReach
}

/** So lange wartet eine Installation höchstens darauf, dass Myzel Wear antwortet. */
private const val WAIT_FOR_WATCH_MILLIS = 5 * 60_000L

/** Ziel eines Auftrags zum Entfernen, siehe [WatchInstaller.goals]. */
private const val REMOVAL = -1L

/** Gründe, aus denen ein erneuter Versuch nichts ändert: Die Uhr muss neu eingerichtet werden. */
private val FINAL_REASONS =
  setOf(
    WatchException.Reason.NOT_SET_UP,
    WatchException.Reason.REFUSED,
    WatchException.Reason.VERSION,
  )

/**
 * Der Stand einer Installation oder Deinstallation auf der Uhr. Gelingt sie, verschwindet er; was
 * auf der Uhr installiert ist, steht dann in [WatchInstaller.apps].
 */
sealed interface WatchInstallState {
  /** Myzel prüft, ob die Uhr antwortet, bevor es die APK lädt. */
  data object Connecting : WatchInstallState

  /** Myzel Wear antwortet nicht; Myzel versucht es weiter, bis es auf der Uhr geöffnet wird. */
  data object WaitingForWatch : WatchInstallState

  /** Die APK kommt aus der Paketquelle. */
  data class Downloading(val fraction: Float) : WatchInstallState

  /** Die APK geht über Bluetooth an die Uhr. */
  data class Sending(val fraction: Float) : WatchInstallState

  /**
   * Die Uhr fragt den Nutzer, ob sie installieren soll.
   *
   * @param notification die Bitte steht in einer Benachrichtigung auf der Uhr
   */
  data class Confirm(val notification: Boolean) : WatchInstallState

  /** Myzel bittet Myzel Wear, die App zu entfernen. */
  data object Removing : WatchInstallState

  /**
   * Die Uhr fragt den Nutzer, ob sie die App entfernen soll.
   *
   * @param notification die Bitte steht in einer Benachrichtigung auf der Uhr
   */
  data class ConfirmRemoval(val notification: Boolean) : WatchInstallState

  /**
   * Der Nutzer hat auf der Uhr nicht rechtzeitig bestätigt; die Bitte steht dort noch. Bestätigt er
   * später, räumt die nächste Abfrage den Stand ab ([WatchInstaller.probe]).
   */
  data class Unconfirmed(val removal: Boolean) : WatchInstallState

  /** Myzel kam nicht an die Uhr, siehe [WatchException.Reason]. */
  data class Unreachable(val reason: WatchException.Reason) : WatchInstallState

  /** @param detail Paketname, Signatur oder Meldung der Uhr, für die Fehlersuche */
  data class Failed(val problem: WatchInstallProblem, val detail: String? = null) :
    WatchInstallState
}

/** Ob die Installation noch läuft. */
val WatchInstallState?.isRunning: Boolean
  get() =
    this == WatchInstallState.Connecting ||
      this == WatchInstallState.WaitingForWatch ||
      this is WatchInstallState.Downloading ||
      this is WatchInstallState.Sending ||
      this is WatchInstallState.Confirm ||
      this == WatchInstallState.Removing ||
      this is WatchInstallState.ConfirmRemoval

/**
 * Bringt die Uhr-Version einer App auf die eingerichtete Uhr und entfernt sie wieder (Umbauplan
 * Phase 5). Myzel prüft zuerst, ob Myzel Wear antwortet ([reach]) und was auf der Uhr installiert
 * ist ([apps]), lädt dann die APK aus der Paketquelle (der Downloader prüft dabei den SHA-256 aus
 * dem Index) und prüft Paketname und Signatur. Dann schickt es sie über [WatchBridge] an Myzel
 * Wear, das den SHA-256 erneut prüft; der Nutzer bestätigt auf der Uhr.
 */
@Singleton
class WatchInstaller
@Inject
constructor(
  @ApplicationContext private val context: Context,
  private val bridge: WatchBridge,
  private val store: WatchStore,
  private val catalog: WatchCatalog,
  private val repoManager: RepoManager,
  private val downloaderFactory: DownloaderFactory,
  @IoDispatcher private val scope: CoroutineScope,
) {
  private val log = KotlinLogging.logger {}
  private val _states = MutableStateFlow<Map<String, WatchInstallState>>(emptyMap())

  /** Der Stand je Paketname; ohne Eintrag lief für die App noch keine Installation. */
  val states: StateFlow<Map<String, WatchInstallState>> = _states.asStateFlow()

  /**
   * Wohin der letzte Auftrag je Paketname wollte: der versionCode einer Installation oder
   * [REMOVAL]. Daran erkennt [settle], dass ein Fehler oder eine offene Bestätigung überholt ist.
   */
  private val goals = ConcurrentHashMap<String, Long>()

  /** Die eingerichtete Uhr, null ohne. */
  val watch: StateFlow<PairedWatch?> = bridge.watch

  /** Siehe [WatchBridge.wearUpdate]. */
  val wearUpdate: StateFlow<WearUpdateState?> = bridge.wearUpdate

  /** Siehe [WatchStore.askAfterInstall]. */
  val askAfterInstall: StateFlow<Boolean> = store.askAfterInstall

  fun setAskAfterInstall(ask: Boolean) = store.setAskAfterInstall(ask)

  private val _reach = MutableStateFlow<WatchReach>(WatchReach.Unknown)

  /** Ob Myzel Wear zuletzt antwortete; siehe [keepChecking]. */
  val reach: StateFlow<WatchReach> = _reach.asStateFlow()

  /** Was zuletzt über die Apps auf der Uhr bekannt wurde, gespeichert in [WatchStore]. */
  val apps: StateFlow<Map<String, WatchPackage>> = store.apps

  /** Wann Myzel zuletzt alle Uhr-Apps abgefragt hat ([checkAll]); 0: noch nie. */
  val appsCheckedAt: StateFlow<Long> = store.appsCheckedAt

  private val _checkingAll = MutableStateFlow(false)

  /** Ob [checkAll] gerade läuft. */
  val checkingAll: StateFlow<Boolean> = _checkingAll.asStateFlow()

  init {
    // eine neu eingerichtete oder entfernte Uhr ist noch nicht geprüft; neue Angaben der Uhr
    // (API-Stufe, Version von Myzel Wear) ändern daran nichts
    scope.launch {
      bridge.watch
        .map { it?.keyId }
        .distinctUntilChanged()
        .drop(1)
        .collect { _reach.value = WatchReach.Unknown }
    }
  }

  /**
   * Meldet sich bei Myzel Wear an, misst einen Ping und fragt nach [packageNames]; das Ergebnis
   * steht danach in [reach] und [apps]. [complete] heißt: Das sind alle Uhr-Apps ([checkAll]).
   */
  suspend fun probe(
    packageNames: List<String> = emptyList(),
    complete: Boolean = false,
  ): WatchReach {
    if (_reach.value == WatchReach.Unknown) _reach.value = WatchReach.Checking
    val result =
      try {
        val info = bridge.check(packageNames)
        store.saveApps(info.apps, complete)
        settle(info.apps)
        WatchReach.Ok(info)
      } catch (e: CancellationException) {
        throw e
      } catch (e: WatchException) {
        WatchReach.Unreachable(e.reason)
      } catch (e: Exception) {
        log.warn(e) { "Prüfung der Uhr" }
        WatchReach.Unreachable(WatchException.Reason.UNREACHABLE)
      }
    _reach.value = result
    return result
  }

  /**
   * Prüft die Verbindung zur Uhr und den Stand von [packageName] darauf, solange der Aufrufer läuft
   * (die Karte „Uhr“ ist zu sehen): sofort, bis die Uhr antwortet alle paar Sekunden, danach
   * selten, denn jede Anmeldung steht im Verlauf der Uhr. Während einer Installation nicht; muss
   * die Uhr neu eingerichtet werden, gar nicht mehr.
   */
  suspend fun keepChecking(packageName: String) {
    while (true) {
      val busy = _states.value.values.any { it.isRunning }
      val reach = if (busy) _reach.value else probe(listOf(packageName))
      if (reach is WatchReach.Unreachable && reach.reason in FINAL_REASONS) return
      val wait = if (reach is WatchReach.Ok) 120_000L else 3_000L
      // schon früher, wenn eine Installation die Uhr nicht erreichte
      withTimeoutOrNull(wait) { _reach.first { it != reach } }
    }
  }

  /**
   * Räumt die Stände ab, die [apps] überholt: Die App ist auf der Uhr so, wie der letzte Auftrag
   * wollte, etwa weil der Nutzer erst nach dem Warten bestätigt hat.
   */
  private fun settle(apps: List<WatchPackage>) {
    val byName = apps.associateBy { it.packageName }
    _states.update { states ->
      states.filterNot { (name, state) ->
        val app = byName[name]
        val goal = goals[name]
        when {
          app == null || goal == null || state.isRunning -> false
          goal == REMOVAL -> !app.installed
          else -> app.installed && app.versionCode >= goal
        }
      }
    }
  }

  /** Fragt Myzel Wear nach allen Apps mit Uhr-Version aus den Paketquellen ([WatchCatalog]). */
  fun checkAll() {
    if (!_checkingAll.compareAndSet(expect = false, update = true)) return
    scope.launch {
      try {
        probe(catalog.current().keys.sorted(), complete = true)
      } finally {
        _checkingAll.value = false
      }
    }
  }

  /** Installiert [versions] nacheinander auf der Uhr, etwa alle Updates. */
  fun installAll(versions: List<AppVersion>) = versions.forEach { install(it) }

  /** Lädt [version] und installiert sie auf der Uhr; den Stand zeigen [states] und [apps]. */
  fun install(version: AppVersion) =
    launchTask(
      version.packageName,
      version.versionCode,
      WatchInstallState.Connecting,
      WatchInstallProblem.INSTALL,
    ) {
      run(version)
    }

  /** Lässt Myzel Wear [packageName] entfernen; den Stand zeigen [states] und [apps]. */
  fun uninstall(packageName: String) =
    launchTask(packageName, REMOVAL, WatchInstallState.Removing, WatchInstallProblem.UNINSTALL) {
      var result: WatchInstallState? = WatchInstallState.Failed(WatchInstallProblem.UNINSTALL)
      bridge.uninstall(packageName) { step ->
        when (step) {
          is WatchInstallStep.Confirm ->
            set(packageName, WatchInstallState.ConfirmRemoval(step.notification))
          WatchInstallStep.Unconfirmed -> result = WatchInstallState.Unconfirmed(removal = true)
          WatchInstallStep.Removed -> {
            remember(WatchPackage(packageName, installed = false))
            result = null
          }
          is WatchInstallStep.Failed ->
            result = WatchInstallState.Failed(WatchInstallProblem.UNINSTALL, step.message)
          is WatchInstallStep.Sending,
          is WatchInstallStep.Installed -> {}
        }
      }
      result
    }

  /**
   * Führt [task] für [packageName] aus, wenn dafür nicht schon etwas läuft. Gibt [task] null
   * zurück, ist es gelungen und der Stand verschwindet; sonst bleibt das Ergebnis in [states].
   * [goal] siehe [goals].
   */
  private fun launchTask(
    packageName: String,
    goal: Long,
    start: WatchInstallState,
    problem: WatchInstallProblem,
    task: suspend () -> WatchInstallState?,
  ) {
    if (_states.value[packageName].isRunning) return
    goals[packageName] = goal
    set(packageName, start)
    scope.launch {
      val result =
        try {
          task()
        } catch (e: CancellationException) {
          throw e
        } catch (e: WatchException) {
          _reach.value = WatchReach.Unreachable(e.reason)
          WatchInstallState.Unreachable(e.reason)
        } catch (e: Exception) {
          log.warn(e) { "Uhr, $problem: $packageName" }
          WatchInstallState.Failed(problem, e.message)
        }
      _states.update { if (result == null) it - packageName else it + (packageName to result) }
    }
  }

  private suspend fun run(version: AppVersion): WatchInstallState? {
    val packageName = version.packageName
    // erst die Uhr, dann der Download: Antwortet sie nicht, lädt Myzel nichts umsonst
    val reach = awaitWatch(packageName)
    if (reach is WatchReach.Unreachable) return WatchInstallState.Unreachable(reach.reason)
    set(packageName, WatchInstallState.Downloading(0f))
    val repo =
      repoManager.getRepository(version.repoId)
        ?: return WatchInstallState.Failed(WatchInstallProblem.DOWNLOAD)
    val apk = File(File(context.cacheDir, "watch").apply { mkdirs() }, "${version.file.sha256}.apk")
    try {
      try {
        val uri = getUri(repo.address, version.file)
        val downloader = downloaderFactory.create(repo, uri, version.file, apk)
        downloader.setListener { read, total ->
          if (total > 0) set(packageName, WatchInstallState.Downloading(read.toFloat() / total))
        }
        downloader.download()
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        log.warn(e) { "Download der Uhr-Version von $packageName" }
        return WatchInstallState.Failed(WatchInstallProblem.DOWNLOAD, e.message)
      }
      check(apk, version)?.let {
        return it
      }
      var result: WatchInstallState? = WatchInstallState.Failed(WatchInstallProblem.INSTALL)
      bridge.install(apk) { step ->
        when (step) {
          is WatchInstallStep.Sending -> set(packageName, WatchInstallState.Sending(step.fraction))
          is WatchInstallStep.Confirm ->
            set(packageName, WatchInstallState.Confirm(step.notification))
          WatchInstallStep.Unconfirmed -> result = WatchInstallState.Unconfirmed(removal = false)
          is WatchInstallStep.Installed -> {
            // nannte die Uhr den Stand nicht, ist es die eben geprüfte APK
            remember(
              step.app
                ?: WatchPackage(
                  packageName,
                  installed = true,
                  versionCode = version.versionCode,
                  versionName = version.versionName,
                  signers = version.signer?.sha256.orEmpty(),
                )
            )
            result = null
          }
          is WatchInstallStep.Failed ->
            result = WatchInstallState.Failed(WatchInstallProblem.INSTALL, step.message)
          WatchInstallStep.Removed -> {}
        }
      }
      return result
    } finally {
      apk.delete()
    }
  }

  /**
   * Prüft die Uhr; antwortet Myzel Wear nicht (nicht geöffnet, Bluetooth aus), versucht es Myzel
   * alle paar Sekunden weiter, bis [WAIT_FOR_WATCH_MILLIS] um sind. So klappt eine Installation
   * auch, wenn der Nutzer Myzel auf der Uhr erst danach öffnet.
   */
  private suspend fun awaitWatch(packageName: String): WatchReach {
    val deadline = System.currentTimeMillis() + WAIT_FOR_WATCH_MILLIS
    while (true) {
      val reach = probe(listOf(packageName))
      val hopeless =
        reach !is WatchReach.Unreachable ||
          reach.reason in FINAL_REASONS ||
          reach.reason == WatchException.Reason.NO_PERMISSION ||
          System.currentTimeMillis() > deadline
      if (hopeless) return reach
      set(packageName, WatchInstallState.WaitingForWatch)
      delay(3_000)
    }
  }

  /**
   * Ob [apk] die App aus dem Index ist: derselbe Paketname und, nennt der Index Signaturen, mit
   * einer davon signiert. Gibt sonst das Ergebnis [WatchInstallProblem.CHECK] zurück.
   */
  private fun check(apk: File, version: AppVersion): WatchInstallState.Failed? {
    val info =
      context.packageManager.getPackageArchiveInfo(
        apk.path,
        PackageManager.GET_SIGNING_CERTIFICATES,
      ) ?: return WatchInstallState.Failed(WatchInstallProblem.CHECK)
    if (info.packageName != version.packageName) {
      return WatchInstallState.Failed(WatchInstallProblem.CHECK, info.packageName)
    }
    val expected = version.signer?.sha256.orEmpty()
    if (expected.isEmpty()) return null
    val signers = info.signingInfo?.apkContentsSigners.orEmpty().map { sha256(it.toByteArray()) }
    if (signers.any { it in expected }) return null
    return WatchInstallState.Failed(WatchInstallProblem.CHECK, signers.firstOrNull())
  }

  private fun set(packageName: String, state: WatchInstallState) = _states.update {
    it + (packageName to state)
  }

  private fun remember(app: WatchPackage) = store.saveApps(listOf(app))
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface WatchInstallerEntryPoint {
  fun watchInstaller(): WatchInstaller
}

/** Was die App-Seite für die Karte „Uhr“ braucht. */
class WatchInstallHandle(
  /** die eingerichtete Uhr, null ohne */
  val watch: PairedWatch?,
  val reach: WatchReach,
  /** siehe [WatchBridge.wearUpdate] */
  val wearUpdate: WearUpdateState?,
  /** die App auf der Uhr, null, solange Myzel nicht nachgefragt hat */
  val app: WatchPackage?,
  val state: WatchInstallState?,
  val install: (AppVersion) -> Unit,
  val uninstall: () -> Unit,
  /** siehe [WatchStore.askAfterInstall] */
  val askAfterInstall: Boolean,
  val setAskAfterInstall: (Boolean) -> Unit,
  /** siehe [WatchInstaller.keepChecking] */
  val keepChecking: suspend () -> Unit,
)

/** Uhr und Stand von [packageName] auf der Uhr; null in der Vorschau. */
@Composable
fun rememberWatchInstall(packageName: String): WatchInstallHandle? {
  if (LocalInspectionMode.current) return null
  val context = LocalContext.current
  val installer = remember {
    EntryPointAccessors.fromApplication(context, WatchInstallerEntryPoint::class.java)
      .watchInstaller()
  }
  val watch by installer.watch.collectAsState()
  val reach by installer.reach.collectAsState()
  val wearUpdate by installer.wearUpdate.collectAsState()
  val apps by installer.apps.collectAsState()
  val states by installer.states.collectAsState()
  val askAfterInstall by installer.askAfterInstall.collectAsState()
  return WatchInstallHandle(
    watch,
    reach,
    wearUpdate,
    apps[packageName],
    states[packageName],
    installer::install,
    { installer.uninstall(packageName) },
    askAfterInstall,
    installer::setAskAfterInstall,
    { installer.keepChecking(packageName) },
  )
}
