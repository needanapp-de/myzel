package de.needanapp.myzel.ui.repositories

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement.spacedBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import de.needanapp.design.components.DeviceDisplay
import de.needanapp.design.components.StatusTone
import de.needanapp.design.components.designCard
import de.needanapp.design.theme.LocalDesignShapes
import de.needanapp.design.theme.emphasizedSpec
import de.needanapp.design.theme.isNeedanappDesign
import de.needanapp.myzel.ui.compat.DisableRepoDialog
import de.needanapp.myzel.ui.compat.MeteredConnectionDialog
import de.needanapp.myzel.ui.discover.RepoRefreshBox
import de.needanapp.myzel.ui.nav.RepoBoardSwitch
import de.needanapp.myzel.ui.nav.rememberRepoState
import de.needanapp.myzel.ui.nav.repoTimeText
import org.fdroid.R
import org.fdroid.ui.icons.PackageVariant
import org.fdroid.ui.repositories.RepositoryInfo
import org.fdroid.ui.repositories.RepositoryItem
import org.fdroid.ui.repositories.RepositoryRow

private const val MORE_HEADER_KEY = "myzel_more_repositories"

/**
 * Ersatz für org.fdroid.ui.repositories.RepositoriesList mit derselben Signatur; Repositories.kt
 * bindet ihn über einen ergänzten Import ein (myzel/tools/apply-imports.py). Die eingeschalteten
 * Paketquellen stehen oben und lassen sich wie bei F-Droid durch langes Drücken und Ziehen
 * sortieren. Die ausgeschalteten folgen darunter in einer aufklappbaren Karte „Weitere
 * Paketquellen“. Beide Teile behalten die Reihenfolge (Priorität) von F-Droid.
 *
 * Kopie von F-Droid samt Dialogen: bei einem Update von F-Droid mit
 * org.fdroid.ui.repositories.RepositoriesList vergleichen.
 */
@Composable
fun RepositoriesList(
  info: RepositoryInfo,
  listState: LazyListState,
  paddingValues: PaddingValues,
  modifier: Modifier = Modifier,
) {
  val repositories = info.model.repositories ?: return
  val (enabled, disabled) = repositories.partition { it.enabled }
  val enabledIds = enabled.map { it.repoId }.toSet()
  var showMore by rememberSaveable { mutableStateOf(false) }
  var showDisableRepoDialog by remember { mutableStateOf<Long?>(null) }
  var showMeteredDialog by remember { mutableStateOf<(() -> Unit)?>(null) }
  val currentRepositoryId = info.currentRepositoryId
  val shapes = LocalDesignShapes.current
  val dragDropState =
    rememberDragDropState(
      lazyListState = listState,
      // nur die eingeschalteten lassen sich ziehen und tauschen
      canMove = { key -> key in enabledIds },
      onMove = { from, to ->
        from as? Long ?: error("from $from was not a repoId")
        to as? Long ?: error("to $to was not a repoId")
        info.onRepositoryMoved(from, to)
      },
      onEnd = { from, to ->
        from as? Long ?: error("from $from was not a repoId")
        to as? Long ?: error("to $to was not a repoId")
        info.onRepositoriesFinishedMoving(from, to)
      },
    )

  @Composable
  fun RepoRow(repoItem: RepositoryItem, rowModifier: Modifier = Modifier) {
    val isSelected = currentRepositoryId == repoItem.repoId
    val interactionModifier =
      if (currentRepositoryId == null) {
        Modifier.clickable(onClick = { info.onRepositorySelected(repoItem) })
      } else {
        Modifier.selectable(
          selected = isSelected,
          onClick = { info.onRepositorySelected(repoItem) },
        )
      }
    RepositoryRow(
      repoItem = repoItem,
      isSelected = isSelected,
      onRepoEnabled = { enable ->
        if (enable) {
          if (info.model.networkState.showWarningDialog)
            showMeteredDialog = { info.onRepositoryEnabled(repoItem.repoId, true) }
          else info.onRepositoryEnabled(repoItem.repoId, true)
        } else {
          showDisableRepoDialog = repoItem.repoId
        }
      },
      modifier = rowModifier.fillMaxWidth().then(interactionModifier),
    )
  }

  // Ziehen nach unten aktualisiert alle, wie in Entdecken; die Liste beginnt schon unter der
  // Kopfleiste
  RepoRefreshBox(modifier.fillMaxSize(), indicatorTop = 0.dp) {
    LazyColumn(
      state = listState,
      contentPadding = paddingValues + PaddingValues(top = 8.dp),
      verticalArrangement = spacedBy(8.dp),
      modifier =
        Modifier.fillMaxSize()
          .then(if (enabled.size > 1) Modifier.dragContainer(dragDropState) else Modifier)
          .then(if (currentRepositoryId == null) Modifier else Modifier.selectableGroup()),
    ) {
      item(key = STATUS_KEY, contentType = "status") {
        RepoStatusDisplay(Modifier.padding(horizontal = 16.dp))
      }
      // die eingeschalteten stehen gleich nach der Anzeige, ihr Index in der Liste ist eins größer
      itemsIndexed(items = enabled, key = { _, item -> item.repoId }) { index, repoItem ->
        DraggableItem(dragDropState, index + 1) { isDragging ->
          val elevation by animateDpAsState(if (isDragging) 4.dp else 0.dp)
          Box {
            // Schatten in der Form der Karte dahinter; F-Droids Schatten am Zeilen-Modifier läge
            // eckig über dem Hintergrund der Karte (de.needanapp.myzel.ui.compat.ListItem)
            if (elevation > 0.dp)
              Box(
                Modifier.matchParentSize()
                  .padding(horizontal = 16.dp, vertical = shapes.rowGap / 2)
                  .dropShadow(
                    shape = RoundedCornerShape((shapes.group + shapes.inner) / 2),
                    shadow = Shadow(radius = 8.dp, offset = DpOffset(x = elevation, elevation)),
                  )
              )
            RepoRow(repoItem)
          }
        }
      }
      if (disabled.isNotEmpty()) {
        item(key = MORE_HEADER_KEY) {
          MoreRepositoriesHeader(
            count = disabled.size,
            expanded = showMore,
            onToggle = { showMore = !showMore },
            modifier = Modifier.animateItem(),
          )
        }
        if (showMore) {
          items(items = disabled, key = { it.repoId }) { repoItem ->
            RepoRow(repoItem, rowModifier = Modifier.animateItem())
          }
        }
      }
    }
  }
  val repoId = showDisableRepoDialog
  if (repoId != null) {
    DisableRepoDialog(
      onDisable = { info.onRepositoryEnabled(repoId, false) },
      onDismiss = { showDisableRepoDialog = null },
    )
  }
  // Metered warning dialog
  val meteredLambda = showMeteredDialog
  if (meteredLambda != null)
    MeteredConnectionDialog(
      numBytes = null,
      onConfirm = { notWarnWhenMetered ->
        if (notWarnWhenMetered) info.onNotWarnWhenMetered()
        meteredLambda()
      },
      onDismiss = { showMeteredDialog = null },
    )
}

