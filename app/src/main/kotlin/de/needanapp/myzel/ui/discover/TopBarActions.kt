package de.needanapp.myzel.ui.discover

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.runtime.Composable
import org.fdroid.ui.navigation.NavDestinations

// Bei Myzel stehen Paketquellen, Einstellungen und das Mehr-Menü in der Werkzeugpille unten
// (de.needanapp.myzel.ui.nav.BottomBar). Discover.kt bindet diese leeren Gegenstücke über den
// Import ein, damit die Kopfzeile von Entdecken ohne Knöpfe bleibt.

/** Ersatz für org.fdroid.ui.navigation.topBarMenuItems in Discover.kt: keine Knöpfe oben. */
val topBarMenuItems: List<NavDestinations> = emptyList()

/** Ersatz für org.fdroid.ui.utils.TopAppBarOverflowButton in Discover.kt: kein Menü oben. */
@Suppress("UNUSED_PARAMETER")
@Composable
fun TopAppBarOverflowButton(
  menuItems: @Composable (ColumnScope.(onDismissRequest: () -> Unit) -> Unit)
) {}
