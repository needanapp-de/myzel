package de.needanapp.myzel.ui.apps

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import de.needanapp.design.components.TagPill
import de.needanapp.design.theme.LocalDesignColors
import de.needanapp.myzel.watch.WatchCatalog
import de.needanapp.myzel.watch.rememberWatchApp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import org.fdroid.R
import org.fdroid.database.AppOverviewItem
import org.fdroid.database.FDroidDatabase
import org.fdroid.updates.UpdatesManager
import org.fdroid.utils.IoDispatcher

/**
 * Die neuen Apps: wie die Liste „Neu“ von F-Droid seit höchstens 14 Tagen in einer eingeschalteten
 * Paketquelle. Die Abfrage läuft bei jeder Änderung der Datenbank neu und rechnet die 14 Tage dabei
 * ab dem aktuellen Zeitpunkt. Entdecken und die Pillen „Neu“ teilen sich diese eine Abfrage.
 */
@Singleton
class NewApps @Inject constructor(db: FDroidDatabase, @IoDispatcher scope: CoroutineScope) {
  val apps: SharedFlow<List<AppOverviewItem>> =
    db.getAppDao().getNewAppsFlow().shareIn(scope, SharingStarted.WhileSubscribed(5_000), 1)

  val packageNames: StateFlow<Set<String>> =
    apps
      .map { apps -> apps.mapTo(HashSet()) { it.packageName } }
      .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptySet())

  /** Wie viele neue Apps aus welcher Paketquelle kommen, nach deren repoId. */
  val countsByRepo: StateFlow<Map<Long, Int>> =
    apps
      .map { apps ->
        apps
          .groupBy { it.repoId }
          .mapValues { (_, items) -> items.distinctBy { it.packageName }.size }
      }
      .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyMap())
}

/**
 * Die Paketnamen der installierten Apps mit einem Update, wie sie „Meine Apps“ zeigt (ohne
 * ignorierte Updates), aus dem [UpdatesManager] von F-Droid.
 */
@Singleton
class AppUpdates
@Inject
constructor(updatesManager: UpdatesManager, @IoDispatcher scope: CoroutineScope) {
  val packageNames: StateFlow<Set<String>> =
    updatesManager.updates
      .map { updates -> updates.orEmpty().mapTo(HashSet()) { it.packageName } }
      .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptySet())

  /** Wie viele Updates aus welcher Paketquelle kommen, nach deren repoId. */
  val countsByRepo: StateFlow<Map<Long, Int>> =
    updatesManager.updates
      .map { updates -> updates.orEmpty().groupingBy { it.repoId }.eachCount() }
      .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptyMap())
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppBadgesEntryPoint {
  fun newApps(): NewApps

  fun appUpdates(): AppUpdates
}

@Composable
private fun rememberEntryPoint(): AppBadgesEntryPoint {
  val context = LocalContext.current
  return remember { EntryPointAccessors.fromApplication(context, AppBadgesEntryPoint::class.java) }
}

/** Ob die App mit [packageName] neu ist (siehe [NewApps]). */
@Composable
fun rememberIsNew(packageName: String?): Boolean {
  if (packageName == null || LocalInspectionMode.current) return false
  val names by rememberEntryPoint().newApps().packageNames.collectAsState()
  return packageName in names
}

/** Wie viele Apps neu sind und wie viele installierte ein Update haben, etwa für Entdecken. */
@Composable
fun rememberNewAndUpdateCounts(): Pair<Int, Int> {
  if (LocalInspectionMode.current) return 0 to 0
  val entryPoint = rememberEntryPoint()
  val new by entryPoint.newApps().packageNames.collectAsState()
  val updates by entryPoint.appUpdates().packageNames.collectAsState()
  return new.size to updates.size
}

/**
 * Wie viele Apps aus einer Paketquelle neu sind und wie viele installierte von dort ein Update
 * haben.
 */
data class NewAndUpdateCount(val new: Int, val updates: Int)

/** [rememberNewAndUpdateCounts] je Paketquelle, nach deren repoId. */
@Composable
fun rememberNewAndUpdateCountsByRepo(): Map<Long, NewAndUpdateCount> {
  if (LocalInspectionMode.current) return emptyMap()
  val entryPoint = rememberEntryPoint()
  val new by entryPoint.newApps().countsByRepo.collectAsState()
  val updates by entryPoint.appUpdates().countsByRepo.collectAsState()
  return remember(new, updates) {
    (new.keys + updates.keys).associateWith { NewAndUpdateCount(new[it] ?: 0, updates[it] ?: 0) }
  }
}

/** Ob es für die installierte App mit [packageName] ein Update gibt (siehe [AppUpdates]). */
@Composable
fun rememberHasUpdate(packageName: String?): Boolean {
  if (packageName == null || LocalInspectionMode.current) return false
  val names by rememberEntryPoint().appUpdates().packageNames.collectAsState()
  return packageName in names
}

/**
 * Paketname der App, deren Symbol ein Baustein von F-Droid gerade zeichnet, für die Pille „Neu“ in
 * [de.needanapp.myzel.ui.compat.BadgedBox]. Gesetzt vom Detailkopf.
 */
val LocalStampPackageName = compositionLocalOf<String?> { null }

/**
 * Wie eine App in Listen und auf Karten erscheint: Installierte Apps ohne Update treten ausgegraut
 * zurück ([isFaded]), die Pillen sagen, was los ist.
 */
