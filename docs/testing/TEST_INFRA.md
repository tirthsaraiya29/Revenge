# Test Infrastructure — Opaque-Box, Requirement-Driven Programme

**Status:** M2 contract (F15/F16 instrumentation)
**Owner:** E2E Test Architect
**Applies to:** waves 0–5, M6 release gates
**Companion documents:** `docs/provenance/REIMPLEMENTATION_ROADMAP.md`,
`docs/provenance/VALIDATION_AND_RELEASE_GATES.md`, `docs/provenance/ARCHITECTURE_CONTRACTS.md`,
`docs/specs/`, `docs/ARCHITECTURE.md`, `docs/DESIGN.md`, `docs/TESTING.md`

## 1. Programme goal and clean-room boundary

Independent, GPL-3 reconstruction of Revenge with **zero Echo-derived code**. This programme exists
to prove the rebuilt product from the outside: every user-facing capability that shipped before the
rebuild must still work, through a clean-room implementation that no test is allowed to inspect.

Rules for every test author in this programme:

1. Read **only** requirement sources: `docs/specs/*.md`, `docs/provenance/ARCHITECTURE_CONTRACTS.md`,
   `docs/provenance/VALIDATION_AND_RELEASE_GATES.md`, `docs/DESIGN.md`, `docs/ARCHITECTURE.md`,
   public upstream API/platform documentation, and `docs/testing/REQUIREMENTS.md`.
2. **Never read** `../Revenge-source`, `../Echo-Music`, any baseline `REIMPLEMENT` file, any
   `BLOCKED` original, or any implementation file being tested. A test derived from an
   implementation is invalid and must be rewritten from the specification.
3. If the specification does not describe the behaviour precisely enough to test it, file a
   **specification gap**. Do not resolve the gap by reading source. The spec owner closes the gap
   first.
4. Do not copy test code, fixture text, or assertion structure from upstream projects. Tier tests
   are subject to the same similarity scan as production code (`G-PROV-1`).
5. Tests are opaque-box: they may touch only the public contracts named in the specifications.
   Reflection into private members, assertions on log strings, internal file paths, private class
   names, or undocumented database columns are prohibited.

The programme does not replace the Independent Reviewer, the similarity scan, or the license audit.
Passing tests prove behaviour, not provenance.

## 2. Requirement traceability model

Every spec section that defines observable behaviour carries a stable requirement ID:

```
REQ-<MODULE>-<NNN>
```

Examples: `REQ-DOMAIN-012`, `REQ-PLAYBACK-004`, `REQ-UI-021`.
The M2 specification author assigns the IDs; they never change after review. Renaming or
renumbering requires a reviewed spec change and a traceability update.

Every test declares the requirement IDs it covers and uses that ID in its name:

```
T1_REQ_DOMAIN_012_QuickPicksOrderTest
T2_REQ_CORE_007_LikedSongsFlowTest
T3_REQ_UI_014_EmptyStateSemanticsTest
T4_REQ_PLAYBACK_004_GaplessTransitionTest
T5_REQ_INNERTUBE_009_MalformedPlayerResponseTest
```

Test naming is the primary traceability key. `docs/testing/TRACEABILITY.md` is the human-readable
matrix and is checked in CI by a script that fails when:

- a requirement ID in `REQUIREMENTS.md` has no test in any required tier;
- a test declares an ID absent from `REQUIREMENTS.md`;
- a user-facing requirement has no Tier 3 and no Tier 4 coverage;
- a new requirement lands without a traceability row in the same change.

`docs/testing/REQUIREMENTS.md` is generated from the specs plus the F4–F16 feature inventory in
`PROGRESS.md` and the roadmap. It is the only test input that is not a spec, and it must cite the
spec section or roadmap feature for every row.

### 2.1 Opaque-box boundary per tier

