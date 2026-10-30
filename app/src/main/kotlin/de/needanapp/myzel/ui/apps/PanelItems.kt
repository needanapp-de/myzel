package de.needanapp.myzel.ui.apps

import androidx.core.content.pm.PackageInfoCompat
import androidx.core.os.LocaleListCompat
import javax.inject.Inject
import org.fdroid.database.AppOverviewItem
import org.fdroid.download.DownloadRequest
import org.fdroid.download.PackageName
import org.fdroid.download.getImageModel
import org.fdroid.index.RepoManager
import org.fdroid.install.InstalledAppsCache
import org.fdroid.settings.SettingsManager
import org.fdroid.ui.apps.AppUpdateItem
import org.fdroid.ui.apps.AppWithIssueItem
import org.fdroid.ui.apps.InstalledAppItem
import org.fdroid.ui.apps.MyAppItem
import org.fdroid.ui.lists.AppListItem

/**
 * Macht aus Apps der Datenbank die Einträge für Listen mit Bedienfeld wie die Favoriten oder „Neu
 * und aktualisiert“: [listItem] für die Karte, [panelItem] für das Bedienfeld ([AppPanel]).
 */
class PanelItems
@Inject
constructor(
  private val repoManager: RepoManager,
  private val settingsManager: SettingsManager,
  private val installedAppsCache: InstalledAppsCache,
) {
  private val localeList = LocaleListCompat.getDefault()

  /** Wie org.fdroid.ui.lists.AppListViewModel.loadApps; null, wenn die Paketquelle fehlt. */
  fun listItem(app: AppOverviewItem): AppListItem? {
    val repository = repoManager.getRepository(app.repoId) ?: return null
    val iconModel =
      app.getIcon(localeList)?.getImageModel(repository, settingsManager.proxyConfig)
        as? DownloadRequest
    val isInstalled = installedAppsCache.isInstalled(app.packageName)
    return AppListItem(
      repoId = app.repoId,
      packageName = app.packageName,
      name = app.getName(localeList) ?: "Unknown App",
      summary = app.getSummary(localeList) ?: "",
      lastUpdated = app.lastUpdated,
      isInstalled = isInstalled,
      isCompatible = app.isCompatible,
      iconModel = if (isInstalled) PackageName(app.packageName, iconModel) else iconModel,
      categoryIds = app.categories?.toSet(),
      antiFeatureIds = app.antiFeatureKeys.toSet(),
    )
  }

  /**
   * Was das Bedienfeld über [item] wissen muss, wie in „Meine Apps“: mit Update, mit Problem oder
   * installiert; null, wenn die App nicht installiert ist.
   */
  fun panelItem(
    item: AppListItem,
    updates: List<AppUpdateItem>?,
    issues: List<AppWithIssueItem>?,
  ): MyAppItem? {
    val packageName = item.packageName
    return updates?.find { it.packageName == packageName }
      ?: issues?.find { it.packageName == packageName }
      ?: installedItem(item)
  }

  // wie org.fdroid.ui.apps.MyAppsViewModel.installedAppItems, die Version vom Gerät
  private fun installedItem(item: AppListItem): InstalledAppItem? {
    val info = installedAppsCache.installedApps.value[item.packageName] ?: return null
    return InstalledAppItem(
      packageName = item.packageName,
      name = item.name,
      installedVersionName = info.versionName ?: "???",
      installedVersionCode = PackageInfoCompat.getLongVersionCode(info),
      lastUpdated = item.lastUpdated,
      iconModel = item.iconModel,
    )
  }
}
