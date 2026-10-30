package de.needanapp.myzel.ui.apps

import android.app.Activity
import android.text.format.Formatter
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import de.needanapp.design.components.DeviceDisplay
import de.needanapp.design.components.DeviceKey
import de.needanapp.design.components.MonoLabel
import de.needanapp.design.components.SignalCircularProgressIndicator
import de.needanapp.design.components.StatusTone
import de.needanapp.design.components.TagPill
import de.needanapp.design.components.designCard
import de.needanapp.design.theme.LocalDesignColors
import de.needanapp.design.theme.LocalDesignShapes
import de.needanapp.design.theme.NeedanappMotion
import de.needanapp.design.theme.emphasizedSpec
import de.needanapp.design.theme.isNeedanappDesign
import de.needanapp.design.theme.mixedWith
import de.needanapp.myzel.diagnose.rememberIssueReason
import de.needanapp.myzel.ui.compat.IgnoreIssueDialog
import de.needanapp.myzel.ui.compat.ListItem
import de.needanapp.myzel.ui.compat.MeteredConnectionDialog
import de.needanapp.myzel.ui.compat.NotAvailableDialog
import de.needanapp.myzel.ui.compat.OnboardingCard
import de.needanapp.myzel.watch.rememberWatchApps
import de.needanapp.myzel.watch.ui.watchAppsSection
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.fdroid.R
import org.fdroid.database.NotAvailable
import org.fdroid.database.UpdateInOtherRepo
import org.fdroid.install.InstallState
import org.fdroid.ui.apps.AppUpdateItem
import org.fdroid.ui.apps.AppWithIssueItem
import org.fdroid.ui.apps.InstalledAppItem
import org.fdroid.ui.apps.InstallingAppItem
import org.fdroid.ui.apps.MyAppItem
import org.fdroid.ui.apps.MyAppsInfo
import org.fdroid.ui.utils.AsyncShimmerImage
import org.fdroid.ui.utils.ExpandIconArrow
import org.fdroid.ui.utils.VersionLine
import org.fdroid.ui.utils.launchSafe

/**
 * Ersatz für org.fdroid.ui.apps.MyAppsList mit derselben Signatur, über einen ergänzten Import in
 * MyApps.kt eingebunden (myzel/tools/apply-imports.py). Dieselben Abschnitte wie bei F-Droid, im
 * Stil der Einstellungen: Überschriften in der Akzentfarbe, jede App eine Karte, Pillen neben dem
 * Namen statt Zeichen am Symbol („Update“, „Installiert“, „Fehler“, „Problem“), „Was ist neu“
 * klappt in der Karte auf. Langes Drücken klappt unter einer App das Bedienfeld mit ihren Optionen
 * auf ([AppPanel]). Darunter der Abschnitt „Auf der Uhr“. Im Design needanapp schlichter: Titel in
 * der Hauptfarbe, Karten mit Schatten, beim Update ein runder Knopf statt „Was ist neu“.
 *
 * Bei einem Update von F-Droid mit org.fdroid.ui.apps.MyAppsList vergleichen.
 */
