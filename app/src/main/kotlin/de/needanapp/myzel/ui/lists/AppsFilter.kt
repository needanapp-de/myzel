package de.needanapp.myzel.ui.lists

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CrisisAlert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Save
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ColorFilter.Companion.tint
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import de.needanapp.design.components.ContentRow
import de.needanapp.design.components.ListRow
import de.needanapp.design.components.SegmentedRow
import de.needanapp.design.components.SettingsGroup
import de.needanapp.design.components.SwitchRow
import de.needanapp.design.components.rememberSectionState
import org.fdroid.R
import org.fdroid.database.AppListSortOrder
import org.fdroid.ui.categories.CategoryChip
import org.fdroid.ui.categories.ChipFlowRow
import org.fdroid.ui.categories.chipHeight
import org.fdroid.ui.icons.PackageVariant
import org.fdroid.ui.lists.AppListInfo
import org.fdroid.ui.utils.AsyncShimmerImage

/**
 * Ersatz für org.fdroid.ui.lists.AppsFilter mit derselben Signatur, über einen ergänzten Import in
 * AppList.kt eingebunden (myzel/tools/apply-imports.py): aufgebaut wie die Einstellungen.
 * Sortierung als Umschalter, „Nur kompatible Apps“ als Schalter, Kategorien, Paketquellen und
 * unerwünschte Merkmale als aufklappbare Gruppen mit Chips (die Zahl im Titel zeigt gesetzte
 * Filter, Zuklappen behält sie), „Als Standard speichern“ und „Alle Filter löschen“ als Zeilen.
 *
 * Bei einem Update von F-Droid mit org.fdroid.ui.lists.AppsFilter vergleichen.
 */
@Composable
fun AppsFilter(info: AppListInfo, modifier: Modifier = Modifier) {
  val model = info.model
  val actions = info.actions
  val sections = rememberSectionState(listOf(CATEGORIES, REPOSITORIES, ANTI_FEATURES))
  // Gruppen mit gesetzten Filtern stehen beim Öffnen offen
  LaunchedEffect(Unit) {
    sections.expandAll(
      buildList {
        if (model.filteredCategoryIds.isNotEmpty()) add(CATEGORIES)
        if (model.filteredRepositoryIds.isNotEmpty()) add(REPOSITORIES)
        if (model.filteredAntiFeatureIds.isNotEmpty()) add(ANTI_FEATURES)
      }
    )
  }
  Column(modifier.verticalScroll(rememberScrollState()).navigationBarsPadding()) {
    SettingsGroup(title = stringResource(R.string.sort_title)) {
      row { position ->
        SegmentedRow(
          position,
          title = null,
          options = listOf(AppListSortOrder.NAME, AppListSortOrder.LAST_UPDATED),
          selected = model.sortBy,
          label = {
            stringResource(
              if (it == AppListSortOrder.NAME) R.string.myzel_sort_name
              else R.string.myzel_sort_updated
            )
          },
          onSelect = { actions.sortBy(it) },
        )
      }
      row { position ->
        SwitchRow(
          position,
          title = stringResource(R.string.filter_only_compatible),
          checked = model.filterIncompatible,
          onCheckedChange = { actions.toggleFilterIncompatible() },
        )
      }
    }
    val categories = model.categories
    if (!categories.isNullOrEmpty()) {
      SettingsGroup(
        title =
          withCount(
            stringResource(R.string.main_menu__categories),
            model.filteredCategoryIds.size,
          ),
        id = CATEGORIES,
        sectionState = sections,
        icon = Icons.Outlined.Category,
      ) {
        row { position ->
          ContentRow(position, title = null) {
            ChipFlowRow {
              categories.forEach { item ->
                val isSelected = item.id in model.filteredCategoryIds
                CategoryChip(
                  categoryItem = item,
                  selected = isSelected,
                  onSelected = {
                    if (isSelected) actions.removeCategory(item.id)
                    else actions.addCategory(item.id)
                  },
                )
              }
            }
          }
        }
      }
    }
    if (model.repositories.isNotEmpty()) {
      SettingsGroup(
        title =
          withCount(
            stringResource(R.string.app_details_repositories),
            model.filteredRepositoryIds.size,
          ),
        id = REPOSITORIES,
        sectionState = sections,
        icon = PackageVariant,
      ) {
        row { position ->
          ContentRow(position, title = null) {
            ChipFlowRow {
              model.repositories.forEach { repo ->
                val selected = repo.repoId in model.filteredRepositoryIds
                FilterChip(
                  selected = selected,
                  modifier = Modifier.height(chipHeight),
                  leadingIcon = {
                    if (selected) {
                      Icon(Icons.Default.Check, stringResource(R.string.filter_selected))
                    } else {
                      AsyncShimmerImage(
                        model = repo.icon,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp).semantics { hideFromAccessibility() },
                      )
                    }
                  },
                  label = { Text(repo.name) },
                  onClick = {
                    if (selected) actions.removeRepository(repo.repoId)
                    else actions.addRepository(repo.repoId)
                  },
                )
              }
            }
          }
        }
      }
    }
    val antiFeatures = model.antiFeatures
    if (!antiFeatures.isNullOrEmpty()) {
      SettingsGroup(
        title =
          withCount(
            stringResource(R.string.filter_antifeatures),
            model.filteredAntiFeatureIds.size,
          ),
        id = ANTI_FEATURES,
        sectionState = sections,
        icon = Icons.Outlined.WarningAmber,
      ) {
        row { position ->
          ContentRow(position, title = null) {
            ChipFlowRow {
              antiFeatures.forEach { item ->
                val isSelected = item.id in model.filteredAntiFeatureIds
                FilterChip(
                  selected = isSelected,
                  modifier = Modifier.height(chipHeight),
                  leadingIcon = {
                    if (isSelected) {
                      Icon(Icons.Default.Remove, stringResource(R.string.filter_selected))
                    } else {
                      AsyncShimmerImage(
                        model = item.iconModel,
                        colorFilter = tint(MaterialTheme.colorScheme.onSurfaceVariant),
                        error = rememberVectorPainter(Icons.Default.CrisisAlert),
                        contentDescription = null,
                        modifier = Modifier.size(24.dp).semantics { hideFromAccessibility() },
                      )
                    }
                  },
                  label = { Text(item.name) },
                  colors =
                    FilterChipDefaults.filterChipColors()
                      .copy(selectedContainerColor = MaterialTheme.colorScheme.errorContainer),
                  onClick = {
                    if (isSelected) actions.removeAntiFeature(item.id)
                    else actions.addAntiFeature(item.id)
                  },
                )
              }
            }
          }
        }
      }
    }
    val anyFilter =
      model.filteredCategoryIds.isNotEmpty() ||
        model.filteredRepositoryIds.isNotEmpty() ||
        model.filteredAntiFeatureIds.isNotEmpty()
    SettingsGroup(title = null) {
      row { position ->
        ListRow(
          position,
          title = stringResource(R.string.filter_button_save),
          icon = Icons.Outlined.Save,
          onClick = actions::saveFilters,
        )
      }
      row { position ->
        ListRow(
          position,
          title = stringResource(R.string.filter_button_clear_all),
          icon = Icons.Outlined.Clear,
          enabled = anyFilter,
          onClick = actions::clearFilters,
        )
      }
    }
  }
}

/** „Kategorien · 2“, wenn Filter gesetzt sind. */
private fun withCount(title: String, count: Int) = if (count > 0) "$title · $count" else title

private const val CATEGORIES = "filter-categories"
private const val REPOSITORIES = "filter-repositories"
private const val ANTI_FEATURES = "filter-anti-features"
