# `:lyrics` (Lyrics Orchestration) Clean-Room Specification

Status: M2 specification (implementation-independent)
Module: `:lyrics`
Android namespace: `com.revenge.lyrics`
Kotlin packages: `com.revenge.music.lyrics`, `com.revenge.music.api`
Source root: `lyrics/src`
Cluster: `lyrics-orchestration`

> **Note on algorithm shape.** Algorithm descriptions in this document are illustrative, not
> prescriptive. Implementers must design independent implementations that satisfy the public API,
> wire schemas, observable rules and acceptance tests stated here; internal structure, helper
> decomposition, data structures and control flow are the implementer's choice. Submissions are
> similarity-audited against reference implementations.

---

## 1. Purpose and Scope

`:lyrics` owns four responsibilities:

1. **Provider registry** - a fixed, ordered catalogue of nine lyrics sources, user-order
   serialisation, per-provider enable flags and a pure "which provider wins" selection rule.
2. **Fetch orchestration** - parallel provider execution with per-provider timeout, in-flight
   de-duplication, an LRU result cache, online/offline short-circuiting, and alternate-lyrics
   enumeration. The whole pipeline is best-effort: no provider failure may propagate to a caller
   or interrupt playback.
3. **Lyrics parsing and romanisation** - decoding of the app's LRC dialect (plain, enhanced and
   rich/word-timed), delegation of XML/TTML-shaped input to the bundled `lyrics-core` parser,
   HTML-entity/escape normalisation, sync-index lookup, and deterministic transliteration of
   Japanese, Korean, Chinese, Cyrillic (Russian/Ukrainian/Serbian/Bulgarian/Belarusian/Kyrgyz/
   Macedonian), Hindi and Punjabi.
4. **Translation** - a process-wide translation state machine, in-memory cache, streaming and
   non-streaming model translation through DeepL, Mistral or any OpenAI-compatible / Anthropic
   endpoint, line-count normalisation, and database persistence.

Out of scope (owned by other modules, reproduced only as cross-module contracts in section 12):

- Provider network clients (`:lrclib`, `:kugou`, `:simpmusic`, `:youlyplus`, `:unison`,
  `:betterlyrics`, `:paxsenixlyrics`, `:innertube`).
- TTML XML parsing itself (`:betterlyrics` `TTMLParser`).
- Persistence schema (`LyricsEntity`, `MusicDatabase`).
- Compose presentation, the `Song.lyricsOffset` / `Song.romanizeLyrics` fields, and translation
  settings UI.
- AI playlist generation; it reuses only `OpenRouterService.isUrlSafe`.

The module is Android-dependent (context, resources, in-memory caching) but all parsing and
romanisation logic must be pure Kotlin and unit-testable on the JVM.

---

## 2. Build and Dependency Contract

`lyrics/build.gradle.kts` must apply:

- `com.android.library`
- `com.google.devtools.ksp`
- `com.google.dagger.hilt.android`
- `org.jetbrains.kotlin.plugin.serialization` (alias `libs.plugins.kotlin.serialization`)

Android block:

- `namespace = "com.revenge.lyrics"`
- `compileSdk = 37`, `minSdk = 26`
- Java/Kotlin toolchain **21**
- core-library desugaring enabled
- no build flavors or custom source sets; tests are plain `src/test` JVM tests

Dependencies (baseline, keep source-compatible):

```
implementation(project(":core"))
implementation(project(":unison"))
implementation(project(":lrclib"))
implementation(project(":kugou"))
implementation(project(":simpmusic"))
implementation(project(":youlyplus"))
implementation(project(":betterlyrics"))
implementation(project(":paxsenixlyrics"))
implementation(project(":innertube"))

api(libs.lyrics.core)                 // com.mocharealm.accompanist:lyrics-core:0.5.0
implementation(libs.kuromoji.ipadic)  // 0.9.0
implementation(libs.tinypinyin)       // 2.0.3
implementation(libs.core.ktx)
implementation(libs.hilt); ksp(libs.hilt.compiler)

implementation(libs.ktor.client.core)
implementation(libs.ktor.client.okhttp)
implementation(libs.ktor.client.content.negotiation)
implementation(libs.ktor.serialization.json)
implementation(libs.kotlinx.serialization.json)

coreLibraryDesugaring(libs.desugaring)
testImplementation(libs.junit)
```

Additional test-only dependencies required by section 14 (not public API):

```
testImplementation(libs.kotlinx.coroutines.test)
testImplementation(libs.robolectric)
testImplementation(libs.ktor.client.mock)   // add catalog alias; ktor 3.6.1
```

`libs.lyrics.core` is deliberately an `api` dependency because the public `LyricsUtils` signatures
reference only JDK types, but the parsing contract depends on `lyrics-core` 0.5.0 semantics.

### Wave 0 note (normative)

Wave 0 replaced Gson/`org.json` with **kotlinx.serialization** in this module. Therefore:

- No `org.json`, Gson or Moshi imports may appear under `lyrics/src/main`.
- All JSON produced or consumed here uses `kotlinx.serialization.json` (`Json`, `JsonObject`,
  `JsonArray`, `JsonPrimitive`, `buildJsonObject`, `buildJsonArray`) with
  `Json { isLenient = true; ignoreUnknownKeys = true }`.
- The module declares **no** `@Serializable` DTO classes. Wire formats are handled as JSON trees;
  the DTO tables in section 11.8 are the normative schemas those trees must satisfy.
- `@Serializable` DTOs owned by provider modules stay in their modules and are never re-declared
  here.

---

## 3. Public API Summary

| FQCN | Kind | Members consumed outside `:lyrics` |
|---|---|---|
| `com.revenge.music.lyrics.LyricsProvider` | interface | `name`, `isEnabled(Context)`, `getLyrics(...)`, `getAllLyrics(...)` |
| `com.revenge.music.lyrics.LyricsProviderRegistry` | object | all members listed in 3.1 |
| `com.revenge.music.lyrics.ProviderOutcome` | enum | `SYNCED`, `UNSYNCED`, `FAILED` |
| `com.revenge.music.lyrics.ProviderChoice` | sealed interface | `Pending`, `Selected(name)`, `None` |
| `com.revenge.music.lyrics.LyricsHelper` | class, `@Inject` | `getLyrics(MediaMetadata)`, `getAllLyrics(...)`, `cancelCurrentLyricsJob()` |
| `com.revenge.music.lyrics.LyricsResult` | data class | `providerName`, `lyrics` |
| `com.revenge.music.lyrics.LyricsEntry` | data class | all fields, `compareTo`, `HEAD_LYRICS_ENTRY` |
| `com.revenge.music.lyrics.WordTimestamp` | data class | `text`, `startTime`, `endTime` |
| `com.revenge.music.lyrics.LyricsUtils` | object | parsers, predicates, romanisers (3.2) |
| `com.revenge.music.lyrics.LyricsTranslationHelper` | object | state flows, triggers, translate/load/persist (3.3) |
| `com.revenge.music.api.DeepLService` | object | `translate`, `testConnection` |
| `com.revenge.music.api.MistralService` | object | `translate` |
| `com.revenge.music.api.OpenRouterService` | object | `isUrlSafe`, `translate`, `testConnection` |
| `com.revenge.music.api.OpenRouterStreamingService` | object | `streamTranslation`, `StreamChunk` |

Not part of the public API:

- `com.revenge.music.api.aiHttpClient`, `aiStreamingHttpClient`
- `com.revenge.music.api.AiPrompts`
- `com.revenge.music.api.AiTranslationParsing`
- any further helpers the implementation adds

The registry resolves to concrete provider singletons, all `object : LyricsProvider`:

`YouLyPlusLyricsProvider`, `PaxSenixLyricsProvider`, `BetterLyricsProvider`,
`UnisonLyricsProvider`, `SimpMusicLyricsProvider`, `LrcLibLyricsProvider`,
`KuGouLyricsProvider`, `YouTubeSubtitleLyricsProvider`, `YouTubeLyricsProvider`.

### 3.1 `LyricsProviderRegistry` signatures

```kotlin
val providerNames: List<String>
fun getProviderByName(name: String): LyricsProvider?
fun getDefaultProviderOrder(): List<String>
fun deserializeProviderOrder(orderString: String): List<String>
fun serializeProviderOrder(order: List<String>): String
fun orderedProviderNames(orderString: String): List<String>
fun getOrderedProviders(orderString: String): List<LyricsProvider>
fun getProviderNameForEnum(enum: PreferredLyricsProvider): String
fun getDisplayName(name: String): String
fun chooseProvider(order: List<String>, outcomes: Map<String, ProviderOutcome>): ProviderChoice
```

### 3.2 `LyricsUtils` signatures

```kotlin
val LINE_REGEX: Regex
val TIME_REGEX: Regex

fun hasSyncedLyrics(lyrics: String): Boolean
fun parseLyrics(lyrics: String): List<LyricsEntry>
fun findCurrentLineIndex(lines: List<LyricsEntry>, position: Long): Int

fun katakanaToRomaji(katakana: String?): String
fun katakanaToRomaji(katakana: String?, nextKatakana: String? = null): String

suspend fun romanizeJapanese(text: String): String
suspend fun romanizeKorean(text: String): String
suspend fun romanizeChinese(text: String): String
suspend fun romanizeCyrillic(text: String): String?
suspend fun romanizeHindi(text: String): String
suspend fun romanizePunjabi(text: String): String

fun isRussian(text: String): Boolean
fun isUkrainian(text: String): Boolean
fun isSerbian(text: String): Boolean
fun isBulgarian(text: String): Boolean
fun isBelarusian(text: String): Boolean
fun isKyrgyz(text: String): Boolean
fun isMacedonian(text: String): Boolean
fun isJapanese(text: String): Boolean
fun isKorean(text: String): Boolean
fun isChinese(text: String): Boolean
fun isHindi(text: String): Boolean
fun isPunjabi(text: String): Boolean
```

### 3.3 `LyricsTranslationHelper` signatures

```kotlin
val status: StateFlow<TranslationStatus>
val hasActiveTranslations: StateFlow<Boolean>
val manualTrigger: SharedFlow<Unit>
val clearTranslationsTrigger: SharedFlow<Unit>
val translationSaved: SharedFlow<Unit>

fun getCachedTranslations(lyrics: List<LyricsEntry>, mode: String, language: String): List<String>?
fun applyCachedTranslations(lyrics: List<LyricsEntry>, mode: String, language: String): Boolean
fun triggerManualTranslation()
fun triggerClearTranslations()
fun hasTranslations(lyricsEntity: LyricsEntity?): Boolean
fun clearTranslations(lyricsEntity: LyricsEntity): LyricsEntity
fun resetStatus()
fun clearCache()
fun setCompositionActive(active: Boolean)
fun cancelTranslation()

fun loadTranslationsFromDatabase(
  lyrics: List<LyricsEntry>, lyricsEntity: LyricsEntity?, targetLanguage: String, mode: String,
)

fun translateLyrics(
  lyrics: List<LyricsEntry>,
  targetLanguage: String,
  apiKey: String,
  baseUrl: String,
  model: String,
  mode: String,
  scope: CoroutineScope,
  context: Context,
  provider: String = "OpenRouter",
  deeplApiKey: String = "",
  deeplFormality: String = "default",
  useStreaming: Boolean = true,
  songId: String = "",
  database: MusicDatabase? = null,
)

sealed class TranslationStatus {
  data object Idle : TranslationStatus()
  data class Translating(val logs: List<String> = emptyList()) : TranslationStatus()
  data object Success : TranslationStatus()
  data class Error(val message: String) : TranslationStatus()
}
```

---

## 4. Data Model

### 4.1 `WordTimestamp`

```kotlin
data class WordTimestamp(val text: String, val startTime: Double, val endTime: Double)
```

- `text` - word/syllable display text, never blank when produced by a parser.
- `startTime`, `endTime` - **seconds** (not milliseconds) as `Double`. Parsers convert source
  milliseconds or TTML clock values before constructing this type.
- Data-class value semantics.

### 4.2 `LyricsEntry`

```kotlin
data class LyricsEntry(
  val time: Long,
  val text: String,
  val words: List<WordTimestamp>? = null,
  val romanizedTextFlow: MutableStateFlow<String?> = MutableStateFlow(null),
  val translatedTextFlow: MutableStateFlow<String?> = MutableStateFlow(null),
  val agent: String? = null,
  val isBackground: Boolean = false,
) : Comparable<LyricsEntry>
```

Field contract:

- `time` - line start in **milliseconds**, `0` for the synthetic head entry.
- `text` - display line text after metadata/tag removal; may be empty only if a source line had
  text that became empty after removing `{agent:...}`/`{bg}` (such entries are normally skipped by
  parsers except the multi-timestamp path, which inherits the original non-empty text).
- `words` - word-level timing, `null` when the line is line-synced only; never an empty list from
  any parser (parsers return `null` instead).
- `romanizedTextFlow` / `translatedTextFlow` - mutable per-entry streams. They are **not value
  fields** for identity although the data class includes them: two entries that differ only in
  flow contents are unequal, and `copy()` shares the same flow instances. Callers must never use
  `LyricsEntry` as a map key or rely on `equals` for content comparison.
- `agent` - vocalist/agent id from `{agent:<id>}` or TTML `ttm:agent`, else `null`.
- `isBackground` - true for `{bg}` lines and TTML `x-bg` accompaniment lines.
- `HEAD_LYRICS_ENTRY = LyricsEntry(0L, "")` is a shared singleton including shared flows.

`compareTo` contract: `(time - other.time).toInt()`. For normal lyric times the sign orders by
time; comparison is not a strict distance. Sorting must therefore be stable enough for equal
timestamps to preserve parse order (`sorted()` is stable in Kotlin). Do not "fix" this to
`Long.compare`. The specified expression is contractual; `Long.compare` produces the same order
for all realistic inputs but is not the specified rule.

### 4.3 `LyricsResult`

```kotlin
data class LyricsResult(val providerName: String, val lyrics: String)
```

`lyrics` is non-null and is never the `LYRICS_NOT_FOUND` sentinel when produced by orchestration.

### 4.4 `ProviderOutcome` / `ProviderChoice`

```kotlin
enum class ProviderOutcome { SYNCED, UNSYNCED, FAILED }

sealed interface ProviderChoice {
  data object Pending : ProviderChoice
  data class Selected(val name: String) : ProviderChoice
  data object None : ProviderChoice
}
```

- Enum order is part of the source contract.
- `Pending` means at least one higher-priority provider has no recorded outcome yet.
- `None` means every provider in the supplied order has `FAILED` (or the order is empty).
- `Selected` names the first provider in priority order whose outcome is `SYNCED` or `UNSYNCED`.

### 4.5 `TranslationStatus`

State transitions and nesting are specified in section 10. The sealed hierarchy above is consumed
by the UI; `Translating.logs` is the accumulated log text and defaults to an empty list.

---

## 5. Provider Registry

### 5.1 Catalogue

Provider registry keys, in registration order:

`YouLyPlus`, `Paxsenix`, `BetterLyrics`, `Unison`, `SimpMusic`, `LrcLib`, `Kugou`,
`YouTubeSubtitle`, `YouTubeMusic`.

Default priority order (different from registration order):

1. `YouLyPlus`
2. `Paxsenix`
3. `Unison`
4. `BetterLyrics`
5. `SimpMusic`
6. `LrcLib`
7. `Kugou`
8. `YouTubeSubtitle`
9. `YouTubeMusic`

`providerNames` returns registration order (not priority order).

### 5.2 Registry key vs provider `name`

Each provider's `name` is the **display and outcome key**, not always the registry key:

| registry key | `LyricsProvider.name` | display name |
|---|---|---|
| `YouLyPlus` | `YouLyPlus` | `YouLyPlus` |
| `Paxsenix` | `Paxsenix` | `PaxSenix` |
| `BetterLyrics` | `BetterLyrics` | `Better Lyrics` |
| `Unison` | `Unison` | `Unison` |
| `SimpMusic` | `SimpMusic` | `SimpMusic` |
| `LrcLib` | `LrcLib` | `LrcLib` |
| `Kugou` | `Kugou` | `KuGou` |
| `YouTubeSubtitle` | `YouTube Subtitle` | `YouTube Subtitle` |
| `YouTubeMusic` | `YouTube Music` | `YouTube Music` |

Consequences that must be preserved:

- `getProviderByName` accepts only registry keys; `getProviderByName("YouTube Music")` is `null`.
- Orchestration keys its outcome map by `LyricsProvider.name`, and builds its priority list from
  the same `name` values, so the two YouTube providers participate correctly.
- A persisted provider name (`LyricsEntity.provider`) uses `LyricsProvider.name`
  (`"YouTube Music"`), not the registry key.

### 5.3 Order serialisation

- `getDefaultProviderOrder()` returns the nine-element default priority order.
- `serializeProviderOrder(order)` filters to known registry keys, preserves their order and joins
  with `,`. Unknown names and duplicates are preserved as duplicates after filtering (no
  `distinct`). Empty input returns `""`.
- `deserializeProviderOrder("")` or a blank string returns the **default order**.
  `deserializeProviderOrder(" , ")` returns an empty list (non-blank input whose elements all
  trim to unknown/empty).
- `deserializeProviderOrder(s)` splits on `,`, trims each element, drops names not in the
  registry, and preserves order and duplicates.
- `orderedProviderNames(s)` = `deserializeProviderOrder(s).distinct()` followed by every default
  name not already present. The result always contains all nine registry keys exactly once when
  `s` is blank; with a partial saved order it appends the missing defaults.
- `getOrderedProviders(s)` = `deserializeProviderOrder(s).mapNotNull(::getProviderByName)`.
  It does **not** append defaults and does **not** de-duplicate. Callers wanting the effective
  list use `orderedProviderNames`.

### 5.4 Enum and display mapping

- `getProviderNameForEnum` maps `PreferredLyricsProvider` values to registry keys:
  `LRCLIB->LrcLib`, `KUGOU->Kugou`, `BETTER_LYRICS->BetterLyrics`, `SIMPMUSIC->SimpMusic`,
  `YOULYPLUS->YouLyPlus`, `PAXSENIX->Paxsenix`, `UNISON->Unison`.
  It throws `NoSuchElementException` for any enum value not in the map; the current enum has
  exactly these seven values.
- `getDisplayName(name)` returns the table value or the input unchanged for unknown names.

### 5.5 `chooseProvider` rule

Iterate `order` in order:

- outcome missing -> `Pending`.
- `FAILED` -> continue.
- `SYNCED` or `UNSYNCED` -> `Selected(name)` immediately.
- exhausted -> `None`.

Priority dominates synchronisation: a higher-priority `UNSYNCED` result beats a lower-priority
`SYNCED` result if both are already complete. There is no preference for synced within one
priority. The function is pure and thread-safe.

### 5.6 Enable flags (DataStore keys)

`isEnabled` reads the app preference store synchronously, defaulting to `true` when the key is
absent or the read fails:

| provider | preference key | default |
|---|---|---|
| `YouLyPlus` | `EnableYouLyPlusKey` (`enableYouLyPlus`) | true |
| `Paxsenix` | `EnablePaxsenixKey` (`enablePaxsenix`) | true |
| `BetterLyrics` | `EnableBetterLyricsKey` (`enableBetterLyrics`) | true |
| `Unison` | `UnisonLyricsEnabledKey` (`unison_lyrics_enabled`) | true |
| `SimpMusic` | `EnableSimpMusicKey` (`enableSimpMusic`) | true |
| `LrcLib` | `EnableLrcLibKey` (`enableLrclib`) | true |
| `Kugou` | `EnableKugouKey` (`enableKugou`) | true |
| `YouTubeSubtitle` | none | always true |
| `YouTubeMusic` | none | always true |

`Paxsenix.isEnabled` must call `Paxsenix.init(context)` when enabled (idempotent). The settings UI
exposes six explicit toggles (YouLyPlus, Paxsenix, BetterLyrics, SimpMusic, LrcLib, Kugou);
Unison has a preference key but no UI toggle, and both YouTube providers are always enabled. `isEnabled` may block briefly on DataStore; it is called from provider-fetch
coroutines and from download-time lyrics fetching, never from the main thread.

---

## 6. Provider Contract and Implementations

```kotlin
interface LyricsProvider {
  val name: String
  fun isEnabled(context: Context): Boolean

  suspend fun getLyrics(
    id: String, title: String, artist: String, duration: Int, album: String? = null,
  ): Result<String>

  suspend fun getAllLyrics(
    id: String, title: String, artist: String, duration: Int, album: String? = null,
    callback: (String) -> Unit,
  ) {
    getLyrics(id, title, artist, duration, album).onSuccess(callback)
  }
}
```

Contract:

- `getLyrics` returns `Result.success(text)` only for non-blank, usable lyrics. Empty documents,
  service "not found" responses and transport/HTTP/parse failures are `Result.failure`.
  `CancellationException` is always rethrown, never wrapped.
- No implementation may return the sentinel string `LYRICS_NOT_FOUND` as success; orchestration
  treats such a value as failure defensively.
- `getAllLyrics` invokes `callback` zero or more times, on the calling coroutine's context, once
  per distinct usable lyrics document. It never invokes the callback for failures. The default
  implementation is exactly "one success -> one callback". Enumerating implementations must be
  cancellation-aware between emissions, so a cancelled enumeration stops calling back.
- Results are raw LRC-dialect text. Providers that receive TTML must convert it to LRC before
  returning (only `UnisonLyricsProvider` currently does).
- `duration` is whole seconds; `-1` (or any non-positive value for some clients) means unknown and
  must be passed through unchanged.

### 6.1 Concrete delegation (all `object`s)

| provider | `getLyrics` | `getAllLyrics` | notes |
|---|---|---|---|
| `YouLyPlus` | `YouLyPlus.getLyrics(title, artist, duration, album, id)` | `YouLyPlus.getAllLyrics(title, artist, duration, album, id, null, callback)` | `isrc` is always `null`; client races mirrors |
| `Paxsenix` | `Paxsenix.getLyrics(title, artist, duration, album)` | `Paxsenix.getAllLyrics(title, artist, duration, album, callback)` | init on enable; catches non-cancellation exceptions and returns failure |
| `BetterLyrics` | `BetterLyrics.getLyrics(title, artist, duration, album)` | `BetterLyrics.getAllLyrics(...)` | client returns LRC already |
| `Unison` | `Unison.getLyrics(videoId=id, title, artist, album, durationSeconds=duration)` mapped through TTML->LRC | same plus per-result TTML->LRC mapping | `content.trimStart().startsWith("<tt", ignoreCase=true)` triggers conversion |
| `SimpMusic` | `SimpMusicLyrics.getLyrics(id, duration)` | `SimpMusicLyrics.getAllLyrics(id, duration, callback)` | title/artist/album ignored |
| `LrcLib` | `LrcLib.getLyrics(title, artist, duration, album)` | `LrcLib.getAllLyrics(title, artist, duration, album, callback)` | no id |
| `Kugou` | `KuGou.getLyrics(title, artist, duration, album)` | `KuGou.getAllPossibleLyricsOptions(title, artist, duration, album, callback)` | `KuGou.useTraditionalChinese` is app-controlled global |
| `YouTubeSubtitle` | `YouTube.transcript(id)` | inherited default (single callback) | always enabled |
| `YouTubeMusic` | `YouTube.next(WatchEndpoint(videoId=id))`, then `YouTube.lyrics(next.lyricsEndpoint)`, throwing `IllegalStateException("Lyrics endpoint not found")` / `"Lyrics unavailable"` on absence | inherited default | wrapped in `runCatching`; always enabled |

`YouTubeLyricsProvider` must throw (inside the `runCatching`) when either the watch-next result
fails, has no `lyricsEndpoint`, or the lyrics call returns `null`.

### 6.2 Provider-side alternate caps (cross-module, for acceptance expectations)

- LrcLib: at most 5 variants; at most one plain/unsynchronised variant and only near the duration.
- SimpMusic: at most 5 distinct variants.
- Unison: at most 5 search results, then one metadata-lookup fallback.
- Paxsenix: at most 5 candidates for `getLyrics`.
- YouLyPlus: one callback per mirror that returns a distinct non-blank document.
- KuGou: first lyric candidate per matching song hash (page size 8, duration tolerance 8 s),
  then all keyword-search candidates.

These caps belong to the provider modules but are observable through `LyricsHelper.getAllLyrics`.

---

## 7. Fetch Orchestration - `LyricsHelper`

```kotlin
class LyricsHelper
@Inject
constructor(
  @ApplicationContext private val context: Context,
  private val networkConnectivity: NetworkConnectivityObserver,
) {
  suspend fun getLyrics(mediaMetadata: MediaMetadata): LyricsWithProvider
  suspend fun getAllLyrics(
    mediaId: String, songTitle: String, songArtists: String, duration: Int,
    album: String? = null, callback: (LyricsResult) -> Unit,
  )
  fun cancelCurrentLyricsJob()
}
```

