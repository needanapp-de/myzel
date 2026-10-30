package de.needanapp.myzel.wear

import android.annotation.SuppressLint
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.SystemClock
import android.util.Log
import androidx.annotation.StringRes
import androidx.core.content.pm.PackageInfoCompat
import de.needanapp.myzel.link.AppState
import de.needanapp.myzel.link.Hello
import de.needanapp.myzel.link.Install
import de.needanapp.myzel.link.InstallStatus
import de.needanapp.myzel.link.LinkConnection
import de.needanapp.myzel.link.LinkKey
import de.needanapp.myzel.link.LinkServer
import de.needanapp.myzel.link.Message
import de.needanapp.myzel.link.Offer
import de.needanapp.myzel.link.OfferKind
import de.needanapp.myzel.link.Result
import de.needanapp.myzel.link.android.LinkKeyStore
import de.needanapp.myzel.link.android.RfcommServer
import de.needanapp.myzel.link.android.bluetoothAdapter
import de.needanapp.myzel.link.android.hasLinkPermissions
import de.needanapp.myzel.link.android.toLinkConnection
import de.needanapp.myzel.link.deviceName
import de.needanapp.myzel.link.sha256Of
import java.io.ByteArrayInputStream
import java.io.File
import java.io.OutputStream
import java.text.DateFormat
import java.util.Date
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val TAG = "MyzelWear"
private const val MAX_EVENTS = 20
private const val PREF_LAST_PHONE = "last_phone"
private const val PREF_LAST_PHONE_AT = "last_phone_at"

/**
 * Eine Installation oder Deinstallation ([removal]), die auf die Bestätigung des Nutzers wartet.
 */
data class PendingConfirm(
  val requestId: Int,
  val name: String,
  val removal: Boolean,
  val intent: Intent,
)

/** Was die Uhr gerade für das Handy tut. */
enum class TaskKind {
  /** Testdaten von „Verbindung testen“ empfangen. */
  TEST,
  RECEIVING,
  INSTALLING,
  REMOVING,
}

/**
 * Ein laufender Auftrag des Handys für die Anzeige, mit Fortschritt in Prozent; null: unbestimmt
 * (Installieren und Entfernen melden keinen Fortschritt).
 */
data class Task(val kind: TaskKind, val name: String, val percent: Int? = null)

/** Warum Myzel Wear gerade nicht lauscht. */
enum class Problem {
  NO_PERMISSION,
  BLUETOOTH_OFF,
}

/** Was die Oberfläche der Uhr zeigt. */
data class WatchState(
  /** Zwischen [WatchLink.start] und [WatchLink.stop]: Myzel Wear soll lauschen. */
  val running: Boolean = false,
  val listening: Boolean = false,
  val problem: Problem? = null,
  /** Name des verbundenen Handys. */
  val peer: String? = null,
  /** Kennung des Schlüssels aus der Einrichtung; ohne ihn nimmt die Uhr nichts an. */
  val keyId: String? = null,
  val confirm: PendingConfirm? = null,
  val task: Task? = null,
  /** Name des Handys, das zuletzt angemeldet war, und wann (Millisekunden); bleibt gespeichert. */
  val lastPhone: String? = null,
  val lastPhoneAt: Long = 0,
  /** Neueste zuerst. */
  val events: List<String> = emptyList(),
)

/**
 * Die Uhr-Seite von Myzel Link: lauscht, solange [LinkService] läuft, und bedient eine Verbindung
 * nach der anderen. Was ankommt, prüft [LinkServer]; APKs installiert und Apps entfernt der
 * [Installer].
 */
@SuppressLint("StaticFieldLeak") // nur der Application-Context
object WatchLink {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val _state = MutableStateFlow(WatchState())
  val state = _state.asStateFlow()

  private lateinit var context: Context
  private var job: Job? = null
  @Volatile private var server: RfcommServer? = null
  @Volatile private var socket: BluetoothSocket? = null

