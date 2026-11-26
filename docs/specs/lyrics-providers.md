# Clean-Room Specification — Lyrics Provider Modules (`:lrclib`, `:kugou`, `:betterlyrics`, `:simpmusic`, `:youlyplus`, `:paxsenixlyrics`, `:unison`)

Status: M2 specification (behavioural contract only).
Cluster: `lyrics-providers`.
Clean-room namespaces (must be preserved exactly):

| Module | Gradle path | Directory | Namespace(s) |
|---|---|---|---|
| LrcLib client | `:lrclib` | `lrclib` | `com.revenge.lrclib`, `com.revenge.lrclib.models` |
| KuGou client | `:kugou` | `kugou` | `com.revenge.kugou`, `com.revenge.kugou.models` |
| BetterLyrics client | `:betterlyrics` | `betterlyrics` | `com.revenge.betterlyrics`, `com.revenge.betterlyrics.models` |
| SimpMusic client | `:simpmusic` | `simpmusic` | `com.revenge.simpmusic`, `com.revenge.simpmusic.models` |
| YouLyPlus client | `:youlyplus` | `youlyplus` | `com.revenge.youlyplus`, `com.revenge.youlyplus.models` |
| Paxsenix client | `:paxsenixlyrics` | `paxsenixlyrics` | `com.revenge.paxsenix`, `com.revenge.paxsenix.models` |
| Unison client | `:unison` | `unison` | `com.revenge.unison`, `com.revenge.unison.models` |

This document describes observable behaviour and API shape. It intentionally omits statement-level
implementation. Any implementation that satisfies the contracts and acceptance tests is acceptable.
Do not copy implementation bodies from the baseline; implement from this contract.

---

## 1. Purpose and scope

Each module is an independent, process-wide client for one third-party lyrics service. Modules do
not know about each other except:

- `:lrclib`, `:paxsenixlyrics` and `:unison` call `com.revenge.metadata.cleaner.MetadataCleaner`.
- `:paxsenixlyrics` calls `com.revenge.betterlyrics.TTMLParser`.
- `:lyrics` consumes all seven modules through seven `LyricsProvider` adapters and races them.

Providers return plain `String` lyric documents (LRC, the app's enhanced LRC dialect, or plain
text) or a Kotlin `Result<String>`. No provider returns a parsed model to callers and no provider
caches lyric text itself. All cross-provider caching, timeouts above the provider's own HTTP
timeout, and precedence live in `:lyrics` (Section 6).

The seven provider modules are Android libraries except `:unison`, which is a JVM library.

---

## 2. Build and dependency contract

All modules use Kotlin JVM toolchain 21. Android modules use `com.android.library`,
`compileSdk = 37`, `minSdk = 26`, Java 21 source/target, core-library desugaring enabled, and
`kotlinx.serialization` plugin.

| Module | Project deps | Ktor engine | Other external deps |
|---|---|---|---|
| `:lrclib` | `:metadata` | CIO | client-core, okhttp (declared), content-negotiation, serialization-json, encoding, desugaring |
| `:kugou` | none | OkHttp | client-core, content-negotiation, serialization-json, encoding, desugaring |
| `:betterlyrics` | none | CIO | client-core, content-negotiation, serialization-json, desugaring |
| `:simpmusic` | none | CIO | client-core, content-negotiation, serialization-json, desugaring |
| `:youlyplus` | none | OkHttp | client-core, content-negotiation, serialization-json, desugaring |
| `:paxsenixlyrics` | `:betterlyrics`, `:metadata` | OkHttp | client-core, content-negotiation, serialization-json, encoding, Timber, desugaring |
| `:unison` | `:metadata` | OkHttp | client-core, content-negotiation, serialization-json, encoding (JVM plugin, no Android) |

Every module uses JUnit 4 for tests. `:unison` and `:betterlyrics` tests are plain JVM tests.
`:paxsenixlyrics` is an Android library because `Paxsenix.init(Context)` reads the package version.

`HttpClient` instances are created lazily and reused for the process lifetime. All engines must be
thread-safe and multiplex concurrent calls.

---

## 3. Common provider contract

### 3.1 Return shape and error taxonomy

- Single-result entry points return `kotlin.Result<String>` and **never throw** for expected
  failures (network, HTTP status, JSON, empty content). `CancellationException` is always
  rethrown and is never converted to `Result.failure`.
- Multi-result entry points (`getAllLyrics` / `getAllPossibleLyricsOptions`) return `Unit` and
  deliver values through a `callback: (String) -> Unit` invoked on the caller's coroutine. They
  never return a `Result`.
- A provider-level failure is `Result.failure(IllegalStateException(message))` with the exact
  messages specified per provider. Transport/parse exceptions may surface as the failure cause
  where the contract says so.
- Success means a non-blank lyric document. Providers that can return a blank document must not
  treat `""` as success.
