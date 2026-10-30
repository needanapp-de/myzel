package de.needanapp.myzel.watch

import kotlin.test.assertEquals
import kotlin.test.assertNull
import org.fdroid.database.WATCH_FEATURE
import org.fdroid.index.v2.FeatureV2
import org.fdroid.index.v2.FileV1
import org.fdroid.index.v2.ManifestV2
import org.fdroid.index.v2.PackageVersionV2
import org.fdroid.index.v2.SignerV2
import org.fdroid.index.v2.UsesSdkV2
import org.junit.Test

internal class MyzelWearSourceTest {
  private val own = "aa".repeat(32)
  private val other = "bb".repeat(32)

  private fun version(code: Long, watch: Boolean, signer: String = own, minSdk: Int = 30) =
    PackageVersionV2(
      added = 0,
      file = FileV1("/de.needanapp.myzel_$code.apk", "$code".padStart(64, '0')),
      manifest =
        ManifestV2(
          versionName = "0.$code",
          versionCode = code,
          usesSdk = UsesSdkV2(minSdk, 37),
          signer = SignerV2(listOf(signer)),
          features = if (watch) listOf(FeatureV2(WATCH_FEATURE)) else emptyList(),
        ),
    )

  @Test
  fun `newest watch version signed like Myzel`() {
    val versions =
      listOf(
        version(1023, watch = false),
        version(23, watch = true),
        version(22, watch = true),
        // höher, aber von jemand anderem signiert
        version(24, watch = true, signer = other),
      )
    assertEquals(23, versions.newestWear(setOf(own), sdk = 33)?.versionCode)
  }

  @Test
  fun `phone versions never count`() {
    assertNull(listOf(version(1023, watch = false)).newestWear(setOf(own), sdk = 33))
  }

  @Test
  fun `versions the watch is too old for are skipped`() {
    val versions = listOf(version(24, watch = true, minSdk = 34), version(23, true))
    assertEquals(23, versions.newestWear(setOf(own), sdk = 33)?.versionCode)
    // unbekannte API-Stufe: die neueste
    assertEquals(24, versions.newestWear(setOf(own), sdk = 0)?.versionCode)
  }
}
