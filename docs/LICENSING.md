# Licensing audit

Revenge is licensed under the GNU General Public License, version 3 only
(`GPL-3.0-only`, see [LICENSE](../LICENSE)). Every runtime dependency must be
compatible with distributing the FOSS build under GPL-3.

## Runtime dependencies

| Dependency | Version | License | GPL-3 compatible |
| --- | --- | --- | --- |
| Kotlin standard library, coroutines | 2.3.10 / 1.10.2 | Apache-2.0 | Yes |
| AndroidX (activity, lifecycle, core, palette, adaptive) | current catalog | Apache-2.0 | Yes |
| Jetpack Compose, Material 3 | current catalog | Apache-2.0 | Yes |
| Media3 / ExoPlayer | 1.7.1 | Apache-2.0 | Yes |
| Room | 2.8.5 | Apache-2.0 | Yes |
| DataStore | 1.2.1 | Apache-2.0 | Yes |
| Hilt / Dagger, KSP | 2.60.1 / 2.3.13 | Apache-2.0 | Yes |
| Ktor client | 3.6.1 | Apache-2.0 | Yes |
| OkHttp, Retrofit, Gson | current catalog | Apache-2.0 | Yes |
| Coil 3 | 3.3.0 | Apache-2.0 | Yes |
| Timber | 5.0.1 | Apache-2.0 | Yes |
| Guava, commons-lang3 | current catalog | Apache-2.0 | Yes |
| kotlinx-serialization | 1.6.3 | Apache-2.0 | Yes |
| Protobuf runtime | 4.36.2 | BSD-3-Clause | Yes |
| Brotli decoder | 0.1.2 | MIT | Yes |
| NewPipeExtractor | v0.26.1 | GPL-3.0 | Yes, same license |
| PipePipeExtractor | f8982ca | GPL-3.0 | Yes, same license |
| MetrolistGroup innertubex | v0.7.0 | GPL-3.0 | Yes, same license |
| Kuromoji IPADIC | 0.9.0 | Apache-2.0 | Yes |
| TinyPinyin | 2.0.3 | Apache-2.0 | Yes |
| Haze | 1.0.2 | Apache-2.0 | Yes |
| Reorderable | 3.1.0 | Apache-2.0 | Yes |
| Shimmer | 1.5.0 | Apache-2.0 | Yes |
| Smooth corner rect | v1.0.0 | MIT | Yes |
| Material Kolor | 4.1.1 | Apache-2.0 | Yes |
| Konfetti | 2.0.4 | Apache-2.0 | Yes |
| Lottie Compose | 6.7.1 | Apache-2.0 | Yes |
| UCrop | 2.2.11 | Apache-2.0 | Yes |
| lyrics-core | 0.5.0 | Apache-2.0 | Yes |
| QuickJS-kt | 1.0.14 | Apache-2.0 | Yes |
| ONNX Runtime Android | 1.28.0 | MIT | Yes (bundled model removed in Wave 0 pending provenance) |
| FFmpegKit audio | 6.0-2 | LGPL-3.0 | Yes, LGPL code may be combined with GPL-3 |
| JUnit 4, Robolectric (test only) | current catalog | EPL-1.0 / Apache-2.0 | Test scope only |

## GMS flavor finding

The `gms` flavor adds proprietary Google libraries:

- Google Play services Cast framework and MediaRouter
- Google Play services Location
- Firebase Analytics and Crashlytics

These are distributed under Google's Android Software Development Kit License
and Firebase Terms, not an OSI license. A binary that combines them with GPL-3
code cannot be redistributed under GPL-3 alone; the distributor needs Google's
terms to permit it, and those terms may conflict with GPL-3. The FOSS flavor
contains none of these libraries and is the build that can be redistributed
under GPL-3 without an additional agreement.

Action taken: the FOSS flavor remains the default, and the release page labels
builds. If GMS binaries are published, the release notes must state the extra
terms. If strict GPL compliance is required, publish FOSS binaries only and let
users build the GMS flavor themselves.

## Bundled assets

- `sans_flex.ttf` (Sans Flex v3.007), `outfit.ttf`, `plus_jakarta_sans.ttf`,
  `bbh_bartle_regular.ttf`: SIL Open Font License 1.1. The proprietary
  Google Sans Flex v2.003 font and the fonts' name-table restrictions no longer
  apply; the removed font is not shipped.
- `autoeq_index.json`: AutoEq index (MIT), corrected to upstream names.
- The previously bundled `beat_this_final0_q8.onnx` and
  `vocals_umxhq_int8.onnx` model binaries were removed as unverifiable; ONNX
  Runtime remains a dependency but no model asset ships until an upstream
  MIT-licensed re-export is recorded with a checksum.
- Google Material Symbols used as icons are Apache-2.0.

## Build-only dependencies

Spotless, ktfmt, the Gradle wrapper, and the Android Gradle Plugin are build
tools. They are not distributed in the APK. Their licenses (Apache-2.0, MIT,
Apache-2.0, Apache-2.0) impose no terms on the shipped application.

## Verification notes

- Licenses above were checked against the artifacts' published metadata and
  repository LICENSE files on 2026-11-26. `innertubex`, `lyrics-core`, and
  `smooth-corner-rect` were verified from their upstream repositories.
- Independence from the reference is measured, not asserted. See
  [INDEPENDENCE.md](INDEPENDENCE.md) for the audit method and the remaining
  overlap. Because the reference is GPL-compatible, overlap is lawful under the
  project license even where the stronger independence goal has not yet been
  met.
- YouTube, YouTube Music, Google Play, Firebase, Last.fm, ListenBrainz, Spotify,
  TIDAL, Apple Music, LRCLIB, KuGou, and other names are trademarks of their
  owners. Revenge is not affiliated with them.
