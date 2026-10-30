package de.needanapp.myzel.link

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.nio.channels.Channels
import java.nio.channels.Pipe
import java.util.concurrent.CopyOnWriteArrayList
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Test

class LinkTest {
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val received = ByteArrayOutputStream()
  private val events = CopyOnWriteArrayList<String>()

  /** Was die Uhr mit einer Ausnahme beendet hat. */
  private val watchErrors = CopyOnWriteArrayList<Exception>()
  private var watchThread: Thread? = null

  /** Rohe Leitung vom Handy zur Uhr, am Protokoll vorbei. */
  private lateinit var phoneRaw: java.io.OutputStream

  private val key = testKey("00112233aabbccdd", seed = 1)
  private val phoneKeys: (String) -> LinkKey? = { if (it == key.id) key else null }

  @After fun tearDown() = scope.cancel()

  private companion object {
    val WATCH_APK_SHA256 = "5a".repeat(32)
  }

  private fun testKey(id: String, seed: Int) =
    LinkKey(id, SecretKeySpec(Random(seed).nextBytes(LINK_KEY_SIZE), "HmacSHA256"))

  /**
   * Handy und Uhr über zwei Rohre im Speicher; die Uhr läuft in einem eigenen Thread. Gibt die
   * Seite des Handys zurück, noch ohne Anmeldung.
   */
  private fun watch(
    watchKey: LinkKey? = key,
    listener: LinkServer.Listener = recordingListener(),
    protocol: Int = PROTOCOL_VERSION,
  ): LinkConnection {
    val toWatch = Pipe.open()
    val toPhone = Pipe.open()
    val watch =
      LinkConnection(
        Channels.newInputStream(toWatch.source()),
        Channels.newOutputStream(toPhone.sink()),
      )
    watchThread =
      Thread {
        try {
          watch.use {
            LinkServer("Uhr", watchKey, listener, 33, 21, WATCH_APK_SHA256, protocol).serve(it)
          }
        } catch (e: Exception) {
          watchErrors += e
        }
      }
        .apply { start() }
    phoneRaw = Channels.newOutputStream(toWatch.sink())
    return LinkConnection(Channels.newInputStream(toPhone.source()), phoneRaw)
  }

  private fun connect(watchKey: LinkKey? = key): LinkClient = LinkClient(watch(watchKey), scope)

  /** Verbunden und angemeldet. */
  private suspend fun connected(): LinkClient = connect().also { it.hello("Handy", phoneKeys) }

  /** Wartet, bis die Uhr die Verbindung beendet hat. */
  private fun watchDone() {
    watchThread!!.join(5_000)
    assertFalse(watchThread!!.isAlive, "Uhr hängt")
  }

  private fun recordingListener() =
    object : LinkServer.Listener {
      override fun onHello(peer: Hello) {
        events += "hello ${peer.device}"
      }

      override fun onAuth(peer: Hello, ok: Boolean) {
        events += "auth $ok"
      }

      override fun sinkFor(offer: Offer) = if (offer.kind == OfferKind.APK) received else null

      override fun onData(offer: Offer, result: Result, connection: LinkConnection) {
        events += "data ${offer.name} ${result.ok}"
        if (offer.kind == OfferKind.APK && result.ok) {
          connection.send(Install(InstallStatus.CONFIRM, "bitte bestätigen"))
          connection.send(Install(InstallStatus.SUCCESS, "installiert"))
        }
      }

      override fun appStates(packageNames: List<String>) = packageNames.map {
        if (it == "org.beispiel.app")
          AppState(it, true, 7, "1.0", listOf("ab".repeat(32)), "com.android.vending")
        else AppState(it, installed = false)
      }

      override fun onUninstall(packageName: String, connection: LinkConnection) {
        events += "uninstall $packageName"
        connection.send(Install(InstallStatus.CONFIRM, "bitte bestätigen"))
        connection.send(Install(InstallStatus.SUCCESS, "entfernt"))
      }
    }

  @Test
  fun helloAndPing() = runBlocking {
    val client = connect()
    val hello = client.hello("Handy", phoneKeys)
    assertEquals("Uhr", hello.device)
    assertEquals(key.id, hello.keyId)
    repeat(5) { assertTrue(client.ping() > 0) }
    client.bye()
    watchDone()
    assertEquals(listOf("hello Handy", "auth true"), events)
    assertEquals(emptyList(), watchErrors)
  }

  @Test
  fun watchWithoutKeyIsNotSetUp() = runBlocking {
    val e =
      assertFailsWith<LinkAuthException> { connect(watchKey = null).hello("Handy", phoneKeys) }
    assertTrue(e.message!!.contains("nicht eingerichtet"))
    watchDone()
    assertEquals(emptyList(), watchErrors)
  }