| Tier | Allowed contact surface | Explicitly forbidden |
|---|---|---|
| 1 | Public top-level functions, public classes, public data-class shapes, documented defaults | Private members, internal packages, Android APIs, network, file system outside fixtures |
| 2 | Tier 1 surface plus documented repository ports, Room schema JSON contract, DataStore key contract, public provider client interfaces | DAO internals beyond documented queries, `InternalDatabase` private members, generated code |
| 3 | Public UI entry points, Compose semantics (`testTag`, `contentDescription`, `Role`, state descriptions), documented navigation and deep-link contracts | ViewModel internals, composable implementation names, visual tree shape unrelated to semantics |
| 4 | Launched app, intents/deep links, MediaSession, notifications, permission dialogs, app-owned files, documented DataStore effect, visible UI, adb-observable system state | Test-only backdoors in production builds, private broadcasts, database file peeking |
| 5 | All Tier 1–4 surfaces plus hostile inputs and OS fault injection | Any bypass of a public trust boundary |

## 3. Tier model

Five tiers, each with a different cost and failure meaning. A lower tier never substitutes for a
higher tier when the requirement is user-facing.

### 3.1 Tier 1 — Unit (JVM)

**Purpose:** fast feedback on pure logic.
**Scope:** `:domain`, `:metadata`, `:dsp-core`, `:canvas`, `:applecanvas`, `:unison`, and pure
parsing/formatting helpers in provider modules (LRC/TTML/JSON parsing, URL handling, scoring,
queue arithmetic, EQ math).
**Tooling:** JUnit 4, `kotlinx-coroutines-test`, JVM 21. No Android, no Robolectric, no sockets.
Use `runTest`, `StandardTestDispatcher`, and explicit virtual-time advancement.
**Fixtures:** JSON/LRC/TTML/URL corpora under `<module>/src/test/resources/fixtures/`.
**Determinism:** fixed seeds for any randomized case; failures print the seed.
**Exit criteria:**
- Every pure-JVM requirement in `:domain`, `:metadata`, `:dsp-core`, `:canvas`, `:applecanvas`,
  `:unison` has at least one Tier 1 test.
- 100% pass on a clean checkout with network disabled.
- No test exceeds 1 second; whole tier under 5 minutes.
- A failing Tier 1 test blocks merge.

### 3.2 Tier 2 — Integration (Robolectric, in-memory Room, fake providers)

**Purpose:** prove module boundaries and persistence contracts without a device.
**Scope:** `:core` repositories, Room DAOs and migrations, DataStore, backup/restore, download
bookkeeping; `:playback` queues and sink-chain selection; `:innertube` and provider clients against
recorded fixtures; `:lyrics` orchestration; `:audio-dsp` processors; `:usbaudio` routing selection.
**Tooling:** Robolectric 4.14.1 (baseline `@Config(sdk = [28])`, plus API 26/33/36 variants where
the requirement is SDK-conditional), in-memory Room, `room-testing` `MigrationTestHelper`,
`PreferenceDataStoreFactory` on a temporary directory, Ktor `MockEngine`,
`kotlinx-coroutines-test`, fake Clock/Dispatcher, Media3 test utilities.
**Fixtures:** sanitized network responses, Room schema JSON 1..48, pre-built migration source
databases, synthetic media metadata.
**Exit criteria:**
- Every repository contract in the specs is exercised, including not-found and error paths.
- Room `1..48` upgrade path and fresh v48 creation both pass; no destructive migration allowed
  without an explicit approved contract.
- Every provider client has success, auth-required, transport-error, malformed-body, timeout, and
  cancellation cases.
- 100% pass with network egress disabled.
- Whole tier under 20 minutes.

### 3.3 Tier 3 — Compose UI / component

**Purpose:** prove screens, states, accessibility, theming, and navigation.
**Scope:** startup/onboarding, library/search/player/settings screens, loading/empty/error/offline
states, dynamic color, OFL font switching, dialogs and sheets, deep links, back-stack
non-duplication, process recreation, semantics/TalkBack.
**Two lanes:**
- **Tier 3a (JVM, per-change):** Compose UI tests under Robolectric. Fast, blocking, screenshot
  goldens.
- **Tier 3b (device, nightly/release):** the same flows on an emulator to catch rendering, IME,
  and OEM-behaviour differences.
**Tooling:** `androidx.compose.ui:ui-test-junit4`, `ui-test-manifest`, AndroidX Test core/ext,
Robolectric, Roborazzi for JVM golden images.
**Selector contract:** UI exposes stable `testTag`s documented in `docs/testing/UI_SEMANTICS.md`.
Tests query semantics, never implementation class names or private state. Accessibility assertions
cover clickable/focusable nodes without labels, missing state descriptions, and touch-target size.
**Screenshot strategy:**
- Goldens are captured on JVM only, at a fixed environment: 360×800 dp, mdpi, font scale 1.0,
  locale `en-US`, fixed clock, animations and shimmer disabled, fixture artwork only.
