package de.needanapp.myzel.diagnose

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.asFlow
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import de.needanapp.myzel.watch.isForWatch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fdroid.database.AppVersion
import org.fdroid.database.FDroidDatabase
import org.fdroid.index.RepoManager
import org.fdroid.ui.details.LoadedAppDetailsItem

@EntryPoint
@InstallIn(SingletonComponent::class)
interface DiagnosisEntryPoint {
  fun db(): FDroidDatabase

  fun repoManager(): RepoManager
}

/**
 * Die Update-Diagnose für die installierte App aus [item], oder null, solange sie lädt oder die App
 * nicht installiert ist. Sie liest die Versionen aller eingeschalteten Paketquellen (die Seite
 * selbst kennt nur die gewählte) und rechnet neu, wenn sich die Versionen, die Einstellungen der
 * App oder ihr Installationszustand ändern; dazwischen bleibt das letzte Ergebnis stehen.
 */
@Composable
fun rememberUpdateDiagnosis(item: LoadedAppDetailsItem): UpdateDiagnosis? {
  if (LocalInspectionMode.current || item.installedVersionCode == null) return null
  val context = LocalContext.current
  val entry = remember {
    EntryPointAccessors.fromApplication(context, DiagnosisEntryPoint::class.java)
  }
  var diagnosis by remember(item.app.packageName) { mutableStateOf<UpdateDiagnosis?>(null) }
  LaunchedEffect(
    item.app.packageName,
    item.installedVersionCode,
    item.preferredRepoId,
    item.appPrefs,
    // nach einem Fehler oder einer Installation neu lesen
    item.installState::class,
  ) {
    entry.db().getVersionDao().getAppVersions(item.app.packageName).asFlow().collect { versions ->
      diagnosis = withContext(Dispatchers.IO) { load(context, entry, item, versions) }
    }
  }
  return diagnosis
}

private fun load(
  context: Context,
  entry: DiagnosisEntryPoint,
  item: LoadedAppDetailsItem,
  versions: List<AppVersion>,
): UpdateDiagnosis? {
  val packageName = item.app.packageName
  val installed = readInstalledApp(context, packageName) ?: return null
  val phoneVersions = versions.filterNot { it.isForWatch }
  val locales = LocaleListCompat.getDefault()
  val repos =
    phoneVersions
      .map { it.repoId }
      .distinct()
      .mapNotNull { entry.repoManager().getRepository(it) }
      .sortedByDescending { it.weight }
      .map { RepoInfo(it.repoId, it.getName(locales) ?: it.address) }
  val prefs = item.appPrefs
  val input =
    DiagnosisInput(
      installed = installed,
      device = readDevice(context),
      repos = repos,
      versions = phoneVersions.map { it.toRepoVersion() },
      preferredRepoId = item.preferredRepoId,
      allowsBeta = item.allowsBetaVersions,
      ignoresAll = item.ignoresAllUpdates,
      ignoredVersionCode = prefs?.ignoreVersionCodeUpdate ?: 0,
      lastError = InstallErrorLog.get(context).get(packageName),
    )
  return diagnose(input)
}

private fun AppVersion.toRepoVersion() =
  RepoVersion(
    repoId = repoId,
    versionName = versionName,
    versionCode = versionCode,
    signers = signer?.sha256,
    releaseChannels = releaseChannels,
    isCompatible = isCompatible,
    minSdk = packageManifest.minSdkVersion,
    maxSdk = packageManifest.maxSdkVersion,
    targetSdk = packageManifest.targetSdkVersion,
    nativeCode = nativeCode,
    features = featureNames,
  )