@Composable
@OptIn(ExperimentalFoundationApi::class)
fun MyAppsList(
  myAppsInfo: MyAppsInfo,
  currentPackageName: String?,
  lazyListState: LazyListState,
  onAppItemClick: (String) -> Unit,
  paddingValues: PaddingValues,
  modifier: Modifier = Modifier,
) {
  val updatableApps = myAppsInfo.model.appUpdates
  val installingApps = myAppsInfo.model.installingApps
  val appsWithIssue = myAppsInfo.model.appsWithIssue
  val installedApps = myAppsInfo.model.installedApps
  // nach oben, wenn neue Updates dazukommen (saveable, damit das Drehen nicht scrollt)
  var previousNumUpdates by rememberSaveable { mutableIntStateOf(0) }
  LaunchedEffect(updatableApps) {
    if (!updatableApps.isNullOrEmpty()) {
      if (updatableApps.size > previousNumUpdates) {
        lazyListState.animateScrollToItem(0)
      }
      previousNumUpdates = updatableApps.size
    }
  }
  // „Alle aktualisieren“ verschwindet nach dem Tippen, damit niemand zweimal tippt
  var showUpdateAllButton by remember(updatableApps) { mutableStateOf(true) }
  var showMeteredDialog by remember { mutableStateOf<(() -> Unit)?>(null) }
  var showIssueIgnoreDialog by remember { mutableStateOf<AppWithIssueItem?>(null) }
  val watchApps = rememberWatchApps()
  // die App, deren Bedienfeld offen ist (höchstens eine); Zurück klappt es zu
  var openPanel by rememberSaveable { mutableStateOf<String?>(null) }
  BackHandler(enabled = openPanel != null) { openPanel = null }
  val context = LocalContext.current
  // Deinstallieren startet hier, nicht im Bedienfeld: dessen Zeile verschwindet mit der App
  var uninstalling by remember { mutableStateOf<MyAppItem?>(null) }
  val uninstallLauncher =
    if (LocalInspectionMode.current) null
    else
      rememberLauncherForActivityResult(StartActivityForResult()) { result ->
        uninstalling?.let {
          appPanelTools(context)
            .appInstallManager()
            .onUninstallResult(it.packageName, it.name, result)
        }
        uninstalling = null
        // abgebrochen bleibt das Bedienfeld offen
        if (result.resultCode == Activity.RESULT_OK) openPanel = null
      }
  val panelHost =
    AppPanelHost(
      onOpenPage = { packageName ->
        openPanel = null
        onAppItemClick(packageName)
      },
      onUpdate = { app ->
        val installLambda = {
          val tools = appPanelTools(context)
          tools.ioScope().launch { tools.updateInstaller().updateAll(listOf(app), true) }
          openPanel = null
        }
        if (myAppsInfo.model.networkState.showWarningDialog) {
          showMeteredDialog = installLambda
        } else {
          installLambda()
        }
      },
      onIgnoreIssue = { showIssueIgnoreDialog = it },
      onUninstall = { app, intent ->
        uninstalling = app
        uninstallLauncher?.launchSafe(intent)
      },
      onClose = { openPanel = null },
      coveredBottom = paddingValues.calculateBottomPadding(),
    )
  val optionsLabel = stringResource(R.string.myzel_my_apps_options)
  val needanapp = isNeedanappDesign
  val side = if (needanapp) 12.dp else 16.dp
  // Tippen öffnet die App, langes Drücken klappt [withPanel] das Bedienfeld auf; in der
  // zweispaltigen Ansicht großer Bildschirme ist die gezeigte App ausgewählt
  fun Modifier.onApp(
    packageName: String,
    withPanel: Boolean = true,
    onClick: () -> Unit = { onAppItemClick(packageName) },
  ): Modifier {
    val click =
      if (withPanel)
        Modifier.combinedClickable(
          onClick = onClick,
          onLongClick = { openPanel = if (openPanel == packageName) null else packageName },
          onLongClickLabel = optionsLabel,
        )
      else Modifier.clickable(onClick = onClick)
    val selection =
      if (currentPackageName == null) Modifier
      else Modifier.semantics { selected = packageName == currentPackageName }
    return then(click).then(selection)
  }
  LazyColumn(
    state = lazyListState,
    contentPadding = paddingValues,
    modifier =
      modifier.then(if (currentPackageName == null) Modifier else Modifier.selectableGroup()),
  ) {
    if (
      updatableApps != null &&
        updatableApps.isEmpty() &&
        myAppsInfo.model.showUpdatesHint &&
        !myAppsInfo.model.isSearching
    ) {
      item(key = "A-hint", contentType = "hint") {
        OnboardingCard(
          title = stringResource(R.string.apps_updates_hint_title),
          message = stringResource(R.string.apps_updates_hint_text),
          buttonText = stringResource(R.string.hide),
          onGotIt = { myAppsInfo.actions.onUpdatesHintSeen() },
          modifier = Modifier.padding(horizontal = side, vertical = 8.dp),
        )
      }
    } else if (!updatableApps.isNullOrEmpty()) {
      item(key = "A-panel", contentType = "panel") {
        UpdatesPanel(
          count = updatableApps.size,
          bytes = updatableApps.sumOf { it.update.size ?: 0L },
          showButton = showUpdateAllButton,
          onUpdateAll = {
            val installLambda = {
              myAppsInfo.actions.updateAll()
              showUpdateAllButton = false
            }
            if (myAppsInfo.model.networkState.showWarningDialog) {
              showMeteredDialog = installLambda
            } else {
              installLambda()
            }
          },
          modifier =
            if (needanapp) Modifier.padding(start = side, end = side, top = 10.dp)
            else Modifier.padding(horizontal = side, vertical = 8.dp),
        )
      }
      item(key = "A", contentType = "header") {
        SectionTitle(
          stringResource(R.string.updates),
          count = updatableApps.size,
          note =
            if (myAppsInfo.model.networkState.isOnline) null
            else stringResource(R.string.banner_no_internet),
        )
      }
      items(items = updatableApps, key = { it.packageName }, contentType = { "A" }) { app ->
        UpdateRow(
          app,
          isSelected = app.packageName == currentPackageName,
          open = app.packageName == openPanel,
          panelHost = panelHost,
          modifier = Modifier.animateItem(),
          clickModifier = Modifier.onApp(app.packageName),
        )
      }
    }
    if (installingApps.isNotEmpty()) {
      item(key = "B", contentType = "header") {
        val recent = myAppsInfo.model.showClearInstallingAppsButton
        SectionTitle(
          stringResource(
            if (recent) R.string.my_apps_header_recently_installed_apps
            else R.string.notification_title_summary_installing
          )
        ) {
          if (recent) {
            TextButton(onClick = myAppsInfo.actions::clearInstallingApps) {
              Text(stringResource(R.string.clear_all))
            }
          }
        }
      }
      items(items = installingApps, key = { it.packageName }, contentType = { "B" }) { app ->
        InstallingRow(
          app,
          app.packageName == currentPackageName,
          Modifier.animateItem(),
          Modifier.onApp(app.packageName, withPanel = false),
        )
      }
    }
    if (!appsWithIssue.isNullOrEmpty()) {
      item(key = "C", contentType = "header") {
        SectionTitle(
          stringResource(R.string.my_apps_header_apps_with_issue),
          count = appsWithIssue.size,
        )
      }
      if (myAppsInfo.model.showAppIssueHint)
        item(key = "C-hint", contentType = "hint") {
          OnboardingCard(
            title = stringResource(R.string.myzel_my_apps_issue_hint_title),
            message = stringResource(R.string.app_issue_hint),
            onGotIt = { myAppsInfo.actions.onAppIssueHintSeen() },
            modifier = Modifier.padding(horizontal = side, vertical = 8.dp),
          )
        }
      items(items = appsWithIssue, key = { it.packageName }, contentType = { "C" }) { app ->
        var showNotAvailableDialog by remember { mutableStateOf(false) }
        // das Problem ignorieren steht im Bedienfeld
        IssueRow(
          app,
          isSelected = app.packageName == currentPackageName,
          open = app.packageName == openPanel,
          panelHost = panelHost,
          modifier = Modifier.animateItem(),
          clickModifier =
            Modifier.onApp(app.packageName) {
              if (app.issue is NotAvailable) {
                showNotAvailableDialog = true
              } else {
                onAppItemClick(app.packageName)
              }
            },
        )
        if (showNotAvailableDialog)
          NotAvailableDialog(app.packageName) { showNotAvailableDialog = false }
      }
    }
    if (!installedApps.isNullOrEmpty()) {
      item(key = "D", contentType = "header") {
        SectionTitle(
          stringResource(R.string.installed_apps__activity_title),
          count = installedApps.size,
        )
      }
      items(items = installedApps, key = { it.packageName }, contentType = { "D" }) { app ->
        InstalledRow(
          app,
          isSelected = app.packageName == currentPackageName,
          open = app.packageName == openPanel,
          panelHost = panelHost,
          modifier = Modifier.animateItem(),
          clickModifier = Modifier.onApp(app.packageName),
        )
      }
    }
    if (watchApps != null && !myAppsInfo.model.isSearching) {
      watchAppsSection(watchApps, onAppItemClick)
    }
  }
  val appToIgnore = showIssueIgnoreDialog
  if (appToIgnore != null)
    IgnoreIssueDialog(
      appName = appToIgnore.name,
      onIgnore = {
        myAppsInfo.actions.ignoreAppIssue(appToIgnore)
        showIssueIgnoreDialog = null
        openPanel = null
      },
      onDismiss = { showIssueIgnoreDialog = null },
    )
  val meteredLambda = showMeteredDialog
  if (meteredLambda != null)
    MeteredConnectionDialog(
      numBytes = myAppsInfo.model.appUpdatesBytes,
      onConfirm = { notWarnWhenMetered ->
        if (notWarnWhenMetered) myAppsInfo.actions.onNotWarnWhenMetered()
        meteredLambda()
      },
      onDismiss = { showMeteredDialog = null },
    )
}

