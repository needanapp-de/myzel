package de.needanapp.myzel.baselineprofile

import android.content.Intent
import android.net.Uri
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import java.util.regex.Pattern
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Die Variante basicDefaultProfiling der App (app/myzel.gradle). */
private const val TARGET_PACKAGE = "de.needanapp.myzel.profiling"

/** Titel eines Bereichs auf Entdecken, auf Deutsch und Englisch: Entdecken ist geladen. */
private val DISCOVER_LOADED = Pattern.compile("Kürzlich aktualisiert|Recently updated")

/** Eine App, die es in der Paketquelle von F-Droid sicher gibt. */
private const val DETAILS_LINK = "market://details?id=org.fdroid.fdroid"

/** Beim ersten Durchgang lädt Myzel erst die Paketquellen. */
private const val FIRST_LOAD_TIMEOUT_MS = 300_000L
private const val TIMEOUT_MS = 10_000L

/**
 * Der Weg, den das Baseline Profile abdeckt: Start, Entdecken mit seinen Karussells und eine
 * App-Seite. Was dabei läuft, übersetzt ART nach der Installation vorab, statt es beim ersten
 * Benutzen zu interpretieren.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

  @get:Rule val rule = BaselineProfileRule()

  @Test
  fun generate() =
    rule.collect(packageName = TARGET_PACKAGE) {
      // sonst fragt Myzel beim Start danach, und der Dialog liegt über allem
      device.executeShellCommand("pm grant $TARGET_PACKAGE android.permission.POST_NOTIFICATIONS")
      pressHome()
      startActivityAndWait()
      check(device.wait(Until.hasObject(By.text(DISCOVER_LOADED)), FIRST_LOAD_TIMEOUT_MS)) {
        "Entdecken ist nicht geladen"
      }
      scrollDiscover()
      openDetails()
    }

  private fun MacrobenchmarkScope.scrollDiscover() {
    val width = device.displayWidth
    val height = device.displayHeight
    device.waitForIdle()
    // die Karussells zur Seite, so weit es geht, und zurück
    device
      .findObjects(By.scrollable(true))
      .filter { it.visibleBounds.width() > it.visibleBounds.height() * 2 }
      .forEach { carousel ->
        repeat(2) { carousel.fling(Direction.RIGHT) }
        carousel.fling(Direction.LEFT)
      }
    // die Seite hinunter und wieder hinauf
    repeat(2) {
      device.swipe(width / 2, height * 3 / 4, width / 2, height / 4, 20)
      device.waitForIdle()
    }
    repeat(2) {
      device.swipe(width / 2, height / 4, width / 2, height * 3 / 4, 20)
      device.waitForIdle()
    }
  }

  private fun MacrobenchmarkScope.openDetails() {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(DETAILS_LINK)).setPackage(TARGET_PACKAGE)
    startActivityAndWait(intent)
    device.wait(Until.hasObject(By.pkg(TARGET_PACKAGE).depth(0)), TIMEOUT_MS)
    device.waitForIdle()
    val width = device.displayWidth
    val height = device.displayHeight
    repeat(3) {
      device.swipe(width / 2, height * 3 / 4, width / 2, height / 4, 20)
      device.waitForIdle()
    }
    device.pressBack()
    device.waitForIdle()
  }
}
