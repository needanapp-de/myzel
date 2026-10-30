package de.needanapp.myzel.favorites

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/** Ziel der Navigation: die Liste der Favoriten, erreichbar über die Menüpille. */
@Serializable data object Favorites : NavKey
