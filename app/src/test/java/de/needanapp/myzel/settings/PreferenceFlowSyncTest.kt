package de.needanapp.myzel.settings

import android.content.Context.MODE_PRIVATE
import android.os.Looper
import kotlin.test.assertEquals
import kotlinx.coroutines.flow.update
import me.zhanghai.compose.preference.createPreferenceFlow
import me.zhanghai.compose.preference.isDefaultPreferenceFlowAndroidLongSupportEnabled
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
internal class PreferenceFlowSyncTest {
  private val prefs =
    RuntimeEnvironment.getApplication().getSharedPreferences("sync-test", MODE_PRIVATE)

  @Before
  fun clean() {
    prefs.edit().clear().commit()
    isDefaultPreferenceFlowAndroidLongSupportEnabled = true
  }

  @Test
  fun `values written directly survive a change through the flow`() {
    val flow = createPreferenceFlow(prefs).syncWith(prefs)
    // wie SettingsManager.lastRepoUpdate: direkt in die SharedPreferences, am Flow vorbei
    prefs.edit().putLong("lastUpdateCheck", 1234L).apply()
    idle()
    // wie die Einstellungsseite beim Umstellen des Modus
    flow.update { it.toMutablePreferences().apply { this["theme"] = "light" } }
    idle()
    assertEquals(1234L, prefs.getLong("lastUpdateCheck", 0L))
    assertEquals("light", prefs.getString("theme", null))
    assertEquals(1234L, flow.value.asMap()["lastUpdateCheck"])
  }

  @Test
  fun `writes through the flow still reach the shared preferences`() {
    val flow = createPreferenceFlow(prefs).syncWith(prefs)
    flow.update { it.toMutablePreferences().apply { this["proxy"] = "proxy.example:9050" } }
    idle()
    assertEquals("proxy.example:9050", prefs.getString("proxy", null))
    flow.update { it.toMutablePreferences().apply { remove("proxy") } }
    idle()
    assertEquals(null, prefs.getString("proxy", null))
  }

  private fun idle() = shadowOf(Looper.getMainLooper()).idle()
}
