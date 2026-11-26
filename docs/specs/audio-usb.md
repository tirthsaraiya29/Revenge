# Clean-Room Specification — cluster `audio-usb`

> **Required behaviour only. Any algorithm shape is illustrative; implementers must design independently and are audited for baseline similarity.**

Status: M2 specification (behavioural contract only).
Cluster modules: `:dsp-core` (Gradle path `:dsp-core`, directory `dsp-core`), `:audio-dsp`
(`:audio-dsp`, directory `audio-dsp`), `:usbaudio` (`:usbaudio`, directory `usbaudio`).
Downstream consumer: `:playback` and `:app` (MusicService / UI). `:playback` declares
`api(project(":audio-dsp"))`, `api(project(":dsp-core"))`, `api(project(":usbaudio"))`.

This document specifies observable behaviour, API shape, invariants, ordering, threading, failure
modes and upgrade-compatibility surfaces. It intentionally does not prescribe internal structure:
helper decomposition, containers, loops and file-per-class layout are implementation choices. Any
implementation that satisfies the contracts and acceptance tests is acceptable. Namespaces, FQCNs,
package paths, constructor parameter names/order/defaults, public property names and serialized
field names are hard compatibility contracts: downstream code constructs these values with named
arguments and imports the exact FQCNs.

**Honesty constraint (cluster-wide, non-negotiable).** The USB path in this build renders through a
platform `android.media.AudioTrack` owned by the process. Android routes media output to a connected
USB Audio Class device through the platform mixer; the code must never bypass the mixer and must
never claim bit-perfect, exclusive, or native isochronous output. `UsbAudioDriver.nativeLoaded` is
`false`; `UsbAudioDriver.isLoaded()` is `true` because the software transport always exists. The
names `BitPerfectPcm`, `VolumeMode.PURE_BIT_PERFECT`, and the UI badge are retained for upgrade/API
compatibility, but their documented semantics are "PCM container conversion" and "bits left
untouched by software" respectively — not "bit-perfect USB". Crossfade/automix may be disabled while
the USB route is selected (a `:app` policy, not a module behaviour).

---

## 1. Module purposes and dependency rules

### 1.1 `:dsp-core` (pure Kotlin/JVM)

Owns the portable parametric-EQ model, the biquad engine and the AutoEQ text parser. No Android
types. Kotlin JVM plugin + kotlinx.serialization. Used by `:audio-dsp`, `:playback`, `:app` tests.

### 1.2 `:audio-dsp` (Android library, namespace `com.revenge.dsp.audio`)

Owns three Media3 `AudioProcessor` stages (parametric EQ, automix duck, stereo widener), the shared
`DspController` that publishes DSP state to every registered processor, the SharedPreferences-backed
`EQProfileRepository`, and the Hilt binding. Public packages are `com.revenge.dsp.audio`,
`com.revenge.dsp.controller`, `com.revenge.dsp.data`, `com.revenge.dsp.di` — note they intentionally
do not all share the Android namespace; this must be preserved because Hilt discovers them by FQCN.

### 1.3 `:usbaudio` (Android library, namespace `com.revenge.usbaudio`)

Owns USB DAC discovery/permission, descriptor parsing, capability models, the platform-`AudioTrack`
PCM driver, a Media3 `AudioSink` bridge, a PCM container converter and a volume helper. Media3 is
`compileOnly`: consumers must already provide Media3 at runtime.

Dependency direction: `:audio-dsp` → `:dsp-core`; `:usbaudio` → (nothing internal); `:playback` →
all three; `:app` → all. `:dsp-core` must not depend on Android, Hilt, Media3 or `:audio-dsp`.

---

## 2. `:dsp-core` — entities and serialization contract

All entities are Kotlin `data class`/`enum`, annotated `@Serializable` from
`kotlinx.serialization`. Serialized property names equal the Kotlin property names exactly.
`equals`/`hashCode` are structural over all declared fields. Properties are `val` and non-null
unless marked nullable. Constructors perform no validation, coercion or normalisation; guards live
in `BiquadFilter` and `ParametricEQParser.validate`.

### 2.1 `com.revenge.dsp.core.models.FilterType`

`@Serializable enum class FilterType { PK, LSC, HSC, LPQ, HPQ }`

- `PK` peaking, `LSC` low shelf, `HSC` high shelf.
- `LPQ` and `HPQ` are accepted for file/API compatibility. The DSP pipeline renders both as peaking
  (this is documented, relied-upon behaviour; do not implement true low/high-pass responses).
- Serialized as the enum constant name.

### 2.2 `com.revenge.dsp.core.models.ParametricEQBand`

`@Serializable data class ParametricEQBand(frequency: Double, gain: Double, q: Double = 1.41, filterType: FilterType = FilterType.PK, enabled: Boolean = true)`

Field order and defaults are a hard contract (positional construction appears in tests and UI).
`frequency` Hz, `gain` dB, `q` dimensionless. No range checks here.

### 2.3 `com.revenge.dsp.core.models.ParametricEQ`

`@Serializable data class ParametricEQ(preamp: Double, bands: List<ParametricEQBand>, metadata: Map<String, String> = emptyMap())`

Companion: `const val MAX_BANDS = 20`.

### 2.4 `com.revenge.dsp.core.models.SavedEQProfile`

`@Serializable data class SavedEQProfile(id: String, name: String, deviceModel: String = "", bands: List<ParametricEQBand> = emptyList(), preamp: Double = 0.0, isCustom: Boolean = false, isActive: Boolean = false, addedTimestamp: Long = System.currentTimeMillis())`

- Computed property `val parametricEQ: ParametricEQ` — returns `ParametricEQ(preamp = preamp,
  bands = bands)` (empty `metadata`). It must not copy or mutate bands.
- `addedTimestamp` default is evaluated at construction; it orders "newest first".
- Required fields `id` and `name` have no defaults; every other field must be omittable by name.

---

## 3. `com.revenge.dsp.core.BiquadFilter`

Declaration:

```kotlin
class BiquadFilter(
  sampleRate: Int,
  frequency: Double,
  gain: Double,
  q: Double = 1.41,
  filterType: FilterType = FilterType.PK,
) {
  fun processSample(input: Double): Double
  fun processStereo(inputLeft: Double, inputRight: Double): Pair<Double, Double>
  fun reset()
}
```

Semantics:

- The filter response is a standard RBJ audio-EQ-cookbook biquad whose coefficients are fixed at
  construction and must not change while samples are processed.
- Parameter sanitisation (observable through output; must never throw or emit NaN/Inf):
  - `sampleRate < 22` is treated as 44100.
  - Non-finite `frequency` is treated as 10.0; otherwise clamp to `[10.0, rate/2 - 1.0]`.
  - Non-finite `gain` is treated as 0.0; otherwise clamp to `[-120.0, 120.0]`.
  - Non-finite or non-positive `q` is treated as 1.41.
- Responses: `PK`/`LPQ`/`HPQ` → peaking with Q and gain; `LSC`/`HSC` → shelf with slope factor
  S = 1.
- Degenerate design: if the normalisation denominator is non-finite or `|a0| <= 1e-12`, the section
  must be an identity filter (pass input through) and must emit no NaN/Inf.
- `processSample` uses the left channel's history. `processStereo` advances independent left and
  right histories, one per input. Mono processing must not disturb the right history.
- `reset()` zeros both channels' history; subsequent output must equal the output of a freshly
  constructed filter on the same input.
- Invariants verified by tests: zero-gain peaking leaves a unit impulse at exactly 1.0 (±0.001);
  peaking boost at 1 kHz produces output > 1.0 on the first unit sample and stays finite; a low
  shelf with gain −6 dB converges after 20 000 samples of DC to `10^(−6/20)` within 0.01; a
  frequency at Nyquist stays finite; `sampleRate = 0` stays finite.

---

## 4. `com.revenge.dsp.core.parser.ParametricEQParser`

`object ParametricEQParser` with:

```kotlin
fun parseFile(file: File): ParametricEQ
fun parseFile(filePath: String): ParametricEQ
fun parseText(content: String): ParametricEQ
fun toString(eq: ParametricEQ): String
fun toFileFormat(eq: ParametricEQ): String
fun validate(eq: ParametricEQ): List<String>
```

