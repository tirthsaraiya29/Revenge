# Validation & Release Gates Baseline (M1.2 -> M6)

**Baseline Reference:** `11ae62071f96db3b70557867ab1254dbcc5b125e` (`1,547/1,547` baseline unit tests passed across `21` modules / `304` XML suites)
**Purpose:** Define non-negotiable build, static analysis, licensing, provenance/similarity, E2E Tiers 1–4, Tier-5 adversarial, and disclosure gates for M2–M6.

---

## 1. Phase 0 Verified Baseline (M1.1 Benchmark)

- **JDK Toolchain:** Eclipse Adoptium Temurin `21.0.7+6-LTS` (`gradle/gradle-daemon-jvm.properties` `toolchainVersion=21`). Note: `docs/BUILDING.md` erroneously cites Studio JBR 21 while installed JBR is `25.0.4.1`; always invoke Gradle with Java 21.
- **Baseline Test Floor (must never regress):** `304` XML suites, **`1,547` tests, `1,547` passed, `0` failed, `0` errors, `0` skipped** (`250` suites / `1,190` tests across 10 `:app` variants + `54` suites / `357` tests across 20 library modules; `0` baseline `androidTest` files).
- **APK Packaging Lock Workaround:** Host Win32 error 32 lock on `app/build/outputs/apk/universalFoss/debug` bypassed cleanly via `--init-script app_build_local.init.gradle.kts` redirecting `:app` `buildDirectory` to `app/build-local` (valid debug-signed `207,181,026` B APK).

---

## 2. Build & Packaging Gates (R5)

| Gate | Command / Check | Pass Criteria |
|---|---|---|
| **G-BUILD-1: Pure-JVM Fast Suite** | `./gradlew :domain:test :metadata:test :dsp-core:test :canvas:test :applecanvas:test :unison:test --no-daemon` | Exit `0`; 100% pass; zero Android SDK dependencies |
| **G-BUILD-2: Full 21-Module Unit Tests** | `./gradlew test --no-build-cache --no-daemon` | Exit `0`; all unit tests pass (`0` failed/skipped) across all modules & default variants (plus `-PallAbis` release matrix check) |
| **G-BUILD-3: FOSS Debug & Release APKs** | `./gradlew :app:assembleUniversalFossDebug :app:assembleUniversalFossRelease` (and `-PallAbis` per-ABI release) | Exit `0`; valid signed APK; `dependenciesInfo { includeInApk = false }` |
| **G-BUILD-4: GMS Debug & Release APKs** | `./gradlew :app:assembleUniversalGmsDebug :app:assembleUniversalGmsRelease` | Exit `0`; builds cleanly from source; release notes/docs state GMS GPL-3.0 binary distribution restriction |
| **G-BUILD-5: FOSS Binary Purity** | Inspect merged FOSS manifest & DEX (`apkanalyzer` / `aapt2` / `dexdump`) | **0** occurrences of `com.google.android.gms.*`, `com.google.firebase.*`, `ReconnectionService`, or `play-services-cronet` |

---

## 3. Code Quality, Formatting & Security Gates

| Gate | Tool / Config | Pass Criteria |
|---|---|---|
| **G-LINT-1: Spotless Formatting** | `./gradlew spotlessCheck` (`ktfmt().googleStyle()` in `build.gradle.kts`) | Exit `0` across all `.kt` and `.gradle.kts` files |
| **G-LINT-2: Android Lint** | `./gradlew :app:lintUniversalFossDebug` (`app/lint.xml`) | Zero `ERROR`-level findings (enforces `MissingQuantity` outside `cs/lt/sk`, manifest exported/PendingIntent rules, unused/broken resources) |
| **G-SEC-1: Semgrep SAST** | `semgrep scan --config .semgrep/rules.yml --error` | 0 findings for `revenge-hardcoded-credential`, `revenge-cleartext-endpoint`, `revenge-insecure-hostname-verifier`, and reviewed `revenge-webview-javascript` (`addJavascriptInterface` audit) |
| **G-SEC-2: Manifest & IPC Hygiene** | Audit `AndroidManifest.xml` & `PendingIntent` flags | Every exported component justified & permission-guarded (`AutomotiveArtworkProvider` URI signature check, `PendingIntent.FLAG_IMMUTABLE` enforced) |

---

