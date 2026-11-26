# M2 Gate Decisions — BLOCKED Item Disposition

**Baseline:** `11ae62071f96db3b70557867ab1254dbcc5b125e`
**Gate status:** RESOLVED — all 119 `BLOCKED` items have an explicit disposition.
**Date of decision:** Wave 0 (M3 foundation), recorded before any Wave 1+ consumption.

All decisions below were applied to the working tree. Verified after application:
zero paths from the `BLOCKED` set remain in the repository.

---

## 1. Decision register

| # | BLOCKED group | Count | Decision | Rationale |
|---|---|---:|---|---|
| 1 | Unverified ONNX models | 2 | **REMOVE both** | `vocals_umxhq_int8.onnx` had zero code references. `beat_this_final0_q8.onnx` had no embedded licence/origin metadata and cannot be provenance-verified offline. ONNX Runtime remains a dependency; no model asset ships. Beat tracking degrades safely (`BeatTracker.session()` returns null when the asset is absent). Re-export from `CPJKU/beat_this` (MIT) with checksum + notice is a Wave 5 follow-up, not a prerequisite for Wave 1. |
| 2 | Celebrity likeness rasters | 86 | **REMOVE** | Publicity/likeness risk. Corresponding `activity-alias` entries removed from `app/src/main/AndroidManifest.xml`; launcher picker entries removed from `AppIconSettingsScreen`; adaptive-icon XML and colour resources removed. |
| 3 | Personal / Apple / UPI rasters | 12 | **REMOVE, with clean-room generic replacements where referenced** | Personal photos and Apple/UPI trademarks removed. Referenced UI slots now use independently created Material-Symbol vectors (`ic_headphones_generic`, `ic_speaker_generic`, `ic_service_generic`, `ic_artwork_placeholder`) or generic Material icons (`play`, `pause`, `skip_next`, `skip_previous`, `queue_music`, `person`, `ic_chart`, `ic_revenge_brain`). Unreferenced files (`birthday_image.png`, `collab.jpg`, `dev.jpg`, `upi_new.png`, `apple_lyrics.png`) deleted outright. |
| 4 | Third-party brand vector logos | 18 | **REMOVE, replace with generic service icon** | Brand-guideline permission could not be verified. All referenced brand marks now use `ic_service_generic`; unreferenced marks (`ic_google`, `ic_telegram_new`, `telegram`, `ic_listenbrainz`, `ic_apple_lossless`, `spatial_tracking_apple`) deleted. |
| 5 | Unverified Lottie JSON | 1 | **REMOVE** | `app/src/main/res/raw/setting.json` had no code reference and no provenance. Nothing depends on it; no replacement required this wave. |

**Total resolved: 119 / 119.**

---

## 2. Additional removals executed in Wave 0 (not BLOCKED, but gated by the audit)

| Asset group | Count | Disposition |
|---|---:|---|
| Echo/meme launcher PNGs | 228 | `REMOVE` per-file inventory. Obsolete activity aliases and picker entries removed. |
| `app/src/main/cpp/vibrafp/` tree | 23 | `REMOVE` (never built; stale JNI keep-rule removed). |
| `google_sans_flex.ttf` v2.003 | 1 | `REMOVE` (name table forbids open-source redistribution). Rebound to verified OFL-1.1 `sans_flex.ttf` v3.007. |
| ONNX models | 2 | See gate #1. |

---

## 3. Explicit feature disclosures (R2 rule 6)

These are the only user-facing changes caused by the gate. They are recorded here
so no removal is silent:

1. **Launcher icon picker** now offers the default and static Revenge icons. The
   meme and celebrity icon variants are removed; they cannot be reinstated
   without cleared likeness/trademark rights.
2. **`Puter` AI provider** option removed from AI settings (it was unreachable in
   practice: the provider was listed but no request path was implemented).
3. **HTTP/3 (Cronet) toggle** removed. The code path was hard-disabled
   (`useCronet = false`) and the dependency was the FOSS GMS leak. RPC is now
   always OkHttp. No working capability is lost.
4. **Neural beat tracking** is disabled until a provenance-verified model
   re-export lands (Wave 5). All other Smart Audio features are unaffected.
5. **Apple-style player glyphs** are now generic play/pause/skip/queue/speaker
   symbols; visual styling of the player is unchanged.

Everything else in the product remains scheduled for clean-room implementation in
Waves 1–5.

---

## 4. Verification performed

- `./gradlew projects` — 21 modules configured.
- `:app:compileUniversalFossDebugKotlin` and `:lyrics:compileDebugKotlin` — PASS.
- Zero paths from the `BLOCKED` list exist on disk.
- Zero `org.json` / JSON-License dependency in `:lyrics`.
- Zero `play-services-cronet` or `org.chromium.net` reference in `app/`.
- `autoeq_index.json` parses as valid JSON and contains zero `Revenge`
  substitutions.
- FOSS manifest now strips `com.google.android.gms.cast.framework.ReconnectionService`.
