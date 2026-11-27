plugins {
  id("com.android.library")
}

android {
  namespace = "com.revenge.usbaudio"
  compileSdk = 37
  defaultConfig { minSdk = 26 }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
}

kotlin { jvmToolchain(21) }

dependencies {
  compileOnly(libs.media3)
  compileOnly(libs.coroutines.core)
  testImplementation(libs.media3)
  testImplementation(libs.junit)
}
