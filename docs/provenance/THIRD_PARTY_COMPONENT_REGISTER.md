# Third-Party Component & Bundled Asset Register (`11ae6207`)

**Baseline Audited:** `11ae62071f96db3b70557867ab1254dbcc5b125e`
**Scope:** Every bundled binary/asset (`782` files including `gradle-wrapper.jar`), vendored library source (`31` files: `Kyant0/backdrop` + `FloatingTabBar`), and external Gradle dependency (`34` groups).

---

## 1. Bundled ONNX Neural Audio Models (`2` Files — `32,193,022` Bytes)

| Path | Size (B) | SHA-256 | ModelProto Metadata | Upstream Candidate & License | Redistribution Permission | Required Notices | Modification Status | Necessity | Classification | Disposition |
|---|---:|---|---|---|---|---|---|---|---|---|
| `app/src/main/assets/beat_this_final0_q8.onnx` | 23,138,816 | `7801f9c1adfd5ff193f37ececa3320bf1fda1ddee2bf7de341cea4c25ffd0acd` | ir=8, opset=[{'domain': '', 'version': 17}, {'domain': 'com.microsoft.nchwc', 'version': 1}, {'domain': 'ai.onnx.ml', 'version': 5}, {'domain': 'ai.onnx.training', 'version': 1}, {'domain': 'ai.onnx.preview', 'version': 1}, {'domain': 'ai.onnx.preview.training', 'version': 1}, {'domain': 'com.microsoft.experimental', 'version': 1}, {'domain': 'com.microsoft', 'version': 1}, {'domain': 'org.pytorch.aten', 'version': 1}], producer=`onnx.quantize`, domain=`None`, doc_string=`None`, nodes=1989, in=['input_spectrogram'], out=['beat', 'downbeat'] | https://github.com/CPJKU/beat_this (MIT (CPJKU/beat_this PyTorch code/weights; ONNX quantizer origin unverified in binary); unverified in binary) | UNVERIFIED (zero embedded license/author/model-card metadata in ONNX ModelProto; BeatTracker.kt lacks provenance URL) | MIT notice if verified/re-exported from CPJKU/beat_this | Exact SHA match to Echo-Music (int8 quantized ONNX) (EchoSHA=True) | Referenced by `BeatTracker.kt` | `BLOCKED` | Escalate BLOCKED gate in M2: re-export/quantize directly from verified upstream CPJKU/beat_this (MIT) with checksum & notice, or replace with clean-room beat tracker. |
| `app/src/main/assets/vocals_umxhq_int8.onnx` | 9,054,206 | `a2be987b55a29bc149d3a6ae99b08175d81f85ee292a8ea21f96c3a473bc94cb` | ir=8, opset=[{'domain': '', 'version': 17}, {'domain': 'com.microsoft', 'version': 1}], producer=`onnx.quantize`, domain=`None`, doc_string=`None`, nodes=114, in=['mix_magnitude'], out=['target_magnitude'] | https://github.com/sigsep/open-unmix-pytorch (MIT (sigsep/open-unmix-pytorch umxhq; ONNX quantizer origin unverified in binary); unverified in binary) | UNVERIFIED (zero embedded license/author/model-card metadata in ONNX ModelProto) | N/A if removed | Exact SHA match to Echo-Music; ZERO references anywhere in Revenge codebase (EchoSHA=True) | **0 code refs anywhere (dead 9.05 MB asset)** | `BLOCKED` | Escalate BLOCKED gate in M2 with recommendation to approve REMOVE (zero code references anywhere in baseline). |

---

## 2. Bundled Fonts (`5` TTF Files — `8,909,800` Bytes)

| Path | Size (B) | SHA-256 | Publisher / Version | License | Redistribution Permission | Required Notices | Modification Status | Necessity | Classification | Disposition |
|---|---:|---|---|---|---|---|---|---|---|---|
| `app/src/main/res/font/bbh_bartle_regular.ttf` | 17,880 | `6240252862fa9dadea44af7e9eb119320c89c667c9ef176bbff1d77241965f3a` | Studio Drama / Studio Drama / `Version 1.000` | OFL-1.1 | Permitted (SIL OFL 1.1) | Include `OFL.txt` + Reserved Font Name notice in `THIRD_PARTY_NOTICES.md` | Unmodified binary (`EchoSHA=True`) | App typography / FontSelectionScreen | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` | Replace bundled copy with clean upstream Google Fonts / GitHub OFL-1.1 binary, verify SHA-256, and add OFL-1.1 text + font attribution to THIRD_PARTY_NOTICES.md. |
| `app/src/main/res/font/google_sans_flex.ttf` | 4,607,592 | `9db5a1555133dad228ed52dc619ab62018e85d2aaf39f8acae11b2ed388d34cf` | Google LLC / Google Sans Authors / `Version 2.003;[8fdf84be0]` | Proprietary (Google LLC, All Rights Reserved) | **FORBIDDEN** (`"Google Sans Flex is not one of them"`) | N/A — Must remove | Unmodified binary (`EchoSHA=True`) | Wired in Type.kt (rebind to sans_flex.ttf) | `REMOVE` | Delete google_sans_flex.ttf immediately in Wave 0 and rebind GoogleSansFontFamily/Type.kt to verified OFL-1.1 sans_flex.ttf (or Roboto/Sans Flex). |
| `app/src/main/res/font/outfit.ttf` | 110,884 | `fc7287273e66929776e2ba54f144fe699080bec29f61bf649d70d871468aeade` | fragTYPE / Rodrigo Fuenzalida / `Version 1.100;gftools[0.9.27]` | OFL-1.1 | Permitted (SIL OFL 1.1) | Include `OFL.txt` + Reserved Font Name notice in `THIRD_PARTY_NOTICES.md` | Unmodified binary (`EchoSHA=True`) | App typography / FontSelectionScreen | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` | Replace bundled copy with clean upstream Google Fonts / GitHub OFL-1.1 binary, verify SHA-256, and add OFL-1.1 text + font attribution to THIRD_PARTY_NOTICES.md. |
| `app/src/main/res/font/plus_jakarta_sans.ttf` | 176,288 | `89b3fb38aa0d275d7a731d0d817a4f1622b316b4d7fbdedcf02ee9099ff68bc8` | Tokotype / Gumpita Rahayu / `Version 2.071;gftools[0.9.30]` | OFL-1.1 | Permitted (SIL OFL 1.1) | Include `OFL.txt` + Reserved Font Name notice in `THIRD_PARTY_NOTICES.md` | Unmodified binary (`EchoSHA=True`) | App typography / FontSelectionScreen | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` | Replace bundled copy with clean upstream Google Fonts / GitHub OFL-1.1 binary, verify SHA-256, and add OFL-1.1 text + font attribution to THIRD_PARTY_NOTICES.md. |
| `app/src/main/res/font/sans_flex.ttf` | 3,997,148 | `2510a8b7a24beb1fe8163e9a49813ccfe96b5453444b9443d42665ca4fa320c9` | Google LLC / Google Sans Authors / `Version 3.007;[58cd9cb9b]` | OFL-1.1 | Permitted (SIL OFL 1.1) | Include `OFL.txt` + Reserved Font Name notice in `THIRD_PARTY_NOTICES.md` | Unmodified binary (`EchoSHA=True`) | App typography / FontSelectionScreen | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` | Replace bundled copy with clean upstream Google Fonts / GitHub OFL-1.1 binary, verify SHA-256, and add OFL-1.1 text + font attribution to THIRD_PARTY_NOTICES.md. |

---

## 3. Bundled JavaScript & WebView Assets (`4` Files — `416,799` Bytes)

| Path | Size (B) | SHA-256 | Upstream Source URL | License | Redistribution Permission | Required Notices | Modification Status | Necessity | Classification | Disposition |
|---|---:|---|---|---|---|---|---|---|---|---|
| `app/src/main/assets/po_token.html` | 8,824 | `962ab645d09618ec6bd4c891abf22c396c215bb2a614224db47eddff794e5825` | https://github.com/LuanRT/BgUtils | Unverified / Echo-authored wrapper (references BgUtils v3.2.0) | Echo-authored wrapper (not redistributable under R2) | Replace via clean-room implementation | EchoSHA=True, no license header | PoTokenWebView BotGuard | `REIMPLEMENT` | Clean-room reimplement BotGuard WebView HTML/JS bridge in Wave 3 from public WebView/BotGuard interface spec (or verified MIT/Apache BgUtils package with notice). |
| `app/src/main/assets/solver/astring.js` | 18,223 | `f25643f679e11b65455ec25a6471b40504c97518b95af9428a02f1445ebbd249` | https://github.com/davidbonnet/astring | MIT (davidbonnet/astring; header stripped in minified copy) | Permitted with copyright/license notice | Restore/preserve upstream license header & add to `THIRD_PARTY_NOTICES.md` | EchoSHA=True, header stripped | EjsNTransformSolver SABR AST | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` | Replace with verified pinned upstream davidbonnet/astring (MIT) bundle preserving original copyright & license header and record in THIRD_PARTY_NOTICES.md. |
| `app/src/main/assets/solver/meriyah.js` | 370,754 | `e3de32534ee99529a244c3b218da1ff7603699d96d228b49e8b1d44e42aac703` | https://github.com/meriyah/meriyah | ISC (meriyah/meriyah; header stripped in minified copy) | Permitted with copyright/license notice | Restore/preserve upstream license header & add to `THIRD_PARTY_NOTICES.md` | EchoSHA=True, header stripped | EjsNTransformSolver SABR AST | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` | Replace with verified pinned upstream meriyah/meriyah (ISC) bundle preserving original copyright & license header and record in THIRD_PARTY_NOTICES.md. |
| `app/src/main/assets/solver/yt.solver.core.js` | 18,998 | `b40f174743ac0318878b5f729f2fee6db3c825049d88a94253216506f6246344` | https://github.com/yt-dlp/ejs | Unlicense (SPDX-License-Identifier: Unlicense, yt-dlp/ejs) | Permitted with copyright/license notice | Restore/preserve upstream license header & add to `THIRD_PARTY_NOTICES.md` | EchoSHA=True, SPDX Unlicense present | EjsNTransformSolver SABR AST | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` | Fetch/pin verified upstream yt-dlp/ejs release artifact, verify SHA-256, and record Unlicense notice in THIRD_PARTY_NOTICES.md. |

---

## 4. Native C/C++ & FFTW Archive (`vibrafp`, `23` Files — `4,235,527` Bytes)

All `23` files under `app/src/main/cpp/vibrafp/` are **100% unbuilt dead code** (`app/build.gradle.kts` has no `externalNativeBuild`; `VibraSignature.kt` delegates directly to pure-Kotlin `ShazamSignatureGenerator.fromI16()`). All `23` are classified `REMOVE`.

