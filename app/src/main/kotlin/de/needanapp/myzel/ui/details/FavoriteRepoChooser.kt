package de.needanapp.myzel.ui.details

import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dagger.hilt.android.EntryPointAccessors
import de.needanapp.design.theme.LocalDesignEffects
import de.needanapp.myzel.favorites.FAVORITE_OFF_ALPHA
import de.needanapp.myzel.favorites.FavoritesEntryPoint
import de.needanapp.myzel.ui.MyzelLogo
import de.needanapp.myzel.ui.MyzelLogoColor
import de.needanapp.myzel.ui.MyzelSignalArrivalMillis
import de.needanapp.myzel.ui.MyzelSignalStyle
import de.needanapp.myzel.ui.apps.LocalStampPackageName
import io.ktor.client.engine.ProxyConfig
import kotlinx.coroutines.delay
import org.fdroid.R
import org.fdroid.database.Repository
import org.fdroid.ui.details.LoadedAppDetailsItem
import org.fdroid.ui.navigation.NavigationKey

// Myzel: in den App-Details steht neben der Paketquelle ein Knopf mit dem Myzel-Logo, der die App
// zu den Favoriten hinzufügt. F-Droid baut den Kasten der Paketquelle (RepoChooser) im Kopf der
// Details (AppDetailsHeader), der den Paketnamen nicht weiterreicht. Beide werden deshalb über
// ergänzte Importe ersetzt (myzel/tools/apply-imports.py): AppDetailsHeader stellt den Paketnamen
// bereit, RepoChooser stellt den Kasten von F-Droid auf zwei Drittel und den Knopf daneben.

/** Paketname der App, deren Details gerade gezeigt werden. */
private val LocalDetailsPackageName = staticCompositionLocalOf<String?> { null }

/**
 * Ersatz für org.fdroid.ui.details.AppDetailsHeader in AppDetails.kt mit derselben Signatur: der
 * Kopf von F-Droid, nur mit dem Paketnamen für den Favoriten-Knopf und den Stempel „Neu“.
 */
@Composable
fun AppDetailsHeader(
  item: LoadedAppDetailsItem,
  onNav: (NavigationKey) -> Unit,
  innerPadding: PaddingValues,
  now: Long = System.currentTimeMillis(),
) {
  CompositionLocalProvider(
    LocalDetailsPackageName provides item.app.packageName,
    LocalStampPackageName provides item.app.packageName,
  ) {
    org.fdroid.ui.details.AppDetailsHeader(item, onNav, innerPadding, now)
  }
}

/**
 * Ersatz für org.fdroid.ui.details.RepoChooser in AppDetailsHeader.kt mit derselben Signatur: der
 * Kasten von F-Droid auf zwei Dritteln der Breite, daneben der Favoriten-Knopf.
 */
@Composable
fun RepoChooser(
  repos: List<Repository>,
  currentRepoId: Long,
  preferredRepoId: Long,
  proxy: ProxyConfig?,
  onRepoChanged: (Long) -> Unit,
  onPreferredRepoChanged: (Long) -> Unit,
  modifier: Modifier = Modifier,
) {
  val packageName = LocalDetailsPackageName.current
  // Vorschauen und Screenshot-Tests von F-Droid laufen ohne Hilt
  if (packageName == null || LocalInspectionMode.current) {
    org.fdroid.ui.details.RepoChooser(
      repos,
      currentRepoId,
      preferredRepoId,
      proxy,
      onRepoChanged,
      onPreferredRepoChanged,
      modifier,
    )
    return
  }
  Row(horizontalArrangement = spacedBy(8.dp), modifier = modifier.fillMaxWidth()) {
    // eigene Box: F-Droid gibt den Modifier auch an die Einträge des Auswahlmenüs weiter, ein
    // weight darf dort nicht landen
    Box(Modifier.weight(2f)) {
      org.fdroid.ui.details.RepoChooser(
        repos,
        currentRepoId,
        preferredRepoId,
        proxy,
        onRepoChanged,
        onPreferredRepoChanged,
      )
    }
    FavoriteButton(
      packageName = packageName,
      // mittig neben dem Feld der Paketquelle; dessen Beschriftung sitzt 8 dp darüber im Rahmen
      modifier = Modifier.weight(1f).padding(top = 8.dp).height(56.dp),
    )
  }
}

/**
 * Das Myzel-Logo als Schalter, ohne Rahmen: in seiner eigenen Farbe, wenn die App ein Favorit ist,
 * sonst ausgegraut wie ein abgeschalteter Knopf. Wird sie zum Favoriten, läuft ein Signal durch das
 * graue Myzel, und es leuchtet in seiner Farbe auf, sobald das Signal die Spitzen erreicht. Nach
 * dem Tippen sagt eine kurze Meldung, was passiert ist. [buttonSize] und [iconSize] passen ihn an
 * den Platz an.
 */
@Composable
internal fun FavoriteButton(
  packageName: String,
  modifier: Modifier = Modifier,
  buttonSize: Dp = 56.dp,
  iconSize: Dp = 32.dp,
) {
  val context = LocalContext.current
  val favoritesManager =
    remember(context) {
      EntryPointAccessors.fromApplication(context, FavoritesEntryPoint::class.java)
        .favoritesManager()
    }
  val favorites by favoritesManager.favorites.collectAsStateWithLifecycle()
  val isFavorite = packageName in favorites
  var signal by remember { mutableIntStateOf(0) }
  var lit by remember { mutableStateOf(isFavorite) }
  val richMotion = LocalDesignEffects.current.richMotion
  LaunchedEffect(isFavorite) {
    // nach dem Tippen erst leuchten, wenn das Signal angekommen ist
    if (isFavorite && !lit && signal > 0 && richMotion) delay(MyzelSignalArrivalMillis - 150)
    lit = isFavorite
  }
  val off = MaterialTheme.colorScheme.onSurface.copy(alpha = FAVORITE_OFF_ALPHA)
  val color by animateColorAsState(if (lit) MyzelLogoColor else off, tween(250), label = "favorite")
  // die letzte Meldung, damit schnelles Tippen keine Meldungen stapelt
  var lastToast by remember { mutableStateOf<Toast?>(null) }
  val label = stringResource(R.string.myzel_favorite)
  Box(contentAlignment = Alignment.Center, modifier = modifier) {
    IconToggleButton(
      checked = isFavorite,
      onCheckedChange = {
        val nowFavorite = favoritesManager.toggle(packageName)
        if (nowFavorite) signal++
        val message =
          if (nowFavorite) R.string.myzel_favorite_added else R.string.myzel_favorite_removed
        lastToast?.cancel()
        lastToast = Toast.makeText(context, message, LENGTH_SHORT).also { it.show() }
      },
      modifier = Modifier.size(buttonSize).semantics { contentDescription = label },
    ) {
      MyzelLogo(
        Modifier.size(iconSize),
        color = color,
        signal = signal,
        style = MyzelSignalStyle.FAVORITE,
      )
    }
  }
}
