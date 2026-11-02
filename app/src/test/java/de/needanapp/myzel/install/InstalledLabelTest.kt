package de.needanapp.myzel.install

import android.app.LocaleManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageInstaller.PreapprovalDetails
import android.content.pm.PackageInstaller.Session
import android.content.pm.PackageManager
import android.content.pm.PackageManager.NameNotFoundException
import android.content.res.Configuration
import android.content.res.Resources
import android.icu.util.ULocale
import android.os.LocaleList
import android.telephony.TelephonyManager
import androidx.core.content.ContextCompat.registerReceiver
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkConstructor
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkAll
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.fdroid.database.AppMetadata
import org.fdroid.index.v2.PackageVersion
import org.fdroid.install.InstallBroadcastListener
import org.fdroid.install.InstallBroadcastReceiver
import org.fdroid.install.InstallBroadcastReceiverFactory
import org.fdroid.install.PreApprovalResult
import org.fdroid.install.SessionInstallManager
import org.fdroid.ui.utils.isAppInForeground
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@Config(sdk = [34])
@RunWith(RobolectricTestRunner::class)
internal class InstalledLabelTest {

  private val packageName = "org.mozilla.fennec_fdroid"
  private val context: Context = mockk(relaxed = true)
  private val packageManager: PackageManager = mockk()
  private val localeManager: LocaleManager = mockk()
  private val appResources: Resources = mockk()
  private val config = slot<Configuration>()

  @Before
  fun setUp() {
    every { context.packageManager } returns packageManager
    every { context.getSystemService(LocaleManager::class.java) } returns localeManager
    every { context.resources.configuration } returns Configuration()
    // Myzel selbst kann eine andere Sprache haben, maßgeblich ist die des Systems
    every { localeManager.systemLocales } returns LocaleList.forLanguageTags("de-DE,en-US")
    every {
      packageManager.getResourcesForApplication(any<ApplicationInfo>(), capture(config))
    } returns appResources
  }

  @After
  fun tearDown() {
    unmockkAll()
  }

  private fun installed(labelRes: Int = 0, nonLocalizedLabel: String? = null) {
    val info = ApplicationInfo()
    info.packageName = packageName
    info.labelRes = labelRes
    info.nonLocalizedLabel = nonLocalizedLabel
    every { packageManager.getApplicationInfo(packageName, 0) } returns info
  }

  @Test
  fun `not installed gives no label`() {
    every { packageManager.getApplicationInfo(packageName, 0) } throws NameNotFoundException()
    assertNull(InstalledLabel.of(context, packageName))
  }

  @Test
  fun `label from the app resources in the system language`() {
    installed(labelRes = 7)
    every { appResources.getText(7) } returns " Fennec \n"
    val label = InstalledLabel.of(context, packageName)
    assertEquals(InstalledLabel("Fennec", ULocale.forLanguageTag("de-DE")), label)
    assertEquals(LocaleList.forLanguageTags("de-DE,en-US"), config.captured.locales)
  }

  @Test
  fun `fixed label stays as it is`() {
    installed(nonLocalizedLabel = "KeePassDX ")
    assertEquals("KeePassDX ", InstalledLabel.of(context, packageName)?.text)
  }

  @Test
  fun `missing label resource gives the package name`() {
    installed(labelRes = 7)
    every { appResources.getText(7) } throws Resources.NotFoundException()
    assertEquals(packageName, InstalledLabel.of(context, packageName)?.text)
  }

  @Test
  fun `pre-approval uses the installed label instead of the repo name`() = runBlocking {
    mockkStatic(PendingIntent::class)
    mockkStatic("androidx.core.content.ContextCompat")
    mockkStatic("org.fdroid.ui.utils.UiUtilsKt")
    mockkConstructor(Intent::class)
    every { anyConstructed<Intent>().setPackage(any()) } returns mockk()
    every { anyConstructed<Intent>().putExtra(any(), any<Int>()) } returns mockk()
    every { anyConstructed<Intent>().putExtra(any(), any<String>()) } returns mockk()
    every { anyConstructed<Intent>().addFlags(any()) } returns mockk()

    val packageInstaller: PackageInstaller = mockk()
    every { packageManager.packageInstaller } returns packageInstaller
    every { packageInstaller.mySessions } returns emptyList()
    every { context.isAppInForeground() } returns true
    every { context.getSystemService(Context.TELEPHONY_SERVICE) } returns
      mockk<TelephonyManager>(relaxed = true)
    every { packageInstaller.createSession(any()) } returns 1
    val session: Session = mockk(relaxed = true)
    every { packageInstaller.openSession(1) } returns session

    val receiverFactory: InstallBroadcastReceiverFactory = mockk()
    val receiver: InstallBroadcastReceiver = mockk(relaxed = true)
    val listener = slot<InstallBroadcastListener>()
    every { receiverFactory.create(1, capture(listener)) } returns receiver
    every { registerReceiver(context, receiver, any<IntentFilter>(), any()) } returns mockk()
    val pendingIntent: PendingIntent = mockk()
    every { PendingIntent.getBroadcast(any(), any(), any<Intent>(), any()) } returns pendingIntent
    every { pendingIntent.intentSender } returns mockk(relaxed = true)

    val details = slot<PreapprovalDetails>()
    every { session.requestUserPreapproval(capture(details), any()) } answers
      {
        listener.captured.invoke(receiver, PackageInstaller.STATUS_SUCCESS, null, null)
      }

    val app =
      AppMetadata(
        repoId = 1L,
        packageName = packageName,
        added = 0L,
        lastUpdated = 0L,
        name = mapOf("en-US" to "Fennec F-Droid"),
        isCompatible = true,
      )
    val version: PackageVersion = mockk(relaxed = true)
    val result =
      SessionInstallManager(context, CoroutineScope(Dispatchers.Unconfined), receiverFactory)
        .requestPreapproval(
          app = app,
          iconGetter = { null },
          isUpdate = true,
          version = version,
          canRequestUserConfirmationNow = true,
          installedLabel = InstalledLabel("Fennec", ULocale.forLanguageTag("de-DE")),
        )

    assertIs<PreApprovalResult.Success>(result)
    assertEquals("Fennec", details.captured.label)
    assertEquals(ULocale.forLanguageTag("de-DE"), details.captured.locale)
  }
}