  /** Wann zuletzt etwas geschah (Start, Verbindung, Ende einer Verbindung); für [LinkService]. */
  @Volatile
  var lastActivity = SystemClock.elapsedRealtime()
    private set

  /**
   * Ob die Activity gerade sichtbar ist. Nur dann darf die App den Bestätigungsdialog selbst
   * öffnen; aus dem Hintergrund blockiert Wear OS den Start (gemessen auf der Pixel Watch 2).
   */
  @Volatile var inForeground = false

  fun init(context: Context) {
    this.context = context.applicationContext
    keyChanged()
    val prefs = prefs()
    _state.update {
      it.copy(
        lastPhone = prefs.getString(PREF_LAST_PHONE, null),
        lastPhoneAt = prefs.getLong(PREF_LAST_PHONE_AT, 0),
      )
    }
  }

  private fun prefs() = context.getSharedPreferences("link", Context.MODE_PRIVATE)

  /** Merkt sich [phone] als zuletzt angemeldetes Handy, für die Anzeige. */
  private fun rememberPhone(phone: String) {
    val now = System.currentTimeMillis()
    prefs().edit().putString(PREF_LAST_PHONE, phone).putLong(PREF_LAST_PHONE_AT, now).apply()
    _state.update { it.copy(lastPhone = phone, lastPhoneAt = now) }
  }

  /** Liest die Kennung des Schlüssels neu, nach der Einrichtung ([KeyProvider]). */
  fun keyChanged() = _state.update { it.copy(keyId = LinkKeyStore.ids().singleOrNull()) }

  /** Der Schlüssel für die nächste Verbindung; ein neuer aus der Einrichtung gilt sofort. */
  private fun currentKey(): LinkKey? = _state.value.keyId?.let { LinkKeyStore.get(it) }

  val isRunning: Boolean
    get() = job?.isActive == true

  /** Ob gerade ein Handy verbunden ist. */
  val isBusy: Boolean
    get() = socket != null

  /** Startet das Lauschen; braucht das Recht „Geräte in der Nähe“. */
  fun start() {
    lastActivity = SystemClock.elapsedRealtime()
    if (isRunning) return
    if (!context.hasLinkPermissions()) {
      _state.update { it.copy(problem = Problem.NO_PERMISSION) }
      return
    }
    _state.update { it.copy(running = true, problem = null) }
    job = scope.launch { listen() }
  }

  fun stop() {
    job?.cancel()
    job = null
    server?.close()
    socket?.close()
    _state.update { it.copy(running = false, listening = false, peer = null) }
  }

  fun setConfirm(confirm: PendingConfirm?) = _state.update { it.copy(confirm = confirm) }

  fun setTask(task: Task?) = _state.update { it.copy(task = task) }

  /** Nimmt die wartende Bestätigung heraus, wenn sie zu [requestId] gehört (oder zu jeder). */
  fun takeConfirm(requestId: Int? = null): PendingConfirm? {
    val confirm = _state.value.confirm ?: return null
    if (requestId != null && confirm.requestId != requestId) return null
    _state.update { it.copy(confirm = null) }
    return confirm
  }

  /** Schickt [message] aus dem Hintergrund, etwa vom PackageInstaller. */
  fun send(connection: LinkConnection, message: Message) {
    scope.launch {
      try {
        connection.send(message)
      } catch (e: Exception) {
        Log.w(TAG, "Senden fehlgeschlagen", e)
      }
    }
  }

  /** Ein Eintrag im Verlauf auf der Uhr. */
  fun event(@StringRes text: Int, vararg args: Any) {
    val line = context.getString(text, *args)
    Log.i(TAG, line)
    val time = DateFormat.getTimeInstance(DateFormat.SHORT).format(Date())
    _state.update { it.copy(events = (listOf("$time $line") + it.events).take(MAX_EVENTS)) }
  }