/**
 * Überschrift eines Abschnitts wie die Gruppentitel der Einstellungen: klein, in Großbuchstaben und
 * der Mono-Schrift, mit [count] dahinter („UPDATES · 3“), [note] darunter und [actions] rechts. Im
 * Design needanapp ein schlichter Titel in der Hauptfarbe, ohne Anzahl.
 */
@Composable
fun SectionTitle(
  title: String,
  modifier: Modifier = Modifier,
  note: String? = null,
  noteIsError: Boolean = true,
  count: Int? = null,
  actions: @Composable RowScope.() -> Unit = {},
) {
  val scheme = MaterialTheme.colorScheme
  val needanapp = isNeedanappDesign
  Row(
    verticalAlignment = Alignment.CenterVertically,
    modifier =
      if (needanapp)
        modifier.fillMaxWidth().padding(start = 28.dp, end = 16.dp, top = 16.dp, bottom = 6.dp)
      else
        modifier
          .fillMaxWidth()
          .heightIn(min = 48.dp)
          .padding(start = 24.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
  ) {
    Column(Modifier.weight(1f)) {
      if (needanapp) {
        Text(
          title,
          style = MaterialTheme.typography.titleSmall,
          color = scheme.primary,
          modifier = Modifier.semantics { heading() },
        )
      } else {
        MonoLabel(
          if (count != null) "$title · $count" else title,
          modifier = Modifier.semantics { heading() },
        )
      }
      if (note != null) {
        Text(
          note,
          style = MaterialTheme.typography.bodySmall,
          color = if (noteIsError) scheme.error else scheme.onSurfaceVariant,
          modifier = Modifier.padding(top = 2.dp),
        )
      }
    }
    actions()
  }
}

/**
 * Oben in „Meine Apps“, wenn es Updates gibt: ein Bedienfeld mit der Anzeige „3 UPDATES“ und ihrer
 * Größe, darunter die Taste „Alle aktualisieren“ (verschwindet nach dem Tippen, [showButton]); im
 * Design needanapp eine Kachel ([TileButton]).
 */
@Composable
private fun UpdatesPanel(
  count: Int,
  bytes: Long,
  showButton: Boolean,
  onUpdateAll: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val context = LocalContext.current
  val needanapp = isNeedanappDesign
  Column(
    modifier
      .fillMaxWidth()
      .designCard()
      .padding(if (needanapp) 8.dp else 10.dp)
      .animateContentSize()
  ) {
    DeviceDisplay(
      pluralStringResource(R.plurals.myzel_display_updates, count, count),
      StatusTone.ACCENT,
      detail = bytes.takeIf { it > 0 }?.let { Formatter.formatShortFileSize(context, it) },
    )
    if (showButton && needanapp) {
      Spacer(Modifier.height(8.dp))
      TileButton(stringResource(R.string.update_all), Icons.Rounded.Download, onUpdateAll)
    } else if (showButton) {
      Spacer(Modifier.height(10.dp))
      DeviceKey(
        stringResource(R.string.update_all),
        onClick = onUpdateAll,
        modifier = Modifier.fillMaxWidth(),
        icon = Icons.Rounded.Update,
      )
    }
  }
}

/**
 * Eine Taste als Kachel im Design needanapp: der Kreis mit dem Symbol in der Hauptfarbe, daneben
 * der Text, etwa „Alle aktualisieren“.
 */
@Composable
fun TileButton(
  text: String,
  icon: ImageVector,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val scheme = MaterialTheme.colorScheme
  val tile = LocalDesignColors.current.tile
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = spacedBy(12.dp),
    modifier =
      modifier
        .fillMaxWidth()
        .height(52.dp)
        .clip(RoundedCornerShape(10.dp))
        .background(tile)
        .clickable(role = Role.Button, onClick = onClick)
        .padding(horizontal = 12.dp),
  ) {
    Box(
      contentAlignment = Alignment.Center,
      modifier =
        Modifier.size(32.dp).clip(CircleShape).background(tile.mixedWith(scheme.primary, 0.2f)),
    ) {
      Icon(icon, contentDescription = null, tint = scheme.primary, modifier = Modifier.size(18.dp))
    }
    Text(text, style = MaterialTheme.typography.titleMedium, color = scheme.onSurface)
  }
}

