package org.fdroid.ui.repositories

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import kotlinx.coroutines.flow.StateFlow
import org.fdroid.download.NetworkState

@Composable
fun RepositoriesPresenter(
  repositoriesFlow: StateFlow<List<RepositoryItem>?>,
  repoSortingMapFlow: StateFlow<Map<Long, Int>>,
  showOnboardingFlow: StateFlow<Boolean>,
  lastUpdateFlow: StateFlow<Long>,
  networkStateFlow: StateFlow<NetworkState>,
): RepositoryModel {
  val repositories = repositoriesFlow.collectAsState().value
  val repoSortingMap = repoSortingMapFlow.collectAsState().value
  val lastUpdated = lastUpdateFlow.collectAsState().value
  return RepositoryModel(
    repositories =
      repositories?.sortedBy { repo -> repoSortingMap[repo.repoId] ?: repoSortingMap.size },
    showOnboarding = showOnboardingFlow.collectAsState().value,
    lastCheckForUpdate = lastUpdated,
    networkState = networkStateFlow.collectAsState().value,
  )
}
