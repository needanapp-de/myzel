package de.needanapp.myzel.ui.details

import android.content.ClipData
import android.content.Intent
import android.provider.Settings
import android.util.Log
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.outlined.Android
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Update
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.core.net.toUri
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation3.runtime.NavKey
import de.needanapp.design.components.CardStatus
import de.needanapp.design.components.ContentDialog
import de.needanapp.design.components.DeviceDisplay
import de.needanapp.design.components.DeviceKey
import de.needanapp.design.components.FloatingKnob
import de.needanapp.design.components.FloatingMenuEntry
import de.needanapp.design.components.FloatingMenuLabel
import de.needanapp.design.components.FloatingMenuSwitch
import de.needanapp.design.components.FloatingSurface
import de.needanapp.design.components.KeyStyle
import de.needanapp.design.components.LabeledRow
import de.needanapp.design.components.MonoLabel
import de.needanapp.design.components.PillAction
import de.needanapp.design.components.PillButton
import de.needanapp.design.components.RoundKey
import de.needanapp.design.components.SectionCard
import de.needanapp.design.components.StatusTone
import de.needanapp.design.components.TagPill
import de.needanapp.design.components.designCard
import de.needanapp.design.components.keyColors
import de.needanapp.design.components.keyEdge
import de.needanapp.design.components.rememberPillScrollBehavior
import de.needanapp.design.theme.DesignFonts
import de.needanapp.design.theme.DesignType
import de.needanapp.design.theme.LocalDesignColors
import de.needanapp.design.theme.LocalDesignEffects
import de.needanapp.design.theme.emphasizedSpec
import de.needanapp.design.theme.isNeedanappDesign
import de.needanapp.design.theme.labelCase
import de.needanapp.myzel.diagnose.UpdateProblem
import de.needanapp.myzel.diagnose.rememberUpdateDiagnosis
import de.needanapp.myzel.ui.MyzelLogo
import de.needanapp.myzel.ui.apps.AppBadgeRow
import de.needanapp.myzel.ui.apps.AppBadgeState
import de.needanapp.myzel.ui.apps.IconCircle
import de.needanapp.myzel.ui.apps.rememberIsNew
import de.needanapp.myzel.ui.compat.MeteredConnectionDialog
import de.needanapp.myzel.watch.WatchApp
import de.needanapp.myzel.watch.WatchException
import de.needanapp.myzel.watch.WatchInstallHandle
import de.needanapp.myzel.watch.WatchInstallState
import de.needanapp.myzel.watch.WatchPackage
import de.needanapp.myzel.watch.WatchReach
import de.needanapp.myzel.watch.WearUpdateState
import de.needanapp.myzel.watch.fitsWatch
import de.needanapp.myzel.watch.isForWatch
import de.needanapp.myzel.watch.isRunning
import de.needanapp.myzel.watch.rememberReposSignedLike
import de.needanapp.myzel.watch.rememberWatchApp
import de.needanapp.myzel.watch.rememberWatchInstall
import de.needanapp.myzel.watch.ui.WatchPage
import de.needanapp.myzel.watch.ui.WearUpdateNote
import de.needanapp.myzel.watch.ui.text
import de.needanapp.myzel.watch.ui.watchInstallerLabel
import de.needanapp.myzel.watch.ui.watchStateText
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.rememberHazeState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToLong
import kotlinx.coroutines.launch
import org.fdroid.LocaleChooser.getBestLocale
import org.fdroid.R
import org.fdroid.database.AppVersion
import org.fdroid.database.KnownVulnerability
import org.fdroid.database.Repository
import org.fdroid.install.InstallState
import org.fdroid.ui.details.AppDetailsItem
import org.fdroid.ui.details.AppDetailsWarnings
import org.fdroid.ui.details.LoadedAppDetailsItem
import org.fdroid.ui.details.MainButtonState
import org.fdroid.ui.details.Version
import org.fdroid.ui.lists.AppListType
import org.fdroid.ui.navigation.NavigationKey
import org.fdroid.ui.repositories.RepoIcon
import org.fdroid.ui.utils.launchSafe
import org.fdroid.ui.utils.startActivitySafe

/**
 * Ersatz für org.fdroid.ui.details.AppDetails mit derselben Signatur, nur nimmt [onNav] jedes Ziel,
 * auch die Seite „Uhr“ von Myzel; AppDetailsEntry.kt bindet ihn über einen ergänzten Import ein
 * (myzel/tools/apply-imports.py). Eine geladene App zeigt [AppDetailsPage], Laden und „nicht
 * gefunden“ bleiben die Seite von F-Droid.
 */
@Composable
fun AppDetails(
  item: AppDetailsItem?,
  onNav: (NavKey) -> Unit,
  onBackNav: (() -> Unit)?,
  modifier: Modifier = Modifier,
) {
  if (item is LoadedAppDetailsItem) {
    AppDetailsPage(item, onNav, onBackNav, modifier)
  } else {
    org.fdroid.ui.details.AppDetails(item, onNav, onBackNav, modifier)
  }
}

/** Größenfaktor des Kopfs: Symbol und Name sind größer als im übrigen Inhalt. */
private const val HERO_SCALE = 1.2f
internal val HeaderIconSize = 58.dp * HERO_SCALE
internal val HeaderGap = 12.dp * HERO_SCALE
private val VersionLabelWidth = 92.dp
internal val DetailLabelWidth = 100.dp

/**
 * Abstand der Karten zum Rand und untereinander (je die Hälfte oben und unten); im Design needanapp
 * etwas näher am Rand.
 */
internal val CardSpacing: Modifier
  @Composable
  @ReadOnlyComposable
  get() = Modifier.padding(horizontal = if (isNeedanappDesign) 12.dp else 16.dp, vertical = 8.dp)

/** Symbol des Kopfs im Design needanapp. */
private val NeedanappHeaderIconSize = 64.dp
private val NeedanappHeaderIconShape = RoundedCornerShape(18.dp)

/** Datum mit Uhrzeit, auf Deutsch TT.MM.JJJJ HH:MM ([R.string.myzel_date_time_pattern]). */
internal val DateTime: DateTimeFormatter
  @Composable get() = datePattern(R.string.myzel_date_time_pattern)

/** Nur das Datum, auf Deutsch TT.MM.JJJJ ([R.string.myzel_date_pattern]). */
internal val Date: DateTimeFormatter
  @Composable get() = datePattern(R.string.myzel_date_pattern)

@Composable
private fun datePattern(@StringRes pattern: Int): DateTimeFormatter {
  val text = stringResource(pattern)
  return remember(text) { DateTimeFormatter.ofPattern(text) }
}

internal fun Long.format(formatter: DateTimeFormatter): String =
  formatter.format(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))

/** SHA-256 als Bytepaare mit Doppelpunkten, in der Mitte gekürzt. */
internal fun String.shortHash(): String {
  val pairs = uppercase().chunked(2)
  return if (pairs.size <= 8) pairs.joinToString(":")
  else (pairs.take(4) + "...." + pairs.takeLast(4)).joinToString(":")
}

/** Größe in MB auf Basis 1024 ohne Nachkommastellen, unter einem MB in KB. */
private fun Long.megabytes(): String =
  if (this < 1024 * 1024) "${(this / 1024.0).roundToLong()} KB"
  else "${(this / (1024.0 * 1024.0)).roundToLong()} MB"

// Schriften der Seite, abgeleitet von der Typografie des Themes (behält eine eigene Schriftart)
internal val Typography.pageTitle: TextStyle
  get() =
    titleLarge.copy(
      fontSize = 30.sp,
      lineHeight = 34.sp,
      fontWeight = FontWeight.SemiBold,
      letterSpacing = (-0.6).sp,
    )

internal fun Typography.small(size: TextUnit, weight: FontWeight = FontWeight.Normal) =
  bodySmall.copy(fontSize = size, lineHeight = size * (16f / 12f), fontWeight = weight)

/** Unter dem Namen, etwa der Autor: in der Mono-Schrift, wie die Beschriftungen des Geräts. */
internal val Typography.byline: TextStyle
  @Composable
  @ReadOnlyComposable
  get() = DesignType.label.copy(fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.2.sp)