| Path | Size (B) | SHA-256 | EchoSHA | Upstream / License | Modification Status | Classification |
|---|---:|---|---:|---|---|---|
| `app/src/main/cpp/vibrafp/include/vibra.h` | 3,519 | `4cb5a184995ddd726304005f99d76062c59bb0b9481624ca8d28ff9504af6d45` | True | Unverified / GPL-3.0 or MIT (vibra / vibrafp, unbuilt) | Exact Echo-Music copy | `REMOVE` |
| `app/src/main/cpp/vibrafp/lib/algorithm/frequency.cpp` | 925 | `3906548f6b68e6368210e5f23739236f1de2cecbbef3e29f1cfa5b02adc644e6` | True | Unverified / GPL-3.0 or MIT (vibra / vibrafp, unbuilt) | Exact Echo-Music copy | `REMOVE` |
| `app/src/main/cpp/vibrafp/lib/algorithm/frequency.h` | 1,116 | `4f93ff779472abb2b0ff43120857b4d1a04690fa107f726a7bf682ff7f08fbaa` | True | Unverified / GPL-3.0 or MIT (vibra / vibrafp, unbuilt) | Exact Echo-Music copy | `REMOVE` |
| `app/src/main/cpp/vibrafp/lib/algorithm/signature.cpp` | 3,185 | `71472dbdbff28905a1bda23572679be956f326e06562feccafff5a6418d5f096` | True | Unverified / GPL-3.0 or MIT (vibra / vibrafp, unbuilt) | Exact Echo-Music copy | `REMOVE` |
| `app/src/main/cpp/vibrafp/lib/algorithm/signature.h` | 2,008 | `51fecae6da368fe47381232314778f35162cd2ff7a4339ae875fd95e4761c258` | True | Unverified / GPL-3.0 or MIT (vibra / vibrafp, unbuilt) | Exact Echo-Music copy | `REMOVE` |
| `app/src/main/cpp/vibrafp/lib/algorithm/signature_generator.cpp` | 8,793 | `46004c90f157cf1ae17acf46933a0efa318d5a4e570f8f1bb7c411a8097729b1` | True | Unverified / GPL-3.0 or MIT (vibra / vibrafp, unbuilt) | Exact Echo-Music copy | `REMOVE` |
| `app/src/main/cpp/vibrafp/lib/algorithm/signature_generator.h` | 1,433 | `5045979e2f2d7056394e6a98ad5c9c6bd7c7a3525210006fed2cbf965f70549b` | True | Unverified / GPL-3.0 or MIT (vibra / vibrafp, unbuilt) | Exact Echo-Music copy | `REMOVE` |
| `app/src/main/cpp/vibrafp/lib/audio/byte_control.h` | 1,584 | `6112cb0a95a7b61e0ebae914c8e2308740e009a7971f5b921ae5c79b51015a94` | True | Unverified / GPL-3.0 or MIT (vibra / vibrafp, unbuilt) | Exact Echo-Music copy | `REMOVE` |
| `app/src/main/cpp/vibrafp/lib/audio/downsampler.cpp` | 7,794 | `d0e176488b72a10ee0d891e93fd8cc850be665c1faa982b51dc61165cff4c698` | True | Unverified / GPL-3.0 or MIT (vibra / vibrafp, unbuilt) | Exact Echo-Music copy | `REMOVE` |
| `app/src/main/cpp/vibrafp/lib/audio/downsampler.h` | 2,566 | `d6a8c464076ec1dfe58828e401c7bc22c7a47744d47f9d40fe5fbeda27734c7c` | True | Unverified / GPL-3.0 or MIT (vibra / vibrafp, unbuilt) | Exact Echo-Music copy | `REMOVE` |
| `app/src/main/cpp/vibrafp/lib/audio/wav.cpp` | 3,511 | `d32b32ae755dd4500974d170d562c993aeb0a982821d3a2dbe814dfc4ea1210e` | True | MIT (or vibrafp upstream) | Exact Echo-Music copy | `REMOVE` |
| `app/src/main/cpp/vibrafp/lib/audio/wav.h` | 2,456 | `d5fdbb5314b0a492588c18232c33e80d30a68399479807d6335b84cab673726a` | True | Unverified / GPL-3.0 or MIT (vibra / vibrafp, unbuilt) | Exact Echo-Music copy | `REMOVE` |
| `app/src/main/cpp/vibrafp/lib/CMakeLists.txt` | 5,618 | `763e7a792cc4d515a5e89d1b10abe2e818543b5a8b28eaedf7f66c6b5bdbf3be` | True | Unverified / GPL-3.0 or MIT (vibra / vibrafp, unbuilt) | Exact Echo-Music copy | `REMOVE` |
| `app/src/main/cpp/vibrafp/lib/utils/base64.h` | 1,614 | `79fcf70be00c448f408a3740b83c1e12e2d703f7d25a74eaf943cad23cfd135a` | True | Unverified / GPL-3.0 or MIT (vibra / vibrafp, unbuilt) | Exact Echo-Music copy | `REMOVE` |
| `app/src/main/cpp/vibrafp/lib/utils/crc32.h` | 612 | `9655566d2ff59abe494fd402c564d6822caf8d5ee1c5ba67e81734049df8cf21` | True | Unverified / GPL-3.0 or MIT (vibra / vibrafp, unbuilt) | Exact Echo-Music copy | `REMOVE` |
| `app/src/main/cpp/vibrafp/lib/utils/fft.h` | 2,334 | `5d8e15d123c32a300645007332f994c1bfa08c78d5ae6e4343010ec54e9c4348` | True | Unverified / GPL-3.0 or MIT (vibra / vibrafp, unbuilt) | Exact Echo-Music copy | `REMOVE` |
| `app/src/main/cpp/vibrafp/lib/utils/hanning.h` | 30,517 | `f3218bb188c8802475d4ee4d5c5febb5d319ce56a055722f6491fb625d2c3b13` | True | Unverified / GPL-3.0 or MIT (vibra / vibrafp, unbuilt) | Exact Echo-Music copy | `REMOVE` |
| `app/src/main/cpp/vibrafp/lib/utils/ring_buffer.h` | 1,568 | `89d559f2a25180ed16650832032a798eacd57022fe3113c245fdf3f0ab1bce81` | True | Unverified / GPL-3.0 or MIT (vibra / vibrafp, unbuilt) | Exact Echo-Music copy | `REMOVE` |
| `app/src/main/cpp/vibrafp/lib/vibra.cpp` | 2,321 | `503e90c8912e8917e9436cec24a0051819751a94c9ea6ba77fe0c4909459f2c0` | True | Unverified / GPL-3.0 or MIT (vibra / vibrafp, unbuilt) | Exact Echo-Music copy | `REMOVE` |
| `app/src/main/cpp/vibrafp/lib/vibra.sym` | 92 | `02a5c1ceddf93a43235b7d50f2a35a59fd76bec3474fc7b9be49e9b79daa79bb` | False | Unverified / GPL-3.0 or MIT (vibra / vibrafp, unbuilt) | Package-renamed to com.revenge.music | `REMOVE` |
| `app/src/main/cpp/vibrafp/lib/vibra_jni.cpp` | 2,776 | `e6796384f29d5478d5e6ae0116d1066d392691c837e94378e6c0d65ae0affd69` | False | Unverified / GPL-3.0 or MIT (vibra / vibrafp, unbuilt) | Package-renamed to com.revenge.music | `REMOVE` |
| `app/src/main/cpp/vibrafp/third_party/build-fftw-android.sh` | 7,291 | `cfedc90673a90cb245706bb47d96c042781801757c4776181f960a8ce44ddf87` | True | Unverified / GPL-3.0 or MIT (vibra / vibrafp, unbuilt) | Exact Echo-Music copy | `REMOVE` |
| `app/src/main/cpp/vibrafp/third_party/fftw-3.3.10.tar.gz` | 4,144,100 | `56c932549852cddcfafdab3820b0200c7742675be92179e59e6215b340e26467` | True | GPL-2.0-or-later (FFTW 3.3.10, MIT / Matteo Frigo) | Exact Echo-Music copy | `REMOVE` |

---

## 5. Data JSONs, AutoEQ Index, Room Schemas & Protobuf (`53` Files)

### 5.1 Non-Schema JSON & Protobuf Files (`5` Files)