- Path: `app/src/test/screenshots/<theme>/<screen>__<state>.png`.
- Diff policy: zero pixel tolerance by default. An intentional visual change updates goldens in the
  same reviewed change with `-Proborazzi.record`; unrelated golden churn is a review failure.
- Device screenshots are CI evidence artifacts, never pixel gates.
**Exit criteria:**
- Every screen and every documented state has at least one test.
- All interactive nodes have labels; semantics scan reports zero accessibility errors.
- Navigation and deep-link requirements pass, including process recreation and back-stack
  non-duplication.
- 100% pass; zero unapproved golden diffs.
- Tier 3a under 15 minutes; Tier 3b under 30 minutes on the emulator matrix.

### 3.4 Tier 4 — E2E functional parity (device/emulator)

**Purpose:** prove the shipped APK behaves as the product requires through system surfaces.
**Scope (maps to `VALIDATION_AND_RELEASE_GATES.md` §5.1):**
- Startup, onboarding, process recreation, non-duplicating back stack, deep links.
- Playback: queue ordering and continuations, gapless, crossfade, cutoff guard, audio focus
  duck/pause, Bluetooth/media-button controls, notification controls, playback recovery.
- Streaming and lyrics: auth-required versus transport-error isolation, malformed LRC/TTML
  isolation, offline lyric caching, translation failure handling.
- DSP and USB: EQ parse/apply, clean AutoEq lookup, USB DAC routing fallback to platform
  `AudioTrack`.
- Recognition ("Find"): permission denial, timeout, cancellation, guaranteed temporary PCM cleanup.
- Storage/downloads/DB: Room upgrade, download retry/collision, backup/restore.
- Integrations/widgets: mocked Spotify/Jellyfin/Navidrome/Listen Together/Last.fm/ListenBrainz
  isolated failures, widget and notification state sync.
**Tooling:** AndroidJUnitRunner, AndroidX Test, UiAutomator2, Compose test rules on device,
`androidx.test:orchestrator`, `adb` (`am force-stop`, `pm revoke`, `dumpsys media_session`,
`dumpsys notification`, `cmd media_session volume`), loopback MockWebServer, MediaSessionManager
with a test notification-listener grant.
**Device matrix:** API 26 (minimum), 31, 33, 36 (target), x86_64 emulator images. One periodic
physical-device lane covers Bluetooth, USB DAC, and real audio routing; results there are evidence,
not a per-PR gate.
**Media:** bundled synthetic fixtures (silence, sine, clipped, 44.1/48 kHz; WAV/MP3/FLAC/M4A).
Never download media during a test. Assert the documented pipeline and MediaSession state, not
acoustic output.
**Exit criteria:**
- 100% pass across the required emulator matrix for the release commit.
- Every bullet in `VALIDATION_AND_RELEASE_GATES.md` §5.1 maps to at least one Tier 4 test.
- No open P0/P1 defect; no silent feature removal identified by the parity checklist.
- Evidence bundle (logs, screenshots, dumpsys captures, mock-server transcripts) archived per run.
- Release candidate cannot ship without a green Tier 4 run on the exact artifact hash.

### 3.5 Tier 5 — Adversarial / hardening

**Purpose:** break the product at its trust boundaries before a challenger does.
**Scope:**
- Fuzzed and malformed JSON, TTML, LRC, protobuf, URLs, cipher/`n`-transform payloads; oversized
  and deeply nested inputs; invalid UTF-8 and hostile XML (XXE, billion laughs).
- Process kill mid-download, mid-recognition, mid-migration, mid-export; restart recovery.
- Network storms: timeouts, 429/5xx, partial bodies, truncation, disconnects, slow responses,
  duplicate responses, out-of-order responses.
- Storage faults: full disk, read-only storage, revoked permissions, missing SAF grant.
- Security probes: exported components, PendingIntent flags, cleartext, WebView JavaScript bridge,
  log redaction, credential leakage, FOSS binary purity.
