package de.needanapp.myzel.ui.details

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import de.needanapp.design.components.CardStatus
import de.needanapp.design.components.PillButton
import de.needanapp.design.components.SectionCard
import de.needanapp.design.components.StatusTone
import de.needanapp.design.theme.LocalDesignColors
import de.needanapp.myzel.diagnose.CertInfo
import de.needanapp.myzel.diagnose.Incompatibility
import de.needanapp.myzel.diagnose.InstallerKind
import de.needanapp.myzel.diagnose.RepoRow
import de.needanapp.myzel.diagnose.RepoStatus
import de.needanapp.myzel.diagnose.UpdateDiagnosis
import de.needanapp.myzel.diagnose.UpdateProblem
import de.needanapp.myzel.diagnose.androidName
import de.needanapp.myzel.diagnose.appLabel
import de.needanapp.myzel.diagnose.installerKind
import de.needanapp.myzel.diagnose.shortSubject
import kotlinx.coroutines.launch
import org.fdroid.BuildConfig
import org.fdroid.R
import org.fdroid.ui.details.LoadedAppDetailsItem

/** Was die Karte zu einem Problem sagt: Überschrift im Band, Erklärung, Lösung und ein Knopf. */
private class ProblemText(
  val title: String,
  val icon: ImageVector,
  val tone: StatusTone,
  val text: String,
  val reasons: List<String> = emptyList(),
  val solution: String,
  val action: String? = null,
  val onAction: (() -> Unit)? = null,
)

/** Eine Zeile der Details; [hash] steht gekürzt darunter, mit Knopf zum Kopieren. */
private class Line(
  val label: String,
  val value: String,
  val hash: String? = null,
  val mono: Boolean = false,
)

/**
 * Die Karte „Update-Diagnose“ für eine installierte App, die sich nicht aktualisieren lässt: im
 * Band, was los ist, darunter die Erklärung in Klartext, was hilft (oft mit Knopf) und aufklappbar
 * alles, was Android und die Paketquellen dazu wissen. „Bericht kopieren“ legt das alles als Text
 * in die Zwischenablage, etwa für eine Fehlermeldung beim Entwickler.
 */
@Composable
internal fun UpdateDiagnosisCard(item: LoadedAppDetailsItem, diagnosis: UpdateDiagnosis) {
  val problem = diagnosis.problem ?: return
  val text = problemText(problem, diagnosis, item)
  val lines = detailLines(diagnosis)
  val rows = diagnosis.rows.map { it to it.status.label() }
  val header = stringResource(R.string.myzel_diag_report_title, item.name, item.app.packageName)
  val report = report(header, text, lines, rows)
  var expanded by rememberSaveable { mutableStateOf(false) }
  val clipboard = LocalClipboard.current
  val scope = rememberCoroutineScope()
  SectionCard(
    stringResource(R.string.myzel_diag_title),
    CardSpacing,
    status = CardStatus(text.title, text.icon, text.tone),
  ) {
    Text(text.text, style = MaterialTheme.typography.small(14.sp).copy(lineHeight = 20.sp))
    text.reasons.forEach {
      Text(
        "•  $it",
        style = MaterialTheme.typography.small(14.sp).copy(lineHeight = 20.sp),
        modifier = Modifier.padding(start = 4.dp, top = 2.dp),
      )
    }
    Text(
      stringResource(R.string.myzel_diag_solution),
      style = MaterialTheme.typography.small(12.sp, FontWeight.SemiBold),
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
    )
    Text(text.solution, style = MaterialTheme.typography.small(14.sp).copy(lineHeight = 20.sp))
    if (text.action != null && text.onAction != null) {
      PillButton(text.action, onClick = text.onAction, modifier = Modifier.padding(top = 12.dp))
    }
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
    ) {
      LinkText(
        stringResource(
          if (expanded) R.string.myzel_diag_hide_details else R.string.myzel_diag_show_details
        )
      ) {
        expanded = !expanded
      }
      Spacer(Modifier.weight(1f))
      LinkText(stringResource(R.string.myzel_diag_copy_report)) {
        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, report))) }
      }
    }
    AnimatedVisibility(expanded) {
      Column(Modifier.padding(top = 12.dp)) {
        lines.forEach { DiagnosisLine(it) }
        if (rows.isNotEmpty()) {
          Text(
            stringResource(R.string.myzel_diag_sources),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
          )
          rows.forEach { (row, status) -> RepoLine(row, status) }
        }
      }
    }
  }
}

