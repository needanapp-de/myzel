package de.needanapp.myzel.link

import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.atomic.AtomicLong
import kotlin.reflect.KClass
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeoutOrNull

/** So lange wartet das Handy höchstens auf eine Antwort der Uhr. */
const val ANSWER_TIMEOUT_MILLIS = 10_000L

/**
 * Nimmt die Uhr so lange keine Daten mehr an, gilt die Verbindung als tot. Wear OS friert eine App
 * ein, die nicht mehr im Vordergrund ist; das Senden bliebe dann für immer hängen.
 */
const val STALL_TIMEOUT_MILLIS = 15_000L

/**
 * Die Seite des Handys: schickt Anfragen und wartet auf die Antworten der Uhr. Ein Leser im
 * Hintergrund nimmt alles an; was gerade nicht erwartet wird (etwa ein [Install] während eines
 * Pings), bleibt liegen, bis es gebraucht wird. Die Aufrufe dürfen nicht gleichzeitig laufen.
 */
class LinkClient(private val connection: LinkConnection, scope: CoroutineScope) : Closeable {
  private val incoming = Channel<Message>(Channel.UNLIMITED)
  private val pending = ArrayDeque<Message>()

  init {
    scope.launch(Dispatchers.IO) {
      try {
        while (isActive) incoming.send(connection.receive())
      } catch (e: Exception) {
        incoming.close(e)
      }
    }
  }

  /**
   * Begrüßung und Anmeldung (siehe [Hello]). [keys] liefert den Schlüssel zu der Kennung, die die
   * Uhr nennt. Gibt die Begrüßung der Uhr zurück; scheitert die Anmeldung, folgt
   * [LinkAuthException] und die Verbindung ist danach nicht mehr zu gebrauchen. Spricht die Uhr
   * eine andere Version, nennt die Ausnahme sie ([LinkAuthException.peerProtocol]); mit [protocol]
   * = dieser Version taugt eine neue Verbindung dann für die Kernnachrichten (siehe
   * [PROTOCOL_VERSION]), etwa um Myzel Wear zu aktualisieren.
   */
  suspend fun hello(
    device: String,
    keys: (String) -> LinkKey?,
    sdk: Int = 0,
    appVersionCode: Long = 0,
    protocol: Int = PROTOCOL_VERSION,
  ): Hello {
    val nonce = randomHex(NONCE_SIZE)
    send(Hello(protocol, device, nonce, sdk = sdk, appVersionCode = appVersionCode))
    val watch = expect(Hello::class, ANSWER_TIMEOUT_MILLIS)
    if (watch.protocol != protocol) {
      refuse(
        "Uhr spricht Protokoll ${watch.protocol}, Handy $protocol",
        LinkAuthException.Reason.VERSION,
        watch.protocol,
      )
    }
    if (watch.keyId.isEmpty()) {
      refuse("Uhr ist nicht eingerichtet, kein Schlüssel", LinkAuthException.Reason.NO_KEY)
    }
    val key =
      keys(watch.keyId)
        ?: refuse("Uhr wurde mit einem anderen Handy eingerichtet (Schlüssel ${watch.keyId})")
    if (
      !NONCE_PATTERN.matches(watch.nonce) ||
        !key.verify(ROLE_WATCH, nonce, watch.nonce, protocol, watch.proof)
    ) {
      refuse("Uhr weist sich nicht aus, Nachweis falsch")
    }
    send(Auth(key.proof(ROLE_PHONE, nonce, watch.nonce, protocol)))
    val result = expect(AuthResult::class, ANSWER_TIMEOUT_MILLIS)
    if (!result.ok) throw LinkAuthException("Uhr lehnt ab: ${result.message}")
    return watch
  }

  /** Legt nach einer gescheiterten Anmeldung auf. */
  private suspend fun refuse(
    message: String,
    reason: LinkAuthException.Reason = LinkAuthException.Reason.KEY,
    peerProtocol: Int = 0,
  ): Nothing {
    runCatching { send(Bye) }
    throw LinkAuthException(message, reason, peerProtocol)
  }