data class AppBadgeState(
  val isInstalled: Boolean,
  val hasUpdate: Boolean,
  val isNew: Boolean,
  /** Es gibt eine Uhr-Version, oder die App ist nur für die Uhr (siehe [WatchCatalog]). */
  val isWatch: Boolean = false,
  /**
   * Vor wie vielen Tagen die App in ihrer Paketquelle zuletzt aktualisiert wurde, höchstens
   * [RECENT_DAYS]; null bei neuen Apps, bei einem Update fürs Gerät und wo es nicht gefragt ist.
   */
  val updatedDays: Int? = null,
) {
  val isFaded: Boolean
    get() = isInstalled && !hasUpdate
}

/**
 * Mit [updatedAt] (Zeitpunkt des letzten Updates in der Paketquelle) bekommt eine App, die in den
 * letzten [RECENT_DAYS] Tagen aktualisiert wurde, die Pille mit dem Alter („↑ heute“), etwa in
 * „Kürzlich aktualisiert“. Neue Apps und solche mit „Update“ bleiben bei ihrer Pille.
 */
@Composable
fun rememberAppBadgeState(
  packageName: String,
  isInstalled: Boolean,
  updatedAt: Long? = null,
): AppBadgeState {
  val hasUpdate = isInstalled && rememberHasUpdate(packageName)
  val isNew = rememberIsNew(packageName)
  val isWatch = rememberWatchApp(packageName) != null
  val updatedDays =
    if (updatedAt == null || updatedAt <= 0 || isNew || hasUpdate) null
    else daysSince(updatedAt).takeIf { it <= RECENT_DAYS }
  return AppBadgeState(isInstalled, hasUpdate, isNew, isWatch, updatedDays)
}

/** So viele Tage gelten Apps als neu oder kürzlich aktualisiert, wie „Neu“ bei F-Droid. */
const val RECENT_DAYS = 14

/** Wie viele Kalendertage [millis] zurückliegt: 0 heute, 1 gestern. */
fun daysSince(millis: Long, today: LocalDate = LocalDate.now()): Int {
  val day = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
  return ChronoUnit.DAYS.between(day, today).toInt().coerceAtLeast(0)
}

/** Die Pille zum Alter eines Updates: „↑ heute“, „↑ gestern“, „↑ 3 T.“. */
@Composable
fun updatedAgoText(days: Int): String =
  when (days) {
    0 -> stringResource(R.string.myzel_age_today)
    1 -> stringResource(R.string.myzel_age_yesterday)
    else -> pluralStringResource(R.plurals.myzel_age_days, days, days)
  }

/**
 * Überschrift eines Tages in Listen nach Datum: „Heute“, „Gestern“, sonst „Montag, 27. Oktober“.
 */
@Composable
fun dayTitle(millis: Long): String =
  when (val days = daysSince(millis)) {
    0 -> stringResource(R.string.myzel_day_today)
    1 -> stringResource(R.string.myzel_day_yesterday)
    else -> {
      val locale = Locale.getDefault()
      val pattern = DateFormat.getBestDateTimePattern(locale, "EEEEdMMMM")
      LocalDate.now().minusDays(days.toLong()).format(DateTimeFormatter.ofPattern(pattern, locale))
    }
  }

/**
 * Die Pillen einer App, als Kinder einer Zeile: „Update“ (Akzentfarbe) oder sonst „Installiert“
 * (Grün), dazu das Alter des letzten Updates (Graphit), „Neu“ und „Uhr“. Steht neben dem Namen oder
 * der Version, wo Platz ist.
 */
@Composable
fun AppBadgePills(state: AppBadgeState) {
  if (state.hasUpdate) {
    TagPill(
      text = stringResource(R.string.myzel_tag_update),
      containerColor = LocalDesignColors.current.signal,
      contentColor = LocalDesignColors.current.onSignal,
      compact = true,
    )
  } else if (state.isInstalled) {
    val colors = LocalDesignColors.current
    TagPill(
      text = stringResource(R.string.myzel_tag_installed),
      containerColor = colors.positive,
      contentColor = colors.onPositive,
      compact = true,
    )
  }
  val updatedDays = state.updatedDays
  if (updatedDays != null) {
    TagPill(
      text = updatedAgoText(updatedDays),
      containerColor = MaterialTheme.colorScheme.inverseSurface,
      contentColor = MaterialTheme.colorScheme.inverseOnSurface,
      compact = true,
    )
  }
  if (state.isNew) {
    TagPill(
      text = stringResource(R.string.myzel_tag_new),
      containerColor = MaterialTheme.colorScheme.tertiary,
      contentColor = MaterialTheme.colorScheme.onTertiary,
      compact = true,
    )
  }
  if (state.isWatch) {
    TagPill(
      text = stringResource(R.string.myzel_tag_watch),
      containerColor = MaterialTheme.colorScheme.secondaryContainer,
      contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
      compact = true,
    )
  }
}

/** [AppBadgePills] in einer eigenen Zeile. */
@Composable
fun AppBadgeRow(state: AppBadgeState, modifier: Modifier = Modifier) {
  Row(
    horizontalArrangement = spacedBy(4.dp),
    verticalAlignment = Alignment.CenterVertically,
    modifier = modifier,
  ) {
    AppBadgePills(state)
  }
}

/** Deckkraft ausgegrauter Inhalte. */
private const val FADED_ALPHA = 0.5f

private val Grayscale =
  RenderEffect.createColorFilterEffect(
      ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
    )
    .asComposeRenderEffect()

/** Ausgegrauter Text: blasser, wenn [faded]. */
fun Modifier.faded(faded: Boolean): Modifier =
  if (faded) graphicsLayer { alpha = FADED_ALPHA } else this

/** Ausgegrautes Symbol: ohne Farbe und blasser, wenn [faded]. */
fun Modifier.fadedIcon(faded: Boolean): Modifier =
  if (faded) {
    graphicsLayer {
      alpha = FADED_ALPHA
      renderEffect = Grayscale
    }
  } else {
    this
  }