/** Name und Pillen nebeneinander; reicht der Platz nicht, rutschen die Pillen darunter. */
@Composable
fun NameWithPills(name: String, pills: @Composable () -> Unit) {
  FlowRow(
    horizontalArrangement = spacedBy(6.dp),
    itemVerticalAlignment = Alignment.CenterVertically,
  ) {
    Text(name)
    pills()
  }
}

/** Das App-Symbol der Zeilen in „Meine Apps“; im Design needanapp kleiner wie in der Vorschau. */
@Composable
fun RowIcon(model: Any?) {
  val needanapp = isNeedanappDesign
  AsyncShimmerImage(
    model = model,
    error = painterResource(R.drawable.ic_repo_app_default),
    contentDescription = null,
    modifier =
      Modifier.size(if (needanapp) NeedanappIconSize else 48.dp)
        .clip(if (needanapp) NeedanappIconShape else MaterialTheme.shapes.medium)
        .semantics { hideFromAccessibility() },
  )
}

private val NeedanappIconSize = 40.dp
private val NeedanappIconShape = RoundedCornerShape(12.dp)

/**
 * Die Zeile einer App-Karte im Design needanapp: Symbol, Name und eine kleine Zeile darunter,
 * rechts [trailingContent]. [modifier] trägt das Tippen, damit die Welle die ganze Zeile füllt.
 */
