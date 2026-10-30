package de.needanapp.myzel.watch.setup

import android.annotation.SuppressLint
import android.bluetooth.BluetoothClass
import android.bluetooth.BluetoothDevice
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import de.needanapp.myzel.link.LinkClient
import de.needanapp.myzel.link.LinkKey
import de.needanapp.myzel.link.android.LinkKeyStore
import de.needanapp.myzel.link.android.bluetoothAdapter
import de.needanapp.myzel.link.android.connectLink
import de.needanapp.myzel.link.android.toLinkConnection
import de.needanapp.myzel.link.deviceName
import de.needanapp.myzel.link.toHex
import de.needanapp.myzel.watch.HiddenDevice
import de.needanapp.myzel.watch.MyzelWearSource
import de.needanapp.myzel.watch.PairedWatch
import de.needanapp.myzel.watch.WatchBridge
import de.needanapp.myzel.watch.WatchException
import de.needanapp.myzel.watch.WatchStore
import de.needanapp.myzel.watch.WearApkException
import de.needanapp.myzel.watch.phoneName
import io.github.muntashirakon.adb.AdbAuthenticationFailedException
import io.github.muntashirakon.adb.AdbPairingRequiredException
import java.io.IOException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withContext
import mu.KotlinLogging

/** Die Schritte der Einrichtung, in dieser Reihenfolge (Umbauplan Phase 1). */
enum class SetupStep {
  /** Mit dem Code aus dem Kopplungsdialog der Uhr koppeln (nur beim ersten Mal). */
  PAIR,

  /** Per ADB über WLAN mit der Uhr verbinden. */
  CONNECT,

  /** Myzel Wear aus der Paketquelle (Debug-Builds: aus der Beilage) installieren. */
  INSTALL,

  /** Myzel Wear darf installieren, Bluetooth nutzen und benachrichtigen. */
  PERMISSIONS,

  /** Gemeinsamen Schlüssel für Myzel Link übergeben. */
  KEY,

  /** Myzel Wear starten, damit es lauscht. */
  START,

  /** Die Uhr in der Bluetooth-Kopplung des Handys finden. */
  BLUETOOTH,

  /** Testverbindung über Myzel Link. */
  TEST,

  /** „Debugging über WLAN“ auf der Uhr wieder abschalten. */
  WIFI_OFF,
}

/** Woran die Einrichtung scheiterte. */
enum class SetupProblem {
  NO_WATCH,

  /** Das gewählte Gerät ist ein Handy oder Tablet mit Debugging über WLAN, keine Uhr. */
  NOT_A_WATCH,
  PAIRING_CLOSED,
  PAIRING_FAILED,
  NOT_REACHABLE,

  /**
   * Das Gerät nimmt die Verbindung an und schließt sie gleich wieder: Dieses Handy ist dort nicht
   * (mehr) gekoppelt. Ob es eine Uhr ist, bleibt offen.
   */
  NOT_PAIRED,

  /** Keine eingeschaltete Paketquelle hat Myzel Wear, siehe [MyzelWearSource]. */
  NO_WEAR_APK,

  /** Myzel Wear ließ sich nicht aus der Paketquelle laden oder war nicht die richtige APK. */
  WEAR_DOWNLOAD_FAILED,
  INSTALL_FAILED,
  KEY_FAILED,
  NOT_BONDED,
  LINK_FAILED,
  FAILED,
}

data class SetupState(
  /** Geräte mit „Debugging über WLAN“ im lokalen Netz */
  val devices: List<AdbDevice> = emptyList(),
  val selected: String? = null,
  /**
   * wie viele Geräte im Netz ausgeblendet sind, weil sie keine Uhr sind (siehe
   * [WatchStore.hideDevice])
   */
  val hiddenCount: Int = 0,
  val running: Boolean = false,
  val steps: List<SetupStep> = emptyList(),
  val current: SetupStep? = null,
  val done: Set<SetupStep> = emptySet(),
  val problem: SetupProblem? = null,
  /** technische Einzelheiten zu [problem] */
  val detail: String? = null,
  /** bei [SetupProblem.LINK_FAILED] */
  val linkProblem: WatchException.Reason? = null,
  /**
   * mDNS-Name des Geräts, an dem [problem] lag, wenn es vielleicht keine Uhr ist; die Einrichtung
   * bietet dann an, es auszublenden (siehe [WatchSetup.hide])
   */
  val failedDevice: String? = null,
  val finished: Boolean = false,
)

