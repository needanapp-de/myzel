package de.needanapp.myzel.ui.apps

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo.FLAG_SYSTEM
import android.net.Uri
import android.provider.Settings
import android.text.format.Formatter
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.net.toUri
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.asFlow
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import de.needanapp.design.components.DeviceDisplay
import de.needanapp.design.components.Led
import de.needanapp.design.components.StatusTone
import de.needanapp.design.theme.LocalDesignColors
import de.needanapp.design.theme.isNeedanappDesign
import de.needanapp.myzel.favorites.FAVORITE_OFF_ALPHA
import de.needanapp.myzel.favorites.FavoritesManager
import de.needanapp.myzel.ui.MyzelLogo
import de.needanapp.myzel.ui.MyzelLogoColor
import de.needanapp.myzel.ui.compat.IgnoreIssueDialog
import de.needanapp.myzel.ui.compat.MeteredConnectionDialog
import de.needanapp.myzel.ui.nav.displayName
import de.needanapp.myzel.watch.WatchReach
import de.needanapp.myzel.watch.fitsWatch
import de.needanapp.myzel.watch.isForWatch
import de.needanapp.myzel.watch.isRunning
import de.needanapp.myzel.watch.rememberWatchApp
import de.needanapp.myzel.watch.rememberWatchInstall
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.fdroid.R
import org.fdroid.database.AppPrefs
import org.fdroid.database.AppVersion
import org.fdroid.database.DbUpdateChecker
import org.fdroid.database.FDroidDatabase
import org.fdroid.database.Repository
import org.fdroid.download.NetworkMonitor
import org.fdroid.index.RepoManager
import org.fdroid.install.AppInstallManager
import org.fdroid.install.InstalledAppsCache
import org.fdroid.settings.SettingsManager
import org.fdroid.ui.apps.AppUpdateItem
import org.fdroid.ui.apps.AppWithIssueItem
import org.fdroid.ui.apps.MyAppItem
import org.fdroid.ui.apps.MyInstalledAppItem
import org.fdroid.ui.utils.launchSafe
import org.fdroid.ui.utils.startActivitySafe
import org.fdroid.updates.UpdateInstaller
import org.fdroid.updates.UpdatesManager
import org.fdroid.utils.IoDispatcher

/**
 * Was das Bedienfeld außerhalb von sich auslöst; [MyAppsList] stellt es bereit, weil dort die
 * Dialoge und der Start der Deinstallation leben, die das Zuklappen der Zeile überdauern.
 */
class AppPanelHost(
  val onOpenPage: (String) -> Unit,
  val onUpdate: (AppUpdateItem) -> Unit,
  val onIgnoreIssue: (AppWithIssueItem) -> Unit,
  val onUninstall: (MyAppItem, Intent) -> Unit,
  val onClose: () -> Unit,
  /** so viel deckt die Pille unten zu; eine aufgeklappte Karte scrollt darüber */
  val coveredBottom: Dp = 0.dp,
  /** installiert eine App, die es noch nicht ist (Favoriten); ohne bleibt „Installieren“ gedimmt */
  val onInstall: ((packageName: String, name: String) -> Unit)? = null,
)

/** Zugang zu Datenbank, Updates, Favoriten und Installation für das Bedienfeld. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppPanelEntryPoint {
  fun db(): FDroidDatabase

  fun repoManager(): RepoManager

  fun updatesManager(): UpdatesManager

  fun updateInstaller(): UpdateInstaller

  fun appInstallManager(): AppInstallManager

  fun installedAppsCache(): InstalledAppsCache

  fun favoritesManager(): FavoritesManager

  fun dbUpdateChecker(): DbUpdateChecker

  fun settingsManager(): SettingsManager

  fun networkMonitor(): NetworkMonitor

  @IoDispatcher fun ioScope(): CoroutineScope
}

fun appPanelTools(context: Context): AppPanelEntryPoint =
  EntryPointAccessors.fromApplication(context, AppPanelEntryPoint::class.java)

/**
 * Ein [AppPanelHost] für Listen außerhalb von „Meine Apps“, z. B. die Favoriten, mit den Dialogen,
 * die dort [MyAppsList] zeigt: Warnung vor einer getakteten Verbindung beim Aktualisieren, Problem
 * ignorieren und Deinstallieren. „Installieren“ startet die Installation und öffnet die App-Seite,
 * die den Fortschritt zeigt und die Bestätigung von Android anfordert. [iconModel] ist das Symbol
 * einer App für die Benachrichtigungen der Installation.
 */
