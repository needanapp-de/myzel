package de.needanapp.myzel.diagnose

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import androidx.core.content.pm.PackageInfoCompat
import java.io.ByteArrayInputStream
import java.security.cert.CertificateException
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import org.fdroid.utils.sha256

/**
 * Ein Signaturzertifikat.
 *
 * @param subject Inhaber aus dem Zertifikat, etwa „O=FairCode“; null, wenn es nicht lesbar ist
 */
data class CertInfo(val sha256: String, val subject: String?)

/** Was Android über eine installierte App verrät, für die Update-Diagnose. */
data class InstalledAppInfo(
  val packageName: String,
  val versionName: String,
  val versionCode: Long,
  val firstInstallTime: Long,
  val lastUpdateTime: Long,
  /** die Schlüssel, mit denen die installierte APK signiert ist */
  val signers: List<CertInfo>,
  /** frühere Schlüssel nach einem Schlüsselwechsel (APK-Signatur v3) */
  val pastSigners: List<CertInfo>,
  /** Store oder Installer, der die App installiert hat; null: unbekannt, etwa per ADB */
  val installer: String?,
  /** wer die Installation angestoßen hat, etwa ein Browser oder Dateimanager */
  val initiator: String?,
  /** Art der Quelle ab Android 13 (`PackageInstaller.PACKAGE_SOURCE_*`); null: nicht bekannt */
  val packageSource: Int?,
  /** Store, dem die Updates gehören, ab Android 14; null: keiner */
  val updateOwner: String?,
  val isSystemApp: Boolean,
  val isUpdatedSystemApp: Boolean,
  /** Teil-APKs neben der Haupt-APK, etwa bei App-Bundles aus dem Play Store */
  val splitCount: Int,
  /** ab Android 15: Code entfernt, nur Daten und Symbol sind noch da */
  val isArchived: Boolean,
  val isEnabled: Boolean,
  val isDebuggable: Boolean,
  val isTestOnly: Boolean,
  val minSdk: Int,
  val targetSdk: Int,
)

/** Das Gerät, soweit es für die Wahl einer Version zählt. */
data class DeviceInfo(
  val sdk: Int,
  val release: String,
  val abis: List<String>,
  val features: Set<String>,
  /** ob Myzel Apps installieren darf („Unbekannte Apps installieren“) */
  val canInstall: Boolean,
)

/** Liest [packageName] aus; null, wenn die App nicht installiert ist. */
fun readInstalledApp(context: Context, packageName: String): InstalledAppInfo? {
  val pm = context.packageManager
  val info =
    try {
      pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
    } catch (_: PackageManager.NameNotFoundException) {
      return null
    }
  val app = info.applicationInfo ?: return null
  val signing = info.signingInfo
  val signers = signing?.apkContentsSigners.orEmpty().map { it.toCertInfo() }
  val current = signers.mapTo(HashSet()) { it.sha256 }
  // die Geschichte enthält auch den aktuellen Schlüssel
  val past =
    if (signing == null || signing.hasMultipleSigners()) emptyList()
    else
      signing.signingCertificateHistory
        .orEmpty()
        .map { it.toCertInfo() }
        .filter { it.sha256 !in current }
  val source =
    try {
      pm.getInstallSourceInfo(packageName)
    } catch (_: PackageManager.NameNotFoundException) {
      null
    } catch (_: SecurityException) {
      null
    }
  return InstalledAppInfo(
    packageName = packageName,
    versionName = info.versionName.orEmpty(),
    versionCode = PackageInfoCompat.getLongVersionCode(info),
    firstInstallTime = info.firstInstallTime,
    lastUpdateTime = info.lastUpdateTime,
    signers = signers,
    pastSigners = past,
    installer = source?.installingPackageName,
    initiator = source?.initiatingPackageName,
    packageSource =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) source?.packageSource else null,
    updateOwner =
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
        source?.updateOwnerPackageName
      } else null,
    isSystemApp = app.flags and ApplicationInfo.FLAG_SYSTEM != 0,
    isUpdatedSystemApp = app.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP != 0,
    splitCount = app.splitSourceDirs?.size ?: 0,
    isArchived = Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM && app.isArchived,
    isEnabled = app.enabled,
    isDebuggable = app.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0,
    isTestOnly = app.flags and ApplicationInfo.FLAG_TEST_ONLY != 0,
    minSdk = app.minSdkVersion,
    targetSdk = app.targetSdkVersion,
  )
}

