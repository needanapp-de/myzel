package de.needanapp.myzel.watch

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import de.needanapp.myzel.link.android.LinkKeyStore
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import mu.KotlinLogging

/**
 * Die eingerichtete Uhr.
 *
 * @param name Hersteller und Modell, etwa „Google Pixel Watch 2“
 * @param serial ADB-Seriennummer der Uhr
 * @param bluetoothAddress Adresse der Uhr in der Bluetooth-Kopplung, für Myzel Link
 * @param keyId Kennung des gemeinsamen Schlüssels; der Schlüssel liegt im Android-Keystore
 * @param wearPackage Paketname von Myzel Wear auf der Uhr
 * @param sdk API-Stufe der Uhr, zuletzt gemeldet
 * @param wearVersionCode versionCode von Myzel Wear, zuletzt gemeldet
 */
@Serializable
data class PairedWatch(
  val name: String,
  val serial: String,
  val bluetoothAddress: String,
  val keyId: String,
  val wearPackage: String,
  val sdk: Int,
  val wearVersionCode: Long,
)

/** Ein ADB-Dienst der Uhr im lokalen Netz. */
@Serializable data class Endpoint(val host: String, val port: Int)

/**
 * Ein Gerät mit „Debugging über WLAN“, das sich bei der Einrichtung als keine Uhr zeigte oder das
 * der Nutzer dort ausgeblendet hat; die Einrichtung zeigt es nicht, bis es in den Einstellungen
 * wieder eingeblendet wird.
 *
 * @param serial ADB-Seriennummer, aus dem mDNS-Namen „adb-<Seriennummer>-<Zufall>“
 * @param name Hersteller und Modell, etwa „Google Pixel 8“; leer, wenn das Gerät von Hand
 *   ausgeblendet wurde, ohne dass Myzel sich verbinden konnte
 */
@Serializable data class HiddenDevice(val serial: String, val name: String)

@Serializable
private data class StoredWatch(
  val watch: PairedWatch? = null,
  /** zuletzt erfolgreiche Verbindungsdienste je mDNS-Name, siehe [WatchStore.rememberAdb] */
  val adb: Map<String, Endpoint> = emptyMap(),
  /** zuletzt bekannter Stand der Apps auf der Uhr, siehe [WatchStore.saveApps] */
  val apps: Map<String, WatchPackage> = emptyMap(),
  /** wann Myzel zuletzt alle Uhr-Apps abgefragt hat; 0: noch nie */
  val appsCheckedAt: Long = 0,
  /** nach einer Installation auf dem Handy fragen, ob die App auch auf die Uhr soll */
  val askAfterInstall: Boolean = true,
  /** Geräte, die keine Uhr sind, siehe [WatchStore.hideDevice] */
  val hidden: List<HiddenDevice> = emptyList(),
)

/**
 * Speichert die eingerichtete Uhr in `no_backup/myzel/watch.json`: Eine Sicherung auf ein anderes
 * Handy nähme den Schlüssel aus dem Keystore nicht mit, die Uhr müsste ohnehin neu eingerichtet
 * werden.
 */
@Singleton
class WatchStore @Inject constructor(@ApplicationContext context: Context) {
  private val log = KotlinLogging.logger {}
  private val file = File(context.noBackupFilesDir, "myzel/watch.json")
  private val json = Json { ignoreUnknownKeys = true }
  private val stored = MutableStateFlow(read())

  private val _watch = MutableStateFlow(stored.value.watch)

  /** Die eingerichtete Uhr, null ohne. */
  val watch: StateFlow<PairedWatch?> = _watch.asStateFlow()

  private val _apps = MutableStateFlow(stored.value.apps)

  /** Was zuletzt über die Apps auf der Uhr bekannt wurde, je Paketname; ohne Eintrag nichts. */
  val apps: StateFlow<Map<String, WatchPackage>> = _apps.asStateFlow()

  private val _appsCheckedAt = MutableStateFlow(stored.value.appsCheckedAt)

  /** Wann Myzel zuletzt alle Uhr-Apps abgefragt hat; 0: noch nie. */
  val appsCheckedAt: StateFlow<Long> = _appsCheckedAt.asStateFlow()

  private val _askAfterInstall = MutableStateFlow(stored.value.askAfterInstall)

  /** Ob Myzel nach einer Installation auf dem Handy fragt, ob die App auch auf die Uhr soll. */
  val askAfterInstall: StateFlow<Boolean> = _askAfterInstall.asStateFlow()

  fun setAskAfterInstall(ask: Boolean) = write { it.copy(askAfterInstall = ask) }

  private val _hiddenDevices = MutableStateFlow(stored.value.hidden)