@Composable
fun rememberAppPanelHost(
  onOpenPage: (String) -> Unit,
  onClose: () -> Unit,
  iconModel: (String) -> Any?,
  coveredBottom: Dp = 0.dp,
): AppPanelHost {
  val context = LocalContext.current
  val tools = remember(context) { appPanelTools(context) }
  var meteredUpdate by remember { mutableStateOf<AppUpdateItem?>(null) }
  var issueToIgnore by remember { mutableStateOf<AppWithIssueItem?>(null) }
  // Deinstallieren startet hier, nicht im Bedienfeld: dessen Zeile verschwindet mit der App
  var uninstalling by remember { mutableStateOf<MyAppItem?>(null) }
  val uninstallLauncher =
    if (LocalInspectionMode.current) null
    else
      rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        uninstalling?.let {
          tools.appInstallManager().onUninstallResult(it.packageName, it.name, result)
        }
        uninstalling = null
        // abgebrochen bleibt das Bedienfeld offen
        if (result.resultCode == Activity.RESULT_OK) onClose()
      }
  val startUpdate = { app: AppUpdateItem ->
    tools.ioScope().launch { tools.updateInstaller().updateAll(listOf(app), true) }
    onClose()
  }
  val noVersion = stringResource(R.string.myzel_panel_no_version)
  val updateToConfirm = meteredUpdate
  if (updateToConfirm != null)
    MeteredConnectionDialog(
      numBytes = updateToConfirm.update.size,
      onConfirm = { notWarnWhenMetered ->
        if (notWarnWhenMetered) tools.settingsManager().onDontWarnOnMeteredNetwork()
        startUpdate(updateToConfirm)
      },
      onDismiss = { meteredUpdate = null },
    )
  val appToIgnore = issueToIgnore
  if (appToIgnore != null)
    IgnoreIssueDialog(
      appName = appToIgnore.name,
      onIgnore = {
        tools
          .settingsManager()
          .ignoreAppIssue(appToIgnore.packageName, appToIgnore.installedVersionCode)
        tools.updatesManager().loadUpdates()
        issueToIgnore = null
        onClose()
      },
      onDismiss = { issueToIgnore = null },
    )
  return AppPanelHost(
    onOpenPage = { packageName ->
      onClose()
      onOpenPage(packageName)
    },
    onUpdate = { app ->
      if (tools.networkMonitor().networkState.value.showWarningDialog) meteredUpdate = app
      else startUpdate(app)
    },
    onIgnoreIssue = { issueToIgnore = it },
    onUninstall = { app, intent ->
      uninstalling = app
      uninstallLauncher?.launchSafe(intent)
    },
    onClose = onClose,
    coveredBottom = coveredBottom,
    onInstall = { packageName, name ->
      tools.installSuggested(packageName, iconModel(packageName)) {
        context.toast(noVersion.format(name))
      }
      onClose()
      onOpenPage(packageName)
    },
  )
}

/**
 * Startet die Installation von [packageName] mit der Version, die auch die App-Seite vorschlägt
 * (bevorzugte Paketquelle, Kanäle der App), wie F-Droid beim Wiederherstellen archivierter Apps
 * (org.fdroid.install.UnarchiveWorker). Passt keine Version, ruft es [onNoVersion] auf dem
 * Hauptthread.
 */
private fun AppPanelEntryPoint.installSuggested(
  packageName: String,
  iconModel: Any?,
  onNoVersion: () -> Unit,
) {
  ioScope().launch {
    val prefs = db().getAppPrefsDao().getAppPrefs(packageName).asFlow().first()
    val version =
      dbUpdateChecker()
        .getSuggestedVersion(
          packageName = packageName,
          releaseChannels = prefs.releaseChannels,
          onlyFromPreferredRepo = true,
        )
    if (version == null) {
      withContext(Dispatchers.Main) { onNoVersion() }
      return@launch
    }
    val app = db().getAppDao().getApp(version.repoId, packageName)
    withContext(Dispatchers.Main) {
      appInstallManager()
        .install(
          packageName = packageName,
          appMetadata = app?.metadata,
          version = version,
          currentVersionName = null,
          repo = repoManager().getRepository(version.repoId),
          iconModel = iconModel,
          canAskPreApprovalNow = true,
        )
    }
  }
}

