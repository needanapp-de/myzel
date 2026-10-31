package de.needanapp.myzel.watch

import android.annotation.SuppressLint
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import androidx.core.content.pm.PackageInfoCompat
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import de.needanapp.myzel.link.AppState
import de.needanapp.myzel.link.CONFIRM_BY_NOTIFICATION
import de.needanapp.myzel.link.Hello
import de.needanapp.myzel.link.InstallStatus
import de.needanapp.myzel.link.LinkAuthException
import de.needanapp.myzel.link.LinkClient
import de.needanapp.myzel.link.MAX_APPS
import de.needanapp.myzel.link.MIN_UPDATE_PROTOCOL
import de.needanapp.myzel.link.Offer
import de.needanapp.myzel.link.OfferKind
import de.needanapp.myzel.link.PROTOCOL_VERSION
import de.needanapp.myzel.link.android.LinkKeyStore
import de.needanapp.myzel.link.android.bluetoothAdapter
import de.needanapp.myzel.link.android.connectLink
import de.needanapp.myzel.link.android.hasLinkPermissions
import de.needanapp.myzel.link.android.toLinkConnection
import de.needanapp.myzel.link.deviceName
import de.needanapp.myzel.link.sha256Of
import java.io.File
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import mu.KotlinLogging
import org.fdroid.database.AppVersion

/**
 * Stand einer App auf der Uhr.
 *
 * @param versionCode 0, wenn sie nicht installiert ist
 * @param signers SHA-256 der Signaturzertifikate, wie im Index
 * @param installer Paketname der App, die sie auf der Uhr installiert hat; leer, wenn unbekannt
 */
@Serializable
data class WatchPackage(
  val packageName: String,
  val installed: Boolean,
  val versionCode: Long = 0,
  val versionName: String = "",
  val signers: List<String> = emptyList(),
  val installer: String = "",
)

/** Was die Uhr bei der Anmeldung über sich sagt. */
data class WatchInfo(
  /** Name der Uhr, etwa „Google Pixel Watch 2“ */
  val name: String,
  val sdk: Int,
  /** versionCode von Myzel Wear */
  val wearVersionCode: Long,
  /** Umlaufzeit eines Pings */
  val pingMillis: Long,
  /** Stand der Apps, nach denen [WatchBridge.check] gefragt hat */
  val apps: List<WatchPackage> = emptyList(),
)

/** Der Stand einer Installation oder Deinstallation auf der Uhr. */
sealed interface WatchInstallStep {
  /** Die APK geht über Bluetooth an die Uhr. */
  data class Sending(val fraction: Float) : WatchInstallStep

  /**
   * Die Uhr fragt den Nutzer, ob sie installieren oder entfernen soll.
   *
   * @param notification die Bitte steht in einer Benachrichtigung auf der Uhr
   *   ([CONFIRM_BY_NOTIFICATION])
   */
  data class Confirm(val notification: Boolean) : WatchInstallStep

  /**
   * Der Nutzer hat auf der Uhr nicht rechtzeitig geantwortet. Die Bitte steht dort noch; bestätigt
   * er später, zeigt das erst die nächste Abfrage.
   */
  data object Unconfirmed : WatchInstallStep

  /** @param app der Stand danach, null, wenn die Uhr ihn nicht nannte */
  data class Installed(val app: WatchPackage?) : WatchInstallStep

  data object Removed : WatchInstallStep

  data class Failed(val message: String) : WatchInstallStep
}

/**
 * Stand der Aktualisierung von Myzel Wear über Bluetooth. Myzel bringt eine neuere Myzel Wear aus
 * der Paketquelle (Debug-Builds: eine andere aus der Beilage) von selbst auf die Uhr, siehe
 * [MyzelWearSource].
 */
sealed interface WearUpdateState {
  /** Die neue Myzel Wear kommt aus der Paketquelle. */
  data class Downloading(val fraction: Float) : WearUpdateState

