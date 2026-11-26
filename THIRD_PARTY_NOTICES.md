# Third-party notices

Revenge is distributed under GPL-3.0-only (see `LICENSE`). This file records the
third-party components that are bundled with, linked into, or vendored by the
application, together with their licences and required attributions.

The full SIL Open Font License 1.1 text is in `licenses/OFL-1.1.txt`.

## Bundled assets

| Component | Version / origin | Licence | Notes |
| --- | --- | --- | --- |
| Sans Flex (`sans_flex.ttf`) | v3.007, Google LLC | SIL OFL 1.1 | Name table declares OFL 1.1; used as the brand typeface. |
| Outfit (`outfit.ttf`) | Google Fonts | SIL OFL 1.1 | |
| Plus Jakarta Sans (`plus_jakarta_sans.ttf`) | Google Fonts | SIL OFL 1.1 | |
| BBH Bartle (`bbh_bartle_regular.ttf`) | Google Fonts | SIL OFL 1.1 | |
| AutoEq index (`autoeq_index.json`) | jaakkopasanen/AutoEq | MIT | Device names restored to upstream values. |
| Material Symbols / icons | Google Material Design Icons | Apache-2.0 | Vector icons and Compose material-icons-extended. |
| astring (`astring.js`) | davidbonnet/astring | MIT | Vendored solver asset; upstream copyright retained where present. |
| meriyah (`meriyah.js`) | meriyah/meriyah | ISC | Vendored solver asset. |
| yt-dlp EJS core (`yt.solver.core.js`) | yt-dlp/ejs | Unlicense | Vendored solver asset. |
| QuickJS engine | quickjs-kt-android | MIT (QuickJS) / Apache-2.0 (binding) | |
| ONNX Runtime Android | 1.28.0 | MIT | No ONNX model is bundled as of Wave 0. |
| FFmpegKit audio | 6.0-2 | LGPL-3.0 | Retired upstream; replacement planned. LGPL code is combined with GPL-3 under GPL-3 terms. |

## Vendored source with attribution headers

| Component | Licence | Location |
| --- | --- | --- |
| Kyant0/backdrop v2.0.0 | Apache-2.0 | `app/src/main/kotlin/com/revenge/music/ui/component/backdrop/` |
| elyesmansour/compose-floating-tab-bar | Apache-2.0 | `app/src/main/kotlin/com/revenge/music/ui/component/floatingtabbar/` |
| Gradle wrapper | Apache-2.0 | `gradlew`, `gradlew.bat`, `gradle/wrapper/` |

## Gradle dependencies

| Group | Licence |
| --- | --- |
| androidx.* (activity, core, datastore, lifecycle, media3, palette, room, work) | Apache-2.0 |
| Kotlin stdlib, kotlinx.coroutines, kotlinx.serialization | Apache-2.0 |
| Hilt / Dagger, javax.inject | Apache-2.0 |
| Ktor, OkHttp, Retrofit, Gson, Coil, Timber | Apache-2.0 |
| Guava, commons-lang3, Kuromoji IPADIC, TinyPinyin | Apache-2.0 |
| Haze, Reorderable, Shimmer, Material Kolor, Lottie Compose, UCrop, lyrics-core | Apache-2.0 |
| Protobuf runtime | BSD-3-Clause |
| Brotli decoder | MIT |
| smooth-corner-rect | MIT |
| Konfetti | ISC |
| NewPipeExtractor, PipePipeExtractor, innertubex-android | GPL-3.0 |
| desugar_jdk_libs_nio | GPL-2.0 with Classpath Exception |
| JUnit, Robolectric, kotlinx-coroutines-test | EPL-1.0 / MIT / Apache-2.0 (test only) |

## GMS flavour

The `gms` flavour additionally links proprietary Google Play services (Cast,
Location, Firebase). Those components are **not** covered by this notice's
open-source licences, are not GPL-3.0 distributable as a prebuilt binary, and
must be built and distributed only in compliance with Google's terms. The
default `foss` flavour contains none of them.
