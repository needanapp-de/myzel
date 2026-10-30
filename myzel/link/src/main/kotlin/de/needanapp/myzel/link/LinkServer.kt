package de.needanapp.myzel.link

import java.io.EOFException
import java.io.OutputStream

/** Größte Datenmenge eines [Offer]; größere Angebote beenden die Verbindung. */
const val MAX_OFFER_SIZE = 512L * 1024 * 1024

/**
 * Die Seite der Uhr: meldet das Handy an ([Hello]), beantwortet [Ping] und [AppQuery] und nimmt die
 * Daten eines [Offer] entgegen. Was mit den Daten geschieht, entscheidet der [Listener], ebenso
 * über ein [Uninstall].
 *
 * @param key der Schlüssel aus der Einrichtung; ohne ihn nimmt die Uhr nichts an
 * @param sdk API-Stufe der Uhr, für das Handy ([Hello.sdk])
 * @param appVersionCode versionCode der Uhr-App ([Hello.appVersionCode])
 * @param appSha256 SHA-256 der APK der Uhr-App ([Hello.appSha256])
 * @param protocol die Version, die die Uhr spricht; nur Tests stellen damit eine ältere Uhr nach
 */
class LinkServer(
  private val device: String,
  private val key: LinkKey?,
  private val listener: Listener,
  private val sdk: Int = 0,
  private val appVersionCode: Long = 0,
  private val appSha256: String = "",
  private val protocol: Int = PROTOCOL_VERSION,
) {

  interface Listener {
    fun onHello(peer: Hello) {}

    /** Ergebnis der Anmeldung von [peer]; bei [ok] = false endet die Verbindung. */
    fun onAuth(peer: Hello, ok: Boolean) {}

    fun onPing() {}

    /** Wohin die Daten von [offer] gehen; null verwirft sie nach der Prüfung. */
    fun sinkFor(offer: Offer): OutputStream? = null

    /** Die Daten von [offer] sind da, [result] ist schon an das Handy unterwegs. */
    fun onData(offer: Offer, result: Result, connection: LinkConnection) {}

    /** Der Stand von [packageNames] auf der Uhr, in derselben Reihenfolge. */
    fun appStates(packageNames: List<String>): List<AppState> = packageNames.map {
      AppState(it, installed = false)
    }

    /** Das Handy möchte [packageName] entfernen; den Stand bekommt es als [Install]. */
    fun onUninstall(packageName: String, connection: LinkConnection) {
      connection.send(Install(InstallStatus.FAILURE, "Entfernen nicht möglich"))
    }
  }

  /**
   * Bedient [connection], bis die Gegenseite [Bye] schickt oder die Verbindung schließt. Ein
   * Verstoß gegen das Protokoll endet mit [LinkException], eine gescheiterte Anmeldung mit
   * [LinkAuthException].
   */
  fun serve(connection: LinkConnection) {
    if (!authenticate(connection)) return
    while (true) {
      val message = connection.receiveOrNull() ?: return
      when (message) {
        is Ping -> {
          connection.send(Pong(message.stamp))
          listener.onPing()
        }
        is Offer -> receive(connection, message)
        is AppQuery -> connection.send(AppStates(listener.appStates(message.packageNames)))
        is Uninstall -> listener.onUninstall(message.packageName, connection)
        Bye -> return
        else -> throw LinkException("Unerwartete Nachricht $message")
      }
    }
  }

  /** Die Anmeldung (siehe [Hello]); false, wenn das Handy vorher auflegt. */
  private fun authenticate(connection: LinkConnection): Boolean {
    val hello = connection.receiveOrNull() ?: return false
    if (hello !is Hello) throw LinkAuthException("Nicht angemeldet: ${hello.javaClass.simpleName}")
    listener.onHello(hello)
    val nonce = randomHex(NONCE_SIZE)
    if (hello.protocol != protocol) {
      connection.send(ownHello(nonce))
      throw LinkAuthException(
        "Handy spricht Protokoll ${hello.protocol}, Uhr $protocol",
        LinkAuthException.Reason.VERSION,
      )
    }
    if (!NONCE_PATTERN.matches(hello.nonce))
      throw LinkAuthException("Ungültige Zufallszahl vom Handy")
    if (key == null) {
      // Ohne Schlüssel gibt es nichts weiter; das Handy legt auf
      connection.send(ownHello(nonce))
      val next = connection.receiveOrNull() ?: return false
      if (next == Bye) return false
      throw LinkAuthException("Nicht eingerichtet, kein Schlüssel")
    }
    connection.send(
      ownHello(nonce)
        .copy(keyId = key.id, proof = key.proof(ROLE_WATCH, hello.nonce, nonce, protocol))
    )
    val auth = connection.receiveOrNull() ?: return false
    if (auth == Bye) return false
    if (auth !is Auth) throw LinkAuthException("Nicht angemeldet: ${auth.javaClass.simpleName}")
    val ok = key.verify(ROLE_PHONE, hello.nonce, nonce, protocol, auth.proof)
    connection.send(AuthResult(ok, if (ok) "Angemeldet" else "Schlüssel stimmt nicht"))
    listener.onAuth(hello, ok)
    if (!ok) throw LinkAuthException("Handy weist sich nicht aus, Schlüssel stimmt nicht")
    return true
  }

  /** Die eigene Begrüßung, noch ohne Schlüssel. */
  private fun ownHello(nonce: String) =
    Hello(
      protocol,
      device,
      nonce,
      sdk = sdk,
      appVersionCode = appVersionCode,
      appSha256 = appSha256,
    )

  private fun receive(connection: LinkConnection, offer: Offer) {
    // Die Rohdaten folgen in jedem Fall; ohne gültige Größe lässt sich das Ende nicht finden
    if (offer.size !in 0..MAX_OFFER_SIZE)
      throw LinkException("Angebot zu groß: ${offer.size} Bytes")
    val valid = SHA256_PATTERN.matches(offer.sha256)
    val start = System.nanoTime()
    val sink = if (valid) listener.sinkFor(offer) else null
    val measured =
      try {
        connection.receiveData(offer.size, sink)
      } finally {
        sink?.close()
      }
    val millis = (System.nanoTime() - start) / 1_000_000
    val ok = valid && measured == offer.sha256
    val text =
      when {
        !valid -> "Ungültiger SHA-256 im Angebot"
        ok -> "SHA-256 stimmt"
        else -> "SHA-256 stimmt nicht"
      }
    val result = Result(ok, measured, millis, text)
    connection.send(result)
    listener.onData(offer, result, connection)
  }
}

/** Die nächste Nachricht, oder null, wenn die Gegenseite die Verbindung geschlossen hat. */
private fun LinkConnection.receiveOrNull(): Message? =
  try {
    receive()
  } catch (_: EOFException) {
    null
  }