/** Liest das Gerät aus. */
fun readDevice(context: Context): DeviceInfo {
  val pm = context.packageManager
  return DeviceInfo(
    sdk = Build.VERSION.SDK_INT,
    release = Build.VERSION.RELEASE,
    abis = Build.SUPPORTED_ABIS.toList(),
    features = pm.systemAvailableFeatures.mapNotNullTo(HashSet()) { it.name },
    canInstall = pm.canRequestPackageInstalls(),
  )
}

/** Der Name von [packageName], so wie das Gerät ihn kennt, etwa „Google Play Store“; sonst null. */
fun appLabel(context: Context, packageName: String): String? =
  try {
    val pm = context.packageManager
    pm.getApplicationInfo(packageName, 0).loadLabel(pm).toString()
  } catch (_: PackageManager.NameNotFoundException) {
    null
  }

private fun Signature.toCertInfo(): CertInfo {
  val bytes = toByteArray()
  val subject =
    try {
      val cert =
        CertificateFactory.getInstance("X.509").generateCertificate(ByteArrayInputStream(bytes))
          as X509Certificate
      cert.subjectX500Principal.name
    } catch (_: CertificateException) {
      null
    }
  return CertInfo(sha256(bytes).lowercase(), subject)
}

/** Wer eine App installiert hat, grob eingeteilt. */
enum class InstallerKind {
  /** ein Store wie Google Play oder ein anderer F-Droid-Client */
  STORE,
  /** das Paketinstallationsprogramm von Android, also aus einer Datei */
  FILE,
  ADB,
  /** Myzel selbst */
  SELF,
  /** Android sagt es nicht, etwa bei vorinstallierten Apps */
  UNKNOWN,
}

private val PACKAGE_INSTALLERS =
  setOf(
    "com.android.packageinstaller",
    "com.google.android.packageinstaller",
    "com.samsung.android.packageinstaller",
  )

private const val SHELL = "com.android.shell"

/** Ordnet [installer] ein; [ownPackage] ist der Paketname von Myzel. */
fun installerKind(installer: String?, ownPackage: String): InstallerKind =
  when (installer) {
    null -> InstallerKind.UNKNOWN
    ownPackage -> InstallerKind.SELF
    SHELL -> InstallerKind.ADB
    in PACKAGE_INSTALLERS -> InstallerKind.FILE
    else -> InstallerKind.STORE
  }

/** Name und Organisation aus einem X.500-Namen, etwa „Marcel Bokhorst · FairCode“. */
fun shortSubject(subject: String): String {
  val parts =
    Regex("""(?:^|,)\s*(CN|O)=((?:\\.|[^,])*)""").findAll(subject).associate {
      it.groupValues[1] to it.groupValues[2].replace("\\", "").trim()
    }
  return listOfNotNull(parts["CN"], parts["O"])
    .filter { it.isNotEmpty() && !it.equals("unknown", ignoreCase = true) }
    .distinct()
    .joinToString(" · ")
    .ifEmpty { subject }
}

/** Die Android-Version zur API-Stufe [sdk], etwa „15“ für 35. */
fun androidName(sdk: Int): String =
  when (sdk) {
    21 -> "5.0"
    22 -> "5.1"
    23 -> "6.0"
    24 -> "7.0"
    25 -> "7.1"
    26 -> "8.0"
    27 -> "8.1"
    28 -> "9"
    29 -> "10"
    30 -> "11"
    31 -> "12"
    32 -> "12L"
    in 33..99 -> (sdk - 20).toString()
    else -> "API $sdk"
  }

/** Nur der Installer von [packageName], leichter als [readInstalledApp]; null: unbekannt. */
fun installerOf(context: Context, packageName: String): String? =
  try {
    context.packageManager.getInstallSourceInfo(packageName).installingPackageName
  } catch (_: PackageManager.NameNotFoundException) {
    null
  } catch (_: SecurityException) {
    null
  }