  private suspend fun send(message: Message) =
    runInterruptible(Dispatchers.IO) { connection.send(message) }

  /** Ein Ping; gibt die Umlaufzeit in Nanosekunden zurück. */
  suspend fun ping(timeoutMillis: Long = ANSWER_TIMEOUT_MILLIS): Long {
    val stamp = System.nanoTime()
    runInterruptible(Dispatchers.IO) { connection.send(Ping(stamp)) }
    while (true) {
      val pong = expect(Pong::class, timeoutMillis)
      if (pong.stamp == stamp) return System.nanoTime() - stamp
    }
  }

  /**
   * Schickt [offer] und die Daten aus [source] und wartet auf das [Result] der Uhr.
   *
   * @param onProgress gesendete Bytes, aus dem Hintergrund
   */
  suspend fun offer(
    offer: Offer,
    source: InputStream,
    resultTimeoutMillis: Long = 60_000,
    stallMillis: Long = STALL_TIMEOUT_MILLIS,
    onProgress: (Long) -> Unit = {},
  ): Result = coroutineScope {
    // Ein blockiertes Schreiben auf einen Bluetooth-Socket lässt sich nicht unterbrechen, nur
    // durch Schließen der Verbindung beenden
    val lastProgress = AtomicLong(System.nanoTime())
    var stalled = false
    val watchdog = launch {
      while (true) {
        delay(minOf(stallMillis, 1_000))
        if ((System.nanoTime() - lastProgress.get()) / 1_000_000 > stallMillis) {
          stalled = true
          connection.close()
          break
        }
      }
    }
    try {
      runInterruptible(Dispatchers.IO) {
        connection.sendOffer(offer, source) { sent ->
          lastProgress.set(System.nanoTime())
          onProgress(sent)
        }
      }
    } catch (e: IOException) {
      if (stalled)
        throw LinkException(
          "Uhr nimmt seit ${stallMillis / 1000} s keine Daten an (App eingefroren?)",
          e,
        )
      throw e
    } finally {
      watchdog.cancel()
    }
    expect(Result::class, resultTimeoutMillis)
  }

  /** Der nächste Stand einer Installation oder Deinstallation auf der Uhr. */
  suspend fun nextInstall(timeoutMillis: Long): Install = expect(Install::class, timeoutMillis)

  /** Der Stand von [packageNames] auf der Uhr, in derselben Reihenfolge. */
  suspend fun appStates(packageNames: List<String>): List<AppState> {
    send(AppQuery(packageNames))
    return expect(AppStates::class, ANSWER_TIMEOUT_MILLIS).apps
  }

  /** Bittet die Uhr, [packageName] zu entfernen; den Stand liefert danach [nextInstall]. */
  suspend fun uninstall(packageName: String) = send(Uninstall(packageName))

  /** Verabschiedet sich und schließt die Verbindung. */
  suspend fun bye() {
    try {
      runInterruptible(Dispatchers.IO) { connection.send(Bye) }
    } finally {
      close()
    }
  }

  override fun close() = connection.close()

  @Suppress("UNCHECKED_CAST")
  private suspend fun <T : Message> expect(type: KClass<T>, timeoutMillis: Long): T {
    pending
      .firstOrNull { type.isInstance(it) }
      ?.let {
        pending.remove(it)
        return it as T
      }
    // keine TimeoutCancellationException: Sie sähe für den Aufrufer wie ein Abbruch aus
    return withTimeoutOrNull(timeoutMillis) {
      var found: T? = null
      while (found == null) {
        val message = incoming.receive()
        if (type.isInstance(message)) found = message as T else pending.addLast(message)
      }
      found
    }
      ?: throw LinkException(
        "Keine Antwort der Uhr (${type.simpleName}) nach ${timeoutMillis / 1000} s"
      )
  }
}
