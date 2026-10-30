package de.needanapp.myzel.ui.settings

import android.net.Uri
import android.widget.Toast
import android.widget.Toast.LENGTH_LONG
import android.widget.Toast.LENGTH_SHORT
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.CreateDocument
import androidx.activity.result.contract.ActivityResultContracts.OpenDocument
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.SettingsBackupRestore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dagger.hilt.android.EntryPointAccessors
import de.needanapp.design.components.ContentDialog
import de.needanapp.design.components.SignalLinearProgressIndicator
import de.needanapp.myzel.backup.BackupEntryPoint
import de.needanapp.myzel.backup.BackupException
import de.needanapp.myzel.backup.BackupFile
import de.needanapp.myzel.backup.BackupManager
import de.needanapp.myzel.backup.BackupSection
import de.needanapp.myzel.backup.favoriteCount
import de.needanapp.myzel.backup.repositoryCount
import de.needanapp.myzel.ui.compat.Checkbox
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import mu.KotlinLogging
import org.fdroid.R
import org.fdroid.ui.utils.launchSafe

// Sichern und Wiederherstellen wie in needafile (de.needanapp.needafile.backup.BackupDialogs),
// nur ohne Passwort: Die Sicherung von Myzel enthält keine geheimen Daten.

private val log = KotlinLogging.logger {}

@Composable
private fun rememberBackupManager(): BackupManager {
  val context = LocalContext.current
  return remember(context) {
    EntryPointAccessors.fromApplication(context, BackupEntryPoint::class.java).backupManager()
  }
}

/**
 * Eine Sicherung erstellen: erst die Bereiche wählen, dann die Datei (Dateiauswahl des Systems).
 * [onDismiss] schließt den Ablauf, auch wenn die Sicherung gespeichert ist.
 */
@Composable
fun ExportBackupFlow(onDismiss: () -> Unit) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val backupManager = rememberBackupManager()
  val sections = remember { mutableStateListOf(*BackupSection.entries.toTypedArray()) }
  var isWorking by remember { mutableStateOf(false) }

  val createFile =
    rememberLauncherForActivityResult(CreateDocument(BACKUP_MIME_TYPE)) { uri ->
      if (uri == null) return@rememberLauncherForActivityResult
      isWorking = true
      scope.launch {
        try {
          backupManager.export(sections.toSet(), uri)
          Toast.makeText(context, R.string.myzel_backup_exported, LENGTH_SHORT).show()
          onDismiss()
        } catch (e: CancellationException) {
          throw e
        } catch (e: Exception) {
          log.error(e) { "Error exporting backup" }
          isWorking = false
          Toast.makeText(context, R.string.myzel_backup_error_write, LENGTH_LONG).show()
        }
      }
    }

  ContentDialog(
    title = stringResource(R.string.myzel_backup_export),
    confirmLabel = stringResource(R.string.myzel_backup_next),
    onConfirm = {
      val date = SimpleDateFormat(FILE_DATE_PATTERN, Locale.ROOT).format(Date())
      val name = context.getString(R.string.myzel_backup_file_name, date)
      if (!createFile.launchSafe(name)) {
        Toast.makeText(context, R.string.no_handler_app_generic, LENGTH_SHORT).show()
      }
    },
    onDismiss = { if (!isWorking) onDismiss() },
    confirmEnabled = sections.isNotEmpty() && !isWorking,
    icon = Icons.Outlined.SettingsBackupRestore,
  ) {
    SectionChoice(BackupSection.entries, sections, enabled = !isWorking)
    Spacer(Modifier.height(12.dp))
    Text(
      stringResource(R.string.myzel_backup_export_hint),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (isWorking) Working(R.string.myzel_backup_exporting)
  }
}

/**
 * Eine Sicherung einspielen: die Datei (Dateiauswahl des Systems), dann die Bereiche, die
 * eingespielt werden sollen.
 */