- Resource hygiene: temporary PCM/file cleanup on every path, no leaked file descriptors,
  StrictMode violations, ANR and memory pressure.
**Tooling:** seeded JUnit property loops over a checked-in corpus plus generated inputs;
MockWebServer fault modes; adb fault injection; `apkanalyzer`/`aapt2`/`dexdump` probes; Android
Monkey with a fixed seed for random UI; LeakCanary in debug only; StrictMode in dedicated runs.
**Exit criteria:**
- Zero crashes, ANRs, data loss, secret exposure, or uncleaned temporary files attributable to the
  tested surface.
- Every discovered defect has a regression test in the appropriate tier before closure.
- Nightly stability at least 99% over 20 consecutive runs with no unexplained failure.
- Release candidate passes one complete Tier 5 run; unresolved findings require written risk
  acceptance by the Release Manager and Independent Reviewer.

## 4. Cross-cutting infrastructure

### 4.1 Deterministic clocks and virtual time

- Production code takes a `Clock`/time source at its boundary. Tests inject a mutable clock and
  advance it explicitly.
- Coroutine tests use `runTest` and `TestCoroutineScheduler`; sleep timers, gapless pre-arm,
  crossfade scheduling, retry backoff, and timeout logic run on virtual time.
- `Thread.sleep`, `delay` in tests, and wall-clock polling are prohibited. Use idling resources,
  `waitUntil`, or explicit clock advancement.
- Fixed epoch for all fixtures: `2026-01-01T00:00:00Z`, UTC, locale `en-US`, 24-hour time, font
  scale 1.0.
- On-device tests may assert only time ranges for OS-owned timestamps; app-owned timing must use
  the injectable clock.

### 4.2 Network mocking

- No test, in any tier, reaches the public internet. CI disables outbound network for test jobs.
- Tier 2/3: Ktor `MockEngine`; assert method, path, essential headers, and body shape, then return
  a checked-in fixture.
- Tier 4: loopback MockWebServer. Provider base URLs are overridden through test configuration, not
  by patching production classes. A debug-only network-security policy must permit cleartext to
  loopback only.
- Fixtures are sanitized recordings: no tokens, cookies, account identifiers, or personal data.
  Recording is a manual, reviewed step; it never runs in CI.
- Every fixture has a schema check so an upstream format change fails loudly rather than silently
  producing empty UI.

### 4.3 Fake providers and doubles

- Fakes implement only the public port interfaces declared in the specs.
- Fakes return canned success, typed failure, delay, cancellation, and malformed responses. They
  contain no production logic.
- Doubles are installed at the composition root or documented injection point; no global mutable
  service locator.
- A fake may not be used to assert that a specific internal collaborator was called. Assert
  observable output instead.

### 4.4 Fake media

- Checked-in audio fixtures cover silence, sine, clipped signal, 44.1/48 kHz, and short/long
  durations.
- A fake audio sink implements the documented sink port and records frames, timestamps, and
  gapless boundaries.
- Media3 test utilities drive playback state without real hardware; real hardware is exercised only
  in the physical-device lane.
- Recognition tests use synthetic PCM with known fingerprints and assert cleanup of every temporary
  file on success, failure, timeout, and cancellation.

### 4.5 Fixtures and test data

- All fixtures are synthetic or explicitly licensed for test use. No real user data, credentials,
  account identifiers, or private media.
- `docs/testing/fixtures/manifest.json` records each fixture's path, SHA-256, generator, and
  license/provenance. A fixture without a manifest entry fails CI.
- Never edit a fixture to make a failing test pass. A fixture change is a reviewed requirement
  change.
- Prefer the smallest fixture that exercises the requirement.

### 4.6 Screenshot strategy

Defined in §3.3. Summary: JVM goldens are the only pixel gate; device captures are evidence;
goldens use fixture artwork, fixed device metrics, fixed clock, and disabled animation.

## 5. Directory conventions

