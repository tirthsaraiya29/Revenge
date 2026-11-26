# Module Specification — `:dsp-core`

Status: M2 clean-room specification (implementation-independent)
Target directory: `C:/Users/tirth/Downloads/Projects/Revenge/dsp-core`
Namespace root: `com.revenge.dsp.core`
Declared dependencies: none (pure JVM; no Android, Compose, Hilt, or network)
Required libraries: Kotlin/JVM (toolchain 21), `kotlinx-coroutines-core`, `kotlinx-serialization-json`, JUnit for tests.

> This spec describes required behaviour; any algorithm shape shown is illustrative. Implementers must design their own structure and will be audited for similarity to the baseline.

The vocabulary and rule data in this spec — enum constant names, accepted tokens and their precedence, numeric bounds, and the exact error/format strings — are normative and must be matched exactly. Algorithm structure, helper decomposition, container choices, and step/loop order are NOT normative unless this spec explicitly states that the resulting order or value is observable (e.g. band order, validation error order, output strings).

This module provides the pure-math DSP primitives (a biquad filter), the parametric equalizer data model, a persisted-profile model, and an AutoEQ-style text-file parser/validator. It must remain a pure JVM library: no `android.*`, `androidx.*`, or framework imports in main sources.

---

## 1. Module build contract

`dsp-core/build.gradle.kts` must:

- apply the Kotlin JVM plugin and the Kotlin serialization plugin;
- set `kotlin { jvmToolchain(21) }`;
- declare `implementation(libs.coroutines.core)`, `implementation(libs.kotlinx.serialization.json)`, and `testImplementation(libs.junit)`.

No other Gradle modules may be declared as dependencies. `:audio-dsp` consumes this module via `api(project(":dsp-core"))` and relies on the exact public API described in Section 6.

---

## 2. `com.revenge.dsp.core.models.FilterType`

`@Serializable enum class FilterType`

Constants, in declaration order: `PK`, `LSC`, `HSC`, `LPQ`, `HPQ`.

Semantics:

- `PK` — peaking response.
- `LSC` — low shelf.
- `HSC` — high shelf.
- `LPQ` — accepted for AutoEQ file compatibility; the audio pipeline renders it with the peaking response (see `BiquadFilter`). It is NOT a true low-pass.
- `HPQ` — accepted for file compatibility; rendered with the peaking response. It is NOT a true high-pass.

JSON contract: serialized by enum name as a JSON string (`"PK"`, `"LSC"`, `"HSC"`, `"LPQ"`, `"HPQ"`). Unknown values are handled by the consumer's `Json` configuration, not by this type.

---

## 3. `com.revenge.dsp.core.models.ParametricEQBand`

`@Serializable data class ParametricEQBand`

Fields (JSON property names identical to Kotlin names; all non-null):

| Field | Type | Default |
|---|---|---|
| `frequency` | `Double` | required |
| `gain` | `Double` | required |
| `q` | `Double` | `1.41` |
| `filterType` | `FilterType` | `FilterType.PK` |
| `enabled` | `Boolean` | `true` |

Semantics: a single parametric band. `frequency` is in Hz, `gain` is in dB, `q` is the dimensionless quality factor. Constructor argument order is exactly `frequency, gain, q, filterType, enabled` so positional construction keeps working. Defaults must be declared so that JSON documents missing optional keys deserialize correctly.

---

## 4. `com.revenge.dsp.core.models.ParametricEQ`

`@Serializable data class ParametricEQ`

Fields (JSON property names identical to Kotlin names; all non-null):

| Field | Type | Default |
|---|---|---|
| `preamp` | `Double` | required |
| `bands` | `List<ParametricEQBand>` | required |
| `metadata` | `Map<String, String>` | `emptyMap()` |

Companion contract: `const val MAX_BANDS = 20` (compile-time constant, not serialized).

