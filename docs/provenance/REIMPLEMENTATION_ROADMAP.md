# Reimplementation Roadmap (Phases 2–6 / Waves 0–5)

**Baseline:** `11ae62071f96db3b70557867ab1254dbcc5b125e` (`1,949` tracked files)
**Objective:** Execute M2–M6 to replace every Echo-derived implementation (`1,112` `REIMPLEMENT`), remediate `138` `REFACTOR` configs/files, deploy `259` verified third-party replacements, remove `252` dead/proprietary/meme files, resolve `119` `BLOCKED` gates, and preserve `69` verified `RETAIN` items — with **zero silent feature removal across F4–F16**.

---

## 1. Strict Clean-Room Separation Protocol (R2)

1. **Provenance Auditor (M1, complete):** Produces `docs/provenance/` and freezes `audit/baseline-11ae6207`.
2. **Specification Writers (M2):** Read baseline interfaces/behavior and produce clean-room functional specifications, contracts, edge cases, and acceptance tests under `docs/specs/` without exposing source code.
3. **Implementation Workers (M3–M5):** Receive **ONLY** M2 clean-room specifications (`docs/specs/`, `ARCHITECTURE_CONTRACTS.md`) and public API documentation. **STRICTLY PROHIBITED** from reading `C:\Users\tirth\Downloads\Projects\Revenge-source`, `C:\Users\tirth\Downloads\Projects\Echo-Music`, or original/`REIMPLEMENT`/`BLOCKED` baseline files.
4. **Test Engineers (E2E Track & M3–M5):** Write deterministic requirement-driven unit, integration, error-path, and Tiers 1–4 E2E tests.
5. **Independent Reviewer & Challenger (M2–M6):** Verify functionality, zero silent feature removal, license/notice completeness, and automated similarity against `Echo-Music` and `Revenge-source` without self-approval.

---

## 2. `BLOCKED` Gate Resolution Plan (`119` Items — M2 Gate Before M3–M5 Consumption)

| `BLOCKED` Group | Count | Paths / Scope | Required M2 Gate Decision |
|---|---:|---|---|
| **Unverified ONNX Models** | 2 | `beat_this_final0_q8.onnx` (23.14 MB), `vocals_umxhq_int8.onnx` (9.05 MB) | Approve `REMOVE` for `vocals_umxhq_int8.onnx` (0 code refs); re-export `beat_this` directly from `CPJKU/beat_this` (MIT) with checksum + notice or replace BeatTracker algorithm |
| **Celebrity Likeness Rasters** | 86 | `billie_eilish_icon*`, `ic_launcher_sabrina*`, `ic_launcher_sabrina2*`, `ic_launcher_sadie*`, `ic_launcher_millie*`, `weeknd.webp` | Approve `REMOVE` + strip corresponding `activity-alias` entries in `AndroidManifest.xml` & settings picker |
| **Personal / Apple / UPI Rasters** | 12 | `dev.jpg`, `collab.jpg`, `birthday_image.png`, `ai_pfp.png`, `songdna.png`, `tast.png`, `preview_artwork.webp`, `apple_*.png`, `speaker_applemusic.png`, `upi_new.png` | Approve `REMOVE` (personal/trademark) or clean-room vector replacement for neutral UI slots |
| **Third-Party Brand Vector Logos** | 18 | `apple_*`, `ic_google`, `ic_jellyfin`, `ic_lastfm`, `ic_listenbrainz`, `ic_navidrome`, `ic_spotify`, `ic_telegram*`, `telegram`, `spatial_tracking_apple`, etc. | Verify official brand-guideline permission or replace with generic Material Symbols / text badges |
| **Unverified Lottie JSON** | 1 | `app/src/main/res/raw/setting.json` | Approve `REMOVE` and replace with verified Apache-2.0/MIT animation or Material icon |
| **TOTAL `BLOCKED`** | **119** | **100% halted until M2 sign-off** | **Zero `BLOCKED` assets allowed in shipped APK** |

---

## 3. Phased Execution Roadmap (Waves 0–5)

