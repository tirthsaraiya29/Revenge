plugins {
    id("com.android.library")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.revenge.lyrics"
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
    implementation(project(":core"))
    implementation(project(":unison"))
    implementation(project(":lrclib"))
    implementation(project(":kugou"))
    implementation(project(":simpmusic"))
    implementation(project(":youlyplus"))
    implementation(project(":betterlyrics"))
    implementation(project(":paxsenixlyrics"))
    implementation(project(":innertube"))

    implementation(libs.kuromoji.ipadic)
    implementation(libs.tinypinyin)
    implementation(libs.core.ktx)
    api(libs.lyrics.core)
    implementation(libs.hilt)
    ksp(libs.hilt.compiler)

    implementation(libs.ktor.client.core)
    implementation(libs.ktor.client.okhttp)
    implementation(libs.ktor.client.content.negotiation)
    implementation(libs.ktor.serialization.json)
    implementation(libs.kotlinx.serialization.json)

    coreLibraryDesugaring(libs.desugaring)
    testImplementation(libs.junit)
}