| Path | Size (B) | SHA-256 | Upstream URL & License | Modification / Corruption Status | Necessity | Classification | Disposition |
|---|---:|---|---|---|---|---|---|
| `app/src/main/res/raw/autoeq_index.json` | 843,383 | `c3311a84314ce3a5a0d25401305ecafb6fd8bf9eb64c8814739e73612fe1a2fe` | `https://github.com/jaakkopasanen/AutoEq` (MIT) | **CORRUPTED IN 11 ENTRIES** by naive `Echo`->`Revenge` replace (`Amazon Echo Buds`->`Amazon Revenge Buds` [515–518], `Echobox Finder X1`->`Revengebox Finder X1` [2215–2217], `Tanchjim Echo`->`Tanchjim Revenge` [7624–7627]) | EQ AutoEQ presets (`EQProfileRepository.kt`) | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` | Fetch clean upstream AutoEq MIT index + add MIT notice |
| `app/src/main/res/raw/setting.json` | 4,638 | `9fcd39005f93791ddca1f16b6d6990433b51a37d2dd457c53b208cc33e2a15ba` | Unknown Lottie animation (`EchoSHA=True`; 0 license metadata) | Unmodified Echo copy | Settings Lottie icon | `BLOCKED` | M2 gate: replace with Apache-2.0/MIT Lottie or Material/Compose icon |
| `app/src/main/assets/player_configs.json` | 31,489 | `b4c668208fbf9ad9d9ddebff20dd816c4f3937d9b311fd4bfc58d278b7d138e3` | YouTube player config metadata (`EchoSHA=True`) | Unmodified Echo copy | CipherWebView config lookup | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` | Replace with verified upstream generator or clean runtime fetch |
| `app/src/main/assets/player_dates.json` | 8,599 | `fa5fa745aee0b3b535a02e39d0b1f8574e165ea46e8fbf918f50ed0eb8e51791` | YouTube player date lookup (`EchoSHA=True`) | Unmodified Echo copy | CipherWebView date lookup | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` | Replace with verified upstream generator or clean runtime fetch |
| `app/src/main/proto/listentogether.proto` | 3,593 | `5ec9dd9256e590eee83c3740252a4413b82ef3e12a5e752135a1910107fd0975` | Echo-authored proto3 (`100%` token/line match) | Java package renamed only | Listen Together WebSocket sync | `REIMPLEMENT` | Author clean-room `.proto` from wire protocol spec in Wave 5 |

### 5.2 Declarative Room Database Schemas (`48` Files: `core/schemas/com.revenge.music.db.InternalDatabase/1.json` .. `48.json`)
- **Origin & Checksums:** Auto-generated SQLite schema JSON snapshots (`1.json`–`48.json`), matching `Echo-Music` by `ExactRawSHA` (moved from `com.music.echo.db.InternalDatabase/`).
- **Classification:** `RETAIN` (provisional migration-compatibility artifacts for Room v48; confirm in M2/M3 whether to retain v1–v48 upgrade compatibility or reset cleanly).

---

## 6. Vendored Kotlin Libraries & Build Tooling Binaries (`32` Files)

| Component | Paths / Count | Upstream URL & Version | License | Modification Status | Classification | Disposition |
|---|---:|---|---|---|---|---|
| `Kyant0/backdrop` | `30` files (`app/.../ui/component/backdrop/**/*.kt`) | `https://github.com/Kyant0/backdrop` (`v2.0.0`, `Copyright 2025 Kyant0`) | Apache-2.0 | Vendored source with intact Apache-2.0 header | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` | Switch to Maven `io.github.kyant0:backdrop` or clean upstream source + `THIRD_PARTY_NOTICES.md` |
| `compose-floating-tab-bar` | `1` file (`.../floatingtabbar/FloatingTabBar.kt`) | `https://github.com/elyesmansour/compose-floating-tab-bar` (`v1.0.1`) | Apache-2.0 | Contains Echo-matched local modifications (`tok=0.9906`) | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` | Replace with clean upstream `v1.0.1`/Compose M3 tab bar + preserve Apache-2.0 notice |
| `gradle-wrapper.jar` | `1` file (`gradle/wrapper/gradle-wrapper.jar`, `48,749` B, `3b8a25775a69158b...`) | Gradle `9.9.1` (`gradle.org`) | Apache-2.0 | Unmodified (`EchoSHA=True`, 35 JAR entries) | `RETAIN` | Retain standard Gradle wrapper; list in `THIRD_PARTY_NOTICES.md` |

---

## 7. Complete Explicit Register of All `334` Raster Images (`334/334` `EchoSHA=True`, `0` `.svg`)

Summary: `98` `BLOCKED` (`86` celebrity likenesses + `12` personal/Apple/UPI rasters), `228` `REMOVE` (`228` Echo/meme launcher PNGs incl. `17` `revenge_cat_icon*` == `echo_cat_icon*`), `8` `REIMPLEMENT` (`8` UI/notification PNGs).

| Path | Ext | Size (B) | SHA-256 (short) | Echo Exact Path | Category | Classification |
|---|---|---:|---|---|---|---|
| `app/src/main/res/drawable-nodpi/birthday_image.png` | `.png` | 1,757,869 | `87d50cad8863fc9f` | `app/src/main/res/drawable-nodpi/birthday_image.png` | `personal_photo_trademark_or_echo_ui` | `BLOCKED` |
| `app/src/main/res/drawable/ai_pfp.png` | `.png` | 836,320 | `b006e34880b54962` | `app/src/main/res/drawable/ai_pfp.png` | `personal_photo_trademark_or_echo_ui` | `BLOCKED` |
| `app/src/main/res/drawable/apple_airpods.png` | `.png` | 1,195 | `64efdea038aafc39` | `app/src/main/res/drawable/apple_airpods.png` | `personal_photo_trademark_or_echo_ui` | `BLOCKED` |
| `app/src/main/res/drawable/apple_headset.png` | `.png` | 1,368 | `955c8ee8ffff7f8b` | `app/src/main/res/drawable/apple_headset.png` | `personal_photo_trademark_or_echo_ui` | `BLOCKED` |
| `app/src/main/res/drawable/apple_lyrics.png` | `.png` | 721 | `90e855a40d365e39` | `app/src/main/res/drawable/apple_lyrics.png` | `personal_photo_trademark_or_echo_ui` | `BLOCKED` |
| `app/src/main/res/drawable/collab.jpg` | `.jpg` | 85,433 | `5533457b436de827` | `app/src/main/res/drawable/collab.jpg` | `personal_photo_trademark_or_echo_ui` | `BLOCKED` |
| `app/src/main/res/drawable/dev.jpg` | `.jpg` | 151,514 | `56a1c731788823c8` | `app/src/main/res/drawable/dev.jpg` | `personal_photo_trademark_or_echo_ui` | `BLOCKED` |
| `app/src/main/res/drawable/preview_artwork.webp` | `.webp` | 26,686 | `062785038a7cd49a` | `app/src/main/res/drawable/preview_artwork.webp` | `personal_photo_trademark_or_echo_ui` | `BLOCKED` |
| `app/src/main/res/drawable/songdna.png` | `.png` | 828,768 | `bcbdbb4b5697c0a8` | `app/src/main/res/drawable/songdna.png` | `personal_photo_trademark_or_echo_ui` | `BLOCKED` |
| `app/src/main/res/drawable/speaker_applemusic.png` | `.png` | 991 | `1764a1924d18455f` | `app/src/main/res/drawable/speaker_applemusic.png` | `personal_photo_trademark_or_echo_ui` | `BLOCKED` |
| `app/src/main/res/drawable/tast.png` | `.png` | 845,677 | `bcd8d87f24acc949` | `app/src/main/res/drawable/tast.png` | `personal_photo_trademark_or_echo_ui` | `BLOCKED` |
| `app/src/main/res/drawable/upi_new.png` | `.png` | 1,351 | `6df67f7bf8333827` | `app/src/main/res/drawable/upi_new.png` | `personal_photo_trademark_or_echo_ui` | `BLOCKED` |
| `app/src/main/res/drawable/weeknd.webp` | `.webp` | 40,064 | `6b439991c4a912a5` | `app/src/main/res/drawable/weeknd.webp` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-hdpi/billie_eilish_icon.png` | `.png` | 10,939 | `acf5c5293ef2ab53` | `app/src/main/res/mipmap-hdpi/billie_eilish_icon.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-hdpi/billie_eilish_icon_foreground.png` | `.png` | 35,475 | `cf72dcb504c474a7` | `app/src/main/res/mipmap-hdpi/billie_eilish_icon_foreground.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-hdpi/billie_eilish_icon_round.png` | `.png` | 11,241 | `1a0417a30898c003` | `app/src/main/res/mipmap-hdpi/billie_eilish_icon_round.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-hdpi/ic_launcher_millie.png` | `.png` | 11,124 | `fc64a006b40b8299` | `app/src/main/res/mipmap-hdpi/ic_launcher_millie.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-hdpi/ic_launcher_millie_foreground.png` | `.png` | 32,219 | `cb29e5a7715941bb` | `app/src/main/res/mipmap-hdpi/ic_launcher_millie_foreground.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-hdpi/ic_launcher_millie_round.png` | `.png` | 11,600 | `dd34da4d5d00e3ea` | `app/src/main/res/mipmap-hdpi/ic_launcher_millie_round.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-hdpi/ic_launcher_sabrina.png` | `.png` | 10,707 | `864cb4f49cce92e5` | `app/src/main/res/mipmap-hdpi/ic_launcher_sabrina.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-hdpi/ic_launcher_sabrina2.png` | `.png` | 10,650 | `baa4da369270538a` | `app/src/main/res/mipmap-hdpi/ic_launcher_sabrina2.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-hdpi/ic_launcher_sabrina2_foreground.png` | `.png` | 56,350 | `93835b8cc405b4c4` | `app/src/main/res/mipmap-hdpi/ic_launcher_sabrina2_foreground.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-hdpi/ic_launcher_sabrina2_round.png` | `.png` | 11,351 | `9301f6ae4f46ad32` | `app/src/main/res/mipmap-hdpi/ic_launcher_sabrina2_round.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-hdpi/ic_launcher_sabrina_foreground.png` | `.png` | 40,545 | `278be351b314b736` | `app/src/main/res/mipmap-hdpi/ic_launcher_sabrina_foreground.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-hdpi/ic_launcher_sabrina_round.png` | `.png` | 11,323 | `49a0ad90ff709731` | `app/src/main/res/mipmap-hdpi/ic_launcher_sabrina_round.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-hdpi/ic_launcher_sadie.png` | `.png` | 10,360 | `a0c17f210663679f` | `app/src/main/res/mipmap-hdpi/ic_launcher_sadie.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-hdpi/ic_launcher_sadie_foreground.png` | `.png` | 34,380 | `f0aa032a2c726784` | `app/src/main/res/mipmap-hdpi/ic_launcher_sadie_foreground.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-hdpi/ic_launcher_sadie_round.png` | `.png` | 10,967 | `4ad4dfc8a43697f7` | `app/src/main/res/mipmap-hdpi/ic_launcher_sadie_round.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-ldpi/billie_eilish_icon.png` | `.png` | 3,300 | `79bdcc160f0f3309` | `app/src/main/res/mipmap-ldpi/billie_eilish_icon.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-ldpi/billie_eilish_icon_round.png` | `.png` | 3,329 | `44170811a7bc30ca` | `app/src/main/res/mipmap-ldpi/billie_eilish_icon_round.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-ldpi/ic_launcher_millie.png` | `.png` | 3,255 | `46eb0064c4f62b46` | `app/src/main/res/mipmap-ldpi/ic_launcher_millie.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-ldpi/ic_launcher_millie_round.png` | `.png` | 3,304 | `eaffd51f3c7d8af7` | `app/src/main/res/mipmap-ldpi/ic_launcher_millie_round.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-ldpi/ic_launcher_sabrina.png` | `.png` | 3,255 | `51ca46c5e8f6a389` | `app/src/main/res/mipmap-ldpi/ic_launcher_sabrina.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-ldpi/ic_launcher_sabrina2.png` | `.png` | 3,137 | `a3e82026af1ee85a` | `app/src/main/res/mipmap-ldpi/ic_launcher_sabrina2.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-ldpi/ic_launcher_sabrina2_round.png` | `.png` | 3,230 | `d1ef91de144b716f` | `app/src/main/res/mipmap-ldpi/ic_launcher_sabrina2_round.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-ldpi/ic_launcher_sabrina_round.png` | `.png` | 3,324 | `b320de9aeedbcfba` | `app/src/main/res/mipmap-ldpi/ic_launcher_sabrina_round.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-ldpi/ic_launcher_sadie.png` | `.png` | 3,092 | `5a339332f76375d2` | `app/src/main/res/mipmap-ldpi/ic_launcher_sadie.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-ldpi/ic_launcher_sadie_round.png` | `.png` | 3,218 | `67c97b4ae05297f2` | `app/src/main/res/mipmap-ldpi/ic_launcher_sadie_round.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-mdpi/billie_eilish_icon.png` | `.png` | 5,152 | `dc5c72bac5d9db49` | `app/src/main/res/mipmap-mdpi/billie_eilish_icon.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-mdpi/billie_eilish_icon_foreground.png` | `.png` | 17,176 | `aaf74b78c37a79f1` | `app/src/main/res/mipmap-mdpi/billie_eilish_icon_foreground.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-mdpi/billie_eilish_icon_round.png` | `.png` | 5,438 | `39c0d7c510d14eaf` | `app/src/main/res/mipmap-mdpi/billie_eilish_icon_round.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-mdpi/ic_launcher_millie.png` | `.png` | 5,160 | `c80395030ace3e71` | `app/src/main/res/mipmap-mdpi/ic_launcher_millie.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-mdpi/ic_launcher_millie_foreground.png` | `.png` | 15,162 | `558fe946fc439fd7` | `app/src/main/res/mipmap-mdpi/ic_launcher_millie_foreground.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-mdpi/ic_launcher_millie_round.png` | `.png` | 5,464 | `f529a8846447e963` | `app/src/main/res/mipmap-mdpi/ic_launcher_millie_round.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-mdpi/ic_launcher_sabrina.png` | `.png` | 5,072 | `92cc31dba06c052f` | `app/src/main/res/mipmap-mdpi/ic_launcher_sabrina.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-mdpi/ic_launcher_sabrina2.png` | `.png` | 4,971 | `efa6a2f957cd22c9` | `app/src/main/res/mipmap-mdpi/ic_launcher_sabrina2.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-mdpi/ic_launcher_sabrina2_foreground.png` | `.png` | 27,122 | `202c3d18e23289de` | `app/src/main/res/mipmap-mdpi/ic_launcher_sabrina2_foreground.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-mdpi/ic_launcher_sabrina2_round.png` | `.png` | 5,388 | `b1ef2f228aa805eb` | `app/src/main/res/mipmap-mdpi/ic_launcher_sabrina2_round.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-mdpi/ic_launcher_sabrina_foreground.png` | `.png` | 18,589 | `247fc9e89adccd25` | `app/src/main/res/mipmap-mdpi/ic_launcher_sabrina_foreground.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-mdpi/ic_launcher_sabrina_round.png` | `.png` | 5,474 | `3757f092bb562063` | `app/src/main/res/mipmap-mdpi/ic_launcher_sabrina_round.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-mdpi/ic_launcher_sadie.png` | `.png` | 4,946 | `510c6f1ada49f1f3` | `app/src/main/res/mipmap-mdpi/ic_launcher_sadie.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-mdpi/ic_launcher_sadie_foreground.png` | `.png` | 16,358 | `f190fde2dfd24d0d` | `app/src/main/res/mipmap-mdpi/ic_launcher_sadie_foreground.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-mdpi/ic_launcher_sadie_round.png` | `.png` | 5,257 | `5d7e9c1252a41e37` | `app/src/main/res/mipmap-mdpi/ic_launcher_sadie_round.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-xhdpi/billie_eilish_icon.png` | `.png` | 17,862 | `9398686922bdba91` | `app/src/main/res/mipmap-xhdpi/billie_eilish_icon.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-xhdpi/billie_eilish_icon_foreground.png` | `.png` | 58,986 | `99b21ac0fe3d19fd` | `app/src/main/res/mipmap-xhdpi/billie_eilish_icon_foreground.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-xhdpi/billie_eilish_icon_round.png` | `.png` | 18,674 | `253202ffb6111e48` | `app/src/main/res/mipmap-xhdpi/billie_eilish_icon_round.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_millie.png` | `.png` | 18,571 | `baf6a8e565a22c7a` | `app/src/main/res/mipmap-xhdpi/ic_launcher_millie.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_millie_foreground.png` | `.png` | 55,141 | `7ffe4fe2edadefb1` | `app/src/main/res/mipmap-xhdpi/ic_launcher_millie_foreground.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_millie_round.png` | `.png` | 19,591 | `0e524df8bd51e5af` | `app/src/main/res/mipmap-xhdpi/ic_launcher_millie_round.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_sabrina.png` | `.png` | 17,774 | `fc3d37c21fc4a52a` | `app/src/main/res/mipmap-xhdpi/ic_launcher_sabrina.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_sabrina2.png` | `.png` | 17,804 | `0ee74df9a01a7a63` | `app/src/main/res/mipmap-xhdpi/ic_launcher_sabrina2.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_sabrina2_foreground.png` | `.png` | 96,217 | `fda8b47dd492bfcb` | `app/src/main/res/mipmap-xhdpi/ic_launcher_sabrina2_foreground.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_sabrina2_round.png` | `.png` | 19,171 | `0d33e2cbf891fd64` | `app/src/main/res/mipmap-xhdpi/ic_launcher_sabrina2_round.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_sabrina_foreground.png` | `.png` | 71,501 | `a33ba7a959d03571` | `app/src/main/res/mipmap-xhdpi/ic_launcher_sabrina_foreground.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_sabrina_round.png` | `.png` | 19,132 | `eed8f24a3e96987f` | `app/src/main/res/mipmap-xhdpi/ic_launcher_sabrina_round.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_sadie.png` | `.png` | 17,140 | `0dd3fb1dda1ce8e1` | `app/src/main/res/mipmap-xhdpi/ic_launcher_sadie.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_sadie_foreground.png` | `.png` | 58,208 | `9c53d9c0274c96b7` | `app/src/main/res/mipmap-xhdpi/ic_launcher_sadie_foreground.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_sadie_round.png` | `.png` | 18,292 | `fd88ecbc47ac91c4` | `app/src/main/res/mipmap-xhdpi/ic_launcher_sadie_round.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-xxhdpi/billie_eilish_icon.png` | `.png` | 36,864 | `34898c5e6d0667dc` | `app/src/main/res/mipmap-xxhdpi/billie_eilish_icon.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-xxhdpi/billie_eilish_icon_foreground.png` | `.png` | 125,555 | `77bf7069e687efff` | `app/src/main/res/mipmap-xxhdpi/billie_eilish_icon_foreground.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-xxhdpi/billie_eilish_icon_round.png` | `.png` | 38,834 | `aa89ae184b491807` | `app/src/main/res/mipmap-xxhdpi/billie_eilish_icon_round.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_millie.png` | `.png` | 39,320 | `f840f093f9fd6ba5` | `app/src/main/res/mipmap-xxhdpi/ic_launcher_millie.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_millie_foreground.png` | `.png` | 116,787 | `a5d52573788970ce` | `app/src/main/res/mipmap-xxhdpi/ic_launcher_millie_foreground.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_millie_round.png` | `.png` | 41,372 | `e90682866abe159c` | `app/src/main/res/mipmap-xxhdpi/ic_launcher_millie_round.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_sabrina.png` | `.png` | 37,447 | `5e7d67a9469c2225` | `app/src/main/res/mipmap-xxhdpi/ic_launcher_sabrina.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_sabrina2.png` | `.png` | 37,362 | `f59955f85019d00d` | `app/src/main/res/mipmap-xxhdpi/ic_launcher_sabrina2.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_sabrina2_foreground.png` | `.png` | 202,050 | `c1d435ea766ff904` | `app/src/main/res/mipmap-xxhdpi/ic_launcher_sabrina2_foreground.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_sabrina2_round.png` | `.png` | 40,395 | `3aed844189f8ddb5` | `app/src/main/res/mipmap-xxhdpi/ic_launcher_sabrina2_round.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_sabrina_foreground.png` | `.png` | 152,909 | `25b7ff1173a8dfc7` | `app/src/main/res/mipmap-xxhdpi/ic_launcher_sabrina_foreground.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_sabrina_round.png` | `.png` | 40,255 | `4ab1c33c5b7fb433` | `app/src/main/res/mipmap-xxhdpi/ic_launcher_sabrina_round.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_sadie.png` | `.png` | 35,666 | `cf6024777ff3fa13` | `app/src/main/res/mipmap-xxhdpi/ic_launcher_sadie.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_sadie_foreground.png` | `.png` | 122,984 | `2fd08f2f1c901e4b` | `app/src/main/res/mipmap-xxhdpi/ic_launcher_sadie_foreground.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_sadie_round.png` | `.png` | 38,364 | `57c4d3947ea7d4da` | `app/src/main/res/mipmap-xxhdpi/ic_launcher_sadie_round.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-xxxhdpi/billie_eilish_icon.png` | `.png` | 62,089 | `017d9660c9617bad` | `app/src/main/res/mipmap-xxxhdpi/billie_eilish_icon.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-xxxhdpi/billie_eilish_icon_foreground.png` | `.png` | 213,885 | `55eeddfe8ec18f44` | `app/src/main/res/mipmap-xxxhdpi/billie_eilish_icon_foreground.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-xxxhdpi/billie_eilish_icon_round.png` | `.png` | 65,043 | `79112fbb83490d62` | `app/src/main/res/mipmap-xxxhdpi/billie_eilish_icon_round.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_millie.png` | `.png` | 67,268 | `65a2c7ea5a0eb7ad` | `app/src/main/res/mipmap-xxxhdpi/ic_launcher_millie.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_millie_foreground.png` | `.png` | 201,058 | `07d360e3efc372a2` | `app/src/main/res/mipmap-xxxhdpi/ic_launcher_millie_foreground.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_millie_round.png` | `.png` | 70,344 | `5d6d678491f71921` | `app/src/main/res/mipmap-xxxhdpi/ic_launcher_millie_round.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_sabrina.png` | `.png` | 65,126 | `a3bcced90848994c` | `app/src/main/res/mipmap-xxxhdpi/ic_launcher_sabrina.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_sabrina2.png` | `.png` | 63,686 | `9cff294cf4bc72eb` | `app/src/main/res/mipmap-xxxhdpi/ic_launcher_sabrina2.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_sabrina2_foreground.png` | `.png` | 342,291 | `e7fefcfb7ae6799f` | `app/src/main/res/mipmap-xxxhdpi/ic_launcher_sabrina2_foreground.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_sabrina2_round.png` | `.png` | 68,575 | `7f9e76266e38c3f3` | `app/src/main/res/mipmap-xxxhdpi/ic_launcher_sabrina2_round.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_sabrina_foreground.png` | `.png` | 272,727 | `5cfd1ee0f50ab887` | `app/src/main/res/mipmap-xxxhdpi/ic_launcher_sabrina_foreground.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_sabrina_round.png` | `.png` | 69,553 | `97f080db8fff5321` | `app/src/main/res/mipmap-xxxhdpi/ic_launcher_sabrina_round.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_sadie.png` | `.png` | 60,410 | `8035fd556ae9f84a` | `app/src/main/res/mipmap-xxxhdpi/ic_launcher_sadie.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_sadie_foreground.png` | `.png` | 211,062 | `75e6fc40c6c766ec` | `app/src/main/res/mipmap-xxxhdpi/ic_launcher_sadie_foreground.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_sadie_round.png` | `.png` | 64,407 | `245196ea5c6d3574` | `app/src/main/res/mipmap-xxxhdpi/ic_launcher_sadie_round.png` | `celebrity_likeness_or_photo` | `BLOCKED` |
| `app/src/main/res/drawable-hdpi/ic_stat_name.png` | `.png` | 815 | `290d2df249cc023a` | `app/src/main/res/drawable-hdpi/ic_stat_name.png` | `echo_ui_or_notification_raster` | `REIMPLEMENT` |
| `app/src/main/res/drawable-mdpi/ic_stat_name.png` | `.png` | 564 | `df0d9024f3ba89ff` | `app/src/main/res/drawable-mdpi/ic_stat_name.png` | `echo_ui_or_notification_raster` | `REIMPLEMENT` |
| `app/src/main/res/drawable-xhdpi/ic_stat_name.png` | `.png` | 1,227 | `6b37b61f401e2a46` | `app/src/main/res/drawable-xhdpi/ic_stat_name.png` | `echo_ui_or_notification_raster` | `REIMPLEMENT` |
| `app/src/main/res/drawable-xxhdpi/ic_stat_name.png` | `.png` | 1,979 | `0838fd85116326be` | `app/src/main/res/drawable-xxhdpi/ic_stat_name.png` | `echo_ui_or_notification_raster` | `REIMPLEMENT` |
| `app/src/main/res/drawable-xxxhdpi/ic_stat_name.png` | `.png` | 2,953 | `1015347cdb132fdb` | `app/src/main/res/drawable-xxxhdpi/ic_stat_name.png` | `echo_ui_or_notification_raster` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_app_settings.png` | `.png` | 9,959 | `842d9d0f8b7a09e2` | `app/src/main/res/drawable/ic_app_settings.png` | `echo_ui_or_notification_raster` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_ringtone.png` | `.png` | 876 | `6d5211a37c7f2210` | `app/src/main/res/drawable/ic_ringtone.png` | `echo_ui_or_notification_raster` | `REIMPLEMENT` |
| `app/src/main/res/drawable/lyrics.png` | `.png` | 7,785 | `aa31aa18e83d0f2b` | `app/src/main/res/drawable/lyrics.png` | `echo_ui_or_notification_raster` | `REIMPLEMENT` |
| `app/src/main/res/eko_icon-web.png` | `.png` | 303,471 | `81cf946b2ea85b38` | `app/src/main/res/eko_icon-web.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/cat_icon.png` | `.png` | 10,477 | `572d474d89ea1756` | `app/src/main/res/mipmap-hdpi/cat_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/cat_icon_foreground.png` | `.png` | 29,168 | `0c9478d577650826` | `app/src/main/res/mipmap-hdpi/cat_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/cat_icon_round.png` | `.png` | 11,100 | `d82386c180b914be` | `app/src/main/res/mipmap-hdpi/cat_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/crazy_blue_icon.png` | `.png` | 11,529 | `abe42fb0dc157a76` | `app/src/main/res/mipmap-hdpi/crazy_blue_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/crazy_blue_icon_foreground.png` | `.png` | 42,949 | `e0968c0b6216655c` | `app/src/main/res/mipmap-hdpi/crazy_blue_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/crazy_blue_icon_round.png` | `.png` | 11,917 | `e245c0082a7f5f59` | `app/src/main/res/mipmap-hdpi/crazy_blue_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/eko_icon.png` | `.png` | 9,935 | `77dff998448479ec` | `app/src/main/res/mipmap-hdpi/eko_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/eko_icon_foreground.png` | `.png` | 34,949 | `2c97c49b4fff386c` | `app/src/main/res/mipmap-hdpi/eko_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/eko_icon_round.png` | `.png` | 11,116 | `b40f9e9ca77b6eb4` | `app/src/main/res/mipmap-hdpi/eko_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/ic_launcher_craft.png` | `.png` | 7,164 | `5cd84946523a10c3` | `app/src/main/res/mipmap-hdpi/ic_launcher_craft.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/ic_launcher_craft_foreground.png` | `.png` | 35,694 | `89355c53af46f552` | `app/src/main/res/mipmap-hdpi/ic_launcher_craft_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/ic_launcher_craft_round.png` | `.png` | 7,420 | `c8ac05ae03d95c70` | `app/src/main/res/mipmap-hdpi/ic_launcher_craft_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/ic_launcher_neon_glass.png` | `.png` | 8,521 | `21b66aa70d4ebefe` | `app/src/main/res/mipmap-hdpi/ic_launcher_neon_glass.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/ic_launcher_neon_glass_foreground.png` | `.png` | 21,280 | `2a8cabffee23fe6d` | `app/src/main/res/mipmap-hdpi/ic_launcher_neon_glass_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/ic_launcher_neon_glass_round.png` | `.png` | 10,680 | `8ac9f3ca28870e37` | `app/src/main/res/mipmap-hdpi/ic_launcher_neon_glass_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/legacy_icon.png` | `.png` | 6,325 | `080788db84dbab59` | `app/src/main/res/mipmap-hdpi/legacy_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/legacy_icon_foreground.png` | `.png` | 11,410 | `90e9869c81be75a0` | `app/src/main/res/mipmap-hdpi/legacy_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/legacy_icon_monochrome.png` | `.png` | 8,120 | `607b3aff750afa94` | `app/src/main/res/mipmap-hdpi/legacy_icon_monochrome.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/legacy_icon_round.png` | `.png` | 7,979 | `e2f57ab44dcdf019` | `app/src/main/res/mipmap-hdpi/legacy_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/legacy_pixel_icon.png` | `.png` | 4,691 | `453ecfc91a0d94d2` | `app/src/main/res/mipmap-hdpi/legacy_pixel_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/legacy_pixel_icon_foreground.png` | `.png` | 10,033 | `c3b28b943ec8bd3f` | `app/src/main/res/mipmap-hdpi/legacy_pixel_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/legacy_pixel_icon_round.png` | `.png` | 6,685 | `465563ae231f984d` | `app/src/main/res/mipmap-hdpi/legacy_pixel_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/new_pixel_icon.png` | `.png` | 8,070 | `3296e2bd78e15e0b` | `app/src/main/res/mipmap-hdpi/new_pixel_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/new_pixel_icon_foreground.png` | `.png` | 14,815 | `cb992a8a1f66fffd` | `app/src/main/res/mipmap-hdpi/new_pixel_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/new_pixel_icon_round.png` | `.png` | 9,322 | `e267e081e5df3bfc` | `app/src/main/res/mipmap-hdpi/new_pixel_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/pookie_icon.png` | `.png` | 11,278 | `562a0e0faf64bb03` | `app/src/main/res/mipmap-hdpi/pookie_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/pookie_icon_foreground.png` | `.png` | 46,738 | `0e9a0d1f491872f5` | `app/src/main/res/mipmap-hdpi/pookie_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/pookie_icon_round.png` | `.png` | 11,960 | `50638b131eeae9c3` | `app/src/main/res/mipmap-hdpi/pookie_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/revenge_cat_icon.png` | `.png` | 11,604 | `eef583974ce6b139` | `app/src/main/res/mipmap-hdpi/echo_cat_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/revenge_cat_icon_foreground.png` | `.png` | 38,989 | `7722f708472472cc` | `app/src/main/res/mipmap-hdpi/echo_cat_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/revenge_cat_icon_round.png` | `.png` | 12,191 | `42295c3d0db3bbc8` | `app/src/main/res/mipmap-hdpi/echo_cat_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/sky_icon.png` | `.png` | 10,802 | `fd8cf5ad52271a1c` | `app/src/main/res/mipmap-hdpi/sky_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/sky_icon_foreground.png` | `.png` | 39,913 | `8b12c38c63d15895` | `app/src/main/res/mipmap-hdpi/sky_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/sky_icon_round.png` | `.png` | 11,350 | `7c059f3a97cb8ed4` | `app/src/main/res/mipmap-hdpi/sky_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/tamil_naidu_icon.png` | `.png` | 11,355 | `bd1a211dc07abb9f` | `app/src/main/res/mipmap-hdpi/tamil_naidu_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/tamil_naidu_icon_foreground.png` | `.png` | 65,478 | `701ad313121f8ab6` | `app/src/main/res/mipmap-hdpi/tamil_naidu_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/tamil_naidu_icon_round.png` | `.png` | 11,592 | `eb8cb824761e9e55` | `app/src/main/res/mipmap-hdpi/tamil_naidu_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/wierd_cat_icon.png` | `.png` | 11,387 | `339f2b30de4524b9` | `app/src/main/res/mipmap-hdpi/wierd_cat_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/wierd_cat_icon_foreground.png` | `.png` | 59,948 | `2f9eb95de0fcee3d` | `app/src/main/res/mipmap-hdpi/wierd_cat_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-hdpi/wierd_cat_icon_round.png` | `.png` | 12,166 | `02f125d9e77f138b` | `app/src/main/res/mipmap-hdpi/wierd_cat_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/cat_icon.png` | `.png` | 3,191 | `b824825b64a1cb1f` | `app/src/main/res/mipmap-ldpi/cat_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/cat_icon_round.png` | `.png` | 3,297 | `33828df32421c273` | `app/src/main/res/mipmap-ldpi/cat_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/crazy_blue_icon.png` | `.png` | 3,501 | `8da28ac1685f1470` | `app/src/main/res/mipmap-ldpi/crazy_blue_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/crazy_blue_icon_round.png` | `.png` | 3,580 | `c055bdd40254232c` | `app/src/main/res/mipmap-ldpi/crazy_blue_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/eko_icon.png` | `.png` | 3,130 | `c4cd5e387b60c622` | `app/src/main/res/mipmap-ldpi/eko_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/eko_icon_round.png` | `.png` | 3,257 | `029da29cc5e0e1ce` | `app/src/main/res/mipmap-ldpi/eko_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/ic_launcher_craft.png` | `.png` | 2,621 | `68ed309de3865c51` | `app/src/main/res/mipmap-ldpi/ic_launcher_craft.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/ic_launcher_craft_round.png` | `.png` | 2,589 | `7ab7ffe3739a4644` | `app/src/main/res/mipmap-ldpi/ic_launcher_craft_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/ic_launcher_neon_glass.png` | `.png` | 2,719 | `e5b935c8b69d925d` | `app/src/main/res/mipmap-ldpi/ic_launcher_neon_glass.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/ic_launcher_neon_glass_round.png` | `.png` | 3,290 | `d54eb87357326303` | `app/src/main/res/mipmap-ldpi/ic_launcher_neon_glass_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/legacy_icon.png` | `.png` | 2,272 | `ffba14f0e4a86215` | `app/src/main/res/mipmap-ldpi/legacy_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/legacy_icon_round.png` | `.png` | 2,838 | `fae4ca5379bb9ee9` | `app/src/main/res/mipmap-ldpi/legacy_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/legacy_pixel_icon.png` | `.png` | 2,010 | `2302fc7ce3a57283` | `app/src/main/res/mipmap-ldpi/legacy_pixel_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/legacy_pixel_icon_round.png` | `.png` | 2,580 | `678ae09aa99b7be3` | `app/src/main/res/mipmap-ldpi/legacy_pixel_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/new_pixel_icon.png` | `.png` | 2,852 | `eb1486e8f3aaf3cd` | `app/src/main/res/mipmap-ldpi/new_pixel_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/new_pixel_icon_round.png` | `.png` | 3,073 | `29cc71f53f911bd8` | `app/src/main/res/mipmap-ldpi/new_pixel_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/pookie_icon.png` | `.png` | 3,316 | `7493157a82bbade0` | `app/src/main/res/mipmap-ldpi/pookie_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/pookie_icon_round.png` | `.png` | 3,392 | `2e9ce2c6c135c726` | `app/src/main/res/mipmap-ldpi/pookie_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/revenge_cat_icon.png` | `.png` | 3,548 | `e2aebbace3e24412` | `app/src/main/res/mipmap-ldpi/echo_cat_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/revenge_cat_icon_round.png` | `.png` | 3,619 | `42e9b4dd13490160` | `app/src/main/res/mipmap-ldpi/echo_cat_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/sky_icon.png` | `.png` | 3,332 | `060217b9336ed6ff` | `app/src/main/res/mipmap-ldpi/sky_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/sky_icon_round.png` | `.png` | 3,429 | `dec327e88a33064c` | `app/src/main/res/mipmap-ldpi/sky_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/tamil_naidu_icon.png` | `.png` | 3,423 | `98101386bb664bb9` | `app/src/main/res/mipmap-ldpi/tamil_naidu_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/tamil_naidu_icon_round.png` | `.png` | 3,400 | `6cc777a1626f1b08` | `app/src/main/res/mipmap-ldpi/tamil_naidu_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/wierd_cat_icon.png` | `.png` | 3,268 | `aed5803b6168c757` | `app/src/main/res/mipmap-ldpi/wierd_cat_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-ldpi/wierd_cat_icon_round.png` | `.png` | 3,386 | `92ae669ab9c7f7fa` | `app/src/main/res/mipmap-ldpi/wierd_cat_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/cat_icon.png` | `.png` | 5,030 | `278083b6a05164bc` | `app/src/main/res/mipmap-mdpi/cat_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/cat_icon_foreground.png` | `.png` | 14,041 | `fba110366aadf245` | `app/src/main/res/mipmap-mdpi/cat_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/cat_icon_round.png` | `.png` | 5,407 | `cfe513bfbd42f415` | `app/src/main/res/mipmap-mdpi/cat_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/crazy_blue_icon.png` | `.png` | 5,508 | `9dceadad29d9d359` | `app/src/main/res/mipmap-mdpi/crazy_blue_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/crazy_blue_icon_foreground.png` | `.png` | 21,628 | `c4faece01f6574a2` | `app/src/main/res/mipmap-mdpi/crazy_blue_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/crazy_blue_icon_round.png` | `.png` | 5,869 | `f2ee644892e2f474` | `app/src/main/res/mipmap-mdpi/crazy_blue_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/eko_icon.png` | `.png` | 4,769 | `37593588247f54b4` | `app/src/main/res/mipmap-mdpi/eko_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/eko_icon_foreground.png` | `.png` | 16,869 | `a210f716aa3cb7cc` | `app/src/main/res/mipmap-mdpi/eko_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/eko_icon_round.png` | `.png` | 5,248 | `ba25a80957e2eaf9` | `app/src/main/res/mipmap-mdpi/eko_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/ic_launcher_craft.png` | `.png` | 3,963 | `6c5eadc90defe250` | `app/src/main/res/mipmap-mdpi/ic_launcher_craft.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/ic_launcher_craft_foreground.png` | `.png` | 19,256 | `75ccc8e0040f73e0` | `app/src/main/res/mipmap-mdpi/ic_launcher_craft_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/ic_launcher_craft_round.png` | `.png` | 4,086 | `b4667ab30d220ed0` | `app/src/main/res/mipmap-mdpi/ic_launcher_craft_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/ic_launcher_neon_glass.png` | `.png` | 4,395 | `54825abf137019d5` | `app/src/main/res/mipmap-mdpi/ic_launcher_neon_glass.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/ic_launcher_neon_glass_foreground.png` | `.png` | 11,889 | `a02f81d2d79a1409` | `app/src/main/res/mipmap-mdpi/ic_launcher_neon_glass_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/ic_launcher_neon_glass_round.png` | `.png` | 5,351 | `bd61f273b79e2be0` | `app/src/main/res/mipmap-mdpi/ic_launcher_neon_glass_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/legacy_icon.png` | `.png` | 3,395 | `5cff18e73eef07a1` | `app/src/main/res/mipmap-mdpi/legacy_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/legacy_icon_foreground.png` | `.png` | 7,523 | `dac160d9d99ce9a5` | `app/src/main/res/mipmap-mdpi/legacy_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/legacy_icon_monochrome.png` | `.png` | 5,337 | `626416873ab2fa53` | `app/src/main/res/mipmap-mdpi/legacy_icon_monochrome.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/legacy_icon_round.png` | `.png` | 4,250 | `18123883f5c30571` | `app/src/main/res/mipmap-mdpi/legacy_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/legacy_pixel_icon.png` | `.png` | 2,428 | `c39089095aea915c` | `app/src/main/res/mipmap-mdpi/legacy_pixel_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/legacy_pixel_icon_foreground.png` | `.png` | 6,102 | `6bd3b5928056101f` | `app/src/main/res/mipmap-mdpi/legacy_pixel_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/legacy_pixel_icon_round.png` | `.png` | 3,904 | `bad86cdccdff4c69` | `app/src/main/res/mipmap-mdpi/legacy_pixel_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/new_pixel_icon.png` | `.png` | 3,771 | `64159133486bc3d6` | `app/src/main/res/mipmap-mdpi/new_pixel_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/new_pixel_icon_foreground.png` | `.png` | 8,475 | `c77d4e9a98539288` | `app/src/main/res/mipmap-mdpi/new_pixel_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/new_pixel_icon_round.png` | `.png` | 4,934 | `ac921f558a94a6b6` | `app/src/main/res/mipmap-mdpi/new_pixel_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/pookie_icon.png` | `.png` | 5,246 | `07b314c844a8df2d` | `app/src/main/res/mipmap-mdpi/pookie_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/pookie_icon_foreground.png` | `.png` | 22,642 | `f90e88220b7d90d6` | `app/src/main/res/mipmap-mdpi/pookie_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/pookie_icon_round.png` | `.png` | 5,682 | `2d2957d12dc91c5e` | `app/src/main/res/mipmap-mdpi/pookie_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/revenge_cat_icon.png` | `.png` | 5,546 | `699b5c4851bf23f0` | `app/src/main/res/mipmap-mdpi/echo_cat_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/revenge_cat_icon_foreground.png` | `.png` | 19,950 | `b097d88adbf89539` | `app/src/main/res/mipmap-mdpi/echo_cat_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/revenge_cat_icon_round.png` | `.png` | 5,892 | `db9b2ff09ddd1880` | `app/src/main/res/mipmap-mdpi/echo_cat_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/sky_icon.png` | `.png` | 5,240 | `c9c565d0c65634ab` | `app/src/main/res/mipmap-mdpi/sky_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/sky_icon_foreground.png` | `.png` | 20,060 | `d03d2d68713cc372` | `app/src/main/res/mipmap-mdpi/sky_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/sky_icon_round.png` | `.png` | 5,620 | `f9f392ede297cf59` | `app/src/main/res/mipmap-mdpi/sky_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/tamil_naidu_icon.png` | `.png` | 5,337 | `37179e707878f19e` | `app/src/main/res/mipmap-mdpi/tamil_naidu_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/tamil_naidu_icon_foreground.png` | `.png` | 31,792 | `b414d8f34d7bd84e` | `app/src/main/res/mipmap-mdpi/tamil_naidu_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/tamil_naidu_icon_round.png` | `.png` | 5,623 | `3daeca137d30ce89` | `app/src/main/res/mipmap-mdpi/tamil_naidu_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/wierd_cat_icon.png` | `.png` | 5,122 | `889b00b4aebb4210` | `app/src/main/res/mipmap-mdpi/wierd_cat_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/wierd_cat_icon_foreground.png` | `.png` | 26,604 | `1405f7d23dc97c57` | `app/src/main/res/mipmap-mdpi/wierd_cat_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-mdpi/wierd_cat_icon_round.png` | `.png` | 5,611 | `e38e5ec1945c7501` | `app/src/main/res/mipmap-mdpi/wierd_cat_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/cat_icon.png` | `.png` | 17,210 | `2c95c7ab8ea497ff` | `app/src/main/res/mipmap-xhdpi/cat_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/cat_icon_foreground.png` | `.png` | 50,651 | `80ead1c6119bb375` | `app/src/main/res/mipmap-xhdpi/cat_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/cat_icon_round.png` | `.png` | 18,469 | `767b6250edf6508e` | `app/src/main/res/mipmap-xhdpi/cat_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/crazy_blue_icon.png` | `.png` | 18,910 | `7dee371428b5da9d` | `app/src/main/res/mipmap-xhdpi/crazy_blue_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/crazy_blue_icon_foreground.png` | `.png` | 75,965 | `691cbba83e8c3888` | `app/src/main/res/mipmap-xhdpi/crazy_blue_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/crazy_blue_icon_round.png` | `.png` | 19,197 | `35e682f12ac5352d` | `app/src/main/res/mipmap-xhdpi/crazy_blue_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/eko_icon.png` | `.png` | 16,375 | `6b0c0fbcc9243408` | `app/src/main/res/mipmap-xhdpi/eko_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/eko_icon_foreground.png` | `.png` | 56,458 | `331c014834a02a5d` | `app/src/main/res/mipmap-xhdpi/eko_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/eko_icon_round.png` | `.png` | 18,613 | `d4c175b0829f7e9a` | `app/src/main/res/mipmap-xhdpi/eko_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_craft.png` | `.png` | 10,673 | `a4c6da912437b44c` | `app/src/main/res/mipmap-xhdpi/ic_launcher_craft.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_craft_foreground.png` | `.png` | 58,487 | `db0a28a88f5ea87c` | `app/src/main/res/mipmap-xhdpi/ic_launcher_craft_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_craft_round.png` | `.png` | 11,910 | `ad138dfa4380837e` | `app/src/main/res/mipmap-xhdpi/ic_launcher_craft_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_neon_glass.png` | `.png` | 13,943 | `92706a00770e9fe9` | `app/src/main/res/mipmap-xhdpi/ic_launcher_neon_glass.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_neon_glass_foreground.png` | `.png` | 36,315 | `f0535778875458d9` | `app/src/main/res/mipmap-xhdpi/ic_launcher_neon_glass_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/ic_launcher_neon_glass_round.png` | `.png` | 17,164 | `28bbc830a799d38c` | `app/src/main/res/mipmap-xhdpi/ic_launcher_neon_glass_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/legacy_icon.png` | `.png` | 9,840 | `d40ae9cc179f0718` | `app/src/main/res/mipmap-xhdpi/legacy_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/legacy_icon_foreground.png` | `.png` | 17,592 | `333e0ef5635cf0ae` | `app/src/main/res/mipmap-xhdpi/legacy_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/legacy_icon_monochrome.png` | `.png` | 12,987 | `d0825cdbc03403f0` | `app/src/main/res/mipmap-xhdpi/legacy_icon_monochrome.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/legacy_icon_round.png` | `.png` | 11,425 | `7e827dbbb83dd4d4` | `app/src/main/res/mipmap-xhdpi/legacy_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/legacy_pixel_icon.png` | `.png` | 7,127 | `859fe1e49cbf078f` | `app/src/main/res/mipmap-xhdpi/legacy_pixel_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/legacy_pixel_icon_foreground.png` | `.png` | 14,602 | `c860ec3d3b9c6224` | `app/src/main/res/mipmap-xhdpi/legacy_pixel_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/legacy_pixel_icon_round.png` | `.png` | 9,860 | `a6d804dabc4290e5` | `app/src/main/res/mipmap-xhdpi/legacy_pixel_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/new_pixel_icon.png` | `.png` | 11,851 | `6255fb5751b1f26a` | `app/src/main/res/mipmap-xhdpi/new_pixel_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/new_pixel_icon_foreground.png` | `.png` | 23,301 | `b7acd27ef94bbec4` | `app/src/main/res/mipmap-xhdpi/new_pixel_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/new_pixel_icon_round.png` | `.png` | 14,608 | `5c261aad3f0b1784` | `app/src/main/res/mipmap-xhdpi/new_pixel_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/pookie_icon.png` | `.png` | 19,708 | `7634d5cb4164c308` | `app/src/main/res/mipmap-xhdpi/pookie_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/pookie_icon_foreground.png` | `.png` | 85,292 | `22dae6541b100bbc` | `app/src/main/res/mipmap-xhdpi/pookie_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/pookie_icon_round.png` | `.png` | 20,368 | `ed4314c8d98c7e68` | `app/src/main/res/mipmap-xhdpi/pookie_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/revenge_cat_icon.png` | `.png` | 18,849 | `69f181cce79f4bf3` | `app/src/main/res/mipmap-xhdpi/echo_cat_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/revenge_cat_icon_foreground.png` | `.png` | 65,473 | `ab33c27654e5dd77` | `app/src/main/res/mipmap-xhdpi/echo_cat_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/revenge_cat_icon_round.png` | `.png` | 19,575 | `e9e7cc3ee15668e9` | `app/src/main/res/mipmap-xhdpi/echo_cat_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/sky_icon.png` | `.png` | 17,715 | `15063683a9519a94` | `app/src/main/res/mipmap-xhdpi/sky_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/sky_icon_foreground.png` | `.png` | 71,340 | `a602ca00c1ffd42c` | `app/src/main/res/mipmap-xhdpi/sky_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/sky_icon_round.png` | `.png` | 18,228 | `120284f1b63727dc` | `app/src/main/res/mipmap-xhdpi/sky_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/tamil_naidu_icon.png` | `.png` | 18,503 | `4e22ef01c5b0bb1f` | `app/src/main/res/mipmap-xhdpi/tamil_naidu_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/tamil_naidu_icon_foreground.png` | `.png` | 109,394 | `e811191dc13a92a7` | `app/src/main/res/mipmap-xhdpi/tamil_naidu_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/tamil_naidu_icon_round.png` | `.png` | 19,365 | `dd59201fe5969eea` | `app/src/main/res/mipmap-xhdpi/tamil_naidu_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/wierd_cat_icon.png` | `.png` | 19,840 | `e313d5f7c4f3d3eb` | `app/src/main/res/mipmap-xhdpi/wierd_cat_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/wierd_cat_icon_foreground.png` | `.png` | 110,265 | `5d2fe1cba08c2f34` | `app/src/main/res/mipmap-xhdpi/wierd_cat_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xhdpi/wierd_cat_icon_round.png` | `.png` | 21,337 | `3fe58e2041279615` | `app/src/main/res/mipmap-xhdpi/wierd_cat_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/cat_icon.png` | `.png` | 36,033 | `06b63c261951177c` | `app/src/main/res/mipmap-xxhdpi/cat_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/cat_icon_foreground.png` | `.png` | 107,257 | `195d62c2a480a7f0` | `app/src/main/res/mipmap-xxhdpi/cat_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/cat_icon_round.png` | `.png` | 38,107 | `db9199d6c9d17b1b` | `app/src/main/res/mipmap-xxhdpi/cat_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/crazy_blue_icon.png` | `.png` | 38,372 | `b0873e2b0ee02de6` | `app/src/main/res/mipmap-xxhdpi/crazy_blue_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/crazy_blue_icon_foreground.png` | `.png` | 156,608 | `7241fc80d53f1748` | `app/src/main/res/mipmap-xxhdpi/crazy_blue_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/crazy_blue_icon_round.png` | `.png` | 38,670 | `a2444b6242289122` | `app/src/main/res/mipmap-xxhdpi/crazy_blue_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/eko_icon.png` | `.png` | 33,780 | `7e72518529dd39e8` | `app/src/main/res/mipmap-xxhdpi/eko_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/eko_icon_foreground.png` | `.png` | 116,426 | `ac1c89145a7811a7` | `app/src/main/res/mipmap-xxhdpi/eko_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/eko_icon_round.png` | `.png` | 38,234 | `668efe153922ec99` | `app/src/main/res/mipmap-xxhdpi/eko_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_craft.png` | `.png` | 19,629 | `3370c19c9124fc6e` | `app/src/main/res/mipmap-xxhdpi/ic_launcher_craft.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_craft_foreground.png` | `.png` | 121,597 | `cd4961907f7a9019` | `app/src/main/res/mipmap-xxhdpi/ic_launcher_craft_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_craft_round.png` | `.png` | 22,213 | `285d50cdc08f5097` | `app/src/main/res/mipmap-xxhdpi/ic_launcher_craft_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_neon_glass.png` | `.png` | 26,814 | `a12d1ba2a87b10dc` | `app/src/main/res/mipmap-xxhdpi/ic_launcher_neon_glass.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_neon_glass_foreground.png` | `.png` | 84,161 | `2b545ce37b450ca1` | `app/src/main/res/mipmap-xxhdpi/ic_launcher_neon_glass_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher_neon_glass_round.png` | `.png` | 33,787 | `20b8feae916efdb4` | `app/src/main/res/mipmap-xxhdpi/ic_launcher_neon_glass_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/legacy_icon.png` | `.png` | 15,209 | `c717e2b070408ed7` | `app/src/main/res/mipmap-xxhdpi/legacy_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/legacy_icon_foreground.png` | `.png` | 29,371 | `361afcdddc36d0d7` | `app/src/main/res/mipmap-xxhdpi/legacy_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/legacy_icon_monochrome.png` | `.png` | 22,286 | `0e20715ee7e7e8f3` | `app/src/main/res/mipmap-xxhdpi/legacy_icon_monochrome.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/legacy_icon_round.png` | `.png` | 18,508 | `5267cd9056a95c9d` | `app/src/main/res/mipmap-xxhdpi/legacy_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/legacy_pixel_icon.png` | `.png` | 10,112 | `3442f7c52c12c1e8` | `app/src/main/res/mipmap-xxhdpi/legacy_pixel_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/legacy_pixel_icon_foreground.png` | `.png` | 26,650 | `e071b64b6540dca5` | `app/src/main/res/mipmap-xxhdpi/legacy_pixel_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/legacy_pixel_icon_round.png` | `.png` | 16,545 | `6541bedbcb90a4f6` | `app/src/main/res/mipmap-xxhdpi/legacy_pixel_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/new_pixel_icon.png` | `.png` | 20,160 | `082cd2ee09e00abe` | `app/src/main/res/mipmap-xxhdpi/new_pixel_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/new_pixel_icon_foreground.png` | `.png` | 42,795 | `15459d275c391755` | `app/src/main/res/mipmap-xxhdpi/new_pixel_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/new_pixel_icon_round.png` | `.png` | 26,494 | `ea87a2f53d28b2c0` | `app/src/main/res/mipmap-xxhdpi/new_pixel_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/pookie_icon.png` | `.png` | 42,068 | `49e1588d3a4cc5ed` | `app/src/main/res/mipmap-xxhdpi/pookie_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/pookie_icon_foreground.png` | `.png` | 176,966 | `ff821734d4180f5c` | `app/src/main/res/mipmap-xxhdpi/pookie_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/pookie_icon_round.png` | `.png` | 42,690 | `1af1fcf7c2985d51` | `app/src/main/res/mipmap-xxhdpi/pookie_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/revenge_cat_icon.png` | `.png` | 37,212 | `5ecdf4020b0a60bd` | `app/src/main/res/mipmap-xxhdpi/echo_cat_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/revenge_cat_icon_foreground.png` | `.png` | 133,327 | `8099a0403b5b74a7` | `app/src/main/res/mipmap-xxhdpi/echo_cat_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/revenge_cat_icon_round.png` | `.png` | 39,564 | `bc548e17982502b7` | `app/src/main/res/mipmap-xxhdpi/echo_cat_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/sky_icon.png` | `.png` | 35,964 | `43be0b5f0634f779` | `app/src/main/res/mipmap-xxhdpi/sky_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/sky_icon_foreground.png` | `.png` | 155,633 | `101a99e88dc19a4f` | `app/src/main/res/mipmap-xxhdpi/sky_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/sky_icon_round.png` | `.png` | 36,612 | `a4e22b557a7f7779` | `app/src/main/res/mipmap-xxhdpi/sky_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/tamil_naidu_icon.png` | `.png` | 37,722 | `f2dfe21961065b80` | `app/src/main/res/mipmap-xxhdpi/tamil_naidu_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/tamil_naidu_icon_foreground.png` | `.png` | 228,575 | `f04518406824815c` | `app/src/main/res/mipmap-xxhdpi/tamil_naidu_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/tamil_naidu_icon_round.png` | `.png` | 39,837 | `f34763436beb1dfd` | `app/src/main/res/mipmap-xxhdpi/tamil_naidu_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/wierd_cat_icon.png` | `.png` | 44,877 | `58d1324216df2553` | `app/src/main/res/mipmap-xxhdpi/wierd_cat_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/wierd_cat_icon_foreground.png` | `.png` | 249,983 | `834c83d052d05d82` | `app/src/main/res/mipmap-xxhdpi/wierd_cat_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxhdpi/wierd_cat_icon_round.png` | `.png` | 49,568 | `895d7b56c7195b55` | `app/src/main/res/mipmap-xxhdpi/wierd_cat_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/cat_icon.png` | `.png` | 62,452 | `6f8e60600bbb870b` | `app/src/main/res/mipmap-xxxhdpi/cat_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/cat_icon_foreground.png` | `.png` | 184,877 | `38a399776f668826` | `app/src/main/res/mipmap-xxxhdpi/cat_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/cat_icon_round.png` | `.png` | 65,216 | `43a86323215e14d7` | `app/src/main/res/mipmap-xxxhdpi/cat_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/crazy_blue_icon.png` | `.png` | 63,816 | `4a0206943097b130` | `app/src/main/res/mipmap-xxxhdpi/crazy_blue_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/crazy_blue_icon_foreground.png` | `.png` | 269,268 | `80d4aaf56b1a5a4b` | `app/src/main/res/mipmap-xxxhdpi/crazy_blue_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/crazy_blue_icon_round.png` | `.png` | 62,666 | `9a090bca8af2ee25` | `app/src/main/res/mipmap-xxxhdpi/crazy_blue_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/eko_icon.png` | `.png` | 55,015 | `1c25d6405d51acf0` | `app/src/main/res/mipmap-xxxhdpi/eko_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/eko_icon_foreground.png` | `.png` | 184,360 | `2c18e24c5ea2a425` | `app/src/main/res/mipmap-xxxhdpi/eko_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/eko_icon_round.png` | `.png` | 63,497 | `85e9d3f262b1f177` | `app/src/main/res/mipmap-xxxhdpi/eko_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_craft.png` | `.png` | 32,134 | `5c918e862f7804d4` | `app/src/main/res/mipmap-xxxhdpi/ic_launcher_craft.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_craft_foreground.png` | `.png` | 201,477 | `bd86d87e23eed7bd` | `app/src/main/res/mipmap-xxxhdpi/ic_launcher_craft_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_craft_round.png` | `.png` | 35,860 | `d86ea64c809a0952` | `app/src/main/res/mipmap-xxxhdpi/ic_launcher_craft_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_neon_glass.png` | `.png` | 45,240 | `decea07f63375d5b` | `app/src/main/res/mipmap-xxxhdpi/ic_launcher_neon_glass.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_neon_glass_foreground.png` | `.png` | 130,007 | `1bc0bfba6fb3a698` | `app/src/main/res/mipmap-xxxhdpi/ic_launcher_neon_glass_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher_neon_glass_round.png` | `.png` | 57,881 | `4d0e6fbb5a6c23a3` | `app/src/main/res/mipmap-xxxhdpi/ic_launcher_neon_glass_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/legacy_icon.png` | `.png` | 23,814 | `fe06a8c95ed4c7bb` | `app/src/main/res/mipmap-xxxhdpi/legacy_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/legacy_icon_foreground.png` | `.png` | 44,875 | `864a0815b6b45e23` | `app/src/main/res/mipmap-xxxhdpi/legacy_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/legacy_icon_monochrome.png` | `.png` | 35,893 | `ea1ff53f1e67e7f7` | `app/src/main/res/mipmap-xxxhdpi/legacy_icon_monochrome.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/legacy_icon_round.png` | `.png` | 27,829 | `965730bff073fcb4` | `app/src/main/res/mipmap-xxxhdpi/legacy_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/legacy_pixel_icon.png` | `.png` | 19,748 | `7707ae16a8da97c1` | `app/src/main/res/mipmap-xxxhdpi/legacy_pixel_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/legacy_pixel_icon_foreground.png` | `.png` | 39,895 | `4bc83bdb9837a193` | `app/src/main/res/mipmap-xxxhdpi/legacy_pixel_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/legacy_pixel_icon_round.png` | `.png` | 24,785 | `7175fecf17a4fbc8` | `app/src/main/res/mipmap-xxxhdpi/legacy_pixel_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/new_pixel_icon.png` | `.png` | 34,922 | `d433670e62e80998` | `app/src/main/res/mipmap-xxxhdpi/new_pixel_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/new_pixel_icon_foreground.png` | `.png` | 68,087 | `245f6660c8430cf2` | `app/src/main/res/mipmap-xxxhdpi/new_pixel_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/new_pixel_icon_round.png` | `.png` | 40,896 | `74fd4dc99982ce91` | `app/src/main/res/mipmap-xxxhdpi/new_pixel_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/pookie_icon.png` | `.png` | 71,340 | `69b28e380559ed1d` | `app/src/main/res/mipmap-xxxhdpi/pookie_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/pookie_icon_foreground.png` | `.png` | 296,082 | `57e642b9fbe36c62` | `app/src/main/res/mipmap-xxxhdpi/pookie_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/pookie_icon_round.png` | `.png` | 70,276 | `13cc6916bd105ff3` | `app/src/main/res/mipmap-xxxhdpi/pookie_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/revenge_cat_icon.png` | `.png` | 61,944 | `80b979ad6696916b` | `app/src/main/res/mipmap-xxxhdpi/echo_cat_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/revenge_cat_icon_foreground.png` | `.png` | 232,088 | `df934f11650bf346` | `app/src/main/res/mipmap-xxxhdpi/echo_cat_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/revenge_cat_icon_round.png` | `.png` | 63,997 | `2e3e4d7b24d8b4d7` | `app/src/main/res/mipmap-xxxhdpi/echo_cat_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/sky_icon.png` | `.png` | 60,894 | `91a0309d4a1cb0c5` | `app/src/main/res/mipmap-xxxhdpi/sky_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/sky_icon_foreground.png` | `.png` | 269,251 | `bb2dc590ea22e25b` | `app/src/main/res/mipmap-xxxhdpi/sky_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/sky_icon_round.png` | `.png` | 60,872 | `560dc4dbb25bc342` | `app/src/main/res/mipmap-xxxhdpi/sky_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/tamil_naidu_icon.png` | `.png` | 63,912 | `b1b16e0b6c81acbe` | `app/src/main/res/mipmap-xxxhdpi/tamil_naidu_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/tamil_naidu_icon_foreground.png` | `.png` | 379,010 | `1eb00713de535e53` | `app/src/main/res/mipmap-xxxhdpi/tamil_naidu_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/tamil_naidu_icon_round.png` | `.png` | 67,399 | `87a6c42843afc543` | `app/src/main/res/mipmap-xxxhdpi/tamil_naidu_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/wierd_cat_icon.png` | `.png` | 82,865 | `c873cfbee7c00c56` | `app/src/main/res/mipmap-xxxhdpi/wierd_cat_icon.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/wierd_cat_icon_foreground.png` | `.png` | 444,748 | `ed743bce08d1864a` | `app/src/main/res/mipmap-xxxhdpi/wierd_cat_icon_foreground.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/mipmap-xxxhdpi/wierd_cat_icon_round.png` | `.png` | 88,345 | `f5711bb50d36cbb0` | `app/src/main/res/mipmap-xxxhdpi/wierd_cat_icon_round.png` | `echo_launcher_or_meme_icon` | `REMOVE` |
| `app/src/main/res/wierd_cat_icon-web.png` | `.png` | 576,849 | `360c4001c12cf35e` | `app/src/main/res/wierd_cat_icon-web.png` | `echo_launcher_or_meme_icon` | `REMOVE` |

