# Module Specification — Music Recognition (`:shazamkit` + app recognition stack)

Status: M2 clean-room specification (implementation-independent)
Cluster: `recognition-shazamkit`
Target directories:
- `C:/Users/tirth/Downloads/Projects/Revenge/shazamkit` (library)
- `C:/Users/tirth/Downloads/Projects/Revenge/app/src/main/kotlin/com/revenge/music/recognition` (Android pipeline)
- `C:/Users/tirth/Downloads/Projects/Revenge/app/src/main/kotlin/com/revenge/music/quicksettings` (QS tile)
- `C:/Users/tirth/Downloads/Projects/Revenge/app/src/main/res/xml/recognizer_widget_info.xml` (widget metadata)

Namespaces: `com.revenge.shazamkit`, `com.revenge.shazamkit.models`, `com.revenge.music.recognition`, `com.revenge.music.quicksettings`.

This document is a behaviour/contract specification only. It describes required types, signatures, observable semantics, invariants, wire formats, error taxonomy, threading, cancellation, permissions, and acceptance tests. It does not contain and must not be implemented by copying any source body.

A note on scope: the task template mentions "Listen Together" wire contract, but no Listen Together code exists in this cluster; that requirement is not applicable here. Likewise there is no third-party provider registry — `Shazam` is the single recognition provider, and its contract is specified in Section 4.

---

## 1. Scope and boundaries

The recognition stack turns live microphone audio into a track identity:

1. Check/obtain `RECORD_AUDIO` permission (UI entry points).
2. Capture 10 s of mono 44.1 kHz 16-bit PCM into memory.
3. Resample to 16 kHz 16-bit mono.
4. Compute a Shazam-compatible DejaVu acoustic fingerprint (`data:audio/vnd.shazam.sig;base64,...`).
5. Submit the fingerprint to the Shazam discovery endpoint and map the response to `RecognitionResult`.
6. Surface `RecognitionStatus` to a foreground notification, the in-app screen, and/or the home-screen widget; persist successes to Room history.

Hard invariants:

- **No audio is ever written to disk.** Capture buffers, resampled buffers and PCM arrays are in-memory only and become eligible for GC when the recognition call ends.
- **No real device location leaves the device.** Geolocation fields in the request are synthetic/plausible values; the app's location permissions are not consumed by this cluster.
- **A recognition failure is never fatal.** Every failure is converted to `Result.failure`, `RecognitionStatus.Error`, or `RecognitionStatus.NoMatch`; no uncaught exception may escape the UI/service entry points except coroutine cancellation.
- **Cancellation is not an error state.** A cancelled recognition must propagate `CoroutineException`/`CancellationException`, not be reported as `RecognitionStatus.Error`.

---

## 2. Module build contract

`shazamkit/build.gradle.kts`:

- Android library plugin (`com.android.library`) + Kotlin serialization plugin.
- `namespace = "com.revenge.shazamkit"`, `compileSdk = 37`, `minSdk = 26`, Java/Kotlin toolchain 21, core-library desugaring enabled.
- Dependencies: Ktor client core, OkHttp engine, content negotiation, kotlinx JSON, client encoding; `testImplementation(junit)`.
- The module's `AndroidManifest.xml` declares no permissions and no components (empty `<manifest>` with the Android namespace). All permissions are declared by `:app`.

`app/build.gradle.kts` must declare `implementation(project(":shazamkit"))` and media3 (`libs.media3`) for `SonicAudioProcessor`. `:app` uses `compileSdk = 37`, `minSdk = 26`, `targetSdk = 36`.

`:shazamkit` must contain no `android.*` runtime dependency in its public API; it may depend on the Android Gradle plugin only for packaging. `RecognitionResult`/`RecognitionStatus` must be importable by Compose and Room code without pulling Android types.

---

## 3. Public data model (`com.revenge.shazamkit.models`)

### 3.1 `RecognitionResult`

A plain (non-`@Serializable`) data class. Constructor argument order is exactly:

| # | Name | Type | Notes |
|---|---|---|---|
| 1 | `trackId` | `String` | non-null; may be `""` when neither key nor tag id exists |
| 2 | `title` | `String` | non-null; may be `""` |
| 3 | `artist` | `String` | non-null; sourced from Shazam `subtitle`; may be `""` |
| 4 | `album` | `String?` | |
| 5 | `coverArtUrl` | `String?` | |
| 6 | `coverArtHqUrl` | `String?` | |
| 7 | `genre` | `String?` | blank values normalized to `null` |
| 8 | `releaseDate` | `String?` | free-form text from response |
| 9 | `label` | `String?` | |
| 10 | `lyrics` | `List<String>?` | |
| 11 | `shazamUrl` | `String?` | |
| 12 | `appleMusicUrl` | `String?` | |
| 13 | `spotifyUrl` | `String?` | |
| 14 | `isrc` | `String?` | blank normalized to `null` |
| 15 | `youtubeVideoId` | `String?` | default `null`; 11-char id only |

Equality is data-class structural equality over all 15 properties. This type is persisted field-by-field into `RecognitionHistory` (Section 10) and rendered by the recognition UI.

### 3.2 `RecognitionStatus`

Sealed class, exact members and order:

- `data object Ready`
- `data object Listening`
- `data object Processing`
- `data class Success(val result: RecognitionResult)`
- `data class NoMatch(val message: String = "No matches found")`
- `data class Error(val message: String)`

Observable semantics: a state machine with transitions `Ready → Listening → Processing → (Success | NoMatch | Error) → Ready` (reset). `Listening` may be interrupted by cancellation/reset. `Processing` begins after capture completes and covers resampling, fingerprinting, HTTP, and response mapping. Terminal states are immutable snapshots. Consumers must treat unknown future subclasses as terminal no-ops (the foreground service and widget both have an `else` branch).

### 3.3 Wire DTOs

`ShazamRequestJson` and `ShazamResponseJson` are `@Serializable` data classes using `@SerialName` for all wire names. They are an implementation detail of `:shazamkit` but must remain public for serialization tests. Nullable fields default to `null`; list element types are nullable where the response may contain `null` entries.

`ShazamRequestJson`:

- `geolocation: Geolocation` — `altitude: Double`, `latitude: Double`, `longitude: Double`.
- `signature: Signature` — `samplems: Long`, `timestamp: Long` (seconds), `uri: String` (the data URI fingerprint).
- `timestamp: Long` (seconds)
- `timezone: String` (IANA zone name)

`ShazamResponseJson` consumes a subset: `matches`, `location`, `timestamp`, `timezone`, `track`, `tagid`. `Track` carries `layout`, `type`, `key`, `title`, `subtitle`, `images`, `share`, `hub`, `sections`, `url`, `artists`, `isrc`, `genres`, `relatedtracksurl`, `albumadamid`. Nested shapes required by the mapping:

- `Images`: `background`, `coverart`, `coverarthq`, `joecolor` (all `String?`).
- `Genres`: `primary: String?`.
- `Section`: `type`, `metapages[{image,caption}]`, `tabname`, `metadata[{title,text}]`, `url`, `text: List<String>?`.
- `Hub`: `type`, `image`, `actions[{name,type,id,uri}]`, `options[{caption,actions[{name,type,uri,id}],type,providername,...}]`, `providers[{caption,images{overflow,default},actions[{name,type,uri}],type}]`, `explicit`, `displayname`.
- `Match`: `id`, `offset`, `timeskew`, `frequencyskew`.

JSON parse configuration (consumer side) must tolerate unknown keys (`ignoreUnknownKeys = true`), be lenient, and encode defaults. A response that fails to parse is a `ShazamHttpException` with message `"Malformed response from Shazam"`, not a crash.

---

## 4. Provider contract: `com.revenge.shazamkit.Shazam`

