import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// Myzel Link (Umbauplan Wear-OS-Unterstützung, Phase 4): die Verbindung zwischen Myzel und
// Myzel Wear über einen eigenen RFCOMM-Kanal der bestehenden Bluetooth-Kopplung, ohne Google-Code.
// Protokoll (de.needanapp.myzel.link) und Bluetooth-Teil (…link.android), übernommen aus dem
// Prototyp myzel-link-prototype (Module link-core und link-android).
plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.ktfmt)
}

android {
  namespace = "de.needanapp.myzel.link"
  compileSdk = libs.versions.compileSdk.get().toInt()

  defaultConfig {
    // Wear OS 3 (Galaxy Watch 4) ist API 30
    minSdk = 30
    consumerProguardFiles("consumer-rules.pro")
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

kotlin { compilerOptions { jvmTarget = JvmTarget.JVM_17 } }

dependencies {
  api(
    "org.jetbrains.kotlinx:kotlinx-coroutines-android:${libs.versions.kotlinxCoroutinesTest.get()}"
  )
  testImplementation(libs.kotlin.test)
  testImplementation(libs.junit)
}

ktfmt { googleStyle() }