## 4. License, Notice & Provenance Gates (R2, R4, R5)

| Gate | Verification Method | Pass Criteria |
|---|---|---|
| **G-LIC-1: Dependency & License Audit** | Inspect `libs.versions.toml` + all `22` `build.gradle.kts` + resolved runtime/test graphs | **0** `org.json:json`; **0** `play-services-cronet` in FOSS; retired `ffmpeg-kit-audio` replaced or mitigated; all runtime deps GPL-3.0 compatible in FOSS |
| **G-LIC-2: Asset & Binary Purity** | Asset audit script against `THIRD_PARTY_COMPONENT_REGISTER.md` | `google_sans_flex.ttf`, `vibrafp/`, `228` Echo/meme PNGs, and all unapproved `BLOCKED` assets (`119` items) **removed or replaced**; `autoeq_index.json` clean upstream MIT (`Amazon Echo Buds` intact); JS headers intact; `THIRD_PARTY_NOTICES.md` & `OFL.txt` present |
| **G-PROV-1: Automated Similarity & Origin Scan** | Run normalized token Jaccard, 4-shingle Jaccard, and line-overlap scanner vs `Echo-Music` (`815273dd`) & `Revenge-source` on all new/reimplemented files | Zero `ExactRawSHA`/near-verbatim Echo matches in `REIMPLEMENT` code; zero superficial renames/helper extractions (`18 M`-style); isomorphic protocol/Room DTOs explicitly justified & approved |
| **G-PROV-2: Independent Reviewer Sign-Off** | Independent Reviewer + Challenger audit (no self-approval) | Signed attestation confirming R2 clean-room separation, zero Echo-authored logic retained, and complete uncertainty disclosure |

---

## 5. E2E Functional & Adversarial Hardening Gates (F15, F16)

1. **Tiers 1–4 Opaque-Box E2E Suite (`F15`):** Must pass 100% before M6 Phase 2 (`TEST_INFRA.md` / `TEST_READY.md`), covering:
   - Application startup, onboarding, process recreation, non-duplicating back-stack navigation, deep links.
   - Compose UI accessibility semantics, dynamic theming, OFL font switching, loading/empty/error/offline states.
   - Playback engine: queue ordering/continuations, gapless/crossfade, cutoff protection, audio focus duck/pause, Bluetooth/notification controls, playback recovery.
   - Streaming & Lyrics: InnerTube auth vs transport error isolation, malformed LRC/TTML isolation, offline lyric caching, translation error handling.
   - Audio DSP & USB: EQ parsing/application, clean AutoEq lookup, USB DAC AudioTrack routing fallback.
   - Recognition ("Find"): permission denial handling, timeout/cancellation, guaranteed temp PCM cleanup.
   - Storage, Downloads, DB Migrations: Room schema upgrade validation, download retry/collision handling, backup/restore.
   - Integrations & Widgets: mocked Spotify/Jellyfin/Navidrome/Listen Together/Last.fm/ListenBrainz isolated failure handling, widget/notification state sync.
2. **Tier-5 Challenger Adversarial Hardening (`F16`):** Fuzz/malformed JSON/TTML/proto inputs, process kill mid-download/mid-recognition, offline/flaky network storms, manifest/FOSS binary probing.

---

## 6. Mandatory Uncertainty & Limitation Disclosure Policy (R5)

Release documentation (`README.md`, `docs/LICENSING.md`, release notes) MUST transparently disclose rather than conceal:
1. **GMS Binary Distribution:** GMS flavor links proprietary Google/Firebase SDKs with GPL-3.0 code and is build-from-source only; only FOSS builds are publicly distributed under GPL-3.0.
2. **USB Audio Routing:** Pure-Kotlin `UsbAudioDriver` routes through platform `AudioTrack` (Android audio mixer), not bit-perfect direct USB isochronous URBs, unless/until a verified native USB engine is implemented.
3. **ONNX Beat Tracker Provenance:** `BeatTracker` ML weights must either be demonstrably re-exported from verified MIT `CPJKU/beat_this` or replaced with an algorithmic beat tracker; unresolved binary weights are never shipped silently.
4. **External Service Terms:** Unofficial streaming/metadata endpoints (`InnerTube`, `Spotify`, `Apple Music`, `TIDAL`, `Shazam`) remain subject to upstream API changes and provider terms of service.