```
docs/testing/
  TEST_INFRA.md            this document
  REQUIREMENTS.md          requirement catalogue (spec-derived, stable IDs)
  TRACEABILITY.md          REQ -> test -> tier -> gate matrix
  UI_SEMANTICS.md          stable testTag/contentDescription contract
  FLAKE_LEDGER.md          quarantined/flaky test register
  fixtures/manifest.json   fixture hashes and provenance
  fixtures/network/        sanitized provider payloads (shared)
  fixtures/lyrics/         LRC/TTML corpora (shared)
  fixtures/media/          audio fixtures and checksums (shared)

app/src/test/kotlin/com/revenge/qa/
  harness/                 clock, fakes, rules, fixture loaders, assertions
  tier1/                   pure app-side logic (rare; prefer module tests)
  tier2/                   app-side integration with Robolectric
  tier3/                   JVM Compose tests
app/src/test/resources/fixtures/     JVM fixture copies (symlinked or generated from docs/testing)
app/src/test/screenshots/            approved goldens

app/src/androidTest/kotlin/com/revenge/qa/
  harness/                 device harness, adb helpers, media/session helpers
  tier3/                   device Compose lane
  tier4/                   E2E functional parity
  tier5/                   adversarial hardening
app/src/androidTest/assets/fixtures/ device fixture copies

<module>/src/test/kotlin/com/revenge/qa/tier1/<area>/
<module>/src/test/kotlin/com/revenge/qa/tier2/<area>/
<module>/src/test/resources/fixtures/
```

Rules:

- Test packages are `com.revenge.qa.*`; they never mirror production packages. This prevents
  accidental package-private access and makes implementation leakage visible in review.
- Module tests stay with their module; cross-cutting E2E stays in `:app`.
- A test file name carries tier and requirement: `T<tier>_<REQ-ID>_<Slug>Test`.
- Test annotations: `@Tier1`–`@Tier5`, `@Requirement("REQ-...")`, `@Quarantined("<ticket>")`.
  Tier and requirement filters work without custom Gradle wiring.
- `internal` production declarations are not testable from `com.revenge.qa.*`; that is intentional.
  If a requirement can only be reached through `internal`, the public contract is wrong.

## 6. Gradle commands

Current baseline (already present):

```bash
./gradlew :domain:test :metadata:test :dsp-core:test
./gradlew test
./gradlew :app:testUniversalFossDebugUnitTest
./gradlew :app:assembleUniversalFossDebug
```

Target tier commands (wired in Wave 0; no custom task names required):

```bash
# Tier 1 — pure JVM
./gradlew :domain:test :metadata:test :dsp-core:test :canvas:test :applecanvas:test :unison:test \
  --tests "com.revenge.qa.tier1.*"

# Tier 2 — Robolectric / in-memory Room / fake providers
./gradlew :core:testDebugUnitTest :playback:testDebugUnitTest :audio-dsp:testDebugUnitTest \
  :usbaudio:testDebugUnitTest :innertube:testDebugUnitTest :lyrics:testDebugUnitTest \
  :app:testUniversalFossDebugUnitTest --tests "com.revenge.qa.tier2.*"

# Tier 3a — JVM Compose UI + goldens
./gradlew :app:testUniversalFossDebugUnitTest --tests "com.revenge.qa.tier3.*"

# Tier 3b — device Compose lane
./gradlew :app:connectedUniversalFossDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.annotation=com.revenge.qa.Tier3

# Tier 4 — E2E parity
./gradlew :app:connectedUniversalFossDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.annotation=com.revenge.qa.Tier4

# Tier 5 — adversarial
./gradlew :app:connectedUniversalFossDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.annotation=com.revenge.qa.Tier5

# GMS flavor sanity for the tiers that are flavor-sensitive
./gradlew :app:testUniversalGmsDebugUnitTest --tests "com.revenge.qa.tier2.*"

# Golden recording (reviewed change only)
./gradlew :app:testUniversalFossDebugUnitTest --tests "com.revenge.qa.tier3.*" -Proborazzi.record

# Coverage/diagnostics (not a gate)
./gradlew koverHtmlReport
```

Adoption requirements (Wave 0):

- Add test-only dependencies to `gradle/libs.versions.toml`: Compose `ui-test-junit4` and
  `ui-test-manifest`, AndroidX Test core/ext/runner/rules, `uiautomator`, `orchestrator`,
  `room-testing`, `ktor-client-mock`, `mockwebserver`, `media3-test-utils`, Roborazzi, Kover.
- Create `app/src/androidTest` and set `testInstrumentationRunner` (already present).
- Add the tier annotations and the traceability check to CI.
- Keep all test dependencies `testImplementation`/`androidTestImplementation`; none may enter a
  release artifact. Tier 5 binary probes verify this.