  /** Die neue Myzel Wear geht an die Uhr. */
  data class Sending(val fraction: Float) : WearUpdateState

  /**
   * Die Uhr fragt den Nutzer, ob sie das Update installieren soll.
   *
   * @param notification die Bitte steht in einer Benachrichtigung auf der Uhr
   */
  data class Confirm(val notification: Boolean) : WearUpdateState

  /** Myzel Wear startet nach dem Update neu; Myzel wartet, bis es wieder antwortet. */
  data object Restarting : WearUpdateState

  /** @param message für die Fehlersuche */
  data class Failed(val message: String?) : WearUpdateState
}

/** Warum die Uhr nicht erreichbar ist. */
class WatchException(val reason: Reason, cause: Throwable? = null) :
  IOException(reason.name, cause) {
  enum class Reason {
    /** Noch keine Uhr eingerichtet. */
    NOT_SET_UP,

    /** Das Recht „Geräte in der Nähe“ fehlt. */
    NO_PERMISSION,
    BLUETOOTH_OFF,

    /** Myzel Wear lauscht nicht: Uhr nicht in der Nähe oder die App nicht offen. */
    UNREACHABLE,

    /** Die Uhr kennt den Schlüssel nicht (anderes Handy, neu installiert): neu einrichten. */
    REFUSED,

    /**
     * Myzel Wear spricht eine andere Version von Myzel Link und ließ sich nicht über Bluetooth
     * aktualisieren: neu einrichten.
     */
    VERSION,
  }
}

/**
 * Die Verbindung von Myzel zur Uhr (Umbauplan Phase 4). Hinter der Schnittstelle steckt Myzel Link
 * ([LinkWatchBridge]); die Uhr richtet [de.needanapp.myzel.watch.setup.WatchSetup] ein.
 */
interface WatchBridge {
  /** Die eingerichtete Uhr, null ohne. */
  val watch: StateFlow<PairedWatch?>

  /**
   * Ob Myzel Wear gerade aktualisiert wird oder es zuletzt nicht klappte; null sonst. Jeder Auftrag
   * prüft zuerst, ob es eine neuere Myzel Wear gibt ([MyzelWearSource]), und bringt sie auf die
   * Uhr.
   */
  val wearUpdate: StateFlow<WearUpdateState?>

  /** Nach einem gescheiterten Update von Myzel Wear: beim nächsten Auftrag gleich neu versuchen. */
  fun retryWearUpdate()

  /**
   * Verbindet sich mit der Uhr, meldet sich an, misst einen Ping und fragt, ob und in welcher
   * Version [packageNames] auf der Uhr installiert sind ([WatchInfo.apps]).
   */
  suspend fun check(packageNames: List<String> = emptyList()): WatchInfo

  /**
   * Schickt [apk] an Myzel Wear und lässt sie installieren; [onStep] meldet den Stand. Endet mit
   * [WatchInstallStep.Installed] oder [WatchInstallStep.Failed].
   */
  suspend fun install(apk: File, onStep: (WatchInstallStep) -> Unit)

  /**
   * Lässt Myzel Wear [packageName] entfernen; der Nutzer bestätigt auf der Uhr. Endet mit
   * [WatchInstallStep.Removed] oder [WatchInstallStep.Failed].
   */
  suspend fun uninstall(packageName: String, onStep: (WatchInstallStep) -> Unit)
}

/**
 * Wartet so lange auf das Ergebnis einer Installation oder Deinstallation, bis die Uhr den Nutzer
 * fragt ([InstallStatus.CONFIRM]).
 */
private const val INSTALL_TIMEOUT_MILLIS = 180_000L

/**
 * Hat die Uhr den Nutzer gefragt, wartet Myzel so lange auf seine Antwort. Ist das Display der Uhr
 * aus, steht die Bitte in einer Benachrichtigung, und der Nutzer findet sie vielleicht erst später.
 */
private const val CONFIRM_TIMEOUT_MILLIS = 5 * 60_000L