/**
 * Das Bedienfeld unter einer App in „Meine Apps“, aufgeklappt durch langes Drücken: oben eine
 * Anzeige mit dem Stand (Update bereit, installiert, Problem, keine Updates) und rechts Paketquelle
 * und Größe oder Version, darunter acht Tasten in zwei Reihen mit feinen Linien wie bei den
 * Kategorien. Die Tasten stehen immer am selben Platz; was für die App nicht geht, ist gedimmt. Im
 * Stil NeedAnApp liegen die Tasten in einem umrandeten Raster mit Haarlinien.
 */
@Composable
fun AppPanel(app: MyAppItem, host: AppPanelHost, modifier: Modifier = Modifier) =
  AppPanel(app.packageName, app.name, app, host, modifier)

/**
 * Das Bedienfeld für eine App, die vielleicht nicht installiert ist, z. B. einen Favoriten: mit
 * [item] wie in „Meine Apps“, ohne zeigt die Anzeige „Nicht installiert“, vorn steht „Installieren“
 * ([AppPanelHost.onInstall]) und was eine installierte App braucht, ist gedimmt.
 */
@Composable
fun AppPanel(
  packageName: String,
  name: String,
  item: MyAppItem?,
  host: AppPanelHost,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val tools = remember(context) { appPanelTools(context) }
  val installed = item != null
  val update = item as? AppUpdateItem
  val issue = item as? AppWithIssueItem
  // die Paketquelle: beim Update die des Updates, sonst die bevorzugte der App
  val repoId by
    produceState(update?.repoId, packageName) {
      if (value == null) value = tools.db().getAppDao().getApp(packageName).asFlow().first()?.repoId
    }
  val repo = remember(repoId) { repoId?.let { tools.repoManager().getRepository(it) } }
  val prefs by
    remember(packageName) { tools.db().getAppPrefsDao().getAppPrefs(packageName).asFlow() }
      .collectAsState(initial = null)
  val favorites by tools.favoritesManager().favorites.collectAsStateWithLifecycle()
  val isFavorite = packageName in favorites
  val intents =
    remember(packageName, repo) { PanelIntents(context, tools, packageName, name, repo) }
  val ignoringAll = prefs?.ignoreAllUpdates == true

  val (text, tone) =
    when {
      update != null -> stringResource(R.string.myzel_panel_update_ready) to StatusTone.ACCENT
      issue != null -> stringResource(R.string.myzel_tag_problem) to StatusTone.ERROR
      !installed ->
        stringResource(R.string.myzel_details_status_not_installed) to StatusTone.NEUTRAL
      ignoringAll -> stringResource(R.string.myzel_panel_updates_off) to StatusTone.NEUTRAL
      else -> stringResource(R.string.myzel_tag_installed) to StatusTone.POSITIVE
    }
  val size =
    update?.update?.size?.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(context, it) }
  // die Version steht schon in der Zeile, hier nur, wenn die Paketquelle fehlt
  val repoName = repo?.displayName()
  val detail =
    listOfNotNull(repoName, size).joinToString(" · ").ifEmpty {
      (item as? MyInstalledAppItem)?.installedVersionName
    }

  // die LEDs leuchten in der Signalfarbe
  val signal = LocalDesignColors.current.signal
  val needanapp = isNeedanappDesign
  val leave = { intent: Intent ->
    context.startActivitySafe(intent)
    host.onClose()
  }
  val updateKey =
    if (installed) {
      PanelAction(
        stringResource(R.string.myzel_panel_update),
        if (needanapp) Icons.Rounded.Download else Icons.Rounded.Update,
        onClick = update?.let { { host.onUpdate(it) } },
        led = update?.let { signal },
        active = update != null,
      )
    } else {
      PanelAction(
        stringResource(R.string.myzel_panel_install),
        Icons.Rounded.Download,
        onClick = host.onInstall?.let { install -> { install(packageName, name) } },
        active = true,
      )
    }
  val openKey =
    PanelAction(
      stringResource(R.string.myzel_panel_open),
      Icons.AutoMirrored.Rounded.OpenInNew,
      onClick = intents.launch?.let { { leave(it) } },
    )
  val favoriteAdded = stringResource(R.string.myzel_favorite_added)
  val favoriteRemoved = stringResource(R.string.myzel_favorite_removed)
  val favoriteKey =
    PanelAction(
      stringResource(R.string.myzel_favorite),
      icon = null,
      onClick = {
        val now = tools.favoritesManager().toggle(packageName)
        context.toast(if (now) favoriteAdded else favoriteRemoved)
      },
      led = if (isFavorite) signal else null,
      selected = isFavorite,
    )
  val shareKey =
    PanelAction(
      stringResource(R.string.myzel_panel_share),
      Icons.Rounded.Share,
      onClick = intents.share?.let { { leave(it) } },
    )
  val skipped = update?.let {
    stringResource(R.string.myzel_panel_skipped, it.update.versionName, name)
  }
  val ignored = stringResource(R.string.myzel_panel_ignored, name)
  val notIgnored = stringResource(R.string.myzel_panel_not_ignored, name)
  val ignoreKey =
    when {
      update != null ->
        PanelAction(
          stringResource(R.string.myzel_panel_skip),
          Icons.Rounded.SkipNext,
          onClick = {
            tools.changePrefs(packageName) {
              it.toggleIgnoreVersionCodeUpdate(update.update.versionCode)
            }
            context.toast(skipped.orEmpty())
            host.onClose()
          },
        )
      issue != null ->
        PanelAction(
          stringResource(R.string.myzel_panel_ignore),
          Icons.Rounded.VisibilityOff,
          onClick = { host.onIgnoreIssue(issue) },
        )
      // Updates ignorieren gibt es erst für installierte Apps
      !installed ->
        PanelAction(
          stringResource(R.string.myzel_panel_ignore),
          Icons.Rounded.NotificationsOff,
          null,
        )
      else ->
        PanelAction(
          stringResource(R.string.myzel_panel_ignore),
          Icons.Rounded.NotificationsOff,
          // erst wenn die Einstellungen geladen sind, ist klar, wohin der Schalter kippt
          onClick =
            if (prefs == null) null
            else {
              {
                tools.changePrefs(packageName) { it.toggleIgnoreAllUpdates() }
                context.toast(if (ignoringAll) notIgnored else ignored)
              }
            },
          led = if (ignoringAll) signal else null,
          selected = ignoringAll,
        )
    }
  val watchKey = rememberWatchAction(packageName, name, host)
  val infoKey =
    PanelAction(
      stringResource(R.string.myzel_panel_app_info),
      Icons.Rounded.Info,
      onClick = if (installed) ({ leave(intents.appInfo) }) else null,
    )
  val uninstallKey =
    PanelAction(
      stringResource(R.string.myzel_panel_uninstall),
      Icons.Rounded.Delete,
      onClick = item?.let { app -> intents.uninstall?.let { { host.onUninstall(app, it) } } },
      danger = true,
    )

  val keys =
    listOf(
      listOf(updateKey, openKey, favoriteKey, shareKey),
      listOf(ignoreKey, watchKey, infoKey, uninstallKey),
    )
  val design = LocalDesignColors.current
  val line = design.divider
  if (needanapp) {
    Column(modifier.fillMaxWidth()) {
      DeviceDisplay(
        text,
        tone,
        Modifier.padding(horizontal = 8.dp),
        detail = detail,
        compact = true,
      )
      // die Linien sind die Lücken zwischen den Tasten auf der Linienfarbe
      Column(
        Modifier.padding(8.dp)
          .fillMaxWidth()
          .clip(NeedanappGridShape)
          .background(line)
          .padding(1.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp),
      ) {
        keys.forEach { row ->
          Row(Modifier.fillMaxWidth().height(KeyHeight), Arrangement.spacedBy(1.dp)) {
            row.forEach { action ->
              PanelKeyCell(action, Modifier.weight(1f).fillMaxHeight().background(design.card))
            }
          }
        }
      }
    }
    return
  }
  Column(modifier.fillMaxWidth()) {
    DeviceDisplay(
      text,
      tone,
      Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
      detail = detail,
      compact = true,
    )
    keys.forEach { row ->
      Hairline(line)
      Row(Modifier.fillMaxWidth().height(KeyHeight)) {
        row.forEachIndexed { column, action ->
          if (column > 0) VerticalHairline(line)
          PanelKeyCell(action, Modifier.weight(1f).fillMaxHeight())
        }
      }
    }
  }
}