- No provider retries the same URL, backs off, or loops on a 403/429. A non-2xx response is a
  failure of that request; the caller (or the provider's own mirror/strategy fallback) decides
  what comes next.

### 3.2 Cancellation

Every public `suspend` function observes structured cancellation. Catch blocks for ordinary
errors must rethrow `kotlin.coroutines.cancellation.CancellationException` (or
`kotlinx.coroutines.CancellationException` — same type). Parallel fan-out (`:youlyplus`,
`:paxsenixlyrics`, `:unison` search) must cancel sibling jobs in `finally` when the caller is
cancelled or a winner is chosen.

### 3.3 JSON decoding rules

All DTO models are `@Serializable` Kotlin `data class`es. Every client's `Json` instance sets
`ignoreUnknownKeys = true` and `isLenient = true`. Additional flags:

| Client | Flags |
|---|---|
| LrcLib | `isLenient`, `ignoreUnknownKeys` |
| KuGou | `ignoreUnknownKeys`, `isLenient` |
| BetterLyrics | `isLenient`, `ignoreUnknownKeys` |
| SimpMusic | `isLenient`, `ignoreUnknownKeys`, `explicitNulls = false` |
| YouLyPlus | `isLenient`, `ignoreUnknownKeys` |
| Paxsenix | `isLenient`, `ignoreUnknownKeys` |
| Unison | `isLenient`, `ignoreUnknownKeys`, `coerceInputValues = true` |

Consequences that are part of the contract:

- Unknown fields are ignored everywhere.
- A DTO field with a Kotlin default is optional on the wire; missing or explicit `null` (for a
  nullable field) decodes to its default/null. A required field with no default makes the whole
  document fail decoding; clients that swallow decode errors then report "no result".
- No client validates `status`/`errcode` numeric fields; they are informational only, except
  where this spec explicitly says otherwise.

### 3.4 HTTP timeout table

| Client | request | connect | socket | Expect success |
|---|---|---|---|---|
| LrcLib | 15 000 ms | 10 000 ms | 15 000 ms | yes |
| KuGou | 20 000 ms | 15 000 ms | 20 000 ms | yes |
| BetterLyrics | 15 000 ms | 10 000 ms | 15 000 ms | no (explicit status check) |
| SimpMusic | 15 000 ms | 10 000 ms | 15 000 ms | no (explicit status check) |
| YouLyPlus | 8 000 ms | 3 000 ms | 8 000 ms | yes |
| Paxsenix | 5 000 ms | 4 000 ms | 5 000 ms | yes |
| Unison | 20 000 ms | 15 000 ms | 20 000 ms | no (explicit status check) |

`Expect success = yes` means a non-2xx status raises from the Ktor call and is caught by that
client's ordinary error handling. `Expect success = no` means the client inspects
`response.status` itself.

### 3.5 State and caching

- No provider caches lyric text or search results.
- `:youlyplus` keeps one process-wide `AtomicReference<String?>` of the last mirror that returned
  lyrics and promotes it to first position; this is the only provider-local "cache".
- `:paxsenixlyrics` caches its `HttpClient` and resolved app version after `init`; the lock is a
  one-time double-checked initialisation.
- All other clients cache only the lazily created `HttpClient`.

### 3.6 Normalisation responsibility

Metadata cleaning (`MetadataCleaner`) happens inside `:lrclib`, `:paxsenixlyrics` and `:unison`
only. `:kugou` applies its own title/artist scrubbing. `:betterlyrics`, `:simpmusic` and
`:youlyplus` send metadata untouched. No provider normalises text produced by the remote service
except where explicitly described (KuGou credit stripping, TTML-to-LRC, KPoe-to-LRC).

---

## 4. Provider specifications

### 4.1 `:lrclib` — `com.revenge.lrclib.LrcLib`

#### 4.1.1 Public surface

```kotlin
object LrcLib {
  suspend fun getLyrics(
    title: String,
    artist: String,
    duration: Int,
    album: String? = null,
  ): Result<String>

  suspend fun getAllLyrics(
    title: String,
    artist: String,
    duration: Int,
    album: String? = null,
    callback: (String) -> Unit,
  )

  suspend fun lyrics(artist: String, title: String): Result<List<Track>>

  @JvmInline
  value class Lyrics(val text: String) {
    val sentences: Map<Long, String>?
  }
}
```

Note the argument order of `lyrics(artist, title)` — artist first — which differs from every
other entry point.

Constants (observable): base URL `https://lrclib.net`, search path `/api/search`, at most
5 alternate lyrics, unsynchronised-only failure message `"Lyrics unavailable"`.

#### 4.1.2 DTO `com.revenge.lrclib.models.Track`

`@Serializable`; field order and types are the wire contract:

| Field | Type | Default | Wire name |
|---|---|---|---|
| `id` | `Int` | required | `id` |
| `trackName` | `String` | required | `trackName` |
| `artistName` | `String` | required | `artistName` |
| `duration` | `Double` | required | `duration` |
| `plainLyrics` | `String?` | `null` | `plainLyrics` |
| `syncedLyrics` | `String?` | `null` | `syncedLyrics` |

Unknown fields (`instrumental`, `lang`, …) are ignored. A missing/`null` required field makes that
search response undecodable; because search swallows decode errors, the search returns no tracks
rather than failing the request.

Also part of the test-visible surface:

```kotlin
internal const val DURATION_UNKNOWN = -1

internal fun List<Track>.bestMatchingFor(duration: Int): Track?
internal fun List<Track>.bestMatchingForRelaxed(duration: Int): Track?
internal fun List<Track>.bestMatchingFor(
  duration: Int,
  trackName: String? = null,
  artistName: String? = null,
): Track?
internal fun similarity(first: String, second: String): Double
```

#### 4.1.3 `getLyrics`

1. Clean `(title, artist)` with `MetadataCleaner.clean`. Use `toSearchQuery()` for search terms.
2. `findTracks` (below).
3. If `duration == -1`, choose `tracks.bestMatchingFor(-1, cleanTitle, cleanArtist)`; otherwise
   choose `tracks.bestMatchingForRelaxed(duration)`.
4. Return `matched.syncedLyrics ?: matched.plainLyrics`.
5. If there is no match or both lyric fields are null, throw
   `IllegalStateException("Lyrics unavailable")` internally and return `Result.failure` with it.
6. Any other non-cancellation exception is `Result.failure(e)`; cancellation is rethrown.

Synchronised lyrics are preferred over plain lyrics for the same track.

#### 4.1.4 Search widening (`findTracks`)

The cleaned title/artist and `cleaned.primaryArtist` drive these queries, in this exact order. The
first query whose result list contains at least one track with `syncedLyrics != null` **or**
`plainLyrics != null` wins; the usable subset of that query is returned. If none produce a usable
track, return an empty list.

1. `track_name = searchTitle, artist_name = primaryArtist, album_name = album`
2. Only if `primaryArtist != searchArtist`: same, with `artist_name = searchArtist`
3. `track_name = searchTitle`
4. `q = "$searchArtist $searchTitle"` after trimming
5. `q = searchTitle`
6. Only if `searchTitle != title.trim()`: `track_name = title.trim(), artist_name = artist.trim()`

Each query is `GET /api/search` on the base URL with the non-null parameters below. A transport,
status, or decode failure for one query yields an empty list for that query (cancellation is
rethrown). `album` is only sent when non-null (and null is sent for query 2 only when the first was
built with `album` — i.e. the same `album` argument is used for queries 1 and 2, and omitted from
3–6).

#### 4.1.5 Matching

`bestMatchingFor(duration)`:

- empty list → `null`;
- `duration == -1` → first track with `syncedLyrics != null`, else the first track;
- otherwise the track closest by `abs(track.duration.toInt() - duration)`, but only if the distance
  is `<= 2` seconds; else `null`.

`bestMatchingForRelaxed(duration)`:

- empty list → `null`;
- `duration == -1` → same as above;
- keep tracks within `<= 5` seconds of `duration`; among those prefer the closest track with
  `syncedLyrics != null`, otherwise the closest track; `null` when none are in range.

`bestMatchingFor(duration, trackName?, artistName?)`:

- empty list → `null`;
- `duration == -1` and both names non-null → `findBestMatch`: score every track as
  `(similarity(title, track.trackName) + similarity(artist, track.artistName)) / 2.0`, require the
  average `> 0.6`, add `0.1` if the track has synced lyrics, take the maximum total (ties keep the
  first encountered); `null` if none pass;
- `duration == -1` and names missing → first synced, else first;
- positive duration → `bestMatchingForRelaxed(duration)`.

`similarity(first, second)`:

- trim and lowercase both sides;
- equal (including both empty after trim) → `1.0`; exactly one empty → `0.0`;
- containment either way contributes `0.8`;
- Levenshtein-normalised score in `0..1` = `1 - distance / maxLength`;
- result is the larger of the containment and distance scores. Never throws.

#### 4.1.6 `getAllLyrics`

Never returns a value and never throws except cancellation. It emits at most 5 documents.

1. Run `findTracks` once.
2. If `duration == -1`: order tracks by descending
   `rank = (similarity(cleanTitle, track.trackName) + similarity(cleanArtist, track.artistName)) / 2 + (1.0 if syncedLyrics != null else 0.0)`;
   for each in order, stop after 5, call `callback` only for tracks with non-null `syncedLyrics`.
3. Otherwise order by ascending `abs(track.duration.toInt() - duration)`; skip tracks farther than
   5 seconds; for each, call `callback` with `syncedLyrics` if non-null, and call `callback` with
   `plainLyrics` at most once across the whole call when non-null; stop at 5 delivered.
4. Call `currentCoroutineContext().ensureActive()` once per candidate so cancellation is observed
   between callbacks.

Callbacks run synchronously inside the caller's coroutine; they may be invoked zero times.

#### 4.1.7 `lyrics(artist, title)`

Returns `Result.success(findTracks(artist, title, album = null))`. Non-cancellation failure from
`findTracks` is impossible in practice because search swallows errors, but any escaping exception
becomes `Result.failure`; cancellation is rethrown. The list may be empty (success).

#### 4.1.8 `LrcLib.Lyrics.sentences`

`sentences` parses the wrapped text into a `Map<Long, String>` keyed by start time in milliseconds.
Contract:

- Always contains `0L -> ""`.
- A line contributes one entry per timestamp matched by
  `\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]` (minutes, seconds, optional 1–3 digit fraction; the
  separator may be `.` or `:`).
- The line content is everything after the **last** timestamp match; leading text remains part of
  the content.
- Fraction scaling: no fraction → 0 ms; 1 digit → ×100; 2 digits → ×10; 3+ digits → first three
  digits as milliseconds.
- Time = `minutes*60_000 + seconds*1_000 + millis`; minutes are not clamped.
- Lines without a timestamp are ignored. Duplicate timestamps: the later parsed line overwrites
  the earlier value.
- An unexpected runtime failure yields `null` (wrapped in `runCatching`); empty input yields
  `mapOf(0L to "")`, not `null`.

`LrcLib.Lyrics` is an inline value class; `text` is public.

---

### 4.2 `:kugou` — `com.revenge.kugou.KuGou`

#### 4.2.1 Public surface

```kotlin
object KuGou {
  var useTraditionalChinese: Boolean

  suspend fun getLyrics(
    title: String,
    artist: String,
    duration: Int,
    album: String? = null,
  ): Result<String>

  suspend fun getAllPossibleLyricsOptions(
    title: String,
    artist: String,
    duration: Int,
    album: String? = null,
    callback: (String) -> Unit,
  )

  suspend fun getLyricsCandidate(
    keyword: Keyword,
    duration: Int,
  ): SearchLyricsResponse.Candidate?

  suspend fun searchSongs(keyword: Keyword): SearchSongResponse

  fun generateKeyword(title: String, artist: String, album: String? = null): Keyword
}

internal fun buildSearchQuery(keyword: Keyword): String
internal val LRC_LINE_PATTERN: Regex
internal val LRC_CREDIT_PATTERN: Regex
internal fun String.normalizeLyrics(): String
```

Constants: song search `https://mobileservice.kugou.com/api/v3/search/song`, lyric search
`https://lyrics.kugou.com/search`, lyric download `https://lyrics.kugou.com/download`; page size
`8`; duration tolerance `8` seconds; credit scan limit `30`.

`useTraditionalChinese` is a mutable process-wide flag kept for the app's locale toggle. The
reference protocol returns the same simplified-Chinese document regardless, so it does not change
any output without an OpenCC-style converter; implement it as a no-op value that callers may set
and read. It must exist with that name and type.

#### 4.2.2 DTOs (`com.revenge.kugou.models`)

Wire formats must match exactly.

`Keyword` (not serializable): `data class Keyword(val title: String, val artist: String, val album: String? = null)`.

`SearchSongResponse` — `@Serializable`:

| Field | Type | Default |
|---|---|---|
| `status` | `Int` | `0` |
| `errcode` | `Int` | `0` |
| `error` | `String` | `""` |
| `data` | `Data` | `Data()` |

`SearchSongResponse.Data` — `@Serializable`: `info: List<Info> = emptyList()`.
`SearchSongResponse.Data.Info` — `@Serializable`: `duration: Int = 0`, `hash: String = ""`.

`SearchLyricsResponse` — `@Serializable`:

| Field | Type | Default |
|---|---|---|
| `status` | `Int` | `0` |
| `info` | `String` | `""` |
| `errcode` | `Int` | `0` |
| `errmsg` | `String` | `""` |
| `expire` | `Int` | `0` |
| `candidates` | `List<Candidate>` | `emptyList()` |

`SearchLyricsResponse.Candidate` — `@Serializable`: `id: Long = 0`,
`@SerialName("product_from") productFrom: String = ""`, `duration: Long = 0`,
`accesskey: String = ""`. Note the snake-case wire name for `productFrom`.

`DownloadLyricsResponse` — `@Serializable`: `content: String` (required), Base64 text.

All fields optional except `content`; missing objects (`data`, `candidates`) decode to their
defaults. `errcode`/`status` are not validated.

#### 4.2.3 Request mapping

- Song search: `GET SONG_SEARCH_URL` with `version=9108`, `plat=0`, `pagesize=8`, `showtype=0`,
  `keyword=<buildSearchQuery(keyword)>`.
- The keyword must be appended as an already-encoded URL parameter with **literal spaces**, not
  `+` (`encodeURLParameter(spaceToPlus = false)` semantics).
- Lyric search by keyword: `GET LYRICS_SEARCH_URL` with `ver=1`, `man=yes`, `client=pc`,
  `duration=<duration*1000>` only when `duration != -1` (omitted for unknown), and the same
  keyword parameter.
- Lyric search by hash: same endpoint with `ver=1`, `man=yes`, `client=pc`, `hash=<hash>`.
- Download: `GET LYRICS_DOWNLOAD_URL` with `fmt=lrc`, `charset=utf8`, `client=pc`, `ver=1`,
  `id=<id>`, `accesskey=<accesskey>`.
- Single page only: `pagesize=8`, no page/continuation parameter, no follow-up request.
- The content-negotiation plugin is installed for `application/json`, `text/html` and
  `text/plain`, and gzip/deflate content encoding is enabled. KuGou sometimes labels JSON as HTML
  or plain text; all three must decode with the same `Json`.

#### 4.2.4 `generateKeyword`

Title normalisation: remove every match of each of these regexes, globally, in this order:
`\(.*\)`, `（.*）`, `「.*」`, `『.*』`, `<.*>`, `《.*》`, `〈.*〉`, `＜.*＞`.
The substitution is not DOTALL, so it applies within a line. This can leave surrounding spaces;
e.g. `"Song (Remastered)"` becomes `"Song "` (trailing space preserved).

Artist normalisation, in order:

1. replace `", "` with `"、"`;
2. replace `" & "` with `"、"`;
3. remove all `"."`;
4. replace `"和"` with `"、"`;
5. remove all `\(.*\)` and `（.*）`.

`album` is stored as given (not trimmed). `generateKeyword("T", "A", "  ")` keeps `"  "` in
`Keyword`; `buildSearchQuery` is what ignores a blank album.

#### 4.2.5 `buildSearchQuery`

`title + " - " + artist`, then if `album != null && album.isNotBlank()` append `" " + album`.
Examples: `Keyword("T","A")` → `"T - A"`; `Keyword("T","A","Album")` → `"T - A Album"`;
blank album → `"T - A"`.

#### 4.2.6 `getLyrics`

Wrap the whole flow in a `Result` that rethrows cancellation:

1. `keyword = generateKeyword(...)`.
2. `candidate = getLyricsCandidate(keyword, duration) ?: throw IllegalStateException("No lyrics candidate for ${keyword.title} - ${keyword.artist}")`.
3. Download and decode: `DownloadLyricsResponse.content`, decode with a **MIME** Base64 decoder
   (`Base64.getMimeDecoder()`), interpret bytes as UTF-8, run `normalizeLyrics()`.
4. Return `Result.success(text)`; any non-cancellation exception is `Result.failure`.

An empty normalised document is a successful `Result.success("")` (callers treat blank as a
failed provider outcome). Do not convert it to failure inside KuGou.

#### 4.2.7 `getLyricsCandidate`

1. `searchSongs(keyword)` (one page).
2. Iterate `data.info` in order; for each song where `duration == -1` **or**
   `abs(song.duration - duration) <= 8`, call `searchLyricsByHash(song.hash)` and return
   `candidates.firstOrNull()` if present. Continue on miss.
3. If no hash candidate was returned, call `searchLyricsByKeyword(keyword, duration)` and return
   `candidates.firstOrNull()` (may be `null`).

Network/status errors from `searchSongs`, `searchLyricsByHash` or `searchLyricsByKeyword`
propagate as exceptions (the HTTP client uses expect-success). `Duration` on `Info` is whole
seconds; `Candidate.duration` is also seconds and is not used for matching.

#### 4.2.8 `getAllPossibleLyricsOptions`

Ordered, potentially duplicate, best-effort emission. It is **not** wrapped in a `Result` and
network errors propagate as exceptions (cancellation included).

1. `searchSongs(keyword)`.
2. For each `info` where `duration == -1` or within 8 seconds: `searchLyricsByHash(hash)`,
   take `candidates.firstOrNull()`, download/decode and callback. (No dedupe against later steps.)
3. Then `searchLyricsByKeyword(keyword, duration)`: download/decode/callback **every** candidate
   in order.

Callback count is unbounded. A decode exception aborts the loop and propagates.

#### 4.2.9 `normalizeLyrics`

1. Split on any line terminator (`lines()`); keep only lines that fully match
   `^\[\d\d:\d\d\.\d{2,3}].*` (two-digit minutes/seconds, fraction of 2 or 3 digits, `.`
   separator). If none remain → return `""`.
2. Head cut: scan `i` from `min(30, lines.lastIndex)` down to `0`; the first line matching
   `.+].+[:：].+` (a timestamped line containing a second `:`/`：` after the timestamp) sets
   `headCut = i + 1`; break. If none, `headCut = 0`.
3. Tail cut: scan `i` from `min(lines.size - 30, lines.lastIndex)` down to `0`; if
   `lines[lines.lastIndex - i]` matches the credit regex, set `tailCut = i + 1`; break. If none,
   `tailCut = 0`.
4. Return `lines.drop(headCut).dropLast(tailCut).joinToString("\n")`.

Consequences: CRLF is normalised to `\n`; untimestamped metadata lines are removed; a credit
block within the first/last 30 timestamped lines is removed; the returned document always uses
`\n`.

---

### 4.3 `:betterlyrics` — `com.revenge.betterlyrics.BetterLyrics` and `TTMLParser`

#### 4.3.1 Public surface

```kotlin
object BetterLyrics {
  suspend fun getLyrics(
    title: String,
    artist: String,
    duration: Int,
    album: String? = null,
  ): Result<String>

  suspend fun getAllLyrics(
    title: String,
    artist: String,
    duration: Int,
    album: String? = null,
    callback: (String) -> Unit,
  )
}

object TTMLParser {
  data class ParsedWord(val text: String, val startTime: Double, val endTime: Double)

  data class ParsedLine(
    val text: String,
    val startTime: Double,
    val words: List<ParsedWord>,
    val agent: String? = null,
    val isBackground: Boolean = false,
    val backgroundLines: List<ParsedLine> = emptyList(),
  )

  fun parseTTML(ttml: String): List<ParsedLine>
  fun toLRC(lines: List<ParsedLine>): String
}
```

Base URL `https://lyrics-api.boidu.dev`.

#### 4.3.2 Request mapping

`GET $BASE_URL/getLyrics` with:

- `s = title` (always),
- `a = artist` (always),
- `d = duration` only when `duration > 0`,
- `al = album` only when `album != null && album.isNotBlank()`.

No metadata cleaning or case folding: the service matches exact metadata.

#### 4.3.3 `getLyrics`

1. Fetch TTML. Only HTTP `200 OK` is success; any other status → treat as unavailable. Transport,
   decode, and status errors are swallowed to `null` (cancellation rethrown).
2. `null` → failure `IllegalStateException("Lyrics unavailable")`.
3. `TTMLParser.parseTTML(ttml)`; empty list → failure
   `IllegalStateException("Failed to parse lyrics")`.
4. Success = `TTMLParser.toLRC(lines)` (may end with a trailing newline).

`getAllLyrics` calls `getLyrics(...).onSuccess(callback)`: the callback runs at most once, only on
success, and failures are silent.

#### 4.3.4 DTOs (`com.revenge.betterlyrics.models`)

- `TTMLResponse(val ttml: String)` — required; a response without `ttml` fails decoding.
- `SearchResponse(val results: List<Track>)` — declared for the search endpoint; not used by the
  client, but must exist exactly.
- `Track(title: String, artist: String, album: String? = null, duration: Double, lyrics: Lyrics? = null)`.
- `Lyrics(lines: List<Line>)`.
- `Line(text: String, startTime: Double, words: List<Word>? = null)`.
- `Word(text: String, startTime: Double, endTime: Double)`.

All are `@Serializable`. Unknown keys are ignored.

#### 4.3.5 `TTMLParser.parseTTML`

Guarantees: **never throws**; malformed XML, empty input, XXE attempts, or a document with no
usable paragraph all return `emptyList()`.

Parsing rules:

1. Build a namespace-aware DOM. Harden against XXE: disallow DOCTYPE declarations, disable
   external general and external parameter entities. Feature-setting failures must be tolerated
   (`runCatching`) so the parser still works on JVMs that reject a feature name.
2. Select every element with local name `p` in any namespace (`getElementsByTagNameNS("*","p")`).
3. Skip a `p` whose `begin` attribute is blank/absent.
4. Read `agent` from the paragraph: try namespace `http://www.w3.org/ns/ttml#metadata`, then the
   plain attribute, then `ttm:agent`, then any attribute whose local name is `agent`. Blank value
   → `null`.
5. Iterate element children in document order, considering only elements whose local name is
   `span` (case-insensitive, ignoring namespace prefix):
   - `role == "x-bg"` → parse as a **background line**.
   - `role == "x-translation"` or `role == "x-roman"` → ignore entirely.
   - otherwise → parse as a timed span.
6. A timed span requires non-blank `begin` and `end`; its text is `textContent.trim()` and must be
   non-empty. A span missing either time or text is dropped.
7. Spans are merged into words: a new span starts a new word when the **previous** span was
   followed by whitespace in the source (`endsWithWhitespace`: the next sibling is a text node
   containing whitespace, or there is no next sibling → `false`) **or** when the previous span does
   not exist. Adjacent spans with no whitespace between them are syllables of one word: their text
   is concatenated and the word runs from the first span's start to the last span's end.
8. Text: if merged words exist, join their text with single spaces; otherwise use the paragraph's
   direct text (text nodes plus non-background/non-ignored span text), trimmed.
9. An empty resulting text drops the line (`null`).

Background line (`role="x-bg"`):

- Its start time is the span's own non-blank `begin`, else the parent paragraph's start.
- Its word children are merged the same way (ignoring `x-translation`/`x-roman`); direct text is
  the fallback.
- `isBackground = true`, `agent = null`, no nested backgrounds.
- Empty text drops it.

The main `ParsedLine` always has `isBackground = false`.

#### 4.3.6 `TTMLParser.toLRC`

For each line, in input order:

1. Emit the main line: `[MM:SS.CC]` (zero-padded minutes, seconds, **centiseconds** =
   `(millis % 1000) / 10`, floor), followed by `{agent:<id>}` when `agent` is non-null and
   non-empty, followed by the text, followed by `\n`.
2. If `words` is non-empty, emit exactly one word track:
   `<text1:start1:end1|text2:start2:end2|...>\n`. Times are seconds as Kotlin `Double`
   `toString()` (e.g. `65.234`, `65.9`, `0.0`). No escaping.
3. Then, for each `backgroundLines` entry in order, emit the same two constructs with the prefix
   `{bg}` instead of an agent prefix.

`minutes` may exceed 99 (`[100:00.00]`). A line with empty words emits only the timestamp line.
No trailing marker is added beyond each line's `\n`.

---

### 4.4 `:simpmusic` — `com.revenge.simpmusic.SimpMusicLyrics`

#### 4.4.1 Public surface

```kotlin
object SimpMusicLyrics {
  suspend fun getLyricsByVideoId(videoId: String): List<LyricsData>
  suspend fun getLyrics(videoId: String, duration: Int = 0): Result<String>
  suspend fun getAllLyrics(
    videoId: String,
    duration: Int = 0,
    callback: (String) -> Unit,
  )
}

internal const val DURATION_TOLERANCE_SECONDS: Int  // 10
internal const val MAX_ALTERNATE_LYRICS: Int        // 5

internal fun LyricsData.durationDistance(targetSeconds: Int): Int
internal fun LyricsData.matchesDuration(targetSeconds: Int): Boolean
internal fun LyricsData.bestLyricsText(): String?
internal fun selectLyrics(tracks: List<LyricsData>, durationSeconds: Int): String?
internal fun collectAlternativeLyrics(tracks: List<LyricsData>, durationSeconds: Int): List<String>
```

Constants: primary `https://api-lyrics.simpmusic.org/v1/`, fallback
`https://vivi-yt-music-server.onrender.com/v1/`, failure message `"Lyrics unavailable"`.

#### 4.4.2 DTOs (`com.revenge.simpmusic.models`)

`LyricsData` — `@Serializable`:

| Field | Type | Default | Wire name |
|---|---|---|---|
| `id` | `String?` | `null` | `id` |
| `videoId` | `String?` | `null` | `videoId` |
| `title` | `String?` | `null` | `songTitle` |
| `artist` | `String?` | `null` | `artistName` |
| `album` | `String?` | `null` | `albumName` |
| `duration` | `Int?` | `null` | `durationSeconds` |
| `syncedLyrics` | `String?` | `null` | `syncedLyrics` |
| `plainLyrics` | `String?` | `null` | `plainLyric` (singular) |
| `richSyncLyrics` | `String?` | `null` | `richSyncLyrics` |
| `vote` | `Int?` | `null` | `vote` |

`SimpMusicApiResponse` — `@Serializable`: `type: String? = null`, `data: List<LyricsData> = emptyList()`;
computed `val success: Boolean get() = type == "success"` (not a wire field). Unknown keys ignored.

#### 4.4.3 Request mapping and fallback

`GET <baseUrl><videoId>` — base URL already ends with `/`, no extra separator. Headers:
`Accept: application/json`, `User-Agent: SimpMusicLyrics/1.0`.

`fetch(videoId, baseUrl)` contract:

- every non-`200` status → `null` (signals "try the next endpoint");
- `200` → decode `SimpMusicApiResponse`; `success == true` → `body.data`; otherwise `emptyList()`
  (the endpoint answered but has no lyrics — fallback must not run);
- transport/decode exception → `null` (cancellation rethrown).

`getLyricsByVideoId`: try primary; if it returns non-null (including an empty list), return it;
otherwise try fallback; if that is null return `emptyList()`. It never throws except cancellation.
This means a primary HTTP/transport failure falls through, but a primary `type != "success"` or an
empty successful payload does not.

#### 4.4.4 Selection

- `durationDistance(target) = abs((duration ?: 0) - target)`.
- `matchesDuration(target) = target <= 0 || distance <= 10`.
- `bestLyricsText` = first non-blank of `richSyncLyrics`, `syncedLyrics`, `plainLyrics`; `null`
  if all missing/blank.
- `selectLyrics(tracks, target)`: candidates = tracks matching duration; if `target > 0` pick the
  minimum distance (ties keep the earlier element); if `target <= 0` pick the first candidate;
  return `bestLyricsText()` or `null`.
- `collectAlternativeLyrics(tracks, target)`: order by distance when `target > 0` (stable sort),
  otherwise keep API order. For each matching track: append `richSyncLyrics` else `syncedLyrics`
  (non-blank); append `plainLyrics` at most once across the list (non-blank). Stop when
  `emitted.size > 4` (i.e. at most 5 outputs); a candidate can contribute its synced text and,
  immediately after, the single plain text. Blank values are skipped.

#### 4.4.5 Entry points

- `getLyrics(videoId, duration)` → `selectLyrics(...)`; null → failure
  `IllegalStateException("Lyrics unavailable")`; otherwise success. Cancellation rethrown.
- `getAllLyrics(videoId, duration, callback)` → `collectAlternativeLyrics(...).forEach(callback)`.
  No failure signal; zero callbacks when nothing matches. Never throws except cancellation.

---

### 4.5 `:youlyplus` — `com.revenge.youlyplus.YouLyPlus`

#### 4.5.1 Public surface

```kotlin
object YouLyPlus {
  suspend fun getLyrics(
    title: String,
    artist: String,
    duration: Int,
    album: String? = null,
    id: String? = null,
    isrc: String? = null,
  ): Result<String>

  suspend fun getAllLyrics(
    title: String,
    artist: String,
    duration: Int,
    album: String? = null,
    id: String? = null,
    isrc: String? = null,
    callback: (String) -> Unit,
  )
}

internal fun formatLrcTimestamp(timeMs: Long, syllable: Boolean = false): String
internal fun List<LyricsItem>.toLrc(): String?
internal fun promoteServer(last: String?, servers: List<String>): List<String>
```

Base servers, in order:

1. `https://lyricsplus.prjktla.my.id`
2. `https://lyricsplus.atomix.one`
3. `https://lyricsplus.binimum.org`
4. `https://lyricsplus.prjktla.workers.dev`
5. `https://lyricsplus-seven.vercel.app`
6. `https://lyrics-plus-backend.vercel.app`

All serve `GET /v2/lyrics/get`.

#### 4.5.2 Request mapping

For one server: `GET <server.trimEnd('/')>/v2/lyrics/get` with `title`, `artist`, `duration`
always; `album`, `id`, `isrc` only when non-null and non-blank. No cleaning.

#### 4.5.3 Mirror race

`promoteServer(last, servers)`: if `last` is null or not present in `servers`, return the input
list unchanged; otherwise return `[last] + servers without last` (no duplicates).

`getLyrics`:

1. Build the promoted server list.
2. In a `coroutineScope`, launch one job per server; each job fetches and sends
   `server to lrc?` into a `Channel` sized to the server count. A server error yields `null`
   (cancellation rethrown).
3. Receive up to `servers.size` results. The first non-null, non-blank LRC is stored as the last
   working server and returned. In `finally`, cancel all jobs and close the channel.
4. If no server yields usable lyrics, the scope throws
   `IllegalStateException("No lyrics found from any YouLyPlus server")`; `getLyrics` converts it to
   `Result.failure`. Cancellation of the caller cancels every in-flight request and is rethrown.

`getAllLyrics`:

1. Same promoted list and fan-out.
2. Receive exactly `servers.size` results (arrival order). For each non-null, non-blank LRC not
   already seen (exact string identity via a `HashSet`), set the last working server and invoke
   the callback. Duplicate documents are suppressed.
3. Cancel remaining jobs and close the channel in `finally`. Zero callbacks when all mirrors fail.
   Never throws except cancellation.

Concurrency notes: both functions keep at most `servers.size` live requests; results arriving after
the winner in `getLyrics` are ignored and their jobs cancelled. The `AtomicReference` update is
last-writer-wins and must not break correctness.

#### 4.5.4 Choosing the document

`LyricsResponse.bestLyrics()` = first non-blank of:

1. `syncedLyrics`,
2. `lyrics.toLrc()` (when the KPoe list exists and produces non-blank text),
3. `plainLyrics`.

#### 4.5.5 DTOs (`com.revenge.youlyplus.models`)

`LyricsResponse` — `@Serializable`: `id: Int? = null`, `syncedLyrics: String? = null`,
`plainLyrics: String? = null`, `lyrics: List<LyricsItem>? = null`, `type: String? = null`,
`trackName: String? = null`, `artistName: String? = null`, `albumName: String? = null`,
`duration: Double? = null`.

`LyricsItem` — `@Serializable`: `text: String? = null`, `time: Long? = null`,
`duration: Long? = null`, `syllabus: List<Syllable>? = null` (wire key is `syllabus`).

`Syllable` — `@Serializable`: `text: String? = null`, `time: Long? = null`,
`duration: Long? = null`, `isBackground: Boolean? = null`.

All times are milliseconds. The response may carry either shape (LRCLib-style or KPoe-style);
both must decode. `duration` is unused for matching.

#### 4.5.6 `toLrc` and `formatLrcTimestamp`

`formatLrcTimestamp(timeMs, syllable)`:

- clamp negative to `0`;
- `minutes = ms / 60_000` (not clamped to two digits in output), `seconds = (ms / 1000) % 60`,
  `millis = ms % 1000`;
- format with `Locale.ROOT`: `[%02d:%02d.%03d]` or `<%02d:%02d.%03d>` when `syllable = true`.
- Examples: `0` → `[00:00.000]`; `61_234` → `[01:01.234]`; `3_600_000` → `[60:00.000]`.

`List<LyricsItem>.toLrc()`:

- empty list → `null`; otherwise map each item to a line and drop any that produce `null`; if no
  lines remain → `null`; join with `\n` (no trailing newline).
- Per item:
  - line prefix = `formatLrcTimestamp(time ?: 0)`;
  - if any syllable has `isBackground == true`, append `{bg}`;
  - if `syllabus` is null or empty: append `text.orEmpty()` (so a textless item yields just the
    timestamp).
  - else build word groups: iterate syllables in order. A syllable whose text starts with a space
    flushes the current word before processing it. When the current buffer is blank, set the
    word's start time to `syllable.time ?: 0`. Append the syllable text. A syllable whose text ends
    with a space flushes the word after processing it. Flushing appends
    `formatLrcTimestamp(wordStart, syllable = true) + trimmedWord + " "` (a trailing space).
    After the loop flush once more, then `trimEnd()` the whole line.
  - A syllable text containing punctuation only remains part of the adjacent word unless the
    whitespace rules above split it.

---

### 4.6 `:paxsenixlyrics` — `com.revenge.paxsenix.Paxsenix` and `PaxsenixCore`

#### 4.6.1 Public surface

```kotlin
object Paxsenix {
  fun init(context: android.content.Context)

  suspend fun getLyrics(
    title: String,
    artist: String,
    duration: Int,
    album: String? = null,
  ): Result<String>

  suspend fun getAllLyrics(
    title: String,
    artist: String,
    duration: Int,
    album: String? = null,
    callback: (String) -> Unit,
  )
}

internal data class TrackTarget(
  val title: String,
  val cleanTitle: String,
  val cleanArtist: String,
  val durationSeconds: Int,
)

internal data class ScoredResult(val result: SearchResult, val score: Double)

internal data class LyricsCandidate(val lyrics: String, val hasWordTimings: Boolean)

internal object PaxsenixCore {
  const val MAX_RANKED_RESULTS: Int            // 10
  const val MAX_GET_LYRICS_CANDIDATES: Int     // 5
  const val MAX_ALL_LYRICS_CANDIDATES: Int     // 3

  fun searchQueries(cleanTitle: String, cleanArtist: String, album: String?): List<String>
  fun rank(results: List<SearchResult>, target: TrackTarget): List<ScoredResult>
  fun score(result: SearchResult, target: TrackTarget): Double
  fun hasWordTimings(text: String): Boolean
  fun buildLyrics(
    response: LyricsResponse,
    ttmlConverter: (String) -> String = ::convertTtmlToLrc,
  ): Result<LyricsCandidate>
  fun plainTextFromContent(content: List<LyricsContent>): String
  fun lrcFromContent(content: List<LyricsContent>): String
}
```

Base URL `https://lyrics.paxsenix.org`. Failure messages: `"No matching track found on Paxsenix"`,
`"No lyrics available from Paxsenix"`, and, from `buildLyrics`,
`"Paxsenix returned no lyrics"` / `"Paxsenix returned empty lyrics"`. Calling a fetch before
`init` must throw `IllegalStateException("Paxsenix.init() must be called before fetching lyrics")`
(caught by `getLyrics` into a failure).

#### 4.6.2 `init`

Idempotent and thread-safe (double-checked locking on a private lock). Reads
`packageManager.getPackageInfo(packageName, 0).versionName`, takes it when non-blank, otherwise
uses `"Unknown"`; a read error logs and uses `"Unknown"`. Creates the shared OkHttp client:

- base URL `https://lyrics.paxsenix.org`;
- `User-Agent: revenge/<version>`;
- timeouts 5 000 / 4 000 / 5 000 ms;
- `expectSuccess = true`;
- JSON as in Section 3.3.

Calling `init` again is a no-op (does not rebuild the client or re-read the version).

#### 4.6.3 Lookup flow

`getLyrics` (wrapped in a cancellation-safe `Result`):

1. `TrackTarget` = raw `title`, `durationSeconds = duration`, and
   `MetadataCleaner.clean(title, artist).toSearchQuery()` → `(cleanTitle, cleanArtist)`.
2. `findCandidates(target, album)`: iterate `searchQueries(cleanTitle, cleanArtist, album)` in
   order; for each, `rank(search(query), target)`; return the first non-empty ranked list; else
   empty. Search errors log and yield an empty list.
3. Empty ranked list → `IllegalStateException("No matching track found on Paxsenix")`.
4. `awaitBestLyrics(ranked, MAX_GET_LYRICS_CANDIDATES)`; null → throw
   `"No lyrics available from Paxsenix"`.

`getAllLyrics`:

1. `findCandidates(target, album = null)` (the fast path; album is deliberately not used).
2. On failure or empty → log and return (no callback).
3. `awaitBestLyrics(ranked, MAX_ALL_LYRICS_CANDIDATES)`; if non-null call `callback` exactly once;
   null → return.

It never throws except cancellation (all failures are contained and logged through Timber).

#### 4.6.4 Search and fetch requests

- `GET /apple-music/search?q=<query>` → `SearchResponse`, which is
  `typealias SearchResponse = List<SearchResult>` (a bare JSON array). Errors → empty list.
- `GET /apple-music/lyrics?id=<id>` → `LyricsResponse`; then
  `PaxsenixCore.buildLyrics(response, ::convertTtmlToLrc).getOrThrow()`. The converter runs
  `TTMLParser.toLRC(TTMLParser.parseTTML(ttml))`, returns `""` on failure, and logs.

#### 4.6.5 `awaitBestLyrics(ranked, limit)`

Concurrent candidate fetch with early exit:

1. Take at most `limit` ranked results and `async`-fetch each.
2. Wait for completions with `select { job.onAwait { it } }`; remove completed jobs from the
   pending list.
3. Skip failed fetches and candidates whose lyrics are blank.
4. The first result with `hasWordTimings == true` wins immediately (returned).
5. Otherwise remember the first non-blank result as a fallback and continue until every candidate
   has completed.
6. Return the fallback (possibly null).
7. In `finally`, cancel all jobs (winners included), so sibling requests stop.

#### 4.6.6 DTOs (`com.revenge.paxsenix.models`)

All `@Serializable`; unknown keys ignored.

`SearchResult`: `id: String` (required), `songName: String? = null`, `trackName: String? = null`,
`artistName: String? = null`, `albumName: String? = null`, `duration: Int? = null` (milliseconds),
`artwork: String? = null`. Computed (non-wire): `displayName = trackName ?: songName ?: ""`,
`displayArtist = artistName ?: ""`.

`LyricText`: `text: String` (required), `timestamp: Long`, `endtime: Long`, `duration: Long`,
`part: Boolean = false`.

`LyricsContent`: `timestamp: Long`, `endtime: Long`, `duration: Long`,
`structure: String? = null`, `text: List<LyricText> = emptyList()`, `background: Boolean = false`,
`backgroundText: List<LyricText> = emptyList()`, `oppositeTurn: Boolean = false`.
`timestamp`/`endtime`/`duration` are required (no defaults), so a content element lacking them
fails decoding.

`LyricsMetadata`: `songwriters: List<String> = emptyList()`.

`LyricsResponse`: `type: String? = null`, `metadata: LyricsMetadata? = null`,
`content: List<LyricsContent> = emptyList()`, `elrc: String? = null`,
`elrcMultiPerson: String? = null`, `ttmlContent: String? = null`, `plain: String? = null`.

#### 4.6.7 `PaxsenixCore` matching

`searchQueries(cleanTitle, cleanArtist, album)`:

1. `combined = non-blank [cleanTitle, cleanArtist] joined by " "`;
2. append `cleanTitle`;
3. if album non-blank, append non-blank `[cleanTitle, cleanArtist, album]` joined by `" "`;
4. filter blank, `distinct()` preserving first occurrences.

Examples: `("Song","Artist","Album")` → `["Song Artist","Song","Song Artist Album"]`;
`("Song","Artist",null)` → `["Song Artist","Song"]`;
`("Song",""," ")` → `["Song"]`.

`score(result, target)`:

- If `result.duration != null`, `diff = abs(duration - durationSeconds*1000)`; add
  `+100` if `diff <= 2000`, `+50` if `<= 5000`, `+10` if `<= 10000`, else `-50`.
- Compare `normalizedTitle(target.cleanTitle)` with `normalizedTitle(result.displayName)` where
  normalisation removes `\s*\([^)]*\)` and `\s*\[[^\]]*\]`, lowercases and trims: exact → `+80`;
  either contains the other → `+40`.
- If result name contains `"mixed"` (case-insensitive) and the raw target title does not → `-60`.
- If result name contains `"remix"` and the raw target title does not → `-40`.
- Artist: if `target.cleanArtist.lowercase().trim()` is non-empty: `+50` when
  `result.displayArtist.lowercase()` contains it; else `+25` when any whitespace-separated target
  artist token of length `> 2` is contained.

`rank`: drop results with blank `id`; score; keep `score > 0.0`; sort by descending score (stable
for ties); take 10.

`hasWordTimings(text)`: `Regex("<[^>]+>").containsMatchIn(text)`.

#### 4.6.8 `PaxsenixCore.buildLyrics`

Precedence, first usable wins:

1. `ttmlContent` non-blank → convert; if converted non-blank, `LyricsCandidate(lrc, hasWordTimings(lrc))`.
2. `elrcMultiPerson` non-blank → candidate with its word-timing flag.
3. `elrc` non-blank → candidate with its word-timing flag.
4. `plain` non-blank → `LyricsCandidate(plain, false)` (no word-timing detection).
5. Otherwise use `content`:
   - if `content.isEmpty()` → failure `"Paxsenix returned no lyrics"`;
   - if `type.equals("Syllable", ignoreCase = true)` → `lrcFromContent`; blank → failure
     `"Paxsenix returned empty lyrics"`, else `(lrc, true)`;
   - else → `plainTextFromContent`; blank → failure `"Paxsenix returned empty lyrics"`, else
     `(text, false)`.

`plainTextFromContent`: for each content line join its words with `" "`, drop blank line texts,
join remaining lines with `\n`.

`lrcFromContent`: for each line:

- `lineText` = words joined by `" "`; skip blank lines;
- append `formatTimestamp(line.timestamp)` = `[MM:SS.CC]` with centiseconds (Locale.US, clamp
  negative to 0) + agent tag + `lineText` + `\n`;
- append a word track when any word is non-blank:
  `<word1:start1:end1|word2:start2:end2>\n`, where each start/end is `timestamp / 1000.0`
  seconds (Kotlin `Double` text).
- Agent tag priority: `background == true` → `{bg}`; else `oppositeTurn == true` → `{agent:v2}`;
  else `{agent:v1}`. `backgroundText` is not emitted.

Example: one line at `61_500 ms`, words `Hello` (61 500–61 800) and `world` (61 800–62 000),
`oppositeTurn` → `"[01:01.50]{agent:v2}Hello world\n<Hello:61.5:61.8|world:61.8:62.0>\n"`.

---

### 4.7 `:unison` — `com.revenge.unison.Unison`

#### 4.7.1 Public surface

```kotlin
object Unison {
  var logger: ((String) -> Unit)?

  suspend fun getLyrics(
    videoId: String? = null,
    title: String,
    artist: String,
    album: String? = null,
    durationSeconds: Int = -1,
  ): Result<String>

  suspend fun getAllLyrics(
    videoId: String? = null,
    title: String,
    artist: String,
    album: String? = null,
    durationSeconds: Int = -1,
    callback: (String) -> Unit,
  )

  internal fun parseSingle(body: String): UnisonEntry?
  internal fun parseSearch(body: String): List<UnisonEntry>
}
```

Base URL `https://unison.boidu.dev/`; maximum search results emitted `5`.

`logger` is an optional diagnostic sink (`null` by default) invoked from network coroutines with
short human-readable strings. It must never be required for correctness and a throwing logger must
be allowed to propagate (no special handling is required, but do not invoke it in a way that
swallows the response).

#### 4.7.2 Request mapping

Relative to the base URL:

- `GET lyrics?v=<videoId>` for a video-id lookup.
- `GET lyrics` with `song=<title>`, `artist=<artist>`, optional `album=<trimmed non-empty>`, and
  `duration=<seconds>` only when `durationSeconds > 0`.
- `GET lyrics/search` with the same metadata parameters.

Non-success status → `null`; transport/read error → `null` (cancellation rethrown); success →
body text.

#### 4.7.3 `getLyrics`

1. `require(title.isNotBlank() && artist.isNotBlank()) { "Song title and artist are required" }`.
   The thrown `IllegalArgumentException` becomes `Result.failure`.
2. `fetchEntry`:
   a. If `videoId` is non-blank, try `lyrics?v=`; if `parseSingle` yields an entry, return it.
   b. Clean `(title, artist)` with `MetadataCleaner.clean` → `toSearchQuery()`; if either cleaned
      value is blank, return `null`.
   c. `GET lyrics` with the cleaned values (plus raw album/duration parameters per 4.7.2) and
      `parseSingle`.
3. `null` → `IllegalStateException("Lyrics unavailable")` → failure.
4. Otherwise `Result.success(entry.lyrics)` — the **raw** document (LRC, TTML, or plain text). The
   provider does not inspect `format`/`syncType`.

Cancellation is rethrown; all other errors are `Result.failure`.

#### 4.7.4 `getAllLyrics`

1. `searchEntries`: clean metadata; if either value blank → empty; otherwise `GET lyrics/search`
   with cleaned values; parse; empty on any error.
2. If results are non-empty: take the first 5, call `currentCoroutineContext().ensureActive()`
   before each, log, and invoke `callback(entry.lyrics)` in order. Return.
3. If results are empty: `ensureActive()`, then `fetchEntry(videoId, title, artist, album,
   durationSeconds)`; invoke `callback` once when non-null; otherwise no callback.

Never throws except cancellation. The single-result fallback is only used when the search returned
no entries, not when it returned entries with blank lyrics (those are filtered out by
`parseSearch`, so effectively only when nothing usable remains).

#### 4.7.5 Parsing

Both parsers use the Unison `Json` (Section 3.3) and `runCatching`; they never throw.

`parseSingle(body)`:

1. decode `UnisonResponse`; decode failure → `null`;
2. `success` must be `true` → else `null`;
3. `data` must be non-null and `data.lyrics.isNotBlank()` → else `null`;
4. return the entry.

`parseSearch(body)`:

1. decode `UnisonSearchResponse`; decode failure → `null` → `emptyList()`;
2. `success` must be `true` → else empty;
3. `data.orEmpty()` filtered to entries with non-blank `lyrics`; preserve order.

Unknown envelope keys such as `"pagination"` are ignored; only `success` and `data` matter. A
`null` `data` (or missing) decodes to the default `null` and yields empty.

#### 4.7.6 DTOs (`com.revenge.unison.models`)

`UnisonEntry` — `@Serializable`:

| Field | Type | Default | Wire name |
|---|---|---|---|
| `id` | `Long` | required | `id` |
| `videoId` | `String?` | `null` | `videoId` |
| `song` | `String` | required | `song` |
| `artist` | `String` | required | `artist` |
| `lyrics` | `String` | required | `lyrics` |
| `format` | `String` | required | `format` |
| `syncType` | `String` | required | `syncType` |
| `score` | `Double` | `0.0` | `score` |
| `effectiveScore` | `Double` | `0.0` | `effectiveScore` |
| `voteCount` | `Int` | `0` | `voteCount` |
| `confidence` | `String` | `"low"` | `confidence` |
| `language` | `String?` | `null` | `language` |

`UnisonResponse`: `success: Boolean` (required), `data: UnisonEntry? = null`.
`UnisonSearchResponse`: `success: Boolean` (required), `data: List<UnisonEntry>? = null`.

`coerceInputValues = true` applies: a JSON `null` in a non-null field with a default falls back to
that default (e.g. `"confidence": null` → `"low"`). A missing required field fails the decode.

---

## 5. Cross-module contracts (must compile against exactly these shapes)

### 5.1 `:metadata` (consumed by LrcLib, Paxsenix, Unison)

```kotlin
package com.revenge.metadata.cleaner
object MetadataCleaner {
  fun clean(rawTitle: String, rawArtist: String = ""): com.revenge.metadata.models.CleanedMetadata
}

package com.revenge.metadata.models
data class CleanedMetadata(
  val rawTitle: String,
  val rawArtist: String,
  val cleanTitle: String,
  val primaryArtist: String,
  val cleanArtist: String = primaryArtist,
  val featuredArtists: List<String> = emptyList(),
  val version: VersionInfo? = null,
  val junkTags: List<String> = emptyList(),
) {
  fun toDisplayTitle(): String
  fun toSearchQuery(): Pair<String, String>   // cleanTitle to cleanArtist
}
```

Consumers may rely only on `clean`, `cleanTitle`, `primaryArtist`, `cleanArtist`,
`toSearchQuery()`. `clean` is idempotent and never throws for non-null inputs.

### 5.2 `:betterlyrics` `TTMLParser` (consumed by Paxsenix and `:lyrics`)

The nested `ParsedLine`/`ParsedWord` shapes in Section 4.3.1 are consumed by
`com.revenge.music.lyrics.UnisonLyricsProvider`, which calls `TTMLParser.toLRC(TTMLParser.parseTTML(content))`
when a Unison document starts with `<tt` (case-insensitive, after `trimStart`). Therefore:

- `parseTTML` must return an empty list rather than throw for non-TTML input.
- `toLRC(emptyList())` must return `""` (a `buildString` over zero lines).
- `ParsedLine` and `ParsedWord` are public and must keep their constructor parameter names and
  defaults; tests construct them by name.

### 5.3 `:core` preference keys and enums (consumed by the `:lyrics` adapters)

```kotlin
package com.revenge.music.constants

val EnableKugouKey: Preferences.Key<Boolean>        // "enableKugou"
val EnableLrcLibKey: Preferences.Key<Boolean>       // "enableLrclib"
val EnableBetterLyricsKey: Preferences.Key<Boolean> // "enableBetterLyrics"
val EnableSimpMusicKey: Preferences.Key<Boolean>    // "enableSimpMusic"
val EnableYouLyPlusKey: Preferences.Key<Boolean>    // "enableYouLyPlus"
val EnablePaxsenixKey: Preferences.Key<Boolean>     // "enablePaxsenix"
val UnisonLyricsEnabledKey: Preferences.Key<Boolean> // "unison_lyrics_enabled"
val LyricsProviderOrderKey: Preferences.Key<String>  // "lyricsProviderOrder"

enum class PreferredLyricsProvider {
  LRCLIB, KUGOU, BETTER_LYRICS, SIMPMUSIC, YOULYPLUS, PAXSENIX, UNISON
}
```

`context.dataStore[key]` returns `T?`; null means "not set". All adapters default a missing value
to `true` (enabled).

### 5.4 `:lyrics` adapter contract (existing downstream; not a target file)

```kotlin
interface LyricsProvider {
  val name: String
  fun isEnabled(context: Context): Boolean
  suspend fun getLyrics(id: String, title: String, artist: String, duration: Int, album: String? = null): Result<String>
  suspend fun getAllLyrics(id: String, title: String, artist: String, duration: Int, album: String? = null, callback: (String) -> Unit)
}
```

Exact wiring the provider modules must satisfy (argument order and nullability are load-bearing):

| Adapter `name` | Provider call |
|---|---|
| `"LrcLib"` | `LrcLib.getLyrics(title, artist, duration, album)` / `LrcLib.getAllLyrics(title, artist, duration, album, callback)` |
| `"Kugou"` | `KuGou.getLyrics(title, artist, duration, album)` / `KuGou.getAllPossibleLyricsOptions(title, artist, duration, album, callback)` |
| `"BetterLyrics"` | `BetterLyrics.getLyrics(...)` / `BetterLyrics.getAllLyrics(...)` |
| `"SimpMusic"` | `SimpMusicLyrics.getLyrics(id, duration)` / `SimpMusicLyrics.getAllLyrics(id, duration, callback)` |
| `"YouLyPlus"` | `YouLyPlus.getLyrics(title, artist, duration, album, id)` / `YouLyPlus.getAllLyrics(title, artist, duration, album, id, null, callback)` |
| `"Paxsenix"` | `Paxsenix.getLyrics(...)` after `Paxsenix.init(context)` when enabled; `/ getAllLyrics(...)` |
| `"Unison"` | `Unison.getLyrics(videoId = id, title, artist, album, durationSeconds = duration).map(::toLrcIfTtml)` / `Unison.getAllLyrics(..., callback = { callback(toLrcIfTtml(it)) })` |

Adapter registry names/display/default order (hard contract):

- Registry keys: `YouLyPlus`, `Paxsenix`, `BetterLyrics`, `Unison`, `SimpMusic`, `LrcLib`,
  `Kugou`, `YouTubeSubtitle`, `YouTubeMusic`.
- Default order: `YouLyPlus`, `Paxsenix`, `Unison`, `BetterLyrics`, `SimpMusic`, `LrcLib`,
  `Kugou`, `YouTubeSubtitle`, `YouTubeMusic`.
- Display names: `Paxsenix` → `PaxSenix`, `BetterLyrics` → `Better Lyrics`, `Kugou` → `KuGou`,
  others unchanged; unknown key → key itself.
- `LyricsProviderRegistry.deserializeProviderOrder` splits on `,`, trims, keeps known keys;
  blank → default order. `serializeProviderOrder` filters unknown and joins with `,`.
  `orderedProviderNames` = saved distinct values followed by default-order names not saved.
- `ProviderOutcome` = `SYNCED | UNSYNCED | FAILED`;
  `ProviderChoice` = `Pending | Selected(name) | None`.
  `chooseProvider(order, outcomes)` walks `order`: a missing outcome → `Pending`; `FAILED` →
  continue; `SYNCED`/`UNSYNCED` → `Selected(name)`; after the loop → `None`.

### 5.5 `LyricsHelper` orchestration (consumer contract)

- `getLyrics(mediaMetadata: MediaMetadata): LyricsWithProvider` where
  `data class LyricsWithProvider(val lyrics: String?, val providerName: String)`.
- Providers are derived from `LyricsProviderOrderKey` (via registry) and filtered by
  `isEnabled(context)`.
- Offline → `LyricsWithProvider(LYRICS_NOT_FOUND, "Unknown")`; `LYRICS_NOT_FOUND = "LYRICS_NOT_FOUND"`.
- A provider fetch is bounded by `withTimeoutOrNull(5_000)`; a timeout, a failed `Result`, or a
  blank/`LYRICS_NOT_FOUND` text marks `FAILED`. Non-blank text is `SYNCED` when
  `LyricsUtils.hasSyncedLyrics` finds any entry with time `> 0`, else `UNSYNCED`.
- Providers run concurrently; the highest-priority provider that has already succeeded wins as
  soon as every higher-priority provider has failed (or has not reported). `Pending` keeps
  waiting; `None` (all failed) → `LYRICS_NOT_FOUND`.
- Result cache: `LruCache<String, List<LyricsResult>>` capacity 20, keyed by `mediaMetadata.id`;
  `data class LyricsResult(val providerName: String, val lyrics: String)`. An in-flight map
  (`mediaMetadata.id` → `Deferred<LyricsWithProvider>`) coalesces duplicate requests; a losing
  duplicate is cancelled.
- `getAllLyrics(mediaId, songTitle, songArtists, duration, album, callback)` caches under
  `"$songArtists-$songTitle".replace(" ", "")`, collects all providers' callbacks into a
  `CopyOnWriteArrayList`, and replays the cache list on subsequent calls. Cancelling the current
  lyrics job stops collection.
- Threading: providers are fetched on a `SupervisorJob + Dispatchers.IO` scope so one provider's
  failure cannot cancel siblings and a caller switch cannot abort the shared fetch.

This section is context for the provider implementer, not a request to modify `:lyrics`.

### 5.6 Translation line-count normalisation (consumer contract, `:lyrics`)

`LyricsTranslationHelper` is outside the seven target modules, but its line-count rules are the
compatibility contract for any provider document that is later translated. Implementers of the
providers must know that:

- Only non-blank `LyricsEntry.text` lines are sent and are matched back by that same filtered
  index. A provider's blank lines therefore do not consume translation slots.
- Applying a translation result of size `n` to `m` non-blank entries sets the first
  `min(n, m)` entries and leaves the remaining entries' `translatedTextFlow` as `null`; extra
  translated lines beyond `m` are ignored (never appended).
- Loading persisted translations (`loadTranslationsFromDatabase`) uses the same
  `index < translatedLines.size` guard.
- Applying a cached translation requires `cached.size >= entries.size`; otherwise it is a miss.
- Streaming translation fills entries incrementally from complete JSON strings and stops at the
  first unterminated string, capped at the entry count; a later complete list replaces/overwrites.

The provider modules themselves must not reorder, merge, or drop lines inside a lyric document
except for the explicit normalisations in Section 4 (KuGou credit stripping, TTML/KPoe conversion).

---

## 6. Threading, concurrency, and side-effect guarantees

- All providers are stateless process-wide `object`s except the two documented mutable fields
  (`KuGou.useTraditionalChinese`, `Unison.logger`) and the `YouLyPlus` last-server reference.
  Concurrent calls from multiple coroutines/threads must be safe and produce equivalent results.
- Network is never performed on a constructor; clients are lazy.
- Callback invocations are synchronous on the caller's dispatcher and are never made from a
  library-created dispatcher.
- No provider touches disk, Android UI, or global settings. `Paxsenix.init` is the only Android
  API access (`Context.packageManager`).
- Providers never log through Timber except `:paxsenixlyrics` (debug/warn only, no lyric text).
  `:unison` uses its injected `logger`.
- Result text is exactly the remote document after documented transformations; no trimming is
  applied to successful provider output except `:betterlyrics`/Paxsenix LRC construction (which
  produce line-shaped text) and `:youlyplus` `trimEnd` per generated line.

---

## 7. Target files

The implementer must create/replace exactly these files (paths relative to the repository root).

### 7.1 `:lrclib`
- `lrclib/build.gradle.kts`
- `lrclib/src/main/kotlin/com/revenge/lrclib/LrcLib.kt`
- `lrclib/src/main/kotlin/com/revenge/lrclib/models/Track.kt`
- `lrclib/src/test/kotlin/com/revenge/lrclib/LrcLibLyricsTest.kt`
- `lrclib/src/test/kotlin/com/revenge/lrclib/models/TrackMatchingTest.kt`

### 7.2 `:kugou`
- `kugou/build.gradle.kts`
- `kugou/src/main/kotlin/com/revenge/kugou/KuGou.kt`
- `kugou/src/main/kotlin/com/revenge/kugou/models/DownloadLyricsResponse.kt`
- `kugou/src/main/kotlin/com/revenge/kugou/models/Keyword.kt`
- `kugou/src/main/kotlin/com/revenge/kugou/models/SearchLyricsResponse.kt`
- `kugou/src/main/kotlin/com/revenge/kugou/models/SearchSongResponse.kt`
- `kugou/src/test/kotlin/com/revenge/kugou/KuGouTest.kt`

### 7.3 `:betterlyrics`
- `betterlyrics/build.gradle.kts`
- `betterlyrics/src/main/kotlin/com/revenge/betterlyrics/BetterLyrics.kt`
- `betterlyrics/src/main/kotlin/com/revenge/betterlyrics/TTMLParser.kt`
- `betterlyrics/src/main/kotlin/com/revenge/betterlyrics/models/Track.kt`
- `betterlyrics/src/test/kotlin/com/revenge/betterlyrics/TTMLParserTest.kt`

### 7.4 `:simpmusic`
- `simpmusic/build.gradle.kts`
- `simpmusic/src/main/kotlin/com/revenge/simpmusic/LyricsSelection.kt`
- `simpmusic/src/main/kotlin/com/revenge/simpmusic/SimpMusicLyrics.kt`
- `simpmusic/src/main/kotlin/com/revenge/simpmusic/models/LyricsModels.kt`
- `simpmusic/src/test/kotlin/com/revenge/simpmusic/LyricsModelsTest.kt`
- `simpmusic/src/test/kotlin/com/revenge/simpmusic/LyricsSelectionTest.kt`

### 7.5 `:youlyplus`
- `youlyplus/build.gradle.kts`
- `youlyplus/src/main/kotlin/com/revenge/youlyplus/LrcFormat.kt`
- `youlyplus/src/main/kotlin/com/revenge/youlyplus/YouLyPlus.kt`
- `youlyplus/src/main/kotlin/com/revenge/youlyplus/models/LyricsResponse.kt`
- `youlyplus/src/test/kotlin/com/revenge/youlyplus/LrcFormatTest.kt`
- `youlyplus/src/test/kotlin/com/revenge/youlyplus/LyricsResponseTest.kt`
- `youlyplus/src/test/kotlin/com/revenge/youlyplus/ServerOrderTest.kt`

### 7.6 `:paxsenixlyrics`
- `paxsenixlyrics/build.gradle.kts`
- `paxsenixlyrics/src/main/kotlin/com/revenge/paxsenix/Paxsenix.kt`
- `paxsenixlyrics/src/main/kotlin/com/revenge/paxsenix/PaxsenixCore.kt`
- `paxsenixlyrics/src/main/kotlin/com/revenge/paxsenix/models/PaxsenixModels.kt`
- `paxsenixlyrics/src/test/kotlin/com/revenge/paxsenix/PaxsenixCoreTest.kt`

### 7.7 `:unison`
- `unison/build.gradle.kts`
- `unison/src/main/kotlin/com/revenge/unison/Unison.kt`
- `unison/src/main/kotlin/com/revenge/unison/models/UnisonModels.kt`
- `unison/src/test/kotlin/com/revenge/unison/UnisonTest.kt`

Do not modify `:lyrics`, `:core` or `:metadata` for this cluster. Their files are consumers.

---

## 8. Acceptance tests

All tests are deterministic and fixture-based (inline JSON/XML/LRC strings; Ktor `MockEngine` for
HTTP). No live network. Numeric labels are test-case IDs.

### 8.1 LrcLib — LRC parsing (`LrcLib.Lyrics.sentences`)

1. `"[00:01.00]first line\n[01:23.45]second line"` → entries `1_000L -> "first line"` and
   `83_450L -> "second line"`.
2. `"[00:10.00]hello"` → exactly `{0L: "", 10_000L: "hello"}`.
3. Fractions: `[00:00.5]a`, `[00:01.50]b`, `[00:02.500]c` → `500`, `1_500`, `2_500`.
4. Multiple stamps `[00:01.00][00:02.50]shared` → both `1_000` and `2_500` map to `"shared"`.
5. `"not a timestamp\n[00:03.00]text"` → map size 2 (zero entry + timestamp).
6. `""` → `mapOf(0L to "")`.
7. `"[01:05:25]dash"` (colon fraction separator) → contains `65_250L -> "dash"`.
8. Malformed LRC (e.g. `[xx:yy]text`, unclosed `[00:01.00 text`) → no exception; map contains only
   `0L` (or any parseable lines).
9. Duplicate timestamp lines → the later line's text wins.

### 8.2 LrcLib — matching and DTO

10. Strict: tracks `(1, 100.0)` and `(2, 110.0)`; `bestMatchingFor(102)!!.id == 1`;
    `bestMatchingFor(105) == null`.
11. Relaxed: `(1, 100.0, plain)` and `(2, 103.0, synced)`; `bestMatchingForRelaxed(101)!!.id == 2`.
12. Relaxed: `(1, 100.0)`, `(2, 104.0)`; `bestMatchingForRelaxed(103)!!.id == 2`;
    `bestMatchingForRelaxed(120) == null`.
13. Unknown duration `-1`: `(1,10.0)` and `(2,20.0,synced)` → id 2 for both relax/strict helpers.
14. Unknown duration with names: "Hello"/"Adele" scores 1.0; returns id 1; unrelated names → null.
15. Empty lists return null for all three helpers.
16. `Track` decodes the fixture with `instrumental`/`lang` unknown keys; `plainLyrics == null`,
    `syncedLyrics == "[00:01.00]You were the shadow"`.
17. Search fixture: first query returns one plain-only track → `getLyrics` succeeds with that plain
    text; no further queries are issued.
18. Search fixture: query 1 returns only tracks with both lyric fields null, query 3 returns a
    synced track → the query-3 track wins.
19. Search fixture: every query returns HTTP 500 → `getLyrics` fails with
    `IllegalStateException("Lyrics unavailable")`, exactly one request per widened query, and
    `lyrics(artist,title)` succeeds with an empty list.
20. `getAllLyrics` with three synced tracks within 5 s → callbacks in ascending duration distance;
    a far track is skipped; at most one plain callback; cap 5.

### 8.3 KuGou — normalisation, DTOs, parsing

21. `generateKeyword("Song (Remastered)", "Artist", "Album")` → title `"Song "`, artist
    `"Artist"`, album `"Album"`.
22. `generateKeyword("Song", "A, B & C")` → artist `"A、B、C"`, album null;
    `generateKeyword("T", "Mt. Joy").artist == "Mt Joy"`.
23. `buildSearchQuery`: `"T - A"`, `"T - A Album"`, blank album → `"T - A"`.
24. `normalizeLyrics` on the fixture with `song info`, two `[00:00.00]作词/作曲` credit lines and
    two body lines → `"[00:10.00]line one\n[00:20.50]line two"`.
25. `normalizeLyrics` on CRLF `"[00:01.00]a\r\n[00:02.00]b"` → LF-joined, unchanged timestamps.
26. `normalizeLyrics` on `"no lyrics here"` and `""` → `""`.
27. Long document (34 timestamped lines) with a trailing credit line → 34 body lines retained,
    first/last unchanged.
28. Malformed LRC (timestamps with one-digit minutes, e.g. `[1:02.00]x`) → no matching line →
    `""`.
29. `SearchLyricsResponse` fixture with `product_from` and unknown keys → candidate fields correct;
    `"{}"` → defaults (`status 0`, empty candidates).
30. Download fixture `{"content":"<base64 of LRC with credits>"}` → decoded/normalised LRC; MIME
    line-wrapped Base64 (with `\r\n`) decodes.
31. `getLyrics` with no candidate (empty search responses) → failure whose message is
    `"No lyrics candidate for <title> - <artist>"`.
32. KuGou HTTP 403 on song search → `getLyrics` returns `Result.failure` and issues **no retry** of
    the same URL (assert one request).

### 8.4 BetterLyrics — TTML

33. Paragraph with two timed spans → one line `"Hello world"`, start `1.5`, two words with the
    exact start/end values.
34. Adjacent no-whitespace spans `Hel` + `lo` → one word `"Hello"`, 0.0–0.2.
35. `x-bg` span → separate `backgroundLines` entry with its own begin and `isBackground == true`.
36. `x-translation` and `x-roman` spans ignored; only the main word remains.
37. `ttm:agent="v1"` → `line.agent == "v1"`.
38. Paragraph with no spans → direct text, empty `words`.
39. Hour form `01:00:05.000` → start `3605.0`; plain seconds `"12.5"` → `12.5`.
40. Paragraph without `begin` skipped; malformed XML `<tt><body><p begin='0'>oops` → empty list;
    `""` → empty list.
41. `toLRC` fixture produces exactly:
    `"[01:05.23]{agent:v1}Hello\n<Hello:65.234:65.9>\n[01:05.50]{bg}echo\n<echo:65.5:65.7>\n"`.
42. Line without words → `"[00:00.00]plain\n"`.
43. `getLyrics` with HTTP 403 → `Result.failure("Lyrics unavailable")`; no exception escapes.
44. `getLyrics` with HTTP 200 `{"ttml":"<tt/>"}` → `Result.failure("Failed to parse lyrics")`.
45. `getLyrics` with malformed JSON body → failure, not crash.
46. `getAllLyrics` invokes the callback exactly once on success and zero times on failure.

### 8.5 SimpMusic — selection, DTOs, fallback

47. DTO fixture with `songTitle`/`artistName`/`albumName`/`durationSeconds`/`plainLyric` → mapped
    fields; unknown key ignored; `"{}"` envelope → `success == false`, empty data; missing lyrics
    fields → null.
48. `durationDistance`: null duration → target; 100 vs 120 → 20.
49. `matchesDuration`: target `0`/negative matches all; 110/130 match 120; 109/131 do not.
50. `bestLyricsText` precedence rich > synced > plain; blanks skipped; all blank → null.
51. `selectLyrics`: target 0 → first API result; target 200 picks the closest of 240/202/215
    (202); only 300 → null; empty → null.
52. `collectAlternativeLyrics`: ordered by closeness; mismatches skipped; one plain max; blank
    ignored; cap 5.
53. Primary returns 500, fallback returns a success payload → `getLyricsByVideoId` returns the
    fallback data and makes exactly two requests.
54. Primary returns `200 {"type":"error","data":[...]}` → empty list and **no** fallback request.
55. Primary transport error, fallback transport error → empty list, no exception.
56. `getLyrics` with no matching candidate → failure `"Lyrics unavailable"`.
57. Unsynced-only payload → `getLyrics` succeeds with the plain text.

### 8.6 YouLyPlus — LRC formatting, race, 403 isolation

58. `formatLrcTimestamp`: `0` → `[00:00.000]`; `61_234` → `[01:01.234]`; `3_600_000` →
    `[60:00.000]`; `-5_000` → `[00:00.000]`; `syllable = true` → `<01:01.234>`.
59. `toLrc` on empty list → null; plain item → `[00:01.000]hello`.
60. Syllables `Hello`@0, ` world`@500, `!`@900 → `[00:00.000]<00:00.000>Hello <00:00.500>world!`.
61. Background syllable → `[00:01.000]{bg}<00:01.000>la`.
62. Multiple lines joined by `\n`; textless item → `[00:00.000]`.
63. DTO: LRCLib-style payload with unknown keys decodes; KPoe `lyrics` + `syllabus` decodes;
    `"{}"` → all optional null.
64. `promoteServer`: null/unknown → unchanged; known `"b"` → `["b","a","c"]`; `"c"` →
    `["c","a","b"]`; no duplicates.
65. MockEngine: three servers, two timeout/500/403, one returns KPoe lyrics → `getLyrics` succeeds
    with the converted LRC; all other requests are cancelled (assert cancellation or that the
    function returns before the slow mock completes).
66. MockEngine: every server 403 → `Result.failure` with
    `"No lyrics found from any YouLyPlus server"`; exactly one request per server (no retry).
67. MockEngine: same document returned by two mirrors → `getAllLyrics` calls the callback once.
68. Response preference: `syncedLyrics` blank + `lyrics` list present → KPoe LRC used;
    `syncedLyrics` non-blank → used verbatim; only `plainLyrics` → used.

### 8.7 Paxsenix — ranking, formatting, precedence

69. Rank exact over distant; `score` positive ordering.
70. Unrelated result (no positive signal) dropped; result with blank id dropped.
71. Remix/mixed penalised vs original; original first.
72. `searchQueries` examples from Section 4.6.7; duplicates collapsed.
73. `hasWordTimings` recognises `[00:01.00]<00:01.00>Hello`, rejects plain LRC.
74. `plainTextFromContent` skips blank lines/words and joins with spaces/newlines.
75. `lrcFromContent` for the two-word fixture → exact
    `"[01:01.50]{agent:v2}Hello world\n<Hello:61.5:61.8|world:61.8:62.0>\n"`.
76. Agent tags: background → `{bg}`, oppositeTurn → `{agent:v2}`, else `{agent:v1}`.
77. `buildLyrics` precedence: TTML > elrcMultiPerson > elrc > plain; converted-empty TTML falls to
    plain.
78. `buildLyrics` non-syllable content → plain text, no word timings; syllable content → LRC with
    `{agent:v1}` and word track.
79. `buildLyrics(LyricsResponse())` and syllable-with-empty-content → failure.
80. DTO decode for bare-array search response and the lyric response fixture.
81. `getLyrics` before `init` → `Result.failure` with the exact init message; `init` twice does not
    throw and does not rebuild the client.
82. 403 from search → all queries tried, then failure `"No matching track found on Paxsenix"`
    (no retry of the same URL).

### 8.8 Unison — parsing, pagination tolerance, blanks

83. `parseSingle` full entry → all fields; optional defaults applied; `success:false` → null;
    blank lyrics → null; unknown/malformed/empty body → null.
84. Pagination tolerance: body `{"success":true,"pagination":{"page":1},"data":{...}}` → entry
    parsed (unknown envelope key ignored).
85. `parseSearch` keeps valid entries and drops blank-lyrics entries; `success:false`, absent
    `data`, and `<html>` → empty.
86. `getLyrics` with blank title or artist → failure (no request); with videoId hit → returns raw
    lyrics; videoId miss + metadata hit → metadata lyrics.
87. `getLyrics` where the service returns 404/500 for every endpoint → failure
    `"Lyrics unavailable"`; no exception escapes; no retry loop.
88. `getAllLyrics` with unusable metadata → zero callbacks; search results 7 entries → first 5
    callbacks in order; search empty + single lookup present → one callback.
89. TTML pass-through: a Unison entry whose lyrics start with `<tt` is converted by the `:lyrics`
    adapter (test the adapter mapping separately); a plain/LRC entry is unchanged.
90. `getAllLyrics` cancellation between callbacks stops further callbacks (the `ensureActive`
    check).

### 8.9 Cross-cutting determinism tests

91. Provider timeout: with a fake `LyricsProvider` whose `getLyrics` suspends longer than 5 s and a
    second fake that succeeds, `LyricsHelper.getLyrics` returns the successful provider and marks
    the slow one `FAILED`; with only the slow provider it returns
    `LyricsWithProvider("LYRICS_NOT_FOUND", "Unknown")` after ~5 s.
92. Failure isolation: one provider throws a non-cancellation exception; the others still produce
    a result; the exception is reported once and never cancels siblings.
93. Cancellation propagation: cancelling the caller of each provider's `getLyrics` rethrows
    `CancellationException` (never returns `Result.failure`) and cancels in-flight HTTP requests.
94. Unknown/absent fields: for every DTO, a fixture missing every optional field decodes with the
    documented defaults; a fixture with extra unknown fields decodes identically.
95. 403 no-retry for every client: one request per attempted URL, no backoff delay, failure
    surfaced through the documented channel.
96. Pagination continuation: KuGou song search sends `pagesize=8` and no page/continuation
    parameter; no provider follows a continuation token; Unison ignores a `pagination` object
    while still returning its `data`.

### 8.10 Translation line-count normalisation (`:lyrics` consumer tests)

97. Three non-blank entries, translation returns `["a","b"]` → entries 0 and 1 get `"a"`/`"b"`,
    entry 2's `translatedTextFlow` stays `null`.
98. Three entries, translation returns five strings → the first three are applied; the last two are
    discarded.
99. Blank entries are excluded before translation and never receive a translation; indices map
    back to the original list positions.
100. Cached translation of size 2 applied to 3 entries → treated as a miss (no partial apply).
101. Streaming partial JSON stops at the first unterminated string and applies only complete lines
     up to the entry count.

---

## 9. Non-functional requirements

- No provider performs work in a top-level initializer; first use may create the HTTP client.
- All public entry points are safe to call concurrently and repeatedly.
- Memory: no unbounded provider-local collections. `YouLyPlus` dedupes one call with a transient
  `HashSet`; `Paxsenix` keeps at most `limit` in-flight jobs; KuGou's song page is 8 entries.
- Determinism: identical fixtures produce identical output strings across runs and platforms,
  except network timing-dependent callback order in `YouLyPlus.getAllLyrics` (arrival order) and
  `Unison`/`LyricsHelper` concurrency. Tests must assert sets/contents for those.
- No provider catches `Throwable`; catch `Exception` (Paxsenix's `attempt` may catch `Throwable`
  except cancellation, matching the existing contract, but must not swallow
  `CancellationException`).
- Security: TTML parsing must remain XXE-hardened. HTTP clients must not log response bodies
  containing lyrics.
- Lint: no wildcard imports, 2-space indentation, trailing commas where the codebase uses them
  (ktfmt-style). Test method names may use backticked sentences.

---

## 10. Out of scope

- The `:lyrics` module's own translation HTTP services (`DeepLService`, `MistralService`,
  `OpenRouterService`, `OpenRouterStreamingService`), `LyricsUtils` romanisation tables, and UI.
- `YouTubeLyricsProvider` / `YouTubeSubtitleLyricsProvider` (InnerTube-backed; not in this
  cluster, though they appear in the registry).
- Database persistence of lyrics (`:core` `LyricsEntity`).
