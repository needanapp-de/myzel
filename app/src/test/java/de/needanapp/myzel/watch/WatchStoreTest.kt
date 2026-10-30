package de.needanapp.myzel.watch

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
internal class WatchStoreTest {
  private val context = RuntimeEnvironment.getApplication()
  private val phone = HiddenDevice("R0000000001", "Google Pixel 8")
  private val tablet = HiddenDevice("R0000000002", "Samsung SM-X200")

  @Before
  fun clean() {
    File(context.noBackupFilesDir, "myzel").deleteRecursively()
  }

  @Test
  fun `hidden devices survive a restart and the forgotten watch`() {
    val store = WatchStore(context)
    store.hideDevice(phone)
    store.hideDevice(tablet)
    // dasselbe Gerät noch einmal: nur ein Eintrag, mit dem neuen Namen
    store.hideDevice(phone.copy(name = "Pixel 8"))
    store.forget()
    val restarted = WatchStore(context)
    assertEquals(listOf(tablet, phone.copy(name = "Pixel 8")), restarted.hiddenDevices.value)
  }

  @Test
  fun `devices come back one by one or all at once`() {
    val store = WatchStore(context)
    store.hideDevice(phone)
    store.hideDevice(tablet)
    store.showDevice(phone.serial)
    assertEquals(listOf(tablet), store.hiddenDevices.value)
    assertEquals(listOf(tablet), WatchStore(context).hiddenDevices.value)
    store.showAllDevices()
    assertTrue(store.hiddenDevices.value.isEmpty())
    assertTrue(WatchStore(context).hiddenDevices.value.isEmpty())
  }
}