internal val Typography.value: TextStyle
  get() = small(14.sp, FontWeight.Medium)

internal val Typography.mono: TextStyle
  get() = value.copy(fontFamily = DesignFonts.GeistMono)

/**
 * Die Seite einer App als Karten: Kopf mit großem Symbol, dann Versionen (mit Band für den Zustand,
 * beim Herunterladen als Fortschrittsbalken), gleich darunter der breite Knopf (bei installierten
 * Apps halb, daneben Deinstallieren), Screenshots, Beschreibung und danach der Rest nach Bedeutung:
 * Uhr, Links, Details, Sicherheit. Unten rechts schwebt der runde Knopf mit dem Myzel, der die
 * Optionen aufklappt.
 */
@Composable
private fun AppDetailsPage(
  item: LoadedAppDetailsItem,
  onNav: (NavKey) -> Unit,
  onBackNav: (() -> Unit)?,
  modifier: Modifier = Modifier,
) {
  InstallStateEffects(item)
  val uninstallLauncher =
    rememberLauncherForActivityResult(StartActivityForResult()) {
      item.actions.onUninstallResult(it)
    }
  // Die Pillen der Szene (PillScene) liegen außerhalb, die Seite selbst ist schon Teil ihrer
  // Unschärfequelle. Die Pille hier braucht eine eigene, samt Verlauf, sonst wirkt sie deckend.
  val hazeState = rememberHazeState()
  val pillScroll = rememberPillScrollBehavior()
  val colors = LocalDesignColors.current
  val windowHeight = LocalWindowInfo.current.containerSize.height.toFloat()
  Box(modifier.fillMaxSize()) {
    Column(
      modifier =
        // der Inhalt scrollt nicht unter die Statusleiste, der Verlauf liegt aber auch dahinter
        Modifier.fillMaxSize()
          .hazeSource(hazeState)
          .drawBehind { drawRect(colors.backgroundBrush(windowHeight)) }
          .windowInsetsPadding(WindowInsets.statusBars)
          .nestedScroll(pillScroll.nestedScrollConnection)
          .verticalScroll(rememberScrollState())
          .padding(top = 12.dp, bottom = 80.dp)
          .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
      Header(item, onBackNav)
      val watch = rememberWatchApp(item.app.packageName)
      val diagnosis = rememberUpdateDiagnosis(item)
      val problem = diagnosis?.problem
      // Dass eine reine Uhr-App nicht aufs Handy passt, sagen Status und Karte „Uhr“ schon. Bei
      // installierten Apps erklärt die Update-Diagnose die Probleme genauer, die Warnungen von
      // F-Droid bleiben nur für Sicherheitslücken und Fälle, die sie nicht abdeckt.
      val hideWarnings =
        (watch?.watchOnly == true && item.issue == null) ||
          (item.issue !is KnownVulnerability &&
            item.installedVersionCode != null &&
            (diagnosis == null || problem != null))
      AnimatedVisibility(item.showWarnings && !hideWarnings) {
        AppDetailsWarnings(item, CardSpacing)
      }
      AnimatedVisibility(problem != null) {
        // beim Ausblenden bleibt die letzte Diagnose stehen
        var shown by remember { mutableStateOf(diagnosis) }
        if (problem != null) shown = diagnosis
        shown?.let { UpdateDiagnosisCard(item, it) }
      }
      VersionsCard(item, watch, problem)
      MainPill(item, uninstallLauncher, watchOnly = watch?.watchOnly == true)
      if (item.phoneScreenshots.isNotEmpty() && isNeedanappDesign) {
        // im Design needanapp stehen die Bildschirmfotos frei zwischen den Karten
        Box(Modifier.padding(vertical = 6.dp)) {
          Screenshots(item.networkState.showWarningDialog, item.phoneScreenshots)
        }
      } else if (item.phoneScreenshots.isNotEmpty()) {
        SectionCard(
          stringResource(R.string.myzel_details_screenshots),
          CardSpacing,
          fullWidthContent = true,
        ) {
          Screenshots(item.networkState.showWarningDialog, item.phoneScreenshots)
        }
      }
      DescriptionCard(item)
      watch?.let {
        if (it.own) MyzelWearCard(item, it, onSetUp = { onNav(WatchPage) }) else WatchCard(item, it)
      }
      LinksCard(item)
      DetailsCard(item)
      SecurityCard(item)
      if (item.authorHasMoreThanOneApp) {
        val authorName = item.app.authorName!!
        val title = stringResource(R.string.app_list_author, authorName)
        TextButton(
          onClick = { onNav(NavigationKey.AppList(AppListType.Author(title, authorName))) },
          modifier = Modifier.align(Alignment.CenterHorizontally),
        ) {
          Text(stringResource(R.string.app_details_more_apps_by_author, authorName))
        }
      }
    }
    OptionsKnob(
      item,
      hazeState,
      visible = pillScroll.isVisible,
      Modifier.align(Alignment.BottomEnd),
    )
  }
}

/**
 * Oben Zurück als Alu-Taste und „App / Details“, darunter Symbol, großer Name und Autor, rechts das
 * Myzel als Schalter für die Favoriten. Im Design needanapp schlichter: Zurück als Symbol ohne
 * Beschriftung daneben, der Name in Titelgröße und darunter Autor und Kategorie.
 */
@Composable
private fun Header(item: LoadedAppDetailsItem, onBackNav: (() -> Unit)?) {
  if (isNeedanappDesign) {
    NeedanappHeader(item, onBackNav)
    return
  }
  Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
    ) {
      if (onBackNav != null) {
        RoundKey(
          Icons.AutoMirrored.Filled.ArrowBack,
          stringResource(R.string.back),
          onBackNav,
          diameter = 48.dp,
        )
      }
      Spacer(Modifier.weight(1f))
      MonoLabel(stringResource(R.string.myzel_details_kicker))
    }
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier = Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp),
    ) {
      IconCircle(item.icon, HeaderIconSize)
      Spacer(Modifier.width(HeaderGap))
      Column(Modifier.weight(1f)) {
        Text(
          text = item.name,
          style = MaterialTheme.typography.pageTitle,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        item.app.authorName?.let { author ->
          Text(
            text = author.uppercase(),
            style = MaterialTheme.typography.byline,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
          )
        }
        // Installiert oder Update zeigt die Anzeige der Versionskarte, hier nur „Neu“
        if (rememberIsNew(item.app.packageName)) {
          AppBadgeRow(
            AppBadgeState(isInstalled = false, hasUpdate = false, isNew = true),
            Modifier.padding(top = 6.dp),
          )
        }
      }
      FavoriteButton(item.app.packageName, buttonSize = 60.dp, iconSize = 40.dp)
    }
  }
}

