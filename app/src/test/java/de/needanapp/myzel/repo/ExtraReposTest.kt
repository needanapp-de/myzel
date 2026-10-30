package de.needanapp.myzel.repo

import android.content.Context
import android.content.res.AssetManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.io.File
import java.security.MessageDigest
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.serialization.json.Json
import org.fdroid.database.FDroidDatabase
import org.fdroid.database.InitialRepository
import org.fdroid.database.Repository
import org.fdroid.database.RepositoryDao
import org.fdroid.repo.DefaultRepository
import org.fdroid.repo.RepoPreLoader
import org.junit.Test

internal class ExtraReposTest {

  private val assets = File("src/main/assets")
  private val extraRepos: List<DefaultRepository> =
    Json.decodeFromString(File(assets, EXTRA_REPOS_ASSET).readText())
  private val defaultRepos: List<DefaultRepository> =
    Json.decodeFromString(File(assets, "default_repos.json").readText())

  /** Das Repo von needanapp.de, an erster Stelle, danach das Testing-Repo. */
  private val ownRepo = NEEDANAPP_REPO

  private val izzyRepo = "https://apt.izzysoft.de/fdroid/repo"

  /** Neben F-Droid die einzigen eingeschalteten Repos. */
  private val enabledRepos = listOf(ownRepo, izzyRepo)

