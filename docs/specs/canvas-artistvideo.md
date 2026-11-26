# Clean-Room Specification — `canvas-artistvideo` cluster

Status: M2 specification (behavioural contract only; no implementation bodies).
Cluster: `canvas-artistvideo`.
Gradle modules in scope:

| Module | Gradle path | Directory | Runtime kind |
|---|---|---|---|
| canvas | `:canvas` | `canvas` | Kotlin/JVM library (no Android APIs) |
| applecanvas | `:applecanvas` | `applecanvas` | Kotlin/JVM library (no Android APIs) |
| artistvideo | `:artistvideo` | `artistvideo` | Android library (Compose + Media3) |

Clean-room packages that downstream modules import verbatim:

- `com.revenge.canvas`
- `com.revenge.applecanvas`
- `com.revenge.artistvideo`

This document defines the observable contract of the animated-artwork providers and the artist-video
surface. Any implementation that satisfies the signatures, semantics, invariants and acceptance tests
below is acceptable. Statement-level implementation is intentionally absent. Constants whose values
are observable (TTLs, thresholds, endpoint shapes, ordering lists) are part of the contract and are
listed explicitly.

> **Implementation-shape disclaimer (read first).** The algorithm descriptions below state
> *observable behaviour*, not prescribed structure. Helper decomposition, function names, types,
> containers, iteration mechanics and control flow are illustrative only: implementers design
> their own internal shape independently, and submissions are similarity-audited. Nothing here
> requires a particular internal design except where it is a public API, a wire/protocol field, a
> mandated string, or an acceptance-test observable. Matching the rules below is what passes
> review; copying a baseline's internal design does not.

---

## 1. Purpose and scope

The cluster resolves and plays short, muted, looping animated artwork ("canvas" / artist video):

- `:canvas` — shared result type `CanvasArtwork`, the TIDAL canvas provider, the Apple Music token
  supplier, and the Apple Music **artist-page background** provider (returns a bare video URL).
- `:applecanvas` — the Apple Music **album/song canvas** provider (returns `CanvasArtwork`).
- `:artistvideo` — a network provider for a third-party artist-video archive plus a reusable Compose
  video surface.

Out of scope for this cluster: YouTube canvas extraction (`YouTubeVideoCanvasProvider`, currently in
`:app`), Spotify canvas, persistence of fetched artwork (`CanvasArtworkPlaybackCache` in `:app`),
player/queue integration. Those are consumers and are described only as contract expectations in §10.

### 1.1 Provider isolation (invariant)

Every provider in this cluster is an **independent singleton**:

- No provider calls another provider. Chaining/fallbacks are the caller's responsibility.
- No provider reads application preferences, player state, Android `Context`, or the filesystem.
- No provider throws for expected failures (network, HTTP error, malformed payload, no match). The
  only exception that may escape a suspend provider function is coroutine cancellation.
- Providers are safe to call concurrently from any dispatcher; all shared mutable state is
  concurrency-safe (see §14).
- Providers are stateless with respect to the caller: identical inputs at the same time may be
  answered from a per-provider cache.

---

## 2. Module build contracts

These must remain true for downstream compilation and packaging.

### 2.1 `canvas/build.gradle.kts`

- Plugins: `org.jetbrains.kotlin.jvm`, `kotlin-serialization`.
- `jvmToolchain(21)`.
- Dependencies: Ktor client core, Ktor OkHttp engine, Ktor content negotiation, Ktor JSON
  serialization, Ktor content encoding; test: JUnit 4.
- No Android or Compose dependencies. Public API must not reference Android types.

### 2.2 `applecanvas/build.gradle.kts`

- Plugins: `org.jetbrains.kotlin.jvm`, `kotlin-serialization`; `jvmToolchain(21)`.
- Depends on `project(":canvas")` plus the same Ktor stack as `:canvas`; test: JUnit 4.
- **Compile-contract fix required:** `:applecanvas` publicly returns `com.revenge.canvas.CanvasArtwork`
  and imports `com.revenge.canvas.AppleMusicTokenProvider`. The dependency on `:canvas` must be
  declared `api(project(":canvas"))` (not `implementation`) so the type is visible to consumers that
  only depend on `:applecanvas`. The app currently also depends on `:canvas`, which masks the defect;
  do not rely on that.

### 2.3 `artistvideo/build.gradle.kts`

- Plugins: `com.android.library`, `kotlin-serialization`, `compose-compiler`.
- `namespace = "com.revenge.artistvideo"`, `compileSdk 37`, `minSdk 26`, Java/Kotlin 21,
  core-library desugaring enabled, `buildFeatures { compose = true }`.
- Dependencies: Ktor stack, Compose runtime/foundation/ui/animation, Material3, Media3 exoplayer +
  UI + OkHttp datasource, desugaring; test: JUnit 4.
- `src/main/AndroidManifest.xml` is intentionally empty (no components, no permissions declared
  here). Network permission is supplied by the consuming app manifest.
- **Runtime dependency risk:** the surface selects HLS (`application/x-mpegURL`) for `.m3u8` URLs and
  the Apple Music providers return HLS. Media3 requires the **`media3-exoplayer-hls`** artifact on
  the runtime classpath for `DefaultMediaSourceFactory` to construct an HLS source. The current build
  does not declare it. Implementers must add `libs.media3.hls` (already present in the version
  catalog) or equivalent; otherwise HLS playback throws at prepare time. This is a packaging
  contract, not a public API change.

---

## 3. Target files and responsibilities

All paths are relative to the repository root. The public package names and public symbols are part
of the contract; file names are indicative, and internal decomposition, private files and helper
types are the implementer's choice provided no new public symbols are introduced.

### 3.1 Main sources

| Target file | Responsibilities |
|---|---|
| `canvas/build.gradle.kts` | JVM module definition, Ktor dependencies, JUnit. |
| `canvas/src/main/kotlin/com/revenge/canvas/CanvasArtwork.kt` | `@Serializable` cross-provider result model + derived properties. |
| `canvas/src/main/kotlin/com/revenge/canvas/TidalCanvasProvider.kt` | TIDAL v1 search → `CanvasArtwork` (§5). |
| `canvas/src/main/kotlin/com/revenge/canvas/AppleMusicTokenProvider.kt` | Process-wide Apple Music web bearer token: discovery, caching, fallback, invalidation. |
| `canvas/src/main/kotlin/com/revenge/canvas/AppleMusicArtistBackgroundProvider.kt` | Artist-name → HLS background video URL (`String?`). |
| `applecanvas/build.gradle.kts` | JVM module definition; must expose `:canvas` as `api` (see §2.2). |
| `applecanvas/src/main/kotlin/com/revenge/applecanvas/AppleMusicCanvasProvider.kt` | Album/song/album-id → `CanvasArtwork`; candidate scoring, album-id resolution, editorial-video extraction. |
| `artistvideo/build.gradle.kts` | Android/Compose/Media3 module definition; must add Media3 HLS runtime artifact. |
| `artistvideo/src/main/AndroidManifest.xml` | Empty manifest; merged into app. |
| `artistvideo/src/main/kotlin/com/revenge/artistvideo/ArtistVideo.kt` | `@Composable ArtistVideo` muted looping video surface (§9.3). |
| `artistvideo/src/main/kotlin/com/revenge/artistvideo/ArtistVideoCanvasProvider.kt` | Archive endpoint → `ArtistVideoResponse`; short-TTL positive and negative cache. |