| Wave | Target Milestone | Scope & Key Deliverables | Mapped Features |
|---|---|---|---|
| **Wave 0** | **M3 (Foundation Setup)** | Fix `app/build.gradle.kts` (`play-services-cronet` -> `gmsImplementation` or remove; apply `app_build_gradle_kts.diff` `-PallAbis`/version flags; replace `ffmpeg-kit-audio` plan), fix `app/src/foss/AndroidManifest.xml` (`ReconnectionService` `tools:node="remove"`), replace `lyrics` `org.json` with `kotlinx.serialization.json`, delete `google_sans_flex.ttf` + `vibrafp/` (23 files) + `228` Echo/meme PNGs + approved `BLOCKED` assets, install clean OFL fonts + `OFL.txt` + `THIRD_PARTY_NOTICES.md`, clean `AutoEq` (MIT) fixing 11 entries, clean `astring.js`/`meriyah.js`/`yt.solver.core.js`, `Kyant0/backdrop` Maven dep, Material Symbols icons, apply `release.yml` tag fix + honest `docs/LICENSING.md`/`BUILDING.md`/`README.md` | F4 |
| **Wave 1** | **M3 (Pure-JVM Core)** | Clean-room specs & reimplementation of `:domain`, `:metadata`, `:dsp-core` under strict pure-JVM isolation (zero Android/network deps), retaining/migrating `<50%` refactored utilities onto clean interfaces | F3, F5, F7 |
| **Wave 2** | **M3–M4 (Persistence, USB & Audio Core)** | Clean-room `:core` (module package `com.revenge.core.*`, Room v48 + migrations or validated schema baseline, DataStore keys, repos, backup/restore, downloads, export), `:usbaudio` (honest AudioTrack USB routing), `:audio-dsp` (`DspControllerImpl` rejecting `18 M` superficial diff), `:playback` (`RoutingAudioSink` rejecting `18 M` diff, Media3 `MusicService`, queues, gapless, crossfade, cutoff guard) | F5, F7 |
| **Wave 3** | **M4 (Streaming & Lyrics)** | Clean-room `:innertube` (rejecting all 9 `18 M` superficial diffs in `NetworkConfig` + `pages/*.kt`), clean `po_token.html`/cipher/SABR/QuickJS solvers, `:lrclib`, `:kugou`, `:betterlyrics`, `:simpmusic`, `:youlyplus`, `:paxsenixlyrics`, `:unison`, `:lyrics` (`AiPrompts.kt` rejecting `18 M` diff; remove dead `Puter` branches) | F6, F8 |
| **Wave 4** | **M4 (App Shell, Compose Design System & Core UI)** | Clean-room `:app` entry (`App`, `MainActivity`, `CrashActivity`, DPI scaler), keep `Design.kt` (`RETAIN`), refactor `RevengeComponents.kt` & `PodcastsScreen.kt` onto clean icons/ViewModels, replace `FloatingTabBar.kt` cleanly, rebind `Type.kt` to `sans_flex.ttf`, clean-room reimplement `RevengeLyrics.kt`, `SettingsDialog.kt`, `updaterscreen.kt`, `CommitItem.kt`, `DownloadManagerUtils.kt`, player/library/search/settings screens, `speed_*.xml`, `8` UI PNGs, `66` shape/layer XMLs | F9, F14 |
| **Wave 5** | **M5 (Advanced Capabilities & Integrations)** | Clean-room `:canvas`, `:applecanvas`, `:artistvideo`, `:shazamkit` (`Shazam.kt` removing hardcoded Dalvik UAs) + Find UI & mic cleanup, Spotify import, Jellyfin, Navidrome/Subsonic, clean-room `listentogether.proto` & sync rooms, Cast (`gms` vs `foss`) & Android Auto, Last.fm/ListenBrainz, Podcasts, Smart Audio (`BeatTracker` with verified model or clean-room fallback), MusicFlow/Wrapped, `RevengeWidgetManager.kt` & 10 home-screen widgets + recognizer widget | F10, F11, F12, F13, F14 |
| **Validation** | **M6 (Release Gates)** | Tiers 1–4 E2E pass (`F15`), Tier-5 adversarial hardening (`F16`), FOSS/GMS debug+release builds, Spotless/Lint/Semgrep, license/FOSS binary audit, automated similarity scan (`<threshold` vs Echo-Music & `Revenge-source`), Independent Reviewer final sign-off & uncertainty disclosure | F15, F16 |

