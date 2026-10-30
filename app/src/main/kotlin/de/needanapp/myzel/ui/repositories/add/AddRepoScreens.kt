package de.needanapp.myzel.ui.repositories.add

import android.Manifest.permission.CAMERA
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.net.Uri
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddCircle
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Alignment.Companion.CenterVertically
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat.checkSelfPermission
import androidx.core.os.LocaleListCompat
import com.google.zxing.client.android.Intents.Scan.MIXED_SCAN
import com.google.zxing.client.android.Intents.Scan.SCAN_TYPE
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.journeyapps.barcodescanner.ScanOptions.QR_CODE
import de.needanapp.design.components.CardStatus
import de.needanapp.design.components.PillButton
import de.needanapp.design.components.SectionCard
import de.needanapp.design.components.SignalCircularProgressIndicator
import de.needanapp.design.components.StatusTone
import de.needanapp.myzel.ui.apps.SectionTitle
import de.needanapp.myzel.ui.compat.MeteredConnectionDialog
import de.needanapp.myzel.ui.compat.Switch
import de.needanapp.myzel.ui.details.CardSpacing
import de.needanapp.myzel.ui.details.DetailRow
import de.needanapp.myzel.ui.details.ValueText
import de.needanapp.myzel.ui.details.mono
import de.needanapp.myzel.ui.lists.AppListRow
import de.needanapp.myzel.ui.nav.saysUnauthorized
import de.needanapp.myzel.ui.repositories.LoginFields
import de.needanapp.myzel.ui.repositories.RepoHeader
import io.ktor.client.engine.ProxyConfig
import io.ktor.client.plugins.ResponseException
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.launch
import org.fdroid.R
import org.fdroid.download.NetworkState
import org.fdroid.download.getImageModel
import org.fdroid.repo.AddRepoError
import org.fdroid.repo.AddRepoError.ErrorType.INVALID_FINGERPRINT
import org.fdroid.repo.AddRepoError.ErrorType.INVALID_INDEX
import org.fdroid.repo.AddRepoError.ErrorType.IO_ERROR
import org.fdroid.repo.AddRepoError.ErrorType.IS_ARCHIVE_REPO
import org.fdroid.repo.AddRepoError.ErrorType.UNKNOWN_SOURCES_DISALLOWED
import org.fdroid.repo.FetchResult
import org.fdroid.repo.FetchResult.IsExistingMirror
import org.fdroid.repo.FetchResult.IsExistingRepository
import org.fdroid.repo.FetchResult.IsNewMirror
import org.fdroid.repo.FetchResult.IsNewRepoAndNewMirror
import org.fdroid.repo.FetchResult.IsNewRepository
import org.fdroid.repo.Fetching
import org.fdroid.ui.lists.AppListItem
import org.fdroid.ui.repositories.add.getDisallowInstallUnknownSourcesErrorMessage
import org.fdroid.ui.utils.OfflineBar
import org.fdroid.ui.utils.addressForUi
import org.fdroid.ui.utils.asRelativeTimeString
import org.fdroid.ui.utils.launchSafe
import org.fdroid.ui.utils.startActivitySafe

/*
 * Ersatz für die Bildschirme von org.fdroid.ui.repositories.add mit denselben Signaturen, über
 * ergänzte Imports in AddRepo.kt eingebunden (myzel/tools/apply-imports.py): QR-Code und Adresse
 * als Karten, die Vorschau mit dem Kopf der Paketquelle-Details und einer Karte „Stand“, Fehler als
 * Karte mit Band.
 *
 * Bei einem Update von F-Droid mit AddRepoIntro.kt, AddRepoPreviewScreen.kt, RepoPreviewHeader.kt,
 * AddRepoProgressScreen.kt und AddRepoErrorScreen.kt vergleichen.
 */