### 3.2 Tests the implementer must provide

| Target test file | Responsibilities |
|---|---|
| `canvas/src/test/kotlin/com/revenge/canvas/CanvasArtworkTest.kt` | Serialization names/defaults, derived URL and YouTube-snippet properties. |
| `canvas/src/test/kotlin/com/revenge/canvas/TidalCanvasProviderTest.kt` | Cover-ID formatting, artist-separator matching, cache/HTTP failure paths. |
| `canvas/src/test/kotlin/com/revenge/canvas/AppleMusicTokenProviderTest.kt` | JWT decode/issuer/expiry selection, fallback and invalidation. (New file; required by §11.) |
| `canvas/src/test/kotlin/com/revenge/canvas/AppleMusicArtistBackgroundProviderTest.kt` | Name scoring, motion-key precedence, URL extraction, negative caching. (New file; required.) |
| `applecanvas/src/test/kotlin/com/revenge/applecanvas/AppleMusicCanvasLogicTest.kt` | Query building, scoring, album-id resolution, editorial extraction, response mapping, cache keys. |
| `artistvideo/src/test/kotlin/com/revenge/artistvideo/ArtistVideoResponseTest.kt` | Wire-model decoding, tolerance of nulls/unknown keys, URL preference. |
| `artistvideo/src/test/kotlin/com/revenge/artistvideo/VideoMimeTypeTest.kt` | Case-insensitive MP4 vs HLS classification and default. |
| `artistvideo/src/test/kotlin/com/revenge/artistvideo/ArtistVideoCanvasProviderTest.kt` | Query parameters, cache TTL/negative caching, failure isolation. (New file; required.) |

Tests must be deterministic and must not perform real network I/O. See §12 for the required internal
test seam.

---

## 4. Core shared type: `CanvasArtwork`

Package `com.revenge.canvas`. Kotlin `@Serializable data class`, **all constructor parameters are
optional with default `null`**. Instances are persisted as JSON by the app, so field names, serial
names, nullability and defaults are frozen.

### 4.1 Shape

| JSON key | Kotlin property | Type | Notes |
|---|---|---|---|
| `name` | `name` | `String?` | Song or album title, as returned by the provider. |
| `artist` | `artist` | `String?` | Billing/primary artist. |
| `albumId` | `albumId` | `String?` | Declared with serial name `albumId` (property name may differ, wire name may not). |
| `albumName` | `albumName` | `String?` | Owning album title when known. |
| `static` | `static` | `String?` | Still-image URL. Providers in this cluster do not populate it; kept for persistence compatibility. |
| `animated` | `animated` | `String?` | Explicitly animated stream (HLS or MP4). |
| `videoUrl` | `videoUrl` | `String?` | Provider's generic video URL (TIDAL); fallback animation stream. |
| `clipStartMs` | `clipStartMs` | `Long?` | Optional snippet start. |
| `clipEndMs` | `clipEndMs` | `Long?` | Optional snippet end. |

`equals`, `hashCode`, `toString`, `copy` and the generated serializer are part of the contract.

### 4.2 Derived properties (observable, non-serialized)

- `preferredAnimationUrl: String?` — returns `animated` if and only if it is non-null; otherwise
  returns `videoUrl`. A blank non-null `animated` is returned as-is (callers filter blanks).
- `isYouTubeSnippet: Boolean` — `true` if **any** of:
  - `clipStartMs != null`, or
  - `clipEndMs != null`, or
  - `videoUrl` contains `googlevideo.com` or `youtube.com` (case-insensitive), or
  - `animated` contains `googlevideo.com` or `youtube.com` (case-insensitive).
  `false` otherwise. Neither property consults `static`.

### 4.3 Persistence invariants

- Decoding must tolerate missing keys, explicit `null`s and unknown keys when the consumer uses a
  tolerant `Json` (`ignoreUnknownKeys = true`, `explicitNulls = false`).
- Re-encoding an instance decoded from a payload that only used the keys above must round-trip to an
  equal instance.
- No default non-null values: `CanvasArtwork()` is valid and all fields are null.

### 4.4 JSON parsing

JSON parsing is private implementation detail; no JSON-accessor API is part of the contract. Each
module parses payloads with its own private code. Only the wire shape in §4.1 and the tolerance
rules in §4.3 are contractual.

---

## 5. `TidalCanvasProvider` (`:canvas`)

Package `com.revenge.canvas`. Kotlin `object`; all cache state is process-wide and lives for the
process lifetime.

### 5.1 Public API

```kotlin
suspend fun getBySongArtist(song: String, artist: String, album: String? = null): CanvasArtwork?
suspend fun getByAlbumArtist(album: String, artist: String): CanvasArtwork?
```

Both suspend on the caller's context (they do not switch dispatchers). They are safe to call from
`Dispatchers.IO` or any other dispatcher.

### 5.2 External protocol

- Base URL: `https://api.tidal.com/v1/`.
- Endpoint: `GET /search`.
- Authentication: HTTP header `X-Tidal-Token: <public TIDAL v1 client token>`. The token is a public,
  fixed compile-time constant (the well-known TIDAL web token); it is **not** user-specific and must
  not be derived from user credentials.
- Query parameters: `query` (free text), `limit=10`, `types` (`TRACKS` or `ALBUMS`),
  `countryCode` (uppercase two-letter).
- `countryCode` is resolved once per process from the default JVM locale: uppercase country code if
  it is exactly 2 characters, otherwise `"US"`. It is **not** a method parameter and not part of the
  cache key.
- Expected success: HTTP 200 with a JSON object containing a section whose `items` array holds
  search results. Section lookup is by lowercased `types` (`tracks`/`albums`) and `items`; if absent,
  the first `items` array found anywhere in the document (document order) is used.
- Compression: gzip and deflate accepted; HTTP caching enabled; `expectSuccess = false` (status
  inspected manually).

### 5.3 Behaviour

`getBySongArtist(song, artist, album)`:
1. If `song` or `artist` is blank after no trimming rule other than blank check → return `null`
   (no request, no cache write).
2. Cache key = `"song|" + lowercase(trim(song)) + "|" + lowercase(trim(artist)) + "|" +
   lowercase(trim(album.orEmpty()))`.
3. On a live cache entry (`expiresAt > now`) return its stored artwork (which is never null).
4. Query = `album.isNullOrBlank() ? "$artist $song" : "$album $artist $song"` (single spaces, no
   deduplication).
5. Search with `types=TRACKS`; map the first acceptable item (see §5.4).
6. Store only non-null results for 24 h.

