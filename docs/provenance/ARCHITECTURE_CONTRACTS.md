# Architecture Contracts Baseline — Revenge (`11ae6207` -> M2 Clean-Room Target)

**Status:** Initial M1.2 Architectural Contract Baseline (to be locked in M2 before Wave 1 implementation)
**Scope:** All `21` Gradle modules (`:app` + `20` library modules), flavor isolation (`foss` vs `gms`), package boundaries, Room v48/DataStore persistence, and R3 subsystem contracts.

---

## 1. Module Dependency DAG (21 Modules)

```
:app (Android Application — Compose UI, Navigation, MusicService, Widgets, DI Wiring)
 ├── :core          ──> :innertube, :metadata, :domain
 ├── :playback      ──> :core, :audio-dsp (:dsp-core), :usbaudio
 ├── :lyrics        ──> :core, :innertube, :lrclib (:metadata), :kugou, :betterlyrics, :simpmusic, :youlyplus, :paxsenixlyrics (:betterlyrics, :metadata), :unison
 ├── :audio-dsp     ──> :dsp-core
 ├── :usbaudio
 ├── :canvas, :applecanvas, :artistvideo
 └── :shazamkit
```

### Enforced Boundary Rules
1. **Pure-JVM Core (zero Android/Compose/Hilt/network runtime dependencies):** `:domain`, `:metadata`, `:dsp-core`, `:canvas`, `:applecanvas`, `:unison`.
2. **Standalone Provider / Utility Modules (no `:core` or `:app` dependency):** `:innertube` (`-> :metadata`), `:usbaudio`, `:lrclib` (`-> :metadata`), `:kugou`, `:betterlyrics`, `:simpmusic`, `:youlyplus`, `:paxsenixlyrics` (`-> :betterlyrics, :metadata`), `:artistvideo`, `:shazamkit`. Providers accept plain strings/domain primitives and return `Result<T>`.
3. **No UI Leakage Into Core Modules:** No module depends on Compose Material 3 UI except `:app` and `:artistvideo` (reusable video surface).

---

## 2. Split-Package Elimination Contract

Baseline `11ae6207` violates module encapsulation via split packages shared across multiple Gradle modules (`com.revenge.music.*` in `:app`, `:core`, `:playback`, `:lyrics`; `com.revenge.canvas` in `:app` & `:canvas`; `com.revenge.playback` in `:app` & `:playback`; `android.net` in `:playback`). M2/M3 enforces strict module-owned packages:

| Module | Baseline Packages (Split) | Clean-Room Target Namespace |
|---|---|---|
| `:app` | `com.revenge.music.*`, `com.revenge.canvas`, `com.revenge.playback`, `com.revenge.dpi` | `com.revenge.music.*` (`com.revenge.dpi` isolated or merged cleanly) |
| `:core` | `com.revenge.core.*`, `com.revenge.music.{ai,constants,db,di,extensions,models,playback,ui,utils}` | `com.revenge.core.*` (all DB/DataStore/repo/DI types migrated) |
| `:playback` | `com.revenge.music.playback.*`, `android.net` | `com.revenge.playback.*` (no `android.net` package shadow) |
| `:lyrics` | `com.revenge.music.{api,lyrics}` | `com.revenge.lyrics.*` |
| `:canvas` / `:applecanvas` / `:artistvideo` | `com.revenge.canvas`, `com.revenge.applecanvas`, `com.revenge.artistvideo` | `com.revenge.canvas.*` (`YouTubeVideoCanvasProvider` moved out of `:app` or cleanly namespaced) |
| Other 13 modules | `com.revenge.{domain,metadata,dsp,usbaudio,innertube,lrclib,kugou,betterlyrics,simpmusic,youlyplus,paxsenix,shazamkit,unison}.*` | Retain clean module-scoped namespaces |

---

## 3. Product Flavors & FOSS / GMS Isolation Contract

- **Flavors:** `variant` dimension (`foss` default, `gms`), `abi` dimension (`universal` default; `arm64`, `armeabi`, `x86`, `x86_64` gated behind `-PallAbis`).
- **FOSS Invariant:** `foss` APK MUST contain **zero** `com.google.android.gms.*`, `com.google.firebase.*`, or proprietary SDK classes/services/manifests:
  - Move `com.google.android.gms:play-services-cronet` out of `implementation` (`app/build.gradle.kts:308`) to `gmsImplementation` (or remove completely since `useCronet = false`).
  - Add `<service android:name="com.google.android.gms.cast.framework.ReconnectionService" tools:node="remove" />` to `app/src/foss/AndroidManifest.xml`.
  - Isolate Cast (`CastOptionsProvider`, `CastConnectionHandler`, `CastButton`, `CastDevicePickerSheet`, `CastSessionSheet`), Firebase, and GMS Location strictly inside `src/gms/` behind `BuildConfig.CAST_AVAILABLE` and Hilt/interface bindings.

