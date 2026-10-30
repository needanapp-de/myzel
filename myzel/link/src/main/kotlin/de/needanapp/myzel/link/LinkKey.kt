package de.needanapp.myzel.link

import java.security.Key
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Mac

/** Länge des gemeinsamen Schlüssels in Bytes. */
const val LINK_KEY_SIZE = 32

/** Länge der Zufallszahl, die jede Seite bei der Anmeldung beisteuert. */
const val NONCE_SIZE = 16

/** Die Anmeldung ist gescheitert, aus dem Grund [reason]. */
/** @param peerProtocol bei [Reason.VERSION] die Version der Gegenseite aus ihrem [Hello] */
class LinkAuthException(
  message: String,
  val reason: Reason = Reason.KEY,
  val peerProtocol: Int = 0,
) : java.io.IOException(message) {
  enum class Reason {
    /** Die Gegenseite spricht eine andere Version des Protokolls. */
    VERSION,

    /** Die Uhr hat keinen Schlüssel, ist also nicht eingerichtet. */
    NO_KEY,

    /** Der Schlüssel ist unbekannt oder der Nachweis stimmt nicht. */
    KEY,
  }
}

/**
 * Gemeinsamer Schlüssel von Handy und Uhr (HMAC-SHA256). Das Handy erzeugt ihn bei der Einrichtung
 * und übergibt ihn per ADB; danach weist sich jede Seite bei jeder Verbindung damit aus. [key] kann
 * im Android-Keystore liegen und das Gerät nie verlassen.
 *
 * @param id Kennung des Schlüssels, hexadezimal; die Uhr nennt sie, damit das Handy weiß, welchen
 *   Schlüssel es nehmen muss
 */
class LinkKey(val id: String, private val key: Key) {
  fun mac(data: ByteArray): ByteArray =
    Mac.getInstance("HmacSHA256").run {
      init(key)
      doFinal(data)
    }

  /**
   * Nachweis einer Seite: [role] trennt Uhr und Handy, damit keine den Nachweis der anderen
   * wiederholen kann. [protocol] ist die Version, die beide für diese Verbindung sprechen, nicht
   * unbedingt [PROTOCOL_VERSION]: Ein neueres Handy spricht beim Aktualisieren die der Uhr.
   */
  internal fun proof(role: String, phoneNonce: String, watchNonce: String, protocol: Int): String =
    mac("myzel-link/$protocol $role $id $phoneNonce $watchNonce".toByteArray()).toHex()

  internal fun verify(
    role: String,
    phoneNonce: String,
    watchNonce: String,
    protocol: Int,
    proof: String,
  ): Boolean =
    MessageDigest.isEqual(
      proof(role, phoneNonce, watchNonce, protocol).toByteArray(),
      proof.toByteArray(),
    )

  companion object {
    /** Erzeugt Kennung und Geheimnis für einen neuen Schlüssel. */
    fun generate(): Pair<String, ByteArray> =
      randomHex(8) to ByteArray(LINK_KEY_SIZE).also { random.nextBytes(it) }
  }
}

/** Eine gültige Zufallszahl der Anmeldung. */
internal val NONCE_PATTERN = Regex("[0-9a-f]{${NONCE_SIZE * 2}}")

internal const val ROLE_WATCH = "watch"
internal const val ROLE_PHONE = "phone"

private val random = SecureRandom()

internal fun randomHex(bytes: Int): String = ByteArray(bytes).also { random.nextBytes(it) }.toHex()

fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

fun String.hexToBytes(): ByteArray {
  require(length % 2 == 0 && all { it in '0'..'9' || it in 'a'..'f' }) { "Keine Hexzahl" }
  return chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