@Composable
private fun NeedanappHeader(item: LoadedAppDetailsItem, onBackNav: (() -> Unit)?) {
  Column(Modifier.fillMaxWidth()) {
    if (onBackNav != null) {
      RoundKey(
        Icons.AutoMirrored.Filled.ArrowBack,
        stringResource(R.string.back),
        onBackNav,
        modifier = Modifier.padding(start = 4.dp),
        diameter = 40.dp,
      )
    }
    Row(
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = spacedBy(14.dp),
      modifier =
        Modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 6.dp, bottom = 8.dp),
    ) {
      IconCircle(item.icon, NeedanappHeaderIconSize, shape = NeedanappHeaderIconShape)
      Column(Modifier.weight(1f)) {
        Text(
          text = item.name,
          style = MaterialTheme.typography.titleLarge,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
        val byline =
          listOfNotNull(item.app.authorName, item.categories?.firstOrNull()?.name)
            .joinToString(" · ")
        if (byline.isNotEmpty()) {
          Text(
            text = byline,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        if (rememberIsNew(item.app.packageName)) {
          AppBadgeRow(
            AppBadgeState(isInstalled = false, hasUpdate = false, isNew = true),
            Modifier.padding(top = 6.dp),
          )
        }
      }
      FavoriteButton(item.app.packageName, buttonSize = 48.dp, iconSize = 34.dp)
    }
  }
}

/** Zeile der Versionskarte: Beschriftung 92 dp, 6 dp darunter. */
@Composable
private fun InfoRow(label: String, value: @Composable RowScope.() -> Unit) =
  LabeledRow(label, Modifier.padding(bottom = 6.dp), labelWidth = VersionLabelWidth, value = value)

/** Zeile der übrigen Karten: Beschriftung 100 dp ohne Abstand, 8 dp darunter. */
@Composable
internal fun DetailRow(label: String, value: @Composable RowScope.() -> Unit) =
  LabeledRow(
    label,
    Modifier.padding(bottom = 8.dp),
    labelWidth = DetailLabelWidth,
    gap = 0.dp,
    value = value,
  )

@Composable
internal fun ValueText(text: String, mono: Boolean = false, size: TextUnit = 14.sp) {
  val typography = MaterialTheme.typography
  Text(text = text, style = (if (mono) typography.mono else typography.value).copy(fontSize = size))
}

/**
 * Link in der Hauptfarbe, unterstrichen (im Design needanapp halbfett statt unterstrichen): öffnet
 * [url] oder führt [onClick] aus.
 */
@Composable
internal fun LinkText(text: String, url: String? = null, onClick: (() -> Unit)? = null) {
  val style =
    if (isNeedanappDesign) {
      SpanStyle(color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
    } else {
      SpanStyle(
        color = MaterialTheme.colorScheme.primary,
        textDecoration = TextDecoration.Underline,
      )
    }
  if (url != null) {
    Text(
      text =
        buildAnnotatedString {
          withLink(LinkAnnotation.Url(url, TextLinkStyles(style))) { append(text) }
        },
      style = MaterialTheme.typography.value,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
  } else {
    Text(
      text = AnnotatedString(text, style),
      style = MaterialTheme.typography.value,
      modifier = Modifier.clickable(enabled = onClick != null) { onClick?.invoke() },
    )
  }
}

/** Zustand der App für das Band der Versionskarte. */
@Composable
private fun status(
  item: LoadedAppDetailsItem,
  watch: WatchApp?,
  problem: UpdateProblem?,
): CardStatus {
  val state = item.installState
  return when {
    watch?.watchOnly == true ->
      CardStatus(
        stringResource(R.string.myzel_details_status_watch),
        Icons.Rounded.Watch,
        StatusTone.NEUTRAL,
      )
    // beim Herunterladen füllt sich das Band als Fortschrittsbalken, der Text steht in der Mitte
    item.mainButtonState == MainButtonState.PROGRESS && state is InstallState.Downloading ->
      CardStatus(
        stringResource(R.string.myzel_display_downloading, (state.progress * 100).toInt()),
        Icons.Rounded.HourglassTop,
        StatusTone.ACCENT,
        progress = state.progress,
        detail =
          stringResource(
            R.string.myzel_display_bytes,
            state.downloadedBytes.megabytes(),
            state.totalBytes.megabytes(),
          ),
      )
    item.mainButtonState == MainButtonState.PROGRESS ->
      CardStatus(
        stringResource(R.string.myzel_details_status_installing),
        Icons.Rounded.HourglassTop,
        StatusTone.ACCENT,
      )
    item.mainButtonState == MainButtonState.LOADING ->
      CardStatus(
        stringResource(R.string.myzel_details_status_loading),
        Icons.Rounded.HourglassTop,
        StatusTone.NEUTRAL,
      )
    // ohne Update-Knopf sagt die Update-Diagnose, warum; „Aktuell“ wäre dann falsch
    item.installedVersionCode != null &&
      item.mainButtonState == MainButtonState.NONE &&
      problem != null &&
      problem !is UpdateProblem.LastError ->
      when (problem) {
        is UpdateProblem.OtherRepo ->
          CardStatus(
            stringResource(R.string.myzel_details_status_other_repo),
            Icons.Rounded.SwapHoriz,
            StatusTone.ACCENT,
          )
        is UpdateProblem.Ignored ->
          CardStatus(
            stringResource(R.string.myzel_details_status_ignored),
            Icons.Rounded.VisibilityOff,
            StatusTone.NEUTRAL,
          )
        else ->
          CardStatus(
            stringResource(R.string.myzel_details_status_blocked),
            Icons.Rounded.Block,
            StatusTone.ERROR,
          )
      }
    item.isIncompatible ->
      CardStatus(
        stringResource(R.string.myzel_details_status_incompatible),
        Icons.AutoMirrored.Rounded.HelpOutline,
        StatusTone.NEUTRAL,
      )
    item.mainButtonState == MainButtonState.UPDATE ->
      CardStatus(
        item.suggestedVersion?.let { stringResource(R.string.myzel_display_update, it.versionName) }
          ?: stringResource(R.string.myzel_details_status_update),
        Icons.Rounded.NewReleases,
        StatusTone.ACCENT,
        detail = item.installedVersionName?.let { stringResource(R.string.myzel_display_from, it) },
      )
    item.installedVersionCode != null ->
      CardStatus(
        stringResource(R.string.myzel_details_status_up_to_date),
        Icons.Rounded.Verified,
        StatusTone.POSITIVE,
        detail = item.installedVersionName,
      )
    else ->
      CardStatus(
        stringResource(R.string.myzel_details_status_not_installed),
        Icons.Rounded.InstallMobile,
        StatusTone.NEUTRAL,
        detail = item.suggestedVersion?.versionName,
      )
  }
}

/**
 * Die Versionen: oben die Anzeige mit dem Zustand, darunter ein Raster aus installierter und
 * neuester Version, Änderungen und Dateien; ein Tipp auf die Dateien klappt alle Versionen auf.
 */
/**
 * Der Rahmen der Versionskarte mit dem Zustand oben. Im Design needanapp steht er wie im Stil Myzel
 * in der Anzeige ([DeviceDisplay]) statt im Band einer [SectionCard], die Karte hat keinen Titel.
 */
@Composable
private fun VersionsCardFrame(status: CardStatus?, content: @Composable ColumnScope.() -> Unit) {
  if (!isNeedanappDesign) {
    SectionCard(
      title = "",
      modifier = CardSpacing,
      status = status,
      fullWidthContent = true,
      content = content,
    )
    return
  }
  Column(CardSpacing.fillMaxWidth().designCard().animateContentSize()) {
    status?.let {
      DeviceDisplay(
        it.text,
        it.tone,
        Modifier.padding(start = 8.dp, top = 8.dp, end = 8.dp),
        detail = it.detail,
        progress = it.progress,
      )
    }
    Column(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 14.dp), content = content)
  }
}

@Composable
private fun VersionsCard(item: LoadedAppDetailsItem, watch: WatchApp?, problem: UpdateProblem?) {
  var showAll by rememberSaveable { mutableStateOf(false) }
  // Uhr-Versionen einer Handy-App gehören nicht hierher, sie stehen in der Karte „Uhr“
  val versions =
    if (watch?.watchOnly == true) item.versions
    else item.versions?.filterNot { it.version.isForWatch }
  val latest = item.suggestedVersion ?: versions?.firstOrNull()?.version
  VersionsCardFrame(status(item, watch, problem)) {
    val installed = stringResource(R.string.myzel_details_installed)
    val latestLabel = stringResource(R.string.myzel_details_latest_short)
    val changelogLabel = stringResource(R.string.myzel_details_changelog)
    val filesLabel = stringResource(R.string.myzel_details_files)
    val dateTime = DateTime
    val count = versions?.size ?: 0
    val cells =
      buildList<Pair<String, @Composable () -> Unit>> {
        item.installedVersionName?.let { add(installed to { GridValue(it) }) }
        latest?.let { version ->
          add(latestLabel to { GridValue(version.versionName, strong = true) })
          add(
            changelogLabel to
              {
                val date = version.added.format(dateTime)
                val changelog = item.app.changelog
                if (changelog != null) LinkText(date, url = changelog) else ValueText(date)
              }
          )
        }
        if (count > 0) {
          add(
            filesLabel to
              {
                val apks = pluralStringResource(R.plurals.myzel_details_apk_count, count, count)
                val text = latest?.size?.let { "$apks · ${it.megabytes()}" } ?: apks
                LinkText(text) { showAll = !showAll }
              }
          )
        }
      }
    ValueGrid(cells, Modifier.padding(horizontal = 10.dp))
    Column(Modifier.padding(horizontal = 16.dp)) {
      item.whatsNew?.let { whatsNew ->
        SelectionContainer {
          Text(
            text = whatsNew.trim(),
            style = MaterialTheme.typography.small(13.sp),
            modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
          )
        }
      }
      AnimatedVisibility(showAll) {
        Column(Modifier.padding(top = 8.dp)) {
          Text(
            text = stringResource(R.string.myzel_details_all_versions).labelCase(),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
          )
          versions?.forEach { versionItem ->
            Version(
              item = versionItem,
              isMetered = item.networkState.showWarningDialog,
              installAction = { version: AppVersion ->
                item.actions.installAction(item.app, version, item.icon)
              },
              onNotWarnWhenMetered = item.actions.onNotWarnWhenMetered,
              scrollUp = { showAll = false },
            )
          }
        }
      }
    }
  }
}

/**
 * Ein Raster mit zwei Spalten wie auf dem Bedienfeld eines Geräts: je Feld eine kleine Beschriftung
 * und darunter der Wert, feine Linien zwischen den Feldern.
 */
@Composable
private fun ValueGrid(cells: List<Pair<String, @Composable () -> Unit>>, modifier: Modifier) {
  if (isNeedanappDesign) {
    TileGrid(cells, modifier)
    return
  }
  val line = LocalDesignColors.current.divider
  Column(modifier.fillMaxWidth()) {
    val rows = cells.chunked(2)
    rows.forEachIndexed { index, row ->
      Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).drawBehind {
          if (index < rows.lastIndex) {
            drawRect(line, Offset(0f, size.height - 1.dp.toPx()), Size(size.width, 1.dp.toPx()))
          }
        }
      ) {
        row.forEachIndexed { column, (label, value) ->
          Column(
            Modifier.weight(1f)
              .fillMaxHeight()
              .drawBehind {
                if (column == 0 && row.size > 1) {
                  drawRect(
                    line,
                    Offset(size.width - 1.dp.toPx(), 0f),
                    Size(1.dp.toPx(), size.height),
                  )
                }
              }
              .padding(
                start = if (column == 0) 6.dp else 14.dp,
                end = 6.dp,
                top = 12.dp,
                bottom = 12.dp,
              )
          ) {
            MonoLabel(label, style = DesignType.label.copy(fontSize = 10.5.sp))
            Spacer(Modifier.height(6.dp))
            value()
          }
        }
        if (row.size == 1) Spacer(Modifier.weight(1f))
      }
    }
  }
}

/**
 * [ValueGrid] im Design needanapp: zwei Spalten aus Kacheln, je mit kleiner Beschriftung und dem
 * Wert darunter.
 */
@Composable
private fun TileGrid(cells: List<Pair<String, @Composable () -> Unit>>, modifier: Modifier) {
  val tile = LocalDesignColors.current.tile
  Column(modifier.fillMaxWidth(), verticalArrangement = spacedBy(6.dp)) {
    cells.chunked(2).forEach { row ->
      Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
        horizontalArrangement = spacedBy(6.dp),
      ) {
        row.forEach { (label, value) ->
          Column(
            Modifier.weight(1f)
              .fillMaxHeight()
              .clip(TileShape)
              .background(tile)
              .padding(horizontal = 12.dp, vertical = 8.dp)
          ) {
            Text(
              label,
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            value()
          }
        }
        if (row.size == 1) Spacer(Modifier.weight(1f))
      }
    }
  }
}

private val TileShape = RoundedCornerShape(10.dp)

/**
 * Ein Wert im Raster der Versionskarte, etwa eine Versionsnummer: groß, [strong] etwas kräftiger;
 * im Design needanapp in der Größe der übrigen Werte.
 */
@Composable
private fun GridValue(text: String, strong: Boolean = false) {
  val needanapp = isNeedanappDesign
  Text(
    text = text,
    style =
      MaterialTheme.typography.titleLarge.copy(
        fontSize = if (needanapp) 15.sp else 21.sp,
        lineHeight = if (needanapp) 22.sp else 26.sp,
        fontWeight =
          when {
            needanapp && strong -> FontWeight.Medium
            needanapp -> FontWeight.Normal
            strong -> FontWeight.SemiBold
            else -> FontWeight.Medium
          },
      ),
    maxLines = 2,
    overflow = TextOverflow.Ellipsis,
  )
}

/**
 * Die Uhr-Version der App (siehe [de.needanapp.myzel.watch.WatchCatalog]): bei einer Handy-App nur
 * eine mit derselben Signatur, bei einer reinen Uhr-App die neueste. Ist eine Uhr eingerichtet,
 * fragt Myzel, was auf ihr installiert ist; die Knöpfe bringen die neueste zur Uhr passende Version
 * über Myzel Wear auf die Uhr oder entfernen die App dort.
 */
@Composable
private fun WatchCard(item: LoadedAppDetailsItem, watch: WatchApp) {
  val installedSigner = item.installedSigner
  // gleiche Signatur heißt hier: dieselbe wie die installierte Handy-App
  val otherSigner =
    !watch.watchOnly &&
      installedSigner != null &&
      watch.watchVersions.none { installedSigner in it.signer.orEmpty() }
  val handle = rememberWatchInstall(item.app.packageName)
  val paired = handle?.watch
  // die Versionen aus dem Katalog mit Datei, höchste zuerst
  val versions =
    remember(item.versions, watch) {
      watch.watchVersions.mapNotNull { watchVersion ->
        item.versions
          ?.firstOrNull {
            it.version.isForWatch && it.version.versionCode == watchVersion.versionCode
          }
          ?.version as? AppVersion
      }
    }
  val target = versions.firstOrNull { paired == null || it.fitsWatch(paired.sdk) }
  SectionCard(title = stringResource(R.string.myzel_details_watch), modifier = CardSpacing) {
    InfoRow(stringResource(R.string.myzel_details_watch_version)) {
      ValueText(target?.versionName ?: watch.watchVersions.first().versionName, mono = true)
    }
    WatchNote(
      stringResource(
        when {
          watch.watchOnly -> R.string.myzel_details_watch_only_text
          otherSigner -> R.string.myzel_details_watch_other_signer
          else -> R.string.myzel_details_watch_text
        }
      )
    )
    if (handle != null && item.versions != null) {
      Column(Modifier.padding(top = 12.dp), verticalArrangement = spacedBy(10.dp)) {
        when {
          paired == null -> WatchNote(stringResource(R.string.myzel_details_watch_set_up_first))
          target == null ->
            WatchNote(stringResource(R.string.myzel_details_watch_no_version, paired.sdk))
          else -> {
            val lifecycle = LocalLifecycleOwner.current.lifecycle
            val keepChecking = handle.keepChecking
            // die Verbindung prüfen, solange die Seite zu sehen ist
            LaunchedEffect(paired.keyId) {
              lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) { keepChecking() }
            }
            WatchActions(handle, target)
            AskAfterPhoneInstall(item, handle, target)
          }
        }
      }
    }
  }
}