/**
 * Die Anzeige über den Paketquellen: wie viele eingeschaltet sind und wann sie zuletzt geladen
 * wurden; lassen sich welche wiederholt nicht laden, sagt sie das. Während einer Aktualisierung
 * fährt sie aus wie die in Entdecken ([RepoBoardSwitch]).
 */
@Composable
private fun RepoStatusDisplay(modifier: Modifier) {
  val repos = rememberRepoState()
  Box(modifier.animateContentSize(emphasizedSpec())) {
    RepoBoardSwitch {
      if (repos.issues > 0) {
        DeviceDisplay(
          pluralStringResource(R.plurals.myzel_display_repos_issues, repos.issues, repos.issues),
          StatusTone.ERROR,
        )
      } else {
        DeviceDisplay(
          pluralStringResource(R.plurals.myzel_repos_active, repos.active, repos.active),
          StatusTone.POSITIVE,
          detail = repos.lastUpdated?.let { repoTimeText(it) },
        )
      }
    }
  }
}

private const val STATUS_KEY = "myzel_repo_status"

/**
 * Kopf der ausgeschalteten Paketquellen, wie die aufklappbaren Gruppen des Design-Pakets: Symbol,
 * Titel mit Anzahl und ein Pfeil, der sich beim Aufklappen nach unten dreht. Die Form passt zu den
 * Zeilen der Liste (de.needanapp.myzel.ui.compat.ListItem), im Design needanapp also eine Karte.
 */
@Composable
private fun MoreRepositoriesHeader(
  count: Int,
  expanded: Boolean,
  onToggle: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val shapes = LocalDesignShapes.current
  val progress by animateFloatAsState(if (expanded) 1f else 0f, emphasizedSpec(), label = "more")
  val arrowDirection = if (LocalLayoutDirection.current == LayoutDirection.Rtl) 1f else -1f
  val actionLabel = stringResource(if (expanded) R.string.collapse else R.string.expand)
  val needanapp = isNeedanappDesign
  Surface(
    shape = RoundedCornerShape(if (needanapp) shapes.group else (shapes.group + shapes.inner) / 2),
    color = if (needanapp) Color.Transparent else MaterialTheme.colorScheme.surfaceContainer,
    modifier =
      if (needanapp)
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).designCard()
      else modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = shapes.rowGap / 2),
  ) {
    Row(
      verticalAlignment = Alignment.CenterVertically,
      modifier =
        Modifier.clickable(onClickLabel = actionLabel, onClick = onToggle)
          .heightIn(min = 56.dp)
          .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
      Icon(
        PackageVariant,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
        modifier = Modifier.size(24.dp),
      )
      Spacer(Modifier.width(16.dp))
      Column(Modifier.weight(1f)) {
        Text(
          stringResource(R.string.myzel_repos_more),
          style = MaterialTheme.typography.bodyLarge,
          modifier = Modifier.semantics { heading() },
        )
        Text(
          stringResource(R.string.myzel_repos_more_summary, count),
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Spacer(Modifier.width(12.dp))
      Icon(
        Icons.Filled.KeyboardArrowDown,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.rotate(arrowDirection * 90f * (1f - progress)),
      )
    }
  }
}
