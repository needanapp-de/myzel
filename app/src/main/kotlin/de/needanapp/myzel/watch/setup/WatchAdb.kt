package de.needanapp.myzel.watch.setup

import android.os.Build
import io.github.muntashirakon.adb.AbsAdbConnectionManager
import io.github.muntashirakon.adb.AdbStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.io.InterruptedIOException
import java.math.BigInteger
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.PrivateKey
import java.security.cert.Certificate
import java.security.cert.CertificateFactory
import java.security.spec.PKCS8EncodedKeySpec
import java.util.Date
import java.util.Timer
import java.util.concurrent.TimeUnit
import kotlin.concurrent.timerTask
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder

/**
 * ADB-Verbindung des Handys zur Uhr (libadb-android). Das Handy weist sich mit einem eigenen
 * RSA-Schlüssel samt selbst signiertem Zertifikat aus; nach dem Koppeln kennt die Uhr ihn und lässt
 * Verbindungen ohne Code zu.
 */
class WatchAdb(keyDir: File) : AbsAdbConnectionManager() {
  private val keys = AdbKeys(keyDir)

  init {
    api = Build.VERSION.SDK_INT
    setTimeout(10, TimeUnit.SECONDS)
  }

  override fun getPrivateKey(): PrivateKey = keys.privateKey

  override fun getCertificate(): Certificate = keys.certificate

  override fun getDeviceName(): String = "Myzel"

  /** Führt [command] auf der Uhr aus und gibt die Ausgabe zurück. */
  fun exec(command: String): String =
    withDeadline(EXEC_TIMEOUT_MILLIS) {
      openStream("exec:$command; echo $END").use { it.readUntilEnd() }
    }

  /** Ob das Gerät eine Wear-OS-Uhr ist (Merkmal `android.hardware.type.watch`). */
  fun isWatch(): Boolean =
    exec("pm has-feature android.hardware.type.watch") == "true" ||
      "watch" in exec("getprop ro.build.characteristics").split(',')

  /** Installiert [apk] über `cmd package install`, wie `adb install -r`. */
  fun install(apk: File): String =
    withDeadline(INSTALL_TIMEOUT_MILLIS) {
      // cmd liest genau die angesagten Bytes, danach läuft echo
      openStream("exec:cmd package install -r -S ${apk.length()}; echo $END").use { stream ->
        // close() des Ausgabestroms spült nur; die Antwort kommt danach
        stream.openOutputStream().use { out -> apk.inputStream().use { it.copyTo(out) } }
        stream.readUntilEnd()
      }
    }
}

/**
 * Hinter jedem Befehl gibt die Uhr diese Marke aus. Myzel liest bis zu ihr statt bis zum Ende des
 * Datenstroms: libadb-android 3.1.1 verliert das Ende, wenn es zusammen mit den letzten Daten
 * ankommt (`AdbStream.notifyClose` setzt dann nur `mPendingClose`, und `read` wartet danach für
 * immer). So hing die Einrichtung nach „Success“ der Installation.
 */
private const val END = "__myzel_end__"

private const val EXEC_TIMEOUT_MILLIS = 60_000L

/** Übertragen (1,8 MB über WLAN) und Installieren brauchten 5 bis 10 s. */
private const val INSTALL_TIMEOUT_MILLIS = 180_000L

/** Liest bis zur Marke [END]; endet der Datenstrom vorher (Verbindung weg), das Bisherige. */
private fun AdbStream.readUntilEnd(): String {
  val out = ByteArrayOutputStream()
  val buffer = ByteArray(8192)
  while (true) {
    val n =
      try {
        read(buffer, 0, buffer.size)
      } catch (e: IOException) {
        if (isClosed) break else throw e
      }
    if (n < 0) break
    out.write(buffer, 0, n)
    val text = out.toString(Charsets.UTF_8.name())
    val end = text.lastIndexOf(END)
    if (end >= 0) return text.substring(0, end).trim()
  }
  return out.toString(Charsets.UTF_8.name()).trim()
}

private val watchdog = Timer("myzel-adb", true)

/**
 * Bricht [block] nach [millis] ab, indem es den Thread unterbricht; libadb wartet mit
 * `Object.wait()` und wirft dann eine IOException. Antwortet die Uhr nicht mehr, hängt die
 * Einrichtung so nicht für immer.
 */
private fun <T> withDeadline(millis: Long, block: () -> T): T {
  val thread = Thread.currentThread()
  val lock = Any()
  var done = false
  var expired = false
  val task = timerTask {
    synchronized(lock) {
      if (!done) {
        expired = true
        thread.interrupt()
      }
    }
  }
  watchdog.schedule(task, millis)
  try {
    return block()
  } catch (e: IOException) {
    if (synchronized(lock) { expired }) {
      throw InterruptedIOException("Die Uhr antwortet nicht (${millis / 1000} s)").apply {
        initCause(e)
      }
    }
    throw e
  } finally {
    synchronized(lock) { done = true }
    task.cancel()
    // eine Unterbrechung, die nicht mehr nötig war, nicht an den nächsten Auftrag weitergeben
    Thread.interrupted()
  }
}

/** RSA-Schlüssel und Zertifikat für ADB, beim ersten Mal erzeugt und in [dir] gespeichert. */
private class AdbKeys(dir: File) {
  val privateKey: PrivateKey
  val certificate: Certificate

  init {
    val keyFile = File(dir, "key.pk8")
    val certFile = File(dir, "cert.der")
    if (keyFile.exists() && certFile.exists()) {
      privateKey =
        KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(keyFile.readBytes()))
      certificate =
        certFile.inputStream().use {
          CertificateFactory.getInstance("X.509").generateCertificate(it)
        }
    } else {
      val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
      val name = X500Name("CN=Myzel")
      val now = System.currentTimeMillis()
      val builder =
        JcaX509v3CertificateBuilder(
          name,
          BigInteger.valueOf(now),
          Date(now),
          Date(now + 30L * 365 * 24 * 60 * 60 * 1000),
          name,
          pair.public,
        )
      val signer = JcaContentSignerBuilder("SHA256withRSA").build(pair.private)
      privateKey = pair.private
      certificate = JcaX509CertificateConverter().getCertificate(builder.build(signer))
      dir.mkdirs()
      keyFile.writeBytes(privateKey.encoded)
      certFile.writeBytes(certificate.encoded)
    }
  }
}