  @Test
  fun watchOfAnotherPhoneIsRefused() = runBlocking {
    val e =
      assertFailsWith<LinkAuthException> {
        connect(testKey("ffffffffffffffff", seed = 2)).hello("Handy", phoneKeys)
      }
    assertTrue(e.message!!.contains("anderen Handy"))
    watchDone()
    assertEquals(listOf("hello Handy"), events)
  }

  @Test
  fun impostorWatchIsRefused() = runBlocking {
    // gleiche Kennung, anderes Geheimnis: Die falsche Uhr kann sich nicht ausweisen
    val e =
      assertFailsWith<LinkAuthException> {
        connect(testKey(key.id, seed = 3)).hello("Handy", phoneKeys)
      }
    assertTrue(e.message!!.contains("weist sich nicht aus"))
    watchDone()
    assertEquals(listOf("hello Handy"), events)
  }

  @Test
  fun phoneWithWrongKeyIsRejected() {
    val phone = watch()
    val nonce = randomHex(NONCE_SIZE)
    phone.send(Hello(PROTOCOL_VERSION, "Fremd", nonce))
    val hello = assertIs<Hello>(phone.receive())
    val wrong = testKey(hello.keyId, seed = 4)
    phone.send(Auth(wrong.proof(ROLE_PHONE, nonce, hello.nonce, PROTOCOL_VERSION)))
    assertEquals(AuthResult(false, "Schlüssel stimmt nicht"), phone.receive())
    assertFailsWith<EOFException> { phone.receive() }
    watchDone()
    assertEquals(listOf("hello Fremd", "auth false"), events)
    assertIs<LinkAuthException>(watchErrors.single())
  }

  @Test
  fun watchProofCannotBeReturned() {
    // Den Nachweis der Uhr zurückschicken hilft nicht: Uhr und Handy rechnen mit verschiedenen
    // Rollen
    val phone = watch()
    val nonce = randomHex(NONCE_SIZE)
    phone.send(Hello(PROTOCOL_VERSION, "Fremd", nonce))
    val hello = assertIs<Hello>(phone.receive())
    phone.send(Auth(hello.proof))
    assertEquals(false, assertIs<AuthResult>(phone.receive()).ok)
    watchDone()
  }

  @Test
  fun replayedProofIsRejected() {
    // Ein mitgeschnittener Nachweis taugt nicht zweimal: Die Uhr würfelt jedes Mal neu
    val nonce = randomHex(NONCE_SIZE)
    val first = watch()
    first.send(Hello(PROTOCOL_VERSION, "Handy", nonce))
    val hello = assertIs<Hello>(first.receive())
    val recorded = Auth(key.proof(ROLE_PHONE, nonce, hello.nonce, PROTOCOL_VERSION))
    first.send(recorded)
    assertEquals(true, assertIs<AuthResult>(first.receive()).ok)
    first.send(Bye)
    watchDone()

    val second = watch()
    second.send(Hello(PROTOCOL_VERSION, "Fremd", nonce))
    second.receive()
    second.send(recorded)
    assertEquals(false, assertIs<AuthResult>(second.receive()).ok)
    watchDone()
  }

  @Test
  fun nothingIsAcceptedBeforeLogin() {
    val ping = watch()
    ping.send(Ping(1))
    assertFailsWith<EOFException> { ping.receive() }
    watchDone()

    // auch nicht nach dem Hello: Die Uhr verlangt den Nachweis
    val offer = watch()
    offer.send(Hello(PROTOCOL_VERSION, "Fremd", randomHex(NONCE_SIZE)))
    offer.receive()
    offer.send(Offer(OfferKind.APK, "boese", 1, 10, "0".repeat(64)))
    assertFailsWith<EOFException> { offer.receive() }
    watchDone()
    assertEquals(2, watchErrors.filterIsInstance<LinkAuthException>().size)
    assertEquals(0, received.size())
  }

  @Test
  fun helloNamesSdkAndVersion() = runBlocking {
    val hello = connect().hello("Handy", phoneKeys, sdk = 37, appVersionCode = 22)
    assertEquals(33, hello.sdk)
    assertEquals(21L, hello.appVersionCode)
    assertEquals(WATCH_APK_SHA256, hello.appSha256)
  }