`getByAlbumArtist(album, artist)`:
1. Blank `album` or `artist` → `null`.
2. Cache key = `"album|" + lowercase(trim(album)) + "|" + lowercase(trim(artist))`.
3. Query = `"$album $artist"`; `types=ALBUMS`.

Caching: a process-wide, concurrency-safe in-memory cache keyed as above, TTL exactly 24 h
(86 400 000 ms) from the time of a successful store. **Misses are not cached.** Expired entries are
treated as absent.

### 5.4 Result mapping (observable)

For each candidate object in order, take the first that maps to a non-null `CanvasArtwork`:

1. Title text (`title`).
2. Title gate:
   - `TRACKS`: if the requested song is non-null and title is non-null, title must contain the song
     (case-insensitive); otherwise reject.
   - `ALBUMS`: if the requested album is non-null and title is non-null, title must contain the album
     **or** album must contain the title (case-insensitive); otherwise reject.
3. Primary artist = `artist.name`, else first element of `artists[].name`.
4. If a primary artist is present it must match the requested artist per the artist-matching rule in
   §5.5, else reject.
5. Owner object = for `TRACKS` the `album` object; for `ALBUMS` the candidate itself.
6. `videoCover` on the owner must be a non-blank string, else reject.
7. Format the cover id per §5.5; a malformed id rejects the candidate.
8. Produce `CanvasArtwork`:
   - `name` = title ?: song ?: album ?: `""`
   - `artist` = primary artist ?: requested artist
   - `videoUrl` = formatted URL
   - `albumName` = for `TRACKS` the owner's `title`; for `ALBUMS` the candidate title
   - all other fields null.

### 5.5 Cover-id and artist-matching rules (observable)

**Cover-id formatting.** A TIDAL video cover id is exactly **five** non-blank dash-separated
segments (e.g. `aa-bb-cc-dd-ee`). Such an id maps to
`https://resources.tidal.com/videos/<s1>/<s2>/<s3>/<s4>/<s5>/1280x1280.mp4`.
Any other segment count, an empty id, or any blank segment is malformed and rejects the candidate.

**Artist matching.** Requested and returned artist strings are split on any of: comma, ampersand,
multiplication sign `×`, bare `x` between spaces, `feat`/`ft` with optional period, `featuring`,
`with` — case-insensitive, allowing surrounding spaces. Each part is normalized (whitespace runs
collapsed to one space, trimmed, lowercased) and blanks dropped. The artists match iff at least one
requested part and one returned part contain one another (either direction). Two blank lists do not
match.

### 5.6 Failure isolation

- HTTP status other than 200 → `null`.
- Any exception while requesting or decoding, except coroutine cancellation → `null`.
- Coroutine `CancellationException` (either `kotlinx.coroutines.CancellationException` or
  `kotlin.coroutines.cancellation.CancellationException`) must be rethrown unchanged.
- No logging is required; failures are silent by contract.

---

## 6. Apple Music token supplier (`:canvas`)

Package `com.revenge.canvas`. Kotlin `object`, process-wide. This is the shared bearer-token source
for every Apple Music web-API request in `:canvas` and `:applecanvas`; downstream app code also calls
it (`AppleMusicAboutAlbum`).

### 6.1 Public API

```kotlin
suspend fun getToken(): String
suspend fun clearToken()
```

- `getToken` never returns an empty string and never throws for network/parse failures. It may throw
  on coroutine cancellation.
- `clearToken` invalidates the cached token so the next `getToken` re-discovers or re-falls-back.

### 6.2 Semantics

1. All `getToken` and `clearToken` calls are serialized through one coordination point, so concurrent
   callers share at most one discovery attempt (no duplicate concurrent discovery).
2. Cached token is reused iff `cachedExpiryMs > now + 5 min` (300 000 ms headroom). Expiry uses the
   JWT's `exp` claim converted from seconds to milliseconds.
3. On cache miss, attempt live discovery:
   - Fetch, in order, `https://music.apple.com/us/browse` and `https://beta.music.apple.com` as HTML
     (`Accept: text/html`), with a desktop Chrome User-Agent.
   - In each page, find an asset bundle path matching `/assets/index<...>.js` (first match).
     Absolute URLs are used as-is; a path is prefixed with `https://music.apple.com`.
   - Fetch the bundle as script text.
   - Find all JWT-shaped strings (`eyJ` prefix plus two dot-separated segments, base64url alphabet,
     first segment ≥20 chars after the prefix).
   - For each: split into 3 parts, base64url-decode the payload, parse JSON, accept only when
     `iss == "AMPWebPlay"` and `exp` parses to a long; expiry = `exp * 1000`.
   - Keep the token with the greatest expiry that is still `> now + 5 min`.
   - Any failure for one page/bundle skips to the next; a whole-discovery failure is swallowed.
4. If no live token is found, use a **bundled fallback token** (a fixed compile-time JWT whose issuer
   is `AMPWebPlay` and which must be valid for catalog reads) and cache it for 24 h.
5. The chosen token and expiry are published so that subsequent callers observe them once the
   serialized discovery completes.
6. `clearToken` sets the cache to empty; it does not wait for in-flight HTTP beyond the serialization
   point.

### 6.3 Implementation notes that are contractual

- The fallback token is deployment data, not behaviour: the implementer must supply a working
  fallback JWT constant. It must not be logged or returned to UI.
- Token discovery must send the same desktop User-Agent used by `:applecanvas`.
- HTTP client timeouts: connect 10 s, request 15 s, socket 15 s; `expectSuccess = false`.
- Cancellation is rethrown; all other exceptions during discovery are treated as "no live token".

---

## 7. Apple Music artist background provider (`:canvas`)

Package `com.revenge.canvas`. Kotlin `object`. Returns the **raw video URL**, not a `CanvasArtwork`,
because the artist page only needs a looping background.

### 7.1 Public API

```kotlin
suspend fun getByArtistName(artistName: String, storefront: String = "us"): String?
```

`storefront` is a lowercase two-letter Apple Music storefront (`us`, `gb`, …). Callers pass a
lowercased locale country or `"us"`.

### 7.2 Behaviour

1. Trim `artistName`; empty → `null` (no request).
2. Cache key = `"artist|" + lowercase(trim(name)) + "|" + lowercase(trim(storefront))`.
3. Cached entry valid for `expiresAt > now` → return its value.
4. Search:
   - `GET https://amp-api.music.apple.com/v1/catalog/{storefront}/search`
   - params `term=<artistName>`, `types=artists`, `limit=3`
   - headers: `Authorization: Bearer <getToken()>`, `Origin: https://music.apple.com`,
     `Referer: https://music.apple.com/`, desktop Chrome User-Agent.
