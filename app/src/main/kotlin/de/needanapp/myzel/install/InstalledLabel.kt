package de.needanapp.myzel.install

import android.app.LocaleManager
import android.content.Context
import android.content.pm.PackageManager.NameNotFoundException
import android.content.res.Configuration
import android.content.res.Resources
import android.icu.util.ULocale
import androidx.annotation.RequiresApi

/**
 * Name einer installierten App für die Vorabfreigabe eines Updates
 * (`PackageInstaller.PreapprovalDetails`).
 *
 * Android prüft beim Installieren, ob der Name der Vorabfreigabe zum Namen der installierten App
 * passt oder zu dem in der APK, in der Sprache der Vorabfreigabe. Passt er nicht, scheitert die
 * Installation, F-Droid installiert ohne Vorabfreigabe noch einmal und Android fragt ein zweites
 * Mal. F-Droid nimmt den Namen aus der Paketquelle, der oft abweicht („Fennec F-Droid“ statt
 * „Fennec“). Myzel nimmt den Namen so, wie ihn das System liest: in der Sprache des Systems, nicht
 * in der von Myzel. Für eine neue App gibt es keinen sicheren Namen, sie bekommt keine
 * Vorabfreigabe ([SessionInstallManager.requestPreapproval]).
 */
data class InstalledLabel(val text: String, val locale: ULocale) {

  companion object {
    /** Der Name von [packageName], oder null, wenn die App nicht installiert ist. */
    @RequiresApi(33)
    fun of(context: Context, packageName: String): InstalledLabel? {
      val pm = context.packageManager
      val info =
        try {
          pm.getApplicationInfo(packageName, 0)
        } catch (_: NameNotFoundException) {
          return null
        }
      val locales = context.getSystemService(LocaleManager::class.java).systemLocales
      // wie PackageItemInfo.loadLabel: fester Name ungekürzt, Name aus den Ressourcen ohne Leerraum
      val text =
        info.nonLocalizedLabel?.toString()
          ?: info.labelRes
            .takeIf { it != 0 }
            ?.let { labelRes ->
              val config = Configuration(context.resources.configuration)
              config.setLocales(locales)
              try {
                pm.getResourcesForApplication(info, config).getText(labelRes).toString().trim()
              } catch (_: NameNotFoundException) {
                null
              } catch (_: Resources.NotFoundException) {
                null
              }
            }
          ?: packageName
      return InstalledLabel(text, ULocale.forLocale(locales[0]))
    }
  }
}
