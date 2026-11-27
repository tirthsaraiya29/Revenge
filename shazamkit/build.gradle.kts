plugins {
  id("com.android.library")
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "com.revenge.shazamkit"
  compileSdk = 37
  defaultConfig { minSdk = 26 }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
    isCoreLibraryDesugaringEnabled = true
  }
}

kotlin { jvmToolchain(21) }

dependencies {
  implementation(libs.ktor.client.core)
  implementation(libs.ktor.client.okhttp)
  implementation(libs.ktor.client.cio)
  implementation(libs.ktor.client.content.negotiation)
  implementation(libs.ktor.serialization.json)
  implementation(libs.ktor.client.encoding)
  coreLibraryDesugaring(libs.desugaring)
  testImplementation(libs.junit)
}
