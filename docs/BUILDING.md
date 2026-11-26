# Building Revenge

## Requirements

- JDK 21 (Android Studio's bundled JBR works)
- Android SDK Platform 37 and Build Tools
- Android NDK `28.2.13676358` (only needed for native recognition assets)
- CMake and Ninja from the SDK manager

## Configure

Create `local.properties` in the repository root:

```properties
sdk.dir=/path/to/Android/Sdk
```

Optional keys, read at build time only:

```properties
LASTFM_API_KEY=...
LASTFM_SECRET=...
GH_CLIENT_ID=...
GH_CLIENT_SECRET=...
REVENGE_UPDATE_REPO=owner/name
```

Never commit this file. It is ignored by git.

## Build variants

The app has two flavor dimensions: ABI (`universal`, `arm64`, `armeabi`,
`x86`, `x86_64`) and distribution (`foss`, `gms`).

```bash
# FOSS debug APK for all ABIs (default development target)
./gradlew :app:assembleUniversalFossDebug

# GMS debug APK with Cast support
./gradlew :app:assembleUniversalGmsDebug

# Release build for one ABI
./gradlew :app:assembleArm64FossRelease
```

Release builds expect `keystore.jks` in the repository root plus
`STORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` in the environment. Without a
keystore the release build falls back to the debug signing config, which is
suitable only for local testing.

## Package layout

| Module | Responsibility |
| --- | --- |
| `:app` | Compose UI, playback service, navigation, widgets, integrations wiring |
| `:core` | Room database, preference keys, shared models and repositories |
| `:domain` | Platform-free domain models and repository contracts |
| `:innertube` | YouTube Music InnerTube client, models, and stream resolution |
| `:playback` | Queue implementations, audio processors, cutoff protection |
| `:lyrics` | Lyrics orchestration, translation, provider registry |
| `:lrclib`, `:kugou`, `:betterlyrics`, `:simpmusic`, `:youlyplus`, `:paxsenixlyrics`, `:unison` | Lyrics providers |
| `:dsp-core`, `:audio-dsp` | Parametric EQ model/parser and Media3 audio processors |
| `:usbaudio` | USB DAC detection and output routing |
| `:canvas`, `:applecanvas`, `:artistvideo` | Animated artwork providers |
| `:shazamkit` | Audio fingerprint recognition client |
| `:metadata` | Tag and metadata helpers shared by providers |

## Troubleshooting

- **SDK 37 not found**: install platform 37 with `sdkmanager "platforms;android-37.0"`.
- **Out of memory**: increase `org.gradle.jvmargs` in `gradle.properties`.
- **NDK errors**: set `ndkVersion` in `app/build.gradle.kts` to an installed NDK.
