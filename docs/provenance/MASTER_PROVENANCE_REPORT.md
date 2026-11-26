# Master Provenance Report — Revenge Android Music Player

**Baseline Commit:** `11ae62071f96db3b70557867ab1254dbcc5b125e` (`audit/baseline-11ae6207`)
**Initial Commit:** `743cbdb6aa6ffbda6e8d13cc73b4fc3f7fd68e01` (`README.md` only)
**Upstream Echo Reference:** `EchoMusicApp/Echo-Music` at `815273ddc993dad72c23a37ceff8b76de5f47183`
**Local Reference Folder:** `C:\Users\tirth\Downloads\Projects\Revenge-source` (directly edited for Revenge; 461/512 non-build files match Echo-Music)
**Total Audited Scope:** `1,949` tracked files (`79,486,355` bytes) across 21 Gradle modules + root tooling/CI/docs
**Audit Status:** Phase 0–1 Provenance Audit Complete (M1.2); Pending Independent Reviewer Sign-Off & M2 `BLOCKED` Gate Approvals

---

## 1. Executive Summary & Core Verdict

At baseline `11ae62071f96db3b70557867ab1254dbcc5b125e`, **Revenge is NOT an independent implementation.** Although `README.md`, `PROGRESS.md`, and `docs/INDEPENDENCE.md` present the repository as a clean-room rebuild (with `docs/INDEPENDENCE.md` reporting 115 of ~400 supporting-module files over 60% line overlap), forensic inspection of all `1,949` tracked files reveals:

1. **Repository-Wide Echo-Music Footprint:** `877` files (`45.0%`) are **byte-for-byte identical** (`ExactRawSHA`) to `Echo-Music` (`815273dd`), `837` (`42.9%`) match at `>=85%` normalized token Jaccard, `80` (`4.1%`) at `60–84%`, `67` (`3.4%`) at `35–59%`, `34` (`1.7%`) at `<35%`, and only `54` (`2.8%`) have no Echo match.
2. **Pre-Existing `:app` Module (`1,503` files):** Roughly `98%` of `:app` (`1,491` of `1,503` files excluding `Design.kt`, `RevengeComponents.kt`, `PodcastsScreen.kt`, `app/.gitignore`, `app/build.gradle.kts`, `app/lint.xml`, `app/proguard-rules.pro`, and `7` Revenge `"R"` brand vectors) originates directly from `Echo-Music`, `Metrolist`, `vivimusic`, vendored third-party libraries (`Kyant0/backdrop`, `FloatingTabBar`, JS solvers), or unverified bundled media assets.
3. **Supporting Modules (`446` non-app files):** `Revenge-source` itself has `461/512` (`90.0%`) files matching `Echo-Music`. Even files rebuilt in `11ae6207` under `<50%` token overlap still couple directly to Echo-derived DTOs, Room entities, and split `com.revenge.music.*` packages.
4. **R2 Clean-Room Disposition (`1,949` files):** `1,112` files require full clean-room specification & reimplementation (`REIMPLEMENT`), `259` require replacement with verified upstream third-party components & notices, `252` are dead/proprietary/meme files slated for removal (`REMOVE`), `138` require architectural/contract/config refactoring (`REFACTOR`), `119` are halted at explicit M2 approval gates (`BLOCKED`), and only `69` verified files are retained as-is (`RETAIN`).

---

## 2. Phase 0 Baseline Freeze & Verification Summary

Before auditing provenance, M1.1 reconciled the split workspace (`Revenge` contained only `app/` without `.git`; `Revenge-Wasted` contained `.git` at `11ae6207` with `app/` deleted and `18 M` uncommitted modifications) non-destructively:

