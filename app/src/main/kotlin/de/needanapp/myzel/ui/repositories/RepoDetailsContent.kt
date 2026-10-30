package de.needanapp.myzel.ui.repositories

import android.widget.Toast
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PauseCircle
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.LocaleListCompat
import de.needanapp.design.components.CardStatus
import de.needanapp.design.components.PillButton
import de.needanapp.design.components.SectionCard
import de.needanapp.design.components.StatusTone
import de.needanapp.myzel.ui.apps.IconCircle
import de.needanapp.myzel.ui.compat.DeleteMirrorDialog
import de.needanapp.myzel.ui.compat.Switch
import de.needanapp.myzel.ui.details.CardSpacing
import de.needanapp.myzel.ui.details.CopyButton
import de.needanapp.myzel.ui.details.DetailRow
import de.needanapp.myzel.ui.details.HeaderGap
import de.needanapp.myzel.ui.details.HeaderIconSize
import de.needanapp.myzel.ui.details.ValueText
import de.needanapp.myzel.ui.details.byline
import de.needanapp.myzel.ui.details.mono
import de.needanapp.myzel.ui.details.pageTitle
import de.needanapp.myzel.ui.nav.needsLogin
import org.fdroid.R
import org.fdroid.database.Repository
import org.fdroid.download.getImageModel
import org.fdroid.repo.RepoUpdateProgress
import org.fdroid.ui.repositories.details.ArchiveState
import org.fdroid.ui.repositories.details.OfficialMirrorItem
import org.fdroid.ui.repositories.details.RepoDetailsInfo
import org.fdroid.ui.repositories.details.UserMirrorItem
import org.fdroid.ui.utils.addressForUi
import org.fdroid.ui.utils.asRelativeTimeString

/**
 * Ersatz für org.fdroid.ui.repositories.details.RepoDetailsContent mit derselben Signatur, über
 * einen ergänzten Import in RepoDetails.kt eingebunden (myzel/tools/apply-imports.py). Aufgebaut
 * wie die App-Seite: Kopf mit großem Symbol und Namen, dann Karten mit Band (Stand mit Apps, Zeiten
 * und dem letzten Fehler), „Apps anzeigen“ als Pille, Beschreibung, Fingerabdruck, Spiegelserver
 * und Einstellungen. Die Kopfleiste mit Teilen, QR-Code, Aktualisieren und Löschen bleibt die von
 * F-Droid.
 *
 * Bei einem Update von F-Droid mit RepoDetailsContent.kt, RepoDetailsHeader.kt, OfficialMirrors.kt
 * und RepoSettings.kt vergleichen.
 */