`object Shazam` — process-wide singleton, thread-safe. All public functions may be called from any coroutine context. The object owns one lazily-created HTTP client and one bounded cache; callers must not create parallel clients per request.

### 4.1 Public API

```kotlin
suspend fun recognize(signature: String, sampleDurationMs: Long): Result<RecognitionResult>
fun getPendingRequestsCount(): Int
fun getActiveRequestsCount(): Int
fun clearCache()
fun cancelPendingRequests()
fun cleanup()
```

Semantics:

- `recognize` never throws for expected failures; it returns `Result`. The only exception that may escape is `CancellationException` caused by the caller's own coroutine cancellation or by `cancelPendingRequests()` (see 4.6).
- `getPendingRequestsCount()` = calls admitted but not yet executing HTTP (`inFlight - active`, floored at 0).
- `getActiveRequestsCount()` = calls currently executing an HTTP attempt.
- `clearCache()` drops all cached successes; in-flight requests are unaffected.
- `cancelPendingRequests()` cancels only requests waiting for an execution permit. Requests already in HTTP are allowed to complete. It must remain safe to call concurrently and repeatedly.
- `cleanup()` cancels pending requests, clears the cache, and closes the HTTP client. The object must not be used for recognition afterwards. Intended for process/test teardown.

### 4.2 Input validation (before any queueing or network)

| Condition | Result |
|---|---|
| `signature.isBlank()` | `Result.failure(IllegalArgumentException("Audio signature is empty"))` |
| `sampleDurationMs <= 0` | `Result.failure(IllegalArgumentException("Sample duration must be positive"))` |

Validation is synchronous and must not touch the network, increment queue counters, or consult the cache.

### 4.3 Admission, concurrency, and queueing

Constants (exact values are contract):

| Constant | Value | Meaning |
|---|---|---|
| `MAX_CONCURRENT_REQUESTS` | 2 | simultaneous HTTP attempts |
| `MAX_QUEUE_SIZE` | 50 | admitted in-flight calls; excess is rejected |
| `MIN_REQUEST_INTERVAL_MS` | 1000 | minimum spacing between attempt starts |
| `WAITER_POLL_INTERVAL_MS` | 50 | permit-waiter poll period |

Admission order: validate → cache lookup → increment admitted counter; if admitted count exceeds 50, decrement and return `Result.failure(IllegalStateException("Too many pending recognition requests"))`. Otherwise capture a cancellation epoch, acquire one of the 2 permits (polling), execute, release, decrement admitted in `finally`.

### 4.4 Rate limiting and retries

- Every HTTP **attempt** (including retries) waits until at least 1000 ms have elapsed since the previous attempt start. The rate-limit bookkeeping is serialized so two concurrent callers cannot start together.
- Total attempts per `recognize` call: at most 3 (1 initial + 2 retries).
- Retryable failures: any `java.io.IOException`, HTTP 429, and HTTP 5xx. Non-retryable: HTTP 4xx other than 429, malformed response, mapping/no-match, validation.
- Backoff before retry *n* (0-based): `2000 ms << n` → 2 s then 4 s. Cancellation checks happen before each attempt and between waits.

### 4.5 Success cache

- Key: the exact `signature` string.
- Value: `RecognitionResult` plus insertion timestamp.
- TTL: 300 000 ms. Expired entries are removed on lookup.
- Capacity: 100 entries, access-ordered LRU eviction (reads refresh order).
- Only successes are cached; failures, no-matches and cancellations are not.
- Cache lookup precedes admission, so a hit consumes neither a permit nor the rate-limit slot.
- Operations must be safe under concurrent access.

### 4.6 Cancellation

- Each admitted call captures the current cancellation epoch.
- While waiting for a permit, the waiter polls every 50 ms and must throw `CancellationException("Recognition request cancelled")` as soon as the epoch changes. If the epoch changes at the moment a permit is acquired, the permit is released before throwing.
- During retry/backoff, an epoch change must abort with `CancellationException`.
- A request already executing the HTTP attempt is not aborted mid-call by `cancelPendingRequests`; it may complete, and its result may be cached.
- Coroutine cancellation must propagate unchanged: `recognize` must rethrow `CancellationException` from any internal suspension point (HTTP, backoff, permit wait) rather than wrapping it in `Result.failure`.
- After cancellation the admitted-counter, active-counter and semaphore must be restored in `finally`.

### 4.7 Error taxonomy

`internal class ShazamHttpException(val code: Int, message: String, cause: Throwable? = null) : Exception(message, cause)`.

`internal fun httpErrorFor(statusCode: Int): ShazamHttpException?` maps status → exception, null for 2xx:

| Status | `code` | `message` |
|---|---|---|
| 200–299 | — | null |
| 404 | 404 | `"No match found"` |
| 429 | 429 | `"Too many requests"` |
| 500–599 | status | `"Shazam service temporarily unavailable"` |
| other non-2xx | status | `"Recognition failed (error <status>)"` |

Additional failure surfaces from `recognize`:

- `IllegalArgumentException` (validation, 4.2)
- `IllegalStateException("Too many pending recognition requests")` (queue overflow)
- `ShazamHttpException(404, "No match found")` when the JSON has no `track`
- `ShazamHttpException(status, "Malformed response from Shazam", cause)` on parse failure
- Ktor/OkHttp timeout or I/O exceptions (after retries) as-is

No-match signalling is part of the contract: **the failure message must contain `"No match"` (case-insensitive match by callers)** for both HTTP 404 and a successfully parsed response without a track. UI layers rely on this substring.

### 4.8 External service protocol (Shazam discovery v5)

This is the complete request contract; a clean-room implementation must reproduce it.

**Method/URL**

`POST https://amp.shazam.com/discovery/v5/en/US/android/-/tag/{sessionId}/{requestId}`

- `sessionId`: an uppercase UUID string, randomly generated per request.
- `requestId`: a lowercase UUID string, randomly generated per request.
- Path locale segment `en/US` is fixed.
- Both ids must be non-empty and URL-safe.

**Query parameters (all present, in any order)**

`sync=true`, `webv3=true`, `sampling=true`, `connected=` (empty value), `shazamapiversion=v3`, `sharehub=true`, `video=v3`.

**Headers**

- `User-Agent`: randomly selected per request from a pool of five plausible legacy Android Dalvik UA strings. Examples of the required shape: `Dalvik/2.1.0 (Linux; U; Android 5.0.2; VS980 4G Build/LRX22G)`.
- `Content-Language: en_US`
- `Content-Type: application/json`

**Request body (JSON)**

```text
{
  "geolocation": { "altitude": Double, "latitude": Double, "longitude": Double },
  "signature": { "samplems": Long, "timestamp": Long, "uri": String },
  "timestamp": Long,
  "timezone": String
}
```

- `timestamp` (top-level) and `signature.timestamp` are the same value: current wall-clock time in **seconds** (`epochMillis / 1000`).
- `signature.samplems` is the caller-supplied `sampleDurationMs`.
- `signature.uri` is the fingerprint data URI from Section 5 (`data:audio/vnd.shazam.sig;base64,...`).
- `geolocation` values are synthetic: altitude roughly 100–500 m, latitude −90..90, longitude −180..180. They must be sent even though the app does not read real location.
- `timezone` is a randomly selected valid IANA zone name (pool of at least Europe/Paris, Europe/London, America/New_York, America/Los_Angeles, Asia/Tokyo, Asia/Dubai).

**Timeouts**

- Overall request timeout: 20 000 ms.
- Connect timeout: 10 000 ms.
- Socket timeout: 20 000 ms.
- HTTP errors are not thrown by the engine (`expectSuccess = false`); status is inspected explicitly.

**Failure isolation**

Network/HTTP/parse failures are converted per 4.7. The provider must never leak a partially parsed result. Retries are per 4.4. The provider must not mutate global app state, write files, or post notifications.