@Composable
private fun DiagnosisLine(line: Line) {
  DetailRow(line.label) {
    Column(Modifier.weight(1f, fill = false)) {
      if (line.value.isNotEmpty()) ValueText(line.value, mono = line.mono, size = 14.sp)
      line.hash?.let { hash ->
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            text = hash.shortHash(),
            style = MaterialTheme.typography.mono.copy(fontSize = 12.sp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
          )
          Spacer(Modifier.width(4.dp))
          CopyButton(hash.uppercase().chunked(2).joinToString(":"))
        }
      }
    }
  }
}

/**
 * Eine Paketquelle: oben der Name und rechts ✓/✗ mit dem, was sie bietet, darunter über die ganze
 * Breite die neueste Version und ihre Signatur.
 */
@Composable
private fun RepoLine(row: RepoRow, status: String) {
  val scheme = MaterialTheme.colorScheme
  Column(Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically) {
      Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
        Text(
          row.repo.name,
          style = MaterialTheme.typography.value,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f, fill = false),
        )
        if (row.isPreferred) {
          Text(
            " · " + stringResource(R.string.myzel_diag_preferred),
            style = MaterialTheme.typography.small(12.sp),
            color = LocalDesignColors.current.hint,
            maxLines = 1,
          )
        }
      }
      Text(
        if (row.signerMatches) "✓" else "✗",
        style = MaterialTheme.typography.small(15.sp, FontWeight.Bold),
        color = if (row.signerMatches) scheme.primary else scheme.error,
        modifier = Modifier.padding(start = 8.dp, end = 4.dp),
      )
      Text(status, style = MaterialTheme.typography.small(12.sp), color = scheme.onSurfaceVariant)
    }
    Text(
      listOfNotNull(row.newestVersionName, row.signer?.shortHash()).joinToString("  "),
      style = MaterialTheme.typography.mono.copy(fontSize = 12.sp),
      color = scheme.onSurfaceVariant,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  }
}

@Composable
private fun RepoStatus.label(): String =
  stringResource(
    when (this) {
      RepoStatus.NotNewer -> R.string.myzel_diag_status_not_newer
      RepoStatus.Signer -> R.string.myzel_diag_status_signer
      is RepoStatus.Incompatible -> R.string.myzel_diag_status_incompatible
      is RepoStatus.BetaOnly -> R.string.myzel_diag_status_beta
      is RepoStatus.Ignored -> R.string.myzel_diag_status_ignored
      is RepoStatus.Update -> R.string.myzel_diag_status_update
    }
  )

/** Name einer App auf dem Gerät, etwa „Google Play Store“; sonst der Paketname. */
@Composable
private fun packageName(packageName: String): String {
  val context = LocalContext.current
  return remember(packageName) { appLabel(context, packageName) ?: packageName }
}