`LyricsWithProvider` is `com.revenge.music.playback.LyricsWithProvider(val lyrics: String?,
val providerName: String)` from `:core`. `LyricsHelper` satisfies the
`com.revenge.music.playback.ILyricsHelper` contract (12.1); the app-injected type is `LyricsHelper`.

Hilt scope: the class is unscoped, so each injection site receives its own instance and caching and
de-duplication are per instance. Making the class process-wide (for example with `@Singleton`) is
permitted; the observable rules below hold either way, and the choice must be documented because
caching then becomes process-wide.

### 7.1 `getLyrics(mediaMetadata)` - best single result

Observable contract (behaviour a caller can rely on):

1. A new single-result request first cancels any in-progress alternate enumeration (7.4).
2. If the cache (7.5) holds the media id, its stored first result is returned immediately, without
   any provider call and without a connectivity check. Cache hits therefore work offline.
3. If offline, return `LyricsWithProvider(LYRICS_NOT_FOUND, "Unknown")`; no provider is called.
4. Concurrent requests for the same media id on the same helper instance share one provider fetch:
   the providers are called once and every waiting caller observes the same outcome. This
   de-duplication is per helper instance.
5. The shared fetch is independent of the caller's coroutine. Cancelling a caller aborts only that
   caller's wait (the caller sees `CancellationException`); the fetch continues and still populates
   the cache for later callers.
6. On completion the selected result is cached under the media id (successes only; see 7.5).

Provider selection for this path:

- Enabled providers are those named by the effective provider order (persisted order; blank order
  means the default order of 5.3), restricted to providers whose enable flag is true (5.6). An empty
  set yields `(LYRICS_NOT_FOUND, "Unknown")`.
- All enabled providers are queried concurrently; there is no sequential fallback walk. Each has
  the per-provider budget of 7.2.
- Per-provider outcome: usable, non-blank text other than the sentinel is `SYNCED` iff
  `LyricsUtils.hasSyncedLyrics(text)`, else `UNSYNCED`; a null/blank/sentinel result, a thrown
  failure or a timeout is `FAILED`.
- The winner is the first provider in priority order whose outcome is `SYNCED` or `UNSYNCED`
  (`chooseProvider`, 5.5), evaluated as outcomes arrive. A higher-priority provider that has not yet
  produced an outcome keeps the choice `Pending` and blocks selection, even when a lower-priority
  provider has already returned lyrics. If every provider in the order is `FAILED`, the result is
  `(LYRICS_NOT_FOUND, "Unknown")`.

Consequences:

- Priority, not synchronisation quality, decides: a higher-priority `UNSYNCED` result beats a
  lower-priority `SYNCED` result.
- A slow top-priority provider can delay the result by up to its budget even when a lower-priority
  provider has already answered; that delay is intended.

### 7.2 Per-provider timeout and failure isolation

- Every provider call carries a 5 s budget. A provider that has not produced a usable result within
  its budget is treated as `FAILED`; no exception reaches the caller.
- Provider arguments are derived from `MediaMetadata`: `id`, `title`, `duration`, `album`
  (`album?.title`) and `artist` = `artists.joinToString { it.name }` with the separator exactly
  `", "`.
- A `Result.failure`, a thrown exception or a timeout all mean `FAILED`. Non-cancellation failures
  are reported through `com.revenge.music.utils.reportException(Throwable)` (logs and forwards to
  the global reporter) and are not surfaced.
- `CancellationException` is always rethrown, never converted to `FAILED`.
- Reporting a failure must never itself throw into the fetch pipeline.

### 7.3 Online check

Connectivity is evaluated before any provider work. If the connectivity source throws or cannot
report a state, the helper treats the device as online. Offline skips all provider work for both
fetch paths and yields the offline results of 7.6.

### 7.4 `getAllLyrics(...)` - alternate enumeration ("pagination/continuation")

There is no network pagination token anywhere in this cluster. The continuation mechanism is the
provider `getAllLyrics` callback stream; orchestration fans it out across providers.

Observable contract:

1. Entry cancels any in-progress alternate enumeration, so two rapid searches cannot both populate
   results.
2. Cache key = `"$songArtists-$songTitle"` with **every literal space character removed**
   (`replace(" ", "")`; not other whitespace). On a hit, stored results are replayed to the caller
   synchronously in stored order, with zero provider calls, and the call returns.
3. If offline, return without invoking the callback; the cache is untouched.
4. Otherwise use the same enabled-provider set as 7.1. Each provider's emitted documents become
   `LyricsResult(provider.name, text)` and are delivered to the callback immediately, in arrival
   order across providers and pages. There is no global sort and no cross-provider de-duplication
   (a provider may de-duplicate its own output).
5. A provider that throws (other than cancellation) does not affect other providers; the failure is
   reported and swallowed. Cancellation is rethrown and stops further callbacks at the next
   provider suspension point.
6. After an online enumeration finishes (including one that emitted nothing), the collected list is
   cached under the key unconditionally; an empty list is cached and replayed as empty.
7. The enumeration is independent of the caller's coroutine: caller cancellation aborts only the
   caller's wait, so enumeration may continue and callbacks may still be delivered into a cancelled
   collector. `cancelCurrentLyricsJob()` explicitly stops enumeration at the next provider
   suspension point.
8. An empty provider set emits nothing and leaves the cache untouched.

### 7.5 Cache contract

- One result cache with capacity **20 entries**, evicting least-recently-used entries (a 21st
  distinct key evicts the oldest).
- Keys share one namespace: song ids (single-best) and `artist-title` (alternates). A song id equal
  to an artist-title string is a theoretical collision; key construction is preserved.
- `getLyrics` caches only successful selections; failures and `LYRICS_NOT_FOUND` are never cached.
- `getAllLyrics` caches the collected list unconditionally after an online enumeration, even when
  it is empty (so a subsequent replay is empty rather than a re-fetch).
- The cache lives as long as the helper instance; it is not invalidated when provider settings,
  order or enable flags change. A cached result is therefore served even after its provider is
  disabled.
- Cache access and in-flight de-duplication (7.1) must be safe under concurrent callers.

### 7.6 Error taxonomy (orchestration)

| condition | observed result |
|---|---|
| offline | `LyricsWithProvider("LYRICS_NOT_FOUND", "Unknown")`; `getAllLyrics` emits nothing |
| no enabled providers | same as offline for `getLyrics` |
| provider returns blank/empty/`LYRICS_NOT_FOUND` | `FAILED`, continue |
| provider throws | `FAILED`, exception reported |
| provider exceeds 5 s | `FAILED`, no exception surfaced |
| caller cancels wait | `CancellationException` propagates to caller; shared fetch continues |
| all providers fail | `("LYRICS_NOT_FOUND", "Unknown")` |

The sentinel value is `com.revenge.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND`
= `"LYRICS_NOT_FOUND"`. The provider name fallback is exactly `"Unknown"`.

### 7.7 Testability requirements (internal, not public API)

To make section 14 deterministic without Android provider clients, the helper must allow tests to
inject, without changing the public constructor or any public signature:

- the enabled-provider set (default: persisted order plus enable flags),
- the connectivity answer (default: the connectivity observer),
- a cache instance or namespace,
- the coroutine context used for the shared fetch,
- the per-provider budget (default `5_000` ms).

These seams are internal; implementers choose their form.

---

## 8. Lyrics Parsing - `LyricsUtils`

`parseLyrics` must be total: for any `String` it returns a list (possibly empty) and never throws.

### 8.1 Pre-processing (in this order)

1. `trim()` the whole input.
2. Remove **one** leading `"` if present, then **one** trailing `"` if present (independent
   prefix/suffix removal; not paired validation).
3. Textual replacements, in order: `\\` (two backslashes) -> `\`, `\n` -> LF, `\r` -> CR,
   `\t` -> TAB. These are literal escape sequences, not a general unescaper.
4. Decode HTML entities:
   - numeric hex `&#xHEX;` and decimal `&#DEC;` -> Unicode code point when the parsed value is in
     `0..0x10FFFF`; otherwise the match is left unchanged.
   - named entities in this order: `&apos;`, `&quot;`, `&lt;`, `&gt;`, `&nbsp;` (to a normal
     space), `&amp;`. Because `&amp;` is last, `&amp;lt;` decodes to `&lt;`, not `<`.
5. `trim()` again.

### 8.2 Format dispatch

- `looksStructured` = trimmed text starts with `<`, or contains `<tt` case-insensitively, or
  contains `<p begin` case-insensitively.
- `hasEnhancedTags` = contains both `<` and `>` and at least one match of the inline word tag
  pattern `<m:ss.xx(x)>text`.
- If `looksStructured || hasEnhancedTags || autoParser.canParse(decoded)`, try
  `parseWithAccompanist(decoded, looksStructured)`. If it returns a non-null list, that is the
  result. `autoParser` is an instance of
  `com.mocharealm.accompanist.lyrics.core.parser.AutoParser`.
- Otherwise, split `decoded` into lines, keep non-blank lines whose trimmed form does **not** start
  with `[offset:`, and classify the document as rich-sync if any line fully matches the rich line
  pattern and contains at least one inline word tag. Rich -> rich parser; otherwise standard LRC
  parser.
- Both LRC parsers return entries sorted by `time` (stable).

### 8.3 Standard LRC dialect

Accepted line shape: one or more `[MM:SS.xx]` or `[MM:SS.xxx]` timestamps (exactly two minute
digits, two second digits, 2 or 3 fraction digits), an optional space after each timestamp, then
non-empty text.

- Every timestamp on a line produces one `LyricsEntry` with the same text.
- Time = `minutes*60000 + seconds*1000 + fraction`, where a 2-digit fraction is multiplied by 10
  (centiseconds -> ms) and a 3-digit fraction is milliseconds.
- Metadata lines such as `[ar:...]`, `[ti:...]`, `[al:...]`, `[by:...]`, `[offset:...]` do not
  match the line pattern and are dropped.
- Text without any timestamp is dropped. Consequence: a plain unsynchronised lyric document
  yields an **empty list**, not line entries. UI layers handle plain text by splitting lines
  themselves.
- `{agent:<id>}`: the first occurrence anywhere in the text is removed; the captured id becomes
  `agent`. `{bg}`: matched only at the very start (`^`), removed, sets `isBackground = true`.
- Optional word-timing companion line: a line that starts with `<` and ends with `>` is treated
  as the word track for the **immediately preceding** timed line and is not itself a lyric line.
  Its inner content is split on `|`, each part split on `:` and accepted only when it has exactly
  three parts -> `WordTimestamp(text, start.toDoubleOrNull() ?: 0.0, end...)` (seconds). The same
  word list is attached to every entry produced by a multi-timestamp line. Malformed parts are
  skipped.
- Multiple timestamps with the same text are intentionally preserved (chorus repeats).
- Zero timestamps are valid entries but do not by themselves make the document "synced"
  (`hasSyncedLyrics` requires any `time > 0`).

### 8.4 Rich-sync (word-timed LRC) dialect

Line shape: `[M:SS.xx]` or `[M:SS.xxx]` (one or two minute digits) followed by content containing
zero or more inline word tags `<M:SS.xx>word text` up to the next `<`.

- Line time as in 8.3.
- `{agent:<id>}` removed anywhere (first occurrence); `{bg}` removed only at line start; both set
  the corresponding entry fields.
- Word text = the text after a word tag up to the next `<`, trimmed; blank words skipped.
- Word start = its tag time; word end = next word's start. The last word's end = the **next raw
  line's** start if that line matches the rich line pattern, else `start + 0.5` seconds.
- A line's `text` = content with all word tags removed and trimmed; blank text -> no entry.
- If a rich line has no word tags, `words` is `null` (line-synced entry).
- `words` is `null` rather than an empty list when no words remain.

### 8.5 Accompanist/XML path (`parseWithAccompanist`)

- Calls `autoParser.parse(decoded)` inside `try/catch (Exception)`. Any exception -> `null`
  (caller falls back to LRC parsing).
- Empty parsed line list -> `null`.
- `KaraokeLine` -> `LyricsEntry(time = line.start.toLong(), text = syllables joined and trimmed)`;
  `words` = each syllable with `content.trim()` (blank ones dropped) and start/end **divided by
  1000** to seconds; `isBackground = line is KaraokeLine.AccompanimentKaraokeLine`. If a line's
  non-blank `translation` exists, set `translatedTextFlow.value`.