5. On 401: call `clearToken()` and return `null`.
6. On 200: read `results.artists.data[]`. For each artist object compute a name score:
   - candidate name blank → 0
   - if neither candidate contains requested nor requested contains candidate (ignore case) → 0
   - exact equals (ignore case) → 10
   - otherwise → 5
   Sort candidates by score descending (stable order for ties). Skip scores `< 4` (i.e. only exact
   or substring matches proceed). For each remaining candidate, take its `id` and fetch artist
   detail; return the first non-blank video URL.
7. Artist detail:
   - `GET https://amp-api.music.apple.com/v1/catalog/{storefront}/artists/{id}`
   - params `extend=editorialVideo,editorialArtwork`, `include=albums`,
     `views=featured-albums,top-music-videos`, `platform=web`
   - 401 → `clearToken()`, `null`; non-200 → `null`.
8. Extraction order from `data[0].attributes`:
   a. `editorialVideo` → first motion URL.
   b. `editorialArtwork` → first motion URL.
   c. Albums gathered in order from `views.featured-albums.data[]` then
      `relationships.albums.data[]`; for each, `attributes.editorialVideo` → first motion URL.
   d. Otherwise `null`.
9. "First motion URL" inside an editorial object:
   - Try keys in this fixed order: `motionArtistWide16x9`, `motionArtistFullscreen16x9`,
     `motionArtistSquare1x1`, `motionDetailTall`, `motionTallVideo3x4`, `motionDetailSquare`,
     `motionSquareVideo1x1`, `motionDetailRaw`. For each key that is an object, return its
     non-blank `video` string.
   - Then scan the object's values in insertion order; return the first object with a non-blank
     `video`.
   - Only the `video` field is used here (not `videoUrl`/`hlsUrl`/`url`).
10. Store the result **including `null` (misses)** for 24 h, so a non-match is not retried for a day.
11. Return the URL.

### 7.3 Failure isolation

Identical to §5.6: 401 clears the token and yields `null`; other non-200 yields `null`; any exception
except cancellation yields `null`; cancellation is rethrown. No propagation to UI.

### 7.4 Threading

Caches are process-wide and concurrency-safe; any transport client is created on first use. No
additional serialization is used beyond the token provider's coordination. Concurrent identical
misses may issue duplicate requests; that is allowed.

---

## 8. Apple Music album/song canvas provider (`:applecanvas`)

Package `com.revenge.applecanvas`. Kotlin `object`. Returns `CanvasArtwork` whose `animated` field
holds the HLS URL. Depends on `com.revenge.canvas.AppleMusicTokenProvider` and
`com.revenge.canvas.CanvasArtwork`.

### 8.1 Public API

```kotlin
suspend fun getByAlbumArtist(album: String, artist: String, storefront: String = "us"): CanvasArtwork?
suspend fun getBySongArtist(song: String, artist: String, album: String? = null, storefront: String = "us"): CanvasArtwork?
suspend fun getByAlbumId(albumId: String, storefront: String = "us"): CanvasArtwork?
```

### 8.2 Caching contract

- Process-wide, concurrency-safe in-memory cache, TTL 24 h.
- Cache key = `"<prefix>|" + lowercase(trim(part))` for each part joined by `|`:
  - album: `("album", album, artist, storefront)`
  - song: `("song", song, artist, album ?: "", storefront)`
  - id: `("id", albumId, storefront)`
- `getByAlbumArtist` / `getBySongArtist`: cache **only positive** results; a `null` is not cached, so
  transient failures may be retried.
- `getByAlbumId`: cache results **including misses** for 24 h.
- A valid entry is `expiresAt > now`; evaluate "now" once per call before lookup and again when
  storing the new expiry.

### 8.3 Album/song lookup flow

Inputs: `term`, `artist`, `album?`, `storefront`, `types` (`albums` or `songs`).

1. If `term` or `artist` is blank → `null`.
2. Build the free-text query:
   - start with `term` if it already contains the artist (ignore case), else `"$artist $term"`;
   - if `album` is non-blank and the query does not already contain it (ignore case), append
     `" $album"`.
3. `GET https://amp-api.music.apple.com/v1/catalog/{storefront}/search` with params
   `term=<query>`, `types=<types>`, `limit=10`, `extend=editorialVideo`, `include=albums`.
4. Read `results.<types>.data[]`; for each object with `attributes`, compute a candidate score
   (§8.4). Drop candidates whose score is `null`. Sort by score descending (stable for ties).
5. For each candidate with score `>= 12`:
   - `isSong = item.type == "songs"`.
   - `resultArtist = attributes.artistName ?: ""`.
   - Resolve the owning album id (§8.5); if unresolved, skip.
   - If `attributes.editorialVideo` directly yields a non-blank URL, return:
     `name = attributes.name`,
     `artist = resultArtist`,
     `albumId = resolved id`,
     `albumName = isSong ? attributes.collectionName : attributes.name`,
     `animated = direct URL`.
   - Otherwise perform album lookup with
     `albumId`, `storefront`, `fallbackArtist = resultArtist`,
     `titleOverride = isSong ? attributes.name : null`,
     `artistOverride = isSong ? resultArtist : null`; return it if non-null.
6. Otherwise `null`.

Album lookup:
- blank id or id starting with `pl.` → `null`.
- `GET https://amp-api.music.apple.com/v1/catalog/{storefront}/albums/{albumId}` with
  `extend=editorialVideo`, `include=tracks`, `platform=web`.
- 401 → `clearToken()`, `null`; non-200 → `null`; malformed body → `null`.
- Map per §8.6.

### 8.4 Candidate scoring (exact, observable)

Returns `null` (reject) or an integer score.

Reject if:
- any of `attributes.name` or `attributes.collectionName` is blacklisted (§8.7), or
- the returned artist does not match the requested artist per §8.8.

Score:
- artist: `10` if `artistName` equals the requested artist (ignore case), else `5`.
- title:
  - name equals `term` (ignore case) → `+15`
  - name contains `term` or `term` contains name (ignore case) → `+7`
  - otherwise → `-10`
- edition words, applied independently for each of `deluxe`, `expanded`, `remastered`, `remix`,
  `version`, `edit`, `mix`, `bonus`:
  - present in **both** term and name (ignore case) → `+5`
  - present in name but not term → `-3`
  - present only in term → `0`
- album/collection, only if `album` is non-blank and `collectionName` is non-blank:
  - equal (ignore case) → `+20`
  - either contains the other (ignore case) → `+10`
  - otherwise `0`

Reference values (used by tests):
- exact album `Abbey Road` by `The Beatles` with no album hint → **25**.
- exact song `Come Together` by `The Beatles` on collection `Abbey Road` with album hint
  `Abbey Road` → **45**.
- A matching album plus an unexpected `(Deluxe)` marker scores strictly lower than the plain album.

### 8.5 Album-id resolution

Given a search item and its attributes, for `item.type == "songs"` the id is the first available of:
1. `item.relationships.albums.data[0].id`
2. `attributes.collectionId`
3. the catalog-URL id extraction rule (§8.5)

For every other type the id is `item.id`.

Return the id only if it is non-blank and does not start with `pl.` (playlist); otherwise `null`.

