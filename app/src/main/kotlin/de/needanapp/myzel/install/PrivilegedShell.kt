package de.needanapp.myzel.install

import java.io.File
import java.io.IOException
import java.io.InputStream
import kotlin.concurrent.thread
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import rikka.shizuku.Shizuku

/** Startet Shell-Befehle mit mehr Rechten als Myzel selbst hat. */
internal fun interface PrivilegedShell {
  /** @throws Exception wenn sich der Befehl nicht starten lässt */
  fun start(command: String): Process
}

/** Befehle als root über su, etwa von Magisk oder KernelSU. */
internal val RootShell = PrivilegedShell { command -> ProcessBuilder("su", "-c", command).start() }

/**
 * Befehle über Shizuku, als Nutzer shell (über ADB gestartet) oder root. Shizuku.newProcess ist
 * seit der API 13 privat, aber weiter vorhanden; der Aufruf geht deshalb über Reflexion. Fehlt die
 * Methode einmal, schlägt start fehl und Myzel installiert wieder mit dem Paketinstaller.
 */
internal val ShizukuShell = PrivilegedShell { command ->
  val newProcess =
    Shizuku::class
      .java
      .getDeclaredMethod(
        "newProcess",
        Array<String>::class.java,
        Array<String>::class.java,
        String::class.java,
      )
      .apply { isAccessible = true }
  newProcess.invoke(null, arrayOf("sh", "-c", command), null, null) as Process
}

/** Rückgabewert und Ausgabe (stdout und stderr) eines Befehls. */
internal data class ShellOutput(val exitCode: Int, val output: String)

/**
 * Führt [command] aus und schreibt [input] in seine Standardeingabe. Lesen, Schreiben und Warten
 * laufen in eigenen Threads: Ein Befehl über Shizuku blockiert in Binder-Aufrufen, die sich nicht
 * unterbrechen lassen. Nach [timeoutMs] oder beim Abbrechen wird der Prozess beendet.
 */
internal suspend fun PrivilegedShell.execute(
  command: String,
  input: File? = null,
  timeoutMs: Long,
): ShellOutput =
  withContext(Dispatchers.IO) {
    val process = start(command)
    val output = StringBuffer()
    try {
      val readers =
        listOf(process.inputStream, process.errorStream).map { stream ->
          stream.collectInto(output)
        }
      thread(isDaemon = true, name = "myzel-shell-input") {
        try {
          process.outputStream.use { out -> input?.inputStream()?.use { it.copyTo(out) } }
        } catch (_: IOException) {
          // der Befehl hat vorzeitig aufgehört zu lesen; seine Ausgabe sagt, warum
        }
      }
      val exitCode = withTimeout(timeoutMs) { process.awaitExit() }
      readers.forEach { it.join(READER_JOIN_MS) }
      ShellOutput(exitCode, output.toString())
    } finally {
      process.destroy()
    }
  }

private fun InputStream.collectInto(output: StringBuffer): Thread =
  thread(isDaemon = true, name = "myzel-shell-output") {
    try {
      // eine Zeile in einem Aufruf: appendLine hängt den Umbruch getrennt an, dazwischen könnte
      // eine Zeile des anderen Stroms landen
      bufferedReader().forEachLine { output.append("$it\n") }
    } catch (_: IOException) {
      // Prozess beendet
    }
  }

private suspend fun Process.awaitExit(): Int = suspendCancellableCoroutine { cont ->
  thread(isDaemon = true, name = "myzel-shell-wait") {
    try {
      val exitCode = waitFor()
      if (cont.isActive) cont.resume(exitCode)
    } catch (e: Exception) {
      if (cont.isActive) cont.resumeWithException(e)
    }
  }
  // beendet auch ein waitFor, das in einem Binder-Aufruf hängt
  cont.invokeOnCancellation { destroy() }
}

private const val READER_JOIN_MS = 2_000L

/**
 * Der Befehl, mit dem pm die APK aus der Standardeingabe installiert. [installer] wird als
 * installierende App eingetragen, damit spätere Updates auch über den Paketinstaller ohne Nachfrage
 * gehen können.
 */
internal fun pmInstallCommand(installer: String, size: Long): String {
  require(PACKAGE_NAME.matches(installer)) { "Unexpected package name $installer" }
  return "pm install -r -i $installer --user current -S $size"
}

private val PACKAGE_NAME = Regex("[A-Za-z0-9_]+(\\.[A-Za-z0-9_]+)+")

/** Ergebnis einer Installation über pm. */
internal sealed interface PmResult {
  data object Success : PmResult

  data class Failure(val message: String) : PmResult
}

/** pm meldet „Success“ oder eine Zeile „Failure [INSTALL_FAILED_…]“. */
internal fun parsePmInstall(output: ShellOutput): PmResult {
  val lines = output.output.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
  return if (output.exitCode == 0 && lines.any { it == "Success" }) {
    PmResult.Success
  } else {
    PmResult.Failure(
      lines.firstOrNull { it.startsWith("Failure") }
        ?: lines.lastOrNull()
        ?: "pm install: exit code ${output.exitCode}"
    )
  }
}

/** Ob die Ausgabe von id root meldet. */
internal fun isRootId(output: ShellOutput): Boolean =
  output.exitCode == 0 && output.output.contains("uid=0")