@Composable
fun NeedanappAppRow(
  modifier: Modifier,
  leadingContent: @Composable () -> Unit,
  headlineContent: @Composable () -> Unit,
  supportingContent: @Composable () -> Unit,
  trailingContent: (@Composable () -> Unit)? = null,
) {
  val scheme = MaterialTheme.colorScheme
  Row(
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = spacedBy(12.dp),
    modifier =
      modifier.fillMaxWidth().heightIn(min = 52.dp).padding(horizontal = 10.dp, vertical = 6.dp),
  ) {
    leadingContent()
    Column(Modifier.weight(1f)) {
      CompositionLocalProvider(LocalContentColor provides scheme.onSurface) {
        ProvideTextStyle(MaterialTheme.typography.bodyLarge, headlineContent)
      }
      CompositionLocalProvider(LocalContentColor provides scheme.onSurfaceVariant) {
        ProvideTextStyle(MaterialTheme.typography.bodySmall, supportingContent)
      }
    }
    trailingContent?.invoke()
  }
}

/** Die Karte um eine App im Design needanapp, ausgewählt in der Farbe der aktiven Einträge. */
@Composable
private fun Modifier.needanappCard(isSelected: Boolean): Modifier =
  padding(horizontal = 12.dp, vertical = 4.dp)
    .designCard()
    .then(
      if (isSelected) Modifier.background(MaterialTheme.colorScheme.secondaryContainer)
      else Modifier
    )