/**
 * Was eine Taste zeigt und tut; ohne [onClick] ist sie gedimmt. Ohne [icon] zeigt sie das Myzel
 * (Favorit), [selected] ist der Zustand eines Schalters, [active] hinterlegt die Taste wie die
 * offene Gruppe bei den Kategorien.
 */
private class PanelAction(
  val label: String,
  val icon: ImageVector?,
  val onClick: (() -> Unit)?,
  val led: Color? = null,
  val blinking: Boolean = false,
  val active: Boolean = false,
  val danger: Boolean = false,
  val selected: Boolean? = null,
)

@Composable
private fun PanelKeyCell(action: PanelAction, modifier: Modifier = Modifier) {
  val scheme = MaterialTheme.colorScheme
  val accent = scheme.primary
  val signal = LocalDesignColors.current.signal
  val onClick = action.onClick
  // im Design needanapp ist die hervorgehobene Taste nur farbig, die übrigen in der Textfarbe
  val needanapp = isNeedanappDesign
  val highlighted = action.active && !needanapp
  val tint =
    when {
      action.danger -> scheme.error
      action.active -> accent
      needanapp -> scheme.onSurface
      else -> scheme.onSurfaceVariant
    }
  val textColor =
    when {
      action.danger -> scheme.error
      action.active && needanapp -> accent
      action.active -> scheme.onSurface
      needanapp -> scheme.onSurface
      else -> scheme.onSurfaceVariant
    }
  Box(
    contentAlignment = Alignment.Center,
    modifier =
      modifier
        .then(
          if (highlighted) Modifier.background(scheme.onSurface.copy(alpha = 0.06f)) else Modifier
        )
        .drawBehind {
          // die hervorgehobene Taste trägt unten einen Strich in der Signalfarbe
          if (highlighted) {
            val bar = 2.dp.toPx()
            drawRect(signal, Offset(0f, size.height - bar), Size(size.width, bar))
          }
        }
        .then(
          if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick)
          else Modifier
        )
        .alpha(if (onClick != null) 1f else DISABLED_ALPHA)
        .semantics {
          if (onClick == null) disabled()
          action.selected?.let { selected = it }
        },
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      if (action.icon != null) {
        Icon(action.icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
      } else {
        MyzelLogo(
          Modifier.size(if (needanapp) 22.dp else 20.dp),
          color =
            if (action.selected == true) MyzelLogoColor
            else scheme.onSurface.copy(alpha = FAVORITE_OFF_ALPHA),
        )
      }
      Spacer(Modifier.height(if (needanapp) 4.dp else 5.dp))
      BasicText(
        text = action.label,
        style =
          TextStyle(
            color = textColor,
            fontSize = 11.sp,
            lineHeight = if (needanapp) 14.sp else 13.sp,
            fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Medium,
          ),
        maxLines = 1,
        autoSize =
          TextAutoSize.StepBased(minFontSize = 8.sp, maxFontSize = 11.sp, stepSize = 0.5.sp),
        modifier = Modifier.padding(horizontal = 4.dp),
      )
    }
    if (action.led != null) {
      Led(
        action.led,
        Modifier.align(Alignment.TopEnd).padding(top = 7.dp, end = 8.dp),
        diameter = 5.dp,
        blinking = action.blinking,
      )
    }
  }
}

