# Revenge

Revenge is an independent Android music player for YouTube Music and local audio. It brings ad-free streaming, offline downloads, synchronized lyrics, a full DSP/equalizer stack, and a Material 3 interface into one app.

## Features

- Ad-free streaming playback backed by YouTube Music through a bundled InnerTube client.
- Background playback, queue management, gapless playback, crossfade, sleep timer, and playback speed controls.
- Offline downloads with a download manager and update-safe file handling.
- Synchronized lyrics from LRCLIB, Paxsenix, YoulyPlus, and BetterLyrics, with on-device caching.
- Multi-band equalizer, bass boost, virtualizer, loudness normalization, and audio-effect routing.
- Local media library playback with folder scanning and metadata extraction.
- Spotify playlist and library import.
- Last.fm scrobbling and ListenBrainz submission.
- Listen Together sessions for synchronized group listening.
- Chromecast support in the `gms` flavor.
- Animated album art (“Canvas”) sourced from Apple Music and TIDAL.
- Home-screen widgets: adaptive, compact, wide, square, showcase, vinyl, turntable, playlist, and music-recognition layouts.
- On-device music recognition (“Find”).
- Listening statistics, a yearly listening recap, and shareable cards.
- Personalized queue recommendations generated on-device.
- USB audio output routed through the platform audio stack (no bit-perfect/URB path).
- Podcast playback and subscription management.
- Selectable app icons, dynamic color, custom fonts, and light/dark/black themes.

## Tech stack

- Kotlin, Jetpack Compose, Material 3
- Hilt (Dagger) and KSP
- Media3 / ExoPlayer
- Room and DataStore
- Kotlin Serialization and Protobuf
- Ktor and OkHttp
- Gradle Kotlin DSL with version catalogs
- Product flavors: `foss` (no Google Play services) and `gms` (Cast and Google services)

## Requirements

- JDK 21
- Android SDK Platform 37

## Development setup

1. Clone the repository.
2. Create `local.properties` in the project root:

   ```properties
   sdk.dir=/path/to/Android/Sdk
   ```

3. Optionally provide the API keys listed below in `local.properties` or as environment variables.
4. Build a debug APK:

   ```bash
   ./gradlew :app:assembleUniversalFossDebug
   # or
   ./gradlew :app:assembleUniversalGmsDebug
   ```

Install with:

```bash
./gradlew :app:installUniversalGmsDebug
```

## Configuration

| Key | Purpose | Default |
| --- | --- | --- |
| `LASTFM_API_KEY` | Last.fm API key | empty |
| `LASTFM_SECRET` | Last.fm API secret | empty |
| `GH_CLIENT_ID` | GitHub OAuth client ID | empty |
| `GH_CLIENT_SECRET` | GitHub OAuth client secret | empty |
| `REVENGE_UPDATE_REPO` | `owner/name` of the release repository used by the in-app updater | empty (update checks disabled) |
| `FLOW_NEURO_BASE_URL` | Base URL for the optional AI service | `https://api.flowneuroengine.com` |
| `FLOW_NEURO_API_KEY` | API key for the optional AI service | empty |

Secrets are never committed. `local.properties`, signing keys, and `google-services.json` are ignored by Git.

## Project structure

```
app/                  Android application, UI, playback service, widgets
core/                 Database, entities, DAOs, repositories, mappings
domain/               Domain models, repositories, and use cases
playback/             Playback queues and crossfade logic
lyrics/               Lyrics providers and synchronization
betterlyrics/         BetterLyrics integration
innertube/            YouTube Music InnerTube client
paxsenixlyrics/       Paxsenix lyrics provider
youlyplus/            YouLyPlus lyrics provider
lrclib/               LRCLIB lyrics provider
kugou/                Kugou lyrics provider
simpmusic/            SimpMusic metadata provider
shazamkit/            Music recognition client
metadata/             Audio metadata parsing
unison/               Audio matching utilities
canvas/               Canvas providers
applecanvas/          Apple Music canvas provider
artistvideo/          Artist video provider
audio-dsp/            Audio effects and DSP controller
dsp-core/             DSP primitives and parsers
usbaudio/             USB audio driver (platform AudioTrack routing)
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for development conventions, formatting, and testing.

## Roadmap

- Restore signed release builds and a configurable update channel.
- Expand unit and instrumentation test coverage.
- Continue localization and accessibility passes.

## Download

Prebuilt APKs are published on the
[releases page](https://github.com/tirthsaraiya29/Revenge/releases). The
[project page](https://tirthsaraiya29.github.io/Revenge/) reads the latest
release and offers the APK with a 30 second automatic download.

## License

Revenge is free software licensed under the
[GNU General Public License v3.0](LICENSE). You may redistribute and modify it
under the same license. There is no warranty.

---

## Website

[Visit my website](https://tirthsaraiya.vercel.app)
