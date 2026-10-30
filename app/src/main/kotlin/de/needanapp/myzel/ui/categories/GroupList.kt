package de.needanapp.myzel.ui.categories

/**
 * Alle Apps einer Kategoriengruppe als eine Liste (org.fdroid.ui.lists.AppListType.Category mit
 * dieser Kennung): F-Droid kennt nur Listen je Kategorie, AppListViewModel fragt die Kategorien
 * einzeln ab ([groupCategoryIds]) und führt sie zusammen.
 */
fun groupCategoryId(categoryIds: List<String>): String =
  GROUP_PREFIX + categoryIds.joinToString(GROUP_SEPARATOR)

/** Die Kategorien einer Kennung von [groupCategoryId], sonst null. */
fun groupCategoryIds(categoryId: String): List<String>? =
  if (categoryId.startsWith(GROUP_PREFIX)) {
    categoryId.removePrefix(GROUP_PREFIX).split(GROUP_SEPARATOR).filter { it.isNotEmpty() }
  } else null

private const val GROUP_PREFIX = "myzel-group:"
private const val GROUP_SEPARATOR = ","