/**
 * Die Taste „Auf der Uhr“: gedimmt ohne Uhr-Version (und bei Myzel selbst, das Myzel Wear
 * mitbringt). Ist die Uhr erreichbar und fehlt die App dort oder ist sie älter, bringt ein Tipp die
 * neueste zur Uhr passende Version hin; sonst öffnet er die App-Seite, deren Karte „Uhr“ den Grund
 * nennt. Die LED blinkt, solange etwas läuft, leuchtet bei einem Update in der Signalfarbe und
 * grün, wenn die App auf der Uhr aktuell ist.
 */
@Composable
private fun rememberWatchAction(
  packageName: String,
  name: String,
  host: AppPanelHost,
): PanelAction {
  val label = stringResource(R.string.myzel_panel_watch)
  val icon = Icons.Rounded.Watch
  val watch = rememberWatchApp(packageName)
  if (watch == null || watch.own) return PanelAction(label, icon, onClick = null)
  val handle = rememberWatchInstall(packageName) ?: return PanelAction(label, icon, onClick = null)
  val context = LocalContext.current
  val tools = remember(context) { appPanelTools(context) }
  val paired = handle.watch
  // die Uhr-Versionen aus dem Katalog mit Datei, höchste zuerst
  val versions by
    produceState<List<AppVersion>?>(null, watch) {
      val codes = watch.watchVersions.map { it.versionCode }.toSet()
      tools.db().getVersionDao().getAppVersions(watch.repoId, packageName).asFlow().collect { list
        ->
        value =
          list
            .filter { it.isForWatch && it.versionCode in codes }
            .sortedByDescending { it.versionCode }
      }
    }
  val target = versions?.firstOrNull { paired == null || it.fitsWatch(paired.sdk) }
  if (paired != null) {
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val keepChecking = handle.keepChecking
    // die Verbindung prüfen, solange das Bedienfeld offen ist
    LaunchedEffect(paired.keyId) {
      lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { keepChecking() }
    }
  }
  val signal = LocalDesignColors.current.signal
  val positive = LocalDesignColors.current.positive
  val started = stringResource(R.string.myzel_panel_watch_started, name)
  val openPage = { host.onOpenPage(packageName) }
  val onWatch = handle.app
  if (handle.state.isRunning) {
    return PanelAction(label, icon, openPage, led = signal, blinking = true)
  }
  if (paired == null || target == null || onWatch == null || handle.reach !is WatchReach.Ok) {
    return PanelAction(label, icon, openPage)
  }
  val install = {
    handle.install(target)
    context.toast(started)
  }
  if (!onWatch.installed) return PanelAction(label, icon, install)
  val expected = target.signer?.sha256.orEmpty()
  val otherSigner =
    expected.isNotEmpty() && onWatch.signers.isNotEmpty() && onWatch.signers.none { it in expected }
  return when {
    otherSigner -> PanelAction(label, icon, openPage)
    target.versionCode > onWatch.versionCode -> PanelAction(label, icon, install, led = signal)
    else -> PanelAction(label, icon, openPage, led = positive)
  }
}

