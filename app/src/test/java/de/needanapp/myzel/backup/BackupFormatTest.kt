package de.needanapp.myzel.backup

import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import org.fdroid.R
import org.junit.Test

internal class BackupFormatTest {

  private val needanapp =
    BackupRepository(
      address = "https://fdroid.needanapp.de/repo",
      certificate = "AABB",
      name = "needanapp.de",
      enabled = true,
      userMirrors = listOf("https://mirror.example.org/repo"),
    )
  private val izzy =
    BackupRepository(address = "https://apt.izzysoft.de/fdroid/repo", certificate = "ccdd")

  @Test
  fun `a backup survives writing and reading`() {
    val backup =
      BackupFile(
        created = 1_000,
        appVersion = "0.7.0",
        repositories = listOf(needanapp, izzy),
        favorites = listOf("de.needanapp.needafile", "org.fdroid.basic"),
        settings = BackupSettings.fromPreferences(emptyMap()),
        appearance = buildJsonObject { put("mode", JsonPrimitive("DARK")) },
      )
    assertEquals(backup, decodeBackup(encodeBackup(backup)))
  }

  @Test
  fun `missing sections stay missing`() {
    val backup = decodeBackup(encodeBackup(BackupFile(favorites = listOf("a.b"))))
    assertEquals(listOf(BackupSection.FAVORITES), backup.sections)
    assertNull(backup.repositories)
  }

  @Test
  fun `other files are not a backup`() {
    for (text in listOf("", "not json", "[]", """{"format":"something.else","version":1}""")) {
      val e = assertFailsWith<BackupException> { decodeBackup(text) }
      assertEquals(R.string.myzel_backup_error_not_a_backup, e.messageRes)
    }
  }

  @Test
  fun `a newer version is refused`() {
    val e =
      assertFailsWith<BackupException> {
        decodeBackup("""{"format":"$BACKUP_FORMAT","version":${BACKUP_VERSION + 1}}""")
      }
    assertEquals(R.string.myzel_backup_error_newer_version, e.messageRes)
  }

  @Test
  fun `wrong types make a damaged backup`() {
    val e =
      assertFailsWith<BackupException> {
        decodeBackup("""{"format":"$BACKUP_FORMAT","version":1,"favorites":"a.b"}""")
      }
    assertEquals(R.string.myzel_backup_error_damaged, e.messageRes)
  }

  @Test
  fun `unknown keys are ignored`() {
    val backup =
      decodeBackup("""{"format":"$BACKUP_FORMAT","version":1,"future":true,"favorites":["a.b"]}""")
    assertEquals(listOf("a.b"), backup.favorites)
  }

  @Test
  fun `odd package names and mirrors are dropped`() {
    val backup =
      decodeBackup(
        encodeBackup(
          BackupFile(
            favorites = listOf("a.b", "a b", "../x", "a.b"),
            repositories =
              listOf(needanapp.copy(userMirrors = listOf("file:///x", "https://m.org"))),
          )
        )
      )
    assertEquals(listOf("a.b"), backup.favorites)
    assertEquals(listOf("https://m.org"), backup.repositories!!.single().userMirrors)
  }

  @Test
  fun `settings with unknown values keep the current ones`() {
    val settings =
      BackupSettings(
          repoAutoUpdates = "Always",
          appAutoUpdates = "OnlyWhenOpenApp",
          mirrorChooser = "Nearest",
          proxy = "localhost:9050",
          appListSortOrder = "name",
          myAppsSortOrder = "size",
          useDnsCache = true,
        )
        .checked()
    assertEquals(
      mapOf(
        "repoAutoUpdates" to "Always",
        "proxy" to "localhost:9050",
        "appListSortOrder" to "name",
        "useDnsCache" to true,
      ),
      settings.toPreferences(),
    )
  }

  @Test
  fun `an invalid proxy is dropped, no proxy is kept`() {
    assertNull(BackupSettings(proxy = "localhost").checked().proxy)
    assertNull(BackupSettings(proxy = "localhost:port").checked().proxy)
    assertEquals("", BackupSettings(proxy = "").checked().proxy)
  }

