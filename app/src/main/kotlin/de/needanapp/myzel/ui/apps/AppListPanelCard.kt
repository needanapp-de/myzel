package de.needanapp.myzel.ui.apps

import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.fdroid.ui.apps.MyAppItem
import org.fdroid.ui.lists.AppListItem

/**
 * Eine App aus der Datenbank als Karte wie in „Meine Apps“: Symbol, Name mit den Pillen der
 * App-Listen („Update“, „Installiert“, „Neu“) und die Kurzbeschreibung; installierte Apps ohne
 * Update sind ausgegraut. Langes Drücken ([clickModifier]) klappt darunter das Bedienfeld auf, mit
 * [installed] wie in „Meine Apps“, ohne steht vorn „Installieren“. Mit [updatedAt] kommt die Pille
 * mit dem Alter des letzten Updates dazu. Für die Favoriten und „Neu und aktualisiert“.
 */
@Composable
internal fun AppListPanelCard(
  app: AppListItem,
  installed: MyAppItem?,
  isSelected: Boolean,
  open: Boolean,
  panelHost: AppPanelHost,
  modifier: Modifier,
  clickModifier: Modifier,
  updatedAt: Long? = null,
) {
  val badges = rememberAppBadgeState(app.packageName, app.isInstalled, updatedAt)
  PanelCard(
    isSelected = isSelected,
    open = open,
    coveredBottom = panelHost.coveredBottom,
    modifier = modifier,
    clickModifier = clickModifier,
    leadingContent = { Box(Modifier.fadedIcon(badges.isFaded)) { RowIcon(app.iconModel) } },
    headlineContent = {
      // reicht der Platz neben dem Namen nicht, rutschen die Pillen in die nächste Zeile
      FlowRow(
        horizontalArrangement = spacedBy(6.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
      ) {
        Text(app.name, Modifier.faded(badges.isFaded))
        AppBadgePills(badges)
      }
    },
    supportingContent = { Text(app.summary, Modifier.faded(badges.isFaded)) },
  ) {
    AppPanel(app.packageName, app.name, installed, panelHost)
  }
}