  /**
   * SHA-256-Fingerabdrücke: die Repos von needanapp.de und die Droid-ify-Liste des Nutzers (Stand
   * Droid-ify v0.7.8).
   */
  private val expectedFingerprints =
    mapOf(
      ownRepo to "35453386642f44485e4d556e1f631a1f2ad22ca3b02fb2c72b0a9807e5b30488",
      NEEDANAPP_TESTING_REPO to "231df03de2428b3a82e67b6cbf0d0cb0bb6e9261ea45392128707d2f39661844",
      "https://guardianproject.info/fdroid/repo" to
        "b7c2eefd8dac7806af67dfcd92eb18126bc08312a7f2d6f3862e46013c7a6135",
      "https://guardianproject.info/fdroid/archive" to
        "b7c2eefd8dac7806af67dfcd92eb18126bc08312a7f2d6f3862e46013c7a6135",
      "https://apt.izzysoft.de/fdroid/repo" to
        "3bf0d6abfeae2f401707b6d966be743bf0eee49c2561b9ba39073711f628937a",
      "https://microg.org/fdroid/repo" to
        "9bd06727e62796c0130eb6dab39b73157451582cbd138e86c468acc395d14165",
      "https://repo.netsyms.com/fdroid/repo" to
        "2581ba7b32d3ab443180c4087cab6a7e8fb258d3a6e98870ecb3c675e4d64489",
      "https://molly.im/fdroid/foss/fdroid/repo" to
        "5198daef37fc23c14d5ee32305b2af45787bd7df2034de33ad302bdb3446df74",
      "https://archive.newpipe.net/fdroid/repo" to
        "e2402c78f9b97c6c89e97db914a2751fda1d02fe2039cc0897a462bdb57e7501",
      "https://www.collaboraoffice.com/downloads/fdroid/repo" to
        "573258c84e149b5f4d9299e7434b2b69a8410372921d4ae586ba91ec767892cc",
      "https://cdn.kde.org/android/fdroid/repo" to
        "b3ebe10afa6c5c400379b34473e843d686c61ae6ad33f423c98af903f056523f",
      "https://calyxos.gitlab.io/calyx-fdroid-repo/fdroid/repo" to
        "c44d58b4547de5096138cb0b34a1cc99dab3b4274412ed753fccbfc11dc1b7b6",
      "https://fdroid.fedilab.app/repo" to
        "11f0a69910a4280e2cd3ccc3146337d006be539b18e1a9feace15ff757a94feb",
      "https://store.nethunter.com/repo" to
        "fe7a23dfc003a1cf2d2add2469b9c0c49b206ba5dc9edd6563b3b7eb6a8f5fab",
      "https://thecapslock.gitlab.io/fdroid-patched-apps/fdroid/repo" to
        "313d9e6e789ff4e8e2d687aae31eef576050003ed67963301821ac6d3763e3ac",
      "https://mobileapp.bitwarden.com/fdroid/repo" to
        "bc54ea6fd1cd5175bcccc47c561c5726e1c3ed7e686b6db4b18bac843a3efe6c",
      "https://briarproject.org/fdroid/repo" to
        "1fb874bee7276d28ecb2c9b06e8a122ec4bcb4008161436ce474c257cbf49bd6",
      "https://guardianproject-wind.s3.amazonaws.com/fdroid/repo" to
        "182cf464d219d340da443c62155198e399fec1bc4379309b775dd9fc97ed97e1",
      "https://nanolx.org/fdroid/repo" to
        "862ed9f13a3981432bf86fe93d14596b381d75be83a1d616e2d44a12654ad015",
      "https://releases.threema.ch/fdroid/repo" to
        "5734e753899b25775d90fe85362a49866e05ac4f83c05bef5a92880d2910639e",
      "https://fdroid.getsession.org/fdroid/repo" to
        "db0e5297eb65cc22d6bd93c869943bdcfcb6a07dc69a48a0dd8c7ba698ec04e6",
      "https://fdroid.twinhelix.com/fdroid/repo" to
        "7b03b0232209b21b10a30a63897d3c6bca4f58fe29bc3477e8e3d8cf8e304028",
      "https://fdroid.typeblog.net" to
        "1a7e446c491c80bc2f83844a26387887990f97f2f379ae7b109679feae3dbc8c",
      "https://s2.spiritcroc.de/fdroid/repo" to
        "6612ade7e93174a589cf5ba26ed3ab28231a789640546c8f30375ef045bc9242",
      "https://s2.spiritcroc.de/testing/fdroid/repo" to
        "52d03f2fab785573bb295c7ab270695e3a1bdd2adc6a6de8713250b33f231225",
      "https://static.cryptomator.org/android/fdroid/repo" to
        "f7c3ec3b0d588d3cb52983e9eb1a7421c93d4339a286398e71d7b651e8d8ecdd",
      "https://cdn.kde.org/android/stable-releases/fdroid/repo" to
        "13784ba6c80ff4e2181e55c56f961eed5844cea16870d3b38d58780b85e1158f",
      "https://zimbelstern.eu/fdroid/repo" to
        "285158decef37cb8de7c5af14818acbf4a9b1fbe63116758efc267f971ca23aa",
      "https://app.simplex.chat/fdroid/repo" to
        "9f358ff284d1f71656a2bfaf0e005deae6aa14143720e089f11ff2ddcfeb01ba",
      "https://f-droid.monerujo.io/fdroid/repo" to
        "a82c68e14af0aa6a2ec20e6b272eff25e5a038f3f65884316e0f5e0d91e7b713",
      "https://fdroid.cakelabs.com/fdroid/repo" to
        "ea44efaee0b641ee7a032d397d5d976f9c4e5e1ed26e11c75702d064e55f8755",
      "https://app.futo.org/fdroid/repo" to
        "39d47869d29cbfce4691d9f7e6946a7b6d7e6ff4883497e6e675744ecdfa6d6d",
      "https://fdroid.mm20.de/repo" to
        "156fbab952f6996415f198f3f29628d24b30e725b0f07a2b49c3a9b5161eee1a",
      "https://breezy-weather.github.io/fdroid-repo/fdroid/repo" to
        "3480a7bb2a296d8f98cb90d2309199b5b9519c1b31978dbcd877adb102af35ee",
      "https://gh.artemchep.com/keyguard-repo-fdroid/repo" to
        "03941ce79b081666609c8a48ab6e46774263f6fc0bbf1fa046ccffc60ea643bc",
      "https://f5a.torus.icu/fdroid/repo" to
        "5d87ce1fad3772425c2a7ed987a57595a20b07543b9595a7fd2ced25dff3cf12",
      "https://fdroid.i2pd.xyz/fdroid/repo" to
        "2b9564b0895eeac039e854c6b065291b01e6a9ca02939cedd0d35cf44bee78e0",
      "https://fdroid.ironfoxoss.org/fdroid/repo" to
        "c5e291b5a571f9c8cd9a9799c2c94e02ec9703948893f2ca756d67b94204f904",
      "https://raw.githubusercontent.com/chrisgch/tca/master/fdroid/repo" to
        "3576596cecdd70488d61cfd90799a49b7ffd26a81a8fef1badec88d069fa72c1",
      "https://brave-browser-apk-release.s3.brave.com/fdroid/repo" to
        "3c60de135aa19ec949e998469c908f7171885c1e2805f39eb403ddb0f37b4bd2",
      "https://brave-browser-apk-beta.s3.brave.com/fdroid/repo" to
        "c4675f87e60d58cecbecd884e43c4a089f2a3fdf58caf823609b809ac1f90fae",
      "https://brave-browser-apk-nightly.s3.brave.com/fdroid/repo" to
        "1735e2323fde104a74f16d31c062df75c30e79a706292fabea1f94e6af06ee62",
    )

  @Test
  fun `the own repos come first and only the stable one and IzzyOnDroid are enabled`() {
    assertEquals(
      listOf(ownRepo, NEEDANAPP_TESTING_REPO, izzyRepo),
      extraRepos.take(3).map { it.address },
    )
    extraRepos.forEach { assertEquals(it.address in enabledRepos, it.enabled, it.name) }
  }