- **Protected Baseline:** Branch `audit/baseline-11ae6207` and `main` in `c:\Users\tirth\Downloads\Projects\Revenge` both point strictly to `11ae62071f96db3b70557867ab1254dbcc5b125e` (`git fsck --full --no-dangling` exit `0`). `C:\Users\tirth\Downloads\Projects\Revenge-Wasted` remained 100% read-only (`0` bytes modified across `14,473` files / `286,300,233` bytes).
- **Archived Pre-Reconciliation Diffs (for M1.2 / M3):**
  - `Revenge/app/build.gradle.kts` (`app_build_gradle_kts.diff`, `+27/-22`): parameterizes `-PrevengeVersionCode` / `-PrevengeVersionName` and gates split-ABI flavors behind `-PallAbis`.
  - `Revenge-Wasted` `18 M` (`revenge_wasted_18M.diff`, `+1,192/-1,056`): `6` CI/doc updates and `12` Kotlin edits in `:audio-dsp`, `:innertube`, `:lyrics`, and `:playback`.
- **Baseline Build & Test Execution (JDK: Eclipse Adoptium Temurin `21.0.7+6-LTS`):**
  - `./gradlew projects --console=plain --no-daemon`: **PASS** (`exit=0`, 21 modules).
  - `./gradlew test --no-build-cache --console=plain --no-daemon`: **PASS** (`exit=0`, `738` tasks).
  - **Unit Test Census (`<module>/build/test-results/*/TEST-*.xml`):** **`304` XML suites, `1,547` tests, `1,547` passed, `0` failed, `0` errors, `0` skipped** (`250` suites / `1,190` tests across 10 `:app` debug ABI×variant combos + `54` suites / `357` tests across the 20 library modules; `0` `androidTest` files).
  - **APK Packaging & Host Lock Note:** `:app:assembleUniversalFossDebug` compiles and dexes cleanly at `11ae6207`; default `app/build/outputs/apk/universalFoss/debug` hits a pre-existing host Win32 error 32 file lock, bypassed read-only via `--init-script app_build_local.init.gradle.kts` (outputting valid debug-signed `207,181,026`-byte `app-universal-foss-debug.apk`).

---

## 3. Audit Methodology (R1 & R2 Compliance)

1. **Git History Forensics:** Verified `743cbdb6` (`Initial commit`, 1 file `README.md`) -> `11ae6207` (`1,949` files, `+327,420/-1`). Internal Git history provides zero pre-`11ae6207` authorship provenance, requiring full external comparison.
2. **Exact & Normalized Blob Hashing:** Computed raw Git blob SHA-1, LF-normalized Git blob SHA-1, and SHA-256 for all `1,949` tracked files against all `2,603` blobs in `Echo-Music` (`815273dd`) and all `512` non-build files in `Revenge-source`.
3. **Global Cross-File Structural Similarity:** Computed multiset token Jaccard, normalized line overlap (stripping comments/blank lines/`package`/`import` and normalizing `com.revenge.*`/`com.music.*`/`echo.music.*` and `Revenge`/`Echo`/`EchoMusic`), and 4-token shingle Jaccard using a global inverted index to defeat path/file renames.
4. **Substantive R2 Inspection & Binary Forensics:** Manually inspected all low-similarity `:app` files, `<50%` supporting-module files, the `18 M` diffs, embedded copyright/SPDX headers (`138` files), Gradle build scripts (`23` `.kts` + `libs.versions.toml`), manifests (`22`), CI workflows (`4`), ONNX `ModelProto` protobuf graphs (`2`), OpenType/TTF `name` tables (`5`), JS/HTML assets (`4`), `vibrafp` C++/FFTW archive (`23`), `334` raster images, `360` drawable XMLs, and `53` JSON/Protobuf files.
5. **Strict R2 Classification Rules:** Applied the exact 6-value enum (`RETAIN`, `REFACTOR`, `REIMPLEMENT`, `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT`, `REMOVE`, `BLOCKED`) without trusting package names, file names, similarity thresholds alone, or self-reported `docs/INDEPENDENCE.md`.

---

## 4. Reconciled Classification & Similarity Census (`1,949` Tracked Files)

### 4.1 Classification Summary (All `1,949` Tracked Files)