### 4.1 `parseText` contract (tolerant AutoEQ reader)

Input is text. Matching is case-insensitive and tolerant of surrounding whitespace. Rules:

- Blank lines are skipped.
- A line whose trimmed text starts with `Preamp:` sets the profile preamp from the first numeric
  value followed by optional whitespace and `dB` in that directive; absent or unparseable → 0.0.
- A line starting with `Filter` is a band candidate. It is accepted **only if** it carries an `ON`
  token (case-insensitive) and parseable `Fc <number> Hz`, `Gain <number> dB` and `Q <number>`
  fields, and its filter type is detectable. Otherwise that whole line is silently skipped (a
  missing piece rejects that band, not the file).
- Detectable filter types are `LSC`, `HSC`, `PK`, `LPQ`, `HPQ` (case-insensitive token). An accepted
  band is constructed with `enabled = true`.
- Any other line containing `:` is stored in `metadata` as `key = text before first ':'` trimmed,
  `value = remainder` trimmed. Lines without `:` are ignored.
- Bands appear in the parsed result in the order their lines appear in the input.
- Unknown directives must never throw.

### 4.2 `parseFile`

- `parseFile(File)` requires `file.exists()`; otherwise throws `IllegalArgumentException` whose
  message contains the absolute path. Reads text and delegates to `parseText`.
- `parseFile(String)` delegates to `parseFile(File(path))`.

### 4.3 Output formats

- `toString(eq)` is the human/display format: first line `Preamp: <preamp> dB`, then for each band
  (1-based index) a line beginning `Filter <n>: <TYPE>` and containing `Fc <frequency> Hz`
  (raw double, e.g. `Fc 440.0 Hz`). It must **not** contain the `ON` marker.
- `toFileFormat(eq)` is the round-trippable format: first line `Preamp: <preamp> dB`, then
  `Filter <n>: ON <TYPE> Fc <frequency as Int> Hz Gain <gain> dB Q <q with 2 decimals, Locale.ROOT>`.
  Parsing this output through `parseText` must reproduce preamp and bands structurally
  (`q` may differ only within printed 2-decimal precision; the reference test uses values whose
  2-decimal print is exact, so equality holds for `1.41`, `0.71`).

### 4.4 `validate`

Returns a list of human-readable error strings; empty list means valid. It never throws. Checks:

- `preamp` outside `[-50.0, 50.0]` → an error mentioning `"Preamp"`.
- `bands.isEmpty()` → `"EQ profile must have at least one band"`.
- `bands.size > ParametricEQ.MAX_BANDS` → an error containing `"maximum is 20"`.
- Per band (index is 1-based in messages): `frequency <= 0 || > 100000` → message mentioning
  `"Frequency"`; `gain < -30 || > 30` → message mentioning `"Gain"`; `q <= 0 || > 20` → message
  mentioning `"Q factor"`.
- Deterministic example: `ParametricEQ(preamp=60, bands=[Band(frequency=0, gain=40, q=0)])` yields
  exactly 4 errors (preamp + frequency + gain + Q), each containing the substrings above.

---

## 5. `:audio-dsp` — Media3 audio processors

All three implement `androidx.media3.common.audio.AudioProcessor` and are annotated
`@androidx.media3.common.util.UnstableApi` (or `@OptIn(UnstableApi::class)`). They handle
**16-bit little-endian PCM, 1 or 2 channels only**. Output is a native-order direct `ByteBuffer`.

Shared lifecycle contract for every processor:

- `configure(format)` rejects `format.encoding != C.ENCODING_PCM_16BIT` or
  `format.channelCount > 2` by throwing `AudioProcessor.UnhandledAudioFormatException(format)`.
  On acceptance it records the sample rate/channel count and returns the *same* `AudioFormat`
  (same instance or an equal one).
- `queueInput(buffer)` with `remaining() == 0` is a no-op; it must not allocate or flip.
- The output buffer presented by `getOutput()` has capacity at least the input `remaining()`, its
  position is 0 and its limit is the number of produced bytes. It must be a direct buffer in native
  order.
- `getOutput()` returns the produced buffer and internally replaces it with an empty direct buffer,
  so a second `getOutput()` without intervening `queueInput` returns an empty buffer.
- `isEnded()` is true iff end-of-stream was signalled and the output buffer has no remaining bytes.
- `queueEndOfStream()` marks end-of-stream.
- `flush()` (deprecated in Media3; still overridden) discards pending output, clears end-of-stream,
  resets per-channel history, and keeps configuration usable.
- `reset()` releases all state, including configuration, as detailed per processor.
- Sample arithmetic (observable in output bytes): PCM16 input samples are treated as
  `short / 32768.0`; processed values are clamped to `[-32768.0, 32767.0]`, converted to `Int` with
  truncation toward zero, then to `Short`. No NaN/Inf may be written as PCM.

### 5.1 `com.revenge.dsp.audio.CustomEqualizerAudioProcessor`

Declaration:

```kotlin
class CustomEqualizerAudioProcessor : AudioProcessor {
  @Synchronized fun applyProfile(parametricEQ: ParametricEQ)
  @Synchronized fun disable()
  fun isEnabled(): Boolean
}
```

Behaviour:

- The last requested `ParametricEQ` is retained. If `applyProfile` arrives while no sample rate is
  configured, the profile is remembered but not yet active (`isEnabled()` is false); `configure`
  installs it. This lets the controller apply a profile before a player exists.
- Installing a profile sets the stage enabled, applies the preamp as a linear multiplier
  `10^(preamp/20)`, and includes a band iff `enabled == true && frequency > 0 &&
  frequency < sampleRate/2.0` (strictly below Nyquist). Excluded bands are skipped silently. A
  profile with no included bands but non-unity preamp still enables the stage; a profile with no
  included bands and unity preamp has no audible effect.
- Included bands are applied in list order (cascade order is observable in the output).
- `isEnabled()` reports whether a profile is currently installed: false before configure and after
  `disable()`, true after a held profile is installed by `configure`.
- `queueInput` passes input through unchanged when the stage is disabled, when no bands are
  included and the preamp multiplier is 1.0, or when no channel configuration exists.
- For mono, each included band processes the single sample; for stereo, each band processes the
  left and right samples independently. After all bands, the preamp multiplier is applied and the
  result clamped to PCM16.
- `reset()` clears configuration and installed EQ **but deliberately keeps the requested profile**,
  so a later `configure` restores the EQ.
- `disable()` forgets the requested profile, disables the stage, removes the bands and restores the
  preamp multiplier to 1.0.
- `isActive()` is the configured/accepting state (`true` after a successful configure, `false`
  after reset), independent of whether EQ is enabled.

### 5.2 `com.revenge.dsp.audio.AutomixDuckAudioProcessor`

Declaration:

```kotlin
class AutomixDuckAudioProcessor(
  private val shelfFrequencyHz: Double = 150.0,
  private val maxDuckDb: Double = -10.0,
) : AudioProcessor {
  fun setMix(fraction: Float)   // clamp to 0..1
  fun resetGain()               // targetMix = 0
}
```

Behaviour:

- Purpose: a low-shelf bass duck used by AutoMix DJ blends; separate from the user EQ chain.
- The shelf response (slope factor 1) is fixed when the pipeline is configured from
  `shelfFrequencyHz`, `maxDuckDb` and the sample rate. It must never be re-derived while samples
  flow; changing the duck amount must only move a dry/wet blend and must not cause a discontinuity.
- `setMix`/`resetGain` request a target mix, readable safely from any thread. The applied mix
  follows the target with exponential one-pole smoothing having a 15 ms time constant at the
  configured rate (per-sample easing factor `1000 / (15 ms * sampleRate)`, clamped to `[0, 1]`).
- Output per sample: `dry + (wet - dry) * appliedMix`, where `wet` is the shelf output. Mono uses
  one filter history; stereo keeps independent left/right histories. PCM16 clamp applies.
- `setMix(5f)` clamps to 1; `setMix(-1f)` clamps to 0. Whenever the applied mix is 0, the stage is
  byte-identical to input.
- A full duck of a DC/low tone inside the 150 Hz shelf must attenuate it but keep its sign
  (positive stays positive).
- `configure` designs the shelf and activates the stage; unconfigured/zero-rate input passes
  through.
