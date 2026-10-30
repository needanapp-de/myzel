package de.needanapp.myzel.recent

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Ziel der Navigation: alle neuen und kürzlich aktualisierten Apps, mit [repoId] zu Beginn nur die
 * aus dieser Paketquelle. Erreichbar über „Alle anzeigen“ unter dem Karussell und die Anzeige auf
 * Entdecken.
 */
@Serializable data class RecentApps(val repoId: Long? = null) : NavKey
