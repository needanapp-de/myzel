package de.needanapp.myzel.diagnose

import org.fdroid.CompatibilityCheckerUtils.minInstallableTargetSdk

/** Eine Version aus einer Paketquelle, soweit die Diagnose sie braucht. */
data class RepoVersion(
  val repoId: Long,
  val versionName: String,
  val versionCode: Long,
  /** SHA-256 der Signaturschlüssel in Kleinbuchstaben; null: keine Angabe, passt zu allem */
  val signers: List<String>?,
  val releaseChannels: List<String>,
  /** wie die Datenbank es beim Einlesen der Paketquelle bewertet hat */
  val isCompatible: Boolean,
  val minSdk: Int?,
  val maxSdk: Int?,
  val targetSdk: Int?,
  val nativeCode: List<String>,
  val features: List<String>,
)

/** Eine Paketquelle mit Namen, bevorzugte zuerst, sonst nach Gewicht. */
data class RepoInfo(val repoId: Long, val name: String)

/** Alles, was die Diagnose über eine installierte App braucht. */
data class DiagnosisInput(
  val installed: InstalledAppInfo,
  val device: DeviceInfo,
  val repos: List<RepoInfo>,
  /** die Versionen aus allen eingeschalteten Paketquellen, ohne Uhr-Versionen */
  val versions: List<RepoVersion>,
  val preferredRepoId: Long,
  val allowsBeta: Boolean,
  val ignoresAll: Boolean,
  /** bis zu diesem Versionscode werden Updates ignoriert, 0: keine */
  val ignoredVersionCode: Long,
  val lastError: InstallError?,
)

/** Warum eine Version nicht auf das Gerät passt. */
sealed interface Incompatibility {
  data class AndroidTooOld(val minSdk: Int) : Incompatibility

  data class AndroidTooNew(val maxSdk: Int) : Incompatibility

  /** die Version enthält nur Code für [abis], das Gerät kann keine davon */
  data class Abi(val abis: List<String>) : Incompatibility

  data class Feature(val feature: String) : Incompatibility

  /** Android verweigert Apps mit so altem Ziel-SDK, ab [required] geht es */
  data class TargetTooOld(val targetSdk: Int, val required: Int) : Incompatibility

  /** die Datenbank sagt „passt nicht“, aber keiner der Gründe oben trifft zu */
  data object Other : Incompatibility
}

/** Wie es um Updates aus einer Paketquelle steht, von „gar nichts Neueres“ bis „geht“. */
sealed interface RepoStatus {
  /** keine Version, die neuer ist als die installierte */
  data object NotNewer : RepoStatus

  /** neuere Versionen gibt es nur mit anderer Signatur */
  data object Signer : RepoStatus

  /** neuere Versionen mit passender Signatur passen nicht aufs Gerät */
  data class Incompatible(val versionName: String, val reasons: List<Incompatibility>) : RepoStatus

  /** neuere passende Versionen gibt es nur als Beta */
  data class BetaOnly(val versionName: String) : RepoStatus

  /** es gäbe ein Update, aber es wird ignoriert */
  data class Ignored(val versionName: String) : RepoStatus

  data class Update(val versionName: String, val versionCode: Long) : RepoStatus
}

/** Eine Zeile der Signaturtabelle: was eine Paketquelle von der App hat. */
data class RepoRow(
  val repo: RepoInfo,
  val isPreferred: Boolean,
  /** die neueste Version dieser Paketquelle */
  val newestVersionName: String,
  val newestVersionCode: Long,
  /** Signatur der neuesten Version; null: keine Angabe */
  val signer: String?,
  /** ob die Signatur der neuesten Version zur installierten App passt */
  val signerMatches: Boolean,
  val status: RepoStatus,
)

/** Warum sich eine App nicht aktualisieren lässt, mit dem, was dagegen hilft. */
sealed interface UpdateProblem {
  /**
   * Keine Paketquelle hat eine neuere Version mit der Signatur der Installation.
   *
   * @param repoWithSigner eine Paketquelle, die überhaupt Versionen mit passender Signatur hat,
   *   aber noch keine neuere; null: keine
   */
  data class SignerMismatch(val repoWithSigner: RepoInfo?) : UpdateProblem

  /** Neuere Versionen mit passender Signatur passen nicht aufs Gerät. */
  data class Incompatible(val versionName: String, val reasons: List<Incompatibility>) :
    UpdateProblem

  /** Neuere Versionen gibt es nur als Beta, Beta ist für die App aber aus. */
  data class BetaOnly(val versionName: String) : UpdateProblem

  data class Ignored(val all: Boolean, val versionName: String) : UpdateProblem

  /**
   * Das Update gibt es nur aus einer anderen als der bevorzugten Paketquelle.
   *
   * @param inPreferred warum es aus der bevorzugten nicht geht; null: sie hat die App nicht
   */
  data class OtherRepo(val repo: RepoInfo, val versionName: String, val inPreferred: RepoStatus?) :
    UpdateProblem

  /**
   * Ein Update wäre möglich, der letzte Versuch ist aber gescheitert.
   *
   * @param code der Fehlercode von Android, etwa „INSTALL_FAILED_UPDATE_INCOMPATIBLE“
   */
  data class LastError(val error: InstallError, val code: String?) : UpdateProblem
}

/** Das Ergebnis: das Problem (null: keins) und die Details dazu. */
data class UpdateDiagnosis(
  val input: DiagnosisInput,
  val problem: UpdateProblem?,
  val rows: List<RepoRow>,
)

private val ERROR_CODE = Regex("""INSTALL_(?:PARSE_)?FAILED_[A-Z_]+""")