| Classification | File Count | Share | Scope Summary |
|---|---:|---:|---|
| `REIMPLEMENT` | **1,112** | 57.1% | Echo/Metrolist/vivimusic `:app` Kotlin/XML/tests (incl. 6 renamed Echo Kotlin files), Echo-derived supporting module code (`>=50%` or artifact-tainted like `Shazam.kt`), `12` superficial `18 M` diffs, `51` custom/widget/speed vectors, `66` shape/layer XMLs, `8` UI PNGs, `po_token.html`, `listentogether.proto` |
| `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` | **259** | 13.3% | `218` Material/UI icons (Material Symbols Apache-2.0), `Kyant0/backdrop` v2.0.0 (`30`), `FloatingTabBar.kt` (`1`), `4` OFL-1.1 TTF fonts, `3` JS solver/parser bundles (`astring.js`, `meriyah.js`, `yt.solver.core.js`), `autoeq_index.json` (MIT), `player_configs.json` & `player_dates.json` (`2`) |
| `REMOVE` | **252** | 12.9% | `228` Echo/meme launcher PNGs (incl. `17` `revenge_cat_icon*` == `echo_cat_icon*`), `23` unbuilt `vibrafp/` C++/FFTW files (`4.24` MB), `1` proprietary `google_sans_flex.ttf` (`4.61` MB) |
| `REFACTOR` | **138** | 7.1% | `68` supporting-module `<50%` rebuilt Kotlin files & tests, `2` Revenge-authored Echo-coupled `:app` files (`RevengeComponents.kt`, `PodcastsScreen.kt`), `23` `.kts` build scripts, `17` XMLs (`14` module manifests, `app/src/main/AndroidManifest.xml`, `app/src/foss/AndroidManifest.xml`, `app/lint.xml`), `18` docs/fastlane/HTML files, `10` CI/GitHub/ProGuard/TOML/hook/properties files |
| `BLOCKED` | **119** | 6.1% | `86` celebrity likeness rasters, `12` personal/Apple/UPI rasters, `18` third-party brand vector logos, `2` ONNX models (`beat_this_final0_q8.onnx`, `vocals_umxhq_int8.onnx`), `1` unverified Lottie `setting.json` |
| `RETAIN` | **69** | 3.5% | `48` Room v1–v48 schema JSONs (with M2/M3 migration note), `7` Revenge `"R"` brand vectors (`EchoSHA=False`), `Design.kt` (`1`), `13` standard tooling/CI/policy files (`LICENSE`, `gradlew*`, `gradle-wrapper.*`, `.editorconfig`, `.gitignore`, `.semgrep/rules.yml`, `codeql.yml`, `semgrep.yml`, `docs/.nojekyll`, `app/.gitignore`, `gradle-daemon-jvm.properties`) |
| **TOTAL** | **1,949** | **100.0%** | **Exact 1-to-1 match with `git ls-files` at `11ae62071f96db3b70557867ab1254dbcc5b125e`** |

### 4.2 Module-by-Module × Classification Matrix