---

## 8. Complete Explicit Register of All `360` Drawable XMLs (`294` `<vector>` + `66` `<shape>`/`<layer-list>`/`<clip>`)

Summary of `294` `<vector>` drawables (`287` `EchoSHA=True`, `7` `EchoSHA=False`): `7` `RETAIN` (Revenge `"R"` monograms), `18` `BLOCKED` (third-party brand logos), `218` `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` (Material Symbols Apache-2.0), `45` `REIMPLEMENT` (Echo widget/custom vectors), `6` `REIMPLEMENT` (`speed_*.xml` with false `google_sans_flex.ttf` OFL comment). All `66` non-vector drawable XMLs are `REIMPLEMENT`.

### 8.1 All `294` `<vector>` Drawable XMLs

| Path | Size (B) | SHA-256 (short) | EchoSHA | Category | Classification |
|---|---:|---|---:|---|---|
| `app/src/main/res/drawable/apple_music_me.xml` | 5,540 | `d6c710b746b00b8e` | True | `third_party_brand_logo` | `BLOCKED` |
| `app/src/main/res/drawable/apple_queue.xml` | 950 | `191863e5e2e20661` | True | `third_party_brand_logo` | `BLOCKED` |
| `app/src/main/res/drawable/apple_skip_next.xml` | 593 | `e46b578127cf3d97` | True | `third_party_brand_logo` | `BLOCKED` |
| `app/src/main/res/drawable/apple_skip_previous.xml` | 595 | `fbc2e0b063d0e409` | True | `third_party_brand_logo` | `BLOCKED` |
| `app/src/main/res/drawable/headset_applemusic.xml` | 1,550 | `6c69500020fe0eeb` | True | `third_party_brand_logo` | `BLOCKED` |
| `app/src/main/res/drawable/ic_apple_lossless.xml` | 1,742 | `3d993dd5e2e861a3` | True | `third_party_brand_logo` | `BLOCKED` |
| `app/src/main/res/drawable/ic_google.xml` | 1,026 | `1f08248bd2a1c806` | True | `third_party_brand_logo` | `BLOCKED` |
| `app/src/main/res/drawable/ic_jellyfin.xml` | 615 | `3db994d2910ac726` | True | `third_party_brand_logo` | `BLOCKED` |
| `app/src/main/res/drawable/ic_lastfm.xml` | 1,056 | `8bd665987e36af83` | True | `third_party_brand_logo` | `BLOCKED` |
| `app/src/main/res/drawable/ic_listenbrainz.xml` | 852 | `18c17258762d7afd` | True | `third_party_brand_logo` | `BLOCKED` |
| `app/src/main/res/drawable/ic_navidrome.xml` | 2,527 | `7a202c4bafe76463` | True | `third_party_brand_logo` | `BLOCKED` |
| `app/src/main/res/drawable/ic_spotify.xml` | 1,448 | `38058ef59576f7bc` | True | `third_party_brand_logo` | `BLOCKED` |
| `app/src/main/res/drawable/ic_telegram_new.xml` | 917 | `ffc20b8f7283ba2d` | True | `third_party_brand_logo` | `BLOCKED` |
| `app/src/main/res/drawable/pause_applemusic.xml` | 640 | `725e9be0a343db33` | True | `third_party_brand_logo` | `BLOCKED` |
| `app/src/main/res/drawable/play_applemusic.xml` | 527 | `f713aa8fe0dcddb4` | True | `third_party_brand_logo` | `BLOCKED` |
| `app/src/main/res/drawable/spatial_tracking_apple.xml` | 1,849 | `07a56c6e66e4c4fa` | True | `third_party_brand_logo` | `BLOCKED` |
| `app/src/main/res/drawable/speaker_apple.xml` | 1,301 | `2a2d7f3f2eda38d4` | True | `third_party_brand_logo` | `BLOCKED` |
| `app/src/main/res/drawable/telegram.xml` | 671 | `bb22df4b3ee27bf6` | True | `third_party_brand_logo` | `BLOCKED` |
| `app/src/main/res/drawable-v31/ic_widget_mic.xml` | 884 | `1eae6c1b4a4ba2a3` | True | `echo_widget_or_custom_vector` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_revenge_brain.xml` | 25,681 | `b979cde1823a1c32` | True | `echo_widget_or_custom_vector` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_revenge_wrapped.xml` | 1,442 | `da353f07b94a390f` | True | `echo_widget_or_custom_vector` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_cast_white.xml` | 553 | `4e92cf5941266848` | True | `echo_widget_or_custom_vector` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_equalizer_dots.xml` | 1,129 | `ce2b50083783f3a4` | True | `echo_widget_or_custom_vector` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_heart_clean.xml` | 692 | `e52022041415ff14` | True | `echo_widget_or_custom_vector` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_heart_filled_clean.xml` | 496 | `98271460feef96e6` | True | `echo_widget_or_custom_vector` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_heart_nav.xml` | 484 | `7d8b9f36e1d487ba` | True | `echo_widget_or_custom_vector` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_heart_outline_nav.xml` | 664 | `297f3689c2ee9dcb` | True | `echo_widget_or_custom_vector` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_heart_solid_white.xml` | 509 | `08120a2fc40f6d17` | True | `echo_widget_or_custom_vector` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_lyrics_pill.xml` | 442 | `a620251df8e7fb39` | True | `echo_widget_or_custom_vector` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_mic.xml` | 850 | `b6ef80c411565cdb` | True | `echo_widget_or_custom_vector` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_more_dots.xml` | 516 | `16c4710e492b32df` | True | `echo_widget_or_custom_vector` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_music_note.xml` | 435 | `c5af60c1b814a93f` | True | `echo_widget_or_custom_vector` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_next_brown.xml` | 313 | `3a023a071c59a61a` | True | `echo_widget_or_custom_vector` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_pause.xml` | 308 | `1581f4ea81f96769` | True | `echo_widget_or_custom_vector` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_pause_bold_white.xml` | 526 | `dd9f7512e4bdb15e` | True | `echo_widget_or_custom_vector` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_pause_brown.xml` | 477 | `7a2de5ef1e0a3a3d` | True | `echo_widget_or_custom_vector` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_pause_low.xml` | 335 | `98a3c4c4bdc06c32` | True | `echo_widget_or_custom_vector` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_pause_secondary.xml` | 335 | `89027dc54ad85b3d` | True | `echo_widget_or_custom_vector` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_pause_solid.xml` | 362 | `69d9a6949ac1051a` | True | `echo_widget_or_custom_vector` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_phone_device.xml` | 451 | `e73fb1e1461ec2d8` | True | `echo_widget_or_custom_vector` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_play.xml` | 291 | `7255cb2aaa1af68a` | True | `echo_widget_or_custom_vector` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_play_bold_white.xml` | 423 | `a5abc2a94ce1c560` | True | `echo_widget_or_custom_vector` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_play_brown.xml` | 393 | `5d35b1e180131315` | True | `echo_widget_or_custom_vector` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_play_low.xml` | 318 | `aa9d2bc5cab60f1d` | True | `echo_widget_or_custom_vector` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_play_secondary.xml` | 318 | `75279f0fef303b35` | True | `echo_widget_or_custom_vector` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_play_triangle.xml` | 347 | `775dffd1c12b899a` | True | `echo_widget_or_custom_vector` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_prev_brown.xml` | 304 | `2a059b30e286cd10` | True | `echo_widget_or_custom_vector` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_queue_library.xml` | 475 | `50711a55e28982eb` | True | `echo_widget_or_custom_vector` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_repeat_all.xml` | 531 | `929aa1d53c8f9d27` | True | `echo_widget_or_custom_vector` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_repeat_off.xml` | 531 | `860d3d9104f259c3` | True | `echo_widget_or_custom_vector` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_repeat_one.xml` | 588 | `4bedd3816c2ed066` | True | `echo_widget_or_custom_vector` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_shuffle_off.xml` | 541 | `ef04b8dff627ec2f` | True | `echo_widget_or_custom_vector` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_shuffle_on.xml` | 541 | `f286d2aeac35dbe4` | True | `echo_widget_or_custom_vector` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_skip_next.xml` | 332 | `7eab4fbb1f640a85` | True | `echo_widget_or_custom_vector` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_skip_next_white.xml` | 369 | `0df870860b249b39` | True | `echo_widget_or_custom_vector` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_skip_prev_white.xml` | 369 | `66ccdd05aeb0634b` | True | `echo_widget_or_custom_vector` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_skip_previous.xml` | 330 | `0b8936a4cb5c1aad` | True | `echo_widget_or_custom_vector` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_soundwave_logo.xml` | 1,217 | `3ee622202e5b8060` | True | `echo_widget_or_custom_vector` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_volume_mute.xml` | 935 | `f8922afeac240b77` | True | `echo_widget_or_custom_vector` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_widget_volume_on.xml` | 541 | `a9839f524290f27d` | True | `echo_widget_or_custom_vector` | `REIMPLEMENT` |
| `app/src/main/res/drawable/revenge_equalizer.xml` | 870 | `dac40fa249a6f965` | True | `echo_widget_or_custom_vector` | `REIMPLEMENT` |
| `app/src/main/res/drawable/speed_0_75x.xml` | 3,885 | `d92eed2acb2aefbf` | True | `speed_badge_vector_google_sans_comment` | `REIMPLEMENT` |
| `app/src/main/res/drawable/speed_1_25x.xml` | 4,013 | `28dc94e0171b1a19` | True | `speed_badge_vector_google_sans_comment` | `REIMPLEMENT` |
| `app/src/main/res/drawable/speed_1_5x.xml` | 2,561 | `40b703097f450089` | True | `speed_badge_vector_google_sans_comment` | `REIMPLEMENT` |
| `app/src/main/res/drawable/speed_1_75x.xml` | 3,382 | `7d400c90882d69a4` | True | `speed_badge_vector_google_sans_comment` | `REIMPLEMENT` |
| `app/src/main/res/drawable/speed_1x.xml` | 1,034 | `4d2146d9f00c0ae6` | True | `speed_badge_vector_google_sans_comment` | `REIMPLEMENT` |
| `app/src/main/res/drawable/speed_2x.xml` | 1,290 | `06d99b14d6d0e00d` | True | `speed_badge_vector_google_sans_comment` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_tonearm.xml` | 1,558 | `c9f23c221060b6dd` | True | `echo_widget_or_custom_vector` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_tonearm_vintage.xml` | 1,573 | `d625e900760ecb7e` | True | `echo_widget_or_custom_vector` | `REIMPLEMENT` |
| `app/src/main/res/drawable/account.xml` | 972 | `7074d8f4c86dfb28` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/add.xml` | 408 | `483f58c26616f784` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/add_circle.xml` | 671 | `f15898f8326833da` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/album.xml` | 860 | `990ed5c3a426e06c` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/alphabet_cyrillic.xml` | 679 | `d3d3b7e4977b5fcb` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/apk_install.xml` | 1,328 | `3fce494e2063c927` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/arrow_back.xml` | 568 | `dfbd1af2be2f0522` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/arrow_back_ios.xml` | 420 | `973951d051f7dbf9` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/arrow_downward.xml` | 384 | `27e1257ec21ccf04` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/arrow_forward.xml` | 417 | `a976c418e7e2d18b` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/arrow_top_left.xml` | 384 | `c6d5001195760f9a` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/arrow_upward.xml` | 384 | `63ac9084ff309330` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/artist.xml` | 835 | `899b955da4d23dcd` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/artist_screen.xml` | 1,405 | `71bf12a25cbb0a22` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/auto_play.xml` | 795 | `682f4f48286a1892` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/automation_slow_connecttion.xml` | 1,572 | `2e37adf36b8fe293` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/avg_time_oldplayer.xml` | 1,488 | `e9690942189d4d56` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/backup.xml` | 711 | `72249ac71c1b6183` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/bedtime.xml` | 637 | `3444cd9c8847edf3` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/biotech.xml` | 1,282 | `83526eb418492e95` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/bluetooth.xml` | 504 | `1c3e1675bf043c4b` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/bug_report.xml` | 1,226 | `e5a941dbf97c0857` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/buymeacoffee.xml` | 6,928 | `46ec95790eab4413` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/cached.xml` | 723 | `4d9eb694b51f55b6` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/cast.xml` | 549 | `b6bf3a4985243437` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/cast_connected.xml` | 597 | `3013aa8e308e325a` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/cast_speaker.xml` | 1,626 | `960fcc50fe9912f6` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/cast_speaker_connected.xml` | 3,933 | `a828fb6700e52a67` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/cast_tv.xml` | 916 | `c4524f8f8adba152` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/cast_tv_connected.xml` | 1,899 | `8c35cd7c98e90916` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/chat_msg.xml` | 762 | `f8c6b8abf937c576` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/check.xml` | 345 | `7ef0d82f8fb47c31` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/chevron_leftpx.xml` | 421 | `92452ecdd74d7dd7` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/chevron_right_px.xml` | 421 | `05f16cf109a26c45` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/clear_all.xml` | 426 | `d69a7ed5a0abd697` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/close.xml` | 396 | `8e5f345a3eaecc4a` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/cloud.xml` | 1,048 | `b362108b5e8033df` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/coffee.xml` | 686 | `379569a1029fe774` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/commit.xml` | 671 | `8829db2fa5a08493` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/connect_people.xml` | 1,121 | `a21432bdfc1e9164` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/content_copy.xml` | 967 | `e07a5cc4cb845ab5` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/contrast.xml` | 427 | `c1f612d82637ba67` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/crop.xml` | 389 | `95c73145f8fdbe4e` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/crown.xml` | 1,039 | `b6ac4ae3e0f3d456` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/currency_rupee_upi.xml` | 1,179 | `f2109c6c1dbd93cc` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/delete.xml` | 505 | `e676869288ab9412` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/delete_history.xml` | 885 | `331e8073899cb037` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/deployed_app_update.xml` | 1,283 | `4288f8df21482b9f` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/discover_tune.xml` | 643 | `790902eef30b8d12` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/dock_to_top.xml` | 392 | `72030114fd8bbb80` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/done.xml` | 360 | `7f5fcd749d82e9ef` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/download.xml` | 593 | `3a53127813566c64` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/drag_handle.xml` | 385 | `bcab0cfa781279df` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/edit.xml` | 617 | `3f028ef8f7b9f48a` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/equalizer.xml` | 426 | `3bba367fcb7974ca` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/error.xml` | 802 | `123988d01aeac2ef` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/expand_less.xml` | 360 | `e0137142420dd9f7` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/expand_more.xml` | 360 | `0912b846f2c29d18` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/explicit.xml` | 542 | `04d68481e68f6139` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/explore_outlined.xml` | 1,050 | `b35aa908154033bc` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/extension.xml` | 1,236 | `a4d53599773ff222` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/fast_forward.xml` | 475 | `dda0184263244e12` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/favorite.xml` | 474 | `a52c50b2fd93df79` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/favorite_border.xml` | 654 | `9eefa3750761b33d` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/file_export.xml` | 551 | `cdb02d130814a5db` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/folder_managed.xml` | 490 | `882452349871d563` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/fullscreen.xml` | 428 | `8c92677adfc5b2cf` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/fullscreen_exit.xml` | 410 | `b82276f0ea68cebd` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/github.xml` | 1,004 | `3862c26645baa647` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/github_main.xml` | 1,124 | `b2997b4b251ff647` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/globe.xml` | 1,069 | `66cf526201a6feab` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/globe_location_pin.xml` | 1,397 | `ff6c71ceca242622` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/globe_search.xml` | 1,069 | `66cf526201a6feab` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/gradient.xml` | 638 | `db2b883afe53942a` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/graphic_eq.xml` | 506 | `5f0c512673ae5563` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/grid_view.xml` | 795 | `b9766995181dbb37` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/group.xml` | 1,593 | `faa06f137e7edde7` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/group_add.xml` | 1,649 | `7de82a70dfd8891a` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/group_filled.xml` | 738 | `112e961aab026c3a` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/group_outlined.xml` | 979 | `bbb3ea858edc30cc` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/hide_image.xml` | 531 | `56c2e82a7bd2557c` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/history.xml` | 632 | `8bbbdc06f4935bd4` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/home_filled.xml` | 342 | `c82f301e550f3d93` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/home_outlined.xml` | 569 | `270418048c66ead3` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_calendar_month.xml` | 450 | `31a71c98f0a29206` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_canvas.xml` | 1,044 | `d31f203f7eecffe1` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_chart.xml` | 951 | `2586af292ab90501` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_drive.xml` | 610 | `98c878e601d24ca1` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_dynamic_icon.xml` | 485 | `002b5c8ad3fa0c52` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_forward_10.xml` | 1,171 | `bb40dc375cc756df` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_heart.xml` | 468 | `d9177f16d9f0387f` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_heart_outline.xml` | 648 | `878b04a8ef07ddef` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_hq.xml` | 996 | `f3a03f2122b676af` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_instagram_new.xml` | 815 | `008d23c1e5374f93` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_monochrome_mode.xml` | 387 | `7f98d520d5eb7f2a` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_nav_chevron_left.xml` | 317 | `48409449e6ec9a25` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_nav_chevron_right.xml` | 319 | `cd3114939a8d4106` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_navidrome_md3.xml` | 828 | `f068e1d11459c691` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_patreon_new.xml` | 408 | `7fe08a390476c81a` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_push_pin.xml` | 388 | `db68f6c00ff8b7d9` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_replay_10.xml` | 1,159 | `92bac06b37e344e8` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_smart_display.xml` | 392 | `32ef880f36ed2f87` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_subsonic.xml` | 1,061 | `e0a85b120a5f1a9d` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_subsonic_md3.xml` | 923 | `ab4dd390f4c04cdb` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_ten_sided_cookie.xml` | 1,315 | `c522faa0a65c9277` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_text_color.xml` | 560 | `6bb0462afcbc3ef3` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_x_new.xml` | 694 | `6f005268167ff652` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/image.xml` | 895 | `70cfb9c5f21e57e6` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/info.xml` | 738 | `09253abac3165581` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/insert_photo.xml` | 454 | `d7fa72a09f2e69aa` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/integration.xml` | 840 | `4ec3971c9b835583` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ios_share.xml` | 788 | `28d2acca0768925c` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/key.xml` | 574 | `f675438b749dd0bf` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/language.xml` | 1,619 | `01b5b4f0cbdaca72` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/language_japanese_latin.xml` | 1,773 | `661adade22d5f33b` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/language_korean_latin.xml` | 1,256 | `2f9f0a43381f4932` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/library_add.xml` | 772 | `bf3db1e0aee47227` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/library_add_check.xml` | 681 | `59f50507421514b2` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/library_music.xml` | 692 | `e384cc14c87b4024` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/library_music_filled.xml` | 685 | `dab7e1c2791ea466` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/library_music_outlined.xml` | 692 | `e384cc14c87b4024` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/linear_scale.xml` | 663 | `873a5f843ccbccd8` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/link.xml` | 599 | `e0853319ff9dab13` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/list.xml` | 918 | `4f52da744939cbbb` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/location_on.xml` | 637 | `57d28526c9668e81` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/lock.xml` | 1,167 | `d503d769401782f3` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/lock_open.xml` | 1,142 | `c9cd5477993a7620` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/login.xml` | 616 | `da27d97c6d746588` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/logout.xml` | 470 | `661c33bc4564fb7e` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/manage_search.xml` | 767 | `ede2d4ab405d1fa7` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/mic.xml` | 502 | `7806a23322354b6f` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/more_horiz.xml` | 762 | `efc873541d7d2857` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/more_vert.xml` | 762 | `f81c5ca0520848ce` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/music_history.xml` | 978 | `d54aa4379e5dae42` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/music_note.xml` | 484 | `28489d565d0d43b7` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/nav_bar.xml` | 499 | `2a63410b8c59129a` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/navigate_next.xml` | 393 | `7fce1bff79fff251` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/network_node.xml` | 1,315 | `60f54f561ef40972` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/network_update.xml` | 1,369 | `eb933a956cc064e8` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/new_release.xml` | 1,114 | `498380355ce28627` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/no_volume.xml` | 632 | `5cd10c94c755c467` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/notification.xml` | 577 | `b3f1971576b8f160` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/offline.xml` | 692 | `b315b9af91524fbf` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/palette.xml` | 1,078 | `bafe31b764af3549` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/pause.xml` | 647 | `5865c2f90860f689` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/paypal.xml` | 1,363 | `0e205d0480ae2870` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/person.xml` | 631 | `bd331218042860d0` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/play.xml` | 534 | `2d89ba7bb5c1f5de` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/playlist_add.xml` | 531 | `65ccd6d82cae80d7` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/playlist_play.xml` | 459 | `bbe701e6abe9ee0d` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/queue_music.xml` | 639 | `2daa487a0c319c5b` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/radio.xml` | 867 | `79efdacd49060e39` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/radio_button_checked.xml` | 1,046 | `ffc892d644cf629f` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/radio_button_unchecked.xml` | 852 | `e26a5d1b25ec0a46` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/refresh.xml` | 654 | `6e0c730e9bac8d4b` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/remove.xml` | 344 | `c0e7b8540076ec39` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/repeat.xml` | 496 | `d58b329382a9be5d` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/repeat_on.xml` | 665 | `1a2ebaea87c7aa36` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/repeat_one.xml` | 553 | `f394dcc754d650ac` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/repeat_one_on.xml` | 722 | `2917930cef82c64e` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/replay.xml` | 718 | `c0bf5a4ac917767e` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/restore.xml` | 689 | `1d68d2455cee88c6` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/screenshot.xml` | 1,037 | `197655a8dee775cc` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/search.xml` | 543 | `e26dc725ce67ece4` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/search_off.xml` | 963 | `c53d4ae6c62a88e6` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/security.xml` | 538 | `6d032c61d54d821c` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/select_all.xml` | 801 | `84742d6d3c0cae23` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/send_chat.xml` | 519 | `f912a846f0b06ac8` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/settings.xml` | 2,039 | `332114ad03db19ba` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/share.xml` | 714 | `89119ac84624d4c2` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/shortcut_library.xml` | 595 | `d50c2f6ed3298f9f` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/shortcut_search.xml` | 683 | `a7d879f9a3d2d981` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/shuffle.xml` | 506 | `3dff3cf0cc8ff6a2` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/shuffle_on.xml` | 675 | `8cabe1a25ad8ded6` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/similar.xml` | 385 | `a7301ae1e890f289` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/skip_next.xml` | 392 | `41d706216a096e03` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/skip_previous.xml` | 390 | `a031d7ec3ff79f0f` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/sleep_timer.xml` | 1,002 | `9e95137a5da2bc5d` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/sliders.xml` | 546 | `b276694190d8c705` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/slow_motion_video.xml` | 850 | `2d063f2af5712f43` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/smalls_icon.xml` | 757 | `728c2abe33bcf141` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/snippet_folder.xml` | 581 | `06ede0748f1ff85b` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/sparks.xml` | 695 | `98e41c4420ec5799` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/speed.xml` | 1,072 | `0337ed29f431e00e` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/star.xml` | 588 | `13cccc73bf437c01` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/stats.xml` | 717 | `d38647fc9aea7be1` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/storage.xml` | 549 | `a9375cee88066fd6` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/stream_old_player.xml` | 1,117 | `1c4ee0acdd4b02df` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/subscribe.xml` | 755 | `606bb2d8e6c1e1ea` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/subscribed.xml` | 743 | `c74ea4cfd39e3cb1` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/swipe.xml` | 852 | `91645731f3b963fe` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/swipe_vertical.xml` | 1,338 | `f7d6f20bdf6b84f8` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/sync.xml` | 745 | `d4993df78b746aa6` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/tab.xml` | 806 | `eba16a3cec94c33c` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/thumb_down.xml` | 614 | `f43d84518b29055d` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/thumb_down_filled.xml` | 572 | `7d2b6dd5f2684502` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/thumb_up_like.xml` | 868 | `6b37e13d81c43eef` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/timer.xml` | 604 | `7b0afbcbfa768474` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/token.xml` | 691 | `b9e5b34b4ac07d33` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/translate.xml` | 626 | `1085c981d02360cb` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/trending_down.xml` | 449 | `4d6cbe78d90ec3de` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/trending_up.xml` | 449 | `52e1695ab62b12eb` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/tune.xml` | 645 | `61f9d040a9d659d9` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/update.xml` | 775 | `ea1b23f1221c3179` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/updated.xml` | 884 | `0139f13678a9009c` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/vibration.xml` | 519 | `36832437d5d6c6f9` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/visibility.xml` | 565 | `11b272256f3421a2` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/visibility_off.xml` | 931 | `a89c1c92348cd573` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/volume_down.xml` | 484 | `0e36817122ff7c14` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/volume_mute.xml` | 424 | `f7f93d7e358355fd` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/volume_off.xml` | 737 | `ef8a7351cffc676d` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/volume_off_pause.xml` | 1,408 | `6150d82f49be8a78` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/volume_up.xml` | 608 | `435df35f4769ef46` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/warning.xml` | 568 | `0fc4fd9ef823a416` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/water_drop.xml` | 529 | `d7d6168b87da39c1` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/web_link.xml` | 699 | `8edeab019df37639` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/website.xml` | 1,602 | `9414661ea1c1aeac` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/wifi_proxy.xml` | 918 | `8a15f22ab525f4d8` | True | `material_or_echo_ui_vector` | `REPLACE WITH VERIFIED THIRD-PARTY COMPONENT` |
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | 603 | `592d4a93c132677a` | False | `revenge_brand_icon` | `RETAIN` |
| `app/src/main/res/drawable/ic_launcher_monochrome.xml` | 487 | `248a4ce28d77e11b` | False | `revenge_brand_icon` | `RETAIN` |
| `app/src/main/res/drawable/ic_launcher_nobg.xml` | 603 | `60df9a354c6dec75` | False | `revenge_brand_icon` | `RETAIN` |
| `app/src/main/res/drawable/ic_qs_revenge_logo.xml` | 486 | `7e60ae610226ac1e` | False | `revenge_brand_icon` | `RETAIN` |
| `app/src/main/res/drawable/ic_revenge_notification.xml` | 486 | `7e60ae610226ac1e` | False | `revenge_brand_icon` | `RETAIN` |
| `app/src/main/res/drawable/icon.xml` | 603 | `592d4a93c132677a` | False | `revenge_brand_icon` | `RETAIN` |
| `app/src/main/res/drawable/tv_banner.xml` | 697 | `c16fc10231c65470` | False | `revenge_brand_icon` | `RETAIN` |