  @SuppressLint("MissingPermission")
  private suspend fun listen() {
    val adapter =
      context.bluetoothAdapter()
        ?: run {
          _state.update { it.copy(problem = Problem.BLUETOOTH_OFF) }
          return
        }
    var bluetoothOff = false
    // Nur der eigene Auftrag zählt: [job] ist beim Start womöglich noch der vorige, abgebrochene
    // (nach der Leerlaufzeit), dann lauschte die Uhr nie
    while (currentCoroutineContext().isActive) {
      try {
        if (!adapter.isEnabled) {
          if (!bluetoothOff) event(R.string.event_bluetooth_off)
          bluetoothOff = true
          _state.update { it.copy(listening = false, problem = Problem.BLUETOOTH_OFF) }
          delay(5_000)
          continue
        }
        bluetoothOff = false
        RfcommServer(adapter).use { s ->
          server = s
          _state.update { it.copy(listening = true, problem = null) }
          event(R.string.event_listening)
          while (true) serve(s.accept())
        }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        // nach stop(): Den Stand hat stop() schon gesetzt, ein neuer Auftrag lauscht vielleicht
        // schon
        if (!currentCoroutineContext().isActive) break
        Log.w(TAG, "Lauschen unterbrochen", e)
        _state.update { it.copy(listening = false) }
        delay(3_000)
      }
    }
  }

  @SuppressLint("MissingPermission")
  private fun serve(socket: BluetoothSocket) {
    this.socket = socket
    lastActivity = SystemClock.elapsedRealtime()
    val connection = socket.toLinkConnection()
    try {
      LinkServer(ownName(), currentKey(), Listener, SDK_INT, appVersionCode(), ownApkSha256)
        .serve(connection)
      event(R.string.event_disconnected)
    } catch (e: Exception) {
      Log.w(TAG, "Verbindung beendet", e)
      event(R.string.event_disconnected)
    } finally {
      connection.close()
      this.socket = null
      lastActivity = SystemClock.elapsedRealtime()
      // Empfang abgebrochen; Installieren und Entfernen laufen ohne die Verbindung weiter
      _state.update { s ->
        val receiving = s.task?.kind == TaskKind.TEST || s.task?.kind == TaskKind.RECEIVING
        s.copy(peer = null, task = s.task.takeUnless { receiving })
      }
    }
  }

  /** Für die Anzeige auf dem Handy, etwa „Google Pixel Watch 2“. */
  private fun ownName() = deviceName(Build.MANUFACTURER, Build.MODEL)

  /** SHA-256 der eigenen APK; daran erkennt das Handy, ob es eine neuere Myzel Wear dabeihat. */
  private val ownApkSha256: String by lazy {
    runCatching { File(context.applicationInfo.sourceDir).inputStream().use { sha256Of(it) } }
      .getOrDefault("")
  }

  private fun appVersionCode(): Long =
    PackageInfoCompat.getLongVersionCode(
      context.packageManager.getPackageInfo(context.packageName, 0)
    )

  private fun incomingApk() = File(context.cacheDir, "incoming.apk")

  /** Ob und in welcher Version [packageName] installiert ist, mit den Signaturen. */
  private fun appState(packageName: String): AppState {
    val info =
      try {
        context.packageManager.getPackageInfo(
          packageName,
          PackageManager.GET_SIGNING_CERTIFICATES,
        )
      } catch (_: PackageManager.NameNotFoundException) {
        return AppState(packageName, installed = false)
      }
    val signers =
      info.signingInfo?.apkContentsSigners.orEmpty().map {
        sha256Of(ByteArrayInputStream(it.toByteArray()))
      }
    val installer = runCatching {
      context.packageManager.getInstallSourceInfo(packageName).installingPackageName
    }
      .getOrNull()
    return AppState(
      packageName,
      installed = true,
      versionCode = PackageInfoCompat.getLongVersionCode(info),
      versionName = info.versionName.orEmpty(),
      signers = signers,
      installer = installer.orEmpty(),
    )
  }