/** Hervorhebung wie bei F-Droid: [de.needanapp.myzel.ui.compat.ListItem] erkennt sie daran. */
@Composable
fun selectedColors(isSelected: Boolean) =
  ListItemDefaults.colors(
    containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else Color.Transparent
  )

@Composable
fun UpdatePill() =
  TagPill(
    text = stringResource(R.string.myzel_tag_update),
    containerColor = LocalDesignColors.current.signal,
    contentColor = LocalDesignColors.current.onSignal,
    compact = true,
  )

/**
 * Eine App als Karte wie [ListItem], darunter das Bedienfeld ([AppPanel]), das langes Drücken
 * aufklappt; offen trägt die Karte einen feinen Rand. [modifier] gehört an die Karte (animateItem
 * der Liste), [clickModifier] an die Zeile, damit Tippen und Welle das Bedienfeld aussparen.
 */
@Composable
private fun AppRowCard(
  app: MyAppItem,
  isSelected: Boolean,
  open: Boolean,
  panelHost: AppPanelHost,
  modifier: Modifier,
  clickModifier: Modifier,
  leadingContent: @Composable () -> Unit,
  headlineContent: @Composable () -> Unit,
  supportingContent: @Composable () -> Unit,
  trailingContent: (@Composable () -> Unit)? = null,
) =
  PanelCard(
    isSelected = isSelected,
    open = open,
    coveredBottom = panelHost.coveredBottom,
    modifier = modifier,
    clickModifier = clickModifier,
    leadingContent = leadingContent,
    headlineContent = headlineContent,
    supportingContent = supportingContent,
    trailingContent = trailingContent,
  ) {
    AppPanel(app, panelHost)
  }

/**
 * Die Karte von [AppRowCard] mit beliebigem Bedienfeld [panel], auch für Listen außerhalb von
 * „Meine Apps“ wie die Favoriten; [coveredBottom] deckt die Pille unten zu.
 */
