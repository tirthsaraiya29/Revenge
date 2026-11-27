plugins {
  id("com.android.library")
  id("com.google.devtools.ksp")
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "com.revenge.core"
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
  api(libs.datastore)
  api(libs.compose.runtime)
  api(libs.compose.ui)
  api(libs.compose.animation)
  api(libs.room.runtime)
  api(libs.room.ktx)
  api(libs.timber)
  api(libs.ktor.client.core)
  api(libs.ktor.serialization.json)
  api(libs.apache.lang3)
  api(libs.protobuf.javalite)
  api(libs.media3)
  api(libs.media3.session)
  api("javax.inject:javax.inject:1")
  api(libs.core.ktx)

  api(project(":innertube"))
  api(project(":metadata"))
  api(project(":domain"))

  ksp(libs.room.compiler)
  coreLibraryDesugaring(libs.desugaring)

  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)
}

ksp { arg("room.schemaLocation", "$projectDir/schemas") }