| Module / Root Dir | Total | `REIMPLEMENT` | `REPLACE` | `REMOVE` | `REFACTOR` | `BLOCKED` | `RETAIN` |
|---|---:|---:|---:|---:|---:|---:|---:|
| `app` | 1503 | 857 | 259 | 252 | 7 | 119 | 9 |
| `core` | 132 | 79 | 0 | 0 | 5 | 0 | 48 |
| `innertube` | 84 | 70 | 0 | 0 | 14 | 0 | 0 |
| `playback` | 34 | 30 | 0 | 0 | 4 | 0 | 0 |
| `lyrics` | 28 | 19 | 0 | 0 | 9 | 0 | 0 |
| `usbaudio` | 18 | 9 | 0 | 0 | 9 | 0 | 0 |
| `(root)` | 15 | 0 | 0 | 0 | 10 | 0 | 5 |
| `audio-dsp` | 13 | 9 | 0 | 0 | 4 | 0 | 0 |
| `domain` | 10 | 6 | 0 | 0 | 4 | 0 | 0 |
| `metadata` | 10 | 7 | 0 | 0 | 3 | 0 | 0 |
| `.github` | 9 | 0 | 0 | 0 | 7 | 0 | 2 |
| `dsp-core` | 9 | 5 | 0 | 0 | 4 | 0 | 0 |
| `canvas` | 8 | 4 | 0 | 0 | 4 | 0 | 0 |
| `docs` | 8 | 0 | 0 | 0 | 7 | 0 | 1 |
| `kugou` | 8 | 5 | 0 | 0 | 3 | 0 | 0 |
| `youlyplus` | 8 | 1 | 0 | 0 | 7 | 0 | 0 |
| `simpmusic` | 7 | 1 | 0 | 0 | 6 | 0 | 0 |
| `artistvideo` | 6 | 2 | 0 | 0 | 4 | 0 | 0 |
| `betterlyrics` | 6 | 2 | 0 | 0 | 4 | 0 | 0 |
| `lrclib` | 6 | 1 | 0 | 0 | 5 | 0 | 0 |
| `paxsenixlyrics` | 6 | 1 | 0 | 0 | 5 | 0 | 0 |
| `shazamkit` | 5 | 2 | 0 | 0 | 3 | 0 | 0 |
| `gradle` | 4 | 0 | 0 | 0 | 1 | 0 | 3 |
| `unison` | 4 | 2 | 0 | 0 | 2 | 0 | 0 |
| `applecanvas` | 3 | 0 | 0 | 0 | 3 | 0 | 0 |
| `fastlane` | 3 | 0 | 0 | 0 | 3 | 0 | 0 |
| `.githooks` | 1 | 0 | 0 | 0 | 1 | 0 | 0 |
| `.semgrep` | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| **TOTAL** | **1949** | **1112** | **259** | **252** | **138** | **119** | **69** |

### 4.3 Echo-Music (`815273dd`) Similarity Distribution Across All `1,949` Files

| Module / Root Dir | Total | ExactRawSHA | >=85% Tok | 60-84% Tok | 35-59% Tok | <35% Tok | NoMatch |
|---|---:|---:|---:|---:|---:|---:|---:|
| `app` | 1503 | 821 | 645 | 15 | 13 | 1 | 8 |
| `core` | 132 | 48 | 63 | 17 | 1 | 0 | 3 |
| `innertube` | 84 | 0 | 57 | 13 | 7 | 3 | 4 |
| `playback` | 34 | 0 | 10 | 15 | 7 | 2 | 0 |
| `lyrics` | 28 | 2 | 10 | 5 | 5 | 2 | 4 |
| `usbaudio` | 18 | 0 | 5 | 3 | 4 | 5 | 1 |
| `(root)` | 15 | 3 | 2 | 0 | 2 | 5 | 3 |
| `audio-dsp` | 13 | 0 | 4 | 3 | 5 | 0 | 1 |
| `domain` | 10 | 0 | 4 | 0 | 5 | 1 | 0 |
| `metadata` | 10 | 1 | 3 | 2 | 4 | 0 | 0 |
| `.github` | 9 | 0 | 0 | 0 | 0 | 5 | 4 |
| `dsp-core` | 9 | 0 | 3 | 2 | 1 | 2 | 1 |
| `canvas` | 8 | 0 | 1 | 1 | 3 | 2 | 1 |
| `docs` | 8 | 1 | 0 | 0 | 0 | 1 | 6 |
| `kugou` | 8 | 0 | 6 | 1 | 0 | 0 | 1 |
| `youlyplus` | 8 | 0 | 3 | 0 | 1 | 0 | 4 |
| `simpmusic` | 7 | 0 | 3 | 0 | 1 | 0 | 3 |
| `artistvideo` | 6 | 0 | 2 | 2 | 0 | 0 | 2 |
| `betterlyrics` | 6 | 0 | 3 | 1 | 1 | 0 | 1 |
| `lrclib` | 6 | 0 | 2 | 0 | 2 | 0 | 2 |
| `paxsenixlyrics` | 6 | 0 | 3 | 0 | 0 | 2 | 1 |
| `shazamkit` | 5 | 0 | 3 | 0 | 1 | 0 | 1 |
| `gradle` | 4 | 1 | 2 | 0 | 1 | 0 | 0 |
| `unison` | 4 | 0 | 2 | 0 | 1 | 0 | 1 |
| `applecanvas` | 3 | 0 | 1 | 0 | 1 | 0 | 1 |
| `fastlane` | 3 | 0 | 0 | 0 | 1 | 2 | 0 |
| `.githooks` | 1 | 0 | 0 | 0 | 0 | 1 | 0 |
| `.semgrep` | 1 | 0 | 0 | 0 | 0 | 0 | 1 |
| **TOTAL** | **1949** | **877** | **837** | **80** | **67** | **34** | **54** |