@Composable
private fun problemText(
  problem: UpdateProblem,
  diagnosis: UpdateDiagnosis,
  item: LoadedAppDetailsItem,
): ProblemText {
  val context = LocalContext.current
  val installed = diagnosis.input.installed
  val preferred = diagnosis.rows.firstOrNull { it.isPreferred }?.repo
  return when (problem) {
    is UpdateProblem.SignerMismatch -> {
      val kind = installerKind(installed.installer, context.packageName)
      val store = installed.installer?.let { packageName(it) }
      val via =
        when (kind) {
          InstallerKind.STORE -> stringResource(R.string.myzel_diag_installed_via_store, store!!)
          InstallerKind.FILE -> stringResource(R.string.myzel_diag_installed_via_file)
          InstallerKind.ADB,
          InstallerKind.UNKNOWN -> stringResource(R.string.myzel_diag_installed_via_unknown)
          InstallerKind.SELF -> null
        }
      val repo = problem.repoWithSigner
      if (repo != null) {
        ProblemText(
          title = stringResource(R.string.myzel_diag_signer_title),
          icon = Icons.Rounded.Key,
          tone = StatusTone.ERROR,
          text = stringResource(R.string.myzel_diag_signer_text_older, repo.name),
          solution = stringResource(R.string.myzel_diag_signer_solution_older, repo.name),
          action =
            if (repo.repoId == preferred?.repoId) null
            else stringResource(R.string.myzel_diag_prefer_repo, repo.name),
          onAction = { item.actions.onPreferredRepoChanged(repo.repoId) },
        )
      } else {
        ProblemText(
          title = stringResource(R.string.myzel_diag_signer_title),
          icon = Icons.Rounded.Key,
          tone = StatusTone.ERROR,
          text =
            listOfNotNull(stringResource(R.string.myzel_diag_signer_text), via).joinToString(" "),
          solution =
            if (kind == InstallerKind.STORE) {
              stringResource(R.string.myzel_diag_signer_solution_store, store!!)
            } else stringResource(R.string.myzel_diag_signer_solution),
        )
      }
    }
    is UpdateProblem.OtherRepo -> {
      val name = preferred?.name.orEmpty()
      val other = problem.repo.name
      val version = problem.versionName
      val text =
        when (problem.inPreferred) {
          null -> stringResource(R.string.myzel_diag_other_repo_missing, other, version)
          RepoStatus.Signer ->
            stringResource(R.string.myzel_diag_other_repo_signer, name, other, version)
          is RepoStatus.Incompatible ->
            stringResource(R.string.myzel_diag_other_repo_incompatible, name, other, version)
          is RepoStatus.BetaOnly ->
            stringResource(R.string.myzel_diag_other_repo_beta, name, other, version)
          is RepoStatus.Ignored ->
            stringResource(R.string.myzel_diag_other_repo_ignored, name, other, version)
          // RepoStatus.Update kommt hier nicht vor, die Diagnose meldet dann kein OtherRepo
          RepoStatus.NotNewer,
          is RepoStatus.Update ->
            stringResource(R.string.myzel_diag_other_repo_not_newer, name, other, version)
        }
      ProblemText(
        title = stringResource(R.string.myzel_diag_other_repo_title, other),
        icon = Icons.Rounded.SwapHoriz,
        tone = StatusTone.ACCENT,
        text = text,
        solution = stringResource(R.string.myzel_diag_other_repo_solution, other),
        action = stringResource(R.string.myzel_diag_prefer_repo, other),
        onAction = { item.actions.onPreferredRepoChanged(problem.repo.repoId) },
      )
    }
    is UpdateProblem.Incompatible ->
      ProblemText(
        title = stringResource(R.string.myzel_diag_incompatible_title),
        icon = Icons.Rounded.Block,
        tone = StatusTone.ERROR,
        text = stringResource(R.string.myzel_diag_incompatible_text, problem.versionName),
        reasons = problem.reasons.map { it.text(diagnosis) },
        solution = stringResource(R.string.myzel_diag_incompatible_solution),
      )
    is UpdateProblem.BetaOnly ->
      ProblemText(
        title = stringResource(R.string.myzel_diag_beta_title),
        icon = Icons.Rounded.Science,
        tone = StatusTone.ACCENT,
        text = stringResource(R.string.myzel_diag_beta_text, problem.versionName),
        solution = stringResource(R.string.myzel_diag_beta_solution),
        action = stringResource(R.string.myzel_diag_allow_beta),
        onAction = item.actions.allowBetaVersions,
      )
    is UpdateProblem.Ignored ->
      ProblemText(
        title = stringResource(R.string.myzel_diag_ignored_title),
        icon = Icons.Rounded.VisibilityOff,
        tone = StatusTone.NEUTRAL,
        text =
          if (problem.all) stringResource(R.string.myzel_diag_ignored_all_text)
          else stringResource(R.string.myzel_diag_ignored_this_text, problem.versionName),
        solution = stringResource(R.string.myzel_diag_ignored_solution),
        action =
          stringResource(
            if (problem.all) R.string.myzel_diag_unignore_all else R.string.myzel_diag_unignore_this
          ),
        onAction =
          if (problem.all) item.actions.ignoreAllUpdates else item.actions.ignoreThisUpdate,
      )
    is UpdateProblem.LastError -> {
      val canInstall = diagnosis.input.device.canInstall
      ProblemText(
        title = stringResource(R.string.myzel_diag_error_title),
        icon = Icons.Rounded.ErrorOutline,
        tone = StatusTone.ERROR,
        text =
          listOfNotNull(
              stringResource(
                R.string.myzel_diag_error_text,
                problem.error.versionName,
                problem.error.time.format(DateTime),
              ),
              errorExplanation(problem.code)?.let { stringResource(it) },
            )
            .joinToString(" "),
        solution =
          stringResource(
            if (canInstall) R.string.myzel_diag_error_solution
            else R.string.myzel_diag_error_solution_permission
          ),
        action = if (canInstall) null else stringResource(R.string.myzel_diag_allow_install),
        onAction = { openInstallPermission(context) },
      )
    }
  }
}

private fun openInstallPermission(context: Context) {
  val intent =
    Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, "package:${context.packageName}".toUri())
  context.startActivity(intent)
}