/** Einstieg: QR-Code scannen oder die Adresse eingeben. */
@Composable
fun AddRepoIntroContent(
  networkState: NetworkState,
  onFetchRepo: (String) -> Unit,
  onNotWarnWhenMetered: () -> Unit,
  paddingValues: PaddingValues = PaddingValues(),
) {
  val context = LocalContext.current
  var showPermissionWarning by remember { mutableStateOf(false) }
  val scanLauncher =
    rememberLauncherForActivityResult(ScanContract()) { result ->
      if (result.contents != null) onFetchRepo(result.contents)
    }

  fun startScanning() {
    scanLauncher.launch(
      ScanOptions().apply {
        setPrompt("")
        setBeepEnabled(true)
        setOrientationLocked(false)
        setDesiredBarcodeFormats(QR_CODE)
        addExtra(SCAN_TYPE, MIXED_SCAN)
      }
    )
  }

  val permissionLauncher =
    rememberLauncherForActivityResult(RequestPermission()) { isGranted ->
      showPermissionWarning = !isGranted
      if (isGranted) startScanning()
    }
  var showMeteredDialog by remember { mutableStateOf<(() -> Unit)?>(null) }

  // bei getakteter Verbindung erst nachfragen
  fun whenAllowed(action: () -> Unit) {
    if (networkState.showWarningDialog) showMeteredDialog = action else action()
  }

  var address by rememberSaveable { mutableStateOf("") }
  var withLogin by rememberSaveable { mutableStateOf(false) }
  var user by rememberSaveable { mutableStateOf("") }
  // das Passwort nicht im gespeicherten Zustand der Ansicht ablegen
  var password by remember { mutableStateOf("") }
  val layoutDirection = LocalLayoutDirection.current
  Column(
    modifier =
      Modifier.padding(
          top = paddingValues.calculateTopPadding(),
          start = paddingValues.calculateStartPadding(layoutDirection),
          end = paddingValues.calculateEndPadding(layoutDirection),
        )
        .imePadding()
        .fillMaxSize()
        .verticalScroll(rememberScrollState())
  ) {
    if (!networkState.isOnline) OfflineBar()
    Text(
      text = stringResource(R.string.myzel_repo_add_intro),
      style = MaterialTheme.typography.bodyMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
    )
    SectionCard(stringResource(R.string.myzel_repo_add_qr), CardSpacing) {
      Text(
        stringResource(R.string.myzel_repo_add_qr_hint),
        style = MaterialTheme.typography.bodyMedium,
      )
      Spacer(Modifier.height(12.dp))
      PillButton(
        text = stringResource(R.string.repo_scan_qr_code),
        onClick = {
          whenAllowed {
            if (checkSelfPermission(context, CAMERA) == PERMISSION_GRANTED) startScanning()
            else permissionLauncher.launchSafe(CAMERA)
          }
        },
      )
      AnimatedVisibility(
        visible = showPermissionWarning,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
      ) {
        Text(
          text = stringResource(R.string.permission_camera_denied),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.error,
          modifier =
            Modifier.padding(top = 12.dp).clickable {
              val intent =
                Intent(ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                  data = Uri.fromParts("package", context.packageName, null)
                }
              context.startActivitySafe(intent)
            },
        )
      }
    }
    SectionCard(stringResource(R.string.myzel_repo_add_address), CardSpacing) {
      val clipboard = LocalClipboard.current
      val scope = rememberCoroutineScope()
      val fetch = {
        if (address.isNotBlank()) {
          val login = if (withLogin) user.trim() else ""
          whenAllowed { onFetchRepo(withCredentials(address.trim(), login, password)) }
        }
      }
      OutlinedTextField(
        value = address,
        onValueChange = { address = it },
        label = { Text(stringResource(R.string.myzel_repo_add_address_label)) },
        placeholder = { Text("https://") },
        minLines = 2,
        trailingIcon = {
          IconButton(
            onClick = {
              scope.launch {
                val text = clipboard.getClipEntry()?.clipData?.takeIf { it.itemCount > 0 }
                text?.getItemAt(0)?.text?.let { address = it.toString().trim() }
              }
            }
          ) {
            Icon(Icons.Rounded.ContentPaste, stringResource(R.string.paste))
          }
        },
        keyboardOptions =
          KeyboardOptions(
            keyboardType = KeyboardType.Uri,
            imeAction = ImeAction.Go,
            autoCorrectEnabled = false,
          ),
        keyboardActions = KeyboardActions(onGo = { fetch() }),
        modifier = Modifier.fillMaxWidth(),
      )
      // Benutzername und Passwort für Paketquellen mit Anmeldung, aufgeklappt bei Bedarf
      Row(
        verticalAlignment = CenterVertically,
        modifier =
          Modifier.fillMaxWidth()
            .padding(top = 8.dp)
            .toggleable(value = withLogin, role = Role.Switch) { withLogin = it }
            .padding(vertical = 4.dp),
      ) {
        Text(
          stringResource(R.string.myzel_repo_login_add),
          style = MaterialTheme.typography.bodyMedium,
          modifier = Modifier.weight(1f),
        )
        Switch(checked = withLogin, onCheckedChange = null)
      }
      AnimatedVisibility(visible = withLogin) {
        Column(Modifier.padding(top = 4.dp)) {
          Text(
            stringResource(R.string.myzel_repo_login_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
          )
          LoginFields(
            user = user,
            onUserChange = { user = it },
            password = password,
            onPasswordChange = { password = it },
            onDone = { fetch() },
          )
        }
      }
      Spacer(Modifier.height(12.dp))
      PillButton(
        text = stringResource(R.string.repo_add_add),
        onClick = fetch,
        enabled = address.isNotBlank() && (!withLogin || user.isNotBlank()),
      )
    }
    Spacer(Modifier.height(paddingValues.calculateBottomPadding() + 16.dp))
  }
  val meteredLambda = showMeteredDialog
  if (meteredLambda != null)
    MeteredConnectionDialog(
      numBytes = null,
      onConfirm = { notWarnWhenMetered ->
        if (notWarnWhenMetered) onNotWarnWhenMetered()
        meteredLambda()
      },
      onDismiss = { showMeteredDialog = null },
    )
}

/**
 * Vorschau der geladenen Paketquelle: Kopf wie in den Details, Karte „Stand“ mit Band, die Aktion
 * als Pille, Beschreibung und die enthaltenen Apps.
 */
@Composable
fun AddRepoPreviewScreen(
  state: Fetching,
  proxyConfig: ProxyConfig?,
  modifier: Modifier = Modifier,
  onAddRepo: () -> Unit,
  onExistingRepo: (Long) -> Unit,
) {
  val localeList = LocaleListCompat.getDefault()
  val repo = state.receivedRepo ?: error("no repo")
  val result = state.fetchResult
  val showApps = result == null || result is IsNewRepository || result is IsNewRepoAndNewMirror
  LazyColumn(modifier = modifier.fillMaxWidth(), contentPadding = PaddingValues(bottom = 80.dp)) {
    item(key = "header", contentType = "header") {
      RepoHeader(
        iconModel = repo.getIcon(localeList)?.getImageModel(repo, proxyConfig),
        name = repo.getName(localeList) ?: "Unknown Repository",
        address = repo.addressForUi,
      )
    }
    item(key = "state", contentType = "card") {
      PreviewStateCard(state, result, showApps, repo.timestamp)
    }
    if (result != null) {
      item(key = "action", contentType = "action") {
        PillButton(
          text = stringResource(result.buttonText()),
          onClick =
            when (result) {
              is IsExistingRepository -> {
                { onExistingRepo(result.existingRepoId) }
              }
              is IsExistingMirror -> {
                { onExistingRepo(result.existingRepoId) }
              }
              else -> onAddRepo
            },
          modifier = CardSpacing,
        )
      }
    }
    val description = repo.getDescription(localeList)?.replace("\n", " ")
    if (!description.isNullOrBlank()) {
      item(key = "description", contentType = "card") {
        SectionCard(stringResource(R.string.myzel_details_description), CardSpacing) {
          Text(description, style = MaterialTheme.typography.bodyMedium)
        }
      }
    }
    if (showApps && state.apps.isNotEmpty()) {
      item(key = "apps", contentType = "title") {
        SectionTitle(stringResource(R.string.myzel_repo_add_apps))
      }
      items(items = state.apps, key = { it.packageName }, contentType = { "app" }) { app ->
        // TODO wie bei F-Droid: die Umwandlung gehört eigentlich nicht in die Oberfläche
        val item =
          AppListItem(
            repoId = repo.repoId,
            packageName = app.packageName,
            name = app.name ?: "Unknown app",
            summary = app.summary ?: "",
            iconModel = app.getIcon(localeList)?.getImageModel(repo, proxyConfig),
            lastUpdated = 1L,
            isInstalled = false,
            isCompatible = true,
          )
        AppListRow(
          item = item,
          isSelected = false,
          modifier = Modifier.animateItem().fillMaxWidth(),
        )
      }
    }
  }
}

/** Band „Neue Paketquelle“, „Neuer Spiegelserver“ oder „Schon hinzugefügt“, darunter die Zahlen. */
@Composable
private fun PreviewStateCard(
  state: Fetching,
  result: FetchResult?,
  showApps: Boolean,
  timestamp: Long,
) {
  val status =
    when (result) {
      IsNewRepository,
      IsNewRepoAndNewMirror ->
        CardStatus(
          stringResource(R.string.myzel_repo_add_new),
          Icons.Rounded.AddCircle,
          StatusTone.ACCENT,
        )
      is IsNewMirror ->
        CardStatus(
          stringResource(R.string.myzel_repo_add_new_mirror),
          Icons.Rounded.AddCircle,
          StatusTone.ACCENT,
        )
      is IsExistingRepository,
      is IsExistingMirror ->
        CardStatus(
          stringResource(R.string.myzel_repo_add_existing),
          Icons.Rounded.CheckCircle,
          StatusTone.NEUTRAL,
        )
      null -> null
    }
  val note =
    when (result) {
      IsNewRepoAndNewMirror ->
        stringResource(R.string.repo_and_mirror_add_both_info, state.fetchUrl)
      is IsNewMirror -> stringResource(R.string.repo_mirror_add_info, state.fetchUrl)
      is IsExistingRepository -> stringResource(R.string.repo_exists)
      is IsExistingMirror -> stringResource(R.string.repo_mirror_exists, state.fetchUrl)
      else -> null
    }
  SectionCard(stringResource(R.string.myzel_repo_state), CardSpacing, status = status) {
    if (showApps) {
      DetailRow(stringResource(R.string.myzel_repo_apps)) {
        val count = state.apps.size
        ValueText(pluralStringResource(R.plurals.repo_num_apps_text, count, count))
        if (!state.done) {
          Spacer(Modifier.width(10.dp))
          SignalCircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
        }
      }
    }
    DetailRow(stringResource(R.string.myzel_repo_published)) {
      ValueText(timestamp.asRelativeTimeString())
    }
    if (note != null) {
      Text(
        text = note,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
      )
    }
  }
}

private fun FetchResult.buttonText(): Int =
  when (this) {
    IsNewRepository -> R.string.repo_add_new_title
    IsNewRepoAndNewMirror -> R.string.repo_add_repo_and_mirror
    is IsNewMirror -> R.string.repo_add_mirror
    is IsExistingRepository,
    is IsExistingMirror -> R.string.repo_view_repo
  }

/** Laden oder Hinzufügen: ein ruhiger Kreis mit dem Text darunter. */
@Composable
fun AddRepoProgressScreen(
  text: String,
  modifier: Modifier = Modifier,
  backAllowed: Boolean = true,
) {
  BackHandler(!backAllowed) {}
  Column(
    verticalArrangement = spacedBy(20.dp, CenterVertically),
    horizontalAlignment = CenterHorizontally,
    modifier = modifier.padding(16.dp).fillMaxSize(),
  ) {
    SignalCircularProgressIndicator(Modifier.size(48.dp))
    Text(
      text = text,
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      textAlign = TextAlign.Center,
    )
  }
}

/** Der Fehler als Karte mit Band, darunter die genaue Meldung zum Markieren. */
@Composable
fun AddRepoErrorScreen(state: AddRepoError, modifier: Modifier = Modifier) {
  // der Server verlangt eine Anmeldung oder hat sie abgelehnt (HTTP 401)
  val unauthorized = state.exception?.isUnauthorized() == true
  val message =
    if (unauthorized) stringResource(R.string.myzel_repo_add_unauthorized)
    else
      when (state.errorType) {
        INVALID_FINGERPRINT -> stringResource(R.string.bad_fingerprint)
        UNKNOWN_SOURCES_DISALLOWED -> {
          if (LocalInspectionMode.current) {
            stringResource(R.string.has_disallow_install_unknown_sources)
          } else {
            getDisallowInstallUnknownSourcesErrorMessage(LocalContext.current)
          }
        }
        INVALID_INDEX -> stringResource(R.string.repo_invalid)
        IO_ERROR -> stringResource(R.string.repo_io_error)
        IS_ARCHIVE_REPO -> stringResource(R.string.repo_error_adding_archive)
      }
  val retryHint =
    !unauthorized &&
      (state.errorType == INVALID_FINGERPRINT ||
        state.errorType == INVALID_INDEX ||
        state.errorType == IO_ERROR)
  Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(top = 8.dp)) {
    SectionCard(
      title = stringResource(R.string.repo_add_new_title),
      modifier = CardSpacing,
      status =
        CardStatus(
          stringResource(R.string.myzel_repo_add_failed),
          Icons.Rounded.WarningAmber,
          StatusTone.ERROR,
        ),
    ) {
      Text(message, style = MaterialTheme.typography.bodyMedium)
      if (retryHint) {
        Text(
          text = stringResource(R.string.myzel_repo_add_failed_hint),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 8.dp),
        )
      }
      // bei einer verweigerten Anmeldung sagt der Hinweis alles, die Fehlerseite wäre nur Rauschen
      val exception = state.exception
      if (exception != null && !unauthorized) {
        SelectionContainer(Modifier.padding(top = 12.dp, bottom = 4.dp)) {
          Text(
            text = exception.toString(),
            style = MaterialTheme.typography.mono.copy(fontSize = 12.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      }
    }
  }
}

/**
 * [address] mit Benutzername und Passwort in der Adresse (https://name:passwort@host/…): So nimmt
 * F-Droid beim Hinzufügen Zugangsdaten an (org.fdroid.repo.RepoUriGetter) und speichert sie zur
 * Paketquelle, die Adresse selbst ohne sie. Ohne [username] bleibt [address], wie sie ist; fehlt
 * das Schema, gilt https wie bei F-Droid.
 */
internal fun withCredentials(address: String, username: String, password: String): String {
  if (username.isBlank()) return address
  val uri = Uri.parse(if ("://" in address) address else "https://$address")
  val authority = uri.encodedAuthority ?: return address
  val host = authority.substringAfterLast('@')
  val userInfo = Uri.encode(username) + ":" + Uri.encode(password)
  return uri.buildUpon().encodedAuthority("$userInfo@$host").build().toString()
}

/** Ob in [this] oder seinen Ursachen der Server mit 401 geantwortet hat (Anmeldung nötig). */
internal fun Throwable.isUnauthorized(): Boolean =
  generateSequence(this) { it.cause }
    .any {
      (it is ResponseException && it.response.status == HttpStatusCode.Unauthorized) ||
        it.message?.let(::saysUnauthorized) == true
    }