### 4.9 Response mapping (behavioural contract)

Given a 2xx response:

1. If `track` is `null`/absent → no result → HTTP 404 `"No match found"`.
2. `trackId`: `track.key` if non-blank, else `tagid` if present, else `""`.
3. `title` = `track.title ?: ""`; `artist` = `track.subtitle ?: ""`.
4. Sections: consider non-null entries; the **first** section with `type == "SONG"` supplies metadata. Metadata lookup is by exact `title` among non-null metadata entries, and only non-blank `text` counts:
   - `album` ← `"Album"`
   - `label` ← `"Label"`
   - `releaseDate` ← `"Released"`
5. `lyrics` = `text` list of the **first** section with `type == "LYRICS"`, if the list is non-empty; otherwise `null`.
6. `coverArtUrl` ← `images.coverart`; `coverArtHqUrl` ← `images.coverarthq` (raw, may be null).
7. `genre` ← `genres.primary` if non-blank.
8. `shazamUrl` ← `track.url`.
9. `appleMusicUrl`: first non-null entry of `hub.options` whose `providername` contains `"apple"` ignoring case; take the first non-null action's `uri`.
10. `spotifyUrl`: first non-null entry of `hub.providers` whose `caption` contains `"spotify"` ignoring case; take the first non-null action's `uri`.
11. `youtubeVideoId`: first non-null `hub.options` entry whose `type` contains `"video"` ignoring case; take the first non-null action's `uri`; extract an 11-character id:
    - if a `v=` query parameter exists, take the value up to `&`/`?` and accept it only if exactly 11 chars from `[A-Za-z0-9_-]`;
    - otherwise take the last path segment (strip `?`/`&` suffix) and accept under the same rule;
    - `null` for null/blank/unparseable URIs or ids of other lengths.
12. `isrc` ← `track.isrc` if non-blank.
13. All other fields are ignored.

Mapping is total: any combination of missing/null/`null`-list-elements must not throw.

### 4.10 Threading

- Public functions may be called from `Main`, `Default`, `IO`, or arbitrary dispatchers.
- The only blocking network call happens inside the Ktor/OkHttp engine; callers must not assume a dispatcher.
- Cache and counters use thread-safe constructs; rate-limit bookkeeping is mutually excluded; permit acquisition suspends rather than blocks.
- No public function holds a lock across a network call.

### 4.11 Test seam

For deterministic acceptance tests, the HTTP transport must be injectable internally (for example an `internal` client/engine field with a default, or an `internal` overload accepting an `HttpClient`). The injection point must not change public semantics. Tests must be able to point the request at `MockEngine` without a real network.

---

## 5. Acoustic fingerprinting

### 5.1 `com.revenge.music.recognition.VibraSignature`

```kotlin
object VibraSignature {
  const val REQUIRED_SAMPLE_RATE: Int = 16_000
  @JvmStatic fun fromI16(samples: ByteArray): String
}
```

`fromI16` delegates to the internal generator. Contract:

- Input: non-empty `ByteArray` with even length; little-endian signed 16-bit PCM at 16 000 Hz, mono.
- Output: `String` beginning exactly with `data:audio/vnd.shazam.sig;base64,` followed by standard Base64 (no line wrapping).
- Invalid input (empty or odd length) throws `IllegalArgumentException` with message `"samples must be a non-empty byte array with even length (16-bit PCM)"`.
- Deterministic: identical input bytes always produce an identical output string.
- The function does not mutate the input and has no I/O, no Android dependencies, and no global mutable state (the generator state is per call).

### 5.2 `com.revenge.music.recognition.ShazamSignatureGenerator`

`internal object` with one entry point: `fun fromI16(samples: ByteArray): String`. Implementation is single-pass and allocation-bounded; memory must not grow with input length beyond fixed ring buffers (256 FFT frames).

Processing parameters (contract for compatibility):

| Parameter | Value |
|---|---|
| Sample rate | 16 000 Hz |
| FFT size | 2048 |
| FFT hop | 128 samples (8 ms) |
| Window | Hann, periodic, denominator 2049 |
| Frequency bins used | 10 .. (1025 − 8) exclusive of edges |
| Peak bands (Hz) | 250–520, 520–1450, 1450–3500, 3500–5500 (band ids 0–3) |
| Peak threshold | magnitude < 1/64 is ignored |
| Max peaks | 255 |
| Max duration | 12.0 s |
| Stop rule | stop feeding when elapsed ≥ 12 s **and** total peaks ≥ 255 |

Peak extraction semantics (observable through the encoded signature):

- Spectrum magnitudes are scaled by `1/131072` and floored at `1e-10`.
- Spectral spreading: each magnitude is the max over itself and the next two bins; spreading is propagated across the previous 1, 3 and 6 spread frames; the peak frame is compared with the spread frame 49 frames earlier, the raw frame 46 frames earlier, and spread frames at offsets `-53, -45, 165, 172, 179, 186, 193, 200, 214, 221, 228, 235, 242, 249` (modulo the ring).
- A peak is accepted only if it exceeds all compared neighbours (`-10, -7, -4, -3, 1, 2, 5, 8` within the 49-frame spread) and the minimum threshold.
- Peak magnitude encoding: `floor(ln(max(1/64, magnitude)) * 1477.3 + 6144)`.
- Frequency: peak magnitude is locally quadratic-interpolated from the bin and its two neighbours; corrected bin = `binPos * 64 + delta`; frequency Hz = `correctedBin * (16000 / 2 / 1024 / 64)` = `correctedBin * 0.1220703125`.
- Peaks with frequency `< 250` or `> 5500` are discarded.
- FFT-pass number: `spreadFramesWritten - 46` (non-negative once recognition starts).

### 5.3 Encoded signature format (wire-compatible DejaVu container)

Output byte layout before Base64:

1. **Header — 48 bytes, little-endian 32-bit words**

| Offset | Value |
|---|---|
| 0 | magic `0xCAFE2580` |
| 4 | CRC32 placeholder (patched in step 6) |
| 8 | `contentsSize + 8` |
| 12 | `0x94119C00` |
| 16 | 0 |
| 20 | 0 |
| 24 | 0 |
| 28 | `3 << 27` |
| 32 | 0 |
| 36 | 0 |
| 40 | `numSamplesFed + 0.24 * 16000`, truncated to int |
| 44 | `(15 << 19) + 0x40000` |

2. **Outer stream header — 8 bytes**: `0x40000000`, then `contentsSize + 8`.

3. **Contents**: for each band id 0..3 that produced at least one peak, in ascending band order:
   - u32 band container tag `0x60030040 + bandId`
   - u32 payload byte length
   - payload (below)
   - zero padding to a 4-byte boundary (`(4 - len % 4) % 4` bytes of `0x00`)
   Empty bands are omitted entirely (no container).

4. **Band payload**: an ordered sequence of peak records. Time is encoded as a delta from the previous peak's FFT-pass number (initial previous = 0):
   - if `delta >= 255`: emit byte `0xFF`, then the absolute pass number as u32 LE, then set previous = absolute;
   - otherwise emit one byte = `delta`;
   - then u16 LE peak magnitude;
   - then u16 LE corrected frequency bin.
   After an `0xFF` escape, the one-byte delta is `0` by construction (`absolute - absolute`).

5. `contentsSize` is the sum of all band container bytes including tags, lengths and padding.

6. **CRC32**: compute CRC-32 (standard IEEE polynomial, `java.util.zip.CRC32`) over bytes from offset 8 to the end of the buffer and store it little-endian at offsets 4–7.

7. **Base64**: encode with `NO_WRAP`; prefix `data:audio/vnd.shazam.sig;base64,`.

Invariants:

- Output is always a valid non-empty data URI for valid input, even if no peaks are found (header + empty contents + CRC).
- Every emitted band payload is ≤ 4-byte aligned within the contents stream.
- The CRC covers the outer header and all contents, never the magic/CRC words.
- No timestamps or randomness in the output; reproducibility is mandatory.

---

## 6. Capture and resampling

### 6.1 `com.revenge.music.recognition.DecodedAudio`

`data class DecodedAudio(val data: ByteArray, val channelCount: Int, val sampleRate: Int, val pcmEncoding: Int)`.

- `equals`/`hashCode` must use **content** equality for `data` (`contentEquals`/`contentHashCode`) so fixtures compare structurally.
- `pcmEncoding` uses `android.media.AudioFormat` constants (e.g., `ENCODING_PCM_16BIT`).
- `data` is interleaved little-endian PCM.

### 6.2 `com.revenge.music.recognition.AudioResampler`

```kotlin
object AudioResampler {
  suspend fun resample(decodedAudio: DecodedAudio, outputSampleRate: Int): Result<DecodedAudio>
}
```

Contract:

- Runs CPU work off the caller thread (`Dispatchers.Default`).
- If input `sampleRate == outputSampleRate`, returns `Result.success` with the **same instance** (no copy, no processing).
- Otherwise uses media3 `SonicAudioProcessor` configured to the target rate, preserving channel count and PCM encoding. Output `sampleRate` equals `outputSampleRate`, `channelCount` equals the configured output channel count (mono input stays mono), `pcmEncoding` equals the configured output encoding (PCM 16-bit in this pipeline).
- Output bytes preserve sample order; for mono 16-bit the result length is `inputSamples * outRate / inRate` ± resampler padding, and must always have even length.
- Any processing error returns `Result.failure(e)`; the resampler must be reset in `finally`.
- Cancellation must propagate: if the coroutine is cancelled during the output drain loop, the `CancellationException` must escape, not be wrapped in `Result.failure`.
- Rejects no specific input; a zero/negative `outputSampleRate` is an error surfaced as `Result.failure`.

### 6.3 `com.revenge.music.recognition.MusicRecognitionService`

`object`, process-wide, single active recognition at a time (the foreground service and widget both call it and must serialize by only running one at a time).

Public API:

```kotlin
val recognitionStatus: StateFlow<RecognitionStatus>   // initial Ready
fun hasRecordPermission(context: Context): Boolean
suspend fun recognize(context: Context): RecognitionStatus
fun reset()
```

Capture parameters:

| Parameter | Value |
|---|---|
| Source | `MediaRecorder.AudioSource.MIC` |
| Sample rate | 44 100 Hz |
| Channels | mono |
| Encoding | 16-bit PCM |
| Duration | 10 000 ms (wall clock) |
| Buffer size | `AudioRecord.getMinBufferSize(...)` |

Semantics of `recognize`:

1. Runs on `Dispatchers.IO`.
2. If `hasRecordPermission(context)` is false: set and return `RecognitionStatus.Error("Microphone permission not granted")`. It must not create an `AudioRecord`.
3. Publish `Listening` **before** capture starts.
4. Capture until 10 s elapsed or the coroutine is no longer active, reading into the min-size buffer and appending bytes; always `stop()` and `release()` the recorder in `finally`.
5. Publish `Processing` after capture.
6. Wrap captured bytes as `DecodedAudio(data, 1, 44100, ENCODING_PCM_16BIT)` and resample to `VibraSignature.REQUIRED_SAMPLE_RATE`. On failure publish/return `Error("Failed to resample audio: <message>")`.
7. Validate the resampled buffer: mono, 16 000 Hz, PCM 16-bit, platform byte order little-endian, non-empty, even length. Violation is a fatal internal error surfaced as `Error` (message containing `"Invalid audio format for fingerprint generation"`).
8. Compute fingerprints via `VibraSignature.fromI16`. On exception publish/return `Error("Failed to generate fingerprint: <message>")`.
9. `sampleDurationMs = (data.size / 2) * 1000L / 16000` (integer division, truncating).
10. Call `Shazam.recognize(signature, sampleDurationMs)`.
11. Map success → `Success(result)`; failure whose message contains `"No match"` (case-insensitive) → `NoMatch("No matches found. Try again with clearer audio.")`; any other failure → `Error(error.message ?: "Unknown error")`.
12. Return the terminal status.
13. Unexpected exceptions anywhere → `Error(e.message ?: "Recognition failed")`, state updated, returned.
14. **Cancellation**: a `CancellationException` must propagate after releasing the recorder; it must not be converted to `Error`. `reset()` must not cancel an active capture by itself; it only sets `Ready`.

`hasRecordPermission` uses `ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PERMISSION_GRANTED`.

`reset()` sets the flow value to `Ready`; it is idempotent and callable from any thread.

Resource/temp-file contract: captured PCM exists only in memory; no temp file is created. The only file in this cluster is the widget album-art cache (Section 9).

---

## 7. Permission and entry-point flows

Permissions required in `app/src/main/AndroidManifest.xml`:

- `android.permission.RECORD_AUDIO` (runtime, dangerous)
- `android.permission.POST_NOTIFICATIONS` (runtime on API 33+)
- `android.permission.FOREGROUND_SERVICE`
- `android.permission.FOREGROUND_SERVICE_MICROPHONE`
- `android.permission.INTERNET`
- `android.permission.ACCESS_NETWORK_STATE`
- `android.permission.ACCESS_COARSE_LOCATION` / `ACCESS_FINE_LOCATION` are declared for other app features; the recognition cluster must not request or consume them.

### 7.1 `RecognitionLaunchActivity`

`class RecognitionLaunchActivity : Activity()`, transparent theme, `noHistory=true`, `excludeFromRecents=true`, `exported=false`, `launchMode=singleTask`. No content view.

Behaviour on `onCreate` and `onNewIntent`:

1. If `RECORD_AUDIO` is granted → start `RecognitionForegroundService`:
   - API ≥ 26: `startForegroundService(intent)` (swallow `ForegroundServiceStartNotAllowedException` on API ≥ 31; rethrow other exceptions);
   - API < 26: `startService(intent)`.
2. Else → start `MainActivity` with `action = "com.revenge.music.action.RECOGNITION"` and boolean extra `"auto_start_recognition" = true`, flags `NEW_TASK | SINGLE_TOP`.
3. Always `finish()`. The activity never asks for permission itself and never shows UI.

It is the target of the Quick Settings tile and may be launched by other shortcuts.

### 7.2 In-app flow (`RecognitionScreen`, route `"recognition"`)

- Observes `MusicRecognitionService.recognitionStatus` with `collectAsState`.
- Checks `RECORD_AUDIO` and, when absent, launches `ActivityResultContracts.RequestPermission()`; on grant it immediately starts `recognize(context)`, on denial it stays on the ready screen (the service will also return `Error` if reached).
- On entry and on dispose, calls `reset()` so a stale terminal state is not shown.
- `Ready` → start action; `Listening` → cancel action (calls `reset()`, which does not stop the active capture — the UI only returns to Ready; the service stops at its own termination); `Processing` → progress; `NoMatch`/`Error` → retry.
- On `Success`, offers save-to-history, play-on-app (YouTube search for `"<title> <artist>"`, `FILTER_SONG`, first `SongItem`, play radio), retry and close.
- Navigation: `"recognition"` and `"recognition_history"` are registered routes.

### 7.3 `MusicRecognizerTileService` (`com.revenge.music.quicksettings`)

