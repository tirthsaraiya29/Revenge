# Revenge rebuild — durable progress log

Updated after each milestone. Read this first when resuming. A tool-agnostic
handoff prompt (for Antigravity or any successor) lives at
`C:\Users\tirth\Downloads\REVENGE_CONTINUATION.md`.

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

## Current milestone (2026-11-27)

- Phase 0 baseline freeze: DONE (`audit/baseline-11ae6207` = `11ae6207`).
- Phase 1 provenance audit: DONE (8 deliverables).
- **M2 BLOCKED gate: RESOLVED** (119/119 recorded in `M2_GATE_DECISIONS.md`).
- **Wave 0 foundation remediation: DONE** (see below).
- **Waves 1–3 and Wave 5 modules: implemented** (`:domain`, `:metadata`,
  `:dsp-core`, `:core`, `:playback`, `:innertube`, `:lyrics`, `:audio-dsp`,
  `:usbaudio`, `:canvas`, `:applecanvas`, `:artistvideo`), with specs in
  `docs/specs/` and review fixes applied.
- **Split-package elimination: DONE** (`40d6f0c`) — `:core` uses
  `com.revenge.core.*`, `:lyrics` uses `com.revenge.lyrics.*`, `:playback`
  uses only `com.revenge.playback.*` (duplicate tree deleted), and the
  `android.net.Uri` test stub is gone.
- **Listen Together: DONE** (`e384433`) — typed protobuf payloads, JSON
  fallback for chat, Player.Listener/queue/volume/heartbeat wiring, tests.
- **Last reported green integration commit: `d995781`.** Its commit record
  states that FOSS + GMS app compilation and unit tests passed after integrating
  the utility rewrites. The earlier `0e0e5c9` snapshot did report 534 compile
  errors, but that is historical and must not be presented as current HEAD.
- **Wave 4 (app clean-room rewrite): IN PROGRESS.**
  - Clean-room foundation: `App.kt`, `MainActivity.kt`, `CrashActivity.kt`,
    `ui/theme`, `dpi`, and `utils/CrashHandler.kt`.
  - Utility rewrite and integration were subsequently committed; see
    `d995781` and its verification note below.
  - November 27 navigation, settings-layout, floating-toolbar, and legal
    notice changes are committed on `main`, but have not been built or tested
    in this session.
- **Current verification boundary:** the last commit with a recorded successful
  FOSS + GMS compile and unit-test result is `d995781`. Commits after it,
  including the release-navigation guard and latest UI repairs, remain
  unverified here. Do not claim the current HEAD compiles until verification
  is run.
- **Remaining Wave 4 scope:** ~390 of the 464 app Kotlin files still carry a
  `REIMPLEMENT` disposition in `FILE_BY_FILE_INVENTORY.md` — dominated by
  `ui/` (235), `viewmodels/` (36), `spotify/` (20), `playback/` (18),
  `widget/` (15), `updates/` (15), plus smaller packages — and the Wave 4
  resources (`REPLACE`, 218 PNG/XML assets).

## Immediate next steps (handoff order)

1. **Verify the current UI repair milestone:** run
   `:app:compileUniversalFossDebugKotlin`, `:app:compileUniversalGmsDebugKotlin`,
   and focused UI/navigation tests. The 534-error report belongs to the
   superseded `0e0e5c9` snapshot, not current HEAD.
2. Run the full verification set and confirm CI + Semgrep + CodeQL green before
   publishing a release artifact.
3. Continue Wave 4 cluster by cluster (bottom-up): `viewmodels`, `playback`,
   `ai`, `cloud`, `spotify`/`spotifyimport`, `data`, `generate`,
   `musicflow`, `recognition`, `podcast`, `widget`, `updates`, then the 235
   `ui/` files (components → menus → screens), each followed by compile/test.
4. Replace the Wave 4 resources marked `REPLACE` and then close E2E Tiers 1–4
   and M6 gates.

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

See "Immediate next steps (handoff order)" above. Historical plan retained in
`docs/provenance/REIMPLEMENTATION_ROADMAP.md`; the only remaining wave is
Wave 4 (app/UI) plus E2E Tiers 1–4 and M6.

## Repo/CI operational notes

- Last verified green commit: `2863065`. Green CI references: `40d6f0c`,
  `e384433` (CI, Semgrep, CodeQL, dependency submission all success).
- CodeQL uses `build-mode: manual` and compiles `:app` — buildless mode cannot
  extract Kotlin (fixed in `2fe352b`).
- CI generates `~/.android/debug.keystore` before assembling (`f974ce8`);
  without it `validateSigningDebug` fails on fresh runners.
- Semgrep: 10 findings were annotated with justified `// nosemgrep` and the
  cleartext rule now excludes XML parser feature URIs.
- Local assemble: Android Studio keeps a handle on
  `app/build/outputs/apk/universalFoss/debug`. Workaround used:
  `./gradlew :app:assembleUniversalFossDebug -I <init script that redirects
  :app layout.buildDirectory>`.
- Watcher commits use `[skip ci]` for unverified snapshots; never stage
  `local.properties`, keystores, `.env`, `google-services.json`; never
  force-push or amend. `.cleanroom_tmp/` is gitignored (agent scratch).

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
- 2026-11-26 — Full suite green (`f974ce8`): 21 modules, CI + assemble + Semgrep
  + CodeQL + dependency submission PASS.
- 2026-11-27 — `40d6f0c`: namespace migration; local `test` + FOSS assemble +
  GMS compile PASS; CI/Semgrep/CodeQL PASS.
- 2026-11-27 — `e384433`: Listen Together typed protocol; app Listen Together
  tests (forced rerun) + full `test` PASS; CI/Semgrep/CodeQL PASS.
- 2026-11-27 — `2863065`: Wave 4 foundation (entry/theme/DPI) verified: FOSS +
  GMS compile, full `test`, FOSS assemble PASS.
- 2026-11-27 — `0e0e5c9`: intermediate utility rewrite snapshot; compile failed
  with 534 errors. Superseded by the integration recorded in `d995781`.
- 2026-11-27 — `d995781`: commit records FOSS + GMS compile and unit tests PASS.
- 2026-11-27 — UI repair commits after `d995781`: floating navigation wired
  into the shell, SettingsScreen given an explicit full-screen layout, launcher
  intents corrected, bottom inset reservation adjusted, and GPL notice added
  to About. Static source edits only; no build/test claimed.