## 7. CI jobs and gating

Existing jobs: `ci.yml` (build + `./gradlew test` + FOSS debug), `semgrep.yml`, `codeql.yml`,
`release.yml`. Extend rather than replace.

| Job | Trigger | Command | Blocking | Artifacts |
|---|---|---|---|---|
| `tier1` | every PR/push | Tier 1 command | yes | JUnit XML |
| `tier2` | every PR/push | Tier 2 command | yes | JUnit XML, Room migration report |
| `tier3-jvm` | every PR/push | Tier 3a command | yes | JUnit XML, golden diff, screenshots |
| `tier3-device` | nightly + release | Tier 3b, API 26/31/33/36 matrix | release yes; nightly no | screenshots, logs |
| `tier4` | nightly + release candidate | Tier 4, sharded, emulator matrix | release yes; nightly no | evidence bundle, dumpsys, mock transcripts |
| `tier5` | nightly + release candidate | Tier 5, fixed seed, full corpus | release yes; nightly no | fuzz seeds, crash/ANR logs, binary probes |
| `hardware-parity` | weekly + release candidate | physical device: BT, USB DAC, audio focus | release sign-off | manual checklist, recordings |

CI topology rules:

- JDK 21 (Temurin), Android SDK 37, Gradle 9.9.1, Gradle cache enabled.
- Pull requests run only Tiers 1–3a. Device tiers are too slow and too noisy to block every PR.
- Release candidates run all five tiers on the exact artifact hash; a rebuild invalidates the run.
- Emulator jobs use a fixed image, fixed locale/timezone, animations disabled, and a clean snapshot.
- Tier 4 shards by test class via the orchestrator; each shard reports independently and is
  aggregated before the gate decision.
- Test jobs have no outbound internet. Mock servers bind to loopback.
- Failure artifacts are always uploaded, including on cancellation.

## 8. Flake policy

**Definition:** a test that fails on identical code, configuration, seed, and environment without a
product change.

Rules:

1. No automatic retries in Tier 1, 2, or 3a. A flaky unit test is a bug in the test.
2. Device tiers may retry a failed shard **once** to collect diagnostics, but a retried pass does
   **not** satisfy a release gate. The flake is recorded and counted.
3. Any test with 2 or more unexplained failures in 200 runs is quarantined. Quarantine is
   `@Quarantined("<ticket>")`, excluded from gating commands, and listed in `FLAKE_LEDGER.md`.
4. Quarantine is time-boxed to 5 business days. After that the test is fixed or deleted; deleting a
   requirement's only test requires a spec-owner decision.
5. Prohibited flake sources: sleeps, wall-clock waits, real network, shared mutable state, unordered
   collection assertions, device-locale assumptions, animation-dependent assertions, unbounded
   dispatchers, and timezone-dependent formatting.
6. Every test is isolated: fresh database, fresh DataStore directory, fresh mock server, fresh
   process per class where the tier requires it.
7. The ledger records test ID, requirement, first failure, run count, owner, ticket, and status.
   The Release Manager reads it before signing a gate.
8. A quarantined test still counts against the requirement's coverage. A release cannot claim a
   requirement green while its only test is quarantined.

## 9. Ownership

| Role | Owns | Accountable for |
|---|---|---|
| E2E Test Architect | This document, tier taxonomy, harness standards, traceability, flake ledger, CI topology | Programme coherence; final test-infrastructure sign-off |
| Specification Owner (M2) | `docs/specs/`, `REQUIREMENTS.md` | Stable requirement IDs and testable acceptance criteria |
| Module Owner | Tier 1/2 tests in their module, module fixtures | Module requirements covered and green |
| UI Owner | Tier 3a/3b, `UI_SEMANTICS.md`, goldens | Screen/state/accessibility coverage |
| Platform/E2E Owner | Tier 4 harness, device matrix, adb/session helpers | Functional parity evidence |
| Security/Hardening Owner | Tier 5 corpus, fault injection, binary probes | Adversarial sign-off |
| CI Maintainer | Workflows, emulator images, sharding, artifacts | Reliable, reproducible jobs |
| Release Manager | Gate enforcement, risk acceptance | Only green artifacts ship |
| Independent Reviewer | Clean-room attestation, similarity scan, traceability audit | No self-approval; no implementation-derived tests |

