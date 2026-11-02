package de.needanapp.myzel.ui.nav

import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.outlined.SyncProblem
import androidx.compose.material.icons.outlined.Watch
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationRailDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation3.runtime.NavKey
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import de.needanapp.design.components.DeviceDisplay
import de.needanapp.design.components.NavigationPillWithMenu
import de.needanapp.design.components.OptionsSheet
import de.needanapp.design.components.PillAction
import de.needanapp.design.components.PillItem
import de.needanapp.design.components.SheetAction
import de.needanapp.design.components.StatusTone
import de.needanapp.design.theme.isNeedanappDesign
import de.needanapp.myzel.favorites.Favorites
import de.needanapp.myzel.favorites.FavoritesEntryPoint
import de.needanapp.myzel.repo.RepoUpdateRun
import de.needanapp.myzel.repo.RepoUpdateStep
import de.needanapp.myzel.ui.MyzelLogo
import de.needanapp.myzel.watch.ui.WatchPage
import java.util.Date
import org.fdroid.BuildConfig.VERSION_NAME
import org.fdroid.R
import org.fdroid.database.Repository
import org.fdroid.index.RepoManager
import org.fdroid.repo.RepoUpdateManager
import org.fdroid.ui.MainModel
import org.fdroid.ui.navigation.NavDestinations
import org.fdroid.ui.navigation.NavigationKey
import org.fdroid.ui.navigation.getMoreMenuItems
import org.fdroid.ui.navigation.topLevelRoutes

/**
 * Ersatz für org.fdroid.ui.navigation.BottomBar mit derselben Signatur: in der Mitte die
 * Navigationspille (Entdecken, Suche, Meine Apps), rechts ein kleiner runder Knopf, der nach oben
 * ein Menü mit Favoriten, Uhr, Paketquellen, Einstellungen, Alle Apps und Über aufklappt. Die
 * Knöpfe standen bei F-Droid oben in der Kopfzeile von Entdecken.
 */
@Composable
fun BottomBar(model: MainModel, currentNavKey: NavKey, onNav: (NavKey) -> Unit) {
  val host = LocalPillHost.current
  DisposableEffect(host) {
    host?.onPillShown()
    // zurück aus einer Unterseite: die Pillen wieder zeigen, auch wenn die Liste nicht scrollt
    host?.scrollBehavior?.show()
    onDispose { host?.onPillGone() }
  }
  LaunchedEffect(currentNavKey) { host?.scrollBehavior?.show() }
  val context = LocalContext.current
  val repos = rememberRepoState()
  val repoIssues = repos.issues
  val needanapp = isNeedanappDesign
  NavigationPillWithMenu(
    items =
      topLevelRoutes.map { dest ->
        PillItem(
          icon = dest.icon,
          label = stringResource(dest.label),
          badgeCount = if (dest == NavigationKey.MyApps) model.numUpdates else null,
        )
      },
    selectedIndex = topLevelRoutes.indexOf(currentNavKey),
    onSelect = { index ->
      host?.scrollBehavior?.show()
      // ein Tipp wechselt sofort, ohne das Gleiten nach einem Wischen
      TopLevelSwipe.clear()
      onNav(topLevelRoutes[index])
    },
    // Favoriten, Uhr, Paketquellen und Einstellungen, dann was bei F-Droid im Menü oben rechts
    // stand
    menuActions =
      listOf(
        favoritesAction(onNav, rememberFavoriteCount()),
        watchAction(onNav),
        reposAction(repoIssues, onNav, repos.active),
      ) +
        (listOf(NavDestinations.Settings) + getMoreMenuItems(context)).map { dest ->
          PillAction(
            dest.icon,
            stringResource(dest.label),
            value = VERSION_NAME.takeIf { dest == NavDestinations.About },
          ) {
            onNav(dest.id)
          }
        },
    menuIcon = Icons.Default.Menu,
    menuLabel = stringResource(R.string.more),
    hazeState = host?.hazeState,
    visible = host?.scrollBehavior?.isVisible ?: true,
    menuHeader = { RepoDisplay(repos) },
    myzelMenu = true,
    menuButtonContent = { expanded ->
      // das Myzel auf der Graphit-Taste (bei needanapp größer, eingedrückt etwas kleiner); beim
      // Aufklappen läuft ein Signal hindurch
      var signal by remember { mutableIntStateOf(0) }
      LaunchedEffect(expanded) { if (expanded) signal++ }
      val size =
        when {
          !needanapp -> MENU_LOGO_SIZE
          expanded -> NEEDANAPP_MENU_LOGO_PRESSED
          else -> NEEDANAPP_MENU_LOGO_SIZE
        }
      MyzelLogo(Modifier.size(size), signal = signal)
    },
  )
}

