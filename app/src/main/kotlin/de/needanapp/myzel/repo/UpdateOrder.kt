package de.needanapp.myzel.repo

import org.fdroid.database.Repository

/**
 * Paketquellen mit sehr großem Index. F-Droid selbst lädt beim ersten Start über 60 MB und braucht
 * damit rund eine Minute; kommen die kleinen Quellen davor, zeigt Entdecken schon nach wenigen
 * Sekunden Apps.
 */
private val LARGE_REPOS = setOf("https://f-droid.org/repo", "https://f-droid.org/archive")

/**
 * Reihenfolge, in der RepoUpdateManager.updateRepos die Paketquellen aktualisiert: die großen
 * zuletzt, sonst wie bei F-Droid nach Priorität. Die Priorität der Quellen (Gewicht) bleibt dabei
 * unverändert.
 */
fun inUpdateOrder(repositories: List<Repository>): List<Repository> = repositories.largeReposLast {
  it.address
}

internal fun <T> List<T>.largeReposLast(address: (T) -> String): List<T> = sortedBy {
  address(it).trimEnd('/') in LARGE_REPOS
}