@Composable
fun RepoDetailsContent(
  info: RepoDetailsInfo,
  onShowAppsClicked: (String, Long) -> Unit,
  modifier: Modifier,
) {
  val repo = info.model.repo as Repository
  val context = LocalContext.current
  val name = repo.getName(LocaleListCompat.getDefault()) ?: "Unknown Repo"
  Column(modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(bottom = 80.dp)) {
    // läuft gerade eine Aktualisierung, zeigt sie ihren Fortschritt auch hier
    val updateState = info.model.updateState
    if (updateState is RepoUpdateProgress) {
      val progress by animateFloatAsState(targetValue = updateState.progress)
      LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
    }
    RepoHeader(
      iconModel =
        repo.getIcon(LocaleListCompat.getDefault())?.getImageModel(repo, info.model.proxy),
      name = name,
      address = repo.addressForUi,
    )
    StateCard(repo, info.model.numberApps)
    if (repo.enabled) {
      PillButton(
        text = stringResource(R.string.repo_num_apps_button),
        onClick = { onShowAppsClicked(name, repo.repoId) },
        modifier = CardSpacing.fillMaxWidth(),
      )
    }
    val description = repo.getDescription(LocaleListCompat.getDefault())?.replace("\n", " ")
    if (!description.isNullOrBlank()) {
      SectionCard(stringResource(R.string.myzel_details_description), CardSpacing) {
        Text(description, style = MaterialTheme.typography.bodyMedium)
      }
    }
    SectionCard(stringResource(R.string.myzel_details_security), CardSpacing) {
      DetailRow(stringResource(R.string.myzel_repo_fingerprint)) {
        val pairs = repo.fingerprint.uppercase().chunked(2).joinToString(" ")
        SelectionContainer(Modifier.weight(1f, fill = false)) {
          Text(pairs, style = MaterialTheme.typography.mono.copy(fontSize = 12.sp))
        }
        Spacer(Modifier.width(4.dp))
        CopyButton(pairs)
      }
    }
    if (info.model.showOfficialMirrors) {
      MirrorsCard(info.model.officialMirrors) { mirror, enabled ->
        info.actions.setMirrorEnabled(mirror, enabled)
      }
    }
    if (info.model.showUserMirrors) {
      UserMirrorsCard(
        mirrors = info.model.userMirrors,
        setMirrorEnabled = { m, e -> info.actions.setMirrorEnabled(m, e) },
        onShareMirror = { mirror -> mirror.share(context, repo.fingerprint) },
        onDeleteMirror = { info.actions.deleteUserMirror(it) },
      )
    }
    SettingsCard(repo, info)
  }
}

/**
 * Großes Symbol, Name und Adresse wie der Kopf der App-Seite; auch in der Vorschau beim Hinzufügen.
 */
@Composable
internal fun RepoHeader(iconModel: Any?, name: String, address: String) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
  ) {
    IconCircle(iconModel, HeaderIconSize)
    Spacer(Modifier.width(HeaderGap))
    Column(Modifier.weight(1f)) {
      Text(
        text = name,
        style = MaterialTheme.typography.pageTitle,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        text = address,
        style = MaterialTheme.typography.byline,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.padding(top = 2.4.dp),
      )
    }
  }
}

/**
 * Band „Eingeschaltet“, „Ausgeschaltet“ oder „Letztes Laden fehlgeschlagen“, darunter die Zahlen.
 */
@Composable
private fun StateCard(repo: Repository, numberOfApps: Int?) {
  val error = repo.lastError
  val needsLogin = repo.needsLogin()
  val status =
    when {
      error != null ->
        CardStatus(
          stringResource(
            if (needsLogin) R.string.myzel_repo_status_login else R.string.myzel_repo_status_error
          ),
          Icons.Rounded.WarningAmber,
          StatusTone.ERROR,
        )
      !repo.enabled ->
        CardStatus(
          stringResource(R.string.myzel_repo_status_disabled),
          Icons.Rounded.PauseCircle,
          StatusTone.NEUTRAL,
        )
      else ->
        CardStatus(
          stringResource(R.string.myzel_repo_status_enabled),
          Icons.Rounded.CheckCircle,
          StatusTone.POSITIVE,
        )
    }
  val never = stringResource(R.string.repositories_last_update_never)
  SectionCard(stringResource(R.string.myzel_repo_state), CardSpacing, status = status) {
    if (numberOfApps != null) {
      DetailRow(stringResource(R.string.myzel_repo_apps)) {
        ValueText(pluralStringResource(R.plurals.repo_num_apps_text, numberOfApps, numberOfApps))
      }
    }
    DetailRow(stringResource(R.string.myzel_repo_published)) {
      ValueText(if (repo.timestamp < 0) never else repo.timestamp.asRelativeTimeString())
    }
    DetailRow(stringResource(R.string.myzel_repo_downloaded)) {
      ValueText(repo.lastUpdated?.asRelativeTimeString() ?: never)
    }
    if (needsLogin) {
      // der Server hat die Anmeldung verweigert (HTTP 401): sagen, wo sie hingehört; die
      // Fehlerseite des Servers dazu wäre nur Rauschen
      Text(
        text = stringResource(R.string.myzel_repo_unauthorized_hint),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.error,
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
      )
    } else if (error != null) {
      SelectionContainer {
        Text(
          text = stringResource(R.string.repo_has_update_error_intro) + "\n\n$error",
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
        )
      }
    }
  }
}