@Composable
fun ImportBackupFlow(onDismiss: () -> Unit) {
  var hasAsked by rememberSaveable { mutableStateOf(false) }
  // die Datei überlebt das Drehen, die gelesene Sicherung wird danach neu gelesen
  var source by rememberSaveable { mutableStateOf<Uri?>(null) }
  var backup by remember { mutableStateOf<BackupFile?>(null) }
  val context = LocalContext.current
  val backupManager = rememberBackupManager()
  val openFile =
    rememberLauncherForActivityResult(OpenDocument()) { uri: Uri? ->
      if (uri == null) onDismiss() else source = uri
    }
  LaunchedEffect(Unit) {
    if (!hasAsked) {
      hasAsked = true
      if (!openFile.launchSafe(OPEN_MIME_TYPES)) {
        Toast.makeText(context, R.string.no_handler_app_generic, LENGTH_SHORT).show()
        onDismiss()
      }
    }
  }
  val uri = source ?: return
  LaunchedEffect(uri) {
    try {
      backup = backupManager.read(uri)
    } catch (e: BackupException) {
      log.warn(e) { "Error reading backup" }
      Toast.makeText(context, e.messageRes, LENGTH_LONG).show()
      onDismiss()
    }
  }
  backup?.let { RestoreDialog(it, backupManager, onDismiss) }
}

@Composable
private fun RestoreDialog(backup: BackupFile, backupManager: BackupManager, onDismiss: () -> Unit) {
  val scope = rememberCoroutineScope()
  val sections = remember { mutableStateListOf(*backup.sections.toTypedArray()) }
  var isWorking by remember { mutableStateOf(false) }

  ContentDialog(
    title = stringResource(R.string.myzel_backup_import),
    confirmLabel = stringResource(R.string.myzel_backup_restore),
    onConfirm = {
      isWorking = true
      scope.launch {
        // läuft im BackupManager weiter, auch wenn der Dialog vorher schließt
        val restored = backupManager.restore(backup, sections.toSet(), beforeAppearance = onDismiss)
        if (restored) onDismiss() else isWorking = false
      }
    },
    onDismiss = { if (!isWorking) onDismiss() },
    confirmEnabled = sections.isNotEmpty() && !isWorking,
    icon = Icons.Outlined.SettingsBackupRestore,
  ) {
    if (backup.created > 0) {
      val created =
        DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
          .format(Date(backup.created))
      Text(
        stringResource(R.string.myzel_backup_created, created, backup.appVersion),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      Spacer(Modifier.height(8.dp))
    }
    SectionChoice(backup.sections, sections, enabled = !isWorking) { section ->
      when (section) {
        BackupSection.REPOSITORIES ->
          pluralStringResource(
            R.plurals.myzel_backup_repo_count,
            backup.repositoryCount,
            backup.repositoryCount,
          )
        BackupSection.FAVORITES ->
          pluralStringResource(
            R.plurals.myzel_backup_favorite_count,
            backup.favoriteCount,
            backup.favoriteCount,
          )
        else -> null
      }
    }
    Spacer(Modifier.height(12.dp))
    Text(
      stringResource(R.string.myzel_backup_restore_hint),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    if (isWorking) Working(R.string.myzel_backup_restoring)
  }
}

/**
 * Häkchen für die Bereiche in [available], die gewählten in [chosen]; [detail] kann die
 * Beschreibung eines Bereichs ersetzen.
 */
@Composable
private fun SectionChoice(
  available: List<BackupSection>,
  chosen: MutableList<BackupSection>,
  enabled: Boolean,
  detail: @Composable (BackupSection) -> String? = { null },
) {
  available.forEach { section ->
    val isChosen = section in chosen
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier =
        Modifier.fillMaxWidth()
          .clip(MaterialTheme.shapes.medium)
          .toggleable(isChosen, enabled = enabled, role = Role.Checkbox) { checked ->
            if (checked) chosen.add(section) else chosen.remove(section)
          }
          .padding(vertical = 4.dp),
    ) {
      Checkbox(
        isChosen,
        onCheckedChange = null,
        enabled = enabled,
        modifier = Modifier.padding(12.dp),
      )
      Column {
        Text(stringResource(section.label), style = MaterialTheme.typography.bodyLarge)
        Text(
          detail(section) ?: stringResource(section.summary),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
private fun Working(text: Int) {
  Spacer(Modifier.height(16.dp))
  Text(stringResource(text), style = MaterialTheme.typography.bodyMedium)
  Spacer(Modifier.height(8.dp))
  SignalLinearProgressIndicator(Modifier.fillMaxWidth().clip(CircleShape))
}

private const val BACKUP_MIME_TYPE = "application/json"
// manche Dateiauswahlen kennen JSON nicht und zeigen die Datei sonst ausgegraut
private val OPEN_MIME_TYPES = arrayOf("application/json", "application/octet-stream", "text/plain")
private const val FILE_DATE_PATTERN = "yyyy-MM-dd"