Semantics: a complete parametric equalizer definition containing the global preamp in dB, an ordered band list (order is meaningful and preserved), and free-form text metadata parsed from import files. `metadata` must never be null; absent JSON yields an empty map. Constructor argument order is `preamp, bands, metadata`.

---

## 5. `com.revenge.dsp.core.models.SavedEQProfile`

`@Serializable data class SavedEQProfile`

Fields (JSON property names identical to Kotlin names; all non-null):

| Field | Type | Default |
|---|---|---|
| `id` | `String` | required |
| `name` | `String` | required |
| `deviceModel` | `String` | `""` |
| `bands` | `List<ParametricEQBand>` | `emptyList()` |
| `preamp` | `Double` | `0.0` |
| `isCustom` | `Boolean` | `false` |
| `isActive` | `Boolean` | `false` |
| `addedTimestamp` | `Long` | `System.currentTimeMillis()` |

Computed property: `val parametricEQ: ParametricEQ` — a read-only getter (no backing field, NOT serialized) returning `ParametricEQ(preamp = preamp, bands = bands)`; `metadata` of the returned object is empty.

Semantics: a user profile persisted by `:audio-dsp` (`EQProfileRepository`) as one JSON array element. The JSON document is written with a serializer configured with `ignoreUnknownKeys = true` and `prettyPrint = true`; therefore all add-only evolution must preserve these names/types/defaults. `addedTimestamp` default must be evaluated at construction time (each newly constructed profile without an explicit value gets the current epoch millis). `id` is stable across restarts; profiles are ordered newest-first by descending `addedTimestamp` by the consumer. `isActive` is a serialized field even though the repository also tracks an active-profile id separately; it must be preserved.

Constructor argument order is exactly `id, name, deviceModel, bands, preamp, isCustom, isActive, addedTimestamp`.

---

## 6. `com.revenge.dsp.core.BiquadFilter`

`class BiquadFilter(sampleRate: Int, frequency: Double, gain: Double, q: Double = 1.41, filterType: FilterType = FilterType.PK)`

A second-order (biquad) IIR section. The internal realisation (filter form, helper decomposition, state layout) is NOT prescribed. All five constructor arguments are named in downstream call sites; the parameter names, order, types, and defaults above are part of the contract.

### 6.1 Construction and coefficient derivation

Coefficients are fixed at construction and never change afterward; only per-channel history mutates while streaming. The filter must realise the standard RBJ Audio EQ Cookbook second-order magnitude response for the configured type and parameters (`Frequency` in Hz, `Fs` sample rate, gain in dB, `Q`). Any derivation, normalisation scheme, or state form that yields an equivalent transfer function is acceptable.

- Peaking (`PK`): a boost/cut of `gain` dB centred at `frequency`, with bandwidth controlled by `q`.
- Low shelf (`LSC`): `gain` dB applied below `frequency` and unity gain above it, using the standard RBJ shelf slope parameter `S = 1`.
- High shelf (`HSC`): `gain` dB applied above `frequency` and unity gain below it, same `S = 1`.
- `LPQ` and `HPQ` must be rendered with exactly the peaking response (`PK`); they are not true low-pass/high-pass.

Normalisation must be numerically stable: the realised coefficients never contain NaN or infinity for any sanitised input. If the derivation would yield a non-finite or degenerate leading denominator coefficient (magnitude `≤ 1e-12` after the effective values in 6.2 are applied), the filter must degrade to pass-through (`b0=1`, `b1=b2=a1=a2=0`), so a degenerate configuration emits its input unchanged rather than NaN. (The specific normalisation mechanism is not prescribed; only this observable fallback is.)

### 6.2 Effective parameter values (normative)

The constructor inputs are sanitised to effective values before coefficient derivation. The order of these rules is not prescribed; the resulting effective values are what is observable:

1. Sample rate: if the supplied `sampleRate >= 22`, use it; otherwise use `44100`.
2. Frequency: if the supplied value is non-finite, use `10.0`; otherwise clamp it into `[10.0, rate/2.0 − 1.0]` using the effective sample rate. The upper bound is one Hz below Nyquist, so a request exactly at Nyquist becomes `Nyquist − 1`.
3. Gain: if non-finite, use `0.0`; then clamp into `[−120.0, +120.0]` dB.
4. Q: if non-finite or `≤ 0`, use `1.41`; otherwise use the supplied value unclamped.

These guarantees hold for adversarial inputs (sample rate 0, frequency at/above Nyquist, NaN/infinite gain or Q): output is always finite.

### 6.3 Public operations

- `fun processSample(input: Double): Double` — filters one mono sample using the left channel's history; returns one output sample.
- `fun processStereo(inputLeft: Double, inputRight: Double): Pair<Double, Double>` — filters a stereo frame. Left and right channels have independent, persistent history. Returns `(leftOut, rightOut)`. When both inputs are equal and both channels have equal history, the two outputs are equal.
- `fun reset()` — clears the history of both channels to zero, restoring the exact post-construction state. After reset, processing the same input sequence reproduces the original outputs.

Processing semantics: the required input/output relation is the second-order recurrence `output[n] = b0·x[n] + b1·x[n−1] + b2·x[n−2] − a1·y[n−1] − a2·y[n−2]`, where `b*`/`a*` are the realised coefficients. The state layout and update order within a call are not prescribed. `processSample` and `processStereo` share the left channel's history; callers must not mix them for a given logical channel without accepting that shared state.

Threading: instances are NOT thread-safe. Coefficients are immutable and safely published; all mutable state (channel history) requires external synchronization if shared across threads. A `BiquadFilter` instance is otherwise reusable indefinitely.

### 6.4 Observable frequency behaviour (acceptance anchors)

- Peaking, gain 0 dB: a unit impulse yields `1.0` (within 1e-3).
- Peaking, positive gain: the first impulse output exceeds `1.0` and is finite.
- Low shelf, −6 dB at DC: constant input `1.0` converges to `10^(−6/20) ≈ 0.501` (within 0.01 after ~20,000 samples).
- No configuration may yield NaN or infinity.

---

## 7. `com.revenge.dsp.core.parser.ParametricEQParser`

`object ParametricEQParser` — stateless singleton; all functions are pure except file I/O. It reads and writes AutoEQ-style parametric EQ text. No synchronization is provided or needed.

### 7.1 `fun parseFile(file: File): ParametricEQ`

- Requires `file.exists()`; otherwise throws `IllegalArgumentException` with message exactly `"File does not exist: <file.absolutePath>"`.
- Reads the file as text using the platform default charset and parses it with the same rules as `parseText`.

### 7.2 `fun parseFile(filePath: String): ParametricEQ`

Convenience overload: behaves identically to 7.1 for the given path (same existence check and exception).

### 7.3 `fun parseText(content: String): ParametricEQ`

Line-oriented, case-insensitive, tolerant parser. Each line is evaluated by its trimmed content; the physical iteration order of lines is not prescribed, but the following observable rules must hold:

1. An empty (after trimming) line is ignored.
2. A line beginning with `Preamp:` (case-insensitive) is a preamp line and is never recorded as metadata.
3. A line beginning with `Filter` (case-insensitive) is a band line and is never recorded as metadata; if it cannot be parsed it is silently skipped.
4. Any other line containing a `:` is metadata: the trimmed text before the first `:` is the key, the trimmed text after it is the value. Lines without `:` are ignored. When keys repeat, the last occurrence wins.

Preamp: the value is the first dB number following `Preamp:` in the form `\bPreamp:\s*([-+]?\d+\.?\d*)\s*dB` (case-insensitive), converted with `toDoubleOrNull`. With no match or a failed conversion the preamp is `0.0`. Malformed preamp lines must not throw.

Band lines — a line yields a band only if ALL of the following hold; otherwise it is skipped entirely (never a partial band):