- `TileService` with `BIND_QUICK_SETTINGS_TILE` permission, exported, label `qs_tile_music_recognizer`.
- `onStartListening()`: sets icon `R.drawable.mic`, `Tile.STATE_INACTIVE`, calls `updateTile()`. The tile is stateless — it must not reflect recognition progress.
- `onClick()`: builds an `Intent(RecognitionLaunchActivity)` with `NEW_TASK | NO_ANIMATION`; on API ≥ 34 wraps it in an immutable `PendingIntent` (request code 0, `UPDATE_CURRENT`) and calls `startActivityAndCollapse(PendingIntent)`; below 34 calls the deprecated `startActivityAndCollapse(Intent)`.
- No permission handling here; `RecognitionLaunchActivity` routes the permission case to the app.

### 7.4 Widget entry point

The 1×1 widget's mic click path is described in Section 9; it has its own permission check and routes to `MainActivity` when permission is missing.

---

## 8. `RecognitionForegroundService`

`class RecognitionForegroundService : Service()`, `exported=false`, `foregroundServiceType="microphone"`, not bound (`onBind` returns null).

### 8.1 Lifecycle

- `onCreate`: create the notification channel (API ≥ 26).
- `onStartCommand`: must call `startForeground` before recognition; return `START_NOT_STICKY` in all paths.
- `onDestroy`: cancel recognition job, status-collection job, cancel the service scope; remove the notification unless `keepNotificationOnStop` is true.

### 8.2 Foreground start

- Build a low-priority ongoing notification with small icon `R.drawable.ic_widget_mic`, title `recognize_music`, text `recognition_notification_listening`.
- API ≥ 29: `startForeground(NOTIFICATION_ID, notification, FOREGROUND_SERVICE_TYPE_MICROPHONE)`; below: two-arg `startForeground`.
- `SecurityException` (missing permission/type) → log, `stopSelf()`, return false (do not start recognition).
- On API ≥ 31, `ForegroundServiceStartNotAllowedException` (matched by class name) → log, `stopSelf()`, return false. Other runtime exceptions rethrow.
- Channel: id `"recognition_channel"`, name `recognition_notification_channel_name`, `IMPORTANCE_LOW`, description set, badge disabled.
- Notification id constant `9100`; result `PendingIntent` request code `9101`.

### 8.3 Recognition orchestration

On a successful foreground start:

1. If a recognition job is already active, do nothing.
2. Reset flags (`keepNotificationOnStop=false`, `terminalStateHandled=false`) and call `MusicRecognitionService.reset()`.
3. Launch a status collector on the service scope that ignores `Ready` and calls `renderStatus` for every other state.
4. Launch the recognition call; if it returns `Error` while the flow's current value is not `Error` (e.g., early permission failure), render that error.

### 8.4 Rendering and terminal handling

Notifications are rebuilt with `setOnlyAlertOnce(true)`, `setOngoing(!isTerminal)`, `setAutoCancel(isTerminal)`, `PRIORITY_LOW`, small icon `ic_widget_mic`.

| Status | Notification |
|---|---|
| `Listening` | title `recognize_music`, text `recognition_notification_listening`, ongoing |
| `Processing` | title `recognize_music`, text `recognition_notification_processing`, ongoing |
| `Success` | title = result title, text = result artist, terminal, content intent + "Listen on Revenge" action |
| `NoMatch` | title `recognize_music`, text `recognition_notification_no_match`, terminal |
| `Error` | title `recognize_music`, text `recognition_notification_failed`, terminal |
| `Ready` | ignored |

- Terminal states are handled at most once per run (`terminalStateHandled`).
- `NoMatch`/`Error` finish immediately as a persistent notification (see 8.5).
- `Success`: immediately render title/artist with the result `PendingIntent`, then asynchronously load cover art from `coverArtHqUrl ?: coverArtUrl`:
  - overall wait bounded by `withTimeoutOrNull(1500 ms)`;
  - HTTP connect timeout 1200 ms and read timeout 1200 ms, redirects followed, decode with `BitmapFactory.decodeStream`;
  - any failure yields null; if a bitmap is obtained, re-render the terminal notification with it as the large icon;
  - then finish as a persistent notification.
- Bitmap loading must run on `Dispatchers.IO` and must never crash the service.
- The result `PendingIntent` targets `MainActivity` with `action = "com.revenge.music.action.RECOGNITION"`, flags `NEW_TASK | SINGLE_TOP`, and string extras `"recognition_track_id"`, `"recognition_title"`, `"recognition_artist"` (values from the result), immutable + `UPDATE_CURRENT`.

### 8.5 Persistent finish

`finishWithPersistentResult()` sets `keepNotificationOnStop = true`, calls `stopForeground(STOP_FOREGROUND_DETACH)` (keeps the notification after service stop) and `stopSelf()`. `onDestroy` must not remove it in that case. The notification remains tappable (content/action intents still valid).

### 8.6 Service extras (public constants)

```kotlin
const val EXTRA_RECOGNITION_TRACK_ID = "recognition_track_id"
const val EXTRA_RECOGNITION_TITLE = "recognition_title"
const val EXTRA_RECOGNITION_ARTIST = "recognition_artist"
```

`MainActivity` consumes these for `ACTION_RECOGNITION`; the service must emit exactly these keys.

---

## 9. Home-screen widget contract

### 9.1 Provider metadata (`res/xml/recognizer_widget_info.xml`)

- `minWidth/minHeight = 48dp`, `minResizeWidth/minResizeHeight = 48dp`, `targetCellWidth/Height = 1`.
- `updatePeriodMillis = 0` (no periodic updates; state is pushed by broadcast).
- `previewLayout` and `initialLayout` = `@layout/widget_recognizer_tiny`.
- `description = @string/widget_recognizer_description`, `widgetCategory = home_screen`, `resizeMode = horizontal|vertical`.

### 9.2 `MusicRecognizerWidgetReceiver` (AppWidgetProvider)

Manifest: exported receiver with filter for `android.appwidget.action.APPWIDGET_UPDATE` plus actions `com.revenge.music.widget.recognizer.TAP_MIC`, `...UPDATE`, `...RESET`. Provider meta-data points at the XML above.

Size classes by `OPTION_APPWIDGET_MIN_WIDTH`:

- `< 110dp` → `widget_recognizer_tiny` (mic only)
- `110–229dp` → `widget_recognizer_compact` (art + text + mic)
- `≥ 230dp` → `widget_recognizer_wide`

State is stored in `SharedPreferences "recognizer_widget_prefs"`:

| Key | Type | Default |
|---|---|---|
| `state` | Int | 0 |
| `song_title` | String | `""` |
| `artist_name` | String | `""` |
| `error_message` | String | `""` |
| `pulse_frame` | Int | 0 |
| `cover_art_path` | String | `""` |

State codes: `0 IDLE`, `1 LISTENING`, `2 PROCESSING`, `3 SUCCESS`, `4 NO_MATCH`, `5 ERROR`.

Click behaviour:

- Mic/admin tap → `ACTION_START_RECOGNITION`. If state is LISTENING or PROCESSING, start `MusicRecognizerWidgetService` with `ACTION_STOP_RECOGNITION` (stop). If terminal, reset state to IDLE first. If `RECORD_AUDIO` is missing, open `MainActivity` with `ACTION_RECOGNITION`. Otherwise `startForegroundService(ACTION_START_RECOGNITION)` on API ≥ 26 (swallowing `ForegroundServiceStartNotAllowedException` on API ≥ 31) or `startService` below.
- Info area/album art tap → `MainActivity` with `ACTION_RECOGNITION`, `NEW_TASK | SINGLE_TOP`.
- `ACTION_RESET_STATE` → reset all prefs, delete the album-art cache file, redraw.
- `ACTION_UPDATE_WIDGET` → redraw all widget ids.

Rendering:

- SUCCESS shows cached art if present, song title (fallback `widget_recognizer_unknown_song`) and artist (fallback `widget_recognizer_unknown_artist`).
- IDLE shows `widget_recognizer_tap_to_search`; LISTENING/PROCESSING/NO_MATCH/ERROR use their strings; ERROR additionally shows `error_message` or generic fallback.
- Active states use `widget_mic_button_bg_active` and cycle pulse drawables `widget_mic_pulse_1..4` by `pulse_frame % 4`; inactive uses `widget_mic_button_bg` and `widget_mic_pulse_idle`.
- Draw failures must degrade to no art, never throw.

