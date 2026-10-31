package org.fdroid.ui.repositories

import org.fdroid.download.NetworkState

interface RepositoryInfo {
  val model: RepositoryModel
  val currentRepositoryId: Long?

  fun onOnboardingSeen()

  fun onRepositorySelected(repositoryItem: RepositoryItem)

  fun onRepositoryEnabled(repoId: Long, enabled: Boolean)

  fun onNotWarnWhenMetered()

  fun onAddRepo()

  fun onRepositoryMoved(fromRepoId: Long, toRepoId: Long)

  fun onRepositoriesFinishedMoving(fromRepoId: Long, toRepoId: Long)
}

data class RepositoryModel(
  val repositories: List<RepositoryItem>?,
  val showOnboarding: Boolean,
  // Myzel: der Zeitpunkt, 0 oder kleiner: noch nie; die Oberfläche formatiert ihn in ihrer Sprache
  val lastCheckForUpdate: Long,
  val networkState: NetworkState,
)
