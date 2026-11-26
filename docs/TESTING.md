# Testing

## Commands

```bash
# Fast unit tests for pure JVM modules
./gradlew :metadata:test :domain:test :dsp-core:test

# Unit tests plus Android module tests
./gradlew test

# Compile every module without running tests
./gradlew compileUniversalFossDebugKotlin
```

Tests live next to the module they cover:

- `domain/src/test` — domain model and repository contract behaviour
- `metadata/src/test` — tag cleaning and metadata normalisation
- `dsp-core/src/test` — parametric EQ parsing and validation
- `core/src/test` — Room DAO behaviour under Robolectric
- `app/src/test` — recommendation scoring, generation, playback helpers

## What is covered

Unit tests target logic that can fail silently: parsing, scoring, queue
ordering, cutoff/crossfade math, DB queries, and metadata normalisation. UI and
network behaviour are exercised manually before a release; they are not claimed
as automated coverage.

## Manual smoke test for a release

1. Fresh install, complete onboarding, grant notification permission.
2. Search a song, play it, background the app, control playback from the
   notification and from a Bluetooth headset.
3. Create a playlist, reorder it, download a song, play it in airplane mode.
4. Open lyrics for a streamed song and for a downloaded local file.
5. Change theme, font, and EQ; kill the app; verify the settings persist.
6. Start and stop a sleep timer, change playback speed, connect a DAC if
   available.
7. Import a Spotify playlist and verify songs resolve.
