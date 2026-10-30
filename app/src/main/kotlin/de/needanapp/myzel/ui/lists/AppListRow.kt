package de.needanapp.myzel.ui.lists

import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.needanapp.myzel.ui.apps.AppBadgePills
import de.needanapp.myzel.ui.apps.faded
import de.needanapp.myzel.ui.apps.fadedIcon
import de.needanapp.myzel.ui.apps.rememberAppBadgeState
import de.needanapp.myzel.ui.compat.ListItem
import org.fdroid.R
import org.fdroid.ui.lists.AppListItem
import org.fdroid.ui.utils.AsyncShimmerImage

/**
 * Ersatz für org.fdroid.ui.lists.AppListRow mit derselben Signatur, über ergänzte bzw. getauschte
 * Importe eingebunden (myzel/tools/apply-imports.py): in AppList.kt, SearchResults.kt und
 * AddRepoPreviewScreen.kt. Wie bei F-Droid, nur statt des Häkchens die Pillen von Myzel neben dem
 * Namen („Update“, „Installiert“, „Neu“); installierte Apps ohne Update sind ausgegraut.
 *
 * Bei einem Update von F-Droid mit org.fdroid.ui.lists.AppListRow vergleichen.
 */
@Composable
fun AppListRow(item: AppListItem, isSelected: Boolean, modifier: Modifier = Modifier) {
  val badges = rememberAppBadgeState(item.packageName, item.isInstalled)
  ListItem(
    headlineContent = {
      // reicht der Platz neben dem Namen nicht, rutschen die Pillen in die nächste Zeile
      FlowRow(
        horizontalArrangement = spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
      ) {
        Text(item.name, Modifier.faded(badges.isFaded))
        AppBadgePills(badges)
      }
    },
    supportingContent = { Text(item.summary, Modifier.faded(badges.isFaded)) },
    leadingContent = {
      AsyncShimmerImage(
        model = item.iconModel,
        error = painterResource(R.drawable.ic_repo_app_default),
        contentDescription = null,
        modifier =
          Modifier.size(48.dp)
            .clip(MaterialTheme.shapes.medium)
            .fadedIcon(badges.isFaded)
            .semantics { hideFromAccessibility() },
      )
    },
    colors =
      ListItemDefaults.colors(
        containerColor =
          if (isSelected) {
            MaterialTheme.colorScheme.surfaceVariant
          } else {
            Color.Transparent
          }
      ),
    modifier = modifier,
  )
}