/** Was der Fehlercode von Android in Klartext heißt; null: dafür gibt es keinen Text. */
private fun errorExplanation(code: String?): Int? =
  when {
    code == null -> null
    code == "INSTALL_FAILED_UPDATE_INCOMPATIBLE" -> R.string.myzel_diag_error_update_incompatible
    code == "INSTALL_FAILED_VERSION_DOWNGRADE" -> R.string.myzel_diag_error_downgrade
    code == "INSTALL_FAILED_NO_MATCHING_ABIS" -> R.string.myzel_diag_error_abis
    code == "INSTALL_FAILED_DEPRECATED_SDK_VERSION" -> R.string.myzel_diag_error_deprecated_sdk
    code == "INSTALL_FAILED_OLDER_SDK" -> R.string.myzel_diag_error_older_sdk
    code == "INSTALL_FAILED_INSUFFICIENT_STORAGE" -> R.string.myzel_diag_error_storage
    code == "INSTALL_FAILED_USER_RESTRICTED" -> R.string.myzel_diag_error_restricted
    code == "INSTALL_FAILED_ABORTED" -> R.string.myzel_diag_error_aborted
    code == "INSTALL_FAILED_CONFLICTING_PROVIDER" ||
      code == "INSTALL_FAILED_DUPLICATE_PERMISSION" -> R.string.myzel_diag_error_conflict
    code.startsWith("INSTALL_PARSE_FAILED") -> R.string.myzel_diag_error_parse
    else -> null
  }

@Composable
private fun Incompatibility.text(diagnosis: UpdateDiagnosis): String =
  when (this) {
    is Incompatibility.AndroidTooOld ->
      stringResource(R.string.myzel_diag_reason_android_old, androidName(minSdk), minSdk)
    is Incompatibility.AndroidTooNew ->
      stringResource(R.string.myzel_diag_reason_android_new, androidName(maxSdk), maxSdk)
    is Incompatibility.Abi ->
      stringResource(
        R.string.myzel_diag_reason_abi,
        abis.joinToString(", "),
        diagnosis.input.device.abis.joinToString(", "),
      )
    is Incompatibility.Feature -> stringResource(R.string.myzel_diag_reason_feature, feature)
    is Incompatibility.TargetTooOld ->
      stringResource(R.string.myzel_diag_reason_target, targetSdk, required)
    Incompatibility.Other -> stringResource(R.string.myzel_diag_reason_other)
  }

