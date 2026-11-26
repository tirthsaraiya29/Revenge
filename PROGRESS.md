# Revenge rebuild — durable progress log

Updated after each milestone. Read this first when resuming.

## Mission

Independent, GPL-3 reconstruction of the Revenge Android music player in this
directory. No Echo/Metrolist/vivimusic implementation is carried forward,
regardless of licence. `../Revenge-source` is a read-only behavioural reference
for M2 specification writers only; implementation workers never read it or the
baseline `src/` of a REIMPLEMENT file.

## Binding documents

- `docs/provenance/MASTER_PROVENANCE_REPORT.md` — audit verdict and census.
- `docs/provenance/FILE_BY_FILE_INVENTORY.md` — per-file disposition (1,949 rows).
- `docs/provenance/REIMPLEMENTATION_ROADMAP.md` — waves 0–5.
- `docs/provenance/ARCHITECTURE_CONTRACTS.md` — module/persistence contracts.
- `docs/provenance/VALIDATION_AND_RELEASE_GATES.md` — M2–M6 gates.
- `docs/provenance/M2_GATE_DECISIONS.md` — all 119 BLOCKED dispositions (RESOLVED).
- `docs/specs/` — clean-room specifications for implementation workers.

## Current milestone

- Phase 0 baseline freeze: DONE (`audit/baseline-11ae6207` = `11ae6207`).
- Phase 1 provenance audit: DONE (8 deliverables).
- **M2 BLOCKED gate: RESOLVED** (119/119 recorded in `M2_GATE_DECISIONS.md`).
- **Wave 0 foundation remediation: DONE** (see below).
- **Build and CI: GREEN at `f974ce8`** — GitHub Actions CI (full unit test
  suite + FOSS universal debug APK upload), Semgrep, CodeQL and Gradle
  dependency submission all pass. Locally the same commit passes
  `./gradlew test` and `:app:assembleUniversalFossDebug`; the `gms` flavor also
  compiles.
- **Waves 1–3 and Wave 5 clean-room modules: implemented** (`:domain`,
  `:metadata`, `:dsp-core`, `:core`, `:playback`, `:innertube`, `:lyrics`,
  `:audio-dsp`, `:usbaudio`, `:canvas`, `:applecanvas`, `:artistvideo`), with
  specs in `docs/specs/` and independent review fixes applied.
- **Wave 4 (app shell / design system / screens): NOT STARTED as a dedicated
  pass.** Existing app UI compiles and its tests pass, but the Wave 4
  design-system spec (`docs/specs/design-system.md`) still needs to be applied
  screen by screen and reviewed.
- Known review residuals still to close: legacy `com.revenge.music.playback.*`
  namespace migration, Listen Together typed protobuf mapping and outbound
  player/queue/volume/heartbeat wiring, and the playback test `android.net.Uri`
  stub.

## Wave 0 — completed and verified

Build/config:
- `app/build.gradle.kts`: removed `play-services-cronet` and cronet branch;
  added `-PrevengeVersionCode` / `-PrevengeVersionName`; split-ABI flavors gated
  behind `-PallAbis`; removed dead `ndkVersion`.
- `app/src/foss/AndroidManifest.xml`: strips `ReconnectionService`.
- `:lyrics`: replaced `org.json`/JSON-License dependency with
  `kotlinx.serialization.json` across translation services.
- `release.yml`: tag input used for artifact naming; NDK/CMake install dropped.
- `autoeq_index.json`: 24 corrupted `Revenge` substitutions restored to upstream
  Echo names; valid JSON.
- Dead `Puter` provider removed from settings and strings.
- Dead HTTP/3 (Cronet) toggle and preference key removed.

Assets:
- Deleted 418 paths: all `REMOVE` (228 launcher PNGs, `vibrafp/`,
  `google_sans_flex.ttf`) and all `BLOCKED` (ONNX models, celebrity/personal/
  brand rasters, Lottie).
- Added clean-room generic vectors (`ic_headphones_generic`,
  `ic_speaker_generic`, `ic_service_generic`, `ic_artwork_placeholder`) and
  repointed every reference.
- Icon picker trimmed to Default + Static; obsolete activity aliases removed.
- `Type.kt` rebound to verified OFL-1.1 `sans_flex.ttf` v3.007; false OFL
  comment removed from `speed_*.xml`.
- Added `THIRD_PARTY_NOTICES.md` and `licenses/OFL-1.1.txt`.
- `README.md`/`docs/BUILDING.md`/`docs/LICENSING.md` corrected (no bit-perfect
  USB claim, no NDK/CMake requirement, no `org.json`, no Cronet).

Verified:
- `./gradlew projects` PASS (21 modules).
- `:app:compileUniversalFossDebugKotlin` + `:lyrics:compileDebugKotlin` PASS.
- Zero BLOCKED paths remain; zero stale resource references.

## Next steps

1. Finish Wave 1 specs/implementations; run `:domain:test :metadata:test
   :dsp-core:test` and full `./gradlew test`.
2. Wave 2 — persistence, USB, DSP, playback (largest wave).
3. Wave 3 — InnerTube + seven lyric providers + `:lyrics`.
4. Wave 4 — app shell, Compose design system, core screens.
5. Wave 5 — advanced capabilities, integrations, widgets, updater.
6. E2E tracks Tiers 1–4 in parallel; M6 release gates.

## Known constraints / risks

- Subagents must be launched with `model: 'sonnet'` (provider guardrail).
- `ffmpeg-kit-audio:6.0-2` is retired upstream; replacement (Media3 Transformer
  or maintained fork) is scheduled for Wave 2. Removing it now would break
  export without a replacement.
- Beat tracking has no bundled model until an MIT re-export is verified.
- The GMS flavor still bundles proprietary Firebase/Location/Cast; GMS APKs
  are not distributable as GPL-3 binaries. FOSS is the release artifact.
- Similarity gate: automated scan vs Echo-Music/Revenge-source must be below
  threshold at M6; per-module reviewer step is part of the clean-room workflow.

## Verification log

- 2026-11-26 — Wave 0: projects PASS; app+lyrics compile PASS.
