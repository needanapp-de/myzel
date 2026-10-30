// Baut das Design-Paket von needanapp.de (Submodul myzel/needanapp-design) mit den Werkzeugen von
// F-Droid. Das Paket bringt eine eigene Build-Datei mit, die aber eine neuere Gradle-Version
// voraussetzt als F-Droid; deshalb werden hier nur seine Quellen übernommen.
// Die Versionen entsprechen gradle/libs.versions.toml des Design-Pakets.
plugins {
  alias(libs.plugins.android.library)
  alias(libs.plugins.jetbrains.compose.compiler)
}

val design = file("../needanapp-design/design")

android {
  namespace = "de.needanapp.design"
  compileSdk = libs.versions.compileSdk.get().toInt()
  defaultConfig {
    minSdk = 31
    consumerProguardFiles(design.resolve("consumer-rules.pro"))
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures { compose = true }
  sourceSets {
    getByName("main") {
      manifest.srcFile(design.resolve("src/main/AndroidManifest.xml"))
      kotlin.directories += design.resolve("src/main/kotlin").path
      res.directories += design.resolve("src/main/res").path
    }
    getByName("test") { kotlin.directories += design.resolve("src/test/kotlin").path }
  }
}

dependencies {
  api(platform("androidx.compose:compose-bom:2026.09.00"))
  api(libs.androidx.compose.material3)
  api("androidx.compose.ui:ui")
  api("androidx.compose.foundation:foundation")
  api("androidx.compose.material:material-icons-core:1.7.8")
  implementation("androidx.compose.material:material-icons-extended:1.7.8")
  api("androidx.activity:activity-compose:1.13.0")
  api("dev.chrisbanes.haze:haze:2.0.1")
  api("dev.chrisbanes.haze:haze-blur:2.0.1")
  implementation("androidx.compose.ui:ui-tooling-preview")
  implementation("androidx.core:core-ktx:1.19.1")
  implementation("androidx.appcompat:appcompat:1.7.1")
  implementation("com.google.android.material:material:1.13.0")
  implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
  implementation("androidx.datastore:datastore-preferences:1.2.1")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
  implementation("com.materialkolor:material-kolor:5.0.1")
  testImplementation(libs.junit)
  testImplementation(libs.mockk)
}
