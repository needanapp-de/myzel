package de.needanapp.myzel.link

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.security.MessageDigest

/** Puffer für Rohdaten; RFCOMM liefert ohnehin nur ein paar hundert Bytes je Paket. */
private const val BUFFER_SIZE = 64 * 1024

/**
 * Eine Verbindung von Myzel Link über zwei beliebige Datenströme, etwa die eines RFCOMM-Sockets.
 * Senden ist threadsicher, Empfangen gehört einem einzigen Leser.
 */
class LinkConnection(
  private val rawInput: InputStream,
  private val rawOutput: OutputStream,
  private val onClose: () -> Unit = {},
) : Closeable {
  private val input = DataInputStream(BufferedInputStream(rawInput, BUFFER_SIZE))
  private val output = DataOutputStream(BufferedOutputStream(rawOutput, BUFFER_SIZE))
  private val sendLock = Any()

  /** Gesendete und empfangene Rohdaten, für die Messung. */
  @Volatile
  var bytesSent = 0L
    private set

  @Volatile
  var bytesReceived = 0L
    private set

  fun send(message: Message) {
    synchronized(sendLock) {
      Frames.write(output, message)
      output.flush()
    }
  }

  /** Liest die nächste Nachricht; [EOFException], wenn die Gegenseite die Verbindung schloss. */
  fun receive(): Message = Frames.read(input)

  /**
   * Sendet [offer] und danach genau [Offer.size] Bytes aus [source]. Der SHA-256 im Angebot muss
   * vorher berechnet sein ([sha256Of]); hier wird nur übertragen.
   */
  fun sendOffer(offer: Offer, source: InputStream, onProgress: (Long) -> Unit = {}) {
    synchronized(sendLock) {
      Frames.write(output, offer)
      val buffer = ByteArray(BUFFER_SIZE)
      var left = offer.size
      while (left > 0) {
        val n = source.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
        if (n < 0) throw LinkException("Quelle endet $left Bytes zu früh")
        output.write(buffer, 0, n)
        left -= n
        bytesSent += n
        onProgress(offer.size - left)
      }
      output.flush()
    }
  }

  /**
   * Liest die [size] Bytes Rohdaten nach einem [Offer], schreibt sie nach [sink] (oder verwirft
   * sie) und gibt ihren SHA-256 zurück.
   */
  fun receiveData(size: Long, sink: OutputStream?): String {
    val digest = MessageDigest.getInstance("SHA-256")
    val buffer = ByteArray(BUFFER_SIZE)
    var left = size
    while (left > 0) {
      val n = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
      if (n < 0) throw EOFException("Verbindung endet $left Bytes vor dem Ende der Daten")
      digest.update(buffer, 0, n)
      sink?.write(buffer, 0, n)
      left -= n
      bytesReceived += n
    }
    sink?.flush()
    return digest.digest().toHex()
  }

  /**
   * Schließt die Verbindung, auch während ein anderer Thread beim Senden blockiert: Die Puffer
   * bleiben außen vor, ihr close() wollte erst den Rest senden und hinge selbst.
   */
  override fun close() {
    for (step in listOf(onClose, rawOutput::close, rawInput::close)) {
      try {
        step()
      } catch (_: Exception) {}
    }
  }
}

/** SHA-256 eines Datenstroms, kleingeschrieben hexadezimal. */
fun sha256Of(source: InputStream): String {
  val digest = MessageDigest.getInstance("SHA-256")
  val buffer = ByteArray(BUFFER_SIZE)
  while (true) {
    val n = source.read(buffer)
    if (n < 0) break
    digest.update(buffer, 0, n)
  }
  return digest.digest().toHex()
}

/** Ein gültiger SHA-256 in der Schreibweise von Myzel Link. */
internal val SHA256_PATTERN = Regex("[0-9a-f]{64}")
