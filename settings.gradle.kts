@file:Suppress("UnstableApiUsage")

pluginManagement {
    repositories {
        google()
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)

    repositories {
        google()
        maven { url = uri("https://maven-central.storage-download.googleapis.com/maven2/") }
        maven { url = uri("https://maven.aliyun.com/repository/public") }
        mavenCentral()
        maven { setUrl("https://jitpack.io") }
    }
}

rootProject.name = "Revenge"

include(
    ":app",
    ":core",
    ":domain",
    ":innertube",
    ":metadata",
    ":playback",
    ":usbaudio",
    ":dsp-core",
    ":audio-dsp",
    ":lyrics",
    ":lrclib",
    ":kugou",
    ":betterlyrics",
    ":simpmusic",
    ":youlyplus",
    ":paxsenixlyrics",
    ":canvas",
    ":applecanvas",
    ":artistvideo",
    ":shazamkit",
    ":unison",
)