/** Der Fehlercode von Android in [message], etwa „INSTALL_FAILED_UPDATE_INCOMPATIBLE“. */
fun installErrorCode(message: String?): String? = message?.let {
  ERROR_CODE.find(it)?.value?.trimEnd('_')
}

/** Findet heraus, warum sich die App nicht aktualisieren lässt. */
fun diagnose(input: DiagnosisInput): UpdateDiagnosis {
  val installed = input.installed
  val ownSigners = (installed.signers + installed.pastSigners).mapTo(HashSet()) { it.sha256 }
  val byRepo = input.versions.groupBy { it.repoId }
  val repoOrder =
    input.repos.sortedBy { if (it.repoId == input.preferredRepoId) 0 else 1 } +
      byRepo.keys.filter { id -> input.repos.none { it.repoId == id } }.map { RepoInfo(it, "#$it") }
  val rows = repoOrder.mapNotNull { repo ->
    val versions = byRepo[repo.repoId]?.sortedByDescending { it.versionCode }
    val newest = versions?.firstOrNull() ?: return@mapNotNull null
    RepoRow(
      repo = repo,
      isPreferred = repo.repoId == input.preferredRepoId,
      newestVersionName = newest.versionName,
      newestVersionCode = newest.versionCode,
      signer = newest.signers?.firstOrNull(),
      signerMatches = newest.signerMatches(ownSigners),
      status = status(versions, input, ownSigners),
    )
  }
  return UpdateDiagnosis(input, problem(input, rows, ownSigners), rows)
}

private fun RepoVersion.signerMatches(own: Set<String>): Boolean =
  signers == null || signers.any { it in own }

private fun status(
  versions: List<RepoVersion>,
  input: DiagnosisInput,
  ownSigners: Set<String>,
): RepoStatus {
  val newer = versions.filter { it.versionCode > input.installed.versionCode }
  if (newer.isEmpty()) return RepoStatus.NotNewer
  val signed = newer.filter { it.signerMatches(ownSigners) }
  if (signed.isEmpty()) return RepoStatus.Signer
  val compatible = signed.filter { it.isCompatible }
  if (compatible.isEmpty()) {
    val newest = signed.first()
    return RepoStatus.Incompatible(newest.versionName, incompatibilities(newest, input.device))
  }
  val allowed = compatible.filter { it.isStable || input.allowsBeta }
  if (allowed.isEmpty()) return RepoStatus.BetaOnly(compatible.first().versionName)
  val update = allowed.first()
  if (input.ignoresAll || update.versionCode <= input.ignoredVersionCode) {
    return RepoStatus.Ignored(update.versionName)
  }
  return RepoStatus.Update(update.versionName, update.versionCode)
}

private val RepoVersion.isStable: Boolean
  get() = releaseChannels.isEmpty()

/** Die Gründe, warum [version] nicht auf das Gerät passt. */
fun incompatibilities(version: RepoVersion, device: DeviceInfo): List<Incompatibility> {
  val reasons = buildList {
    version.minSdk?.let { if (device.sdk < it) add(Incompatibility.AndroidTooOld(it)) }
    version.maxSdk?.let { if (device.sdk > it) add(Incompatibility.AndroidTooNew(it)) }
    val required = minInstallableTargetSdk(device.sdk)
    version.targetSdk?.let { if (it < required) add(Incompatibility.TargetTooOld(it, required)) }
    if (version.nativeCode.isNotEmpty() && version.nativeCode.none { it in device.abis }) {
      add(Incompatibility.Abi(version.nativeCode))
    }
    version.features
      .filterNot { it in device.features }
      .forEach { add(Incompatibility.Feature(it)) }
  }
  return reasons.ifEmpty { listOf(Incompatibility.Other) }
}

private fun problem(
  input: DiagnosisInput,
  rows: List<RepoRow>,
  ownSigners: Set<String>,
): UpdateProblem? {
  if (rows.all { it.status == RepoStatus.NotNewer }) return null
  val preferred = rows.firstOrNull { it.isPreferred }
  val preferredStatus = preferred?.status
  if (preferredStatus is RepoStatus.Update) {
    val error = input.lastError ?: return null
    // eine spätere Installation hat den Fehler überholt
    if (error.time <= input.installed.lastUpdateTime) return null
    return UpdateProblem.LastError(error, installErrorCode(error.message))
  }
  rows
    .firstOrNull { it.status is RepoStatus.Update }
    ?.let { row ->
      val update = row.status as RepoStatus.Update
      return UpdateProblem.OtherRepo(row.repo, update.versionName, preferredStatus)
    }
  // keine Paketquelle hat ein Update: die Quelle, die am weitesten kommt, erklärt es am besten
  val best =
    rows
      .map { it.status }
      .maxBy {
        when (it) {
          RepoStatus.NotNewer -> 0
          RepoStatus.Signer -> 1
          is RepoStatus.Incompatible -> 2
          is RepoStatus.BetaOnly -> 3
          is RepoStatus.Ignored -> 4
          is RepoStatus.Update -> 5
        }
      }
  return when (best) {
    is RepoStatus.Ignored -> UpdateProblem.Ignored(input.ignoresAll, best.versionName)
    is RepoStatus.BetaOnly -> UpdateProblem.BetaOnly(best.versionName)
    is RepoStatus.Incompatible -> UpdateProblem.Incompatible(best.versionName, best.reasons)
    else -> {
      val withSigner = rows.firstOrNull { row ->
        input.versions.any { it.repoId == row.repo.repoId && it.signerMatches(ownSigners) }
      }
      UpdateProblem.SignerMismatch(withSigner?.repo)
    }
  }
}