/** Alles, was Android über die installierte App und das Gerät sagt, als Zeilen. */
@Composable
private fun detailLines(diagnosis: UpdateDiagnosis): List<Line> {
  val app = diagnosis.input.installed
  val device = diagnosis.input.device
  val context = LocalContext.current
  val unknown = stringResource(R.string.myzel_diag_unknown)
  val installer = app.installer?.let { installerName(it, context.packageName) }
  val initiator = app.initiator?.let { installerName(it, context.packageName) }
  val source =
    when (app.packageSource) {
      null -> null
      PACKAGE_SOURCE_OTHER -> R.string.myzel_diag_source_other
      PACKAGE_SOURCE_STORE -> R.string.myzel_diag_source_store
      PACKAGE_SOURCE_LOCAL_FILE -> R.string.myzel_diag_source_local
      PACKAGE_SOURCE_DOWNLOADED_FILE -> R.string.myzel_diag_source_downloaded
      else -> R.string.myzel_diag_source_unspecified
    }
  val special = buildList {
    if (app.isUpdatedSystemApp) add(stringResource(R.string.myzel_diag_updated_system_app))
    else if (app.isSystemApp) add(stringResource(R.string.myzel_diag_system_app))
    if (app.splitCount > 0) add(stringResource(R.string.myzel_diag_bundle, app.splitCount + 1))
    if (app.isArchived) add(stringResource(R.string.myzel_diag_archived))
    if (!app.isEnabled) add(stringResource(R.string.myzel_diag_disabled))
    if (app.isDebuggable) add(stringResource(R.string.myzel_diag_debuggable))
    if (app.isTestOnly) add(stringResource(R.string.myzel_diag_test_only))
  }
  val error = diagnosis.input.lastError
  return buildList {
    add(
      Line(
        stringResource(R.string.myzel_diag_installed),
        stringResource(R.string.myzel_diag_version_code, app.versionName, app.versionCode),
      )
    )
    add(
      Line(stringResource(R.string.myzel_diag_first_installed), app.firstInstallTime.format(Date))
    )
    if (app.lastUpdateTime != app.firstInstallTime) {
      add(Line(stringResource(R.string.myzel_diag_last_updated), app.lastUpdateTime.format(Date)))
    }
    add(Line(stringResource(R.string.myzel_diag_installer), installer ?: unknown))
    if (initiator != null && app.initiator != app.installer) {
      add(Line(stringResource(R.string.myzel_diag_initiator), initiator))
    }
    source?.let { add(Line(stringResource(R.string.myzel_diag_source), stringResource(it))) }
    // den Update-Besitzer gibt es erst ab Android 14
    if (device.sdk >= 34) {
      add(
        Line(
          stringResource(R.string.myzel_diag_update_owner),
          app.updateOwner?.let { installerName(it, context.packageName) }
            ?: stringResource(R.string.myzel_diag_none),
        )
      )
    }
    app.signers.forEachIndexed { i, cert ->
      add(cert.line(if (i == 0) stringResource(R.string.myzel_diag_signer) else ""))
    }
    app.pastSigners.forEachIndexed { i, cert ->
      add(cert.line(if (i == 0) stringResource(R.string.myzel_diag_past_signers) else ""))
    }
    if (special.isNotEmpty()) {
      add(Line(stringResource(R.string.myzel_diag_special), special.joinToString(" · ")))
    }
    add(
      Line(
        stringResource(R.string.myzel_diag_sdk),
        stringResource(R.string.myzel_diag_sdk_value, app.targetSdk, app.minSdk),
      )
    )
    add(
      Line(
        stringResource(R.string.myzel_diag_device),
        stringResource(
          R.string.myzel_diag_device_value,
          device.release,
          device.sdk,
          device.abis.joinToString(", "),
        ),
      )
    )
    add(
      Line(
        stringResource(R.string.myzel_diag_can_install),
        stringResource(if (device.canInstall) R.string.myzel_diag_yes else R.string.myzel_diag_no),
      )
    )
    error?.let {
      add(
        Line(
          stringResource(R.string.myzel_diag_last_error),
          stringResource(R.string.myzel_diag_error_value, it.time.format(DateTime), it.versionName),
        )
      )
      it.message?.let { message -> add(Line("", message, mono = true)) }
    }
  }
}

private fun CertInfo.line(label: String) =
  Line(label, subject?.let { shortSubject(it) }.orEmpty(), hash = sha256)

/** „Google Play Store (com.android.vending)“, bei ADB und Myzel selbst nur der Name. */
@Composable
private fun installerName(packageName: String, ownPackage: String): String =
  when (installerKind(packageName, ownPackage)) {
    InstallerKind.ADB -> stringResource(R.string.myzel_diag_adb)
    InstallerKind.SELF -> packageName(packageName)
    else -> {
      val label = packageName(packageName)
      if (label == packageName) packageName else "$label ($packageName)"
    }
  }

/** Der Bericht als Text: Problem, Lösung, Details und Paketquellen, dazu die Myzel-Version. */
private fun report(
  header: String,
  text: ProblemText,
  lines: List<Line>,
  rows: List<Pair<RepoRow, String>>,
): String = buildString {
  appendLine(header)
  appendLine()
  appendLine(text.title)
  appendLine(text.text)
  text.reasons.forEach { appendLine("- $it") }
  appendLine("→ " + text.solution)
  appendLine()
  var label = ""
  lines.forEach { line ->
    if (line.label.isNotEmpty()) label = line.label
    val value = listOfNotNull(line.value.ifEmpty { null }, line.hash).joinToString(" ")
    appendLine("$label: $value")
  }
  if (rows.isNotEmpty()) {
    appendLine()
    rows.forEach { (row, status) ->
      val preferred = if (row.isPreferred) " (*)" else ""
      val match = if (row.signerMatches) "✓" else "✗"
      appendLine(
        "${row.repo.name}$preferred: ${row.newestVersionName} (${row.newestVersionCode}), " +
          "${row.signer ?: "-"} $match, $status"
      )
    }
  }
  appendLine()
  append("Myzel ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
}

// PackageInstaller.PACKAGE_SOURCE_* (API 33), hier ohne Versionsprüfung nutzbar
private const val PACKAGE_SOURCE_OTHER = 1
private const val PACKAGE_SOURCE_STORE = 2
private const val PACKAGE_SOURCE_LOCAL_FILE = 3
private const val PACKAGE_SOURCE_DOWNLOADED_FILE = 4
