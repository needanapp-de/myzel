package de.needanapp.myzel.diagnose

import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import org.junit.Test

internal class UpdateDiagnosisTest {

  private val dev = "e0206724"
  private val fdroidKey = "b9d04158"
  private val fdroid = RepoInfo(1, "F-Droid")
  private val izzy = RepoInfo(2, "IzzyOnDroid")

  private val installed =
    InstalledAppInfo(
      packageName = "eu.faircode.email",
      versionName = "1.2337",
      versionCode = 2337,
      firstInstallTime = 1_000,
      lastUpdateTime = 2_000,
      signers = listOf(CertInfo(dev, "O=FairCode")),
      pastSigners = emptyList(),
      installer = "com.android.vending",
      initiator = "com.android.vending",
      packageSource = null,
      updateOwner = null,
      isSystemApp = false,
      isUpdatedSystemApp = false,
      splitCount = 0,
      isArchived = false,
      isEnabled = true,
      isDebuggable = false,
      isTestOnly = false,
      minSdk = 23,
      targetSdk = 35,
    )

  private val device =
    DeviceInfo(
      sdk = 37,
      release = "17",
      abis = listOf("arm64-v8a"),
      features = setOf("android.hardware.touchscreen"),
      canInstall = true,
    )

  private fun version(
    repo: RepoInfo,
    code: Long,
    signer: String?,
    channels: List<String> = emptyList(),
    compatible: Boolean = true,
    minSdk: Int? = 23,
    targetSdk: Int? = 35,
    nativeCode: List<String> = emptyList(),
    features: List<String> = emptyList(),
  ) =
    RepoVersion(
      repoId = repo.repoId,
      versionName = "1.$code",
      versionCode = code,
      signers = signer?.let { listOf(it) },
      releaseChannels = channels,
      isCompatible = compatible,
      minSdk = minSdk,
      maxSdk = null,
      targetSdk = targetSdk,
      nativeCode = nativeCode,
      features = features,
    )

  private fun input(
    versions: List<RepoVersion>,
    preferred: RepoInfo = fdroid,
    allowsBeta: Boolean = false,
    ignoredVersionCode: Long = 0,
    lastError: InstallError? = null,
    installed: InstalledAppInfo = this.installed,
  ) =
    DiagnosisInput(
      installed = installed,
      device = device,
      repos = listOf(fdroid, izzy),
      versions = versions,
      preferredRepoId = preferred.repoId,
      allowsBeta = allowsBeta,
      ignoresAll = ignoredVersionCode == Long.MAX_VALUE,
      ignoredVersionCode = ignoredVersionCode,
      lastError = lastError,
    )

  @Test
  fun `fairemail from play only updates from izzyondroid`() {
    val result = diagnose(input(listOf(version(fdroid, 2338, fdroidKey), version(izzy, 2338, dev))))
    val problem = assertIs<UpdateProblem.OtherRepo>(result.problem)
    assertEquals(izzy, problem.repo)
    assertEquals("1.2338", problem.versionName)
    assertEquals(RepoStatus.Signer, problem.inPreferred)
    assertEquals(listOf(false, true), result.rows.map { it.signerMatches })
    assertEquals(listOf(true, false), result.rows.map { it.isPreferred })
  }

  @Test
  fun `no problem when the preferred source has the update`() {
    val result = diagnose(input(listOf(version(fdroid, 2338, dev))))
    assertNull(result.problem)
  }

  @Test
  fun `no problem when nothing is newer`() {
    val result = diagnose(input(listOf(version(fdroid, 2337, fdroidKey))))
    assertNull(result.problem)
  }

  @Test
  fun `no source has the signature`() {
    val result = diagnose(input(listOf(version(fdroid, 2338, fdroidKey))))
    assertEquals(UpdateProblem.SignerMismatch(null), result.problem)
  }

  @Test
  fun `a source has the signature but nothing newer yet`() {
    val result = diagnose(input(listOf(version(fdroid, 2338, fdroidKey), version(izzy, 2337, dev))))
    assertEquals(UpdateProblem.SignerMismatch(izzy), result.problem)
  }

  @Test
  fun `an earlier key of a rotated signature counts`() {
    val rotated =
      installed.copy(
        signers = listOf(CertInfo("neu", null)),
        pastSigners = listOf(CertInfo(dev, null)),
      )
    val result = diagnose(input(listOf(version(fdroid, 2338, dev)), installed = rotated))
    assertNull(result.problem)
  }

