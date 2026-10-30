package de.needanapp.myzel.watch.setup

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build.VERSION.SDK_INT
import de.needanapp.myzel.watch.Endpoint
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.Executors
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import mu.KotlinLogging

private const val PAIRING = "_adb-tls-pairing._tcp"
private const val CONNECT = "_adb-tls-connect._tcp"

/**
 * Ein Gerät mit „Debugging über WLAN“: Kopplungsdienst (nur bei offenem Kopplungsdialog) und
 * Verbindungsdienst. [remembered]: Der Verbindungsdienst stammt nicht aus dem mDNS, sondern von der
 * letzten erfolgreichen Verbindung.
 */
data class AdbDevice(
  val name: String,
  val pairing: Endpoint? = null,
  val connect: Endpoint? = null,
  val remembered: Boolean = false,
) {
  /** Seriennummer aus dem Dienstnamen „adb-<Seriennummer>-<Zufall>“. */
  val serial: String
    get() = name.removePrefix("adb-").substringBeforeLast('-')
}

/**
 * Sucht mit dem NsdManager nach Geräten mit „Debugging über WLAN“ im lokalen Netz. Die Suche von
 * libadb-android findet nur das eigene Gerät.
 */
class AdbDiscovery(context: Context) {
  private val log = KotlinLogging.logger {}
  private val nsd = context.getSystemService(NsdManager::class.java)
  private val executor = Executors.newSingleThreadExecutor()
  private val discoveries = mutableListOf<NsdManager.DiscoveryListener>()
  private val callbacks = mutableListOf<NsdManager.ServiceInfoCallback>()
  private val _devices = MutableStateFlow<Map<String, AdbDevice>>(emptyMap())
  val devices = _devices.asStateFlow()

  fun start() {
    stop()
    for (type in listOf(PAIRING, CONNECT)) {
      val listener = discoveryListener(type)
      nsd.discoverServices(type, NsdManager.PROTOCOL_DNS_SD, listener)
      discoveries += listener
    }
  }

  fun stop() {
    discoveries.forEach { runCatching { nsd.stopServiceDiscovery(it) } }
    discoveries.clear()
    if (SDK_INT >= 34) callbacks.forEach { runCatching { nsd.unregisterServiceInfoCallback(it) } }
    callbacks.clear()
  }

  private fun discoveryListener(type: String) =
    object : NsdManager.DiscoveryListener {
      override fun onServiceFound(info: NsdServiceInfo) = resolve(info, type)

      override fun onServiceLost(info: NsdServiceInfo) = update(info.serviceName, type, null)

      override fun onDiscoveryStarted(serviceType: String) {
        log.info { "mDNS-Suche nach $serviceType läuft" }
      }

      override fun onDiscoveryStopped(serviceType: String) {}

      override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) {
        log.warn { "mDNS-Suche nach $serviceType nicht gestartet: $errorCode" }
      }

      override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) {}
    }

  @Suppress("DEPRECATION")
  private fun resolve(info: NsdServiceInfo, type: String) {
    if (SDK_INT >= 34) {
      val callback =
        object : NsdManager.ServiceInfoCallback {
          override fun onServiceUpdated(resolved: NsdServiceInfo) {
            update(resolved.serviceName, type, endpoint(resolved))
          }

          override fun onServiceLost() = update(info.serviceName, type, null)

          override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) {}

          override fun onServiceInfoCallbackUnregistered() {}
        }
      nsd.registerServiceInfoCallback(info, executor, callback)
      callbacks += callback
    } else {
      nsd.resolveService(
        info,
        object : NsdManager.ResolveListener {
          override fun onServiceResolved(resolved: NsdServiceInfo) =
            update(resolved.serviceName, type, endpoint(resolved))

          override fun onResolveFailed(failed: NsdServiceInfo, errorCode: Int) {}
        },
      )
    }
  }

  @Suppress("DEPRECATION")
  private fun endpoint(info: NsdServiceInfo): Endpoint? {
    val addresses: List<InetAddress> =
      if (SDK_INT >= 34) info.hostAddresses else listOfNotNull(info.host)
    val address =
      addresses.firstOrNull { it is Inet4Address } ?: addresses.firstOrNull() ?: return null
    return Endpoint(address.hostAddress ?: return null, info.port)
  }

  private fun update(name: String, type: String, endpoint: Endpoint?) {
    _devices.update { all ->
      val device = all[name] ?: AdbDevice(name)
      val changed =
        if (type == PAIRING) device.copy(pairing = endpoint) else device.copy(connect = endpoint)
      if (changed.pairing == null && changed.connect == null) all - name
      else all + (name to changed)
    }
  }
}
