plugins {
  id("org.jetbrains.kotlin.jvm")
}

kotlin {
  jvmToolchain(21)
}

dependencies {
  implementation(libs.coroutines.core)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
}