### 9.3 `MusicRecognizerWidgetService`

Manifest: `exported=false`, `foregroundServiceType="microphone"`.

- Actions (service-internal constants): `com.revenge.music.widget.recognizer.START`, `...STOP`.
- On START: post a low-importance ongoing foreground notification (channel `music_recognizer_widget`, id `9001`, title `widget_recognizer_listening`, text `widget_recognizer_notification_text`, cancel action → service STOP, content → `MainActivity`; microphone type on API ≥ 34), set widget state LISTENING, start a pulse job (4 frames, 600 ms interval) and a recognition coroutine.
- Recognition coroutine calls `MusicRecognitionService.recognize(this)`:
  - SUCCESS: download cover art via Coil at ≤200×200, center-crop to square, round 24 px, compress PNG quality 90 into `cacheDir/recognizer_widget_art.png`; store state/title/artist/art path; insert a `RecognitionHistory` row via Hilt entry point `RecognizerWidgetEntryPoint.databaseDao()`. History insert failure is swallowed (widget still shows the result). Blank art URL → empty path.
  - NO_MATCH / ERROR: store state and message, clear art path.
  - Any other status: state IDLE.
  - Any thrown exception: state ERROR with `e.message ?: widget_recognizer_error`.
- Finally: redraw widgets, `stopForeground(STOP_FOREGROUND_REMOVE)`, `stopSelf()`.
- STOP action: cancel jobs, state IDLE, pulse 0, redraw, remove foreground notification, stop self.
- Pulse job must be cancelled before rendering a terminal state and in `onDestroy`; the scope is cancelled in `onDestroy`.
- Temp-file lifecycle: exactly one cache file, `recognizer_widget_art.png`, overwritten on each success and deleted on explicit RESET. It contains no audio. It is not deleted automatically on service stop (needed by the widget).

### 9.4 Duplicate-history caveat

Both the widget service (on success) and the in-app `RecognitionScreen` (via its save action) insert rows. There is no dedupe flag in the current contract; the spec requires that history insertion be idempotent per recognition only if a shared marker is introduced. Until then, duplicates from the widget path followed by an in-app save are accepted and must not break the history UI.

---

## 10. Recognition history contract

### 10.1 Entity — `com.revenge.music.db.entities.RecognitionHistory` (`:core`)

Room entity, table `recognition_history`, index on `trackId` (non-unique):

```kotlin
@Entity(tableName = "recognition_history", indices = [Index(value = ["trackId"], unique = false)])
data class RecognitionHistory(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val trackId: String,
  val title: String,
  val artist: String,
  val album: String? = null,
  val coverArtUrl: String? = null,
  val coverArtHqUrl: String? = null,
  val genre: String? = null,
  val releaseDate: String? = null,
  val label: String? = null,
  val shazamUrl: String? = null,
  val appleMusicUrl: String? = null,
  val spotifyUrl: String? = null,
  val isrc: String? = null,
  val youtubeVideoId: String? = null,
  val recognizedAt: LocalDateTime = LocalDateTime.now(),
  val liked: Boolean = false,
)
```

`RecognitionResult` maps 1:1 to the first 15-ish fields; `recognizedAt` is assigned at insertion (`LocalDateTime.now()`); `liked` defaults false. The entity must be registered in `MusicDatabase` (`RecognitionHistory::class` in the entity array) and require a Room schema/migration consistent with existing DB versioning.

### 10.2 DAO contract (`DatabaseDao`)

- `fun recognitionHistory(): Flow<List<RecognitionHistory>>` — all rows, stable ordering (implementation currently returns insertion/DAO order; UI groups by date).
- `fun recognitionHistoryById(id: Long): Flow<RecognitionHistory?>`
- `fun searchRecognitionHistory(query: String): Flow<List<RecognitionHistory>>`
- `@Transaction @Query("DELETE FROM recognition_history") fun clearRecognitionHistory()`
- `fun deleteRecognitionHistoryById(id: Long)`
- `fun updateRecognitionHistoryLiked(id: Long, liked: Boolean)`
- `fun insert(recognitionHistory: RecognitionHistory): Long`
- `@Delete fun delete(recognitionHistory: RecognitionHistory)`

### 10.3 History UI (`RecognitionHistoryScreen`, route `"recognition_history"`)

- Collects `recognitionHistory()`; search filters by title or artist (case-insensitive substring).
- Groups by `recognizedAt` local date: `Today`, `Yesterday`, `This Week`, else `"MMMM yyyy"`.
- Clear-all with confirmation; per-item delete with confirmation; opens the recognition result / play action per item.
- Empty state when no rows. Must not crash on null optional fields or duplicate `trackId`s.

---

## 11. Android manifest / resources contract (compile-critical)

Manifest components (`:app`):

```xml
<activity android:name=".recognition.RecognitionLaunchActivity"
    android:excludeFromRecents="true" android:exported="false"
    android:launchMode="singleTask" android:noHistory="true"
    android:theme="@style/Theme.Metrolist.Transparent" />

<receiver android:name=".widget.MusicRecognizerWidgetReceiver" android:exported="true"
    android:label="@string/widget_recognizer_name">
  <intent-filter>
    <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    <action android:name="com.revenge.music.widget.recognizer.TAP_MIC" />
    <action android:name="com.revenge.music.widget.recognizer.UPDATE" />
    <action android:name="com.revenge.music.widget.recognizer.RESET" />
  </intent-filter>
  <meta-data android:name="android.appwidget.provider"
      android:resource="@xml/recognizer_widget_info" />
</receiver>

<service android:name=".widget.MusicRecognizerWidgetService"
    android:exported="false" android:foregroundServiceType="microphone" />

<service android:name=".recognition.RecognitionForegroundService"
    android:exported="false" android:foregroundServiceType="microphone" />

<service android:name=".quicksettings.MusicRecognizerTileService"
    android:label="@string/qs_tile_music_recognizer"
    android:icon="@drawable/ic_qs_revenge_logo"
    android:exported="true"
    android:permission="android.permission.BIND_QUICK_SETTINGS_TILE">
  <intent-filter>
    <action android:name="android.service.quicksettings.action.QS_TILE" />
  </intent-filter>
</service>
```

Resources required to compile/link:

- Strings: `recognize_music`, `recognition_notification_listening`, `recognition_notification_processing`, `recognition_notification_no_match`, `recognition_notification_failed`, `recognition_notification_channel_name`, `recognition_notification_channel_desc`, `listen_on_revenge`, `recognition_history`, `clear_recognition_history`, `clear_recognition_history_confirm`, `qs_tile_music_recognizer`, and the `widget_recognizer_*` family (name, description, channel name/desc, notification text, tap_to_search, listening, processing, no_match, error, error_generic, unknown_song, unknown_artist).
- Drawables: `ic_widget_mic`, `mic`, `ic_qs_revenge_logo`, `widget_mic_button_bg`, `widget_mic_button_bg_active`, `widget_mic_pulse_1..4`, `widget_mic_pulse_idle`, plus history/action icons used by the screens.
- Layouts: `widget_recognizer_tiny`, `widget_recognizer_compact`, `widget_recognizer_wide` with ids `widget_recognizer_mic_container`, `widget_recognizer_pulse`, `widget_recognizer_text_area`, `widget_recognizer_album_art`, `widget_recognizer_song_title`, `widget_recognizer_artist_name`, `widget_recognizer_tiny_mic_container`, `widget_recognizer_tiny_pulse`, `widget_recognizer_tiny_root`.

---