@Composable
internal fun PanelCard(
  isSelected: Boolean,
  open: Boolean,
  coveredBottom: Dp,
  modifier: Modifier,
  clickModifier: Modifier,
  leadingContent: @Composable () -> Unit,
  headlineContent: @Composable () -> Unit,
  supportingContent: @Composable () -> Unit,
  trailingContent: (@Composable () -> Unit)? = null,
  panel: @Composable () -> Unit,
) {
  val shapes = LocalDesignShapes.current
  val scheme = MaterialTheme.colorScheme
  val needanapp = isNeedanappDesign
  // wie bei ListItem: Zeilen kennen ihre Stellung in der Liste nicht
  val shape = RoundedCornerShape((shapes.group + shapes.inner) / 2)
  val container = if (isSelected) scheme.secondaryContainer else scheme.surfaceContainer
  val border by
    animateColorAsState(
      if (open) LocalDesignColors.current.cardBorder else Color.Transparent,
      label = "panelBorder",
    )
  val reveal = remember { BringIntoViewRequester() }
  val density = LocalDensity.current
  var cardSize by remember { mutableStateOf(IntSize.Zero) }
  LaunchedEffect(open) {
    if (open) {
      // nach dem Aufklappen die ganze Karte zeigen, auch über der Pille
      delay(NeedanappMotion.MEDIUM_DURATION.toLong())
      val covered = with(density) { coveredBottom.toPx() }
      reveal.bringIntoView(Rect(0f, 0f, cardSize.width.toFloat(), cardSize.height + covered))
    }
  }
  Column(
    modifier
      .bringIntoViewRequester(reveal)
      .onSizeChanged { cardSize = it }
      .then(
        if (needanapp) Modifier.needanappCard(isSelected)
        else
          Modifier.padding(horizontal = 16.dp, vertical = shapes.rowGap / 2)
            .clip(shape)
            .background(container)
            .border(1.dp, border, shape)
      )
  ) {
    if (needanapp) {
      NeedanappAppRow(
        clickModifier,
        leadingContent,
        headlineContent,
        supportingContent,
        trailingContent,
      )
    } else {
      androidx.compose.material3.ListItem(
        headlineContent = headlineContent,
        modifier = clickModifier,
        supportingContent = supportingContent,
        leadingContent = leadingContent,
        trailingContent = trailingContent,
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
      )
    }
    AnimatedVisibility(
      visible = open,
      enter = expandVertically(emphasizedSpec()) + fadeIn(emphasizedSpec()),
      exit = shrinkVertically(emphasizedSpec()) + fadeOut(emphasizedSpec()),
    ) {
      panel()
    }
  }
}

/**
 * Eine App mit Update: Versionen und Größe, „Was ist neu“ klappt in der Karte auf. Im Design
 * needanapp steht rechts stattdessen ein runder Knopf, der das Update startet.
 */
@Composable
private fun UpdateRow(
  app: AppUpdateItem,
  isSelected: Boolean,
  open: Boolean,
  panelHost: AppPanelHost,
  modifier: Modifier,
  clickModifier: Modifier,
) {
  var isExpanded by remember { mutableStateOf(false) }
  val needanapp = isNeedanappDesign
  AppRowCard(
    app = app,
    isSelected = isSelected,
    open = open,
    panelHost = panelHost,
    modifier = modifier,
    clickModifier = clickModifier,
    leadingContent = { RowIcon(app.iconModel) },
    headlineContent = {
      if (needanapp) Text(app.name) else NameWithPills(app.name) { UpdatePill() }
    },
    supportingContent = {
      Column {
        VersionLine(app)
        AnimatedVisibility(
          visible = isExpanded,
          modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        ) {
          Text(
            text = app.whatsNew.orEmpty(),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(top = 8.dp),
          )
        }
      }
    },
    trailingContent = {
      if (needanapp) {
        UpdateButton(stringResource(R.string.myzel_panel_update)) { panelHost.onUpdate(app) }
      } else if (app.whatsNew != null) {
        IconButton(onClick = { isExpanded = !isExpanded }) { ExpandIconArrow(isExpanded) }
      }
    },
  )
}

/** Der runde Knopf „Aktualisieren“ einer App-Karte im Design needanapp. */
@Composable
private fun UpdateButton(contentDescription: String, onClick: () -> Unit) {
  val scheme = MaterialTheme.colorScheme
  Box(
    contentAlignment = Alignment.Center,
    modifier =
      Modifier.size(40.dp)
        .clip(CircleShape)
        .background(scheme.primaryContainer)
        .clickable(role = Role.Button, onClick = onClick)
        .semantics { this.contentDescription = contentDescription },
  ) {
    Icon(
      Icons.Rounded.Download,
      contentDescription = null,
      tint = scheme.onPrimaryContainer,
      modifier = Modifier.size(20.dp),
    )
  }
}