  @Test
  fun `addresses are unique and not already default repos`() {
    val addresses = extraRepos.map { it.address }
    assertEquals(addresses.size, addresses.toSet().size)
    val defaults = defaultRepos.map { it.address }.toSet()
    addresses.forEach { assertTrue(it !in defaults, "$it ist schon ein Standard-Repo") }
    addresses.forEach { assertTrue(!it.endsWith("/"), "$it endet mit /") }
  }

  @Test
  fun `certificates match the expected fingerprints`() {
    assertEquals(expectedFingerprints.keys, extraRepos.map { it.address }.toSet())
    extraRepos.forEach { repo ->
      val cert = repo.certificate.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
      val fingerprint =
        MessageDigest.getInstance("SHA-256").digest(cert).joinToString("") { "%02x".format(it) }
      assertEquals(expectedFingerprints[repo.address], fingerprint, repo.name)
    }
  }

  @Test
  fun `pre-loader inserts extra repos after the default repos`() {
    val assetManager: AssetManager = mockk(relaxed = true)
    val context: Context = mockk(relaxed = true)
    val db: FDroidDatabase = mockk(relaxed = true)
    val repositoryDao: RepositoryDao = mockk(relaxed = true)
    every { context.assets } returns assetManager
    every { context.packageName } returns "org.fdroid.basic"
    every { db.getRepositoryDao() } returns repositoryDao
    every { assetManager.open("default_repos.json") } returns
      File(assets, "default_repos.json").inputStream()
    every { assetManager.open(EXTRA_REPOS_ASSET) } returns
      File(assets, EXTRA_REPOS_ASSET).inputStream()

    val inserted = mutableListOf<InitialRepository>()
    every { repositoryDao.insert(capture(inserted)) } returns 0L
    RepoPreLoader(context) { File("does-not-exist") }.addPreloadedRepositories(db)

    verify(exactly = defaultRepos.size + extraRepos.size) {
      repositoryDao.insert(any<InitialRepository>())
    }
    assertEquals(
      defaultRepos.map { it.address } + extraRepos.map { it.address },
      inserted.map { it.address },
    )
    inserted.drop(defaultRepos.size).forEach {
      assertEquals(it.address in enabledRepos, it.enabled, it.name)
    }
  }

  @Test
  fun `removed repos are no longer shipped`() {
    val addresses = extraRepos.map { it.address }.toSet()
    REMOVED_REPOS.forEach { assertTrue(it !in addresses, "$it ist noch dabei") }
  }

  @Test
  fun `cleanup removes only disabled removed repos`() {
    fun repo(id: Long, address: String, enabled: Boolean): Repository = mockk {
      every { repoId } returns id
      every { this@mockk.address } returns address
      every { this@mockk.enabled } returns enabled
    }
    val pan1kOff = repo(1, "https://fdroid.pan1k.de/repo", enabled = false)
    val pan1kOn = repo(2, "https://fdroid.pan1k.de/repo", enabled = true)
    val ownOff = repo(3, ownRepo, enabled = false)
    assertEquals(listOf(pan1kOff), removableRepos(listOf(pan1kOff, ownOff)))
    assertEquals(emptyList(), removableRepos(listOf(pan1kOn, ownOff)))
  }

  @Test
  fun `the move adds the new repo once and turns off the testing repo without login`() {
    fun repo(id: Long, address: String, enabled: Boolean, user: String? = null): Repository =
      mockk {
        every { repoId } returns id
        every { this@mockk.address } returns address
        every { this@mockk.enabled } returns enabled
        every { username } returns user
      }
    val testingOn = repo(1, NEEDANAPP_TESTING_REPO, enabled = true)
    val testingLoggedIn = repo(2, NEEDANAPP_TESTING_REPO, enabled = true, user = "ich")
    val testingOff = repo(3, NEEDANAPP_TESTING_REPO, enabled = false)
    val apps = repo(4, NEEDANAPP_REPO, enabled = true)
    assertEquals(
      NeedanappRepoChanges(add = true, disable = testingOn),
      needanappRepoChanges(listOf(testingOn)),
    )
    assertEquals(
      NeedanappRepoChanges(add = true, disable = null),
      needanappRepoChanges(listOf(testingLoggedIn)),
    )
    assertEquals(
      NeedanappRepoChanges(add = false, disable = null),
      needanappRepoChanges(listOf(testingOff, apps)),
    )
    assertEquals(
      NeedanappRepoChanges(add = true, disable = null),
      needanappRepoChanges(emptyList()),
    )
  }
}
