package de.needanapp.myzel.install

import androidx.annotation.StringRes
import org.fdroid.R

/**
 * Wie Myzel Apps installiert. Root und Shizuku installieren ohne Nachfrage; Legacy (die alte
 * Installation per Intent) bietet Myzel nicht an, weil sie ab Android 12 nur noch nachfragt wie der
 * Paketinstaller selbst.
 */
enum class InstallMethod(
  val key: String,
  @StringRes val label: Int,
  @StringRes val summary: Int,
) {
  /** Der Paketinstaller von Android wie bei F-Droid: neue Apps fragen nach. */
  SESSION("session", R.string.myzel_install_session, R.string.myzel_install_session_summary),
  ROOT("root", R.string.myzel_install_root, R.string.myzel_install_root_summary),
  SHIZUKU("shizuku", R.string.myzel_install_shizuku, R.string.myzel_install_shizuku_summary);

  companion object {
    /** Schlüssel in den Preferences von F-Droid. */
    const val PREF_KEY = "myzel_installMethod"
    val DEFAULT = SESSION

    fun fromKey(key: String?): InstallMethod = entries.firstOrNull { it.key == key } ?: DEFAULT
  }
}