/**
 * Die Karte „Uhr“ auf der Seite von Myzel selbst. Seine Uhr-Version ist Myzel Wear: Die Einrichtung
 * bringt sie auf die Uhr, danach bringt Myzel jede neuere aus den Paketquellen von selbst hin
 * ([de.needanapp.myzel.watch.WatchBridge.wearUpdate]). Statt der Knöpfe zum Installieren zeigt die
 * Karte deshalb, welche Myzel Wear auf der Uhr ist, oder ohne Uhr den Weg zur Einrichtung.
 */
@Composable
private fun MyzelWearCard(item: LoadedAppDetailsItem, watch: WatchApp, onSetUp: () -> Unit) {
  val handle = rememberWatchInstall(item.app.packageName)
  val paired = handle?.watch
  val wearVersions =
    remember(item.versions) {
      item.versions.orEmpty().mapNotNull {
        (it.version as? AppVersion)?.takeIf { v -> v.isForWatch }
      }
    }
  // die neueste, die auf die Uhr passt, wie bei MyzelWearSource.latest
  val target =
    watch.watchVersions
      .mapNotNull { watchVersion ->
        wearVersions.firstOrNull { it.versionCode == watchVersion.versionCode }
      }
      .firstOrNull { paired == null || it.fitsWatch(paired.sdk) }
  SectionCard(title = stringResource(R.string.myzel_details_watch), modifier = CardSpacing) {
    InfoRow(stringResource(R.string.myzel_details_watch_version)) {
      ValueText(target?.versionName ?: watch.watchVersions.first().versionName, mono = true)
    }
    if (paired != null && paired.wearVersionCode > 0) {
      InfoRow(stringResource(R.string.myzel_details_wear_on_watch)) {
        // Myzel Wear trägt den Versionsnamen von Myzel; ältere fehlen in der Paketquelle
        val installed =
          wearVersions.firstOrNull { it.versionCode == paired.wearVersionCode }?.versionName
            ?: stringResource(R.string.myzel_details_wear_code, paired.wearVersionCode)
        ValueText(installed, mono = true)
      }
    }
    WatchNote(stringResource(R.string.myzel_details_wear_text))
    if (handle == null) return@SectionCard
    Column(Modifier.padding(top = 12.dp), verticalArrangement = spacedBy(10.dp)) {
      val wearUpdate = handle.wearUpdate
      when {
        paired == null -> {
          WatchNote(stringResource(R.string.myzel_details_wear_set_up))
          PillButton(stringResource(R.string.myzel_details_wear_set_up_button), onClick = onSetUp)
        }
        wearUpdate != null -> WearUpdateNote(wearUpdate)
        // gleich nach der Einrichtung, bis Myzel Wear sich meldet
        paired.wearVersionCode <= 0 ->
          WatchNote(stringResource(R.string.myzel_details_wear_unknown))
        target != null && target.versionCode > paired.wearVersionCode ->
          WatchNote(stringResource(R.string.myzel_details_wear_update_due, target.versionName))
        else ->
          WatchNote(
            stringResource(R.string.myzel_details_wear_current, paired.name),
            MaterialTheme.colorScheme.primary,
          )
      }
    }
  }
}