---

## 4. Persistence & Migration Contract (Room v48 + DataStore)

- **Room Database:** `InternalDatabase` (baseline schema v48, `core/schemas/com.revenge.music.db.InternalDatabase/1..48.json`) manages songs, artists, albums, playlists, mappings, play events, lyrics cache, recognition history, and taste profiles. Cross-table writes execute inside `MusicDatabase.query { }`.
- **M2/M3 Schema Decision Gate:** Preserve Room v48 table/column names and `1..48` migration compatibility for upgrade continuity, OR declare an explicit clean v1 schema + migration strategy if schema cleanup is approved.
- **DataStore Preferences:** Preference keys live in `:core` (`PreferenceKeys`) as single source of truth shared across `:app`, `:playback`, and services.
- **Queue & Player State:** `PersistQueue` and `PersistPlayerState` serialize atomically to recover playback after process death.

---

## 5. R3 Subsystem Interface Contracts

| R3 Subsystem | Modules | Core Clean-Room Contract & Invariants |
|---|---|---|
| **App Entry & Lifecycle** | `:app` | `App`, `MainActivity` (singleTask, deep links, non-duplicating nav stack, process recreation), `CrashActivity` (`:crash` process), `DensityScaler`, cold-start budget |
| **Compose UI & Design System** | `:app` | `Design.kt` tokens (`RevengeColor`, `RevengeSpacing`, `RevengeRadius`, `RevengeSize`, `RevengeZIndex`, `RevengeMotion`), refactored `RevengeComponents.kt`, Material 3 dynamic color, OFL `sans_flex.ttf` typography, loading/empty/error/offline states, semantics/TalkBack |
| **Discovery & Streaming** | `:innertube`, `:app` | `YouTube` / `InnerTube` returning typed `Result<T>` distinguishing auth-required vs transport errors; QuickJS/WebView cipher, `po_token`, and SABR `n`-transform (`astring`/`meriyah`/`yt-dlp/ejs`); SponsorBlock; retries & cancellation |
| **Playback Engine** | `:playback`, `:app` | Media3 `MusicService` (`MediaLibraryService`), `Queue` contract (`StateFlow` + lazy continuations + error stop), gapless, crossfade (`StandbyCrossfadeScheduler`), `PlaybackCutoffGuard`, silence skip, audio focus, Bluetooth/media button controls, skip-on-error gating |
| **Audio DSP & USB Audio** | `:dsp-core`, `:audio-dsp`, `:usbaudio` | Parametric EQ parser/validator, clean `AutoEq` (MIT) profiles, `BiquadFilter` / `CustomEqualizer` / `StereoWidener` / `AutomixDuck` processors; `UsbDacManager` + `UsbAudioDriver` via platform `AudioTrack` routed to USB device (no false bit-perfect claim unless native URB path is built) |
| **Lyrics & Translation** | `:lyrics`, 7 providers | Provider interface returning parsed LRC/TTML (`XXE`-hardened), sync/offset, Kuromoji/TinyPinyin romanization, DeepL/Mistral/OpenRouter translation (`Puter` dead branch removed), on-device cache |
| **Canvas & Artist Video** | `:canvas`, `:applecanvas`, `:artistvideo` | Provider-isolated animated artwork & video surfaces (`Result<CanvasArtwork>`), clean cancellation |
| **Audio Recognition ("Find")** | `:shazamkit`, `:app` | `RECORD_AUDIO` permission flow, PCM capture, clean-room acoustic fingerprint generation (`ShazamSignatureGenerator`), provider matching, timeout/cancellation, guaranteed temporary audio cleanup, `RecognitionForegroundService`, QS tile |
| **External Integrations** | `:app`, `:core` | Spotify OAuth/import, Jellyfin, Navidrome/Subsonic, Listen Together (clean `listentogether.proto` + WebSocket sync), Google Cast (`gms` only), Android Auto (`AutomotiveArtworkProvider` signed URI check), Last.fm/ListenBrainz, Podcasts (`PodcastsScreen.kt` refactored) |
| **Smart Audio, AI, Flow & Wrapped** | `:app`, `:core`, `:playback` | `BeatTracker` (verified `CPJKU/beat_this` or clean-room fallback), `LoopMix`, `VersionAudioAligner`, `RecommendationEngine`, `LocalTasteScoring`, FlowNeuroengine (optional endpoint), Weather context, MusicFlow, Stats & Wrapped |
| **Storage, Downloads & Export** | `:core`, `:app` | `ExoDownloadService`, `DownloadUtil`, `AudioExportService` (replace retired `ffmpeg-kit-audio`), local media scanner, SAF/storage permissions, backup/restore |
| **Widgets, Notifications & Updater** | `:app` | `RevengeWidgetManager` + 10 home-screen widgets + recognizer widget, app shortcuts, `PendingIntent.FLAG_IMMUTABLE` audit, `exported` component security, GitHub release updater |