- `flush`/`reset`: zero the per-channel histories; `reset` additionally clears configuration and
  both mixes. `resetGain` alone requests dry but does not clear history.
- `isActive()` is true only after a successful configure.

### 5.3 `com.revenge.dsp.audio.StereoWidenerAudioProcessor`

Declaration:

```kotlin
class StereoWidenerAudioProcessor : AudioProcessor {
  @Volatile var width: Float   // default 1f; setter clamps to [1f, 2f]
}
```

Behaviour:

- Mid/side widening on stereo only. Per frame:
  `mid = (L + R) * 0.5`, `side = (L - R) * 0.5 * width`,
  `outL = mid + side`, `outR = mid - side`.
- If `peak = max(|outL|, |outR|) > 32767`, scale **both** channels by `32767 / peak` (joint scaling
  preserves the image; never clip channels independently). Convert to `Int` then `Short`.
- `width = 1` recombines losslessly (identity on the sample grid).
- Mono (`channelCount != 2`) and unconfigured buffers pass through untouched.
- `isActive()` stays `true` at any width after a successful configure (avoids reconfiguration when
  toggled); `reset()` clears the channel configuration and deactivates.
- Deterministic example: stereo frame `(1000, 500)` at width 1.5 yields `(1125, 375)`.
  Frame `(32767, -32768)` at width 2 stays inside PCM16 and keeps `left > 0`, `right < 0`.

---

## 6. `com.revenge.dsp.controller` — shared DSP state

### 6.1 `PlayerDspProcessors`

`data class PlayerDspProcessors(val equalizer: CustomEqualizerAudioProcessor, val duckProcessor: AutomixDuckAudioProcessor, val stereoWidener: StereoWidenerAudioProcessor)`

`fun asList(): List<AudioProcessor>` returns exactly `[equalizer, duckProcessor, stereoWidener]`.

### 6.2 `DspController` interface (exact surface)

```kotlin
interface DspController {
  val activeProfile: StateFlow<SavedEQProfile?>
  val isEnabled: StateFlow<Boolean>
  val stereoWidth: StateFlow<Float>

  fun getAudioProcessors(): List<AudioProcessor>
  fun createProcessors(): PlayerDspProcessors
  fun releaseProcessors(processors: PlayerDspProcessors)

  fun addAudioProcessor(processor: CustomEqualizerAudioProcessor)
  fun removeAudioProcessor(processor: CustomEqualizerAudioProcessor)
  fun addStereoWidener(processor: StereoWidenerAudioProcessor)
  fun removeStereoWidener(processor: StereoWidenerAudioProcessor)
  fun addDuckProcessor(processor: AutomixDuckAudioProcessor)
  fun removeDuckProcessor(processor: AutomixDuckAudioProcessor)

  fun applyProfile(profile: SavedEQProfile): Result<Unit>
  fun disable()
  fun disableEqualizer()
  fun setStereoWidth(width: Float)
  fun setAutoMixDuck(duckFactor: Float)
  fun isInitialized(): Boolean
  fun release()
}
```

### 6.3 `DspControllerImpl` behaviour

`@Singleton class DspControllerImpl @Inject constructor() : DspController` (also
`@OptIn(UnstableApi::class)`).

- Thread-safe state owner: safe to call from any thread, including concurrently from the service
  and player threads. EQ, duck and widener processors each have a registry that supports concurrent
  registration and release.
- State flows start at `activeProfile = null`, `isEnabled = false`, `stereoWidth = 1f`.
- The latest requested profile and a "pending disable" flag are retained so a processor registered
  later is brought up to date immediately.
- `getAudioProcessors()` returns at most one of each type, in fixed chain order: first registered
  EQ, then first registered duck, then first registered widener. Any registry may be empty; the
  result may be empty.
- `createProcessors()` constructs a fresh trio, registers all three (applying any pending state),
  and returns the `PlayerDspProcessors`.
- `releaseProcessors(trio)` removes exactly those three instances (identity removal).
- Adding an EQ processor: if a disable is pending, the new processor is disabled; otherwise, if a
  pending profile exists, that profile is applied to it.
- Adding a stereo widener: it inherits the current stereo width at registration, so a new player
  starts at the width already requested.
- Adding a duck processor just registers it.
- `applyProfile(profile)`: records the profile as pending, clears any pending disable, publishes
  `activeProfile = profile` and `isEnabled = true`. If no EQ processor is registered, the request is
  retained and `Result.success(Unit)` is returned. Otherwise the profile is applied to every
  registered EQ processor; a failure on one processor is logged and does not prevent the others.
  The result is `Result.failure` with the first failure if any occurred, else `Result.success(Unit)`.
  This fan-out is deliberately best-effort per processor.
- `disable()` is an alias for `disableEqualizer()`.
- `disableEqualizer()`: marks a disable pending, clears the pending profile, publishes a null active
  profile and `isEnabled = false`, then disables every registered EQ processor, logging and
  continuing past failures.
- `setStereoWidth(width)`: clamp to `[1f, 2f]`, publish the value, and set it on every registered
  widener.
- `setAutoMixDuck(duckFactor)`: clamp to `[0f, 1f]` and forward to every registered duck.
- `isInitialized()` is `true` iff at least one EQ processor is registered.
- `release()` clears all three registries. It does not reset the exposed state flows (post-release
  the flows keep their last values); downstream relies only on `getAudioProcessors()` being empty
  and `isInitialized()` false after release.

Threading: controller methods are safe from any thread; processor `applyProfile`/`disable` are
`@Synchronized` on the processor instance; stereo width and duck target mix are safe for concurrent
read/write (writes become visible to audio processing without tearing or blocking).

### 6.4 `com.revenge.dsp.di.DspModule`

```kotlin
@Module @InstallIn(SingletonComponent::class)
abstract class DspModule {
  @Binds @Singleton abstract fun bindDspController(impl: DspControllerImpl): DspController
}
```

Must be discoverable by Hilt. `DspControllerImpl` and `EQProfileRepository` use `@Inject`
constructors.

---

## 7. `com.revenge.dsp.data.EQProfileRepository` — persistence contract

`@Singleton class EQProfileRepository @Inject constructor(@param:ApplicationContext context: Context)`

There is no SQLite/Room schema in this cluster. The equivalent upgrade-compatibility surface is the
SharedPreferences document. The following **must be preserved exactly**:

| Item | Value |
|---|---|
| Preferences file name | `nanosonic_eq_profiles` |
| Mode | `Context.MODE_PRIVATE` |
| Key: serialized profile list | `eq_profiles` |
| Key: active profile id | `active_profile_id` |
| JSON decoder | `ignoreUnknownKeys = true`, `prettyPrint = true` |
| Stored value | JSON array of `SavedEQProfile` (kotlinx.serialization default field names, see §2.4) |

A profile saved by an earlier build must load unchanged after upgrade. Removed/unknown JSON keys
must be ignored; a corrupt or unparseable document must not crash the process — the repository falls
back to an empty list and null active profile.

Public surface:

```kotlin
val profiles: StateFlow<List<SavedEQProfile>>
val activeProfile: StateFlow<SavedEQProfile?>

suspend fun saveProfile(profile: SavedEQProfile)
suspend fun deleteProfile(profileId: String)
suspend fun setActiveProfile(profileId: String?)
fun getAllProfiles(): List<SavedEQProfile>
fun getActiveProfile(): SavedEQProfile?
suspend fun importCustomProfile(name: String, parametricEQ: ParametricEQ)
fun getSortedProfiles(): List<SavedEQProfile>
```

Semantics:

- Construction restores state synchronously: read `eq_profiles`; if absent, remain empty. If
  present, decode; on any exception log and fall back to empty/null. Then read `active_profile_id`
  and set the active profile to the loaded profile whose `id` matches, or `null` if none/absent.
- `saveProfile` runs off the main thread. Saving a profile whose `id` already exists replaces that
  entry in place (no duplicate, position retained); otherwise the profile is appended. Persistence
  must complete before the new list is published on the flow, so a flow observer never sees a list
  that has not been persisted.
- `deleteProfile` removes all entries matching the id, persists, then publishes. If the deleted id
  was the active profile, the active profile becomes null and `active_profile_id` is removed.