---

## 5. Critical Provenance, Licensing & Configuration Discoveries

1. **6 Renamed Echo/Metrolist Kotlin Files in `:app` (`REIMPLEMENT`):** Survey-level path matching missed 6 files whose filenames were changed from Echo/Metrolist originals: `RevengeLyrics.kt` (`EchoMusicLyrics.kt`, `tok=0.994`), `RevengeWidgetManager.kt` (`EchoMusicWidgetManager.kt`, `tok=0.996`, Metrolist GPL-3.0 header), `CommitItem.kt` (`commiitem.kt`, `tok=1.000`), `DownloadManagerUtils.kt` (`downloadmanager.kt`, `tok=0.980`), `updaterscreen.kt` (`echomusicupdater.kt`, `tok=0.963`), and `SettingsDialog.kt` (`SettingDialoge.kt`, `tok=0.434`, identical sheet/checks/grouping).
2. **`Revenge-Wasted` `18 M` Superficial Kotlin Diffs Rejected Under R2 (`REIMPLEMENT`):** The 12 Kotlin modifications in `revenge_wasted_18M.diff` (`DspControllerImpl.kt`, `NetworkConfig.kt`, 8 `innertube/pages/*.kt`, `AiPrompts.kt`, `RoutingAudioSink.kt`) extract trivial private helpers, rename local variables, flatten fields, or reword KDoc/prompt headings while keeping identical control flow and JSON traversal. All 12 are classified `REIMPLEMENT`.
3. **Archived `app/build.gradle.kts` Diff (`REFACTOR`):** `app_build_gradle_kts.diff` (`+27/-22`) parameterizes `revengeVersionCode`/`revengeVersionName` and gates split ABIs behind `-PallAbis`. Adopt in Wave 0.
4. **CRITICAL — Proprietary `google_sans_flex.ttf` v2.003 (`REMOVE`):** TTF `name` table explicitly forbids open-source redistribution (`"Google offers many fonts on open source terms. Google Sans Flex is not one of them."`), yet `Type.kt` binds `GoogleSansFontFamily` to `R.font.google_sans_flex`, and 6 `speed_*.xml` drawables carry a false comment claiming it is SIL OFL. Rebind `Type.kt` to verified OFL-1.1 `sans_flex.ttf` v3.007.
5. **CRITICAL — `play-services-cronet:18.0.1` FOSS Leak (`REFACTOR`):** `app/build.gradle.kts:308` declares `implementation("com.google.android.gms:play-services-cronet:18.0.1")` unconditionally instead of `gmsImplementation`, leaking proprietary Play Services into FOSS builds despite `MusicService.kt:4272` hardcoding `useCronet = false` and `docs/LICENSING.md`/`PRIVACY.md` claiming zero GMS in FOSS.
6. **HIGH — GMS Flavor GPL-3.0 Incompatibility & `ReconnectionService` Manifest Leak:** `gms` bundles proprietary Firebase, Location, and Cast binaries alongside `GPL-3.0-only` code (`NewPipeExtractor`, `PipePipeExtractor`, `innertubex-android`), prohibiting public binary distribution of GMS APKs. Additionally, `app/src/foss/AndroidManifest.xml` omits `tools:node="remove"` for `com.google.android.gms.cast.framework.ReconnectionService` (`app/src/main/AndroidManifest.xml:564-567`).
7. **HIGH — `org.json:json:20260814` Misreported in `docs/LICENSING.md`:** `lyrics/build.gradle.kts:46` uses `testImplementation(libs.json)` (GPL-incompatible JSON License "used for Good, not Evil"). `docs/LICENSING.md:43` wrongly reports it as a GPL-compatible runtime dependency. Replace with `kotlinx.serialization.json`.
8. **HIGH — `autoeq_index.json` Corrupted in 11 Entries (`REPLACE`):** Naive `Echo` -> `Revenge` string substitution mangled `Amazon Echo Buds` -> `Amazon Revenge Buds` (4 entries), `Echobox Finder X1` -> `Revengebox Finder X1` (3 entries), and `Tanchjim Echo` -> `Tanchjim Revenge` (4 entries). Replace cleanly from `jaakkopasanen/AutoEq` (MIT).
9. **HIGH — ONNX Models Lack Provenance (`BLOCKED`):** `beat_this_final0_q8.onnx` (`23.14` MB) and `vocals_umxhq_int8.onnx` (`9.05` MB) contain zero embedded license/origin metadata in `ModelProto`. Moreover, `vocals_umxhq_int8.onnx` has **0** references in the entire repository (M2 disposition: approve `REMOVE`).
10. **MEDIUM — Dead `vibrafp/` Tree & Retired `ffmpeg-kit-audio`:** `app/src/main/cpp/vibrafp/` (`23` files, `4.24` MB including GPL-2.0+ `fftw-3.3.10.tar.gz`) is never built (`externalNativeBuild` absent; `VibraSignature.kt` runs pure Kotlin). Meanwhile `com.arthenica:ffmpeg-kit-audio:6.0-2` is retired upstream with deleted binaries.
11. **MEDIUM — `334` Raster Images & `360` Drawable XMLs:** `334/334` rasters match `Echo-Music` SHA-256 (including `revenge_cat_icon*` == `echo_cat_icon*`, `86` celebrity likenesses `BLOCKED`, `12` personal/Apple/UPI rasters `BLOCKED`, `228` Echo/meme icons `REMOVE`, `8` UI PNGs `REIMPLEMENT`). Only `7` Revenge `"R"` monogram vectors (`EchoSHA=False`) are `RETAIN`; `287/294` vectors match Echo-Music.
12. **Inaccurate Project Self-Documentation:** `docs/INDEPENDENCE.md` admits 115 module files exceed 60% overlap while ignoring `:app`; `PROGRESS.md` admits `Revenge-source` behavioral derivation and pre-existing `app/`; `README.md` inaccurately claims Revenge is independent and supports bit-perfect USB PCM (`UsbAudioDriver.kt` routes via platform `AudioTrack`); `docs/BUILDING.md` cites JBR 21 and NDK/CMake steps that are unused.

