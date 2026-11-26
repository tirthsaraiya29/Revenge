# Privacy

Revenge is a local-first music player. It has no account system and no telemetry
endpoint. The FOSS flavor contains no Google Play services and no analytics SDK.
The GMS flavor bundles Firebase Analytics and Crashlytics so Google Play builds
can report crashes; install the FOSS build if you do not want those libraries on
the device.

## What stays on the device

- Library, playlists, listening history, statistics, downloads, cached lyrics,
  and artwork are stored in the app's private storage (Room and DataStore).
- Search history, EQ profiles, and recommendation statistics never leave the
  device unless you export a backup yourself.
- Credentials for optional integrations (Last.fm, ListenBrainz, Spotify import,
  Listen Together) are stored locally in DataStore.

## Network requests the app makes

Requests go only to services needed for the feature you use:

- YouTube Music endpoints, to search, browse, and stream.
- Lyrics providers you enable: LRCLIB, Paxsenix, YouLyPlus, BetterLyrics,
  KuGou, SimpMusic, and Unison.
- Artwork and canvas providers: YouTube/Google image CDNs, Apple Music, TIDAL.
- Optional scrobbling: Last.fm and ListenBrainz, only if configured.
- Optional translation: the AI/translation endpoint you configure with your own
  key.
- Update checks: the GitHub release API for this repository, if enabled.
- Music recognition sends a fingerprint, not raw audio, to the recognition
  service.

Each request carries the minimum data needed. The app does not send device
identifiers or advertising IDs.

## Permissions

- Network and network state: streaming and browsing.
- Notifications and foreground service: playback controls.
- Read audio: the local library you choose.
- Record audio: only while you run music recognition.
- Location: optional, used for weather-aware artwork; denied by default.
- Install unknown apps: only to run an in-app update you explicitly start.

## Children

Revenge is not directed at children and collects no personal data.

## Changes

Material changes to this document ship with the release that introduces them.