/** Die offiziellen Spiegelserver mit Schalter, die Adresse der Paketquelle zuerst. */
@Composable
private fun MirrorsCard(
  mirrors: List<OfficialMirrorItem>,
  setMirrorEnabled: (org.fdroid.download.Mirror, Boolean) -> Unit,
) {
  SectionCard(stringResource(R.string.repo_official_mirrors), CardSpacing) {
    mirrors.forEach { item ->
      SwitchLine(
        text = item.url,
        leading = item.emoji,
        checked = item.isEnabled,
        onCheckedChange = { setMirrorEnabled(item.mirror, it) },
      )
    }
  }
}

/**
 * Spiegelserver, die der Nutzer selbst hinzugefügt hat: je Adresse ein Schalter, darunter Teilen
 * und Löschen (mit Rückfrage).
 */
@Composable
private fun UserMirrorsCard(
  mirrors: List<UserMirrorItem>,
  setMirrorEnabled: (org.fdroid.download.Mirror, Boolean) -> Unit,
  onShareMirror: (UserMirrorItem) -> Unit,
  onDeleteMirror: (org.fdroid.download.Mirror) -> Unit,
) {
  var toDelete by remember { mutableStateOf<org.fdroid.download.Mirror?>(null) }
  SectionCard(stringResource(R.string.repo_user_mirrors), CardSpacing) {
    mirrors.forEachIndexed { index, item ->
      if (index > 0) Spacer(Modifier.height(8.dp))
      SwitchLine(
        text = item.url,
        checked = item.isEnabled,
        onCheckedChange = { setMirrorEnabled(item.mirror, it) },
      )
      Row {
        TextButton(onClick = { onShareMirror(item) }) { Text(stringResource(R.string.menu_share)) }
        TextButton(
          onClick = { toDelete = item.mirror },
          colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
        ) {
          Text(stringResource(R.string.delete))
        }
      }
    }
  }
  toDelete?.let { mirror ->
    DeleteMirrorDialog(onDismiss = { toDelete = null }, onDelete = { onDeleteMirror(mirror) })
  }
}

/** Archiv der Paketquelle und Zugangsdaten. */
@Composable
private fun SettingsCard(repo: Repository, info: RepoDetailsInfo) {
  SectionCard(stringResource(R.string.menu_settings), CardSpacing) {
    when (val archive = info.model.archiveState) {
      ArchiveState.UNKNOWN -> {
        Text(
          stringResource(R.string.repo_archive_unknown),
          style = MaterialTheme.typography.bodyMedium,
        )
        TextButton(onClick = { info.actions.setArchiveRepoEnabled(true) }) {
          Text(stringResource(R.string.repo_archive_check))
        }
      }
      ArchiveState.LOADING ->
        LinearProgressIndicator(Modifier.fillMaxWidth().padding(vertical = 16.dp))
      else ->
        SwitchLine(
          text = stringResource(R.string.repo_archive_toggle_description),
          checked = archive == ArchiveState.ENABLED,
          onCheckedChange = info.actions::setArchiveRepoEnabled,
        )
    }
    // auch nachträglich: F-Droid zeigt die Anmeldung nur, wenn schon ein Benutzername gespeichert
    // ist
    val savedText = stringResource(R.string.myzel_repo_login_saved)
    val context = LocalContext.current
    LoginForm(repo.username.orEmpty()) { user, password ->
      info.actions.updateUsernameAndPassword(user, password)
      Toast.makeText(context, savedText, Toast.LENGTH_SHORT).show()
    }
  }
}