## 12. Cross-module compile contract

- `:app` depends on `:shazamkit`; imports used: `com.revenge.shazamkit.Shazam`, `com.revenge.shazamkit.models.RecognitionResult`, `com.revenge.shazamkit.models.RecognitionStatus`. Public declarations in Section 3 and 4 must keep these packages/names.
- `:app` imports `com.revenge.music.db.entities.RecognitionHistory` and DAO methods from `:core`; `:core` must expose them as in Section 10.
- `MainActivity` must expose `const val ACTION_RECOGNITION = "com.revenge.music.action.RECOGNITION"` and `const val EXTRA_AUTO_START_RECOGNITION = "auto_start_recognition"`. `handleRecognitionIntent` reads and removes the boolean extra and navigates to `"recognition"` (with `?autoStart=true` when set) using `launchSingleTop = true`.
- `RecognitionForegroundService` extra keys must match the values in 8.6 and remain public constants.
- `MusicRecognizerWidgetReceiver` and `MusicRecognizerWidgetService` share the `SharedPreferences` name and state constants; changing either without the other breaks the widget.
- media3 `SonicAudioProcessor`/`AudioProcessor` are used by `AudioResampler`; media3 must be on `:app`'s classpath.
- Hilt: `RecognizerWidgetEntryPoint` (`@EntryPoint @InstallIn(SingletonComponent::class)`) exposes `DatabaseDao` and must remain discoverable from the widget service.

---

## 13. Failure isolation, privacy, and cleanup summary

- Recognition failure paths never crash: `Result` in `:shazamkit`; `RecognitionStatus` in `:app`; notification/widget fallbacks in UI.
- No retry storm: at most 3 attempts, ≥1 s apart, at most 2 concurrent.
- No battery/audio leak: recorder always stopped/released; resampler reset; service scope cancelled; widget jobs cancelled.
- No temp audio: PCM is in-memory only. Widget album art is the only cached artifact and is deleted on widget RESET.
- Privacy: no real location; no audio persisted or uploaded beyond the fingerprint; fingerprint is a lossy acoustic feature, not the raw recording.
- Notification persistence: a terminal result survives service destruction by design (`STOP_FOREGROUND_DETACH`); it is cleared by the user or a subsequent run (`setAutoCancel(true)` / same notification id replacement).
- Permission absence is always converted to an error state or a navigation-to-app path, never an uncaught security exception.

---

## 14. Target files and responsibilities

| File | Responsibility |
|---|---|
| `shazamkit/build.gradle.kts` | library build contract, Ktor deps |
| `shazamkit/src/main/AndroidManifest.xml` | empty library manifest |
| `shazamkit/src/main/kotlin/com/revenge/shazamkit/Shazam.kt` | provider: validation, queue, rate limit, retry, cache, cancellation, HTTP protocol, response mapping, error taxonomy |
| `shazamkit/src/main/kotlin/com/revenge/shazamkit/models/ShazamModels.kt` | request/response DTOs, `RecognitionResult`, `RecognitionStatus` |
| `shazamkit/src/test/kotlin/com/revenge/shazamkit/ShazamTest.kt` | mapping/error/no-match/input-validation unit tests |
| `app/src/main/kotlin/com/revenge/music/recognition/AudioResampler.kt` | `DecodedAudio`, Sonic-based resampling contract |
| `app/src/main/kotlin/com/revenge/music/recognition/MusicRecognitionService.kt` | permission check, 10 s PCM capture, pipeline orchestration, status flow, reset |
| `app/src/main/kotlin/com/revenge/music/recognition/RecognitionForegroundService.kt` | microphone FGS, notification state machine, cover-art loading, terminal persistence, result intent |
| `app/src/main/kotlin/com/revenge/music/recognition/RecognitionLaunchActivity.kt` | permission-gated launch routing (service vs app) |
| `app/src/main/kotlin/com/revenge/music/recognition/ShazamSignatureGenerator.kt` | clean-room DejaVu fingerprint computation and encoding |
| `app/src/main/kotlin/com/revenge/music/recognition/VibraSignature.kt` | public fingerprint facade, 16 kHz requirement constant |
| `app/src/main/kotlin/com/revenge/music/quicksettings/MusicRecognizerTileService.kt` | QS tile entry point |
| `app/src/main/res/xml/recognizer_widget_info.xml` | widget provider metadata |
| `app/src/main/kotlin/com/revenge/music/widget/MusicRecognizerWidgetReceiver.kt` | widget state prefs, click routing, size-class rendering, pulse animation |
| `app/src/main/kotlin/com/revenge/music/widget/MusicRecognizerWidgetService.kt` | widget FGS, recognition, cover cache, history insert |
| `app/src/main/kotlin/com/revenge/music/ui/screens/recognition/RecognitionScreen.kt` | permission request and status-driven UI |
| `app/src/main/kotlin/com/revenge/music/ui/screens/recognition/RecognitionHistoryScreen.kt` | history list, search, grouping, delete/clear |
| `core/src/main/kotlin/com/revenge/music/db/entities/RecognitionHistory.kt` | history Room entity |
| `core/src/main/kotlin/com/revenge/music/db/DatabaseDao.kt` | history DAO operations |
| `app/src/main/AndroidManifest.xml` | permissions and component registration |
| `app/src/main/kotlin/com/revenge/music/MainActivity.kt` | `ACTION_RECOGNITION` / `EXTRA_AUTO_START_RECOGNITION` handling and navigation |
| `app/build.gradle.kts` | `:shazamkit` and media3 dependencies |

Supporting files that must exist but carry no new contract: `app/src/main/res/layout/widget_recognizer_{tiny,compact,wide}.xml`, the referenced strings/drawables, `core/.../MusicDatabase.kt` entity registration, `app/.../ui/screens/NavigationBuilder.kt` routes.

---

## 15. Acceptance tests

All tests must be deterministic: no real network, no real microphone, no real clock dependence beyond injected values. Prefer JVM tests in `:shazamkit`, Robolectric/instrumentation fakes for Android services where required.

### 15.1 `:shazamkit` — response mapping

Fixtures are raw JSON strings decoded with `Json { ignoreUnknownKeys = true; isLenient = true }`.

1. **Complete response** (fixture A: key, title, subtitle, URL, ISRC, images, genres, SONG metadata Album/Label/Released, LYRICS text, Apple option, Spotify provider, YouTube video option) → every field mapped exactly; YouTube id extracted from `watch?v=...&t=10`.
2. **`track: null` / absent** (fixture B: `{"tagid":"only-tag","matches":[]}`) → `null` result.
3. **Minimal track** (`{"track":{"title":"T","subtitle":"A"}}`) → `trackId == ""`, title/artist set, all optional fields `null`.
4. **No-match message**: `recognize` against a MockEngine returning 200 with fixture B → failure message contains `"No match"`; `ShazamHttpException.code == 404`.
5. **Track id fallback**: absent or blank key (`"   "`) with `tagid` → `trackId == tagid`.
6. **Metadata isolation**: two SONG sections → only the first is used; metadata entries with null text ignored; blank text yields `null`.
7. **Provider matching is case-insensitive**: `"ProviderName": "APPLE MUSIC"` and caption `"Spotify"` both map; a `type:"video"` option with a malformed/too-short URL yields `youtubeVideoId == null`.
8. **YouTube extraction table** (null, `""`, watch, watch+list, youtu.be, embed, `tooShort`, 12-char, invalid chars) → expected id or null.

### 15.2 `:shazamkit` — error and input taxonomy

