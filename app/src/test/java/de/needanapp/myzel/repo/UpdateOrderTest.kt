package de.needanapp.myzel.repo

import kotlin.test.assertEquals
import org.junit.Test

internal class UpdateOrderTest {

  @Test
  fun `f-droid comes last, the others keep their order`() {
    val repos =
      listOf(
        "https://f-droid.org/repo",
        "https://fdroid.needanapp.de/repo",
        "https://apt.izzysoft.de/fdroid/repo",
      )
    assertEquals(
      listOf(
        "https://fdroid.needanapp.de/repo",
        "https://apt.izzysoft.de/fdroid/repo",
        "https://f-droid.org/repo",
      ),
      repos.largeReposLast { it },
    )
  }

  @Test
  fun `a trailing slash does not matter`() {
    val repos = listOf("https://f-droid.org/repo/", "https://fdroid.needanapp.de/repo")
    assertEquals(
      listOf("https://fdroid.needanapp.de/repo", "https://f-droid.org/repo/"),
      repos.largeReposLast { it },
    )
  }
}
