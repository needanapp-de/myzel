package de.needanapp.myzel.repo

/**
 * Wie weit eine laufende Aktualisierung der Paketquellen ist: Die Paketquelle [repoId] wird gerade
 * aktualisiert, als [index] (ab 0) von [total]. Den Fortschritt innerhalb dieser Paketquelle meldet
 * F-Droid über RepoUpdateManager.repoUpdateState. Die Anzeige oben in Entdecken baut daraus den
 * Gesamtfortschritt.
 */
data class RepoUpdateRun(val repoId: Long, val index: Int, val total: Int)
