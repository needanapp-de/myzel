package de.needanapp.myzel.ui.compat

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import de.needanapp.myzel.ui.apps.AppBadgeRow
import de.needanapp.myzel.ui.apps.AppBadgeState
import de.needanapp.myzel.ui.apps.LocalStampPackageName
import de.needanapp.myzel.ui.apps.rememberIsNew

/**
 * Ersatz für androidx.compose.material3.BadgedBox um das App-Symbol im Detailkopf von F-Droid
 * (ui/details/AppDetailsHeader.kt, über den Import getauscht). [badge] liegt in der Größe des
 * Symbols darüber, dazu oben links die Pille „Neu“ für die App aus [LocalStampPackageName].
 */
@Composable
fun BadgedBox(
  badge: @Composable BoxScope.() -> Unit,
  modifier: Modifier = Modifier,
  content: @Composable BoxScope.() -> Unit,
) {
  Box(modifier) {
    content()
    Box(Modifier.matchParentSize()) { badge() }
    if (rememberIsNew(LocalStampPackageName.current)) {
      AppBadgeRow(
        AppBadgeState(isInstalled = false, hasUpdate = false, isNew = true),
        Modifier.align(Alignment.TopStart).offset(x = (-6).dp, y = (-6).dp),
      )
    }
  }
}

/**
 * Ersatz für org.fdroid.ui.utils.InstalledBadge im Detailkopf: die Pille „Installiert“ unten am
 * Symbol statt des Häkchens. Gehört in [BadgedBox] von Myzel, das ihm die Größe des Symbols gibt.
 */
@Composable
fun BoxScope.InstalledBadge() {
  AppBadgeRow(
    AppBadgeState(isInstalled = true, hasUpdate = false, isNew = false),
    Modifier.align(Alignment.BottomCenter).offset(y = 6.dp),
  )
}