/**
 * Nach einer Installation oder einem Update auf dem Handy, während die Seite offen ist: Soll die
 * App auch auf die Uhr? Nicht, wenn dort schon dieselbe oder eine neuere Version ist, wenn sie dort
 * anders signiert ist oder wenn der Nutzer „Nie fragen“ gewählt hat.
 */
@Composable
private fun AskAfterPhoneInstall(
  item: LoadedAppDetailsItem,
  handle: WatchInstallHandle,
  target: AppVersion,
) {
  var ask by rememberSaveable { mutableStateOf(false) }
  var previous by remember { mutableStateOf<InstallState?>(null) }
  val current by rememberUpdatedState(handle)
  LaunchedEffect(item.installState) {
    val state = item.installState
    // nur ein Wechsel von „läuft“ zu „installiert“; nicht beim Öffnen der Seite
    val justInstalled = state is InstallState.Installed && previous?.showProgress == true
    previous = state
    if (
      justInstalled &&
        current.askAfterInstall &&
        !current.state.isRunning &&
        wantsWatch(current.app, target)
    ) {
      ask = true
    }
  }
  if (!ask) return
  val update = handle.app?.installed == true
  ContentDialog(
    title =
      stringResource(
        if (update) R.string.myzel_watch_ask_update_title else R.string.myzel_watch_ask_title
      ),
    confirmLabel =
      stringResource(
        if (update) R.string.myzel_details_watch_update else R.string.myzel_watch_ask_confirm
      ),
    onConfirm = {
      ask = false
      handle.install(target)
    },
    onDismiss = { ask = false },
    dismissLabel = stringResource(R.string.myzel_watch_ask_later),
    extraLabel = stringResource(R.string.myzel_watch_ask_never),
    onExtra = {
      ask = false
      handle.setAskAfterInstall(false)
    },
    icon = Icons.Rounded.Watch,
  ) {
    Text(
      stringResource(
        R.string.myzel_watch_ask_text,
        item.name,
        target.versionName,
        handle.watch?.name.orEmpty(),
      )
    )
    if (handle.reach !is WatchReach.Ok) {
      Text(
        stringResource(R.string.myzel_watch_ask_open),
        modifier = Modifier.padding(top = 8.dp),
      )
    }
  }
}

/** Ob [target] auf die Uhr soll: Dort fehlt die App, oder dort ist eine ältere, gleich signiert. */
private fun wantsWatch(app: WatchPackage?, target: AppVersion): Boolean {
  if (app == null || !app.installed) return true
  val expected = target.signer?.sha256.orEmpty()
  val sameSigner = expected.isEmpty() || app.signers.isEmpty() || app.signers.any { it in expected }
  return sameSigner && target.versionCode > app.versionCode
}

/**
 * Was gerade auf der Uhr geschieht und, wenn nichts läuft, was dort installiert ist, mit den
 * passenden Knöpfen: installieren, wenn die App fehlt; aktualisieren, wenn eine ältere Version da
 * ist; entfernen, wenn sie da ist. Frei sind sie erst, wenn Myzel Wear antwortet und den Stand der
 * App genannt hat.
 */
@Composable
private fun WatchActions(handle: WatchInstallHandle, target: AppVersion) {
  val scheme = MaterialTheme.colorScheme
  val state = handle.state
  val wearUpdate = handle.wearUpdate
  // ein laufendes Update von Myzel Wear, oder ein gescheitertes, wenn die Uhr deshalb nicht
  // antwortet
  if (
    wearUpdate != null &&
      (wearUpdate !is WearUpdateState.Failed || handle.reach is WatchReach.Unreachable)
  ) {
    WearUpdateNote(wearUpdate)
  }
  WatchTaskNote(state)
  if (state.isRunning) return
  val reach = handle.reach
  // bis die Uhr antwortet, bleiben die Knöpfe vom letzten bekannten Stand stehen, aber gesperrt
  val app = handle.app
  val ready = reach is WatchReach.Ok && app != null
  if (!ready) WatchReachNote(reach)
  if (app == null || !app.installed) {
    if (ready && reach is WatchReach.Ok) {
      WatchNote(stringResource(R.string.myzel_details_watch_ready, reach.info.name), scheme.primary)
    }
    PillButton(
      stringResource(R.string.myzel_details_watch_install),
      onClick = { handle.install(target) },
      enabled = ready,
    )
    return
  }
  val expected = target.signer?.sha256.orEmpty()
  val otherSigner =
    expected.isNotEmpty() && app.signers.isNotEmpty() && app.signers.none { it in expected }
  val canUpdate = !otherSigner && target.versionCode > app.versionCode
  if (ready) {
    when {
      otherSigner -> {
        WatchNote(
          stringResource(R.string.myzel_details_watch_other_signer_installed, app.versionName),
          scheme.error,
        )
        OtherSignerInfo(app)
      }
      canUpdate ->
        WatchNote(
          stringResource(
            R.string.myzel_details_watch_update_available,
            app.versionName,
            target.versionName,
          )
        )
      else ->
        WatchNote(
          stringResource(R.string.myzel_details_watch_installed, app.versionName),
          scheme.primary,
        )
    }
  }
  if (canUpdate) {
    Row(horizontalArrangement = spacedBy(10.dp)) {
      DeviceKey(
        stringResource(R.string.myzel_details_watch_update),
        onClick = { handle.install(target) },
        modifier = Modifier.weight(3f),
        enabled = ready,
      )
      DeviceKey(
        stringResource(R.string.myzel_details_watch_remove_short),
        onClick = handle.uninstall,
        modifier = Modifier.weight(2f),
        style = KeyStyle.GRAPHITE,
        enabled = ready,
      )
    }
  } else {
    DeviceKey(
      stringResource(R.string.myzel_details_watch_remove),
      onClick = handle.uninstall,
      modifier = Modifier.fillMaxWidth(),
      style = KeyStyle.GRAPHITE,
      enabled = ready,
    )
  }
}

/**
 * Woher die anders signierte App auf der Uhr kommt: wer sie installiert hat, ihre Signatur und
 * welche Paketquellen sie so signiert anbieten.
 */