**Album id from a catalog URL.** From a URL shaped
`https://music.apple.com/.../album/<slug>/<id>?i=...`, the id is the text after `/album/`, cut at
`?`, then after the last `/`; it is accepted only when non-blank and **all characters are digits**.
A missing URL or a non-numeric id yields no id.

### 8.6 Response mapping (album endpoint)

The album-endpoint mapping rule is observable as follows (`titleOverride` and `artistOverride` are
supplied when the lookup originated from a song):

1. `root.data[0]` object with `attributes`; otherwise `null`.
2. `albumName = attributes.name ?: ""`; if blacklisted → `null`.
3. Extract `attributes.editorialVideo`; if absent or no URL → `null`.
4. Return `CanvasArtwork`:
   - `name = titleOverride ?: albumName`
   - `artist = artistOverride ?: attributes.artistName ?: fallbackArtist`
   - `albumId = albumId`
   - `albumName = albumName`
   - `animated = extracted URL`
   - all other fields null.

### 8.7 Editorial video extraction (observable precedence)

- Try these keys in fixed order: `motionSquareVideo1x1`, `motionDetailSquare`, `motionTallVideo3x4`,
  `motionDetailTall`, `motionDetailRaw`, `motionDetailStatic`; for each object use the first non-blank
  of `video`, `videoUrl`, `hlsUrl`, `url`.
- Then scan values in insertion order; for the first object with a non-blank one of those fields,
  return it.
- No usable field → `null`.

### 8.8 Blacklist and artist matching

**Blacklist.** A text is not blacklisted when blank; otherwise it is blacklisted when the lowercased
text contains any of: `playlist`, `set list`, `essentials`, `session`, `dj mix`, `mixed`,
`apple music`, `today's hits`.

**Artist matching.** A returned and a requested artist match iff they are equal (ignore case) or
either contains the other (ignore case). Blank handling falls out of the contains comparison.

### 8.9 Helper API

No helper functions, names, signatures or types are part of the contract. The query-building,
cache-identity, scoring, id-resolution and mapping rules are fully specified in §8.2–§8.6 and are
the contract.

### 8.10 Failure isolation and logging

- 401 clears the shared token and returns `null`.
- Non-200 and malformed JSON return `null`; a request failure is logged with a stable prefix
  `AppleMusicCanvas: ` to stdout and, in the search/album catch paths, a stack trace is printed.
  Logging is diagnostic only and must not affect return values.
- Any exception except cancellation → `null`; cancellation is rethrown.
- HTTP client: connect 15 s, request 25 s, socket 25 s; gzip/deflate; HTTP cache; `expectSuccess`
  false.

---

## 9. Artist video (`:artistvideo`)

### 9.1 `ArtistVideoResponse`

Package `com.revenge.artistvideo`. `@Serializable data class` with all fields optional and default
`null`:

| JSON key | Property | Type |
|---|---|---|
| `name` | `name` | `String?` |
| `artist` | `artist` | `String?` |
| `albumId` | `albumId` | `String?` (serial name `albumId`) |
| `static` | `static` | `String?` |
| `animated` | `animated` | `String?` |
| `videoUrl` | `videoUrl` | `String?` |

Derived `preferredAnimationUrl: String?` = `animated ?: videoUrl` (a blank non-null `animated` still
wins; callers must filter blanks). No snippet fields exist on this type.

Decoding contract: unknown keys ignored, explicit nulls accepted, lenient parsing.

### 9.2 `ArtistVideoCanvasProvider`

Package `com.revenge.artistvideo`. Kotlin `object`.

```kotlin
suspend fun getBySongArtist(
  song: String,
  artist: String,
  album: String? = null,
  duration: Int? = null,
): ArtistVideoResponse?
```

External protocol:
- Base URL: `https://artwork-archivetune.koiiverse.cloud/` (GET).
- Query params: `s=<song>`, `a=<artist>`, `al=<album>` only when `album != null`,
  `d=<duration>` only when `duration != null && duration > 0`.
- No authentication, no custom headers required.
- Success: HTTP 200; body decoded as `ArtistVideoResponse`.

Behaviour:
1. Cache key = trimmed lowercased `song`, `artist`, `album.orEmpty()`,
   `duration?.toString().orEmpty()` joined by `|`.
2. Valid cache entry (`expiresAt > now`) → return stored value (may be `null`). Expired entries are
   treated as absent.
3. Fetch; store the result **including `null`** for 60 000 ms.
4. There is **no blank-input guard**: blank song/artist still produce a request and are cached. This
   matches baseline behaviour and must not be "fixed" silently because callers rely only on `null`.
5. Decode failure after a 200 → `null` (still cached negatively).
6. Any exception except cancellation → `null`; cancellation rethrown.
7. HTTP client: 15 s connect, 30 s request/socket, gzip/deflate, HTTP cache, `expectSuccess` false.

### 9.3 `ArtistVideo` composable

Package `com.revenge.artistvideo`.

```kotlin
@Composable
fun ArtistVideo(
  videoUrl: String,
  modifier: Modifier = Modifier,
  onClick: () -> Unit,
)
```

`onClick` has no default and is required.

Observable UI/player contract:

- Renders a `Box` with `modifier`, clipped to a 4 dp rounded rectangle, with a 30 % black scrim
  background, clickable invoking `onClick`.
- Inside, a Media3 `ExoPlayer` surface is hosted in an `AspectRatioFrameLayout` with
  `RESIZE_MODE_ZOOM`, containing a transparent `TextureView` that fills the parent.
- The player is muted (`volume = 0f`), repeats one item (`REPEAT_MODE_ONE`), and declares audio
  attributes usage `C.USAGE_MEDIA` / content type `C.AUDIO_CONTENT_TYPE_MOVIE` (attributes are
  declared even though output is muted).
- The surface is invisible until the player reports its first rendered frame; then it fades to fully
  opaque over 400 ms (animated float state). While invisible, whatever the caller drew behind the
  composable remains visible.
- The player is created once per composition and reused across URL changes. Changing `videoUrl`:
  - resets the first-frame flag (fade back to transparent),
  - trims the URL; blank → stop playback and clear media items,
  - otherwise sets a `MediaItem` with the URL and a MIME type from §9.4, prepares, and plays.
- `onDispose` removes the frame listener and releases the player. Textures are not manually detached.
- Data source stack: OkHttp-backed `DefaultDataSource` feeding `DefaultMediaSourceFactory`.
- No audio focus, wake lock, or lifecycle pause beyond composition disposal is implemented here;
  callers are responsible for visibility/lifecycle.

**MIME selection.** Lowercase the URL; if it contains `mp4` → `video/mp4`; otherwise (including
`.m3u8`, unknown, or empty) → `application/x-mpegURL`.

MIME selection is substring-based: `clip.mp4?token=...` and uppercase variants classify as MP4;
`master.m3u8` and `stream/playlist` classify as HLS.

---

## 10. Cross-module and downstream contracts

