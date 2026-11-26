# Revenge rebuild — durable progress log

Updated after each milestone. Read this first when resuming.

## Mission

Independent, GPL-3 reconstruction of the Revenge Android music player in this
directory. `../Revenge-source` is a read-only behavioural reference. No source
file is transplanted; modules are re-implemented from their public API contract
and observable behaviour.

## Starting state (discovered)

- Destination contained an existing ~122k-line `app/` module (package
  `com.revenge.music`) plus README/CONTRIBUTING/.gitignore. No root Gradle build,
  no version catalog, no supporting modules.
- Reference contains all supporting modules (`core`, `domain`, `playback`,
  `innertube`, lyrics providers, DSP, USB audio, canvas, and others) but no
  `app/` directory. Reference is not a git repository.
- App imports 1060 distinct `com.revenge.*` symbols; 84 come from supporting
  modules. The app also uses same-package classes that live in `:playback` and
  `:core` (for example `com.revenge.music.playback.audio.BeatAnalyzer`,
  `com.revenge.music.db.MusicDatabase`).
- Toolchain: Android Studio JBR 21, Android SDK 37, Gradle wrapper 9.9.1
  (copied tooling), reference catalog versions adopted.

## Done

- [x] Root build: `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`,
  `gradle/libs.versions.toml`, wrapper, `local.properties`, minimal manifests.
- [x] `./gradlew projects` succeeds (21 projects configured).
- [x] Module build files authored for all 20 supporting modules.
- [x] Leaf modules implemented independently: metadata, domain, dsp-core,
  canvas, applecanvas, unison, lrclib, kugou, betterlyrics, simpmusic,
  youlyplus, paxsenixlyrics, shazamkit, artistvideo, usbaudio.
- [x] Leaf modules compile; JVM tests for domain, metadata, dsp-core pass.
- [x] GPL-3 LICENSE (SPDX text), issue templates, PR template, CodeQL and
  Semgrep workflows with code-path filters, security/privacy/conduct docs,
  building/testing/architecture docs, design direction and landing page.
- [x] Semgrep ruleset runs clean on the current app and leaf modules.
- [x] `:innertube:compileDebugKotlin` and `:core:compileDebugKotlin` pass after
  fixing Ktor 3.6 compression/userAgent APIs, a nested Thumbnail type, a
  missing return, and trailing-lambda binding in page filters.

## In progress

- [ ] `core` implementation (Room database, entities, DAOs, preference keys,
  repositories, models, utils).
- [ ] `innertube` implementation (models and YouTube client).
- [ ] `lyrics` implementation (orchestration, provider adapters, translation).
- [ ] `playback` implementation (queues, audio processors, cutoff guard).
- [ ] `audio-dsp` implementation (processors, controller, EQ repository).
- [x] All 20 supporting modules compile; `:app:compileUniversalFossDebugKotlin`
  passes. The USB DAC path was changed to a real AudioTrack output (platform
  routed), and the settings label no longer claims bit-perfect output.
- [ ] App tests and APK build (full `test` running).
- [ ] Remove the unimplemented `Puter` AI provider path from app settings and
  the three AI helper branches.
- [ ] Replace the USB audio transport fallback with real output (see risks).
- [ ] Final verification, scan run, release notes, commit and push.

## Known constraints / risks

- Subagents must be launched with `model: 'sonnet'`; the default subagent model
  is blocked by the provider guardrail.
- USB bit-perfect DAC: the pure-Kotlin driver buffers PCM but cannot submit
  isochronous USB URBs from Java. `UsbDacAudioSink` must fall back to a real
  AudioTrack routed to the USB device until a native engine exists, or the
  feature must be hidden. Do not claim bit-perfect output until measured.
- Existing app contains unimplemented `Puter` AI provider branches. Remove the
  provider instead of shipping a dead control.
- Duplicate FQCN check required: app defines classes in packages shared with
  modules such as `com.revenge.music.playback.*` and `com.revenge.canvas.*`.
- The app was present before this session; its internal consistency has not yet
  been proven by a successful compile. The integration compile is the source of
  truth.

## Verification log

- `./gradlew projects` PASS (2026-11-26).
- `:metadata:compileKotlin :domain:compileKotlin :dsp-core:compileKotlin` PASS.
- `:lrclib :kugou :betterlyrics :simpmusic :youlyplus :shazamkit :artistvideo
  :canvas :applecanvas :unison :paxsenixlyrics` compile PASS.
- `:domain:test :metadata:test :dsp-core:test` PASS.
- `semgrep scan --config .semgrep/rules.yml ... --error` PASS (0 findings).
- Landing page: HTML structure valid, all in-page anchors resolve, no duplicate
  ids, JS syntax checked with Node.

## Independence audit finding (must resolve)

An automated line-overlap audit against the reference found 115 module files
with more than 60% identical non-comment lines (12.8k lines total). Many small
DTO, Room entity, and provider files are necessarily isomorphic because field
names must match the YouTube JSON and the existing Room schema, but several
behaviour-bearing files (YouTube.kt, DatabaseDao.kt, MusicDatabase.kt,
InnerTube.kt, page parsers, provider clients) overlap enough to require an
independent rewrite before claiming independent implementation. This is tracked
as a release blocker and documented in docs/INDEPENDENCE.md.