/**
 * Anmeldung bei einer Paketquelle mit Passwortschutz, statt BasicAuth von F-Droid: die Felder von
 * [LoginFields], Speichern als Pille. Ohne gespeicherten Benutzernamen erklärt ein Satz, wann es
 * sie braucht. Das Passwort wird nicht angezeigt, also bleibt das Feld leer, bis ein neues
 * eingegeben wird. Nach dem Speichern lädt F-Droid die Paketquelle neu.
 */
@Composable
internal fun LoginForm(username: String, onSave: (String, String) -> Unit) {
  var user by remember(username) { mutableStateOf(username) }
  var password by remember { mutableStateOf("") }
  var saved by remember { mutableStateOf(false) }
  Column(Modifier.fillMaxWidth().padding(top = 12.dp).imePadding()) {
    Text(
      stringResource(R.string.myzel_repo_login),
      style = MaterialTheme.typography.titleSmall,
      color = MaterialTheme.colorScheme.primary,
    )
    if (username.isBlank()) {
      Text(
        stringResource(R.string.myzel_repo_login_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 2.dp),
      )
    }
    Spacer(Modifier.height(8.dp))
    LoginFields(
      user = user,
      onUserChange = {
        user = it
        saved = false
      },
      password = password,
      onPasswordChange = {
        password = it
        saved = false
      },
    )
    Spacer(Modifier.height(12.dp))
    PillButton(
      text = stringResource(R.string.repo_basicauth_edit),
      onClick = {
        onSave(user.trim(), password)
        password = ""
        saved = true
      },
      enabled = !saved && user.isNotBlank() && password.isNotEmpty(),
    )
  }
}

/**
 * Benutzername und Passwort einer Paketquelle, das Passwort verdeckt mit Auge zum Aufdecken; auf
 * der Seite einer Paketquelle und beim Hinzufügen.
 */
@Composable
internal fun LoginFields(
  user: String,
  onUserChange: (String) -> Unit,
  password: String,
  onPasswordChange: (String) -> Unit,
  onDone: (() -> Unit)? = null,
) {
  var passwordVisible by remember { mutableStateOf(false) }
  Column(Modifier.fillMaxWidth()) {
    OutlinedTextField(
      value = user,
      onValueChange = onUserChange,
      label = { Text(stringResource(R.string.repo_basicauth_username)) },
      singleLine = true,
      keyboardOptions =
        KeyboardOptions(
          autoCorrectEnabled = false,
          keyboardType = KeyboardType.Text,
          imeAction = ImeAction.Next,
        ),
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
      value = password,
      onValueChange = onPasswordChange,
      label = { Text(stringResource(R.string.repo_basicauth_password)) },
      singleLine = true,
      visualTransformation =
        if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
      trailingIcon = {
        IconButton(onClick = { passwordVisible = !passwordVisible }) {
          Icon(
            if (passwordVisible) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
            contentDescription = null,
          )
        }
      },
      keyboardOptions =
        KeyboardOptions(
          autoCorrectEnabled = false,
          keyboardType = KeyboardType.Password,
          imeAction = ImeAction.Done,
        ),
      keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
      modifier = Modifier.fillMaxWidth(),
    )
  }
}

/** Eine Zeile mit Schalter, als Ganzes antippbar; [leading] (die Flagge) bleibt links vom Text. */
@Composable
private fun SwitchLine(
  text: String,
  checked: Boolean,
  onCheckedChange: (Boolean) -> Unit,
  leading: String? = null,
) {
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      Modifier.fillMaxWidth()
        .toggleable(value = checked, role = Role.Switch, onValueChange = onCheckedChange)
        .padding(vertical = 4.dp),
  ) {
    if (leading != null) {
      Text(leading, style = MaterialTheme.typography.bodyMedium)
      Spacer(Modifier.width(10.dp))
    }
    Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    Spacer(Modifier.width(12.dp))
    Switch(checked = checked, onCheckedChange = null)
  }
}