/**
 * Die kleine Anzeige oben im Menü: ob die Paketquellen aktuell sind und wann sie zuletzt geladen
 * wurden, oder wie viele sich wiederholt nicht laden lassen. Während einer Aktualisierung zeigt sie
 * ohne Punktreihe, welche Quelle lädt, damit das Menü seine Größe behält.
 */
@Composable
private fun RepoDisplay(repos: RepoState) {
  val last = repos.lastUpdated
  val update = repos.update
  when {
    update != null -> RepoUpdateDisplay(update, compact = true)
    repos.issues > 0 ->
      DeviceDisplay(
        pluralStringResource(R.plurals.myzel_display_repos_issues, repos.issues, repos.issues),
        StatusTone.ERROR,
        compact = true,
      )
    last == null ->
      DeviceDisplay(
        stringResource(R.string.myzel_display_repos_never),
        StatusTone.NEUTRAL,
        compact = true,
      )
    else ->
      DeviceDisplay(
        stringResource(R.string.myzel_display_repos_current),
        StatusTone.POSITIVE,
        detail = repoTimeText(last),
        compact = true,
      )
  }
}

/** Wann die Paketquellen zuletzt geladen wurden: heute als Uhrzeit, sonst als Datum. */
@Composable
internal fun repoTimeText(last: Long): String {
  val context = LocalContext.current
  return if (DateUtils.isToday(last)) DateFormat.getTimeFormat(context).format(Date(last))
  else DateFormat.getDateFormat(context).format(Date(last))
}

/**
 * Ersatz für org.fdroid.ui.navigation.NavigationRail auf großen Bildschirmen: die Seitenleiste von
 * F-Droid, darunter Paketquellen, Einstellungen und Mehr.
 */
@Composable
fun NavigationRail(
  numUpdates: Int,
  hasIssues: Boolean,
  currentNavKey: NavKey,
  onNav: (NavKey) -> Unit,
  modifier: Modifier = Modifier,
) {
  var showMore by rememberSaveable { mutableStateOf(false) }
  val repoIssues = rememberRepoIssues()
  Surface(color = NavigationRailDefaults.ContainerColor, modifier = modifier.fillMaxHeight()) {
    Column {
      org.fdroid.ui.navigation.NavigationRail(
        numUpdates = numUpdates,
        hasIssues = hasIssues,
        currentNavKey = currentNavKey,
        onNav = onNav,
        modifier = Modifier.weight(1f),
      )
      Column(Modifier.padding(start = 12.dp, end = 12.dp, bottom = 16.dp)) {
        toolbarActions(repoIssues, onNav) { showMore = true }
          .forEach { action ->
            IconButton(onClick = action.onClick) {
              val count = action.badgeCount ?: 0
              BadgedBox(badge = { if (count > 0) Badge { Text(count.toString()) } }) {
                Icon(action.icon, contentDescription = action.contentDescription)
              }
            }
          }
      }
    }
  }
  if (showMore) MoreSheet(onDismiss = { showMore = false }, onNav = onNav)
}

@Composable
private fun toolbarActions(
  repoIssues: Int,
  onNav: (NavKey) -> Unit,
  onMore: () -> Unit,
): List<PillAction> =
  listOf(
    favoritesAction(onNav),
    watchAction(onNav),
    reposAction(repoIssues, onNav),
    PillAction(NavDestinations.Settings.icon, stringResource(NavDestinations.Settings.label)) {
      onNav(NavigationKey.Settings)
    },
    PillAction(Icons.Default.MoreHoriz, stringResource(R.string.more), onClick = onMore),
  )

/**
 * Die Favoriten von Myzel, mit dem Logo als Symbol wie beim Knopf in den App-Details; im Menü steht
 * am Ende, wie viele es sind ([count]).
 */
@Composable
private fun favoritesAction(onNav: (NavKey) -> Unit, count: Int? = null): PillAction =
  PillAction(
    ImageVector.vectorResource(R.drawable.myzel_logo),
    stringResource(R.string.myzel_favorites),
    value = count?.takeIf { it > 0 }?.toString(),
  ) {
    onNav(Favorites)
  }

/** Die Seite „Uhr“: Einrichten und Stand der Wear-OS-Uhr. */
@Composable
private fun watchAction(onNav: (NavKey) -> Unit): PillAction =
  PillAction(Icons.Outlined.Watch, stringResource(R.string.myzel_watch)) { onNav(WatchPage) }