- It contains `ON` (case-insensitive). A line containing `OFF` but no `ON` is dropped. The `ON`/`OFF` marker is not stored.
- A filter type is present as a case-insensitive substring. When more than one type token occurs, resolve it by this precedence: `LSC` → `LSC`, `HSC` → `HSC`, `PK` → `PK`, `LPQ` → `LPQ`, `HPQ` → `HPQ`. No match → skip.
- `Fc <number> Hz` occurs in the form `\bFc\s+([-+]?\d+\.?\d*)\s*Hz` (case-insensitive).
- `Gain <number> dB` occurs in the form `\bGain\s+([-+]?\d+\.?\d*)\s*dB`.
- `Q <number>` occurs in the form `\bQ\s+([-+]?\d+\.?\d*)`.

Each of the three numeric captures must convert via `toDoubleOrNull`; a missing or failed value skips the band. Parsed bands are appended in file order with `enabled = true`. Return `ParametricEQ(preamp, bands, metadata)`. This function never throws for any text content.

Accepted numbers are the subset `[-+]?digits(.digits)?` — no exponent notation and no leading decimal point; such values fail to match and cause the line to be skipped.

### 7.4 `fun toString(eq: ParametricEQ): String`

Human-readable display format. Each output line is terminated by the platform line separator. The emitted text is observable and exact:

- First line: `Preamp: <preamp> dB`, where the number uses the standard shortest round-trip double decimal representation (Kotlin `Double.toString`).
- Then, for each band at index `i` (1-based): `Filter <i>: <filterType> Fc <frequency> Hz Gain <gain> dB Q <q>`, with the same number formatting for the three values.
- No `ON`/`OFF` marker. `metadata` is not emitted.
- An empty band list yields only the preamp line.

### 7.5 `fun toFileFormat(eq: ParametricEQ): String`

AutoEQ round-trip format, one line per band, each line terminated by the platform line separator. The emitted text is observable and exact:

- First line: `Preamp: <preamp> dB` (standard shortest round-trip double representation, Kotlin `Double.toString`).
- Then, per band at index `i` (1-based): `Filter <i>: ON <filterType> Fc <frequencyInt> Hz Gain <gain> dB Q <q2>` where:
  - `frequencyInt` is the frequency truncated toward zero to an integer (`Double.toInt()`);
  - `gain` uses the same shortest round-trip double representation;
  - `q2` has exactly two fraction digits and a dot decimal separator (equivalent to `String.format(Locale.ROOT, "%.2f", q)`).

`toString(eq)` output followed by `parseText` must NOT be relied on for round-trip (it lacks `ON`); `toFileFormat(eq)` followed by `parseText` MUST reproduce `preamp` exactly (within floating tolerance) and reproduce each band with identical `frequency` (when the band frequency is integral), `gain` (within `0.01` because of display precision for Q only; gain is unrounded), `filterType`, and `q` rounded to 2 decimals. Bands are always emitted as enabled by `toFileFormat`.

### 7.6 `fun validate(eq: ParametricEQ): List<String>`

Returns a list of human-readable error strings, empty when the profile is acceptable. It never throws and never mutates `eq`. Errors are appended in the following order, which is observable in the returned list; the internal evaluation mechanism is not otherwise prescribed:

1. Preamp: valid iff `-50.0 <= preamp <= 50.0`; otherwise append `"Preamp value <preamp> dB is out of range (-50 to +50 dB)"`.
2. Empty bands: if `bands.isEmpty()`, append `"EQ profile must have at least one band"`.
3. Too many bands: if `bands.size > ParametricEQ.MAX_BANDS` (20), append `"EQ profile has <n> bands, maximum is 20"` (using the `MAX_BANDS` value).
4. For each band in order, and within a band in this order:
   - Frequency valid iff `0.0 < frequency <= 100000.0`; else append `"Band <i>: Frequency <frequency> Hz is out of range (1 to 100000 Hz)"`.
   - Gain valid iff `-30.0 <= gain <= 30.0`; else append `"Band <i>: Gain <gain> dB is out of range (-30 to +30 dB)"`.
   - Q valid iff `0.0 < q <= 20.0`; else append `"Band <i>: Q factor <q> is out of range (0.01 to 20)"`.