/** Nach einem gescheiterten Update von Myzel Wear versucht Myzel es so lange nicht von selbst. */
private const val WEAR_UPDATE_BACKOFF_MILLIS = 15 * 60_000L

/** So oft und in diesem Abstand fragt Myzel nach einem Update, ob die neue Myzel Wear lauscht. */
private const val RESTART_ATTEMPTS = 20
private const val RESTART_DELAY_MILLIS = 3_000L

/** Ergebnis einer Anmeldung bei Myzel Wear. */
private sealed interface Login {
  class Ok(val client: LinkClient, val hello: Hello) : Login

  /** Myzel Wear spricht [protocol]; die Verbindung ist schon zu. */
  class OtherProtocol(val protocol: Int) : Login
}

/** [WatchBridge] über Myzel Link: ein RFCOMM-Kanal je Auftrag, danach legt das Handy auf. */
@Singleton
class LinkWatchBridge
@Inject
constructor(
  @ApplicationContext private val context: Context,
  private val store: WatchStore,
  private val wearSource: MyzelWearSource,
) : WatchBridge {
  private val log = KotlinLogging.logger {}
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

  /** Es spricht immer nur ein Auftrag mit der Uhr. */
  private val mutex = Mutex()

  override val watch: StateFlow<PairedWatch?> = store.watch

  private val _wearUpdate = MutableStateFlow<WearUpdateState?>(null)
  override val wearUpdate: StateFlow<WearUpdateState?> = _wearUpdate.asStateFlow()

  /**
   * Wann das Update auf die Myzel Wear mit dem SHA-256 [failedSha] zuletzt scheiterte; so lange
   * kein neuer Versuch.
   */
  @Volatile private var failedAt = 0L
  @Volatile private var failedSha = ""

  override fun retryWearUpdate() {
    failedAt = 0
    _wearUpdate.update { if (it is WearUpdateState.Failed) null else it }
  }

  override suspend fun check(packageNames: List<String>): WatchInfo = connected { client, info ->
    val ping = client.ping() / 1_000_000
    val apps =
      packageNames.chunked(MAX_APPS).flatMap { names ->
        client.appStates(names).map { it.toWatchPackage() }
      }
    info.copy(pingMillis = ping, apps = apps)
  }

  override suspend fun install(apk: File, onStep: (WatchInstallStep) -> Unit) =
    connected { client, _ ->
      val archive = context.packageManager.getPackageArchiveInfo(apk.path, 0)
      val name = archive?.packageName ?: apk.name
      val versionCode = archive?.let { PackageInfoCompat.getLongVersionCode(it) } ?: 0
      val size = apk.length()
      val sha256 = withContext(Dispatchers.IO) { apk.inputStream().use { sha256Of(it) } }
      onStep(WatchInstallStep.Sending(0f))
      val result =
        apk.inputStream().use { input ->
          client.offer(Offer(OfferKind.APK, name, versionCode, size, sha256), input) { sent ->
            onStep(WatchInstallStep.Sending(sent.toFloat() / size))
          }
        }
      if (!result.ok) {
        onStep(WatchInstallStep.Failed(result.message))
        return@connected
      }
      if (awaitOutcome(client, onStep)) {
        val app =
          try {
            client.appStates(listOf(name)).singleOrNull()?.toWatchPackage()
          } catch (e: IOException) {
            log.warn(e) { "Stand von $name nach der Installation" }
            null
          }
        onStep(WatchInstallStep.Installed(app))
      }
    }

  override suspend fun uninstall(packageName: String, onStep: (WatchInstallStep) -> Unit) =
    connected { client, _ ->
      client.uninstall(packageName)
      if (awaitOutcome(client, onStep)) onStep(WatchInstallStep.Removed)
    }

  /** Wartet auf das Ende einer Installation oder Deinstallation; true, wenn sie gelang. */
  private suspend fun awaitOutcome(
    client: LinkClient,
    onStep: (WatchInstallStep) -> Unit,
  ): Boolean {
    var asked = false
    while (true) {
      val install =
        try {
          client.nextInstall(if (asked) CONFIRM_TIMEOUT_MILLIS else INSTALL_TIMEOUT_MILLIS)
        } catch (e: IOException) {
          // Die Bitte auf der Uhr bleibt; bestätigt der Nutzer später, gelingt es doch noch
          if (!asked) throw e
          log.info { "Keine Antwort des Nutzers auf der Uhr: ${e.message}" }
          onStep(WatchInstallStep.Unconfirmed)
          return false
        }
      when (install.status) {
        InstallStatus.CONFIRM -> {
          asked = true
          onStep(WatchInstallStep.Confirm(install.message == CONFIRM_BY_NOTIFICATION))
        }
        InstallStatus.SUCCESS -> return true
        InstallStatus.FAILURE -> {
          onStep(WatchInstallStep.Failed(install.message))
          return false
        }
      }
    }
  }

  /** Verbindet sich, meldet sich an, führt [block] aus und legt danach auf. */
  private suspend fun <T> connected(block: suspend (LinkClient, WatchInfo) -> T): T =
    mutex.withLock {
      val watch = store.watch.value ?: throw WatchException(WatchException.Reason.NOT_SET_UP)
      val login = loginUpToDate(watch)
      val client = login.client
      val hello = login.hello
      try {
        store.updateWatch(hello.sdk, hello.appVersionCode)
        val result = block(client, WatchInfo(hello.device, hello.sdk, hello.appVersionCode, 0))
        runCatching { client.bye() }
        result
      } catch (e: Exception) {
        log.warn(e) { "Myzel Link: ${e.message}" }
        client.close()
        throw e
      }
    }

  /**
   * Meldet sich an. Gibt es eine neuere Myzel Wear als auf der Uhr ([newerWear]), bringt Myzel erst
   * diese auf die Uhr und meldet sich danach bei ihr an. Spricht die alte dieselbe Version von
   * Myzel Link und scheitert das Update, geht es mit ihr weiter; sonst folgt
   * [WatchException.Reason.VERSION].
   */
  private suspend fun loginUpToDate(watch: PairedWatch): Login.Ok {
    val first = login(watch)
    val target =
      try {
        newerWear(watch, first)
      } catch (e: Exception) {
        if (first is Login.Ok) runCatching { first.client.close() }
        throw e
      }
    if (first is Login.Ok) {
      if (target == null) return first
      runCatching { first.client.bye() }
    } else if (target == null || (first as Login.OtherProtocol).protocol < MIN_UPDATE_PROTOCOL) {
      throw WatchException(WatchException.Reason.VERSION)
    }
    var apk: WearApk? = null
    return try {
      apk = load(target)
      if (apk == null) {
        // Download gescheitert: mit der alten weiter, wenn sie dieselbe Version spricht
        return login(watch) as? Login.Ok ?: throw WatchException(WatchException.Reason.VERSION)
      }
      val protocol = (first as? Login.OtherProtocol)?.protocol ?: PROTOCOL_VERSION
      if (!updateWear(watch, protocol, apk)) {
        return login(watch) as? Login.Ok ?: throw WatchException(WatchException.Reason.VERSION)
      }
      awaitRestart(watch, apk)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      // ein Update, das so endet, ist gescheitert; sonst bliebe sein Stand stehen
      val update = _wearUpdate.value
      if (update != null && update !is WearUpdateState.Failed) failed(target.sha256, e.message)
      throw e
    } finally {
      apk?.let { wearSource.discard(it) }
    }
  }

  /**
   * Eine Myzel Wear, die Myzel auf die Uhr bringen will: aus der Beilage oder einer Paketquelle.
   */
  private sealed interface WearTarget {
    val sha256: String

    class Bundled(val apk: WearApk) : WearTarget {
      override val sha256 = apk.sha256
    }

    class Repo(val version: AppVersion) : WearTarget {
      override val sha256: String = version.file.sha256.lowercase()
    }
  }

  /**
   * Die Myzel Wear, die auf die Uhr soll, oder null, wenn die dort aktuell ist oder ein Update
   * gerade erst scheiterte. Die Beilage zählt, wenn sie sich unterscheidet (Debug-Builds); sonst
   * die neueste aus den Paketquellen, wenn sie eine höhere Version hat. Spricht die Uhr eine andere
   * Version von Myzel Link, ist ihre Version die zuletzt gemeldete.
   */
  private suspend fun newerWear(watch: PairedWatch, login: Login): WearTarget? {
    val hello = (login as? Login.Ok)?.hello
    val bundled = wearSource.bundled()
    val target =
      if (bundled != null) {
        WearTarget.Bundled(bundled).takeIf { hello == null || hello.appSha256 != bundled.sha256 }
      } else {
        val installed = hello?.appVersionCode ?: watch.wearVersionCode
        val latest = wearSource.latest(hello?.sdk ?: watch.sdk)
        latest?.takeIf { it.versionCode > installed }?.let { WearTarget.Repo(it) }
      }
    return target?.takeIf { mayUpdate(it.sha256) }
  }

  /** Holt die APK zu [target]; null, wenn das scheiterte ([failed] ist dann schon gesetzt). */
  private suspend fun load(target: WearTarget): WearApk? =
    when (target) {
      is WearTarget.Bundled -> target.apk
      is WearTarget.Repo -> {
        log.info { "Myzel Wear ${target.version.versionName} laden" }
        _wearUpdate.value = WearUpdateState.Downloading(0f)
        try {
          wearSource.download(target.version) {
            _wearUpdate.value = WearUpdateState.Downloading(it)
          }
        } catch (e: WearApkException) {
          failed(target.sha256, e.message)
          null
        }
      }
    }

  /**
   * Schickt [apk] mit der Version [protocol] von Myzel Link an die Uhr; Myzel Wear installiert sich
   * damit selbst. Gibt zurück, ob die Uhr mit dem Installieren begonnen hat.
   */
  private suspend fun updateWear(watch: PairedWatch, protocol: Int, apk: WearApk): Boolean {
    log.info { "Myzel Wear aktualisieren (Protokoll $protocol)" }
    _wearUpdate.value = WearUpdateState.Sending(0f)
    val login = login(watch, protocol) as? Login.Ok ?: return failed(apk.sha256, "Protokoll")
    val client = login.client
    try {
      val size = apk.file.length()
      val offer = Offer(OfferKind.APK, apk.packageName, apk.versionCode, size, apk.sha256)
      val result =
        apk.file.inputStream().use { input ->
          client.offer(offer, input) { sent ->
            _wearUpdate.value = WearUpdateState.Sending(sent.toFloat() / size)
          }
        }
      if (!result.ok) return failed(apk.sha256, result.message)
      // Myzel Wear installiert sich selbst; dabei endet sein Prozess und mit ihm die Verbindung
      var asked = false
      while (true) {
        val install =
          try {
            client.nextInstall(if (asked) CONFIRM_TIMEOUT_MILLIS else INSTALL_TIMEOUT_MILLIS)
          } catch (_: IOException) {
            break
          }
        when (install.status) {
          InstallStatus.CONFIRM -> {
            asked = true
            _wearUpdate.value = WearUpdateState.Confirm(install.message == CONFIRM_BY_NOTIFICATION)
          }
          InstallStatus.SUCCESS -> break
          InstallStatus.FAILURE -> return failed(apk.sha256, install.message)
        }
      }
    } catch (e: IOException) {
      return failed(apk.sha256, e.message)
    } finally {
      client.close()
    }
    _wearUpdate.value = WearUpdateState.Restarting
    return true
  }

  /**
   * Wartet, bis die neue Myzel Wear lauscht, und meldet sich bei ihr an. Antwortet bis zuletzt die
   * alte (das Update wurde abgelehnt oder scheiterte nach dem Ende der Verbindung) und spricht sie
   * dieselbe Version von Myzel Link, geht es mit ihr weiter.
   */
  private suspend fun awaitRestart(watch: PairedWatch, apk: WearApk): Login.Ok {
    var oldAnswered = false
    var otherProtocol = false
    for (attempt in 1..RESTART_ATTEMPTS) {
      delay(RESTART_DELAY_MILLIS)
      // bis der Prozess endet, antwortet noch die alte Myzel Wear
      val login =
        try {
          login(watch)
        } catch (e: WatchException) {
          if (e.reason != WatchException.Reason.UNREACHABLE) throw e
          null
        } catch (e: IOException) {
          null
        }
      if (login is Login.Ok) {
        if (login.hello.appSha256 == apk.sha256) {
          log.info { "Myzel Wear aktualisiert" }
          _wearUpdate.value = null
          return login
        }
        login.client.close()
        oldAnswered = true
      }
      if (login is Login.OtherProtocol) otherProtocol = true
    }
    failed(apk.sha256, "Myzel Wear hat nach dem Update nicht geantwortet")
    if (oldAnswered) {
      return login(watch) as? Login.Ok ?: throw WatchException(WatchException.Reason.VERSION)
    }
    throw WatchException(
      if (otherProtocol) WatchException.Reason.VERSION else WatchException.Reason.UNREACHABLE
    )
  }

  private fun mayUpdate(sha256: String): Boolean =
    sha256 != failedSha || System.currentTimeMillis() - failedAt > WEAR_UPDATE_BACKOFF_MILLIS

  private fun failed(sha256: String, message: String?): Boolean {
    log.warn { "Update von Myzel Wear: $message" }
    failedAt = System.currentTimeMillis()
    failedSha = sha256
    _wearUpdate.value = WearUpdateState.Failed(message)
    return false
  }

  /** Verbindet sich und meldet sich mit der Version [protocol] von Myzel Link an. */
  @SuppressLint("MissingPermission")
  private suspend fun login(watch: PairedWatch, protocol: Int = PROTOCOL_VERSION): Login {
    if (!context.hasLinkPermissions()) {
      throw WatchException(WatchException.Reason.NO_PERMISSION)
    }
    val adapter = context.bluetoothAdapter()
    if (adapter == null || !adapter.isEnabled) {
      throw WatchException(WatchException.Reason.BLUETOOTH_OFF)
    }
    val device = adapter.getRemoteDevice(watch.bluetoothAddress)
    val socket: BluetoothSocket =
      try {
        runInterruptible(Dispatchers.IO) { device.connectLink() }
      } catch (e: IOException) {
        throw WatchException(WatchException.Reason.UNREACHABLE, e)
      }
    val client = LinkClient(socket.toLinkConnection(), scope)
    return try {
      val hello =
        client.hello(phoneName(), { LinkKeyStore.get(it) }, SDK_INT, ownVersionCode(), protocol)
      Login.Ok(client, hello)
    } catch (e: LinkAuthException) {
      client.close()
      if (e.reason == LinkAuthException.Reason.VERSION) Login.OtherProtocol(e.peerProtocol)
      else throw WatchException(WatchException.Reason.REFUSED, e)
    } catch (e: Exception) {
      client.close()
      throw e
    }
  }

  private fun ownVersionCode(): Long =
    PackageInfoCompat.getLongVersionCode(
      context.packageManager.getPackageInfo(context.packageName, 0)
    )
}

private fun AppState.toWatchPackage() =
  WatchPackage(packageName, installed, versionCode, versionName, signers, installer)

/** Name des Handys für die Uhr, etwa „Google Pixel 6“. */
internal fun phoneName() = deviceName(Build.MANUFACTURER, Build.MODEL)

@Module
@InstallIn(SingletonComponent::class)
abstract class WatchBridgeModule {
  @Binds abstract fun bindWatchBridge(bridge: LinkWatchBridge): WatchBridge
}