---

## 6. Phase 2–6 Readiness & Open Uncertainties (R5 Disclosure)

- **Readiness:** Phase 0–1 inventory, classification, license/notice mapping, and M1.1 test baseline (`1,547/1,547`) are complete. M2 can immediately begin clean-room architecture locking, `BLOCKED` gate resolution (`119` items), and clean-room specification authoring.
- **Disclosed Uncertainties:**
  1. `beat_this_final0_q8.onnx`: Upstream `CPJKU/beat_this` is MIT, but the shipped quantized binary cannot be provenance-verified without re-exporting from upstream weights or user approval.
  2. `GMS` binary distribution: Combining Firebase/Cast/Location SDKs with GPL-3.0 extractors cannot be redistributed as prebuilt binaries without upstream GPL exceptions.
  3. `Revenge` `7` `"R"` brand vectors (`EchoSHA=False`) and `Design.kt` (`tok=0.054`): Verified non-Echo in baseline, pending formal M2 authorial attestation.
  4. Third-party protocol stability & terms (`YouTube InnerTube`, `Spotify`, `Apple Music`, `TIDAL`, `Shazam`, `ListenBrainz`, `Last.fm`, `LRCLIB`, `KuGou`, `BetterLyrics`, `SimpMusic`, `YouLyPlus`, `Paxsenix`, `Unison`): External endpoints require mockable provider boundaries and explicit user/terms compliance checks.
