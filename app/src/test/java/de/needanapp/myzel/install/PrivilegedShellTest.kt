package de.needanapp.myzel.install

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

internal class PrivilegedShellTest {

  @get:Rule val folder = TemporaryFolder()

  /** Eine gewöhnliche Shell statt su oder Shizuku. */
  private val shell = PrivilegedShell { command -> ProcessBuilder("sh", "-c", command).start() }

  @Test
  fun `pm reads the apk from stdin`() {
    assertEquals(
      "pm install -r -i de.needanapp.myzel --user current -S 1234",
      pmInstallCommand("de.needanapp.myzel", 1234),
    )
  }

  @Test
  fun `only package names go into the command`() {
    assertFailsWith<IllegalArgumentException> { pmInstallCommand("x; reboot", 1) }
    assertFailsWith<IllegalArgumentException> { pmInstallCommand("single", 1) }
  }

  @Test
  fun `pm success and failure`() {
    assertEquals(PmResult.Success, parsePmInstall(ShellOutput(0, "Performing install\nSuccess\n")))
    assertEquals(
      PmResult.Failure("Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures do not match]"),
      parsePmInstall(
        ShellOutput(1, "Failure [INSTALL_FAILED_UPDATE_INCOMPATIBLE: signatures do not match]\n")
      ),
    )
    // su verweigert: keine Zeile von pm
    assertEquals(
      PmResult.Failure("Permission denied"),
      parsePmInstall(ShellOutput(1, "Permission denied\n")),
    )
    assertEquals(PmResult.Failure("pm install: exit code 13"), parsePmInstall(ShellOutput(13, "")))
    // ohne Rückgabewert 0 zählt auch „Success“ nicht
    assertTrue(parsePmInstall(ShellOutput(1, "Success")) is PmResult.Failure)
  }

  @Test
  fun `root is uid 0`() {
    assertTrue(isRootId(ShellOutput(0, "uid=0(root) gid=0(root) groups=0(root)\n")))
    assertFalse(isRootId(ShellOutput(0, "uid=2000(shell) gid=2000(shell)\n")))
    assertFalse(isRootId(ShellOutput(1, "uid=0")))
  }

  @Test
  fun `unknown methods fall back to the package installer`() {
    assertEquals(InstallMethod.ROOT, InstallMethod.fromKey("root"))
    assertEquals(InstallMethod.SHIZUKU, InstallMethod.fromKey("shizuku"))
    assertEquals(InstallMethod.SESSION, InstallMethod.fromKey("legacy"))
    assertEquals(InstallMethod.SESSION, InstallMethod.fromKey(null))
  }

  @Test
  fun `the input reaches the command`() = runBlocking {
    val apk = folder.newFile("app.apk").apply { writeBytes(ByteArray(300_000) { it.toByte() }) }
    val output = shell.execute("wc -c", apk, timeoutMs = 10_000)
    assertEquals(0, output.exitCode)
    assertEquals("300000", output.output.trim())
  }

  @Test
  fun `stdout and stderr are both read`() = runBlocking {
    val output = shell.execute("echo out; echo err >&2; exit 3", timeoutMs = 10_000)
    assertEquals(3, output.exitCode)
    assertEquals(setOf("out", "err"), output.output.lines().filter { it.isNotEmpty() }.toSet())
  }

  @Test
  fun `a command that stops reading early does not hang`() = runBlocking {
    val apk = folder.newFile("big.apk").apply { writeBytes(ByteArray(5_000_000)) }
    val output = shell.execute("echo Failure; exit 1", apk, timeoutMs = 10_000)
    assertEquals(PmResult.Failure("Failure"), parsePmInstall(output))
  }

  @Test
  fun `a hanging command times out`() {
    assertFailsWith<TimeoutCancellationException> {
      runBlocking { shell.execute("sleep 10", timeoutMs = 200) }
    }
  }
}