`<i>` is 1-based. Values are interpolated using Kotlin `Double.toString`. Boundary values are inclusive where shown (preamp ±50, frequency 100000, gain ±30, Q 20 valid). All violations are reported, not merely the first. A profile with preamp 60, one band `frequency=0, gain=40, q=0` yields exactly four errors.

Note the deliberate asymmetry: frequency `≤ 0` is invalid but a missing-band profile is only one error (rule 2), and band checks still run when the list is non-empty.

---

## 8. Cross-module contracts

The following are the only consumed surfaces; keep them binary/source compatible.

`:audio-dsp` (`com.revenge.dsp.audio.CustomEqualizerAudioProcessor`) constructs `BiquadFilter(sampleRate = Int, frequency = Double, gain = Double, q = Double, filterType = FilterType)` using named arguments, calls `processSample(Double)` and `processStereo(Double, Double)` returning `Pair<Double, Double>`, and inspects `ParametricEQ.bands`, `ParametricEQ.preamp`, `ParametricEQBand.enabled/frequency/gain/q/filterType`.

`:audio-dsp` (`com.revenge.dsp.data.EQProfileRepository`, `com.revenge.dsp.controller.DspControllerImpl`) serializes `List<SavedEQProfile>` with kotlinx-serialization JSON (`ignoreUnknownKeys = true`, `prettyPrint = true`), constructs `SavedEQProfile(...)` by named arguments, reads `SavedEQProfile.parametricEQ`, and sorts by `addedTimestamp`. It also constructs `ParametricEQ(bands = ..., preamp = ...)`.

`:app` (`EQViewModel`, `AxionEqViewModel`, `EqScreen`, `AxionEqScreen`, `EQState`) imports `com.revenge.dsp.core.parser.ParametricEQParser` (calls `parseText`, `validate`), `com.revenge.dsp.core.models.{FilterType, ParametricEQBand, SavedEQProfile}`, constructs bands/profiles with named arguments, and reads `SavedEQProfile.bands`.

Because `:audio-dsp` exposes `:dsp-core` as `api`, the serialization annotations and enum string encoding are part of the public contract.

---

## 9. Error and edge-case summary

- `BiquadFilter`: never throws; sanitises all constructor inputs; guarantees finite coefficients via normalization or pass-through fallback.
- `ParametricEQParser.parseText`: never throws; tolerant and skip-on-malformed.
- `ParametricEQParser.parseFile`: throws `IllegalArgumentException` only when the file does not exist.
- `ParametricEQParser.validate`: never throws; returns every violation in documented order.
- Serialization: all model classes are `@Serializable`; defaults allow older JSON to decode after new optional fields are added. Unknown JSON keys are tolerated by the consumer's configuration.
- Ordering: band order is preserved in parse, serialize, and validation.

---

## 10. Target files to write

Main sources (package root `com.revenge.dsp.core`):

- `dsp-core/src/main/kotlin/com/revenge/dsp/core/BiquadFilter.kt`
- `dsp-core/src/main/kotlin/com/revenge/dsp/core/models/FilterType.kt`
- `dsp-core/src/main/kotlin/com/revenge/dsp/core/models/ParametricEQ.kt`
- `dsp-core/src/main/kotlin/com/revenge/dsp/core/models/SavedEQProfile.kt`
- `dsp-core/src/main/kotlin/com/revenge/dsp/core/parser/ParametricEQParser.kt`

Test sources:

- `dsp-core/src/test/kotlin/com/revenge/dsp/core/BiquadFilterTest.kt`
- `dsp-core/src/test/kotlin/com/revenge/dsp/core/ParametricEQParserTest.kt`
- `dsp-core/src/test/kotlin/com/revenge/dsp/core/SavedEQProfileTest.kt`

Build file: `dsp-core/build.gradle.kts` (already correct; preserve dependency layout).

---

## 11. Acceptance tests

Deterministic, opaque-box cases an independent test engineer can implement against the public API only.

### BiquadFilter

1. **Zero-gain peaking is transparent at impulse.** `BiquadFilter(44100, 1000.0, 0.0, 1.41, PK).processSample(1.0) == 1.0 ± 1e-3`.
2. **Positive peaking gain boosts first sample.** Gain `+6 dB` at 1 kHz: first impulse output `> 1.0` and finite.
3. **Stereo symmetry.** Any filter, `processStereo(v, v)` returns two equal values (±1e-4) when history is symmetric.
4. **Reset reproduces.** Process one stereo frame, `reset()`, process the same frame again → identical outputs (±1e-4).
5. **Nyquist safety.** `frequency = sampleRate/2`, gain `+3 dB`, HSC: output finite, not NaN.
6. **Invalid sample rate safety.** `sampleRate = 0`: output finite, not NaN (falls back to 44100).
7. **Low-shelf DC gain.** LSC 500 Hz, `−6 dB`, `q=1.0`, sample rate 48000: after 20,000 samples of `1.0`, output ≈ `10^(−6/20) = 0.5012` ± 0.01.
8. **High-shelf DC gain is unity.** HSC with any gain: after many constant samples of `1.0`, output ≈ `1.0` ± 0.02 (shelf affects high frequencies only).
9. **Negative gain lowers energy.** PK `−12 dB` at 1 kHz processed over a 1 kHz sine yields lower RMS than the input.
10. **LPQ/HPQ behave as peaking.** For identical frequency/gain/q, `PK`, `LPQ`, `HPQ` produce identical sample sequences.

### Models and serialization

11. **Defaults.** `SavedEQProfile(id="id", name="Flat")`: `deviceModel == ""`, `bands.isEmpty()`, `preamp == 0.0`, `isCustom == false`, `isActive == false`, `addedTimestamp > 0`.
12. **View projection.** `parametricEQ.preamp == profile.preamp`, `parametricEQ.bands == profile.bands`, `parametricEQ.metadata.isEmpty()`.
13. **JSON round-trip.** Serialize a `SavedEQProfile` and a `ParametricEQ` to JSON and deserialize → equal data-class instances (including enum by name and optional defaults).
14. **JSON backward compatibility.** A JSON object omitting `q`, `filterType`, `enabled`, `metadata`, and `addedTimestamp` deserializes using documented defaults.
15. **MAX_BANDS.** `ParametricEQ.MAX_BANDS == 20`.

### Parser — happy path

16. **AutoEQ block.** Input with `Preamp: -6.5 dB` and three `ON` filters (PK 31 Hz +5.2 dB Q 1.41, LSC 105 Hz +3.0 dB Q 0.71, HSC 10000 Hz −2.0 dB Q 0.71) yields preamp `-6.5`, three bands in order, correct type/frequency/gain/q, and `validate(...).isEmpty()`.
17. **Lowercase directives.** `preamp: +2.0 dB` and `filter 1: on lsc fc 100 hz gain 4.5 db q 0.7` parse to preamp `2.0`, one LSC band, gain `4.5`.
18. **LPQ/HPQ recognised.** `ON LPQ ...` and `ON HPQ ...` map to `FilterType.LPQ` / `HPQ` in order.

### Parser — tolerance