- `setActiveProfile(id)` runs off the main thread. `null` clears the published value and removes the
  key. A non-null id is looked up exactly; the published value is the found profile (or `null` when
  the id is unknown) and the key is written only when found, otherwise removed. It never throws for
  an unknown id.
- `importCustomProfile(name, parametricEQ)` builds and saves a `SavedEQProfile` with
  `id = "custom_${System.currentTimeMillis()}_${name.hashCode()}"`, `name = name`,
  `deviceModel = name`, `bands = parametricEQ.bands`, `preamp = parametricEQ.preamp`,
  `isCustom = true`; other fields default. It does not change the active profile.
- `getAllProfiles()` / `getActiveProfile()` read the current flow values synchronously.
- `getSortedProfiles()` returns **custom profiles only**, newest first by `addedTimestamp`
  descending. Used by `EQViewModel`/`AxionEqViewModel`.

Downstream collectors (MusicService, both EQ view models) observe `activeProfile` and `profiles`;
`MusicService` reapplies whatever becomes active to `DspController` and disables the EQ when it
becomes null. Do not change emission semantics (StateFlow emits current value on collection).

---

## 8. `:usbaudio` — models (`com.revenge.usbaudio.model`)

### 8.1 `DacFormat`

`data class DacFormat(val interfaceNumber: Int, val altSetting: Int, val endpointAddress: Int, val syncEndpointAddress: Int? = null, val bitDepth: Int, val subslotBytes: Int, val channels: Int, val sampleRates: List<Int>)`

`bitDepth` = meaningful resolution; `subslotBytes` = wire container bytes (e.g. 24-bit in 4 bytes);
`syncEndpointAddress` = async feedback endpoint or null. Required fields (`interfaceNumber`,
`altSetting`, `endpointAddress`, `bitDepth`, `subslotBytes`, `channels`, `sampleRates`) have no
defaults.

### 8.2 `DacCapabilities`

`data class DacCapabilities(val uacVersion: Int, val supportedFormats: List<DacFormat> = emptyList(), val supportedSampleRates: List<Int> = emptyList(), val clockSourceId: Int? = null, val hasHardwareVolume: Boolean = false, val volumeFeatureUnitId: Int? = null, val minVolumeDb: Float = 0.0f, val maxVolumeDb: Float = 0.0f, val volumeResDb: Float = 0.0f)`

`uacVersion` is 1 or 2. UAC2 rates are typically seeded with 48 000 pending a GET_RANGE query.
`supportedSampleRates` is the union used for sink matching and UI display; `supportedFormats`
carries per-interface detail. UI reads `supportedSampleRates.maxOrNull()`,
`supportedFormats.maxOfOrNull { it.bitDepth }`, `uacVersion`; preserve types/defaults.

### 8.3 `VolumeMode`

`enum class VolumeMode { PURE_BIT_PERFECT, HARDWARE, SOFTWARE_64BIT }`.
`PURE_BIT_PERFECT` and `HARDWARE` both mean "software applies no attenuation" (multiplier 1.0);
they do not imply bypassing the platform mixer.

---

## 9. `com.revenge.usbaudio.BitPerfectPcm` (PCM container converter)

`object BitPerfectPcm`. This is a conversion helper, **not** a bit-perfect USB claim.

```kotlin
val INTEGER_BITS: List<Int>              // [16, 24, 32]
val STANDARD_RATES: List<Int>            // 44100,48000,88200,96000,176400,192000,352800,384000,705600,768000

fun outputBits(sourceBits: Int, supported: Collection<Int>): Int?
fun sourceBits(encoding: Int): Int?
fun bytesPerSample(encoding: Int): Int
fun convert(input: ByteBuffer, encoding: Int, channelCount: Int, outputBits: Int, output: ByteBuffer): Boolean
```

Behaviour:

- `outputBits`: among `supported ∩ INTEGER_BITS` that are `>= sourceBits`, return the smallest;
  `null` when none fits.
- `sourceBits`: 16-bit (LE/BE) → 16; 24-bit (LE/BE) and float → 24; 32-bit (LE/BE) → 32; anything
  else → `null` (8-bit unsigned is unsupported).
- `bytesPerSample`: 2/3/4 for the above; 0 for unsupported.
- `convert` copies whole frames, placing each source sample left-aligned in the destination
  container and writing little-endian integer PCM of `outputBits` (a 16-bit value 0x1234 written as
  24-bit yields bytes `00 34 12`). Both buffer positions advance by the bytes actually produced and
  consumed. It returns `true` when every float sample was exactly representable at 24-bit precision,
  and `false` otherwise (16/24/32-bit integer sources always return `true`). It throws
  `IllegalArgumentException` when the encoding is unsupported (8-bit) or `outputBits` would narrow
  below the source precision (e.g. 32→16); in the unsupported case the input position must be
  unchanged. Input byte order is derived from the encoding (BE variants read big-endian); output is
  always little-endian.
- Deterministic anchors: 16-bit values 12345/−12345 round-trip exactly through 16→16; 0x1234 through
  16→24 yields bytes `00 34 12`; float 0.5 through float→24 reconstructs 0.5 ± 0.001; float NaN
  maps to 0.

---

## 10. `com.revenge.usbaudio.UsbAudioDriver`

Software PCM transport. Public surface:

```kotlin
class UsbAudioDriver {
  companion object {
    val nativeLoaded: Boolean                    // always false in this build
  }
  fun isLoaded(): Boolean                        // always true
  fun getNativeDriverVersion(): String           // "1.0.0-usbaudio"
  fun testRingBufferWrite(data: ByteArray): Int
  fun testRingBufferRead(size: Int): ByteArray
  fun testRingBufferFlush()
  fun parseDescriptors(rawDescriptors: ByteArray): DacCapabilities
  fun calculateNominalPacketSamples(sampleRate: Int): Int
  fun startStream(fd: Int, interfaceNumber: Int = 0, altSetting: Int = 1, dataEp: Int,
                  syncEp: Int = -1, sampleRate: Int, bitDepth: Int = 16,
                  channels: Int = 2): Int
  fun stopStream(): Int
  fun writeAudio(buffer: ByteArray, size: Int = buffer.size): Int
  fun getAvailableWrite(): Int
  fun setSoftwareVolumeMultiplier(multiplier: Double)
  fun flushStream()
  fun getFramesPlayed(): Long
  fun hasPendingData(): Boolean
}
```

Behaviour:

- Transport: when a 16-bit mono/stereo stream at 8 kHz–192 kHz is started, playback is rendered
  through a platform `AudioTrack` in streaming mode with media/music usage routed by the platform
  mixer to the USB device. The track buffer must hold at least the platform minimum and enough
  audio to sustain roughly 100 ms at the configured rate. Other bit depths, channel counts and
  rates use a thread-safe bounded FIFO byte buffer (capacity 131 072 bytes), so callers never
  receive silently corrupted audio. The FIFO always exists as the fallback transport.
- `startStream` is synchronized and returns 0 on success. It replaces any active stream, discards
  buffered audio, records the stream configuration (`sampleBytes = (bitDepth + 7) / 8`,
  `channels = channels > 0 ? channels : 1`, rate, frame size), resets frame counters, and attempts
  the platform track for the eligible case. If track creation or start fails, the track is
  released and the FIFO path is used; this is not an error to the caller.
- `stopStream` is synchronized: releases any track, discards buffered audio, resets frame counters,
  returns 0.
- `writeAudio(buffer, size)` queues `min(size, buffer.size)` bytes and returns 0 for a non-positive
  request. When the software multiplier is not 1.0, attenuation is applied before queueing. The
  platform path writes without blocking; a negative result or a track that has become invalid is
  reported as 0 accepted bytes (never negative) and must not throw. Accepted frames are counted.
  The FIFO path returns the number of bytes copied and also advances the played-frame counter.
- `getAvailableWrite()` reports free bytes in the active transport, clamped to
  `[0, Int.MAX_VALUE]` (FIFO free space when no track; otherwise track buffer space not occupied by
  queued-but-unplayed frames).
- `setSoftwareVolumeMultiplier` clamps to `[0,1]`, stores the value, and also applies it to the
  platform track, swallowing `IllegalStateException` from a concurrently released track.
- `flushStream()` is synchronized: on the platform path it pauses, flushes, resets queued-frame
  accounting and resumes, releasing the track if it became invalid; it always discards FIFO data
  and resets played-frame accounting.