### 8.2 All `66` Non-Vector Drawable XMLs (`<shape>` / `<layer-list>` / `<clip>`)

| Path | Size (B) | SHA-256 (short) | Category | Classification |
|---|---:|---|---|---|
| `app/src/main/res/drawable-night-v31/widget_background.xml` | 371 | `975fed6b5a5c137d` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable-night-v31/widget_play_pill_bg.xml` | 327 | `a2a2dd4cc4ece1f3` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable-night-v31/widget_progress_fill.xml` | 246 | `89f4653488f7c8c1` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable-night-v31/widget_progress_track.xml` | 246 | `7f1f8e73060d1bc0` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable-night-v31/widget_turntable_nav_bg.xml` | 247 | `4ede8c57779351b2` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable-night-v31/widget_turntable_play_bg.xml` | 303 | `77b56fc0bfacac54` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable-night/widget_background.xml` | 398 | `b9add5387a6ddb7b` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable-night/widget_play_pill_bg.xml` | 221 | `17d4bf5e4700f79e` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable-night/widget_progress_fill.xml` | 220 | `5347b9dee2078c1a` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable-night/widget_progress_track.xml` | 220 | `09748479952189e6` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable-night/widget_turntable_nav_bg.xml` | 246 | `e80f98c9f2145179` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable-night/widget_turntable_play_bg.xml` | 277 | `53828adb08fd4c29` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable-v31/ic_launcher_background_v31.xml` | 276 | `4ef53ff43a0c6a53` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable-v31/widget_background.xml` | 365 | `57cdbfb823f5a3ce` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable-v31/widget_mic_button_bg.xml` | 355 | `26427c8369336341` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable-v31/widget_mic_button_bg_active.xml` | 358 | `e3318e0f6f69fb76` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable-v31/widget_mic_pulse_1.xml` | 418 | `4f6491c202c6319f` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable-v31/widget_mic_pulse_2.xml` | 418 | `86fab265aa88a9f5` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable-v31/widget_mic_pulse_3.xml` | 416 | `a2ad701ccf83ff0f` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable-v31/widget_mic_pulse_4.xml` | 414 | `4f1dbb771f385314` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable-v31/widget_play_pill_bg.xml` | 327 | `f18139d9e17dd699` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable-v31/widget_progress_fill.xml` | 246 | `6fa6a55919c332b3` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable-v31/widget_progress_track.xml` | 246 | `ffe7152be5cfabe2` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable-v31/widget_turntable_nav_bg.xml` | 247 | `3a391a3483de3152` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable-v31/widget_turntable_play_bg.xml` | 303 | `0f3a1bc7d1753f2e` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable/ic_launcher_background_v31.xml` | 345 | `c2a0508182b9e600` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_adaptive_card_bg.xml` | 340 | `2d10026516dd465a` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_adaptive_queue_bg.xml` | 342 | `703754078d6245ee` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_artwork_scrim.xml` | 374 | `fcd32f5e284892c0` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_aurora_card_bg.xml` | 453 | `b746be07045fe61b` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_background.xml` | 387 | `d27cbeb4ff5751cc` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_card_dark_bg.xml` | 340 | `ac9cf2c4ae79d365` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_card_gradient_bg.xml` | 368 | `da670c2e1ae2c0d8` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_circle_button_bg.xml` | 293 | `b5655d80c30f4515` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_circle_glow_play_bg.xml` | 404 | `a6abe091ab0f6996` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_circle_play_accent_bg.xml` | 199 | `bd93948d3cceddfe` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_device_badge_bg.xml` | 221 | `0bf6efd1a9f6f9e4` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_like_button_bg.xml` | 259 | `18f6ddaf6736f54a` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_mic_button_bg.xml` | 323 | `8186fcd05f1af786` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_mic_button_bg_active.xml` | 330 | `14e0bc020517426b` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_mic_pulse_1.xml` | 428 | `9653add402a329ad` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_mic_pulse_2.xml` | 420 | `30c4a6f2012c47a4` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_mic_pulse_3.xml` | 424 | `896c0cebf38dcf3d` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_mic_pulse_4.xml` | 437 | `0ae79edc7edaf1ef` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_mic_pulse_idle.xml` | 315 | `47c589e2b5272bf8` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_pill_aurora_blue_bg.xml` | 300 | `bcfc2d1b94cba521` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_play_button_circular.xml` | 298 | `ca3051539bb760f4` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_play_pill_bg.xml` | 242 | `e217f13be4020809` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_preview_tile_downloaded.xml` | 470 | `c9d4fd50079716b2` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_preview_tile_liked.xml` | 491 | `8dada84eb983957a` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_preview_tile_playlist.xml` | 477 | `bda4094473a6ab6a` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_preview_tile_top.xml` | 475 | `250351cb12b17400` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_progress_clip.xml` | 229 | `cc24a5991d0e9bb4` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_progress_clip_modern.xml` | 236 | `21ca5733e9b2f87b` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_progress_fill.xml` | 241 | `ee0e00ee5240c407` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_progress_fill_modern.xml` | 241 | `ee0e00ee5240c407` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_progress_fill_vintage.xml` | 315 | `5e07cda5e30a559f` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_progress_track.xml` | 220 | `1def62af80622f1f` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_progress_track_modern.xml` | 247 | `8fe12ade0f9c45a5` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_progress_track_vintage.xml` | 222 | `b86f906cb74b7b77` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_thumbnail_squircle_bg.xml` | 252 | `26ba4d6029c29d1d` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_turntable_default_art.xml` | 987 | `81ca17e4d9b77df9` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_turntable_nav_bg.xml` | 246 | `e80f98c9f2145179` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_turntable_play_bg.xml` | 298 | `fff54f359ea535d6` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_vintage_cream_bg.xml` | 298 | `33771c488689fc18` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |
| `app/src/main/res/drawable/widget_vinyl_glass_bg.xml` | 349 | `14ec09eaec939c55` | `shape_layer_clip_drawable_xml` | `REIMPLEMENT` |

---

## 9. External Gradle Libraries (`34` Dependency Groups)

Full coordinate, version, scope, license, GPL-3.0 compatibility, and upstream URL records for all `34` dependency groups appear in `docs/provenance/LICENSE_AND_NOTICE_REGISTER.md § 3`.