19. **Disabled bands dropped.** A file with one `OFF` and one `ON` filter yields exactly one band (the ON one).
20. **Malformed lines skipped.** Missing `Q`, unknown filter token, and a valid line → exactly the valid band survives.
21. **Metadata captured.** `Device: Pixel 7` and `Source: AutoEQ` appear in `metadata`; other lines still parse; `Preamp` is not placed in metadata.
22. **Missing/invalid preamp → 0.0.** Text without a preamp, and a preamp line without `dB`, both yield `preamp == 0.0`.
23. **Empty input.** `parseText("")` returns `preamp 0.0`, empty bands, empty metadata; does not throw.

### Parser — writers

24. **File format round-trip.** `toFileFormat(eq)` then `parseText` reproduces preamp and, for integral frequencies and 2-decimal Q/Gain, all band fields; output contains `ON` and `Preamp: -3.5 dB` for preamp `-3.5`.
25. **Display format.** `toString(eq)` contains `Preamp: 1.5 dB`, `Filter 1: PK`, and `Fc 440.0 Hz`; contains no ` ON ` marker.
26. **toFileFormat truncates frequency and formats Q.** A band at 100.9 Hz, Q 1.41 emits `Fc 100 Hz` and `Q 1.41`.

### Parser — validate and file I/O

27. **Out-of-range values.** Preamp `60`, band frequency `0.0`, gain `40.0`, q `0.0` → exactly 4 errors, one containing each of `Preamp`, `Frequency`, `Gain`, `Q factor`.
28. **Empty and oversized.** Empty band list → 1 error containing `at least one band`; `MAX_BANDS + 1` valid bands → 1 error containing `maximum is 20`.
29. **Boundary acceptance.** Preamp `±50`, frequency `100000.0`, gain `±30`, q `20.0` → no errors.
30. **parseFile success.** A temp file with `Preamp: -2.0 dB` and one valid filter parses preamp `-2.0` and the band.
31. **parseFile missing.** A non-existent `File` throws `IllegalArgumentException`; message contains `File does not exist:` and the absolute path.

---

## 12. Normative vocabulary and observable edge rules

The vocabulary and rule data referenced above are normative and must be reproduced exactly; only their implementation structure is free.

- **Enum vocabulary.** `FilterType` JSON encoding uses the exact names `PK`, `LSC`, `HSC`, `LPQ`, `HPQ` (declaration order `PK, LSC, HSC, LPQ, HPQ`).
- **Biquad effective inputs.** Sample rate `>= 22` kept, else `44100`; non-finite frequency → `10.0`, else clamped to `[10, rate/2 − 1]`; non-finite gain → `0.0`, else clamped to `[−120, +120]` dB; non-finite or non-positive Q → `1.41`, else unclamped. Any degenerate derivation → pass-through. Output is never NaN/infinity.
- **Biquad response contract.** `PK`, `LPQ`, `HPQ` share the identical peaking response; `LSC`/`HSC` use the standard RBJ shelf response with `S = 1`. Coefficient immutability and per-channel independent history are observable invariants.
- **Parser acceptance vocabulary.** Case-insensitive `Preamp:`, `Filter`, `ON`/`OFF`, type tokens `LSC`/`HSC`/`PK`/`LPQ`/`HPQ`, and `Fc … Hz` / `Gain … dB` / `Q …` forms. Numeric subset is `[-+]?digits(.digits)?` (no exponent, no leading point). When multiple type tokens occur, the precedence is `LSC`, `HSC`, `PK`, `LPQ`, `HPQ`.
- **Parser precedence.** Preamp lines and Filter lines are never metadata; an unparseable Filter line is skipped whole (never partial, never metadata); metadata uses the text before the first `:` as key and after it as value, trimmed, with last-wins duplicates. `parseText` never throws.
- **Writer format.** Display format omits `ON` and metadata; file format emits `ON`, truncates frequency toward zero to an integer, and formats Q with exactly two fraction digits and `.`; both use the platform line separator.
- **Validation order and messages.** Errors are returned in the documented 7.6 order using the exact interpolated strings there; boundaries are inclusive as stated; every violation is reported, not just the first.