/** Die Intents des Bedienfelds; Deinstallieren nicht bei System-Apps, Teilen nur mit Webadresse. */
private class PanelIntents(
  context: Context,
  tools: AppPanelEntryPoint,
  private val packageName: String,
  name: String,
  repo: Repository?,
) {
  private val packageUri = Uri.fromParts("package", packageName, null)

  // Myzel startet sich nicht selbst
  val launch: Intent? =
    if (packageName == context.packageName) null
    else context.packageManager.getLaunchIntentForPackage(packageName)

  val uninstall: Intent? =
    tools.installedAppsCache().installedApps.value[packageName]?.let { info ->
      if (((info.applicationInfo?.flags ?: 0) and FLAG_SYSTEM) != 0) null
      else
        Intent(Intent.ACTION_DELETE).apply {
          data = packageUri
          @Suppress("DEPRECATION") putExtra(Intent.EXTRA_RETURN_RESULT, true)
        }
    }

  val share: Intent? =
    repo?.webBaseUrl?.let { base ->
      val uri = base.toUri().buildUpon().appendPath(packageName).build()
      val send =
        Intent(Intent.ACTION_SEND).apply {
          type = "text/plain"
          putExtra(Intent.EXTRA_SUBJECT, name)
          putExtra(Intent.EXTRA_TITLE, name)
          putExtra(Intent.EXTRA_TEXT, uri.toString())
        }
      Intent.createChooser(send, name)
    }

  val appInfo = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri)
}

/** Ändert die Einstellungen der App wie die App-Seite und lädt danach die Updates neu. */
private fun AppPanelEntryPoint.changePrefs(packageName: String, change: (AppPrefs) -> AppPrefs) {
  ioScope().launch {
    val dao = db().getAppPrefsDao()
    dao.update(change(dao.getAppPrefs(packageName).asFlow().first()))
    updatesManager().loadUpdates()
  }
}

private fun Context.toast(text: String) = Toast.makeText(this, text, Toast.LENGTH_SHORT).show()

@Composable
private fun Hairline(color: Color) {
  Spacer(Modifier.fillMaxWidth().height(1.dp).background(color))
}

@Composable
private fun VerticalHairline(color: Color) {
  Spacer(Modifier.fillMaxHeight().width(1.dp).background(color))
}

private val KeyHeight = 58.dp
private val NeedanappGridShape = RoundedCornerShape(10.dp)
private const val DISABLED_ALPHA = 0.32f
