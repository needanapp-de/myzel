import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Myzel Wear (Umbauplan Wear-OS-Unterstützung, Phase 2): die App für die Uhr. Nimmt über Myzel Link
// APKs vom Handy an, prüft sie und installiert sie. Gleicher Paketname, gleicher Versionsname und
// gleicher Schlüssel wie Myzel; in der Paketquelle ist es die Uhr-Version von Myzel (Merkmal
// android.hardware.type.watch), die Myzel bei der Einrichtung per ADB und danach über Bluetooth auf
// die Uhr bringt. Nur Debug-Builds von Myzel legen sie als Asset bei (app/myzel.gradle).
// Übernommen aus dem Prototyp myzel-link-prototype (Modul wear).
plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.jetbrains.compose.compiler)
  alias(libs.plugins.ktfmt)
}

val myzelVersion = Properties()

/** Abstand der versionCodes von Myzel zu denen von Myzel Wear (myzel/version.properties). */
val wearVersionOffset = 1_000

rootProject.file("myzel/version.properties").inputStream().use { myzelVersion.load(it) }

// derselbe Release-Schlüssel wie bei Myzel (app/myzel.gradle); fehlt er, bleibt der Release wie
// dort unsigniert (so baut ihn F-Droid und signiert selbst)
val signingDir =
  File(
    findProperty("needanapp.signingDir")?.toString()
      ?: "${System.getProperty("user.home")}/needanapp-signing"
  )
val keystoreProperties = File(signingDir, "keystore.properties")

android {
  namespace = "de.needanapp.myzel.wear"
  compileSdk = libs.versions.compileSdk.get().toInt()

  defaultConfig {
    applicationId = "de.needanapp.myzel"
    // Wear OS 3 (Galaxy Watch 4) und neuer
    minSdk = 30
    targetSdk = 37
    // eigene Codes, denn Handy- und Uhr-APK liegen nebeneinander in der Paketquelle; die der Uhr
    // liegen darunter (wie bei Catima), damit kein Client sie dem Handy als Update anbietet: Myzel
    // 1023 gehört zu Myzel Wear 23
    versionCode =
      myzelVersion.getProperty("versionCode").toInt().let {
        check(it > wearVersionOffset) {
          "versionCode von Myzel muss über $wearVersionOffset liegen"
        }
        it - wearVersionOffset
      }
    versionName = myzelVersion.getProperty("versionName")
  }

  signingConfigs {
    if (keystoreProperties.exists()) {
      create("needanapp") {
        val properties = Properties()
        keystoreProperties.inputStream().use { properties.load(it) }
        storeFile = signingDir.resolve(properties.getProperty("storeFile"))
        storePassword = properties.getProperty("storePassword")
        keyAlias = properties.getProperty("keyAlias")
        keyPassword = properties.getProperty("keyPassword")
      }
    }
  }

  buildTypes {
    getByName("release") {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
      signingConfigs.findByName("needanapp")?.let { signingConfig = it }
    }
    getByName("debug") {
      // wie bei Myzel, damit Handy und Uhr dasselbe Paket haben: der Schlüssel geht an
      // content://<Paket>.key
      applicationIdSuffix = ".debug"
      // Myzel legt die Debug-APK bei; unverkleinert wäre sie gut 25 MB groß
      isMinifyEnabled = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures { compose = true }
}

kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_17 } }

dependencies {
  implementation(project(":myzel-link"))
  implementation(platform(libs.androidx.compose.bom))
  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.core.ktx)
  implementation("androidx.wear.compose:compose-material3:1.7.0")
  implementation("androidx.wear.compose:compose-foundation:1.7.0")
}

ktfmt { googleStyle() }
