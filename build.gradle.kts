plugins {
    id("com.diffplug.spotless") version "8.11.0"

    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.kotlin.ksp) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.protobufPlugin) apply false
    alias(libs.plugins.compose.compiler) apply false
}

buildscript {
    repositories {
        google()
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
    }
    dependencies {
        classpath(libs.gradle)
        classpath(kotlin("gradle-plugin", libs.versions.kotlin.get()))
    }
}

subprojects {
    apply(plugin = "com.diffplug.spotless")
    configure<com.diffplug.gradle.spotless.SpotlessExtension> {
        kotlin {
            target("**/*.kt")
            targetExclude("**/build/**")
            ktfmt().googleStyle()
        }
        kotlinGradle {
            target("*.gradle.kts")
            ktfmt().googleStyle()
        }
    }
}