  @Test
  fun phoneLearnsWatchProtocolAndSpeaksIt() = runBlocking {
    // eine Uhr mit einer älteren Version nennt sie dem Handy
    val oldWatch = MIN_UPDATE_PROTOCOL
    val e =
      assertFailsWith<LinkAuthException> {
        LinkClient(watch(protocol = oldWatch), scope).hello("Handy", phoneKeys)
      }
    assertEquals(LinkAuthException.Reason.VERSION, e.reason)
    assertEquals(oldWatch, e.peerProtocol)
    watchDone()
    // mit ihr klappen Anmeldung (der Nachweis enthält die gesprochene Version) und Angebot, wie
    // für das Aktualisieren von Myzel Wear
    val client = LinkClient(watch(protocol = oldWatch), scope)
    assertEquals(oldWatch, client.hello("Handy", phoneKeys, protocol = e.peerProtocol).protocol)
    val apk = Random(5).nextBytes(5_000)
    val offer =
      Offer(OfferKind.APK, "de.needanapp.myzel", 21, apk.size.toLong(), sha256Of(apk.inputStream()))
    assertTrue(client.offer(offer, ByteArrayInputStream(apk)).ok)
    client.bye()
  }

  @Test
  fun proofMatchesOlderBuilds() {
    // so rechneten Builds bis Protokoll 5 mit ihrer eigenen Version; eine Uhr mit Protokoll 4
    // erwartet genau diesen Nachweis
    val phoneNonce = "ab".repeat(16)
    val watchNonce = "cd".repeat(16)
    val old = key.mac("myzel-link/4 $ROLE_PHONE ${key.id} $phoneNonce $watchNonce".toByteArray())
    assertEquals(old.toHex(), key.proof(ROLE_PHONE, phoneNonce, watchNonce, 4))
  }

  @Test
  fun helloOfProtocol4HasNoApkHash() {
    // Myzel Wear vor Protokoll 5 schickt kein appSha256
    val payload = ByteArrayOutputStream()
    DataOutputStream(payload).use {
      it.writeInt(4)
      it.writeUTF("Uhr")
      it.writeUTF("ab".repeat(16))
      it.writeUTF("0011")
      it.writeUTF("cd".repeat(32))
      it.writeInt(37)
      it.writeLong(21)
    }
    val frame = ByteArrayOutputStream()
    DataOutputStream(frame).use {
      it.writeByte(FrameType.HELLO)
      it.writeInt(payload.size())
      payload.writeTo(it)
    }
    val hello = Frames.read(DataInputStream(ByteArrayInputStream(frame.toByteArray())))
    assertEquals(Hello(4, "Uhr", "ab".repeat(16), "0011", "cd".repeat(32), 37, 21), hello)
  }

  @Test
  fun oldPhoneIsRejected() {
    // Hello von Protokoll 1: nur Version und Gerätename
    val phone = watch()
    val bytes = ByteArrayOutputStream()
    DataOutputStream(bytes).use {
      it.writeInt(1)
      it.writeUTF("Altes Handy")
    }
    val frame = ByteArrayOutputStream()
    DataOutputStream(frame).use {
      it.writeByte(FrameType.HELLO)
      it.writeInt(bytes.size())
      bytes.writeTo(it)
    }
    phoneRaw.write(frame.toByteArray())
    phoneRaw.flush()
    assertEquals(PROTOCOL_VERSION, assertIs<Hello>(phone.receive()).protocol)
    assertFailsWith<EOFException> { phone.receive() }
    watchDone()
    assertTrue(watchErrors.single().message!!.contains("Protokoll 1"))
  }

  @Test
  fun offerTestDataIsCheckedAndDropped() = runBlocking {
    val client = connected()
    val data = Random(1).nextBytes(1_000_000)
    val offer =
      Offer(OfferKind.TEST, "test", 0, data.size.toLong(), sha256Of(ByteArrayInputStream(data)))
    val result = client.offer(offer, ByteArrayInputStream(data))
    assertTrue(result.ok)
    assertEquals(offer.sha256, result.sha256)
    assertEquals(0, received.size())
    // die Verbindung bleibt danach benutzbar
    assertTrue(client.ping() > 0)
    client.bye()
  }

  @Test
  fun apkIsStoredAndInstallMessagesWait() = runBlocking {
    val client = connected()
    val apk = Random(2).nextBytes(70_000)
    val offer =
      Offer(
        OfferKind.APK,
        "org.beispiel.app",
        7,
        apk.size.toLong(),
        sha256Of(ByteArrayInputStream(apk)),
      )
    assertTrue(client.offer(offer, ByteArrayInputStream(apk)).ok)
    // ein Ping dazwischen darf die Meldungen der Installation nicht verschlucken
    assertTrue(client.ping() > 0)
    assertEquals(InstallStatus.CONFIRM, client.nextInstall(1_000).status)
    assertEquals(InstallStatus.SUCCESS, client.nextInstall(1_000).status)
    client.bye()
    assertContentEquals(apk, received.toByteArray())
  }

