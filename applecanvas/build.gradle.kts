plugins {
  alias(libs.plugins.kotlin.serialization)
  id("org.jetbrains.kotlin.jvm")
}

kotlin {
  jvmToolchain(21)
}

dependencies {
  api(project(":canvas"))
  implementation(libs.ktor.client.core)
  implementation(libs.ktor.client.okhttp)
  implementation(libs.ktor.client.content.negotiation)
  implementation(libs.ktor.serialization.json)
  implementation(libs.ktor.client.encoding)
  testImplementation(libs.junit)
}