- `getFramesPlayed()` reports the platform playback head position masked to 32 bits (0 if the track
  became invalid), or the FIFO's accumulated played-frame counter when no track is active.
- `hasPendingData()` is true when FIFO data exists, or when unplayed frames are queued on the
  platform path.
- `calculateNominalPacketSamples(rate)`: 0 when `rate <= 0`, else `rate / 8000` (high-speed USB
  microframes). Deterministic: 96 000 → 12, 44 100 → 5.
- `parseDescriptors` delegates to `UsbDescriptorParser.parse`.
- Test hooks (`testRingBuffer*`) expose the FIFO for deterministic unit tests; they are part of the
  public surface but documented as test-only. FIFO semantics: byte order preserved, reads clamp to
  available bytes, writes past capacity accept only what fits, an emptied buffer reports no pending
  data, flushing discards all data.
- Attenuation helper (used by `writeAudio`) scales interleaved samples for 2, 3 or 4-byte
  little-endian samples, clamping to the sample width; unsupported widths (<2 or >4) leave data
  unchanged. Deterministic: 16-bit sample 1000 at multiplier 0.5 → 500.

---

## 11. `com.revenge.usbaudio.UsbDacAudioSink`

`@UnstableApi class UsbDacAudioSink(private val driver: UsbAudioDriver, var capabilities: DacCapabilities = DacCapabilities(uacVersion = 2), private val dacConnectionProvider: (() -> DacDeviceState.Connected?)? = null) : androidx.media3.exoplayer.audio.AudioSink`

Purpose: bridge Media3 decoded PCM to `UsbAudioDriver`. It renders via the platform `AudioTrack`
route; it does not bypass the mixer. It must implement the full Media3 `AudioSink` interface (see
§15.2) with the following semantics.

- `setListener(listener)` stores the listener for error reporting.
- `supportsFormat(format)`:
  - `sampleMimeType != MimeTypes.AUDIO_RAW` → false.
  - `pcmEncoding` must be `Format.NO_VALUE`, `C.ENCODING_PCM_16BIT`, `C.ENCODING_PCM_24BIT` or
    `C.ENCODING_PCM_32BIT`; otherwise false (float/8-bit unsupported).
  - `sampleRate == Format.NO_VALUE` → true.
  - If `capabilities.supportedSampleRates` is non-empty → true iff it contains the rate.
  - Else → true iff rate `<= 192000`.
- `getFormatSupport(format)` returns `SINK_FORMAT_SUPPORTED_DIRECTLY` when supported, else
  `SINK_FORMAT_UNSUPPORTED`.
- `configure(inputFormat, specifiedBufferSize, outputChannels)`: records the input format, clears
  frame count, end-of-stream and start position. It does not start the stream.
- `play()`: marks playing, then negotiates/starts the stream if needed.
- `handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount): Boolean`:
  1. Empty buffer → `true` (accepted).
  2. Not playing → `false` (retry later; input buffer unchanged).
  3. Negotiate/start the stream if needed.
  4. If a DAC connection provider exists and no stream is active (DAC absent/not ready) → `false`
     without consuming input.
  5. If the start position is unset and `presentationTimeUs != C.TIME_UNSET`, record it.
  6. Convert the input bit depth to the active stream container depth (16-bit on the platform
     path), frame-aligned.
  7. Compute writable space as a whole number of target frames from `driver.getAvailableWrite()`;
     if zero, leave the input buffer unchanged and return `false` (backpressure, no bytes
     consumed).
  8. Write as many frame-aligned bytes as fit, advance the input buffer by exactly the input frames
     accepted, add accepted frames to the written-frame count, and return `true` iff the whole
     input was consumed.
- `playToEndOfStream()`: mark end-of-stream. `isEnded()` is true iff end-of-stream is marked and the
  driver has no pending data. `hasPendingData()` delegates to the driver.
- `getCurrentPositionUs(sourceEnded)`: `CURRENT_POSITION_NOT_SET` until a start position exists;
  otherwise `startMediaTimeUs + frames * 1_000_000 / sampleRate`, where `sampleRate` is the input
  format rate (fallback 44 100) and `frames` is the driver's played-frame count while streaming,
  else the count of frames written. Deterministic: 480 stereo 16-bit frames at 48 kHz and start
  1 000 000 µs → 1 010 000 µs.
- `setVolume(volume)`: clamp `[0,1]`, store, forward to `driver.setSoftwareVolumeMultiplier`.
- `pause()`: clear playing (stream retained). `flush()`: flush the driver, reset frame accounting,
  clear end-of-stream and start position. `reset()`: stop any active stream, flush, clear
  playing/format/connection/stream state, and restore the container depth to 16.
- Interface-only no-ops: `handleDiscontinuity`, `setPlaybackParameters`,
  `getPlaybackParameters` (returns `PlaybackParameters.DEFAULT`), `setSkipSilenceEnabled`,
  `getSkipSilenceEnabled` (false), `setAudioAttributes`, `getAudioAttributes`
  (`AudioAttributes.DEFAULT`), `setAudioSessionId`, `setAuxEffectInfo`, `enableTunnelingV21`,
  `disableTunneling`, `getAudioTrackBufferSizeUs` (0).
- Stream negotiation, driven by `dacConnectionProvider`:
  - No provider → nothing to negotiate (callers/tests use the driver directly).
  - If the connected device disappears or changes identity (a different `Connected` instance) while
    streaming → stop the driver stream and clear connection state.
  - Adopt the connected device's capabilities. Take the rate from the input format if > 0 else
    44 100; channels from the input format if valid else 2; input depth 16/24/32 (default 16).
    Select a `DacFormat`.
  - The target container depth is 16 whenever `UsbAudioDriver.nativeLoaded` is false (platform
    path).
  - If already streaming and rate, channels or container depth changed → stop, reset frame count
    and start position.
  - Start the driver with the chosen interface/alt/endpoints (defaults 0 / 1 / 0x01 / −1). On
    result 0 mark streaming and record the active connection, rate, channels and depth. On non-zero
    call `listener.onAudioSinkError(IllegalStateException(...))` and leave the sink not streaming.
  - Format selection preference: exact rate+channels+depth; else highest-bitDepth format matching
    rate+channels; else highest-bitDepth format matching rate; else first format matching rate;
    else the first format. No formats → null (use defaults). A format's empty `sampleRates` matches
    any rate.
- PCM conversion raises depth by left-shifting (exact) and lowers it by dropping low bytes,
  little-endian signed samples; the converted buffer is valid until the next call.

---

## 12. `com.revenge.usbaudio.UsbDacManager` and `DacDeviceState`

### 12.1 `DacDeviceState` (top-level sealed class, package `com.revenge.usbaudio`)

```kotlin
sealed class DacDeviceState {
  object Disconnected : DacDeviceState()
  data class PermissionRequired(val device: android.hardware.usb.UsbDevice) : DacDeviceState()
  data class Connected(
    val device: android.hardware.usb.UsbDevice,
    val connection: android.hardware.usb.UsbDeviceConnection,
    val capabilities: DacCapabilities,
    val fileDescriptor: Int,
  ) : DacDeviceState()
  data class Error(val message: String) : DacDeviceState()
}
```

Downstream uses `Connected` (cast + `capabilities`, `device`, `fileDescriptor`) and treats any other
state as "no DAC". Field names/order are hard contracts.

### 12.2 `UsbDacManager`

```kotlin
class UsbDacManager(
  private val context: Context,
  private val driver: UsbAudioDriver = UsbAudioDriver(),
  private val detector: UsbDeviceDetector = UsbDeviceDetector(),
) {
  companion object { const val ACTION_USB_PERMISSION = "com.revenge.usbaudio.USB_PERMISSION" }
  val activeDacFlow: StateFlow<DacDeviceState>
  var onDeviceDetachedCallback: (() -> Unit)?
  fun register()
  fun unregister()
  fun scanExistingDevices()
  fun requestPermission(device: UsbDevice)
}
```

`UsbDacManager(this, usbAudioDriver)` (2-arg positional) is used by `MusicService`; keep the
constructor shape and defaults.

Behaviour:

- `activeDacFlow` starts `Disconnected` and is a `StateFlow`.
- `register()` is idempotent. If already registered, just rescan existing devices. Otherwise
  register a broadcast receiver for `ACTION_USB_DEVICE_ATTACHED`, `ACTION_USB_DEVICE_DETACHED` and
  `ACTION_USB_PERMISSION` (on API 33+ use `Context.RECEIVER_NOT_EXPORTED`), record the registration,
  then scan.
- `unregister()` is idempotent: unregister the receiver if registered, swallowing
  `IllegalArgumentException` ("not registered"), then disconnect.
- `scanExistingDevices()`: among `UsbManager.deviceList`, adopt the first device recognised by the
  detector as a USB audio device; stop at the first match. No USB service → no-op.
- `requestPermission(device)`: build a broadcast `PendingIntent` for `ACTION_USB_PERMISSION` scoped
  to the app package; on API 31+ use `FLAG_MUTABLE or FLAG_UPDATE_CURRENT`, else
  `FLAG_UPDATE_CURRENT`; call `UsbManager.requestPermission`. No USB service → no-op.
- On attach: ignore non-audio devices; if permission is already granted, connect; otherwise
  publish `PermissionRequired` and request permission.
- On permission result: if granted and the device is present, connect; otherwise publish
  `Error("USB permission denied")`.
- On detach: only if the current state is `Connected` for the same `deviceId`, disconnect and then
  invoke `onDeviceDetachedCallback`.
- Connect: if already connected to the same device id → no-op; if connected to a different device →
  disconnect first. A null `UsbManager.openDevice` → `Error("Failed to open USB device
  connection")`. Read the raw descriptors (empty array when absent), parse capabilities via the
  driver, and publish
  `Connected(device, connection, capabilities, connection.fileDescriptor)`.
- Disconnect: when connected, best-effort stop the driver stream and close the connection
  (exceptions swallowed because the device may already be gone), then publish `Disconnected`.
- Receiver callbacks and flow updates happen on the main thread in production. `register`/
  `unregister` must be safe to call repeatedly and must not leak a registration.

---

## 13. `com.revenge.usbaudio.UsbDescriptorParser` (internal) and `UsbDeviceDetector`

### 13.1 `UsbDescriptorParser`

`internal object UsbDescriptorParser { fun parse(data: ByteArray): DacCapabilities }`.

Parses a raw USB configuration-descriptor blob (untrusted input). Contract:

- Input shorter than a descriptor header → `DacCapabilities(uacVersion = 1)` (all defaults empty).
- Descriptor records are length-prefixed by their first byte. Parsing must stop safely on a length
  below 2, a record that would extend past the input, or fewer than 2 bytes remaining. It must
  never read out of bounds, and malformed input must return what was parsed so far (commonly UAC1
  with empty lists) rather than throw.
- Descriptor types: Interface `0x04`, Endpoint `0x05`, CS_INTERFACE `0x24`. Class Audio `0x01`,
  subclasses AudioControl `0x01`, AudioStreaming `0x02`. Protocols UAC1 `0x00`, UAC2 `0x20`.
- On an AudioControl interface, the protocol selects `uacVersion` (1 or 2).
- AudioStreaming interface with alt setting > 0 opens a pending format (interface number + alt). A
  new interface finishes the previous pending format.
- AudioControl CS features:
  - UAC2 Clock Source (`subtype 0x0A`, len ≥ 8) → record `clockSourceId` (byte 3) and seed the
    global rate set with 48 000 (UAC2 ranges require a control request the parser cannot issue).
  - Feature Unit (`subtype 0x06`, len ≥ 6) → `hasHardwareVolume = true`,
    `volumeFeatureUnitId = byte 3`.
- AudioStreaming CS features:
  - AS General (`subtype 0x01`): for UAC2 with len ≥ 11 read `channels = byte 10`.
  - Format Type I (`subtype 0x02`, len ≥ 4, format type byte 3 == 0x01):
    - UAC2: len ≥ 7 → `subslotBytes = byte 4`, `bitDepth = byte 5`.
    - UAC1: len ≥ 8 → `channels = byte 4`, `subslotBytes = byte 5`, `bitDepth = byte 6`,
      `rateCount = byte 7`. If `rateCount > 0`, read up to `min(rateCount, (len-8)/3)` little-endian
      24-bit rates from offset 8, ignoring zero rates. If `rateCount == 0` and len ≥ 14, read a
      continuous range (24-bit min at 8, 24-bit max at 11): if max < min ignore; else add every
      standard UAC1 rate (44 100, 48 000, 88 200, 96 000, 176 400, 192 000) inside `[min,max]`;
      if none match, add min (and max when different from min and min > 0).
  - Endpoint (`0x05`, len ≥ 7): transfer type = byte 3 & 0x03; only isochronous `0x01` counts.
    Direction bit 0x80 set → feedback/sync endpoint, else OUT data endpoint.
- A pending format is emitted only if it has at least one sample rate **or** `uacVersion == 2`.
- For UAC2, after the walk, any format with empty `sampleRates` inherits the global rate list.
- Output `supportedSampleRates` is the sorted union of all discovered rates; `supportedFormats`
  preserves discovery order.

Deterministic anchors (see acceptance tests §18): the UAC1 fixture yields uacVersion 1, hardware
volume with unit id 2, rates `[44100, 48000]`, one format `(interface 1, alt 1, ep 0x01, depth 24,
subslot 3, channels 2, rates [44100,48000], sync null)`; the continuous-range fixture yields
`[44100, 48000, 88200, 96000]`; the UAC2 clock fixture yields uacVersion 2, clockSourceId 1,
48 000 seeded, one format; malformed/empty input yields UAC1 and empty lists.

### 13.2 `UsbDeviceDetector`

```kotlin
class UsbDeviceDetector {
  fun isAudioClassDevice(hasAudioInterface: Boolean): Boolean
  fun isUsbAudioDevice(device: android.hardware.usb.UsbDevice): Boolean
}
```

- `isAudioClassDevice` is an identity pass-through of its argument (convenience for callers that
  already scanned).
- `isUsbAudioDevice`: true iff the device has an interface with `interfaceClass == 1` (AUDIO) and
  `interfaceSubclass == 2` (AUDIOSTREAMING) that exposes at least one OUT-direction endpoint
  (`UsbConstants.USB_DIR_OUT`). Audio-Control-only, microphone-only and MIDI devices are false.

---

## 14. `com.revenge.usbaudio.VolumeController`

```kotlin
class VolumeController(
  val hasHardwareVolume: Boolean,
  val minDb: Float = -60.0f,
  val maxDb: Float = 0.0f,
  var volumeMode: VolumeMode = if (hasHardwareVolume) VolumeMode.HARDWARE else VolumeMode.PURE_BIT_PERFECT,
) {
  fun calculateDbForSlider(ratio: Float): Float
  fun dbToUacMillibel(db: Float): Short
  fun setVolume(ratio: Float)
  fun getEffectiveSoftwareMultiplier(): Double
}
```

- `calculateDbForSlider`: clamp ratio to `[0,1]`; `<= 0` → `minDb`; `>= 1` → `maxDb`; otherwise
  `(60 * log10(r)).coerceIn(minDb, maxDb)`. So 0.5 ≈ −18 dB (test accepts −22..−15).
- `dbToUacMillibel`: `(db * 256).toInt()` clamped to signed 16-bit; −18 dB → −4608; ±500 dB clamp
  to `Short.MIN/MAX`.