These are required for the app to compile and behave; they are not owned by this cluster but constrain
it.

### 10.1 Imports consumed by `:app`

- `com.revenge.canvas.CanvasArtwork`
- `com.revenge.canvas.TidalCanvasProvider`
- `com.revenge.canvas.AppleMusicArtistBackgroundProvider`
- `com.revenge.canvas.AppleMusicTokenProvider`
- `com.revenge.applecanvas.AppleMusicCanvasProvider`
- `com.revenge.artistvideo.ArtistVideo`
- `com.revenge.artistvideo.ArtistVideoCanvasProvider` (available for future wiring)

Signatures and derived properties above are the exact shapes these call sites use. Any change to
nullability or defaults is a breaking change.

### 10.2 Expected caller orchestration (described, not implemented here)

The app chains providers itself, sequentially, on `Dispatchers.IO`:

1. If an album title is known, `AppleMusicCanvasProvider.getByAlbumArtist`.
2. Spotify canvas (app-side).
3. `AppleMusicCanvasProvider.getBySongArtist`.
4. `TidalCanvasProvider.getBySongArtist`.

At each step a result is accepted only when `preferredAnimationUrl` is non-blank. The provider layer
must therefore be safe to call repeatedly and must not assume it is the only provider tried.

The app then validates `artist`/`name`/`albumName` against the requested metadata and skips results
whose `isYouTubeSnippet` is true (unless it explicitly wants YouTube). Providers must populate
`name`, `artist` and `albumName` accurately from the service response; leaving them null is allowed
by the type but reduces acceptance by the caller.

Album screens use `TidalCanvasProvider.getByAlbumArtist` and require `albumName` to equal the
requested album title after trimming (case-insensitive) for fallback acceptance.

Artist screens call `AppleMusicArtistBackgroundProvider.getByArtistName(artistName)` and pass a
non-null `String` directly to `ArtistVideo`. `ArtistVideoCanvasProvider.getBySongArtist(..., album,
duration)` results are consumed through `preferredAnimationUrl`.

### 10.3 Persistence contract

The app serializes fetched `CanvasArtwork` to `filesDir/canvas_<mediaId>.json` and reads it back with
a tolerant `Json`. Therefore §4.1/§4.3 are hard compatibility requirements; do not rename fields,
add required fields, or change null defaults.

### 10.4 Split-package note

`:app` currently contains `app/src/main/kotlin/com/revenge/canvas/YouTubeVideoCanvasProvider.kt` in
package `com.revenge.canvas` (a split package across `:app` and `:canvas`). This cluster must not
define a duplicate `YouTubeVideoCanvasProvider`. The long-term architecture contract moves or
namespaces it; doing so is a separate change and must update all imports (`ArtistViewModel` imports
it from `com.revenge.canvas`).

### 10.5 Compile-required values

Only names/signatures are compile-relevant. Constants in §5–§9 are behavioural and private/internal;
implementers may store them differently but must preserve the observable values.

---

## 11. External services — protocol, data mapping, isolation, permissions

| Service | Endpoint(s) | Auth | Request data | Response data used | Failure isolation |
|---|---|---|---|---|---|
| TIDAL v1 | `GET https://api.tidal.com/v1/search` | header `X-Tidal-Token` (fixed public token) | `query`, `limit=10`, `types=TRACKS|ALBUMS`, `countryCode` | `*.items[]`: `title`, `id`, `artist.name`, `artists[].name`, `album.title`, `album.videoCover`, `videoCover` | non-200/parse/transport → `null`; cancellation rethrown; misses uncached |
| Apple AMP (search) | `GET https://amp-api.music.apple.com/v1/catalog/{storefront}/search` | `Authorization: Bearer <AMPWebPlay JWT>` | `term`, `types`, `limit` (3 for artists, 10 for albums/songs), `extend`, `include` | `results.<type>.data[]`: `id`, `type`, `attributes.name`, `artistName`, `collectionName`, `collectionId`, `url`, `editorialVideo`, `relationships.albums.data[].id` | 401 clears token → `null`; non-200/malformed → `null`; cancellation rethrown |
| Apple AMP (artist) | `GET .../v1/catalog/{storefront}/artists/{id}` | same bearer | `extend=editorialVideo,editorialArtwork`, `include=albums`, `views=featured-albums,top-music-videos`, `platform=web` | `data[0].attributes.editorialVideo`, `editorialArtwork`, `views.featured-albums.data[].attributes.editorialVideo`, `relationships.albums.data[...]` | as above; miss cached 24 h |
| Apple AMP (album) | `GET .../v1/catalog/{storefront}/albums/{albumId}` | same bearer | `extend=editorialVideo`, `include=tracks`, `platform=web` | `data[0].attributes.name`, `artistName`, `editorialVideo` | as above; miss cached only for `getByAlbumId` |
| Apple web player | `https://music.apple.com/us/browse`, `https://beta.music.apple.com`, then `/assets/index*.js` | none | desktop UA; `Accept: text/html` for pages | HTML → bundle path; JS → JWT strings | each source skipped on failure; overall fallback token |
| Artist-video archive | `GET https://artwork-archivetune.koiiverse.cloud/` | none | `s`, `a`, `al?`, `d?` | `ArtistVideoResponse` JSON | all failures → `null`; negative results cached 60 s |

Permissions and platform:

- All endpoints are HTTPS; no cleartext exceptions are needed.
- Network access requires the Android `INTERNET` permission. `:artistvideo` declares no permissions;
  the consuming app must. Any future network use in the JVM modules is the host's responsibility.
- No user accounts, cookies, or OAuth are used. The Apple token is an unauthenticated web-player
  token; the TIDAL token is a public client token. Neither may be attributed to the end user.
- No device identifiers, geolocation, or personal data are sent. Storefront/country comes from the
  device locale only.
- `User-Agent` spoofs a desktop Chrome browser for Apple endpoints; this is required behaviour.

---

## 12. Determinism and test seams

Acceptance tests must run without live network or timing flakiness, and the public API must not
change to enable that. The seam shape is the implementer's choice; the contract is only that the
observable rules in §5–§9 are exercisable deterministically:

1. Transport is injectable per provider, so tests can supply canned HTTP responses (mock engine or
   local mock server). Production uses a real HTTP client.
2. Time is controllable for every cache-expiry and token-expiry decision (injectable clock or
   equivalent), so TTLs never depend on wall-clock flakiness.
3. The pure decision rules in this spec (cover-id formatting, artist matching, query building,
   candidate scoring, album-id and URL extraction, editorial precedence, MIME classification, name
   scoring) are exercisable without a network call. Whether via internal functions or a test facade
   is not contractual.
4. Token discovery is exercisable from supplied HTML/JS text fixtures.

All fixtures must be literal JSON/JS strings in test sources; no files outside the repository and no
network.

---

## 13. Acceptance tests

Each test below is deterministic: no real network, fixed clock, literal fixtures, JUnit 4 assertions.
IDs are stable and may be used as test method names.