/**
 * Die Paketquellen. Lassen sich welche wiederholt nicht aktualisieren, zählt das Abzeichen am
 * Menüknopf sie, und der Eintrag sagt es (das Design-Paket zeigt Abzeichen nur am Knopf).
 */
@Composable
private fun reposAction(issues: Int, onNav: (NavKey) -> Unit, active: Int? = null): PillAction {
  val label = stringResource(NavDestinations.Repos.label)
  return PillAction(
    icon = if (issues > 0) Icons.Outlined.SyncProblem else NavDestinations.Repos.icon,
    contentDescription =
      if (issues > 0) pluralStringResource(R.plurals.myzel_repos_with_issues, issues, label, issues)
      else label,
    badgeCount = issues.takeIf { it > 0 },
    value = active?.let { pluralStringResource(R.plurals.myzel_repos_active, it, it) },
  ) {
    onNav(NavigationKey.Repos)
  }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface RepoIssuesEntryPoint {
  fun repoManager(): RepoManager

  fun repoUpdateManager(): RepoUpdateManager
}

/**
 * Stand der eingeschalteten Paketquellen für das Menü und die Anzeigen der Hauptseiten; [update]
 * ist die laufende Aktualisierung, sonst null. [sources] sind die eingeschalteten Paketquellen in
 * der Reihenfolge ihrer Liste, [names] die Namen aller für die Anzeigen.
 */
internal data class RepoState(
  val issues: Int,
  val active: Int,
  val lastUpdated: Long?,
  val update: RepoUpdate? = null,
  val sources: List<RepoSource> = emptyList(),
  val names: Map<Long, String> = emptyMap(),
)

/**
 * Eine eingeschaltete Paketquelle; [hasIssue], wenn sie sich zuletzt nicht laden ließ,
 * [needsLogin], wenn der Server dabei eine Anmeldung verlangt hat.
 */
internal data class RepoSource(
  val repoId: Long,
  val name: String,
  val hasIssue: Boolean,
  val needsLogin: Boolean = false,
)

/** Was bei einer Aktualisierung gerade geschieht; [UPDATES]: alle geladen, Myzel sucht Updates. */
internal enum class RepoPhase {
  CHECKING,
  LOADING,
  SAVING,
  UPDATES,
}

/**
 * Eine laufende Aktualisierung: Die Paketquelle [name] ist als [number]. von [total] dran, gerade
 * bei [phase] mit [percent] (null, wenn unbekannt); [progress] ist der Fortschritt aller zusammen
 * (0 bis 1).
 */
internal data class RepoUpdate(
  val name: String,
  val number: Int,
  val total: Int,
  val phase: RepoPhase,
  val percent: Int?,
  val progress: Float,
)

/** Die laufende Aktualisierung für die Anzeigen, null nach ihrem Ende. */
internal fun RepoUpdateRun.toUpdate(names: Map<Long, String>): RepoUpdate? {
  if (isFinished) return null
  val repoId = current ?: return RepoUpdate("", total, total, RepoPhase.UPDATES, null, 1f)
  val (phase, percent) =
    when (val step = step(repoId)) {
      is RepoUpdateStep.Loading -> RepoPhase.LOADING to step.percent
      is RepoUpdateStep.Saving -> RepoPhase.SAVING to step.percent
      // geladen, F-Droid prüft nur noch die Datei und speichert dann
      RepoUpdateStep.Loaded -> RepoPhase.SAVING to 0
      else -> RepoPhase.CHECKING to null
    }
  return RepoUpdate(names[repoId].orEmpty(), number, total, phase, percent, progress)
}

/** „Prüft F-Droid“, „Lädt F-Droid“, „Speichert F-Droid“ oder „Sucht Updates“. */
@Composable
internal fun RepoUpdate.displayText(): String =
  when {
    phase == RepoPhase.UPDATES -> stringResource(R.string.myzel_board_updates)
    name.isEmpty() -> stringResource(R.string.myzel_display_repos_loading)
    else ->
      stringResource(
        when (phase) {
          RepoPhase.CHECKING -> R.string.myzel_display_repo_checking
          RepoPhase.LOADING -> R.string.myzel_display_repo_loading
          else -> R.string.myzel_display_repo_saving
        },
        name,
      )
  }

/**
 * Wie viele Paketquellen eingeschaltet sind, wie viele davon sich zuletzt nicht aktualisieren
 * ließen (schon nach dem ersten Fehlschlag; F-Droid wartet für sein Abzeichen in Entdecken auf
 * fünf, DiscoverViewModel.hasRepoIssues, und eine Quelle, die eine Anmeldung verlangt, blieb so
 * tagelang unbemerkt), wann zuletzt eine geladen wurde und wie weit eine laufende Aktualisierung
 * ist.
 */
@Composable
internal fun rememberRepoState(): RepoState {
  if (LocalInspectionMode.current) return RepoState(0, 0, null)
  val context = LocalContext.current
  val entryPoint = remember {
    EntryPointAccessors.fromApplication(context, RepoIssuesEntryPoint::class.java)
  }
  val repos by entryPoint.repoManager().repositoriesState.collectAsState()
  val run by entryPoint.repoUpdateManager().updateRun.collectAsState()
  val enabled = repos.orEmpty().filter { it.enabled }
  val names = repos.orEmpty().associate { it.repoId to it.displayName() }
  val update = run?.toUpdate(names)
  return RepoState(
    issues = enabled.count { it.hasIssue() },
    active = enabled.size,
    lastUpdated = enabled.mapNotNull { it.lastUpdated }.maxOrNull(),
    update = update,
    sources =
      enabled.map { RepoSource(it.repoId, it.displayName(), it.hasIssue(), it.needsLogin()) },
    names = names,
  )
}

private fun Repository.hasIssue() = errorCount > 0

/**
 * Ob die Paketquelle beim letzten Laden eine Anmeldung verlangt hat (HTTP 401). Ktor schreibt „…
 * invalid: 401 Unauthorized. …“, über HTTP/2 fehlt der Zusatz und es steht nur „invalid: 401 .“ da.
 */
internal fun Repository.needsLogin() = lastError?.let { saysUnauthorized(it) } == true

/** Ob eine Fehlermeldung von Ktor eine Antwort 401 beschreibt, siehe [needsLogin]. */
internal fun saysUnauthorized(message: String) = UNAUTHORIZED.containsMatchIn(message)

private val UNAUTHORIZED = Regex("""invalid: 401\b|401 Unauthorized""", RegexOption.IGNORE_CASE)

/**
 * Der Name einer Paketquelle für die Anzeigen, ohne angehängtes „F-Droid Repo“ oder „Repository“:
 * Aus „IzzyOnDroid F-Droid Repo“ wird „IzzyOnDroid“.
 */
internal fun Repository.displayName(): String {
  val name = getName(LocaleListCompat.getDefault()) ?: return ""
  return name.replace(REPO_NAME_SUFFIX, "").ifBlank { name }
}

private val REPO_NAME_SUFFIX = Regex("""\s+(F-?Droid\s+)?Repo(sitory)?$""", RegexOption.IGNORE_CASE)

/**
 * Die kleine Anzeige während einer Aktualisierung oben im Menü: was die Paketquelle tut, die gerade
 * dran ist, und als wievielte von wie vielen, ohne Prozent, damit ihr Name Platz hat. Die großen
 * Anzeigen zeigen statt dessen die ganze Tafel ([RepoBoardSwitch]).
 */
@Composable
internal fun RepoUpdateDisplay(update: RepoUpdate, compact: Boolean = false) {
  DeviceDisplay(
    update.displayText(),
    StatusTone.ACCENT,
    detail = stringResource(R.string.myzel_display_repo_number, update.number, update.total),
    compact = compact,
    blinking = true,
  )
}

@Composable private fun rememberRepoIssues(): Int = rememberRepoState().issues

/** Wie viele Favoriten es gibt. */
@Composable
private fun rememberFavoriteCount(): Int {
  if (LocalInspectionMode.current) return 0
  val context = LocalContext.current
  val favoritesManager = remember {
    EntryPointAccessors.fromApplication(context, FavoritesEntryPoint::class.java).favoritesManager()
  }
  val favorites by favoritesManager.favorites.collectAsStateWithLifecycle()
  return favorites.size
}

private val MENU_LOGO_SIZE = 26.dp
private val NEEDANAPP_MENU_LOGO_SIZE = 34.dp
private val NEEDANAPP_MENU_LOGO_PRESSED = 30.dp

/** Was bei F-Droid im Menü oben rechts stand: Alle Apps und Über (je nach Variante mehr). */
@Composable
private fun MoreSheet(onDismiss: () -> Unit, onNav: (NavKey) -> Unit) {
  OptionsSheet(onDismiss = onDismiss) {
    getMoreMenuItems(LocalContext.current).forEach { dest ->
      SheetAction(
        icon = dest.icon,
        label = stringResource(dest.label),
        onClick = {
          onDismiss()
          onNav(dest.id)
        },
      )
    }
  }
}