Review requirements:

- Every new test is reviewed by a person who did not write it and who has not read the
  corresponding implementation.
- A reviewer rejects a test that names private implementation, asserts log text, or depends on
  execution order.
- Test-only dependency additions require E2E Test Architect and license review.

## 10. Exit criteria and release-gate mapping

| Gate | Tier(s) | Criteria |
|---|---|---|
| `G-TEST-1` merge | 1, 2, 3a | 100% pass, traceability check green, no new flake, no unapproved golden diff |
| `G-TEST-2` nightly | 1–5 | All tiers executed; Tier 5 stability ≥99% over 20 runs; no unexplained failure |
| `G-TEST-3` release candidate | 1–5 | All tiers 100% on the exact artifact hash; full requirement matrix green or explicitly waived |
| `G-TEST-4` parity | 4 | Every `VALIDATION_AND_RELEASE_GATES.md` §5.1 bullet mapped to a passing Tier 4 test |
| `G-TEST-5` hardening | 5 | Zero crash/ANR/data-loss/secret findings; all others risk-accepted in writing |

A tier exits only when its own criteria in §3 are met **and** its `TRACEABILITY.md` rows are green.
Percentages are not a substitute for requirement coverage; coverage reports are diagnostic only.

## 11. Prohibited practices

- Reading `../Revenge-source` or `../Echo-Music` while authoring tests.
- Reading the implementation under test to decide expected values.
- Reflection, private access, log-string assertions, or file-layout assertions.
- Real network calls, real credentials, real personal media, or production endpoints.
- `Thread.sleep`, unbounded polling, animation-dependent assertions, or order-dependent tests.
- Retrying a blocking test to green.
- Editing a fixture or golden to make a red test pass without a reviewed requirement change.
- Adding a test-only dependency to a production configuration.
- Marking a requirement green while its only test is quarantined or skipped.

## 12. Baseline and adoption plan

Baseline facts (from `VALIDATION_AND_RELEASE_GATES.md` and the current tree):

- 1,547 unit tests across 304 XML suites, 21 modules, 0 failures at baseline.
- 0 `androidTest` files at baseline; device infrastructure does not exist yet.
- Robolectric 4.14.1 and `kotlinx-coroutines-test` are already used; Compose UI test, Ktor mock,
  Room testing, orchestrator, Roborazzi, and Kover are not yet declared.

Adoption by wave:

| Wave | Test work |
|---|---|
| 0 | Create `docs/testing/`, annotations, harness skeleton, fixture manifest, CI jobs, test-only dependency catalog entries |
| 1 | Tier 1 for `:domain`, `:metadata`, `:dsp-core`; Tier 2 contracts where persistence is touched |
| 2 | Tier 2 for `:core` Room/migrations/DataStore and `:playback`; Tier 4 persistence and playback flows; fake media harness |
| 3 | Tier 2 provider/lyrics integration with MockEngine; Tier 5 malformed lyric/JSON corpus |
| 4 | Tier 3 screens/states/accessibility/goldens; Tier 4 navigation, deep links, settings |
| 5 | Tier 4 integrations/widgets; Tier 5 fault-injection and security probes |
| M6 | Full matrix on release candidate; traceability audit; flake ledger review; evidence archive |

The 1,547-test baseline is a floor, not a target. Rebuilt requirements must be re-proven from the
specifications; a baseline test that encoded Echo-derived implementation detail is not carried
forward.

## 13. Known limitations and risks

- Device audio tests cannot prove acoustic output; they prove the documented pipeline and system
  state. USB DAC and Bluetooth require the physical-device lane.
- Emulator behaviour differs from OEM devices; the release gate therefore includes one physical
  device pass.
- Specs are still being written. Requirements without a spec are untestable and remain blocked;
  the programme must not invent behaviour.
- Screenshot goldens depend on deterministic font rendering. The bundled OFL font must be used in
  all capture environments, and goldens must be regenerated only on the pinned environment.
- The traceability check proves coverage, not correctness; Independent Reviewer and similarity scan
  remain mandatory.
- Adding test dependencies enlarges the dependency graph; license review is required for each.