  /** Der Name der installierten App [packageName], für Verlauf und Bestätigung. */
  private fun installedLabel(packageName: String): String =
    try {
      context.packageManager
        .getApplicationInfo(packageName, 0)
        .loadLabel(context.packageManager)
        .toString()
    } catch (_: PackageManager.NameNotFoundException) {
      packageName
    }

  /** Der Name der App in [apk], für Verlauf und Bestätigung. */
  private fun label(apk: File): String? {
    val info = context.packageManager.getPackageArchiveInfo(apk.path, 0) ?: return null
    val app = info.applicationInfo ?: return null
    app.sourceDir = apk.path
    app.publicSourceDir = apk.path
    return app.loadLabel(context.packageManager).toString()
  }

  /** Verwirft die Testdaten; den SHA-256 prüft [LinkServer] trotzdem. */
  private object Discard : OutputStream() {
    override fun write(b: Int) {}

    override fun write(b: ByteArray, off: Int, len: Int) {}
  }

  private object Listener : LinkServer.Listener {
    override fun onHello(peer: Hello) {
      _state.update { it.copy(peer = peer.device) }
      event(R.string.event_connected, peer.device)
    }

    override fun onAuth(peer: Hello, ok: Boolean) {
      event(if (ok) R.string.event_auth_ok else R.string.event_auth_refused)
      if (ok) rememberPhone(peer.device)
    }

    override fun onPing() {
      lastActivity = SystemClock.elapsedRealtime()
    }

    override fun sinkFor(offer: Offer): OutputStream {
      // Der Name der App steht erst in der APK; bei einem Update kennt die Uhr ihn schon
      val (kind, name, target) =
        when (offer.kind) {
          OfferKind.TEST -> Triple(TaskKind.TEST, offer.name, Discard)
          OfferKind.APK ->
            Triple(TaskKind.RECEIVING, installedLabel(offer.name), incomingApk().outputStream())
        }
      setTask(Task(kind, name, 0))
      return ProgressOutputStream(target, offer.size) { setTask(Task(kind, name, it)) }
    }

    override fun onData(offer: Offer, result: Result, connection: LinkConnection) {
      lastActivity = SystemClock.elapsedRealtime()
      if (offer.kind != OfferKind.APK || !result.ok) setTask(null)
      if (offer.kind != OfferKind.APK) return
      if (!result.ok) {
        event(R.string.event_damaged, offer.name)
        return
      }
      val name = label(incomingApk()) ?: offer.name
      event(R.string.event_received, name, offer.size / 1024)
      setTask(Task(TaskKind.INSTALLING, name))
      try {
        Installer.install(context, incomingApk(), name, connection)
      } catch (e: Exception) {
        Log.w(TAG, "Installation nicht gestartet", e)
        setTask(null)
        event(R.string.event_install_failed, name)
        send(connection, Install(InstallStatus.FAILURE, "not started: ${e.message}"))
      }
    }

    override fun appStates(packageNames: List<String>): List<AppState> {
      lastActivity = SystemClock.elapsedRealtime()
      return packageNames.map { appState(it) }
    }

    override fun onUninstall(packageName: String, connection: LinkConnection) {
      lastActivity = SystemClock.elapsedRealtime()
      // Myzel Wear entfernt sich nicht selbst; das geht nur über die Einrichtung
      if (packageName == context.packageName) {
        send(connection, Install(InstallStatus.FAILURE, "Myzel Wear entfernt sich nicht selbst"))
        return
      }
      if (!appState(packageName).installed) {
        send(connection, Install(InstallStatus.SUCCESS, "not installed"))
        return
      }
      val name = installedLabel(packageName)
      setTask(Task(TaskKind.REMOVING, name))
      try {
        Installer.uninstall(context, packageName, name, connection)
      } catch (e: Exception) {
        Log.w(TAG, "Entfernen nicht gestartet", e)
        setTask(null)
        event(R.string.event_uninstall_failed, name)
        send(connection, Install(InstallStatus.FAILURE, "not started: ${e.message}"))
      }
    }
  }
}