- `setVolume`: stores the clamped `[0,1]` slider ratio.
- `getEffectiveSoftwareMultiplier`: `PURE_BIT_PERFECT` and `HARDWARE` → exactly 1.0 (samples
  untouched; attenuation is the DAC's/external responsibility). `SOFTWARE_64BIT` →
  `10^(calculateDbForSlider(sliderRatio)/20)`.
- Default mode: `HARDWARE` when `hasHardwareVolume`, else `PURE_BIT_PERFECT`. `volumeMode` is
  reassignable.

---

## 15. Cross-module type shapes the implementer must compile against

### 15.1 Media3 `AudioProcessor` (external, provided by `libs.media3`)

The three `:audio-dsp` processors implement this interface. Required shape:

```kotlin
interface AudioProcessor {
  fun configure(inputAudioFormat: AudioFormat): AudioFormat
  fun isActive(): Boolean
  fun queueInput(inputBuffer: java.nio.ByteBuffer)
  fun getOutput(): java.nio.ByteBuffer
  fun isEnded(): Boolean
  @Deprecated fun flush()
  fun reset()
  fun queueEndOfStream()

  class AudioFormat(val sampleRate: Int, val channelCount: Int, val encoding: Int)
  class UnhandledAudioFormatException(format: AudioFormat) : Exception
}
```

Constants used: `C.ENCODING_PCM_16BIT`, `C.ENCODING_PCM_24BIT`, `C.ENCODING_PCM_32BIT`,
`C.ENCODING_PCM_FLOAT`, `C.ENCODING_PCM_16BIT_BIG_ENDIAN`, `C.ENCODING_PCM_24BIT_BIG_ENDIAN`,
`C.ENCODING_PCM_32BIT_BIG_ENDIAN`, `C.ENCODING_PCM_8BIT`.

### 15.2 Media3 `AudioSink` (external)

`UsbDacAudioSink` must implement every member. The behavioural members that matter are:
`setListener(AudioSink.Listener)`, `supportsFormat(Format)`, `getFormatSupport(Format)`,
`getCurrentPositionUs(Boolean)`, `configure(Format, Int, IntArray?)`, `play()`,
`handleDiscontinuity()`, `handleBuffer(ByteBuffer, Long, Int): Boolean`, `playToEndOfStream()`,
`isEnded()`, `hasPendingData()`, `getAudioTrackBufferSizeUs()`, `setPlaybackParameters`,
`getPlaybackParameters`, `setSkipSilenceEnabled`, `getSkipSilenceEnabled`, `setAudioAttributes`,
`getAudioAttributes`, `setAudioSessionId`, `setAuxEffectInfo`, `enableTunnelingV21`,
`disableTunneling`, `setVolume(Float)`, `pause()`, `flush()`, `reset()`.
Constants: `CURRENT_POSITION_NOT_SET`, `SINK_FORMAT_SUPPORTED_DIRECTLY`,
`SINK_FORMAT_UNSUPPORTED`; `Format.NO_VALUE`, `Format` fields `sampleMimeType`, `pcmEncoding`,
`sampleRate`, `channelCount`.

### 15.3 `:playback` routing wrapper (downstream, not written by this cluster)

`com.revenge.music.playback.AudioSinkSelector` and `RoutingAudioSink` consume the sink:
`AudioSinkSelector.selectSink(isBitPerfectActive: Boolean, usbDacAudioSink: AudioSink?,
defaultAudioSink: AudioSink): AudioSink`; `createRoutingSink(defaultAudioSink, usbDacAudioSink,
isBitPerfectActive: () -> Boolean): AudioSink`; `RoutingAudioSink.activeSink` is readable. The
sink must therefore behave correctly when wrapped: `supportsFormat` gates selection,
`configure`+`play` after a runtime switch must resume cleanly, and `flush`/`reset` must leave it
reusable. The wrapper replays the last configuration into the newly selected sink.

### 15.4 Downstream usage that fixes the API

- `MusicService`: builds `UsbDacAudioSink(driver = usbAudioDriver, dacConnectionProvider = { … })`,
  constructs `UsbDacManager(this, usbAudioDriver)` and assigns `onDeviceDetachedCallback`, calls
  `register()`/`unregister()`, collects `activeDacFlow`, casts to `DacDeviceState.Connected`, reads
  `capabilities`. It also constructs the three processors directly, sets `StereoWidener.width`,
  calls `DspController.addAudioProcessor/removeAudioProcessor/applyProfile/disableEqualizer`, and
  reads `EqProfileRepository.activeProfile`.
- `EQViewModel`: `ParametricEQParser.parseText/validate`, `EQProfileRepository.profiles/
  activeProfile/getSortedProfiles/importCustomProfile/setActiveProfile/deleteProfile`,
  `DspController.applyProfile/disableEqualizer`.
- `AxionEqViewModel`: constructs `SavedEQProfile`/`ParametricEQBand`, calls
  `saveProfile/setActiveProfile/deleteProfile/applyProfile/disableEqualizer`, reads `profiles`.
- `Player.kt` UI: casts `activeDacFlow` to `Connected`, reads `capabilities.supportedSampleRates`,
  `capabilities.supportedFormats[].bitDepth`, `capabilities.uacVersion` for its badge. Preserve
  these fields; the module itself must not advertise bit-perfect output.

---

## 16. Build configuration (must be preserved)

### `dsp-core/build.gradle.kts`

Plugins: `org.jetbrains.kotlin.jvm`, kotlin serialization. `kotlin { jvmToolchain(21) }`.
Dependencies: `implementation(libs.coroutines.core)`, `implementation(libs.kotlinx.serialization.json)`,
`testImplementation(libs.junit)`. No Android plugin.

### `audio-dsp/build.gradle.kts`

Plugins: `com.android.library`, `com.google.devtools.ksp`, `com.google.dagger.hilt.android`,
kotlin serialization. `namespace = "com.revenge.dsp.audio"`, `compileSdk = 37`, `minSdk = 26`,
Java 21 source/target, `kotlin { jvmToolchain(21) }`. Dependencies: `api(project(":dsp-core"))`,
`api(libs.media3)`, `implementation(libs.timber)`, `implementation(libs.coroutines.core)`,
`implementation(libs.kotlinx.serialization.json)`, `implementation(libs.hilt)`,
`ksp(libs.hilt.compiler)`, `testImplementation(libs.junit)`.

### `usbaudio/build.gradle.kts`

Plugins: `com.android.library` only. `namespace = "com.revenge.usbaudio"`, `compileSdk = 37`,
`minSdk = 26`, Java 21, `kotlin { jvmToolchain(21) }`. Dependencies:
`compileOnly(libs.media3)`, `compileOnly(libs.coroutines.core)`, `testImplementation(libs.media3)`,
`testImplementation(libs.junit)`. Do not add Hilt/Android-lifecycle deps; the module is a leaf.

Both Android modules have an empty `src/main/AndroidManifest.xml` (`<manifest/>`). `:audio-dsp`
must remain Hilt-discoverable; `:usbaudio` has no manifest permissions requirement because USB
permission is requested at runtime through `UsbManager`.

---

## 17. Source layout

Implementations compile into the module source trees
`dsp-core/src/main/kotlin/com/revenge/dsp/core/`,
`audio-dsp/src/main/kotlin/com/revenge/dsp/` and
`usbaudio/src/main/kotlin/com/revenge/usbaudio/`, under the package paths and public FQCNs in
§2–§14 (the compatibility contract). How classes, helpers and files are decomposed within those
roots is an implementation choice. Tests live under each module's `src/test`, mirroring main
packages. Each module's root also carries the `build.gradle.kts` and empty
`src/main/AndroidManifest.xml` described in §16.

---

## 18. Acceptance tests

All cases are deterministic and use no real audio device. Test packages mirror main packages.

### 18.1 `dsp-core` — `BiquadFilterTest`, `ParametricEQParserTest`, `SavedEQProfileTest`

1. Zero-gain peaking leaves a unit impulse at 1.0 ± 0.001.
2. Peaking +6 dB at 1 kHz produces first-sample output > 1.0 and finite (no NaN/Inf).
3. `processStereo(0.5, 0.5)` gives equal left/right with low shelf; after `reset()` the same input
   reproduces the pre-reset output.
4. Frequency exactly at Nyquist (±3 dB HSC) yields finite output.
5. Low shelf −6 dB at 500 Hz/48 kHz converges to `10^(-6/20)` ± 0.01 after 20 000 DC samples.
6. `sampleRate = 0` yields finite output.
7. Parser: mixed PK/LSC/HSC block parses preamp −6.5, 3 bands with exact frequency/gain/Q/type and
   `validate` empty.
8. Parser: `OFF` band skipped, following `ON` band kept.
9. Parser: metadata keys captured (`Device`, `Source`), unknown lines ignored.
10. Parser: lines missing Q or with unknown type `ZZZ` are skipped; valid line kept.
11. Parser: lowercase directives (`preamp:`, `filter`, `on lsc fc …`) accepted.
12. Parser: `LPQ`/`HPQ` round-trip as their enum values.
13. Parser: `toFileFormat` output contains `ON`, `Preamp: -3.5 dB`; reparsed bands equal original.
14. Parser: `toString` contains no `ON` but keeps `Preamp: 1.5 dB`, `Filter 1: PK`, `Fc 440.0 Hz`.
15. Parser: `parseFile` reads a temp file; missing file throws `IllegalArgumentException`.
16. Parser `validate`: preamp 60 / band freq 0 / gain 40 / q 0 → exactly 4 errors containing
    `Preamp`, `Frequency`, `Gain`, `Q factor`. Empty bands → one "at least one band" error.
    21 bands → one "maximum is 20" error.
17. `SavedEQProfile` defaults: `deviceModel == ""`, empty bands, preamp 0, isCustom/isActive false,
    `addedTimestamp > 0`; `parametricEQ` carries preamp + same bands and empty metadata.

### 18.2 `audio-dsp` — processors and controller

1. EQ: profile applied before `configure` is held (`isEnabled()` false), then `configure(48 kHz, 2)`
   installs it (`isEnabled()` true, `isActive()` true).
2. EQ: disabled processor copies samples byte-for-byte (`1234`, `−5678` preserved).
3. EQ: preamp +6.0206 dB scales sample 1000 to 1990..2010.
4. EQ: apply −6 dB preamp then `disable()` restores bypass (3000 unchanged).
5. EQ: enabled +12 dB PK band at 1 kHz raises total energy of a sine input at 48 kHz mono.
6. EQ: rejects >2 channels and non-16-bit with `UnhandledAudioFormatException`.
7. EQ: deprecated `flush()` leaves stage usable (`isEnded()` false; samples pass).
8. EQ: `getOutput()` second call returns empty.
9. Duck: mix 0 + resetGain leaves samples unchanged.
10. Duck: mix 1 attenuates a constant 8000 mono input inside the shelf (0 < output < 8000).
11. Duck: `setMix(5f)` clamps then `resetGain` leaves samples unchanged.
12. Duck: rejects >2 channels and float encoding.
13. Duck: `queueEndOfStream` → `isEnded()` true; `reset()` → `isActive()` false.
14. Widener: setter clamps 0.25→1, 1.5→1.5, 9→2.
15. Widener: width 1 recombines `(1000,−500)`, `(−3000,2500)` unchanged.
16. Widener: width 1.5 maps `(1000,500)` → `(1125,375)`.
17. Widener: width 2 on `(32767,−32768)` stays in range, keeps signs, no independent clipping.
18. Widener: mono passthrough; rejects 6 channels; inactive before configure, inactive after reset.
19. Controller: initial state null/false/1f, empty processor list, not initialized.
20. Controller: `createProcessors()` registers exactly 3 in order EQ, duck, widener; returning them
    asserts identity; `releaseProcessors` empties the list and clears initialized.
21. Controller: `applyProfile` on a configured EQ succeeds, updates `activeProfile.id` and
    `isEnabled`, enables the processor; `disableEqualizer` clears flow and processor.
22. Controller: profile applied before `createProcessors` is restored when the new processor is
    configured.
23. Controller: `setStereoWidth(1.6)` reaches the widener; `setStereoWidth(7)` clamps to 2.
24. Controller: `release()` empties all processors and clears `isInitialized`.

### 18.3 `usbaudio` — converter, driver, parser, detector, volume

1. `BitPerfectPcm.sourceBits`: 16→16, 24→24, float→24, 32→32, 8-bit→null; `bytesPerSample`
   2/3/4/4 and 0 for 8-bit.
2. `outputBits`: (16,{16,24,32})→16; (24,{16,24,32})→24; (24,{16,32})→32; (32,{16,24})→null.
3. 16-bit stereo round-trip exact; 16→24 left-aligns `0x1234` to bytes `00 34 12`; float 0.5→24
   reconstructs 0.5 ± 0.001; 8-bit source throws and does not advance input; 32→16 throws.
4. Driver identity: `isLoaded()` true, version `"1.0.0-usbaudio"`, `nativeLoaded` false.
5. Ring: 16 bytes written/read in order; partial read then wrap preserves sequence; drained ring
   reports no pending data.
6. Ring read clamps to available; empty read returns empty.
7. `getAvailableWrite` shrinks by bytes written and restores after flush.
8. `calculateNominalPacketSamples`: 96000→12, 44100→5, 0→0.
9. `startStream(fd=-1, dataEp=1, 48000)` returns 0; writing 480 stereo 16-bit frames (1920 bytes)
   returns 1920, `getFramesPlayed()` 480, `hasPendingData()` true; `flushStream` resets to 0.
10. Software volume 0.5 on 16-bit mono sample 1000 yields 500 in the ring.
11. Empty/zero-size `writeAudio` returns 0.
12. Descriptor UAC2 clock fixture: uacVersion 2, clockSourceId 1, rates contain 48 000, exactly 1
    format whose rates contain 48 000.
13. Descriptor UAC1 fixture: uacVersion 1, hardware volume true, unit id 2, rates `[44100,48000]`,
    single format (interface 1, alt 1, ep 0x01, depth 24, subslot 3, channels 2, sync null).
14. Descriptor continuous-range fixture: rates `[44100,48000,88200,96000]`.
15. Descriptor malformed/truncated and empty input: UAC1, empty formats, empty rates, null clock.
16. Detector: `isAudioClassDevice` pass-through; real `UsbDevice` scanning returns true only for
    audio-streaming interfaces with OUT endpoints (unit test may rely on the pass-through variant).
17. Volume: slider endpoints 1.0→0 dB, 0.0→−60 dB, 0.5 in −22..−15 dB, out-of-range clamps.
18. Volume: `dbToUacMillibel(-18)` = −4608; ±500 dB clamp to Short bounds.
19. Volume: PURE_BIT_PERFECT and HARDWARE → multiplier 1.0; SOFTWARE_64BIT at slider 1.0 → 1.0 and
    at 0.5 < 1.0 equal to `10^(db/20)`; default mode follows `hasHardwareVolume`.

### 18.4 `usbaudio` — sink

1. `supportsFormat` accepts 24-bit RAW at 96 kHz when advertised; rejects 384 kHz, float, and
   non-RAW (`audio/aac`).
2. With no advertised rates, accepts ≤192 kHz and rejects 384 kHz; `getFormatSupport` returns
   `SINK_FORMAT_SUPPORTED_DIRECTLY` for supported.
3. 480 stereo 16-bit frames at 48 kHz with start 1 000 000 µs → position 1 010 000 µs; not ended
   before EOS; `playToEndOfStream()` alone does not end while data is queued; `flush()` resets
   position to `CURRENT_POSITION_NOT_SET` and pending data false.
4. Full transport: buffer larger than ring capacity → `handleBuffer` returns false and leaves the
   position at exactly capacity; repeated call with a full transport consumes nothing; after
   `flush()` the same call completes and consumes the whole buffer.
5. `reset()` stops playback, clears pending data, resets position.

### 18.5 Cross-cutting

1. Persistence upgrade: a pre-existing `nanosonic_eq_profiles` JSON array with an unknown extra key
   and an active id loads successfully; unknown key ignored, active profile matched by id.
2. Corrupt JSON in `eq_profiles` does not crash construction; repository starts empty and null.
3. `importCustomProfile` produces id prefix `custom_`, `isCustom = true`, copies bands/preamp, and
   is returned first by `getSortedProfiles()` among custom profiles.
4. Honesty/regression: `UsbAudioDriver.nativeLoaded == false`, `isLoaded() == true`, and no module
   code path reports "bit-perfect"/"exclusive" USB output; `PURE_BIT_PERFECT` mode still yields a
   software multiplier of 1.0 and the platform `AudioTrack` remains the transport.

---

## 19. Non-goals / explicitly out of scope

- No native isochronous USB backend, no USB request/URB handling, no ALSA/AAudio exclusivity.
- No real bit-perfect or exclusive-mode claim; platform mixer routing is the contract.
- No changes to `:playback`, `:app`, Hilt component generation, or the media3 version.
- No new public API beyond the surfaces listed above; keep internal helpers (descriptor parser,
  ring-buffer state, biquad channel state) internal.
- No SQLite/Room schema exists in these modules; the SharedPreferences document in §7 is the only
  persistence surface that must survive upgrades.