### 13.1 `CanvasArtwork`

| ID | Given | Then |
|---|---|---|
| CA-1 | `animated="https://cdn/a.mp4"`, `videoUrl="https://cdn/v.mp4"` | `preferredAnimationUrl == "https://cdn/a.mp4"` |
| CA-2 | only `videoUrl`; and an empty instance | `preferredAnimationUrl` is the video URL; empty instance → `null` |
| CA-3 | `animated=""`, `videoUrl="v"` | `preferredAnimationUrl == ""` (blank wins; caller filters) |
| CA-4 | `clipStartMs=1000`; `clipEndMs=2000` | `isYouTubeSnippet == true` for each |
| CA-5 | `videoUrl="https://rr1.googlevideo.com/videoplayback"`; `animated="https://www.youtube.com/watch?v=x"` | `isYouTubeSnippet == true` for both |
| CA-6 | `animated="https://resources.tidal.com/videos/a/b/c/d/e.mp4"` | `isYouTubeSnippet == false` |
| CA-7 | JSON fixture with all documented keys | every property decoded, including `albumId` from serial name |
| CA-8 | encode→decode a fully populated instance | decoded equals original |
| CA-9 | `{}` | all nullable fields null; no exception |

**Failure path:** CA-10 — JSON with unknown keys and explicit `null`s decodes without error.

### 13.2 `TidalCanvasProvider` pure logic

| ID | Given | Then |
|---|---|---|
| TD-1 | `"aa-bb-cc-dd-ee"` | `https://resources.tidal.com/videos/aa/bb/cc/dd/ee/1280x1280.mp4` |
| TD-2 | `""`, 3 segments, 6 segments, `"aa-bb--dd-ee"` | all `null` |
| TD-3 | `("The Weeknd","The Weeknd")`, case variants | `true` |
| TD-4 | `"The Weeknd feat. Daft Punk"` vs `"The Weeknd"`; `"Calvin Harris & Dua Lipa"` vs `"Dua Lipa"`; `"Drake, Future"` vs `"Future"`; `"Charli xcx"` vs `"Charli XCX"` | all `true` |
| TD-5 | `"The Weeknd"` vs `"Daft Punk"`; `""` vs `"Daft Punk"` | `false` |

Network/cache tests (with MockEngine + clock):
| ID | Given | Then |
|---|---|---|
| TD-6 | blank song or artist | returns `null`, zero requests |
| TD-7 | 200 response with a matching TRACKS item with 5-segment `album.videoCover` | mapped fields exactly: `name`, `artist`, `videoUrl`, `albumName`; other fields null |
| TD-8 | 200 with a title mismatch or artist mismatch first, match second | returns the second item |
| TD-9 | non-200; engine throws IOException; malformed JSON | each → `null`, no exception |
| TD-10 | successful lookup repeated within TTL | second call issues no request |
| TD-11 | failed lookup repeated | second call issues a request again (misses uncached) |
| TD-12 | engine throws `CancellationException` | rethrown unchanged |

### 13.3 `AppleMusicTokenProvider`

| ID | Given | Then |
|---|---|---|
| TK-1 | bundle text containing two valid `AMPWebPlay` JWTs with expiries T1<T2 (both > now+5 min) | `getToken()` returns the T2 token |
| TK-2 | valid token expiring within 5 min | ignored; fallback used |
| TK-3 | token with issuer other than `AMPWebPlay` | ignored |
| TK-4 | garbage JWT / non-3-part string | ignored without throwing |
| TK-5 | discovery throws for both pages | bundled fallback returned, non-empty |
| TK-6 | two concurrent `getToken()` calls on a cold cache | exactly one discovery sequence |
| TK-7 | `getToken()` then advance clock past expiry−5 min | discovery runs again |
| TK-8 | `clearToken()` then `getToken()` | discovery runs again |
| TK-9 | engine throws `CancellationException` | rethrown |

### 13.4 `AppleMusicArtistBackgroundProvider`

| ID | Given | Then |
|---|---|---|
| AB-1 | exact name candidate | score 10; substring-only candidate scores 5; unrelated scores below threshold |
| AB-2 | editorial object with several preferred keys and `video` values | returns the first key in the fixed order |
| AB-3 | editorial object with only an unknown key containing `video` | returns that fallback URL |
| AB-4 | `editorialVideo` absent, `editorialArtwork` present | returns artwork URL |
| AB-5 | artist attributes have no motion but a featured album does | returns the album's motion URL |
| AB-6 | no motion anywhere on a 200 | returns `null` and caches the miss |
| AB-7 | first call misses; second within 24 h | second issues no request and returns `null` |
| AB-8 | 401 on search | token invalidated (`clearToken` invoked) and `null` returned |
| AB-9 | 500 / IOException / malformed JSON | `null`, no exception |
| AB-10 | blank artist name | `null`, zero requests |
| AB-11 | candidate score 5 (substring) | fetched (threshold is 4 or more) |
| AB-12 | engine throws `CancellationException` | rethrown |

### 13.5 `AppleMusicCanvasProvider` logic

Existing pure-logic cases (must all pass exactly):

| ID | Given | Then |
|---|---|---|
| AM-1 | query for term `The Beatles Abbey Road`, artist `The Beatles`, no album | `"The Beatles Abbey Road"` (no duplication) |
| AM-2 | query for term `Come Together`, artist `The Beatles`, album `Abbey Road` | `"The Beatles Come Together Abbey Road"` |
| AM-3 | query for term `25`, artist `Adele`, album `25` | `"Adele 25"` (album already contained) |
| AM-4 | exact album candidate | score `25`, `>= 12` |
| AM-5 | exact song on matching album | score `45` |
| AM-6 | wrong artist | `null` |
| AM-7 | playlist/compilation name or collection (`Today's Hits`, `... Essentials`) | `null` |
| AM-8 | `(Deluxe)` unexpected | score strictly lower than plain |
| AM-9 | song item with `relationships.albums.data[0].id` | resolves that id |
| AM-10 | song item with no relationship, `collectionId` present; then only `url` present | falls back to `collectionId`, then numeric id from URL |
| AM-11 | item id `pl.abc` | `null` |
| AM-12 | catalog-URL id extraction: valid URL / slug-only URL / null | id / no id / no id |
| AM-13 | editorial with `motionDetailSquare.video` and `motionSquareVideo1x1.hlsUrl` | preferred key wins → `square-hls` |
| AM-14 | editorial with unknown object `videoUrl` | returns it |
| AM-15 | editorial object has only dimensions | `null` |
| AM-16 | album response with motion | all mapped fields: name, artist, albumId, albumName, animated |
| AM-17 | song mapping with title/artist overrides | name = song, albumName = album |
| AM-18 | blacklisted album response | `null` |
| AM-19 | album without editorial video | `null` |
| AM-20 | two equivalent song lookups differing only in input case and surrounding whitespace | the second is served from the first's cache entry (no second request) |

