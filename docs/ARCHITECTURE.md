# Architecture

Revenge is a single Android application (`:app`) over a set of focused library
modules. The app owns UI, navigation, playback orchestration, and feature
wiring. Modules own protocols, persistence, DSP, and provider clients, and do not
depend on the app.

## Dependency direction

```
:app
 ├─ :core        (persistence, preferences, shared models, repositories)
 │   ├─ :innertube
 │   ├─ :metadata
 │   └─ :domain
 ├─ :playback
 │   ├─ :core
 │   ├─ :audio-dsp ─ :dsp-core
 │   └─ :usbaudio
 ├─ :lyrics
 │   ├─ :core and :innertube
 │   └─ provider modules
 ├─ provider modules (lrclib, kugou, betterlyrics, simpmusic, youlyplus,
 │   paxsenixlyrics, unison)
 ├─ :canvas, :applecanvas, :artistvideo
 └─ :shazamkit
```

Rules enforced by the graph:

- Provider modules never depend on `:core` or `:app`; they expose small clients
  that take plain strings.
- `:innertube` depends only on `:metadata` and network libraries, so the
  YouTube client can be tested without Android persistence.
- `:domain` is a plain Kotlin module: models and repository contracts, no
  Android, no network.
- `:playback` may depend on DSP, USB audio, and core, but not on UI or lyrics.

## Data flow

1. Compose screens call view models.
2. View models call repositories from `:core` or clients from `:innertube` and
   provider modules.
3. `:innertube` returns typed page results that the app maps to UI models.
4. Playback runs in `MusicService` (Media3 `MediaSessionService`). Queues in
   `:playback` decide what plays next; the audio pipeline applies DSP before the
   sink.
5. Room and DataStore persist library, history, queues, and preferences.

## Persistence

- Room owns relational data: songs, artists, albums, playlists, mappings, play
  events, lyrics cache, recognition history, taste profiles.
- DataStore Preferences owns user settings. Keys live in `:core` so the app and
  services read the same names.
- Queue and player state are serialized so playback can resume after process
  death.
- Writes that span tables use `MusicDatabase.query { }` for a single
  transaction.

## Playback pipeline

Media3 `ExoPlayer` decodes audio. The app assembles an audio sink chain:

```
decoder -> DSP processors (EQ, widening, ducking, normalisation)
        -> USB DAC sink (when enabled and connected)
        -> platform AudioTrack otherwise
```

Queue implementations share a `Queue` contract: peek the current item, advance,
and expose a `StateFlow` for the UI. Radio queues fetch continuations lazily and
stop on error instead of looping forever.

## Networking

- Ktor clients, one per provider, with timeouts and structured JSON parsing.
- Failures are wrapped in `Result` by public clients; the UI shows an error
  state and offers retry.
- No provider module logs request bodies or credentials.
- The InnerTube client distinguishes "login required" from transport failures so
  the app can prompt instead of showing an empty list.

## Theming and UI

- Material 3 with dynamic color from album art and a user-selected palette.
- Shared components live in the app, not in modules, so no module depends on
  Compose Material 3 UI except `:artistvideo` (its player surface is reusable).
- Screens expose empty, loading, and error states; skeletons are used only where
  layout is known in advance.

## Error handling

- Boundaries return `Result` for network work; the app maps failures to
  user-visible messages.
- Repository reads are flows; a database failure closes the flow with a
  diagnostic message rather than silently returning empty.
- Playback errors trigger skip-on-error only when the user enabled it.

## Concurrency

- Room and DataStore operations run on their own dispatchers.
- Network calls use `Dispatchers.IO` through Ktor.
- Service state is confined to the main thread; long work runs in coroutines
  owned by the service scope.