- `SyncedLine` -> line-synced entry; same `time` conversion and translation handling.
- Other line types are ignored.
- Blank text -> no entry.
- Acceptance gate: return the entries (sorted) only if `isStructured || hasWordTimings ||
  entries.size >= rawLines.size`, where `rawLines` = decoded non-blank lines excluding
  `[offset:`, `[ar:`, `[ti:`, `[al:`, `[by:`; `hasWordTimings` = any entry with non-empty words.
  Otherwise return `null` so the LRC fallback runs. This gate prevents a partial XML parse from
  discarding most of the document.
- `line.start`/`line.end` are milliseconds in `lyrics-core` 0.5.0; syllable times are also
  milliseconds. `LyricsEntry.time` follows.

Because `looksStructured` also accepts any text starting with `<`, the XML path can be entered for
arbitrary markup; the gate and the total-function rule protect the fallback.

### 8.6 `hasSyncedLyrics`

`parseLyrics(lyrics).any { it.time > 0L }`. It parses the entire document on every call (there is
no memoisation); callers should not call it in a tight loop. A parse failure/empty result -> false.

### 8.7 Sync lookup and offset

```kotlin
fun findCurrentLineIndex(lines: List<LyricsEntry>, position: Long): Int
```

- Iterate indices from 0; if `lines[index].time >= position + 300`, return `index - 1`.
- If no line satisfies it, return `lines.lastIndex`.
- Empty list -> `-1`.
- Before the first line (when the first line's time is at least `position + 300`) -> `-1`.
- Equal timestamps: the first line with that time is the boundary; the returned index is the last
  line strictly before the 300 ms lookahead window, so a multi-entry timestamp group resolves to
  the entry before the group until the group's time passes.
- 300 ms is a fixed lookahead constant.

The LRC `[offset:]` metadata tag is **ignored/dropped**; it is never applied to entry times. The
app applies the user's `Song.lyricsOffset` (milliseconds, default 0) externally:

- display: `findCurrentLineIndex(lines, playerPosition + lyricsOffset)`;
- tap-to-seek: `seekTo((entry.time - lyricsOffset).coerceAtLeast(0))`.

The implementation must not additionally apply `[offset:]`, or offsets would double.

### 8.8 Hardening requirements (normative)

`lyrics-core` `AutoParser` uses a hand-written, iterative XML tokenizer (`SimpleXmlParser`), not
`javax.xml`; it never resolves external entities, external DTDs or network resources, and it does
not expand `&custom;` entities. `:betterlyrics`'s `TTMLParser` (used by `UnisonLyricsProvider`)
uses `DocumentBuilderFactory` with namespace awareness and, best-effort, disables
`disallow-doctype-decl`, external general entities and external parameter entities. Both paths
must remain free of external entity resolution.

In addition, the implementation must enforce explicit bounds at the `parseLyrics` entry point:

- **Size cap**: if `lyrics.length > 4_194_304` UTF-16 code units (4 MiB), return `emptyList()`
  without parsing. `hasSyncedLyrics` therefore returns false.
- **XML depth cap**: when the text looks XML (`looksStructured` or contains `<`), compute maximum
  element nesting by scanning tags; `<?...?>`, `<!--...-->`, `<!...>` and self-closing `<x/>` do
  not increase depth; a closing tag decreases. If maximum depth exceeds `128`, return
  `emptyList()`.
- **Entry cap**: LRC parsing stops after `20_000` produced entries (the first 20 000 sorted by
  time are kept).
- Regular-expression work over user-controlled text must be linear: the public `LINE_REGEX` and
  `TIME_REGEX` and any internal line/time/agent/background/token patterns must avoid nested
  quantifiers.

These caps are fail-closed and deterministic. They do not change any realistic lyric document.

---

## 9. Romanisation

`romanize*` functions are pure with respect to their arguments: the same input always yields the
same output and no shared mutable state is observable. They run off the caller's main thread.
Prompt cancellation mid-string is not guaranteed. They must not throw for any input, including
null/empty where the signature accepts it.

### 9.1 Japanese - `katakanaToRomaji`, `romanizeJapanese`

- `katakanaToRomaji(null)` and `katakanaToRomaji("")` -> `""`.
- Katakana mapping rules (longest match wins; a 2-character entry is preferred over single
  characters):
  - youon (`キャ` etc.), `シャ/シュ/ショ`, `チャ/チュ/チョ`, `ニャ...`, `ヒャ...`, `ミャ...`,
    `リャ...`, `ギャ...`, `ジャ/ジュ/ジョ`, `ヂャ...`, `ビャ...`, `ピャ...`;
  - all base kana, voiced (`ガ..`, `ザ..`, `ダ..`, `バ..`), semi-voiced (`パ..`);
  - `ヲ` -> `o`, `ン` -> `n`, `ー` -> empty.
  - Hepburn-style: `シ=shi`, `チ=chi`, `ツ=tsu`, `フ=fu`, `ジ=ji`, `ヂ=ji`, `ヅ=zu`.
- Small tsu `ッ`: if a `nextKatakana` argument is supplied, append the first roman letter of that
  next kana's mapping (lowercased); if no mapping, append the next character itself; if
  `nextKatakana` is null/empty, append nothing. Consumes one character.
- Unmapped characters pass through unchanged.
- Output is lowercased.
- `romanizeJapanese(text)`: tokenise with a Kuromoji IPADIC tokenizer. For each token, use its
  `reading` when non-empty and not `"*"`, else its `surface`; pass the following token's reading
  (same rule, else null) as the liaison context to `katakanaToRomaji`; join the token outputs with
  one space. Non-Japanese tokens pass through (surface) lowercased. A process-wide tokenizer
  instance is acceptable but not required.
- Both public overloads must exist with the exact default argument (one-arg and
  two-arg-with-default); JVM overload resolution relies on it.

### 9.2 Korean - `romanizeKorean`

- Iterate characters; Hangul syllables `U+AC00..U+D7A3` are decomposed:
  `cho = (code-0xAC00)/(21*28)`, `jung = ((code-0xAC00)%(21*28))/28`, `jong = (code-0xAC00)%28`.
- Jamo index maps: choseong base `U+1100`, jungseong base `U+1161`, jongseong base `U+11A7`
  (index 0 means no final).
- Cho/jung tables produce Revised-Romanisation-style letters (`g/kk/n/d/tt/r/m/b/pp/s/ss/''/j/
  jj/ch/k/t/p/h`; `a/ae/ya/yae/eo/e/yeo/ye/o/wa/wae/oe/yo/u/wo/we/wi/yu/eu/ui/i`).
- Final consonant is **deferred** so liaison can be applied: when the next character starts a new
  Hangul syllable, look up `jong+nextCho` first (e.g. `ᆨᄋ -> g`, `ᆫᄅ -> ll`, `ᆯᄋ -> r`,
  `ᆺᄋ -> s`), else the final alone. If a non-Hangul character follows, flush the final alone.
- A final at end of input is flushed alone.
- Non-Hangul characters pass through unchanged.
- Empty input -> `""`.

### 9.3 Chinese - `romanizeChinese`

- Empty -> `""`.
- For every character in `U+4E00..U+9FFF`, append `Pinyin.toPinyin(char)` lowercased with the
  default locale, followed by one space. All other characters pass through unchanged.
- Post-process: remove whitespace before ASCII punctuation `,.!?;:`, remove whitespace before
  CJK punctuation `，。！？；：、（）《》〈〉【】『』「」`, then `trim()`.
- Uses `com.github.promeg.pinyinhelper.Pinyin` (TinyPinyin). Characters outside the BMP or
  outside the CJK Unified Ideographs block are not romanised.

### 9.4 Cyrillic - `romanizeCyrillic`

- Empty -> `null`.
- Filter characters in `U+0400..U+04FF`. If none, or if the only Cyrillic character is a single
  `е`/`Е`, return `null`.
- Detect the alphabet in this exact order: Russian, Ukrainian, Serbian, Bulgarian, Belarusian,
  Kyrgyz, Macedonian. Unknown -> `null`.
- Tokenisation: split on whitespace and ASCII punctuation `.,!?;` while keeping separators; only
  letter runs are transliterated, separators pass through unchanged.
- Russian: three-letter specials `ого->ovo`, `Ого->Ovo`, `его->evo`, `Его->Evo`; word-initial
  `е/Е -> ye/Ye`; otherwise a general Cyrillic table (`Ж->Zh`, `Х->Kh`, `Ц->Ts`, `Ч->Ch`,
  `Ш->Sh`, `Щ->Shch`, `Ю->Yu`, `Я->Ya`, `Ё->Yo`, `Й->Y`, `Ъ->ʺ`, `Ы->Y`, `Ь->ʹ`, `Э->E`, ...).
- Ukrainian: `Г->H`, `Ґ->G`, `Є->Ye`, `І->I`, `Ї->Yi` (+ lowercase); after a consonant (previous
  character is a letter and not a Cyrillic vowel), `Ю->Iu`, `Я->Ia` (+ lowercase); otherwise the
  general table.
- Serbian: `Ж->Ž`, `Љ->Lj`, `Њ->Nj`, `Ц->C`, `Ч->Č`, `Џ->Dž`, `Ш->Š`, `Х->H` (+ lowercase),
  then general.
- Bulgarian: `Ж->Zh`, `Ц->Ts`, `Ч->Ch`, `Ш->Sh`, `Щ->Sht`, `Ъ->A`, `Ь->Y`, `Ю->Yu`, `Я->Ya`
  (+ lowercase).
- Belarusian: `Г->H`, `Ў->W` (+ lowercase); word-initial `е/Е -> ye/Ye`; otherwise general.
- Kyrgyz: `Ү->Ü`, `Ы->Y` (+ lowercase); otherwise general.
- Macedonian: `Ѓ->Gj`, `Ѕ->Dz`, `И->I`, `Ј->J`, `Љ->Lj`, `Њ->Nj`, `Ќ->Kj`, `Џ->Dž`, `Ч->Č`,
  `Ш->Sh`, `Ж->Zh`, `Ц->C`, `Х->H` (+ lowercase).
- `isCyrillicVowel` set = `АаЕеЄєИиІіЇїОоУуЮюЯяЫыЭэ`.

### 9.5 Hindi / Punjabi

- `romanizeHindi`: two-character table lookup first (conjuncts `क्ष->ksh`, `त्र->tr`,
  `ज्ञ->gy`, `श्र->shr`, nukta combinations `क़->q` etc.), else single-character table
  (independent vowels, consonants, matras, anusvara/visarga/candrabindu, Devanagari digits
  `०..९`, `ॐ->Om`, virama and nukta -> empty). Unmapped characters pass through.
- `romanizePunjabi`: addak `U+0A71` doubles the following consonant by emitting the first roman
  letter of its mapping (then consumes the addak); otherwise two-character table then
  single-character table. Includes Gurmukhi digits, `ੴ -> "Ek Onkar"`, virama/nukta -> empty.
  Unmapped characters pass through.

### 9.6 Script detection predicates

All predicates are pure, case-sensitive and do not trim.

| predicate | true when |
|---|---|
| `isRussian` | at least one char in the Russian alphabet set, and every Cyrillic-range char is in that set |
| `isUkrainian` | at least one Ukrainian letter/specific char, and every Cyrillic char is in the Ukrainian set (includes `ҐґЄєІіЇї`) |
| `isSerbian` | at least one Serbian letter/specific char, every Cyrillic char in the Serbian set (includes `ЂђЈјЉљЊњЋћЏџ`) |
| `isBulgarian` | at least one Bulgarian letter, every Cyrillic char in the Bulgarian set |
| `isBelarusian` | at least one Belarusian letter/specific char, every Cyrillic char in the Belarusian set (includes `ЎўІі`) |
| `isKyrgyz` | at least one Kyrgyz letter/specific char, every Cyrillic char in the Kyrgyz set (includes `ҢңӨөҮү`) |
| `isMacedonian` | at least one Macedonian letter/specific char, every Cyrillic char in the Macedonian set (includes `ЃѓЅѕЌќ`) |
| `isJapanese` | any hiragana `U+3040..U+309F`, katakana `U+30A0..U+30FF`, **or** CJK ideograph `U+4E00..U+9FFF` |
| `isKorean` | any Hangul syllable `U+AC00..U+D7A3` |
| `isChinese` | empty -> false; at least one CJK ideograph; kana count / total length < 0.1 |
| `isHindi` | any Devanagari `U+0900..U+097F` |
| `isPunjabi` | any Gurmukhi `U+0A00..U+0A7F` |

Note that `isJapanese` is true for CJK-only text; callers guard with `!isChinese`. Latin-only text
is false for every predicate.

---

## 10. Translation Orchestration - `LyricsTranslationHelper`

Process-wide `object`; all state survives song changes and is shared by every screen. It is
intentionally not Hilt-injected.

### 10.1 State and triggers

- `status` starts `Idle`.
- `hasActiveTranslations` starts `false`; set true on success/cached/database load, false on
  clear.
- `manualTrigger`, `clearTranslationsTrigger` and `translationSaved` are `SharedFlow<Unit>` with
  `extraBufferCapacity = 1` and `onBufferOverflow = DROP_OLDEST`; emissions use `tryEmit` and may
  be dropped when no collector is ready.
- `setCompositionActive(active)` gates UI-facing error/success reset behaviour: while inactive,
  status is left unchanged by error/success reporting. The Compose screen sets it true on entry
  and false (then cancels) on dispose.
- `resetStatus()` sets `Idle` and cancels any pending status reset.
- `cancelTranslation()` cancels any pending status reset and any in-flight translation, sets
  `Idle`, and does not clear the cache or per-entry flows.
- `clearCache()` clears the translation cache only.

Status linger is 3 s; a translation attempt has a 45 s budget.

### 10.2 Cache

- In-memory cache keyed by the translation inputs: the non-blank entry texts joined with `"\n"`,
  the mode and the language. Any deterministic key derivation is acceptable, including hash-based
  keys with accepted collisions.
- Value: translated lines, one per non-blank entry.
- Entries are written after a successful translation run (streaming or not) and by database load.
  Cache reads never mutate it.
- `getCachedTranslations(lyrics, mode, language)` returns the cached list or `null`; it does not
  check the length against the entry count.
- `applyCachedTranslations` returns false when no entry exists or when the cached list is shorter
  than the non-blank entry count. Otherwise it assigns `cached[i]` to the i-th non-blank entry and
  returns true; surplus cached lines are ignored.
- The cache is never evicted except by `clearCache()`.
- Cache access must be safe under concurrent use (translation, UI reads and database load may run
  concurrently).

`nonEmptyEntries` = entries whose `text.isNotBlank()`, paired with their original index.

### 10.3 `translateLyrics` pipeline (observable order)

Effects occur in this order:

1. Any pending status reset and any previous translation are cancelled; `status` becomes
   `Translating()`.
2. Every `translatedTextFlow` is cleared to `null`.
3. On the caller's scope, off the main thread, the following checks run in order:
   1. `effectiveApiKey` = `deeplApiKey` when `provider == "DeepL"`, else `apiKey`. Blank ->
      `Error(ai_error_api_key_required)` and stop.
   2. `lyrics.isEmpty()` -> `Error(ai_error_no_lyrics)` and stop.
   3. Non-blank entries (`nonEmptyEntries`) empty -> `Error(ai_error_lyrics_empty)` and stop.
   4. `fullText` = non-blank entry texts joined with `"\n"`.
   5. Cache lookup for `(fullText, mode, targetLanguage)`. If present with at least as many lines
      as entries: assign lines to entries, log via `AiLogger` (`"Lyrics Translation"`,
      `"Translated lyrics for $songId"`), persist with only-if-empty semantics (10.5), set success
      (10.6), and stop. This precedes the blank-language check, so a cached translation is served
      even with a blank target language.
   6. Blank `targetLanguage` -> `Error(ai_error_language_required)` and stop.
   7. `languageName` = `LanguageCodeToName[code]`, else
      `Locale.forLanguageTag(code).displayLanguage` when non-blank and different from the code,
      else the code itself.
   8. Run the provider translation (selection below) with a 45 s budget. Timeout -> failure with
      `ai_error_translation_failed`.
   9. On success:
      - if composition is inactive, stop without applying (before any cache/persist work);
      - write the translated list to the cache;
      - persist all lines joined with `"\n"`;
      - if the translated list is empty while entries are non-empty -> `Error(ai_error_unexpected)`
        (the empty list has already been cached and persisted in this path);
      - assign `translated[i]` to entry i for `i < entries.size`; surplus lines are ignored and
        missing lines stay `null`;
      - log via `AiLogger`; set success.
   10. On provider failure -> report the error message, or `ai_error_unknown` when absent.
4. `CancellationException` propagates. Any other unexpected exception reports its message, or
   `ai_error_translation_failed` when absent.
5. Provider selection (first match):
   - `provider == "DeepL"` -> `DeepLService.translate` with the full text, the mapped target
     language code, the DeepL key, the configured formality and a log sink;
   - `provider == "Mistral"` -> `MistralService.translate` with the full text, language name, key,
     model, mode and a log sink;
   - `useStreaming && provider != "Custom"` -> streaming merge (10.4);
   - else -> `OpenRouterService.translate` with the full text, language name, key, baseUrl, model,
     mode and a log sink.

Log lines are appended only while the status is `Translating`; in any other status they are
silently dropped.

### 10.4 Streaming merge

While a streaming translation is consumed:

- Each `Content(delta)` is appended to a buffer and scanned for complete JSON-array string
  elements, bounded by the expected entry count. Every complete, non-blank line is assigned to its
  index while `index < entries.size`. While the status is `Translating`, the `Translating` state is
  re-emitted (no log change).
- `Complete(lines)` is the authoritative result.
- `Error(message)` records the failure message.
- At end of stream: a recorded error wins -> failure with that message; else a `Complete` ->
  success; else failure with `"No translation received"`.

Partial-array scanning tolerates incremental output:

- Only material after the first `[` is considered; without `[` nothing is parsed.
- Outside a JSON string, `]` ends the array; `"` toggles string mode; inside a string, `\` copies
  the next character raw (no unescaping). Each closed string is one line. Scanning stops once the
  expected count is reached.
- An unterminated trailing string is discarded; non-string elements and commas are ignored; lines
  are not trimmed and may contain literal backslash escapes.

### 10.5 Database load and persist

Loading:

1. Every `translatedTextFlow` is set to `null`.
2. If the entity is null, `translatedLyrics` is null/blank, or its `translationLanguage`/
   `translationMode` differ from the arguments -> `hasActiveTranslations = false` and stop.
3. `translatedLyrics` is split on `"\n"` (a trailing newline yields a trailing empty element).
4. Line i is assigned to the i-th non-blank entry while `i < translatedLines.size`.
5. The full split list is cached under the current key and `hasActiveTranslations = true`. Surplus
   database lines remain cached and count as complete.

Persistence:

- No-op when `songId` is blank or the database is null.
- Runs in the background; it reads the existing entity for the song and returns when absent. With
  only-if-empty semantics it also returns when the existing entity already has non-blank
  `translatedLyrics`.
- On write it upserts the existing entity with `translatedLyrics = lines.joinToString("\n")`,
  `translationLanguage = targetLanguage` and `translationMode = mode`.
- `translationSaved` is emitted on success. Non-cancellation failures are logged and swallowed;
  `CancellationException` is rethrown.
- Persistence never blocks translation completion.

`hasTranslations(entity)` = `!entity?.translatedLyrics.isNullOrBlank()`.
`clearTranslations(entity)` = copy with `translatedLyrics = ""`, `translationLanguage = ""`,
`translationMode = ""`.

### 10.6 Status timing and errors

- Error reporting: while composition is inactive, status is not changed. Otherwise any pending
  status reset is cancelled, `Error(message)` is set, and after 3 s the status returns to `Idle`
  only if it is still that same error and composition is active.
- Success reporting: `hasActiveTranslations = true`, `status = Success`, after 3 s back to `Idle`
  only if still `Success` and composition is active.
- `triggerClearTranslations()` sets `hasActiveTranslations = false` and emits the clear flow.
- Error string resources are module-local (`lyrics/src/main/res`): `ai_error_api_key_required`,
  `ai_error_no_lyrics`, `ai_error_lyrics_empty`, `ai_error_language_required`,
  `ai_error_translation_failed`, `ai_error_unexpected`, `ai_error_unknown`. Both `values/` and
  `values-es/` must exist with the same keys.

### 10.7 Threading and cancellation

- Translation work runs off the main thread on the caller's scope; status flows are thread-safe and
  cache access is concurrency-safe (10.2).
- Persistence runs separately and never blocks translation completion.
- `cancelTranslation()` cancels the in-flight translation. Cancellation ends it silently: no error
  status and no reported error.
- Tests require injectable dispatcher/time control and an injectable translation delegate so
  virtual time and fake results can be used; public API unchanged.

---

## 11. AI HTTP Services

### 11.1 Shared clients

```kotlin
internal val aiHttpClient: HttpClient        // OkHttp engine
internal val aiStreamingHttpClient: HttpClient
```

| client | connect | socket | request |
|---|---|---|---|
| `aiHttpClient` | 30 s | 90 s | 90 s |
| `aiStreamingHttpClient` | 30 s | 45 s | 45 s |

Both are process-wide instances. There is no automatic retry, authentication or content
negotiation (bodies are strings; retries are specified per service). For tests, services must be
constructible against a supplied `HttpClient` (for example MockEngine); this seam is internal.

### 11.2 Prompts (`AiPrompts`, internal)

`system(lineCount)` must instruct: output only a valid JSON array of strings; no prose; one output
per input line; preserve empty lines as `""`; exactly `lineCount` items; best approximation if
unsure.

`user(mode, text, lineCount, targetLanguage)`:

- `"Romanized"`: transliterate to simple ASCII `a-z A-Z 0-9` punctuation, no diacritics, leave
  Latin-script text unchanged, do not translate meaning, preserve line structure; embed the
  `lineCount` and the input text.
- `"Transcribed"`: transliterate phonetically **into the target language's native script**, no
  meaning translation, leave text already in that script unchanged; embed count/text.
- any other mode (including `"Literal"`, the UI default): natural translation into the target
  language, maintain meaning/poetic flow/punctuation and line structure; embed count/text.

All three must state the output is a JSON array with exactly `lineCount` items. Exact wording is
not wire-critical.

### 11.3 Response parsing (`AiTranslationParsing`, internal)

`extractContent(body): String?`:

- Parse `body` as a JSON object (lenient); any parse/type failure -> `null`.
- If `content` is a JSON array: concatenate the `text` string fields of its object elements
  (elements without a string `text` are ignored; the block `type` is not inspected), trim;
  return it if non-blank.
- Otherwise (or if the above was blank): `choices[0].message.content` as a string, trimmed,
  returned only if non-blank. Missing/mistyped paths -> `null`.

`parseLines(content, expected): List<String>?`:

1. Try the trimmed content as a JSON array.
2. Try with ` ```json ` and ` ``` ` fences removed.
3. Try the substring from the first `[` to the last `]` (only when end > start).
4. If JSON parsing still fails but a bracketed substring exists, best-effort line split: keep
   non-blank lines, trim, strip one pair of surrounding `"` then one pair of surrounding `'`.
   This fallback may retain commas/quotes; it is deliberately looser.
5. If no strategy yields a list -> `null`.
6. Normalise: exact size -> unchanged; surplus -> `take(expected)`; short -> append `""` until
   `expected`.
7. Each array element: `JsonPrimitive.contentOrNull` or `""` (JSON `null`/objects/arrays become
   `""`; numbers/booleans become their literal text).

`normalize(lines, expected)` is the same size rule and is part of the contract.

### 11.4 DeepL - `DeepLService`

```kotlin
suspend fun translate(
  text: String, targetLanguage: String, apiKey: String, formality: String = "default",
  maxRetries: Int = 3, onLog: ((String) -> Unit)? = null,
): Result<List<String>>

suspend fun testConnection(apiKey: String): Result<String>
```

- Blank `text` -> failure `Exception("Input text is empty")`.
- `expected = text.lines().size` (Kotlin `lines()`: empty input -> empty list; a trailing newline
  yields a trailing empty element).
- Endpoint: `https://api-free.deepl.com/v2/translate` when `apiKey.endsWith(":fx")`, otherwise
  `https://api.deepl.com/v2/translate`.
- Request JSON: `text: [String]` (one element per line), `target_lang: String`,
  `preserve_formatting: true`, and `formality` only when `formality != "default"`.
- Target language mapping (case-insensitive input):
  - `zh`, `zh-cn`, `zh-hans`, `zh-tw`, `zh-hant` -> `ZH`
  - `en`, `en-us` -> `EN-US`
  - `en-gb` -> `EN-GB`
  - `pt`, `pt-pt` -> `PT-PT`
  - `pt-br` -> `PT-BR`
  - else uppercase and `take(2)` (e.g. `ja-JP` -> `JA`).
- Headers: `Authorization: DeepL-Auth-Key <apiKey.trim()>`, JSON content type.
- Retry loop `attempt = 1..maxRetries`:
  - log start and each attempt;
  - HTTP 5xx -> delay `1000 * attempt` ms, then next attempt;
  - other non-2xx -> immediate failure `Exception("Translation failed: <message>")`, where
    `<message>` is the top-level `message` field if non-blank, else `"HTTP <code>"`;
  - 2xx but `translations` missing/not an array/empty -> delay `1000 * attempt`, retry;
  - 2xx with translations -> map each element's `text` (`orEmpty()`), log count, return
    `Result.success(normalize(translated, expected))`.
  - Non-cancellation exception -> retry (with backoff) unless the attempt count is exhausted, in
    which case `Result.failure(e)`.
  - After the loop -> `Result.failure(Exception("Max retries exceeded"))`.
- `testConnection("")` -> failure `Exception("DeepL API key is required")`; otherwise translate
  `"Hello"` to `"EN"` and map success to `"DeepL connection successful!"` (the line text is
  ignored).

### 11.5 Mistral - `MistralService`

```kotlin
suspend fun translate(
  text: String, targetLanguage: String, apiKey: String, model: String, mode: String,
  maxRetries: Int = 3, sourceLanguage: String? = null, onLog: ((String) -> Unit)? = null,
): Result<List<String>>
```

- Endpoint constant `https://api.mistral.ai/v1/chat/completions`.
- Blank text -> failure `"Input text is empty"`.
- Request JSON: `model` = `model.ifBlank { "mistral-small-latest" }`;
  `messages = [{role:"user", content: AiPrompts.user(...)}]` (no system message);
  `temperature = 0.3`; `max_tokens = expected * 100`.
- Header `Authorization: Bearer <trimmed key>` only when the key is non-blank.
- 5xx -> backoff retry; other non-2xx -> failure with the `error.message` field if non-blank,
  else `HTTP <code>`; 2xx: `extractContent` then `parseLines`; both must succeed to return.
  A `null` content or `null` parse retries the loop **without an explicit delay** (baseline).
- Exceptions retried as in DeepL; exhaustion -> `"Max retries exceeded"`.
- `sourceLanguage` is accepted but unused.

### 11.6 OpenRouter / OpenAI-compatible / Anthropic - `OpenRouterService`

`isUrlSafe(url): Boolean`:

- Blank -> `true` (treated as "use default").
- Parse with `java.net.URI`; parse failure -> `false`.
- Scheme lowercased must be `http` or `https`; host must exist.
- Reject host `169.254.169.254`, any host starting with `169.254.`, `metadata.google.internal`,
  `instance-data` (case-insensitive).
- Everything else is allowed, including `localhost`, private ranges, ports and paths.
  (This is the app's SSRF guard for user-supplied base URLs; do not expand it to block localhost.)

`isAnthropic(url)` = contains `api.anthropic.com` AND `/messages`, case-insensitive.

`requestChat` payload:

- `model` only when non-blank.
- Anthropic: top-level `system` when non-blank; `messages=[{role:"user",content:user}]`.
  Otherwise: messages array with an optional `{role:"system",content:system}` first, then the
  user message.
- `temperature` only when non-null; `max_tokens` always.
- Headers: Anthropic -> `x-api-key: <trim>` and `anthropic-version: 2023-06-01`; non-Anthropic ->
  `X-Title: revenge` (Anthropic does **not** get `X-Title`); all -> `Authorization: Bearer
  <trim>` when the key is non-blank. JSON content type.

`translate(...)`:

- `url = baseUrl.ifBlank { "https://openrouter.ai/api/v1/chat/completions" }`; unsafe ->
  `Result.failure(SecurityException("Invalid or restricted endpoint destination"))`.
- Blank text -> `"Input text is empty"`; `expected = text.lines().size`; build the user prompt.
- Retry loop:
  - 5xx -> backoff retry;
  - other non-success -> failure
    `Exception("Translation failed: <error.message | message | HTTP code>")` (error object's
    message first, then top-level `message`);
  - success but `extractContent == null` -> delay + retry;
  - success with content but `parseLines == null` -> retry immediately (no delay);
  - success with parsed lines -> return.
  - Exceptions: retry with backoff; exhausted -> `Result.failure(e)`.
  - After loop -> `"Max retries exceeded"`.
- `temperature = 0.3`, `maxTokens = expected * 100`, `system = AiPrompts.system(expected)`.
- `sourceLanguage` is accepted but unused.

`testConnection(baseUrl, apiKey, model)`:

- Unsafe URL -> `Result.failure(SecurityException(...))`. No retry.
- Request: system `null`, user `"ping"`, max tokens 10, no temperature, model as given.
- Non-success -> failure with the extracted error message (or `HTTP code`).
- Success -> `Result.success(extractContent(body) ?: "Connection OK")`.
- Exceptions -> `Result.failure(e)` (cancellation rethrown).

### 11.7 Streaming - `OpenRouterStreamingService`

```kotlin
fun streamTranslation(
  text: String, targetLanguage: String, apiKey: String, baseUrl: String,
  model: String, mode: String,
): Flow<StreamChunk>

sealed class StreamChunk {
  data class Content(val text: String) : StreamChunk()
  data class Complete(val translatedLines: List<String>) : StreamChunk()
  data class Error(val message: String) : StreamChunk()
}
```

Flow behaviour (emissions happen off the main thread; collector cancellation cancels the
underlying HTTP call):

1. Blank text -> emit `Error("Input text is empty")`, complete.
2. `url = baseUrl.ifBlank { DEFAULT }`; `!isUrlSafe` -> emit
   `Error("Invalid or restricted endpoint destination")`, complete.
3. Request JSON: optional `model`; `messages` = system (`AiPrompts.system(expected)`) + user
   (`AiPrompts.user(mode, text, expected, targetLanguage)`); `stream = true`;
   `temperature = 0.3`; `max_tokens = expected * 100`.
   Header `Authorization: Bearer <trim>` when key non-blank; `X-Title: revenge`; JSON content
   type. No Anthropic special-casing here: streaming always uses the OpenAI shape and bearer
   header.
4. `preparePost(url).execute { response -> ... }`:
   - non-2xx -> read body, extract `error.message`, emit
     `Error("Translation failed: <message | HTTP code>")`, return. No retry.
   - read body channel as text lines while the coroutine is active.
   - Ignore any line that does not start exactly with `"data: "`.
   - Strip the prefix, trim; `"[DONE]"` -> stop reading.
   - Parse the frame as JSON: `choices[0].delta.content`. Any parse/type failure is ignored and
     the stream continues. `null`/empty deltas are ignored.
   - Non-empty delta -> append to a buffer and emit `Content(delta)`.
5. After the stream, cancellation is rechecked. If any delta was received, the accumulated text is
   parsed with the expected line count: success -> `Complete(lines)`; failure ->
   `Error("Failed to parse translation")`. If no delta was received, no terminal chunk is emitted
   (the flow completes after zero or more Content chunks; the helper then reports
   `"No translation received"`).
6. `CancellationException` is rethrown. For any `IOException`/other exception, cancellation is
   rechecked (rethrown if cancelled), then `Error(message ?: "Network error" | "Unknown error")`
   is emitted.
7. Collector cancellation cancels the HTTP call and the flow.

### 11.8 Wire DTO schemas (normative; built/parsed as JSON trees)

No Kotlin serializable classes exist in `:lyrics`. The following logical DTOs define the exact
external wire format. Unknown fields must be ignored everywhere; absent optional fields use the
listed default.

**DeepL request**

| field | type | presence | handling |
|---|---|---|---|
| `text` | array of string | required | one per `text.lines()` |
| `target_lang` | string | required | mapped as 11.4 |
| `formality` | string | optional | omitted when `"default"` |
| `preserve_formatting` | boolean | required | always `true` |

**DeepL response**

| field | type | presence |
|---|---|---|
| `translations` | array of object | required for success |
| `translations[].text` | string | optional -> `""` |
| `translations[].detected_source_language` | string | ignored |
| `message` | string | error paths only |

**OpenAI-compatible chat request** (Mistral, OpenRouter, streaming)

| field | type | presence |
|---|---|---|
| `model` | string | Mistral: always (default `mistral-small-latest`); OpenRouter/streaming: only when non-blank |
| `messages` | array of `{role: string, content: string}` | required |
| `temperature` | number | OpenRouter non-stream 0.3, Mistral 0.3, streaming 0.3; testConnection omits |
| `max_tokens` | integer | required; `lines * 100`, testConnection 10 |
| `stream` | boolean | streaming only, `true` |

**Anthropic Messages request** (only when `requestChat` detects the Anthropic URL)

| field | type | presence |
|---|---|---|
| `model` | string | only when non-blank |
| `system` | string | optional, top-level |
| `messages` | array of `{role:"user", content: string}` | required |
| `max_tokens` | integer | required |
| `temperature` | number | optional |

**Chat response (either family)**

| field | type | presence |
|---|---|---|
| `content` | array of `{type: string, text: string}` | Anthropic; blocks without string `text` ignored |
| `choices` | array | OpenAI; only `[0]` read |
| `choices[0].message.content` | string | optional |
| `choices[0].delta.content` | string | streaming frames; optional |
| `error` | `{message: string}` | error paths |
| `message` | string | OpenRouter fallback error text |

**SSE frames**: lines `data: <json>`; `data: [DONE]` terminates. Non-`data:` lines (comments,
`event:`, blank lines) are ignored. Each frame is independently parsed; a malformed frame is
ignored without terminating the stream.

Absent/null handling summary:

- Any missing path -> `null`, then the caller's fallback (`HTTP <code>`, retry, or `""`).
- String-typed leaves are read only from `JsonPrimitive`; objects/arrays/null become `null`
  (or `""` inside a parsed line array).
- `Json { isLenient = true; ignoreUnknownKeys = true }` is mandatory for every parser here.
- Numbers/booleans used as translation lines are stringified by `contentOrNull`.

---

## 12. Cross-Module Contracts Required to Compile

### 12.1 `:core`

```kotlin
// com.revenge.music.playback
data class LyricsWithProvider(val lyrics: String?, val providerName: String)
interface ILyricsHelper { suspend fun getLyrics(mediaMetadata: MediaMetadata): LyricsWithProvider }

// com.revenge.music.models
data class MediaMetadata(
  val id: String, val title: String, val artists: List<Artist>, val duration: Int,
  val thumbnailUrl: String? = null, val album: Album? = null, /* ... */
) {
  data class Artist(val id: String?, val name: String)
  data class Album(val id: String, val title: String)
}

// com.revenge.music.db.entities
data class LyricsEntity(
  val id: String,
  val lyrics: String,
  val provider: String = "Unknown",
  val translatedLyrics: String = "",
  val translationLanguage: String = "",
  val translationMode: String = "",
) { companion object { const val LYRICS_NOT_FOUND = "LYRICS_NOT_FOUND" } }

// com.revenge.music.db.MusicDatabase (Room; :lyrics only uses these)
fun lyrics(id: String?): Flow<LyricsEntity?>
fun query(block: MusicDatabase.() -> Unit)   // posts block to the query executor
@Upsert fun upsert(lyrics: LyricsEntity)     // inherited from DatabaseDao

// com.revenge.music.utils
val Context.dataStore: DataStore<Preferences>          // preferencesDataStore("settings")
operator fun <T> DataStore<Preferences>.get(key: Preferences.Key<T>): T?   // runBlocking, null on failure
fun reportException(throwable: Throwable)

class NetworkConnectivityObserver(context: Context) {
  val networkStatus: Flow<Boolean>
  fun isCurrentlyConnected(): Boolean
  fun unregister()
}

// com.revenge.music.constants
val LyricsProviderOrderKey: Preferences.Key<String>      // "lyricsProviderOrder"
val EnableYouLyPlusKey / EnablePaxsenixKey / EnableBetterLyricsKey /
    EnableSimpMusicKey / EnableLrcLibKey / EnableKugouKey / UnisonLyricsEnabledKey
val LanguageCodeToName: Map<String, String>
enum class PreferredLyricsProvider { LRCLIB, KUGOU, BETTER_LYRICS, SIMPMUSIC, YOULYPLUS, PAXSENIX, UNISON }

// com.revenge.music.ai (used by translation success/cache paths)
object AiLogger { suspend fun log(context: Context, action: String, details: String) }
```

`reportException` logs via Timber and forwards to the global exception reporter; it must not throw.

### 12.2 Provider modules

```kotlin
// :youlyplus
object YouLyPlus {
  suspend fun getLyrics(title: String, artist: String, duration: Int, album: String? = null,
    id: String? = null, isrc: String? = null): Result<String>
  suspend fun getAllLyrics(title: String, artist: String, duration: Int, album: String? = null,
    id: String? = null, isrc: String? = null, callback: (String) -> Unit)
}

// :lrclib
object LrcLib {
  suspend fun getLyrics(title: String, artist: String, duration: Int,
    album: String? = null): Result<String>
  suspend fun getAllLyrics(title: String, artist: String, duration: Int,
    album: String? = null, callback: (String) -> Unit)
}

// :kugou
object KuGou {
  var useTraditionalChinese: Boolean
  suspend fun getLyrics(title: String, artist: String, duration: Int,
    album: String? = null): Result<String>
  suspend fun getAllPossibleLyricsOptions(title: String, artist: String, duration: Int,
    album: String? = null, callback: (String) -> Unit)
}

// :simpmusic
object SimpMusicLyrics {
  suspend fun getLyrics(videoId: String, duration: Int = 0): Result<String>
  suspend fun getAllLyrics(videoId: String, duration: Int = 0, callback: (String) -> Unit)
}

// :unison
object Unison {
  var logger: ((String) -> Unit)?
  suspend fun getLyrics(videoId: String? = null, title: String, artist: String,
    album: String? = null, durationSeconds: Int = -1): Result<String>
  suspend fun getAllLyrics(videoId: String? = null, title: String, artist: String,
    album: String? = null, durationSeconds: Int = -1, callback: (String) -> Unit)
}

// :betterlyrics
object BetterLyrics {
  suspend fun getLyrics(title: String, artist: String, duration: Int,
    album: String? = null): Result<String>
  suspend fun getAllLyrics(title: String, artist: String, duration: Int,
    album: String? = null, callback: (String) -> Unit)
}
object TTMLParser {
  data class ParsedWord(val text: String, val startTime: Double, val endTime: Double)
  data class ParsedLine(val text: String, val startTime: Double,
    val words: List<ParsedWord>, val agent: String? = null,
    val isBackground: Boolean = false, val backgroundLines: List<ParsedLine> = emptyList())
  fun parseTTML(ttml: String): List<ParsedLine>   // empty on malformed input
  fun toLRC(lines: List<ParsedLine>): String
}

// :paxsenixlyrics
object Paxsenix {
  fun init(context: Context)
  suspend fun getLyrics(title: String, artist: String, duration: Int,
    album: String? = null): Result<String>
  suspend fun getAllLyrics(title: String, artist: String, duration: Int,
    album: String? = null, callback: (String) -> Unit)
}
```

### 12.3 `:innertube`

```kotlin
object YouTube {
  suspend fun next(endpoint: WatchEndpoint, continuation: String? = null): Result<NextResult>
  suspend fun lyrics(endpoint: BrowseEndpoint): Result<String?>
  suspend fun transcript(videoId: String): Result<String>
}
data class NextResult(/* ... */ val lyricsEndpoint: BrowseEndpoint? = null)
data class WatchEndpoint(val videoId: String? = null, /* ... */)
```

`YouTube.transcript` returns one LRC line per cue (`[MM:SS.mmm]text`), trims `♪` and spaces.
Any continuation/pagination is internal to `:innertube`; the lyrics provider sees a single
`Result<String>`.

### 12.4 `lyrics-core` 0.5.0 (`api` dependency)

```kotlin
class AutoParser(/* ... */) {
  fun canParse(content: String): Boolean
  fun parse(content: String): SyncedLyrics
}
data class SyncedLyrics(val lines: List<ISyncedLine>)
// ISyncedLine implementations used here:
data class SyncedLine(val content: String, val translation: String?,
  val start: Int, val end: Int, val phonetic: String? = null, val languageTag: String? = null)
sealed interface KaraokeLine {
  val syllables: List<KaraokeSyllable>
  val translation: String?
  val start: Int          // milliseconds
  val end: Int            // milliseconds
  data class MainKaraokeLine(/* syllables, translation, alignment, start, end, ... */) : KaraokeLine
  data class AccompanimentKaraokeLine(/* same shape */) : KaraokeLine   // marks x-bg
}
data class KaraokeSyllable(val content: String, val start: Int, val end: Int,
  /* optional languageTag */)
```

All `start`/`end` values are milliseconds. `lyrics-core`'s TTML parser uses a hand-written
iterative tokenizer and performs no external entity resolution.

---

## 13. Deliverables and Test Surface

A conforming implementation delivers the `:lyrics` module with the public API of section 3, the
data model of section 4, the wire schemas of 11.8 and the behaviour of sections 5-11. Source file
names, package-internal classes and helper decomposition are the implementer's choice; the module
must remain a single Android library at `lyrics/` whose parsing and romanisation logic is JVM
testable.

Required test classes (names are referenced by section 14; assertions are normative):

`LyricsUtilsTest`, `LyricsParsingFixturesTest`, `RomanizationTest`, `LyricsProviderRegistryTest`,
`LyricsHelperOrchestrationTest`, `LyricsTranslationHelperTest`, `AiTranslationParsingTest`,
`AiServiceHttpTest`, `OpenRouterStreamingServiceTest`.

Required fixture resources (embedding as test constants is also acceptable):

- `fixtures/lrc/standard.lrc`, `rich.lrc`, `metadata_only.lrc`, `malformed.lrc`, `offset.lrc`
- `fixtures/ttml/apple.ttml`, `malformed.ttml`, `xxe.ttml`
- `fixtures/ai/deepl_success.json`, `deepl_403.json`, `chat_success.json`, `chat_anthropic.json`,
  `chat_fenced.json`, `chat_malformed.json`
- `fixtures/sse/stream.txt`, `error.txt`

Implementations must be original. Copying from `../Revenge-source`, `../Echo-Music` or any other
reference implementation is prohibited; designs are similarity-audited.

---

## 14. Acceptance Tests

All tests are deterministic: no real network, no real clock waits (use `kotlinx-coroutines-test`
virtual time or MockEngine), fixed fixtures, and explicit counts of provider/HTTP invocations.
Test names below are indicative; assertions are normative.

### 14.1 LRC parsing (`LyricsUtilsTest`, `LyricsParsingFixturesTest`)

| # | input | expectation |
|---|---|---|
| L1 | `standard.lrc` (two metadata lines + 3 timed lines, 2- and 3-digit fractions) | 3 entries; times `0`, `12_500`, `65_234`; text preserved; sorted |
| L2 | `"[00:01.234][00:02.50]Hello"` | 2 entries; `1_234`, `2_500`; both text `Hello` |
| L3 | `metadata_only.lrc` = `"[ar:Artist]\nFirst line\nSecond line"` | `parseLyrics` empty; `hasSyncedLyrics` false |
| L4 | `"[00:00.00]First\n[00:00.00]Second"` | 2 entries; `hasSyncedLyrics` false |
| L5 | `"\n[ar:Artist]\n[00:00.00]First\n[00:12.50]Second"` | times `[0, 12_500]`; `hasSyncedLyrics` true |
| L6 | `offset.lrc` = `"[offset:500]\n[00:01.00]A"` | one entry at `1_000`, offset **not applied** |
| L7 | line without timestamp `"plain text"` | empty list |
| L8 | malformed `"[0:01.00]x"`, `"[00:1.00]x"`, `"[00:01.00]"`, `"[xx:yy.zz]x"` | empty list, no throw |
| L9 | `"[00:01.00]A &amp; B\\n[00:02.00]C &#32;D"` | texts `"A & B"` and `"C  D"` (two spaces) |
| L10 | `"&#x1F600;"`-style invalid (`&#x110000;`) | entity left literal |
| L11 | 20 001 timed lines | exactly 20 000 entries; no OOM; runtime bounded |
| L12 | 4 MiB + 1 char input | empty list; no throw |
| L13 | provider returns plain multi-line text with no timestamps | `parseLyrics` empty; `hasSyncedLyrics` false; orchestration classifies it `UNSYNCED` and may still select it by priority (see O2) |

### 14.2 Rich-sync parsing (`LyricsParsingFixturesTest`)

| # | fixture pattern | expectation |
|---|---|---|
| R1 | `rich.lrc` `"[00:12.34]<00:12.34>Hel<00:12.80>lo"` | text `Hello`; 2 words; starts `12.34`, `12.80`; last end `12.34+0.5 = 12.84` |
| R2 | last word followed by another rich line | last end equals the next line start |
| R3 | `"[00:20.00]{bg}echo {agent:v1}"` | `isBackground`, text `echo`, `agent="v1"` |
| R4 | `"[00:30.00]{agent:v1}text {bg}not-prefix"` | agent set; `isBackground` false because `{bg}` is not at start |
| R5 | rich line with no word tags | `words == null` |
| R6 | malformed word tags `<bad>`, `<00:1.0>` | tags ignored; line text still produced; no throw |

### 14.3 TTML / XML (`LyricsParsingFixturesTest`)

| # | fixture | expectation |
|---|---|---|
| T1 | `apple.ttml` with `<tt xmlns=...><p begin="1.5"><span begin="1.5" end="2.0">Hello</span> <span ...>world</span></p>` | at least one entry; text contains `Hello`; `words` non-null; word times in seconds (`1.5`, `2.0`) |
| T2 | `malformed.ttml` (`<tt><body><p begin='0'>oops`) | returns a list (possibly empty); never throws |
| T3 | `xxe.ttml` with `<!DOCTYPE ... <!ENTITY xxe SYSTEM "file:///etc/passwd">>` and `&xxe;` | never contains file contents (`root:`, `daemon:`); no exception; no network/file access |
| T4 | 10 000 nested `<span>` elements | returns empty list within 1 s (depth cap 128) |
| T5 | text starting `<` but not XML (`"<3 lyrics"`) | no throw; falls back to LRC parsing; empty list |
| T6 | TTML with `x-bg`/accompaniment | entries with `isBackground = true` where the backing parser exposes it |
| T7 | TTML with inline translation | `translatedTextFlow.value` set for the corresponding entry |

### 14.4 Sync lookup (`LyricsUtilsTest`)

| # | lines | position | expected index |
|---|---|---|---|
| S1 | `[0,1000,2000]` | `0` | `0` |
| S2 | same | `800` | `1` (see note below) |
| S3 | same | `1_000` | `1` |
| S4 | same | `5_000` | `2` (lastIndex) |
| S5 | empty | any | `-1` |
| S6 | first time `1_000`, position `0` | `-1` |

Note for S2: the rule is `lines[i].time >= position + 300`. At position 800, the first line (0)
and second line (1000) are both below 1100, so the third line (2000) triggers the rule and the
function returns index 2 - 1 = 1.

### 14.5 Romanisation (`RomanizationTest`)

| # | call | expected |
|---|---|---|
| P1 | `katakanaToRomaji(null)` / `""` | `""` |
| P2 | `katakanaToRomaji("キャ")` | `"kya"` |
| P3 | `katakanaToRomaji("ッカ", "カ")` | `"kka"` |
| P4 | `katakanaToRomaji("ッ")` | `""` |
| P5 | `romanizeJapanese("こんにちは")` | `"konnichiha"` (Kuromoji readings, space-joined) |
| P6 | `romanizeKorean("안녕하세요")` | deterministic Revised Romanisation string pinned by fixture |
| P7 | `romanizeChinese("你好")` | `"ni hao"` |
| P8 | `romanizeChinese("你好，世界")` | `"ni hao，shi jie"` (space removed before full-width comma) |
| P9 | `romanizeCyrillic("Привет")` | `"Privet"` |
| P10 | `romanizeCyrillic("Привіт світ")` | `"Privit svit"` |
| P11 | `romanizeCyrillic("hello")` | `null` |
| P12 | `romanizeCyrillic("е")` | `null` |
| P13 | `romanizeHindi("हिन्दी")` | `"hindee"` |
| P14 | `romanizePunjabi("ਸਤ ਸ੍ਰੀ ਅਕਾਲ")` | `"st sree akaal"` |
| P15 | script detection matrix | as existing `LyricsUtilsTest.script detection` (Russian/Ukrainian/Hindi/Punjabi/Japanese/Korean/Chinese positive; `"こんにちは世界"` not Chinese; `"hello"` not Russian) |

### 14.6 Registry (`LyricsProviderRegistryTest`)

| # | case | expectation |
|---|---|---|
| G1 | `getDefaultProviderOrder()` | exact nine-element order of 5.1 |
| G2 | `deserializeProviderOrder("")` | default order |
| G3 | `deserializeProviderOrder("Kugou,Unknown,LrcLib,LrcLib")` | `["Kugou","LrcLib","LrcLib"]` |
| G4 | `orderedProviderNames("Kugou")` | `["Kugou", <remaining eight defaults in default order>]`, no duplicates |
| G5 | `serializeProviderOrder(["Kugou","Nope","LrcLib"])` | `"Kugou,LrcLib"` |
| G6 | `getOrderedProviders("Kugou")` | exactly one provider (`KuGou`), defaults **not** appended |
| G7 | `getProviderByName("YouTubeSubtitle")` | `YouTubeSubtitleLyricsProvider` |
| G8 | `getProviderByName("YouTube Subtitle")` | `null` |
| G9 | provider `name` values | `"YouTube Subtitle"` / `"YouTube Music"` for the two YouTube providers |
| G10 | `getDisplayName("Paxsenix")` / unknown | `"PaxSenix"` / input unchanged |
| G11 | `chooseProvider` outcomes | missing -> Pending; failed -> continue; unsynced beats failed; priority beats sync |
| G12 | `chooseProvider` all failed / empty order | None |

### 14.7 Orchestration (`LyricsHelperOrchestrationTest`, fake providers)

| # | scenario | expectation |
|---|---|---|
| O1 | provider A (priority 1) returns blank, B returns synced | B selected; cache holds B; A called once |
| O2 | A returns unsynced, B returns synced | A selected (priority dominates) |
| O3 | A delays 6 s (virtual), B returns synced after 10 ms, timeout 5 s | B not selected until A times out; final result B; total virtual elapsed 5 s |
| O4 | all providers fail/throw | `(LYRICS_NOT_FOUND, "Unknown")`; no exception escapes |
| O5 | offline supplier false | zero provider calls; `(LYRICS_NOT_FOUND, "Unknown")` |
| O6 | two concurrent `getLyrics` for the same id | provider fetch executes once; both callers get the same result |
| O7 | second call after first success | cache hit; zero additional provider calls; works offline |
| O8 | `getLyrics` called while `getAllLyrics` job is running | alternate job is cancelled from the first step |
| O9 | caller cancels while awaiting | caller gets `CancellationException`; shared fetch still completes and populates cache |
| O10 | enabled provider list empty | `(LYRICS_NOT_FOUND, "Unknown")` |
| O11 | getAllLyrics: two providers emit 2 and 3 pages | 5 callbacks in arrival order; cache holds 5 in same order |
| O12 | getAllLyrics cache hit | callbacks replay cached list synchronously; zero provider calls |
| O13 | getAllLyrics offline | zero callbacks; cache untouched |
| O14 | getAllLyrics second call with same artist/title but different spacing | same cache key (`spaces removed`) |
| O15 | getAllLyrics job cancelled after first page | no further callbacks after the provider observes cancellation; `cancelCurrentLyricsJob()` idempotent |
| O16 | provider `getAllLyrics` throws after one page | other providers still complete; exception reported, not propagated |
| O17 | LRU capacity | 21 distinct song ids -> oldest entry evicted |

The timeout test uses the internal `providerTimeoutMs` seam; concurrency uses the internal
`fetchScope`/providers seam. No Android provider client is called.

### 14.8 Translation orchestration (`LyricsTranslationHelperTest`, Robolectric)

Reset state before each test: `cancelTranslation()`, `clearCache()`, `resetStatus()`,
`setCompositionActive(true)`, and clear all entry flows.

| # | scenario | expectation |
|---|---|---|
| X1 | cache hit for matching text/mode/language | no HTTP; flows assigned; `hasActiveTranslations` true; DB upsert called with `onlyIfEmpty` semantics; status Success then Idle after 3 s |
| X2 | cached list shorter than non-blank entries | falls through to translation (`applyCachedTranslations` false) |
| X3 | blank API key | `Error(ai_error_api_key_required)`; no HTTP |
| X4 | empty lyrics list | `Error(ai_error_no_lyrics)` |
| X5 | all entries blank | `Error(ai_error_lyrics_empty)` |
| X6 | blank target language, no cache | `Error(ai_error_language_required)` |
| X7 | model returns fewer lines | missing entries stay `null`, earlier ones assigned |
| X8 | model returns more lines | surplus discarded, all entries assigned |
| X9 | translation delegate returns `Result.success(emptyList())` (internal seam) | `Error(ai_error_unexpected)`; translatedLines cache contains the empty list (baseline order) |
| X10 | translation exceeds 45 s (virtual) | `Error(ai_error_translation_failed)` |
| X11 | error while composition inactive | status remains `Translating()` (reportError no-op) |
| X12 | success while composition inactive | result is discarded before cache/persist/apply; status stays `Translating()`; `hasActiveTranslations` unchanged |
| X13 | `clearTranslations` | entity copy has three empty strings |
| X14 | `loadTranslationsFromDatabase` with matching entity + trailing newline | lines assigned; trailing empty line mapped; cache set; active true |
| X15 | mismatch language or mode | all flows null; active false |
| X16 | null entity / blank stored lyrics | all flows null; active false |
| X17 | status logs | `onLog` messages appear in `Translating.logs` only while Translating |
| X18 | `triggerClearTranslations` | active false; clear flow emits one item |
| X19 | cancellation during translation | no error status; job cancelled; status Idle after `cancelTranslation()` |

### 14.9 AI HTTP (`AiServiceHttpTest`, MockEngine)

| # | scenario | expectation |
|---|---|---|
| H1 | DeepL blank text | failure message `Input text is empty`; zero requests |
| H2 | DeepL 200 `deepl_success.json` | one request; URL contains `api-free` iff key ends `:fx`; parse list normalised to line count |
| H3 | DeepL 403 `deepl_403.json` | exactly **one** request (no retry); failure message contains `Translation failed:` and the body message |
| H3b | 500 on attempt 1, 403 on attempt 2 | exactly **two** requests; the 403 terminates immediately with the body message (no third attempt) |
| H4 | DeepL 500 three times | exactly `maxRetries` requests; then `Max retries exceeded`; delays grow 1 s, 2 s, 3 s (virtual) |
| H5 | DeepL 200 with `translations: []` | retried, then max-retries failure |
| H6 | DeepL response missing some `text` | those lines become `""`; count normalised |
| H7 | `deeplLanguageCode` table | `zh-tw->ZH`, `en-US->EN-US`, `en-gb->EN-GB`, `pt-BR->PT-BR`, `ja-JP->JA`, `de->DE` |
| H8 | Mistral 200 `chat_success.json` | one request; body has `model` default when blank, user-only message, temperature 0.3, max_tokens `lines*100` |
| H9 | Mistral 403 | one request, failure with `error.message` |
| H10 | Mistral 200 unparseable content | retried (no explicit delay), then `Max retries exceeded` |
| H11 | OpenRouter unsafe URL (`file://`, metadata host, `not a url`) | failure `SecurityException` with message `Invalid or restricted endpoint destination`; zero requests |
| H12 | OpenRouter 200 `chat_success.json` | `choices[0].message.content` extracted; lines normalised |
| H13 | Anthropic URL + `chat_anthropic.json` | `x-api-key` + `anthropic-version` headers; top-level `system`; concatenated `content[].text` |
| H14 | fenced/prose output `chat_fenced.json` | recovered and normalised |
| H15 | unknown extra JSON fields | ignored everywhere; parsing succeeds |
| H16 | `parseLines` missing lines | padded with `""` |
| H17 | `parseLines` surplus lines | truncated to expected |
| H18 | `parseLines` non-string elements (`[1, null, {"a":1}, "x"]`) | `["1","","","x"]` for expected 4 |
| H19 | `isUrlSafe` matrix | blank true; `https://openrouter.ai/...` true; `http://localhost:11434/...` true; `file:///etc/passwd` false; metadata hosts false |
| H20 | `testConnection` success with empty content | `"Connection OK"` |
| H21 | `testConnection` non-success | failure with extracted error message; no retry |
| H22 | timeout/socket failure | non-cancellation exception retried up to maxRetries then failure |
| H23 | cancellation | `CancellationException` propagates; zero further retries |

### 14.10 Streaming (`OpenRouterStreamingServiceTest`)

| # | scenario | expectation |
|---|---|---|
| Z1 | `sse/stream.txt` (`data:` frames with deltas `["a",` then `"b"]`, then `[DONE]`) | `Content` chunks emitted in order; terminal `Complete(["a","b"])`; line array normalised |
| Z2 | non-`data:` lines, comments, blank lines | ignored; no error |
| Z3 | malformed JSON frame between valid frames | ignored; subsequent valid deltas still emitted; `Complete` at end |
| Z4 | `error.txt` HTTP 403 `{"error":{"message":"Forbidden"}}` | single `Error("Translation failed: Forbidden")`; no retry |
| Z5 | 200 with no deltas | no terminal chunk (only zero Content); helper reports `No translation received` |
| Z6 | deltas that never form valid JSON array | `Error("Failed to parse translation")` |
| Z7 | blank text | `Error("Input text is empty")`; zero requests |
| Z8 | unsafe URL | `Error("Invalid or restricted endpoint destination")`; zero requests |
| Z9 | collector cancels after first chunk | HTTP call cancelled; no further emissions |
| Z10 | partial scanner: `["a\", b"]` | escapes preserved raw; only completed strings emitted |
| Z11 | request body | `stream=true`, system+user messages, model only when non-blank, `Authorization: Bearer` when key non-blank |

### 14.11 Cross-cutting invariants

| # | invariant |
|---|---|
| C1 | `parseLyrics` never throws for any fixture (including malformed LRC/TTML, NULs, lone surrogates, 4 MiB input) |
| C2 | No provider failure or parse failure ever escapes `LyricsHelper`/`LyricsTranslationHelper` as an exception (cancellation excepted) |
| C3 | No test performs real network I/O; request counts are asserted |
| C4 | All JSON parsing uses kotlinx.serialization with lenient + ignoreUnknownKeys |
| C5 | All tests pass with `:lyrics:test` only (no device/instrumented tests) |

---

## 15. Non-Goals and Behaviour to Preserve

- No sequential provider fallback for `getLyrics`; all providers start in parallel and priority
  (not sync quality) decides.
- No application of the LRC `[offset:]` tag; user offset is applied by the app.
- No caching of failed results; no invalidation of the LRU cache on settings changes.
- No pagination token: alternate enumeration is the continuation mechanism.
- `LyricsProvider.getAllLyrics`'s default implementation is a single `getLyrics` success.
- The two YouTube providers are always enabled and have no toggle.
- Streaming uses the OpenAI request shape even for Anthropic-style base URLs; non-streaming
  `OpenRouterService` has Anthropic detection.
- `MistralService.testConnection` does not exist; `DeepLService.testConnection` maps any success
  to a fixed message.
- Translation cache keys may be hash-derived; collisions are accepted.
- The alternate-results cache key removes only literal space characters from
  `"$artists-$title"`, not all whitespace.
- `LyricsEntry.compareTo` uses the `Int`-truncated time difference.
- `isJapanese` accepts CJK-only text; callers exclude Chinese with `!isChinese`.
- `romanizeCyrillic` returns `null` for non-Cyrillic or ambiguous single-`е` input.

## 16. Known Hazards for the Implementer

1. **Provider timeouts vs selection**: a slow top-priority provider delays every result for up to
   5 s even when a lower-priority provider already answered. This is intended; do not add early
   selection of lower-priority results, or priority semantics break.
2. **Shared fetch vs caller cancellation**: a caller's cancellation stops only its wait; the shared
   fetch continues. Tests pin both sides of this asymmetry.
3. **Android/unit-test boundary**: the helper depends on Android facilities; tests must use the
   injected seams of 7.7 and Robolectric rather than android.jar stubs.
4. **TTML has two parsers**: `lyrics-core` for `parseLyrics`, `:betterlyrics` for Unison. Both
   must be treated as untrusted-input parsers, and the new size/depth caps live in `parseLyrics`.
5. **Translation helper is global mutable state**: tests and production share it; always reset it
   between tests and on screen disposal.
6. **Line counts depend on `String.lines()`**: an empty string yields `[]`; a trailing newline
   yields a trailing empty element. Fixtures must encode this explicitly.