  @Test
  fun appStatesAndUninstall() = runBlocking {
    val client = connected()
    val apps = client.appStates(listOf("org.beispiel.app", "org.fehlt"))
    assertEquals(
      AppState("org.beispiel.app", true, 7, "1.0", listOf("ab".repeat(32)), "com.android.vending"),
      apps[0],
    )
    assertFalse(apps[1].installed)
    client.uninstall("org.beispiel.app")
    assertEquals(InstallStatus.CONFIRM, client.nextInstall(1_000).status)
    assertEquals(InstallStatus.SUCCESS, client.nextInstall(1_000).status)
    client.bye()
    watchDone()
    assertEquals(listOf("hello Handy", "auth true", "uninstall org.beispiel.app"), events)
  }

  @Test
  fun wrongHashIsReported() = runBlocking {
    val client = connected()
    val data = Random(3).nextBytes(10_000)
    val offer = Offer(OfferKind.APK, "org.beispiel.app", 1, data.size.toLong(), "0".repeat(64))
    val result = client.offer(offer, ByteArrayInputStream(data))
    assertFalse(result.ok)
    assertEquals(sha256Of(ByteArrayInputStream(data)), result.sha256)
    client.bye()
    // die Uhr schickt das Ergebnis vor dem Aufruf des Listeners
    watchDone()
    assertEquals(listOf("hello Handy", "auth true", "data org.beispiel.app false"), events)
  }

  @Test
  fun frozenWatchEndsTheOffer() = runBlocking {
    // Die Uhr liest nie: Das Rohr läuft voll, das Senden blockiert, der Wächter schließt
    val toWatch = Pipe.open()
    val toPhone = Pipe.open()
    val phone =
      LinkConnection(
        Channels.newInputStream(toPhone.source()),
        Channels.newOutputStream(toWatch.sink()),
      )
    val client = LinkClient(phone, scope)
    val data = Random(4).nextBytes(4_000_000)
    val offer =
      Offer(OfferKind.TEST, "test", 0, data.size.toLong(), sha256Of(ByteArrayInputStream(data)))
    val e =
      assertFailsWith<LinkException> {
        client.offer(offer, ByteArrayInputStream(data), stallMillis = 500)
      }
    assertTrue(e.message!!.contains("eingefroren"))
  }

  @Test
  fun framesRoundTrip() {
    val messages =
      listOf(
        Hello(
          PROTOCOL_VERSION,
          "Pixel Watch 2",
          "ab".repeat(16),
          "0011223344556677",
          "cd".repeat(32),
          33,
          21,
          "9f".repeat(32),
        ),
        Hello(PROTOCOL_VERSION, "Pixel 6", "ef".repeat(16)),
        Auth("12".repeat(32)),
        AuthResult(false, "Schlüssel stimmt nicht"),
        Ping(42),
        Pong(-1),
        Offer(OfferKind.APK, "a.b", Long.MAX_VALUE, 3, "f".repeat(64)),
        Result(false, "", 12, "Fehler ä"),
        Install(InstallStatus.FAILURE, "abgebrochen"),
        AppQuery(listOf("a.b", "c.d")),
        AppQuery(emptyList()),
        AppStates(
          listOf(
            AppState(
              "a.b",
              true,
              200,
              "1.0.1",
              listOf("12".repeat(32), "34".repeat(32)),
              "com.android.vending",
            ),
            AppState("c.d", false),
          )
        ),
        Uninstall("a.b"),
        Bye,
      )
    val bytes = ByteArrayOutputStream()
    DataOutputStream(bytes).use { out -> messages.forEach { Frames.write(out, it) } }
    val input = DataInputStream(ByteArrayInputStream(bytes.toByteArray()))
    assertEquals(messages, messages.map { Frames.read(input) })
  }

  @Test
  fun oversizedFrameIsRejected() {
    val bytes = ByteArrayOutputStream()
    DataOutputStream(bytes).use { out ->
      out.writeByte(FrameType.PING)
      out.writeInt(MAX_FRAME_SIZE + 1)
    }
    assertFailsWith<LinkException> {
      Frames.read(DataInputStream(ByteArrayInputStream(bytes.toByteArray())))
    }
  }

  @Test
  fun tooManyAppsAreRejected() {
    val out = DataOutputStream(ByteArrayOutputStream())
    assertFailsWith<LinkException> { Frames.write(out, AppQuery(List(MAX_APPS + 1) { "a$it" })) }
  }

  @Test
  fun unknownFrameIsRejected() {
    val bytes = byteArrayOf(99, 0, 0, 0, 0)
    assertFailsWith<LinkException> { Frames.read(DataInputStream(ByteArrayInputStream(bytes))) }
  }

  @Test
  fun deviceNameNamesManufacturerOnce() {
    assertEquals("Google Pixel Watch 2", deviceName("Google", "Google Pixel Watch 2"))
    assertEquals("Google Pixel 6", deviceName("Google", "Pixel 6"))
    assertEquals("samsung SM-R960", deviceName("samsung", "SM-R960"))
  }
}