  @Test
  fun `settings use the keys of f-droid`() {
    val prefs =
      mapOf<String, Any?>(
        "appAutoUpdates" to "Never",
        "keepInstallHistory" to true,
        "incompatibleVersions" to false,
      )
    val settings = BackupSettings.fromPreferences(prefs)
    assertEquals("Never", settings.appAutoUpdates)
    assertEquals(true, settings.keepInstallHistory)
    assertEquals(false, settings.showIncompatible)
    // Standardwerte von F-Droid für alles, was nicht gesetzt ist
    assertEquals("OnlyWifi", settings.repoAutoUpdates)
    assertEquals(true, settings.warnWhenMetered)
    assertEquals(12, settings.toPreferences().size)
  }

  @Test
  fun `repositories are matched by certificate`() {
    val existing =
      listOf(
        ExistingRepo(1, "https://apt.izzysoft.de/fdroid/repo/", "CCDD"),
        ExistingRepo(2, "https://old.needanapp.de/repo", "aabb"),
      )
    val plan = planRepoRestore(listOf(needanapp, izzy), existing)
    assertEquals(listOf(RepoStep.Update(needanapp, 2), RepoStep.Update(izzy, 1)), plan.steps)
    assertEquals(emptyList(), plan.skipped)
  }

  @Test
  fun `unknown repositories are added`() {
    val plan = planRepoRestore(listOf(needanapp), emptyList())
    assertEquals(listOf(RepoStep.Add(needanapp)), plan.steps)
  }

  @Test
  fun `the same address with another certificate is skipped`() {
    val existing = listOf(ExistingRepo(1, "https://fdroid.needanapp.de/repo", "eeff"))
    val plan = planRepoRestore(listOf(needanapp), existing)
    assertEquals(emptyList(), plan.steps)
    assertEquals(listOf(needanapp), plan.skipped)
  }

  @Test
  fun `repositories without certificate, web address or twice are skipped`() {
    val noCertificate = izzy.copy(certificate = "")
    val local = izzy.copy(address = "content://usb/repo")
    val twice = needanapp.copy(address = "https://copy.example.org/repo")
    val plan = planRepoRestore(listOf(noCertificate, local, needanapp, twice), emptyList())
    assertEquals(listOf(RepoStep.Add(needanapp)), plan.steps)
    assertEquals(listOf(noCertificate, local, twice), plan.skipped)
  }

  @Test
  fun `reorder steps follow reorderRepositories`() {
    // jeder Schritt holt eine Quelle von unten an die Stelle des Ziels
    val current = listOf(1L, 2L, 3L, 4L)
    assertEquals(emptyList(), reorderSteps(current, current))
    assertEquals(listOf(4L to 1L), reorderSteps(current, listOf(4L, 1L, 2L, 3L)))
    assertEquals(
      listOf(4L to 1L, 3L to 1L, 2L to 1L),
      reorderSteps(current, listOf(4L, 3L, 2L, 1L)),
    )
  }

  @Test
  fun `repositories missing in the backup go below the others`() {
    val current = listOf(1L, 2L, 3L, 4L)
    // nur 3 und 1 in der Sicherung: 3, 1, dann 2 und 4 in ihrer bisherigen Reihenfolge
    val steps = reorderSteps(current, listOf(3L, 1L))
    assertEquals(listOf(3L, 1L, 2L, 4L), apply(current, steps))
    // unbekannte Kennungen stören nicht
    assertEquals(emptyList(), reorderSteps(current, listOf(9L, 1L, 2L)))
  }

  /** Spielt die Schritte so durch, wie RepositoryDao.reorderRepositories sie ausführt. */
  private fun apply(current: List<Long>, steps: List<Pair<Long, Long>>): List<Long> {
    val list = current.toMutableList()
    for ((move, target) in steps) {
      list.remove(move)
      list.add(list.indexOf(target), move)
    }
    return list
  }
}