  /** Geräte, die die Einrichtung nicht zeigt, weil sie keine Uhr sind. */
  val hiddenDevices: StateFlow<List<HiddenDevice>> = _hiddenDevices.asStateFlow()

  /**
   * Blendet [device] in der Einrichtung aus. Es bleibt ausgeblendet, auch wenn die Uhr vergessen
   * oder neu eingerichtet wird.
   */
  fun hideDevice(device: HiddenDevice) = write { s ->
    s.copy(hidden = s.hidden.filter { it.serial != device.serial } + device)
  }

  /** Zeigt das Gerät mit [serial] wieder in der Einrichtung. */
  fun showDevice(serial: String) = write { s ->
    s.copy(hidden = s.hidden.filter { it.serial != serial })
  }

  /** Zeigt alle ausgeblendeten Geräte wieder in der Einrichtung. */
  fun showAllDevices() = write { it.copy(hidden = emptyList()) }

  /** Die ADB-Dienste, mit denen sich das Handy zuletzt verbunden hat, je mDNS-Name. */
  val rememberedAdb: Map<String, Endpoint>
    get() = stored.value.adb

  /** Ordner für den ADB-Schlüssel des Handys (nicht in der Sicherung). */
  val adbKeyDir = File(context.noBackupFilesDir, "myzel/adb")

  fun save(watch: PairedWatch) {
    val old = stored.value.watch
    // ein früherer Schlüssel taugt nicht mehr, die Uhr kennt nur den neuen
    if (old != null && old.keyId != watch.keyId) LinkKeyStore.delete(old.keyId)
    // nach dem Neueinrichten derselben Uhr gilt der Stand ihrer Apps weiter, bei einer anderen
    // nicht
    val sameWatch = old?.bluetoothAddress == watch.bluetoothAddress
    write {
      if (sameWatch) it.copy(watch = watch)
      else it.copy(watch = watch, apps = emptyMap(), appsCheckedAt = 0)
    }
  }

  /** Merkt sich, was die Uhr bei der Anmeldung über sich sagt. */
  fun updateWatch(sdk: Int, wearVersionCode: Long) {
    val watch = stored.value.watch ?: return
    if (watch.sdk == sdk && watch.wearVersionCode == wearVersionCode) return
    write { it.copy(watch = watch.copy(sdk = sdk, wearVersionCode = wearVersionCode)) }
  }

  /** Vergisst die Uhr samt Schlüssel; danach nimmt sie nichts mehr von diesem Handy an. */
  fun forget() {
    stored.value.watch?.let { LinkKeyStore.delete(it.keyId) }
    write { it.copy(watch = null, apps = emptyMap(), appsCheckedAt = 0) }
  }

  /**
   * Merkt sich den Stand von [apps] auf der Uhr. [complete] heißt: Das waren alle Uhr-Apps aus den
   * Paketquellen; dann gilt jetzt als Zeitpunkt der letzten Abfrage.
   */
  fun saveApps(apps: List<WatchPackage>, complete: Boolean = false) {
    if (apps.isEmpty() && !complete) return
    write { s ->
      s.copy(
        apps = s.apps + apps.associateBy { it.packageName },
        appsCheckedAt = if (complete) System.currentTimeMillis() else s.appsCheckedAt,
      )
    }
  }

  /**
   * Merkt sich den ADB-Dienst [endpoint] von [name]. Die Uhr beantwortet mDNS-Anfragen im
   * Ruhezustand oft nicht, obwohl sie erreichbar und Debugging über WLAN an ist (Pixel Watch 2).
   */
  fun rememberAdb(name: String, endpoint: Endpoint) = write {
    it.copy(adb = it.adb + (name to endpoint))
  }

  /** Vergisst den ADB-Dienst von [name], etwa weil das Gerät keine Uhr ist. */
  fun forgetAdb(name: String) {
    if (name in stored.value.adb) write { it.copy(adb = it.adb - name) }
  }

  private fun write(change: (StoredWatch) -> StoredWatch) {
    stored.update(change)
    _watch.value = stored.value.watch
    _apps.value = stored.value.apps
    _appsCheckedAt.value = stored.value.appsCheckedAt
    _askAfterInstall.value = stored.value.askAfterInstall
    _hiddenDevices.value = stored.value.hidden
    try {
      file.parentFile?.mkdirs()
      val tmp = File(file.path + ".tmp")
      tmp.writeText(json.encodeToString(StoredWatch.serializer(), stored.value))
      tmp.renameTo(file)
    } catch (e: Exception) {
      log.warn(e) { "Could not write $file" }
    }
  }

  private fun read(): StoredWatch =
    try {
      if (file.exists()) json.decodeFromString(StoredWatch.serializer(), file.readText())
      else StoredWatch()
    } catch (e: Exception) {
      log.warn(e) { "Could not read $file" }
      StoredWatch()
    }
}