private class SetupFailure(
  val problem: SetupProblem,
  detail: String? = null,
  cause: Throwable? = null,
  val linkProblem: WatchException.Reason? = null,
) : Exception(detail, cause)

/**
 * Richtet die Uhr ein (Umbauplan Phase 1): findet sie per mDNS, koppelt per ADB über WLAN,
 * installiert Myzel Wear, übergibt den Schlüssel für Myzel Link, prüft die Verbindung über
 * Bluetooth und schaltet „Debugging über WLAN“ wieder ab. Übernommen aus dem Prototyp
 * myzel-link-prototype (PhoneViewModel).
 */
class WatchSetup
@Inject
constructor(
  @ApplicationContext private val context: Context,
  private val store: WatchStore,
  private val bridge: WatchBridge,
  private val wearSource: MyzelWearSource,
) {
  private val log = KotlinLogging.logger {}
  private val discovery = AdbDiscovery(context)
  private val _state = MutableStateFlow(SetupState())
  val state: StateFlow<SetupState> = _state.asStateFlow()
  private var adb: WatchAdb? = null
  private var searchScope: CoroutineScope? = null

  /** Sucht Geräte mit „Debugging über WLAN“, bis [stop]. */
  fun start() {
    if (searchScope != null) return
    discovery.start()
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    searchScope = scope
    scope.launch {
      combine(discovery.devices, store.hiddenDevices) { found, hidden -> found to hidden }
        .collect { (found, hidden) ->
          // dazu die Uhren, mit denen sich das Handy zuletzt verbunden hat
          val known =
            store.rememberedAdb
              .filterKeys { it !in found || found.getValue(it).connect == null }
              .mapValues { (name, endpoint) ->
                (found[name] ?: AdbDevice(name)).copy(connect = endpoint, remembered = true)
              }
          // Geräte, die sich schon einmal als keine Uhr zeigten, fehlen in der Liste
          val hiddenSerials = hidden.mapTo(HashSet()) { it.serial }
          val all = (found + known).values
          val devices = all.filter { it.serial !in hiddenSerials }.sortedBy { it.name }
          // Im Netz können weitere Geräte mit Debugging über WLAN sein; von selbst gewählt wird nur
          // eines, dessen Kopplungsdialog gerade offen ist
          _state.update { s ->
            s.copy(
              devices = devices,
              hiddenCount = all.size - devices.size,
              selected =
                s.selected?.takeIf { name -> devices.any { it.name == name } }
                  ?: devices.firstOrNull { it.pairing != null }?.name,
            )
          }
        }
    }
  }

  fun stop() {
    discovery.stop()
    searchScope?.cancel()
    searchScope = null
    runCatching { adb?.close() }
    adb = null
  }

  fun select(name: String) = _state.update { it.copy(selected = name) }

  /**
   * Blendet das Gerät [name] aus, ohne zu wissen, was es ist: für Geräte, die sich nicht verbinden
   * lassen. Wieder einblenden lässt es sich in den Einstellungen.
   */
  fun hide(name: String) {
    val device = _state.value.devices.firstOrNull { it.name == name } ?: return
    store.forgetAdb(name)
    _state.update {
      it.copy(
        selected = it.selected.takeIf { selected -> selected != name },
        steps = emptyList(),
        done = emptySet(),
        problem = null,
        failedDevice = null,
      )
    }
    store.hideDevice(HiddenDevice(device.serial, name = ""))
  }

  /** Vergisst das Ergebnis des letzten Durchgangs. */
  fun reset() = _state.update {
    it.copy(steps = emptyList(), done = emptySet(), problem = null, finished = false)
  }

  /**
   * Richtet die gewählte Uhr ein; mit [code] koppelt sie zuerst. Den Stand zeigt [state]; Fehler
   * landen dort und nicht als Ausnahme.
   */
  suspend fun setUp(code: String?) {
    if (_state.value.running) return
    val steps = SetupStep.entries.filter { it != SetupStep.PAIR || !code.isNullOrBlank() }
    _state.update {
      it.copy(
        running = true,
        steps = steps,
        current = null,
        done = emptySet(),
        problem = null,
        detail = null,
        linkProblem = null,
        failedDevice = null,
        finished = false,
      )
    }
    var deviceName: String? = null
    try {
      val device =
        _state.value.devices.firstOrNull { it.name == _state.value.selected }
          ?: throw SetupFailure(SetupProblem.NO_WATCH)
      deviceName = device.name
      var session = Session(device)
      for (step in steps) {
        _state.update { it.copy(current = step) }
        withContext(Dispatchers.IO) { session = run(step, session, code) }
        _state.update { it.copy(done = it.done + step) }
      }
      _state.update { it.copy(finished = true) }
    } catch (e: SetupFailure) {
      log.warn(e) { "Einrichtung: ${e.problem}" }
      val unknown = e.problem == SetupProblem.NOT_PAIRED || e.problem == SetupProblem.NOT_REACHABLE
      _state.update {
        it.copy(
          problem = e.problem,
          detail = e.message,
          linkProblem = e.linkProblem,
          failedDevice = deviceName.takeIf { unknown },
        )
      }
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      log.warn(e) { "Einrichtung fehlgeschlagen" }
      _state.update {
        it.copy(problem = SetupProblem.FAILED, detail = "${e.javaClass.simpleName}: ${e.message}")
      }
    } finally {
      _state.update { it.copy(running = false, current = null) }
    }
  }

  /** Was die Schritte einander weitergeben. */
  private data class Session(
    val device: AdbDevice,
    val adb: WatchAdb? = null,
    val name: String = device.serial,
    val wearPackage: String = "",
    val keyId: String = "",
  )

  private suspend fun run(step: SetupStep, s: Session, code: String?): Session =
    when (step) {
      SetupStep.PAIR -> {
        val pairing = s.device.pairing ?: throw SetupFailure(SetupProblem.PAIRING_CLOSED)
        val ok =
          try {
            watchAdb().pair(pairing.host, pairing.port, code!!.trim())
          } catch (e: Exception) {
            throw SetupFailure(SetupProblem.PAIRING_FAILED, e.message, e)
          }
        if (!ok) throw SetupFailure(SetupProblem.PAIRING_FAILED)
        s
      }
      SetupStep.CONNECT -> {
        val a = connectAdb(s.device)
        val manufacturer = a.exec("getprop ro.product.manufacturer")
        val model = a.exec("getprop ro.product.model")
        val name = deviceName(manufacturer, model).ifEmpty { s.device.serial }
        // im Netz können auch Handys mit Debugging über WLAN sein; auf die kommt nichts, und die
        // Einrichtung zeigt sie nicht mehr
        if (!a.isWatch()) {
          runCatching { a.disconnect() }
          store.forgetAdb(s.device.name)
          _state.update { it.copy(selected = null) }
          store.hideDevice(HiddenDevice(s.device.serial, name))
          throw SetupFailure(SetupProblem.NOT_A_WATCH, name)
        }
        store.rememberAdb(s.device.name, s.device.connect!!)
        s.copy(adb = a, name = name)
      }
      SetupStep.INSTALL -> {
        val a = s.adb!!
        val sdk = a.exec("getprop ro.build.version.sdk").toIntOrNull() ?: 0
        val apk =
          wearSource.bundled()
            ?: run {
              val version = wearSource.latest(sdk) ?: throw SetupFailure(SetupProblem.NO_WEAR_APK)
              try {
                wearSource.download(version)
              } catch (e: WearApkException) {
                throw SetupFailure(SetupProblem.WEAR_DOWNLOAD_FAILED, e.message, e)
              }
            }
        try {
          var result = a.install(apk.file)
          // mit einem anderen Schlüssel signiert (etwa Debug statt Release): erst entfernen
          if ("INSTALL_FAILED_UPDATE_INCOMPATIBLE" in result) {
            a.exec("pm uninstall ${apk.packageName}")
            result = a.install(apk.file)
          }
          // auf der Uhr ist schon eine neuere, gleich signierte: die bleibt
          val newer = "INSTALL_FAILED_VERSION_DOWNGRADE" in result
          if (!result.startsWith("Success") && !newer) {
            throw SetupFailure(SetupProblem.INSTALL_FAILED, result)
          }
        } finally {
          wearSource.discard(apk)
        }
        s.copy(wearPackage = apk.packageName)
      }
      SetupStep.PERMISSIONS -> {
        val a = s.adb!!
        val out = a.exec("appops set ${s.wearPackage} REQUEST_INSTALL_PACKAGES allow")
        if (out.isNotEmpty()) throw SetupFailure(SetupProblem.INSTALL_FAILED, out)
        // gibt es auf älteren Uhren nicht; dann fragt Myzel Wear selbst
        a.exec("pm grant ${s.wearPackage} android.permission.BLUETOOTH_CONNECT")
        a.exec("pm grant ${s.wearPackage} android.permission.POST_NOTIFICATIONS")
        s
      }
      SetupStep.KEY -> s.copy(keyId = handOverKey(s.adb!!, s.wearPackage))
      SetupStep.START -> {
        s.adb!!.exec("am start -n ${s.wearPackage}/de.needanapp.myzel.wear.MainActivity")
        s
      }
      SetupStep.BLUETOOTH -> {
        val address = findBluetoothAddress(s.adb!!, s.keyId)
        store.save(
          PairedWatch(
            name = s.name,
            serial = s.device.serial,
            bluetoothAddress = address,
            keyId = s.keyId,
            wearPackage = s.wearPackage,
            sdk = s.adb.exec("getprop ro.build.version.sdk").toIntOrNull() ?: 0,
            wearVersionCode = 0,
          )
        )
        s
      }
      SetupStep.TEST -> {
        // Myzel Wear braucht nach dem Start einen Moment, bis es lauscht
        var attempt = 1
        while (true) {
          try {
            bridge.check()
            break
          } catch (e: WatchException) {
            val retry = e.reason == WatchException.Reason.UNREACHABLE && attempt < 6
            if (!retry) throw SetupFailure(SetupProblem.LINK_FAILED, e.reason.name, e, e.reason)
            attempt++
            delay(2_000)
          }
        }
        s
      }
      SetupStep.WIFI_OFF -> {
        // die ADB-Verbindung endet damit; der Befehl kommt trotzdem an
        runCatching { s.adb!!.exec("settings put global adb_wifi_enabled 0") }
        runCatching { s.adb!!.disconnect() }
        s
      }
    }

  /** Frische ADB-Verbindung zu [device]; eine alte kann tot sein, wenn das WLAN der Uhr schlief. */
  private suspend fun connectAdb(device: AdbDevice): WatchAdb {
    val connect = device.connect ?: throw SetupFailure(SetupProblem.NOT_REACHABLE)
    val a = watchAdb()
    runCatching { a.disconnect() }
    // Schläft das WLAN der Uhr, scheitert der erste Versuch mit „Host unreachable“, bis sie wieder
    // auf ARP antwortet (bis etwa 20 s, Pixel Watch 2)
    var attempt = 1
    while (true) {
      try {
        a.connect(connect.host, connect.port)
        break
      } catch (e: Exception) {
        val cause = generateSequence<Throwable>(e) { it.cause }.last()
        if (cause.isRejected()) throw SetupFailure(SetupProblem.NOT_PAIRED, e.message, e)
        val retry = cause is NoRouteToHostException || cause is SocketTimeoutException
        if (attempt >= 4 || !retry) throw SetupFailure(SetupProblem.NOT_REACHABLE, e.message, e)
        attempt++
        delay(5_000)
      }
    }
    return a
  }

  /**
   * Ob das Gerät die Verbindung angenommen und gleich wieder geschlossen hat: Das tut adbd mit
   * einem Schlüssel, den es aus keiner Kopplung kennt. libadb-android erkennt das nur an einer
   * SSLProtocolException als [AdbPairingRequiredException]; mit Conscrypt endet die Verbindung nach
   * dem TLS-Handshake aber einfach, und es bleibt bei „Connection failed“.
   */
  private fun Throwable.isRejected(): Boolean =
    this is AdbPairingRequiredException ||
      this is AdbAuthenticationFailedException ||
      (javaClass == IOException::class.java && message == "Connection failed")

  /** Die ADB-Verbindung samt Schlüssel; der Schlüssel entsteht beim ersten Mal (dauert kurz). */
  private fun watchAdb(): WatchAdb = adb ?: WatchAdb(store.adbKeyDir).also { adb = it }

  /**
   * Erzeugt einen neuen gemeinsamen Schlüssel und übergibt ihn über die ADB-Shell an Myzel Wear
   * (KeyProvider). Das Handy legt ihn in seinen Keystore; den alten löscht [WatchStore.save].
   */
  private fun handOverKey(a: WatchAdb, wearPackage: String): String {
    val (id, secret) = LinkKey.generate()
    try {
      LinkKeyStore.import(id, secret)
      // Das Geheimnis steht nur in diesem Befehl, nicht im Log
      val out =
        a.exec(
          "content call --uri content://$wearPackage.key --method setKey --arg $id:${secret.toHex()}"
        )
      if ("ok=true" !in out) {
        LinkKeyStore.delete(id)
        throw SetupFailure(SetupProblem.KEY_FAILED, out)
      }
    } catch (e: IOException) {
      LinkKeyStore.delete(id)
      throw SetupFailure(SetupProblem.KEY_FAILED, e.message, e)
    } finally {
      secret.fill(0)
    }
    return id
  }

  /**
   * Die Adresse der Uhr in der Bluetooth-Kopplung des Handys. Erst fragt Myzel die Uhr per ADB;
   * kennt das Handy diese Adresse nicht, probiert es die gekoppelten Uhren über Myzel Link durch:
   * Die mit dem neuen Schlüssel ist die richtige.
   */
  @SuppressLint("MissingPermission")
  private suspend fun findBluetoothAddress(a: WatchAdb, keyId: String): String {
    val bonded =
      context.bluetoothAdapter()?.bondedDevices.orEmpty().takeIf { it.isNotEmpty() }
        ?: throw SetupFailure(SetupProblem.NOT_BONDED)
    val fromAdb =
      sequenceOf(
          { a.exec("settings get secure bluetooth_address") },
          { OWN_ADDRESS.find(a.exec("dumpsys bluetooth_manager"))?.groupValues?.get(1).orEmpty() },
        )
        .mapNotNull { query -> runCatching { query() }.getOrNull()?.trim()?.uppercase() }
        .firstOrNull { MAC.matches(it) && it != "02:00:00:00:00:00" }
    bonded
      .firstOrNull { it.address == fromAdb }
      ?.let {
        return it.address
      }
    val candidates = bonded.sortedByDescending {
      it.bluetoothClass?.majorDeviceClass == BluetoothClass.Device.Major.WEARABLE
    }
    for (device in candidates) {
      if (answersWithKey(device, keyId)) return device.address
    }
    throw SetupFailure(SetupProblem.NOT_BONDED, fromAdb)
  }

  /** Ob auf [device] Myzel Wear lauscht und den Schlüssel [keyId] hat. */
  @SuppressLint("MissingPermission")
  private suspend fun answersWithKey(device: BluetoothDevice, keyId: String): Boolean {
    val socket =
      runCatching { runInterruptible(Dispatchers.IO) { device.connectLink() } }.getOrNull()
        ?: return false
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    return try {
      LinkClient(socket.toLinkConnection(), scope).use { client ->
        client.hello(phoneName(), keys = { id -> if (id == keyId) LinkKeyStore.get(id) else null })
        runCatching { client.bye() }
        true
      }
    } catch (_: Exception) {
      false
    } finally {
      socket.close()
      scope.cancel()
    }
  }

  private companion object {
    val MAC = Regex("([0-9A-F]{2}:){5}[0-9A-F]{2}")

    /** Die eigene Adresse in `dumpsys bluetooth_manager` (Abschnitt „Bluetooth Status“). */
    val OWN_ADDRESS = Regex("""^\s*address:\s*(\S+)""", RegexOption.MULTILINE)
  }
}
