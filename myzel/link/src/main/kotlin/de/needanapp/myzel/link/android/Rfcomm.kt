package de.needanapp.myzel.link.android

import android.Manifest.permission.BLUETOOTH_CONNECT
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothServerSocket
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.os.Build.VERSION.SDK_INT
import de.needanapp.myzel.link.LinkConnection
import java.io.Closeable
import java.util.UUID

/**
 * Kennung des Dienstes im SDP-Verzeichnis der Uhr; Handy und Uhr müssen dieselbe nutzen. Der
 * Prototyp (myzel-link-prototype) hat eine andere, damit beide Uhr-Apps nebeneinander lauschen
 * können.
 */
val MYZEL_LINK_UUID: UUID = UUID.fromString("a95286dd-f392-4355-8332-3bff728a9244")

/** Name des Dienstes im SDP-Verzeichnis, nur zur Anzeige. */
const val MYZEL_LINK_NAME = "Myzel Link"

/** Die Rechte, die Myzel Link zur Laufzeit braucht. */
val linkPermissions: Array<String> = if (SDK_INT >= 31) arrayOf(BLUETOOTH_CONNECT) else emptyArray()

fun Context.hasLinkPermissions(): Boolean = linkPermissions.all {
  checkSelfPermission(it) == PERMISSION_GRANTED
}

fun Context.bluetoothAdapter(): BluetoothAdapter? =
  getSystemService(BluetoothManager::class.java)?.adapter

/**
 * Lauscht auf der Uhr auf Verbindungen. Der Kanal ist verschlüsselt und nimmt nur gekoppelte Geräte
 * an ([BluetoothAdapter.listenUsingRfcommWithServiceRecord]).
 */
@SuppressLint("MissingPermission")
class RfcommServer(adapter: BluetoothAdapter) : Closeable {
  private val socket: BluetoothServerSocket =
    adapter.listenUsingRfcommWithServiceRecord(MYZEL_LINK_NAME, MYZEL_LINK_UUID)

  /** Wartet auf die nächste Verbindung; [close] bricht das Warten ab. */
  fun accept(): BluetoothSocket = socket.accept()

  override fun close() = socket.close()
}

/**
 * Verbindet das Handy mit Myzel Link auf [this]. Sucht den Kanal über SDP; schlägt fehl, wenn die
 * Uhr-App gerade nicht lauscht. Blockiert, bis die Verbindung steht.
 */
@SuppressLint("MissingPermission")
fun BluetoothDevice.connectLink(): BluetoothSocket {
  val socket = createRfcommSocketToServiceRecord(MYZEL_LINK_UUID)
  try {
    socket.connect()
  } catch (e: Exception) {
    socket.close()
    throw e
  }
  return socket
}

fun BluetoothSocket.toLinkConnection(): LinkConnection =
  LinkConnection(inputStream, outputStream, onClose = { close() })
