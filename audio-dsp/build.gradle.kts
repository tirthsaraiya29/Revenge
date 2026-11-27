plugins {
  id("com.android.library")
  id("com.google.devtools.ksp")
  id("com.google.dagger.hilt.android")
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "com.revenge.dsp.audio"
  compileSdk = 37
  defaultConfig { minSdk = 26 }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
}

kotlin { jvmToolchain(21) }

dependencies {
  api(project(":dsp-core"))
  api(libs.media3)
  implementation(libs.timber)
  implementation(libs.coroutines.core)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.hilt)
  ksp(libs.hilt.compiler)
  testImplementation(libs.junit)
}