@Composable
private fun OtherSignerInfo(app: WatchPackage) {
  WatchNote(
    stringResource(R.string.myzel_details_watch_installer, watchInstallerLabel(app.installer))
  )
  app.signers.firstOrNull()?.let {
    WatchNote(stringResource(R.string.myzel_details_watch_signer, it.shortHash()))
  }
  val repos = rememberReposSignedLike(app) ?: return
  WatchNote(
    if (repos.isEmpty()) stringResource(R.string.myzel_details_watch_signer_no_repo)
    else stringResource(R.string.myzel_details_watch_signer_repos, repos.joinToString(", "))
  )
}

/** Was auf der Uhr gerade geschieht, oder woran es zuletzt scheiterte. */
@Composable
private fun WatchTaskNote(state: WatchInstallState?) {
  when (state) {
    null,
    // den Grund nennt der Stand der Verbindung darunter
    is WatchInstallState.Unreachable -> {}
    is WatchInstallState.Failed -> WatchNote(watchStateText(state), MaterialTheme.colorScheme.error)
    is WatchInstallState.Downloading -> WatchProgress(watchStateText(state), state.fraction)
    is WatchInstallState.Sending -> WatchProgress(watchStateText(state), state.fraction)
    else -> WatchProgress(watchStateText(state), null)
  }
}

/** Ob Myzel Wear antwortet. */
@Composable
private fun WatchReachNote(reach: WatchReach) {
  val scheme = MaterialTheme.colorScheme
  when (reach) {
    // bei Ok fehlt noch der Stand der App
    WatchReach.Unknown,
    WatchReach.Checking,
    is WatchReach.Ok ->
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = spacedBy(8.dp),
        modifier = Modifier.padding(top = 6.dp),
      ) {
        CircularProgressIndicator(Modifier.size(14.dp), strokeWidth = 2.dp)
        Text(
          stringResource(R.string.myzel_details_watch_checking),
          style = MaterialTheme.typography.small(13.sp),
          color = scheme.onSurfaceVariant,
        )
      }
    is WatchReach.Unreachable -> {
      WatchNote(stringResource(reach.reason.text()), scheme.error)
      // muss die Uhr neu eingerichtet werden, wartet Myzel nicht
      if (reach.reason !in NEEDS_SET_UP) {
        WatchNote(stringResource(R.string.myzel_details_watch_waiting))
      }
    }
  }
}

private val NEEDS_SET_UP =
  setOf(
    WatchException.Reason.NOT_SET_UP,
    WatchException.Reason.REFUSED,
    WatchException.Reason.VERSION,
  )

