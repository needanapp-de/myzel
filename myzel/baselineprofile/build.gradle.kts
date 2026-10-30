// Erzeugt das Baseline Profile von Myzel auf einem angeschlossenen Gerät (ab Android 13, ohne
// Root): startet die Variante basicDefaultProfiling der App, wartet auf Entdecken, scrollt und
// öffnet eine App-Seite. Aufruf und Ablage des Ergebnisses: myzel/README.md, Abschnitt
// „Baseline Profile“.
plugins {
  id("com.android.test")
  alias(libs.plugins.ktfmt)
}

android {
  namespace = "de.needanapp.myzel.baselineprofile"
  compileSdk = libs.versions.compileSdk.get().toInt()

  defaultConfig {
    minSdk = 31
    targetSdk = 37
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  // dieselben Varianten wie die App, damit basicDefaultProfiling zueinander findet
  flavorDimensions += listOf("variant", "release")
  productFlavors {
    create("basic") { dimension = "variant" }
    create("default") { dimension = "release" }
  }
  buildTypes {
    create("profiling") {
      isDebuggable = true
      signingConfig = signingConfigs.getByName("debug")
      // Bibliotheken ohne diesen Build-Typ, wie in der App
      matchingFallbacks += "release"
    }
  }

  targetProjectPath = ":app"
  // der Test läuft in einem eigenen Prozess und startet die App wie ein Nutzer
  @Suppress("UnstableApiUsage")
  experimentalProperties["android.experimental.self-instrumenting"] = true
}

androidComponents { beforeVariants { it.enable = it.buildType == "profiling" } }

dependencies {
  implementation(libs.androidx.test.ext.junit)
  implementation(libs.androidx.test.runner)
  implementation(libs.androidx.test.uiautomator)
  implementation("androidx.benchmark:benchmark-macro-junit4:1.5.0")
}

ktfmt { googleStyle() }