Network/cache/failure tests:
| ID | Given | Then |
|---|---|---|
| AM-21 | blank term/artist on album or song lookup | `null`, zero requests |
| AM-22 | 401 | `clearToken` invoked, `null` |
| AM-23 | non-200, malformed JSON, engine IOException | `null`, no exception |
| AM-24 | album/song lookup returns `null` | **not** cached; next call requests again |
| AM-25 | `getByAlbumId` returns `null` (including blank/`pl.` id) | cached miss; next call within 24 h does not request |
| AM-26 | direct `editorialVideo` on a search candidate | album endpoint is not called |
| AM-27 | direct editorial absent but album endpoint has it | follow-up call made and mapped |
| AM-28 | engine throws `CancellationException` | rethrown from search and album paths |
| AM-29 | successful result repeated within 24 h | no request; after expiry | request again |

### 13.6 `ArtistVideoResponse` and MIME

| ID | Given | Then |
|---|---|---|
| AV-1 | full fixture with all six fields | all decode exactly |
| AV-2 | `{}` | all fields null, `preferredAnimationUrl` null |
| AV-3 | explicit nulls + unknown/extra nested key | decodes; unknowns ignored |
| AV-4 | `animated` set, `videoUrl` set | prefers `animated` |
| AV-5 | `animated=null`, `videoUrl` set | falls back to `videoUrl` |
| AV-6 | `animated=""`, `videoUrl` set | returns `""` (blank wins) |
| AV-7 | MIME classification for `.m3u8`, uppercase, `clip.mp4?token=abc`, unknown, empty | HLS, HLS, MP4, HLS, HLS respectively |

### 13.7 `ArtistVideoCanvasProvider`

| ID | Given | Then |
|---|---|---|
| AP-1 | song/artist only | GET has `s` and `a`, no `al`/`d` |
| AP-2 | album and duration > 0 | GET includes `al` and `d` |
| AP-3 | duration 0 or negative | `d` omitted |
| AP-4 | 200 valid JSON | decoded response returned |
| AP-5 | 200 malformed JSON | `null` and the miss is cached for 60 s |
| AP-6 | non-200 / IOException | `null`, cached negatively |
| AP-7 | second call within 60 s | no request, same value (including `null`) |
| AP-8 | clock advanced past 60 s | request issued again |
| AP-9 | engine throws `CancellationException` | rethrown |
| AP-10 | blank song/artist | still issues a request (documented baseline behaviour) |

### 13.8 `ArtistVideo` surface (Robolectric or instrumentation)

| ID | Given | Then |
|---|---|---|
| AS-1 | initial composition | player created once, volume 0, repeat one, surface alpha 0 before first frame |
| AS-2 | player reports first frame | alpha animates to 1 over ~400 ms |
| AS-3 | `videoUrl` changes to a new non-blank URL | first-frame flag resets, media item replaced, prepare/play requested |
| AS-4 | `videoUrl` becomes blank | playback stops and media items cleared |
| AS-5 | composition disposed | listener removed and player released |
| AS-6 | click | `onClick` invoked exactly once |
| AS-7 | `.m3u8` URL | media item MIME is `application/x-mpegURL`; `.mp4` URL → `video/mp4` |
| AS-8 | recalculating parent | TextureView fills, `RESIZE_MODE_ZOOM` set |

Compose/Media3 tests may use a fake `Player` or Robolectric shadows; they must assert the contract in
§9.3, not internal structure.

---

## 14. Threading, cancellation and error taxonomy (consolidated)

- All provider functions are `suspend` and run on the caller's dispatcher (no internal dispatcher
  switch). Callers use `Dispatchers.IO`.
- Shared state: process-wide, concurrency-safe caches in every provider and in the token provider;
  no other mutable shared state. Container and synchronization choices are not contractual, but
  concurrent calls must be safe. The token provider must serialize discovery so that concurrent
  cold-cache callers trigger at most one discovery sequence (TK-6).
- Wall-clock time drives cache/token expiry; tests substitute a controllable clock.
- Error classes:
  - **Cancellation** (`CancellationException`, both kotlinx and kotlin.coroutines variants):
    always rethrown, never converted to `null`, in every provider and every nested try.
  - **Transport error** (connect/socket/timeout/IO): `null`.
  - **HTTP non-success**: `null`; only 401 additionally invalidates the Apple token.
  - **Malformed payload / wrong shape / decode error**: `null`.
  - **No match / filtered out**: `null`.
  - **Blank required input** (all providers except `ArtistVideoCanvasProvider`): `null` with no
    request.
- Providers log at most diagnostic messages to stdout; logging never changes control flow and never
  includes token values.

---

## 15. Explicit non-goals

- **Listen Together wire contract: not applicable.** No listen-together protocol, message type, tag,
  proto, or XML artefact exists in `canvas`, `applecanvas`, or `artistvideo`, and no file in this
  cluster participates in that feature. Reproducing such a wire contract here would invent
  requirements; it belongs to the module that owns the session protocol.
- No protobuf, XML resources, databases, DataStore keys, DI graph, Hilt annotations, or Android
  services are part of this cluster.
- No UI beyond `ArtistVideo`; no player orchestration, queueing, caching to disk, or preference
  gating.

---

## 16. Known risks and review flags

1. **Apple bearer token** is scraped from a public web bundle with a bundled fallback. It will break
   when Apple rotates bundles/tokens; the contract intentionally degrades to `null`, and callers fall
   back to TIDAL/cover art. Do not surface token errors as user-facing failures.
2. **TIDAL public token** is a fixed client token; if TIDAL rotates it, all TIDAL lookups return
   `null`. Keep it a single replaceable constant.
3. **Third-party archive host** (`artwork-archivetune.koiiverse.cloud`) is unaffiliated and may
   disappear or change shape; its 60 s negative cache limits retry pressure. Treat as best-effort.
4. **Media3 HLS artifact missing** from the current `:artistvideo` dependency list while the surface
   defaults to HLS — must be added or HLS playback breaks at runtime (see §2.3).
5. **Apple `implementation(project(":canvas"))`** hides `CanvasArtwork` from consumers of
   `:applecanvas`; change to `api` (see §2.2).
6. **Split package** `com.revenge.canvas` exists in both `:app` and `:canvas` because
   `YouTubeVideoCanvasProvider` lives in the app. Do not add duplicate symbols; future move is out of
   scope.
7. **`AppleMusicArtistBackgroundProvider` caches misses for 24 h**, unlike
   `AppleMusicCanvasProvider`'s album/song lookups and `TidalCanvasProvider`. This asymmetry is
   deliberate baseline behaviour and is asserted by AB-7 / AM-24 / TD-11; do not unify it.
8. **`ArtistVideoCanvasProvider` has no blank-input guard** and caches nulls; AP-10 locks this in.
9. **`ArtistVideo` never pauses for visibility**; it plays while composed. Callers gate composition.
10. **Storefront and TIDAL country come from the default locale**, so results are locale-dependent;
    tests must inject/override locale where they assert request parameters.