@Composable
private fun WatchProgress(text: String, fraction: Float?) {
  val scheme = MaterialTheme.colorScheme
  Column(verticalArrangement = spacedBy(6.dp)) {
    Text(text, style = MaterialTheme.typography.small(13.sp, FontWeight.Medium))
    if (fraction != null) {
      LinearProgressIndicator(
        progress = { fraction },
        trackColor = scheme.outlineVariant,
        modifier = Modifier.fillMaxWidth(),
      )
    } else {
      LinearProgressIndicator(
        trackColor = scheme.outlineVariant,
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}

@Composable
private fun WatchNote(text: String, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
  Text(
    text = text,
    style = MaterialTheme.typography.small(13.sp),
    color = color,
    modifier = Modifier.padding(top = 6.dp),
  )
}

@Composable
private fun SecurityCard(item: LoadedAppDetailsItem) {
  val signer = item.installedSigner ?: item.suggestedVersion?.signer?.sha256?.firstOrNull()
  val antiFeatures = item.antiFeatures.orEmpty()
  if (signer == null && antiFeatures.isEmpty()) return
  SectionCard(stringResource(R.string.myzel_details_security), CardSpacing) {
    signer?.let { hash ->
      DetailRow(stringResource(R.string.myzel_details_certificate)) {
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
    if (antiFeatures.isNotEmpty()) {
      DetailRow(stringResource(R.string.myzel_details_anti_features)) {
        FlowRow(horizontalArrangement = spacedBy(6.dp), verticalArrangement = spacedBy(6.dp)) {
          antiFeatures.forEach { TagPill(it.name) }
        }
      }
    }
  }
}

/** Kopier-Symbol 14 dp in einer Fläche von 18 dp. */
@Composable
internal fun CopyButton(text: String) {
  val clipboard = LocalClipboard.current
  val scope = rememberCoroutineScope()
  val label = stringResource(R.string.myzel_details_copy)
  Box(
    contentAlignment = Alignment.Center,
    modifier =
      Modifier.size(18.dp).clip(RoundedCornerShape(10.dp)).clickable(onClickLabel = label) {
        scope.launch { clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(null, text))) }
      },
  ) {
    Icon(Icons.Rounded.ContentCopy, contentDescription = label, modifier = Modifier.size(14.dp))
  }
}

@Composable
private fun DetailsCard(item: LoadedAppDetailsItem) {
  SectionCard(stringResource(R.string.myzel_details_details), CardSpacing) {
    DetailRow(stringResource(R.string.myzel_details_package)) {
      // lange Paketnamen brechen an den Punkten um statt mitten im Wort; kopiert wird der Name
      // ohne die unsichtbaren Trennstellen, deshalb Kopierknopf statt Markieren
      Text(
        text = item.app.packageName.replace(".", ".\u200B"),
        style = MaterialTheme.typography.mono.copy(fontSize = 14.sp),
        modifier = Modifier.weight(1f, fill = false),
      )
      Spacer(Modifier.width(4.dp))
      CopyButton(item.app.packageName)
    }
    if (item.repositories.isNotEmpty()) {
      DetailRow(stringResource(R.string.myzel_details_sources)) {
        // die gewählte Paketquelle zuerst, nach einem Strich die anderen zum Wechseln
        val current = item.repositories.firstOrNull { it.repoId == item.app.repoId }
        val others = item.repositories.filter { it.repoId != item.app.repoId }
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = spacedBy(8.dp),
          modifier = Modifier.horizontalScroll(rememberScrollState()),
        ) {
          current?.let { SourceButton(it, item, enabled = false) }
          if (current != null && others.isNotEmpty()) {
            Box(
              Modifier.width(1.dp)
                .height(24.dp)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
            )
          }
          others.forEach { SourceButton(it, item, enabled = true) }
        }
      }
    }
    DetailRow(stringResource(R.string.myzel_details_categories)) {
      val names = item.categories?.map { it.name }.orEmpty()
      if (names.isEmpty()) {
        ValueText(stringResource(R.string.myzel_details_none), size = 16.sp)
      } else {
        FlowRow(horizontalArrangement = spacedBy(6.dp), verticalArrangement = spacedBy(6.dp)) {
          names.forEach { TagPill(it) }
        }
      }
    }
    item.app.license?.let { license ->
      DetailRow(stringResource(R.string.myzel_details_license)) {
        LinkText(license, url = "https://spdx.org/licenses/$license")
      }
    }
    DetailRow(stringResource(R.string.myzel_details_added)) {
      ValueText(item.app.added.format(Date))
    }
  }
}

/** Symbol einer Paketquelle, 32 dp in einer Fläche von 48 dp; tippen wechselt zu ihr. */
@Composable
private fun SourceButton(repo: Repository, item: LoadedAppDetailsItem, enabled: Boolean) {
  Box(
    contentAlignment = Alignment.Center,
    modifier =
      Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).clickable(enabled = enabled) {
        item.actions.onRepoChanged(repo.repoId)
      },
  ) {
    RepoIcon(repo, item.proxy, Modifier.size(32.dp).clip(RoundedCornerShape(32.dp * 0.22f)))
  }
}

@Composable
private fun DescriptionCard(item: LoadedAppDetailsItem) {
  val description = item.description
  val summary = item.summary
  if (description == null && summary == null) return
  var expanded by rememberSaveable { mutableStateOf(false) }
  SectionCard(stringResource(R.string.myzel_details_description), CardSpacing) {
    summary?.let {
      Text(
        text = it,
        style = MaterialTheme.typography.value.copy(fontSize = 15.sp),
        modifier = Modifier.padding(bottom = 8.dp),
      )
    }
    description?.let {
      SelectionContainer {
        Text(
          text = AnnotatedString.fromHtml(it),
          style = MaterialTheme.typography.small(14.sp).copy(lineHeight = 21.sp),
          maxLines = if (expanded) Int.MAX_VALUE else 6,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Box(Modifier.padding(top = 8.dp)) {
        LinkText(
          stringResource(if (expanded) R.string.myzel_details_less else R.string.myzel_details_more)
        ) {
          expanded = !expanded
        }
      }
    }
  }
}

@Composable
private fun LinksCard(item: LoadedAppDetailsItem) {
  val app = item.app
  val links = buildList {
    app.webSite?.let { add(R.string.myzel_details_website to it) }
    app.sourceCode?.let { add(R.string.myzel_details_source_code to it) }
    app.issueTracker?.let { add(R.string.myzel_details_issues to it) }
    app.changelog?.let { add(R.string.myzel_details_changelog to it) }
    app.translation?.let { add(R.string.myzel_details_translation to it) }
    app.video?.getBestLocale(LocaleListCompat.getDefault())?.let {
      add(R.string.myzel_details_video to it)
    }
    app.authorWebSite?.let { add(R.string.myzel_details_author to it) }
    app.authorEmail?.let { add(R.string.myzel_details_email to "mailto:$it") }
    item.donateLinks.forEach { add(R.string.myzel_details_donate to it.url) }
  }
  if (links.isEmpty()) return
  SectionCard(stringResource(R.string.myzel_details_links), CardSpacing) {
    links.forEach { (label, url) ->
      DetailRow(stringResource(label)) {
        LinkText(
          url
            .removePrefix("mailto:")
            .removePrefix("https://")
            .removePrefix("www.")
            .removeSuffix("/"),
          url = url,
        )
      }
    }
  }
}

/**
 * Die Haupttaste: Installieren oder Aktualisieren mit Größe, sonst Öffnen; während einer
 * Installation eine pulsierende Graphit-Taste mit Abbrechen daneben ([InstallProgress]). Lässt sich
 * die App deinstallieren, steht rechts daneben die schmalere Graphit-Taste Entfernen.
 */
@Composable
private fun MainPill(
  item: LoadedAppDetailsItem,
  uninstallLauncher: ActivityResultLauncher<Intent>,
  watchOnly: Boolean,
) {
  val context = LocalContext.current
  var showMeteredDialog by remember { mutableStateOf(false) }
  val version = item.suggestedVersion
  val size = version?.size?.megabytes() ?: ""
  val install = {
    if (item.networkState.showWarningDialog) {
      showMeteredDialog = true
    } else if (version != null) {
      item.actions.installAction(item.app, version, item.icon)
    }
  }
  val needanapp = isNeedanappDesign
  val keyHeight = if (needanapp) NeedanappKeyHeight else MainKeyHeight
  val pillModifier = Modifier.padding(horizontal = if (needanapp) 12.dp else 16.dp, vertical = 8.dp)
  if (item.mainButtonState == MainButtonState.PROGRESS) {
    InstallProgress(item, pillModifier)
  } else {
    val uninstallIntent = item.actions.uninstallIntent
    val split = uninstallIntent != null && item.mainButtonState != MainButtonState.INSTALL
    val (label, onClick) =
      when (item.mainButtonState) {
        MainButtonState.INSTALL -> stringResource(R.string.myzel_details_install, size) to install
        MainButtonState.UPDATE ->
          // neben Entfernen ist kein Platz für die Größe, sie steht im Raster darüber
          (if (split) stringResource(R.string.myzel_details_update_title)
          else stringResource(R.string.myzel_details_update, size)) to install
        MainButtonState.NONE ->
          if (item.showOpenButton) {
            stringResource(R.string.myzel_details_open) to
              {
                context.startActivitySafe(item.actions.launchIntent)
              }
          } else if (watchOnly) {
            // aufs Handy gibt es nichts zu installieren
            stringResource(R.string.myzel_details_status_watch) to null
          } else stringResource(R.string.myzel_details_status_up_to_date) to null
        else -> stringResource(R.string.myzel_details_status_loading) to null
      }
    val icon =
      when (item.mainButtonState) {
        MainButtonState.INSTALL -> Icons.Rounded.Download
        MainButtonState.UPDATE -> if (needanapp) Icons.Rounded.Download else Icons.Rounded.Update
        MainButtonState.NONE ->
          if (item.showOpenButton) Icons.AutoMirrored.Rounded.OpenInNew else null
        else -> null
      }
    if (split) {
      Row(pillModifier, horizontalArrangement = spacedBy(10.dp)) {
        DeviceKey(
          label,
          onClick = { onClick?.invoke() },
          modifier = Modifier.weight(3f),
          icon = icon,
          enabled = onClick != null,
          height = keyHeight,
        )
        DeviceKey(
          stringResource(R.string.myzel_details_remove),
          onClick = { uninstallLauncher.launchSafe(uninstallIntent) },
          modifier = Modifier.weight(2f),
          style = KeyStyle.GRAPHITE,
          height = keyHeight,
        )
      }
    } else {
      DeviceKey(
        label,
        onClick = { onClick?.invoke() },
        modifier = pillModifier.fillMaxWidth(),
        icon = icon,
        enabled = onClick != null,
        height = keyHeight,
      )
    }
  }
  if (showMeteredDialog) {
    MeteredConnectionDialog(
      numBytes = version?.size,
      onConfirm = { notWarnWhenMetered ->
        if (notWarnWhenMetered) item.actions.onNotWarnWhenMetered()
        if (version != null) item.actions.installAction(item.app, version, item.icon)
      },
      onDismiss = { showMeteredDialog = false },
    )
  }
}

/**
 * Während einer Installation: eine Graphit-Taste, die langsam pulsiert, mit „Wird heruntergeladen
 * …“ oder „Wird installiert …“; den Fortschritt zeigt die Anzeige der Versionskarte. Rechts daneben
 * eine runde Taste zum Abbrechen. Bei reduzierten Effekten pulsiert sie nicht.
 */
@Composable
private fun InstallProgress(item: LoadedAppDetailsItem, modifier: Modifier) {
  val design = LocalDesignColors.current
  // 0: gedämpft, 1: hell; gelesen erst beim Zeichnen
  val pulse: State<Float> =
    if (LocalDesignEffects.current.richMotion) {
      rememberInfiniteTransition(label = "pulse")
        .animateFloat(
          initialValue = 0f,
          targetValue = 1f,
          animationSpec = infiniteRepeatable(tween(PULSE_MILLIS), RepeatMode.Reverse),
          label = "pulse",
        )
    } else remember { mutableFloatStateOf(1f) }
  val text =
    stringResource(
      if (item.installState is InstallState.Downloading) R.string.myzel_details_downloading
      else R.string.myzel_details_status_installing
    )
  val (graphite, onGraphite) = keyColors(KeyStyle.GRAPHITE)
  val needanapp = isNeedanappDesign
  val keyHeight = if (needanapp) NeedanappKeyHeight else MainKeyHeight
  val keyShape = RoundedCornerShape(if (needanapp) 12.dp else 18.dp)
  Row(modifier.fillMaxWidth(), horizontalArrangement = spacedBy(10.dp)) {
    Box(
      contentAlignment = Alignment.Center,
      modifier =
        Modifier.weight(1f)
          .height(keyHeight)
          .clip(keyShape)
          .background(graphite)
          .keyEdge(KeyStyle.GRAPHITE)
          .padding(horizontal = 14.dp),
    ) {
      Text(
        text = text.labelCase(),
        style = DesignType.key,
        color = onGraphite,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier =
          Modifier.semantics { disabled() }
            .graphicsLayer { alpha = lerp(PULSE_TEXT_FROM, PULSE_TEXT_TO, pulse.value) },
      )
    }
    var cancelled by remember { mutableStateOf(false) }
    Box(
      contentAlignment = Alignment.Center,
      modifier =
        Modifier.size(keyHeight)
          .clip(keyShape)
          .background(if (needanapp) design.tile else design.aluKey)
          .then(if (needanapp) Modifier else Modifier.border(1.dp, design.aluKeyBorder, keyShape))
          .keyEdge(KeyStyle.ALU, enabled = !cancelled)
          .clickable(enabled = !cancelled, role = Role.Button) {
            item.actions.cancelInstall()
            cancelled = true
          },
    ) {
      Icon(
        Icons.Rounded.Close,
        contentDescription = stringResource(R.string.cancel),
        tint = MaterialTheme.colorScheme.onSurface.copy(alpha = if (cancelled) 0.38f else 1f),
      )
    }
  }
}

private val MainKeyHeight = 60.dp
private val NeedanappKeyHeight = 52.dp

// Deckkraft der Schrift auf der pulsierenden Taste
private const val PULSE_MILLIS = 900
private const val PULSE_TEXT_FROM = 0.45f
private const val PULSE_TEXT_TO = 1f

/**
 * Unten rechts der runde Graphit-Knopf mit dem Myzel; beim Antippen läuft ein Signal hindurch, er
 * bleibt eingedrückt und klappt darüber ein dunkles Feld auf: oben eine Anzeige, dann App-Info (nur
 * installiert), Teilen und APK teilen, darunter Beta-Versionen und ignorierte Updates als Schalter
 * (das Feld bleibt dabei offen, damit man den Zustand sieht). Ein Tipp daneben oder Zurück schließt
 * es. Der Knopf verschwindet beim Scrollen nach unten und kommt beim Scrollen nach oben wieder.
 * Deinstallieren steht nur neben der Haupttaste ([MainPill]).
 */
@Composable
private fun OptionsKnob(
  item: LoadedAppDetailsItem,
  hazeState: HazeState,
  visible: Boolean,
  modifier: Modifier,
) {
  var open by rememberSaveable { mutableStateOf(false) }
  var signal by remember { mutableIntStateOf(0) }
  LaunchedEffect(visible) { if (!visible) open = false }
  BackHandler(enabled = open) { open = false }
  // im Design needanapp dunkelt das offene Feld die Seite nicht ab, wie das Menü der Pille
  val needanapp = isNeedanappDesign
  val scrim by
    animateColorAsState(
      if (open && !needanapp) MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f)
      else Color.Transparent,
      emphasizedSpec(),
      label = "optionsScrim",
    )
  Box(
    contentAlignment = Alignment.BottomEnd,
    modifier =
      modifier
        .then(if (open) Modifier.fillMaxSize() else Modifier)
        .background(scrim)
        .then(
          if (open) Modifier.pointerInput(Unit) { detectTapGestures { open = false } } else Modifier
        ),
  ) {
    AnimatedVisibility(
      visible = visible,
      enter = slideInVertically(emphasizedSpec()) { it } + fadeIn(emphasizedSpec()),
      exit = slideOutVertically(emphasizedSpec()) { it } + fadeOut(emphasizedSpec()),
    ) {
      Column(
        horizontalAlignment = Alignment.End,
        modifier =
          Modifier.navigationBarsPadding().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
      ) {
        AnimatedVisibility(
          visible = open,
          enter =
            expandVertically(emphasizedSpec(), expandFrom = Alignment.Bottom, clip = false) +
              scaleIn(
                emphasizedSpec(),
                initialScale = 0.6f,
                transformOrigin = TransformOrigin(1f, 1f),
              ) +
              fadeIn(emphasizedSpec()),
          exit =
            shrinkVertically(emphasizedSpec(), shrinkTowards = Alignment.Bottom, clip = false) +
              scaleOut(
                emphasizedSpec(),
                targetScale = 0.6f,
                transformOrigin = TransformOrigin(1f, 1f),
              ) +
              fadeOut(emphasizedSpec()),
        ) {
          OptionsPanel(item, hazeState, Modifier.padding(bottom = 10.dp)) { open = false }
        }
        FloatingKnob(
          stringResource(R.string.myzel_details_more_options, item.name),
          pressed = open,
          hazeState = hazeState,
          onClick = {
            open = !open
            if (open) signal++
          },
        ) {
          MyzelLogo(Modifier.size(if (needanapp) 40.dp else 26.dp), signal = signal)
        }
      }
    }
  }
}

/** Das dunkle Feld über dem Optionen-Knopf, siehe [OptionsKnob]; [onClose] schließt es. */
@Composable
private fun OptionsPanel(
  item: LoadedAppDetailsItem,
  hazeState: HazeState,
  modifier: Modifier,
  onClose: () -> Unit,
) {
  val context = LocalContext.current
  val actions = item.actions
  val shareApkTitle = stringResource(R.string.menu_share_apk)
  val entries = buildList {
    if (item.installedVersionCode != null) {
      add(
        PillAction(Icons.Outlined.Info, stringResource(R.string.myzel_details_app_info)) {
          val uri = "package:${item.app.packageName}".toUri()
          context.startActivitySafe(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
        }
      )
    }
    actions.shareIntent?.let { intent ->
      add(
        PillAction(Icons.Outlined.Share, stringResource(R.string.menu_share)) {
          context.startActivitySafe(intent)
        }
      )
    }
    actions.shareApk?.let { shareApk ->
      add(
        PillAction(Icons.Outlined.Android, shareApkTitle) {
          context.startActivitySafe(Intent.createChooser(shareApk, shareApkTitle))
        }
      )
    }
  }
  FloatingSurface(RoundedCornerShape(24.dp), hazeState, modifier) {
    // ein Tipp ins Feld schließt es nicht
    Column(
      Modifier.widthIn(max = OptionsPanelWidth)
        .fillMaxWidth()
        .pointerInput(Unit) { detectTapGestures {} }
        .padding(8.dp)
    ) {
      DeviceDisplay(
        stringResource(R.string.myzel_details_options),
        StatusTone.ACCENT,
        detail = item.name,
        compact = true,
      )
      Spacer(Modifier.height(4.dp))
      entries.forEachIndexed { index, action ->
        FloatingMenuEntry(action, divider = index < entries.lastIndex) {
          onClose()
          action.onClick()
        }
      }
      val ignoreThis = actions.ignoreThisUpdate
      val ignoreAll = actions.ignoreAllUpdates
      if (item.appPrefs != null || ignoreThis != null || ignoreAll != null) {
        FloatingMenuLabel(stringResource(R.string.updates))
        if (item.appPrefs != null) {
          FloatingMenuSwitch(
            stringResource(R.string.menu_release_channel_beta),
            checked = item.allowsBetaVersions,
            onCheckedChange = { actions.allowBetaVersions() },
            enabled = !item.ignoresAllUpdates,
            divider = ignoreThis != null || ignoreAll != null,
          )
        }
        ignoreThis?.let {
          FloatingMenuSwitch(
            stringResource(R.string.menu_ignore_this),
            checked = item.ignoresCurrentUpdate,
            onCheckedChange = { ignoreThis() },
            enabled = !item.ignoresAllUpdates,
            divider = ignoreAll != null,
          )
        }
        ignoreAll?.let {
          FloatingMenuSwitch(
            stringResource(R.string.menu_ignore_all),
            checked = item.ignoresAllUpdates,
            onCheckedChange = { ignoreAll() },
          )
        }
      }
    }
  }
}

private val OptionsPanelWidth = 300.dp

/**
 * Was der Kopf von F-Droid bei Installationen erledigt: Bestätigung anfordern, sie nach der
 * Rückkehr prüfen (Fehler im Android-Installer) und Fehler melden.
 */
@Composable
private fun InstallStateEffects(item: LoadedAppDetailsItem) {
  var showInstallError by remember { mutableStateOf(false) }
  LaunchedEffect(item.installState) {
    val state = item.installState
    if (state is InstallState.UserConfirmationNeeded) {
      Log.i("AppDetailsPage", "Requesting user confirmation... $state")
      item.actions.requestUserConfirmation(state)
    } else if (state is InstallState.Error) {
      showInstallError = true
    }
  }
  val lifecycleOwner = LocalLifecycleOwner.current
  val currentInstallState by rememberUpdatedState(item.installState)
  var numChecks by remember { mutableIntStateOf(0) }
  DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
      if (event == Lifecycle.Event.ON_RESUME) {
        val state = currentInstallState
        if (state is InstallState.UserConfirmationNeeded && numChecks < 3) {
          numChecks += 1
          item.actions.checkUserConfirmation(state)
        } else if (state is InstallState.UserConfirmationNeeded) {
          item.actions.cancelInstall()
        }
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
  }
  val state = item.installState
  if (showInstallError && state is InstallState.Error) {
    InstallErrorDialog(item.name, state.msg) { showInstallError = false }
  }
}