9. `httpErrorFor` table: 200→null, 204→null, 404→"No match found", 429→"Too many requests", 500/503→message contains "temporarily unavailable", 400/403→message contains status number.
10. `recognize("", 1000)` → `IllegalArgumentException("Audio signature is empty")`; `recognize("sig", 0)` and negative → `IllegalArgumentException("Sample duration must be positive")`; no HTTP request is issued (MockEngine call counter == 0).
11. Malformed JSON on 200 → `ShazamHttpException` message `"Malformed response from Shazam"`.
12. Retry policy: MockEngine returns 503 twice then 200 → success; total attempts 3. Returns 500 three times → failure after exactly 3 attempts. Returns 400 → exactly 1 attempt. Throws `IOException` then 200 → success (attempt spacing asserted with a virtual clock or a test dispatcher).
13. Rate limit: two sequential successful calls (different signatures) start ≥1000 ms apart; asserted with an injectable clock.
14. Queue/limits: `getActiveRequestsCount()` never exceeds 2 while 5 concurrent calls run; a 51st simultaneous admitted call returns `IllegalStateException("Too many pending recognition requests")` (or pending count is bounded).
15. Cache: same signature twice → MockEngine called once; `clearCache()` → called twice; cache TTL expiry with injected clock → called twice; >100 distinct signatures → oldest evicted.
16. No-match is not cached: two consecutive no-match calls issue two requests.
17. Cancellation: with 2 long-running slots occupied, a third call enters the permit wait; `cancelPendingRequests()` → the third returns/throws `CancellationException`, the two active calls complete. Caller-coroutine cancellation during backoff also propagates `CancellationException`. Counters return to 0 afterwards.
18. `cleanup()` closes the client and clears cache; subsequent use fails predictably (no hang).

### 15.3 Fingerprinting

19. **Structure**: feed a deterministic 16 kHz mono fixture (e.g., 1 s of a 1 kHz sine, or a fixed pseudo-random PCM sequence) → output starts with `data:audio/vnd.shazam.sig;base64,`; Base64 decodes; decoded length ≥ 48 + 8; bytes 0–3 equal `0xCAFE2580` LE; stored CRC32 equals recomputed CRC32 over bytes 8..end; header size field (offset 8) equals `decoded.length - 48`.
20. **Determinism**: same input twice → byte-identical output.
21. **Golden fixture**: one PCM fixture with a recorded golden signature string (generated once from the reference implementation and stored in test resources) → exact string equality. This guards the DejaVu wire format.
22. **Band container validity**: decode the contents; every band tag ∈ `{0x60030040..0x60030043}`, ascending, each declared payload length fits, padding to 4-byte boundary.
23. **Time-delta escape**: craft or select a fixture producing `delta >= 255` (e.g., silence followed by a peak) → the record begins with `0xFF` followed by a u32 absolute pass number; payload length accounting holds.
24. **Invalid inputs**: empty bytes and odd-length bytes → `IllegalArgumentException` with the specified message.
25. **No peaks / pure silence** → still a valid, non-empty data URI with zero band containers.
26. **Duration cap**: a 30 s input produces a signature whose sample offset corresponds to ≤ 12 s + 0.24 s, and peak count ≤ 255.

### 15.4 Resampling and capture

27. **Pass-through**: `resample(DecodedAudio(bytes, 1, 16000, PCM16), 16000)` returns the identical instance (referential equality).
28. **44100→16000**: mono 16-bit input → output sampleRate 16000, channelCount 1, pcmEncoding PCM16, even byte length, non-empty, successful `Result`. Compare against a fixture with an RMS/energy tolerance.
29. **Content equality**: `DecodedAudio` with equal bytes but distinct arrays compare `equals` and share `hashCode`.
30. **Cancellation**: cancel the calling coroutine during a large resample → `CancellationException` propagates (not `Result.failure`).
31. **Capture permission**: `MusicRecognitionService.recognize(context)` with permission denied (Robolectric shadow) → returns `Error("Microphone permission not granted")`, flow reaches `Error`, no `AudioRecord` instantiation (shadow/fake assertion).
32. **Capture pipeline (fake recorder)**: inject/observe a fake 10 s PCM stream → status sequence is exactly `Listening → Processing → Success|NoMatch|Error`; `sampleDurationMs` passed to the provider equals `frames * 1000 / 16000`; input after resample is mono/16 kHz/PCM16/even.
33. **Recorder release**: an exception thrown mid-capture still calls `stop()` and `release()` exactly once.
34. **No temp files**: after `recognize` completes, the app cache directory contains no new audio files.
35. **Cancellation of capture**: cancel during capture → `CancellationException` escapes and the recorder is released; status is not left at `Error` (it may remain `Listening`/be reset by caller — document and assert the chosen contract).

### 15.5 UI/service/widget behaviour

36. **Permission routing**: `RecognitionLaunchActivity` with permission granted starts `RecognitionForegroundService` (shadow `startForegroundService` recorded) and finishes; with permission denied starts `MainActivity` with `ACTION_RECOGNITION` and the auto-start extra, and does not start the service.
37. **Foreground start failure**: `startForeground` throwing `SecurityException` → service stops itself and never calls `MusicRecognitionService.recognize`.
38. **Notification state machine**: fake status flow emitting Listening → Processing → Success → exactly one terminal notification update, title/artist from result, one content intent with the three extras and `ACTION_RECOGNITION`; no second terminal update on duplicate Success.
39. **Terminal persistence**: on NoMatch/Error the service calls `stopForeground(STOP_FOREGROUND_DETACH)` and `stopSelf`; `onDestroy` does not remove the notification.
40. **Cover-art timeout**: fake image loader that never completes → service still finishes within ~1.5 s with the text-only terminal notification (virtual clock).
41. **QS tile**: `onStartListening` sets inactive state and mic icon; `onClick` routes to `RecognitionLaunchActivity` and (API ≥ 34) uses a `PendingIntent`.
42. **Widget state routing**: prefs LISTENING + mic tap → STOP action to widget service; prefs SUCCESS + mic tap → state reset then START; missing permission + mic tap → `MainActivity` with `ACTION_RECOGNITION` and no service start. Terminal state strings/fallbacks render as specified. RESET deletes `recognizer_widget_art.png` and zeroes prefs.
43. **Widget history insert**: fake DAO records exactly one `RecognitionHistory` on success with all result fields and a non-null `recognizedAt`; a DAO exception does not change the displayed success state.
44. **History**: fake DAO flow with duplicate `trackId`, null optionals, and multiple dates → screen renders without crash, groups `Today`/`Yesterday`/`This Week`/month, search filters by title/artist, clear-all and per-item delete call the right DAO methods.

### 15.6 Failure-path matrix (must all be covered)

| Failure | Expected observable |
|---|---|
| No `RECORD_AUDIO` | `Error("Microphone permission not granted")`, no recorder |
| FGS start not allowed | service stops, no recognition |
| Resampler throws | `Error("Failed to resample audio: ...")` |
| Fingerprint throws | `Error("Failed to generate fingerprint: ...")` |
| Shazam 404 / no track | `NoMatch("No matches found. Try again with clearer audio.")` |
| Shazam 429/5xx after retries | `Error` with readable message |
| Network timeout | `Error`, at most 3 attempts |
| Malformed response | `Error("Malformed response from Shazam")` |
| Queue overflow | `Error("Too many pending recognition requests")` |
| User cancels pending | `CancellationException`, counters restored |
| Widget cover download fails | success state without art |
| Widget history insert fails | success state still shown |
| Bitmap decode fails | text-only terminal notification |

---

## 16. Non-goals / explicit exclusions

- No Listen Together integration in this cluster.
- No multi-provider abstraction or provider fallback; `Shazam` is the only matching provider. Any future provider must satisfy Section 4's external contract and error/message taxonomy (in particular the `"No match"` substring used by UI layers).
- No audio persistence, no recording playback, no waveform UI.
- No automatic history deduplication (see 9.4); `liked` is stored but not surfaced by the specified history screen behaviour beyond the DAO method.
- No stateful QS tile (no listening/processing indication).