  @Test
  fun `versions without a signer fit everything`() {
    val result = diagnose(input(listOf(version(fdroid, 2338, null))))
    assertNull(result.problem)
  }

  @Test
  fun `incompatible versions name their reasons`() {
    val result =
      diagnose(
        input(
          listOf(
            version(
              fdroid,
              2338,
              dev,
              compatible = false,
              minSdk = 38,
              targetSdk = 22,
              nativeCode = listOf("x86_64"),
              features = listOf("android.hardware.nfc", "android.hardware.touchscreen"),
            )
          )
        )
      )
    val problem = assertIs<UpdateProblem.Incompatible>(result.problem)
    assertEquals(
      listOf(
        Incompatibility.AndroidTooOld(38),
        Incompatibility.TargetTooOld(22, 24),
        Incompatibility.Abi(listOf("x86_64")),
        Incompatibility.Feature("android.hardware.nfc"),
      ),
      problem.reasons,
    )
  }

  @Test
  fun `incompatible without a known reason`() {
    val result = diagnose(input(listOf(version(fdroid, 2338, dev, compatible = false))))
    val problem = assertIs<UpdateProblem.Incompatible>(result.problem)
    assertEquals(listOf(Incompatibility.Other), problem.reasons)
  }

  @Test
  fun `beta only, and fine once beta is allowed`() {
    val versions = listOf(version(fdroid, 2338, dev, channels = listOf("Beta")))
    assertEquals(UpdateProblem.BetaOnly("1.2338"), diagnose(input(versions)).problem)
    assertNull(diagnose(input(versions, allowsBeta = true)).problem)
  }

  @Test
  fun `ignored updates`() {
    val versions = listOf(version(fdroid, 2338, dev))
    assertEquals(
      UpdateProblem.Ignored(all = true, versionName = "1.2338"),
      diagnose(input(versions, ignoredVersionCode = Long.MAX_VALUE)).problem,
    )
    assertEquals(
      UpdateProblem.Ignored(all = false, versionName = "1.2338"),
      diagnose(input(versions, ignoredVersionCode = 2338)).problem,
    )
  }

  @Test
  fun `the last error counts only after the last update`() {
    val message = "INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package signatures do not match"
    val versions = listOf(version(fdroid, 2338, dev))
    val newer = InstallError(time = 3_000, versionName = "1.2338", message = message)
    val problem =
      assertIs<UpdateProblem.LastError>(diagnose(input(versions, lastError = newer)).problem)
    assertEquals("INSTALL_FAILED_UPDATE_INCOMPATIBLE", problem.code)
    val older = newer.copy(time = 1_500)
    assertNull(diagnose(input(versions, lastError = older)).problem)
  }

  @Test
  fun `error codes`() {
    assertEquals(
      "INSTALL_PARSE_FAILED_NO_CERTIFICATES",
      installErrorCode("x INSTALL_PARSE_FAILED_NO_CERTIFICATES: y"),
    )
    assertNull(installErrorCode("Connection reset"))
    assertNull(installErrorCode(null))
  }

  @Test
  fun `installer kinds`() {
    val own = "de.needanapp.myzel"
    assertEquals(InstallerKind.STORE, installerKind("com.android.vending", own))
    assertEquals(InstallerKind.FILE, installerKind("com.google.android.packageinstaller", own))
    assertEquals(InstallerKind.ADB, installerKind("com.android.shell", own))
    assertEquals(InstallerKind.SELF, installerKind(own, own))
    assertEquals(InstallerKind.UNKNOWN, installerKind(null, own))
  }

  @Test
  fun `short subjects`() {
    assertEquals(
      "Marcel Bokhorst · FairCode",
      shortSubject("CN=Marcel Bokhorst,OU=Unknown,O=FairCode,L=Unknown,C=NL"),
    )
    assertEquals("Example, Inc", shortSubject("O=Example\\, Inc,C=DE"))
    assertEquals("C=DE", shortSubject("C=DE"))
  }

  @Test
  fun `android names`() {
    assertEquals("12L", androidName(32))
    assertEquals("15", androidName(35))
    assertEquals("17", androidName(37))
  }
}