---

## 4. File Count Reconciliation by Target Wave (`1,949` Total)

| Target Wave / Gate | `REIMPLEMENT` | `REPLACE` | `REMOVE` | `REFACTOR` | `BLOCKED` | `RETAIN` | Total |
|---|---:|---:|---:|---:|---:|---:|---:|
| `Retained` | 0 | 0 | 0 | 0 | 0 | 21 | 21 |
| `Wave 0` | 0 | 38 | 252 | 15 | 0 | 0 | 305 |
| `Wave 0 (Build, CI, Docs & Notices)` | 0 | 0 | 0 | 20 | 0 | 0 | 20 |
| `Wave 0 / M2 Gate` | 0 | 0 | 0 | 0 | 119 | 0 | 119 |
| `Wave 0 / Wave 3` | 0 | 2 | 0 | 0 | 0 | 0 | 2 |
| `Wave 0 / Wave 4` | 0 | 218 | 0 | 0 | 0 | 0 | 218 |
| `Wave 1 (Pure-JVM Core: domain, metadata, dsp-core)` | 18 | 0 | 0 | 11 | 0 | 0 | 29 |
| `Wave 2` | 0 | 0 | 0 | 0 | 0 | 48 | 48 |
| `Wave 2 (Persistence, Audio DSP & Playback Core)` | 127 | 0 | 0 | 22 | 0 | 0 | 149 |
| `Wave 3` | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `Wave 3 (Discovery & Lyrics Modules)` | 102 | 0 | 0 | 54 | 0 | 0 | 156 |
| `Wave 4` | 14 | 1 | 0 | 2 | 0 | 0 | 17 |
| `Wave 4 (App Resources)` | 3 | 0 | 0 | 0 | 0 | 0 | 3 |
| `Wave 4 / Wave 5` | 111 | 0 | 0 | 0 | 0 | 0 | 111 |
| `Wave 4/5 (App Foundation, Compose UI & Integrations)` | 727 | 0 | 0 | 0 | 0 | 0 | 727 |
| `Wave 5` | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| `Wave 5 (Advanced Capabilities & Integrations)` | 8 | 0 | 0 | 14 | 0 | 0 | 22 |
| **TOTAL** | **1112** | **259** | **252** | **138** | **119** | **69** | **1949** |

---

## 5. Carryover from M1.1 Archived Diffs & Zero-Silent-Removal Feature Checklist

- **Carry Into Wave 0:** `app_build_gradle_kts.diff` (`-PrevengeVersionCode`, `-PrevengeVersionName`, `-PallAbis`), `.github/workflows/release.yml` (`inputs.tag`), `README.md` (honest AudioTrack USB routing description), `docs/LICENSING.md` (ONNX warning, corrected for `meriyah.js`, `org.json`, `vocals_umxhq_int8.onnx`).
- **Reject Under R2:** All `12` Kotlin diffs in `revenge_wasted_18M.diff` (`DspControllerImpl.kt`, `NetworkConfig.kt`, 8 `innertube/pages/*.kt`, `AiPrompts.kt`, `RoutingAudioSink.kt`) — scheduled for clean-room `REIMPLEMENT`.
- **Feature Preservation Invariant (F4–F16):** Every user-facing capability in `PROJECT.md` (streaming, background/gapless/crossfade playback, offline downloads/export, parametric EQ + AutoEQ, USB DAC routing, multi-provider lyrics/translation, canvas/artist video, audio recognition, Spotify/Jellyfin/Navidrome/Listen Together/Cast/Auto/Last.fm/ListenBrainz/Podcasts/Wrapped, 10+1 widgets, updater) must ship via clean-room implementation or verified third-party component. Only dead non-functional controls (`Puter` AI dead branch, unused `vocals_umxhq_int8.onnx`, unbuilt `vibrafp/`, proprietary/celebrity launcher icon aliases) may be removed with explicit M2/M3 documentation.
