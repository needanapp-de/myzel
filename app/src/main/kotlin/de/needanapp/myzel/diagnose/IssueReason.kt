package de.needanapp.myzel.diagnose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fdroid.R
import org.fdroid.database.AppIssue
import org.fdroid.database.KnownVulnerability
import org.fdroid.database.NoCompatibleSigner
import org.fdroid.database.NotAvailable
import org.fdroid.database.UpdateInOtherRepo

/** Paketquelle und Store zu einem Problem, für den Kurzgrund. */
private data class IssueNames(val repo: String?, val store: String?)

/**
 * Der Grund in wenigen Worten, warum sich die App [packageName] nicht aktualisieren lässt, für
 * „Meine Apps“, etwa „Signatur passt nur bei IzzyOnDroid · über Google Play Store installiert“. Die
 * genaue Diagnose steht auf der App-Seite.
 */
@Composable
fun rememberIssueReason(packageName: String, issue: AppIssue): String {
  val context = LocalContext.current
  val preview = LocalInspectionMode.current
  val repoId =
    when (issue) {
      is NoCompatibleSigner -> issue.repoIdWithCompatibleSigner
      is UpdateInOtherRepo -> issue.repoIdWithUpdate
      else -> null
    }
  val entry = remember {
    if (preview) null
    else EntryPointAccessors.fromApplication(context, DiagnosisEntryPoint::class.java)
  }
  val names by
    produceState(IssueNames(null, null), packageName, issue) {
      if (entry == null) return@produceState
      value =
        withContext(Dispatchers.IO) {
          val repo = repoId?.let { id ->
            entry.repoManager().getRepository(id)?.let {
              it.getName(LocaleListCompat.getDefault()) ?: it.address
            }
          }
          // der Store zählt nur, wenn die Signatur nicht passt
          val installer =
            if (issue is NoCompatibleSigner) installerOf(context, packageName) else null
          val store =
            installer
              ?.takeIf { installerKind(it, context.packageName) == InstallerKind.STORE }
              ?.let { appLabel(context, it) ?: it }
          IssueNames(repo, store)
        }
    }
  val reason =
    when (issue) {
      is NoCompatibleSigner ->
        names.repo?.let { stringResource(R.string.myzel_issue_signer_other, it) }
          ?: stringResource(R.string.myzel_issue_signer_none)
      is UpdateInOtherRepo ->
        names.repo?.let { stringResource(R.string.myzel_issue_other_repo, it) }
          ?: stringResource(R.string.myzel_details_status_other_repo)
      NotAvailable -> stringResource(R.string.myzel_issue_not_available)
      is KnownVulnerability -> stringResource(R.string.myzel_issue_vulnerable)
    }
  val via = names.store?.let { stringResource(R.string.myzel_issue_installed_via, it) }
  return listOfNotNull(reason, via).joinToString(" · ")
}
