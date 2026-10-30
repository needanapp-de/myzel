package de.needanapp.myzel.ui.history

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import de.needanapp.design.components.SettingsGroup
import de.needanapp.design.components.SwitchRow
import de.needanapp.design.components.TagPill
import de.needanapp.design.theme.LocalDesignColors
import de.needanapp.myzel.ui.apps.NameWithPills
import de.needanapp.myzel.ui.apps.RowIcon
import de.needanapp.myzel.ui.apps.selectedColors
import de.needanapp.myzel.ui.compat.ListItem
import org.fdroid.R
import org.fdroid.history.InstallEvent
import org.fdroid.ui.history.HistoryItem
import org.fdroid.ui.utils.VersionLine
import org.fdroid.ui.utils.asRelativeTimeString

/**
 * Ersatz für org.fdroid.ui.history.HistoryList mit derselben Signatur, über einen ergänzten Import
 * in History.kt eingebunden (myzel/tools/apply-imports.py): der Schalter als Zeile wie in den
 * Einstellungen, jedes Ereignis eine Karte mit Pille „Installiert“, „Update“ oder „Entfernt“ statt
 * des Zeichens am Symbol.
 *
 * Bei einem Update von F-Droid mit org.fdroid.ui.history.HistoryList vergleichen.
 */
@Composable
fun HistoryList(
  items: List<HistoryItem>,
  enabled: Boolean?,
  onEnabled: (Boolean) -> Unit,
  paddingValues: PaddingValues,
) {
  LazyColumn(contentPadding = paddingValues) {
    if (enabled != null)
      item {
        SettingsGroup(title = null) {
          row { position ->
            SwitchRow(
              position,
              title = stringResource(R.string.install_history_switch),
              checked = enabled,
              onCheckedChange = onEnabled,
            )
          }
        }
      }
    if (items.isEmpty())
      item {
        Text(
          text =
            stringResource(
              if (enabled == true) R.string.install_history_empty_state
              else R.string.install_history_disabled_state
            ),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          textAlign = TextAlign.Center,
          modifier = Modifier.padding(16.dp).fillMaxWidth(),
        )
      }
    else
      items(
        items = items,
        key = { "${it.event.time}-${it.event.packageName}" },
        contentType = { "item" },
      ) { item ->
        val event = item.event
        ListItem(
          leadingContent = { RowIcon(item.iconModel) },
          headlineContent = { NameWithPills(event.name ?: event.packageName) { EventPill(event) } },
          supportingContent = {
            val date = event.time.asRelativeTimeString()
            if (event is InstallEvent) VersionLine(event.oldVersionName, event.versionName, date)
            else Text(date)
          },
          colors = selectedColors(isSelected = false),
          modifier = if (enabled == false) Modifier.alpha(0.5f) else Modifier,
        )
      }
  }
}

/** „Update“ bei einer neuen Version, „Installiert“ beim ersten Mal, „Entfernt“ beim Entfernen. */
@Composable
private fun EventPill(event: org.fdroid.history.HistoryEvent) {
  val scheme = MaterialTheme.colorScheme
  val colors = LocalDesignColors.current
  val (text, container, content) =
    when {
      event !is InstallEvent -> Triple(R.string.myzel_tag_removed, scheme.error, scheme.onError)
      event.oldVersionName != null ->
        Triple(R.string.myzel_tag_update, colors.signal, colors.onSignal)
      else -> Triple(R.string.myzel_tag_installed, colors.positive, colors.onPositive)
    }
  TagPill(
    text = stringResource(text),
    containerColor = container,
    contentColor = content,
    compact = true,
  )
}