/** Eine installierte App ohne Update, wie [org.fdroid.ui.apps.InstalledAppRow]. */
@Composable
private fun InstalledRow(
  app: InstalledAppItem,
  isSelected: Boolean,
  open: Boolean,
  panelHost: AppPanelHost,
  modifier: Modifier,
  clickModifier: Modifier,
) {
  AppRowCard(
    app = app,
    isSelected = isSelected,
    open = open,
    panelHost = panelHost,
    modifier = modifier,
    clickModifier = clickModifier,
    leadingContent = {
      if (isNeedanappDesign) {
        RowIcon(app.iconModel)
      } else {
        AsyncShimmerImage(
          model = app.iconModel,
          error = painterResource(R.drawable.ic_repo_app_default),
          contentDescription = null,
          modifier = Modifier.size(48.dp).semantics { hideFromAccessibility() },
        )
      }
    },
    headlineContent = { Text(app.name) },
    supportingContent = { Text(app.installedVersionName) },
  )
}

/**
 * Eine App, die gerade installiert wird oder eben installiert wurde. [modifier] gehört an die
 * Karte, [clickModifier] an die Zeile.
 */
@Composable
private fun InstallingRow(
  app: InstallingAppItem,
  isSelected: Boolean,
  modifier: Modifier,
  clickModifier: Modifier,
) {
  val state = app.installState
  val leading = @Composable { RowIcon(app.iconModel) }
  val headline =
    @Composable {
      NameWithPills(app.name) {
        when (state) {
          is InstallState.Installed -> {
            val colors = LocalDesignColors.current
            TagPill(
              text = stringResource(R.string.myzel_tag_installed),
              containerColor = colors.positive,
              contentColor = colors.onPositive,
              compact = true,
            )
          }
          is InstallState.Error ->
            TagPill(
              text = stringResource(R.string.myzel_tag_error),
              containerColor = MaterialTheme.colorScheme.error,
              contentColor = MaterialTheme.colorScheme.onError,
              compact = true,
            )
          else -> {}
        }
      }
    }
  val supporting = @Composable { VersionLine(state.currentVersionName, state.versionName) }
  val trailing =
    @Composable {
      when (state) {
        is InstallState.Installed,
        is InstallState.Error -> {}
        is InstallState.Downloading ->
          SignalCircularProgressIndicator(Modifier.size(28.dp), progress = { state.progress })
        else -> SignalCircularProgressIndicator(Modifier.size(28.dp))
      }
    }
  if (isNeedanappDesign) {
    Column(modifier.needanappCard(isSelected)) {
      NeedanappAppRow(clickModifier, leading, headline, supporting, trailing)
    }
    return
  }
  ListItem(
    leadingContent = leading,
    headlineContent = headline,
    supportingContent = supporting,
    trailingContent = trailing,
    colors = selectedColors(isSelected),
    modifier = modifier.then(clickModifier),
  )
}

/**
 * Eine App mit Problem; ein Update aus einer anderen Paketquelle ist nur ein Hinweis. Unter der
 * Version steht der Grund in wenigen Worten, die genaue Diagnose hat die App-Seite.
 */
@Composable
private fun IssueRow(
  app: AppWithIssueItem,
  isSelected: Boolean,
  open: Boolean,
  panelHost: AppPanelHost,
  modifier: Modifier,
  clickModifier: Modifier,
) {
  val scheme = MaterialTheme.colorScheme
  val otherRepo = app.issue is UpdateInOtherRepo
  val reason = rememberIssueReason(app.packageName, app.issue)
  AppRowCard(
    app = app,
    isSelected = isSelected,
    open = open,
    panelHost = panelHost,
    modifier = modifier,
    clickModifier = clickModifier,
    leadingContent = { RowIcon(app.iconModel) },
    headlineContent = {
      NameWithPills(app.name) {
        TagPill(
          text =
            stringResource(
              if (otherRepo) R.string.myzel_tag_other_repo else R.string.myzel_tag_problem
            ),
          containerColor = if (otherRepo) scheme.secondaryContainer else scheme.error,
          contentColor = if (otherRepo) scheme.onSecondaryContainer else scheme.onError,
          compact = true,
        )
      }
    },
    supportingContent = { Text("${app.installedVersionName} · $reason") },
  )
}
