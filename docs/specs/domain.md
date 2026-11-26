# Clean-Room Specification — `:domain`

Status: M2 specification (behavioural contract only).
Module: `:domain` — directory `domain`, Gradle path `:domain`.
Declared dependencies: none between project modules (pure JVM).
External dependencies: Kotlin stdlib, Kotlin coroutines core (`Flow`, `combine`, `first`), JUnit 4 and `kotlinx-coroutines-test` for tests only.
JVM target: toolchain 21, Kotlin JVM plugin.
Clean-room namespace (must be preserved exactly): `com.revenge.domain.{models,repositories,usecase}`.

This spec describes required behaviour; any algorithm shape shown is illustrative. Implementers must
design their own structure and will be audited for similarity to the baseline.

This document describes observable behaviour and API shape. It intentionally omits statement-level
implementation. Any implementation that satisfies the contracts and acceptance tests is acceptable.

---

## 1. Module purpose

`:domain` is the innermost layer of the application. It defines the storage- and network-agnostic
entities used across modules, the repository ports that infrastructure modules implement, and pure
recommendation use cases. It must have no Android, Hilt, Room, or network dependency.

Consumers in this repository:

- `:core` implements `SongRepository` and `PlaylistRepository` and maps its persistence entities to
  the domain entities (`core/src/main/kotlin/com/revenge/core/repository/{SongRepositoryImpl,PlaylistRepositoryImpl}.kt`,
  `core/src/main/kotlin/com/revenge/core/mapping/DomainMappers.kt`).
- `:app` binds those implementations into DI (`app/src/main/kotlin/com/revenge/music/di/DomainBindingsModule.kt`).

Because downstream modules construct the data classes with **named arguments** and override exact
repository signatures, every public name, type, parameter name, parameter order and default value in
Sections 2–4 is a hard compatibility contract.

---

## 2. Entities — package `com.revenge.domain.models`

All entities are Kotlin `data class` values:

- `equals`/`hashCode` are structural over all declared fields.
- `copy(...)` is generated and defaults each parameter to the current value.
- Properties are `val` (immutable). All types are non-null unless marked nullable.
- No validation, coercion, or normalisation happens in constructors. Negative values, blank strings
  and inconsistent combinations are accepted as given; only the use cases apply guard rules.

### 2.1 `com.revenge.domain.models.Song`

A playable track independent of any storage or network source.

| Order | Field | Type | Default | Meaning |
|---|---|---|---|---|
| 1 | `id` | `String` | required | Stable unique identifier of the track. |
| 2 | `title` | `String` | required | Display title. |
| 3 | `artists` | `List<ArtistRef>` | required | Ordered performers; may be empty. |
| 4 | `album` | `AlbumRef?` | required (nullable) | Owning album, or `null` when the track has no album. |
| 5 | `durationSeconds` | `Int` | required | Track length in whole seconds. |
| 6 | `thumbnailUrl` | `String?` | required (nullable) | Remote artwork URL, or a local `content://` URI for local tracks, or `null` when unknown. |
| 7 | `isExplicit` | `Boolean` | `false` | Explicit-content flag. |
| 8 | `isLocal` | `Boolean` | `false` | Whether the track is device-local rather than streamed. |
| 9 | `liked` | `Boolean` | `false` | Whether the user has liked the track. |
| 10 | `totalPlayTimeMs` | `Long` | `0L` | Cumulative listening time in milliseconds; recommendation heuristic input. |

Invariants:

- `artists` is a non-null list; an unknown artist set is represented by an empty list, not `null`.
- `album == null` and `thumbnailUrl == null` are both legal and must round-trip through `copy`.
- The defaulted fields must be omittable at every construction site.
- No normalisation: a supplied `artists` list is returned unchanged (same elements and order).

### 2.2 `com.revenge.domain.models.ArtistRef`

Reference to an artist, usable without loading the full artist entity.

| Order | Field | Type | Default | Meaning |
|---|---|---|---|---|
| 1 | `id` | `String` | required | Artist identifier; may be blank. |
| 2 | `name` | `String` | required | Display name; may contain surrounding whitespace. |

### 2.3 `com.revenge.domain.models.AlbumRef`

Reference to an album.

| Order | Field | Type | Default | Meaning |
|---|---|---|---|---|
| 1 | `id` | `String` | required | Album identifier. |
| 2 | `title` | `String` | required | Display title. |

### 2.4 `com.revenge.domain.models.Playlist`

A user-created playlist summary.

| Order | Field | Type | Default | Meaning |
|---|---|---|---|---|
| 1 | `id` | `String` | required | Playlist identifier. |
| 2 | `name` | `String` | required | Display name. |
| 3 | `songCount` | `Int` | `0` | Number of songs currently in the playlist. |
| 4 | `thumbnailUrl` | `String?` | `null` | Cover artwork URL, or `null` when absent. |

`Playlist` contains no song list; the songs are obtained through `PlaylistRepository.getPlaylistSongs`.

---

## 3. Repository ports — package `com.revenge.domain.repositories`

Both are `interface`s with no default method bodies. Implementations live outside this module.
All collection-returning reads are cold `kotlinx.coroutines.flow.Flow` streams that re-emit when the
underlying data changes; callers may collect them repeatedly. Reads never throw for "not found";
not-found is represented by `null` or an empty list. Writes are `suspend` functions that complete
after the change is persisted. The module itself makes no dispatcher or thread-affinity promise;
concurrency behaviour is inherited from the implementation and the collector's context.

### 3.1 `com.revenge.domain.repositories.SongRepository`

```kotlin
fun getSong(id: String): Flow<Song?>
fun getLikedSongs(): Flow<List<Song>>
fun getRecentlyPlayed(limit: Int): Flow<List<Song>>
suspend fun updateLiked(id: String, isLiked: Boolean)
suspend fun incrementPlayTime(id: String, durationMs: Long)
```

Contract:

- `getSong(id)` — stream of the track with that id, or `null` when it does not exist. Must not throw
  for an unknown id. Typically an initial emission immediately follows collection.
- `getLikedSongs()` — stream of all liked tracks, in a stable implementation-defined order. Empty
  list when none. Emitted songs are expected to carry `liked == true`, but consumers must not rely
  on that (see §4.1).
- `getRecentlyPlayed(limit)` — stream of at most `limit` recently played tracks, most recent first,
  de-duplicated by `id` at the implementation level. `limit <= 0` may yield an empty list; callers
  in this module never pass a non-positive value. Implementations may return fewer than `limit`.
- `updateLiked(id, isLiked)` — sets the liked flag; completes when persisted.
- `incrementPlayTime(id, durationMs)` — adds `durationMs` to the track's cumulative
  `totalPlayTimeMs`; completes when persisted.

### 3.2 `com.revenge.domain.repositories.PlaylistRepository`

```kotlin
fun getAllPlaylists(): Flow<List<Playlist>>
fun getPlaylistSongs(playlistId: String): Flow<List<Song>>
suspend fun addSongToPlaylist(playlistId: String, songId: String)
suspend fun removeSongFromPlaylist(playlistId: String, songId: String)
```

Contract:

- `getAllPlaylists()` — stream of all playlists (summaries). Empty list when none.
- `getPlaylistSongs(playlistId)` — stream of the songs in the given playlist, in playlist order.
  Empty list for an unknown playlist; no exception.
- `addSongToPlaylist` / `removeSongFromPlaylist` — mutate membership and complete when persisted.
  Duplicate-add and remove-of-absent-song behaviour is implementation-defined and not exercised by
  this module.

---

## 4. Use cases — package `com.revenge.domain.usecase`

Both use cases are plain classes holding one constructor dependency, are stateless, hold no mutable
fields, are safe to reuse across collections, and perform all work in the caller's coroutine context.

### 4.1 `com.revenge.domain.usecase.GetQuickPicksUseCase`

```kotlin
class GetQuickPicksUseCase(songRepository: SongRepository) {
  operator fun invoke(limit: Int = 20): Flow<List<Song>>
}
```

Produces a "quick picks" shelf of up to `limit` songs built from the liked songs and the recent
listening history.

Observable contract (what is externally required):

- **Limit guard.** A negative `limit` behaves exactly like `0`. With `requested = max(limit, 0)`, a
  `requested` of `0` yields an empty list; the flow is still created and may be collected without
  error.
- **History request size.** The use case must ask `getRecentlyPlayed` for `requested * 2` songs. This
  doubling is observable and normative: for `limit = 4` the repository is asked for 8 (B3). For
  `requested` large enough that `requested * 2` would exceed `Int.MAX_VALUE`, the value must
  saturate rather than overflow, and the call must still complete with a correct bounded list (B9).
- **Reactive emission.** The result is a cold `Flow` that, each time either source emits, emits a new
  list computed from the latest emission of both sources. Collecting again re-subscribes.
- **Candidate set and order.** Candidates are every song from either source, de-duplicated by
  `Song.id`. For a duplicated id the retained copy is the one from the recent source (recent field
  values win); a song present only in the liked source keeps its liked-source values. Candidate order
  is recent-source emission order, then liked-source emission order for songs whose id did not occur
  in the recent source. This order is externally observable through the tie rule below.
- **Score.** For a candidate:
  - `likedBonus` = `50.0` when the candidate's `id` occurs among the liked-source songs' ids, else
    `0.0`. Membership in the liked source is authoritative; the candidate's own `liked` field is not
    consulted.
  - `minutes` = `min(totalPlayTimeMs / 60_000.0, 60.0)` (listening minutes, capped at one hour;
    negative play time yields a non-positive contribution).
  - `score = likedBonus + minutes * 2.0`.
- **Ordering.** Results are ordered by score descending. Equal scores keep candidate order — recent
  songs before liked-only songs. This tie behaviour is normative.
- **Truncation.** At most `requested` items are emitted. If `requested == 0` the emitted list is
  empty.
- **Empty sources.** Both sources empty yields an empty list; never an exception.
- **Errors.** Exceptions from either repository flow propagate to the collector; they are not
  swallowed.

### 4.2 `com.revenge.domain.usecase.ResolveLocalMixUseCase`

```kotlin
class ResolveLocalMixUseCase(songRepository: SongRepository) {
  suspend fun invoke(seedSongId: String, limit: Int = 25): List<Song>
}
```

Expands a seed song into an endless-mix style queue of up to `limit` songs drawn from liked and
recently played tracks, using each source's current value.

Observable contract (what is externally required):

- **Limit guard.** A negative `limit` behaves exactly like `0`. With `requested = max(limit, 0)`, a
  `requested` of `0` returns `emptyList()` and consults no repository method (observable via
  recording fakes; see C6).
- **Seed resolution.** The seed is the first emission of `getSong(seedSongId)`. If the seed is absent
  — the flow emits `null`, or completes without emitting — the call returns `emptyList()`.
- **Seed artist keys.** Derive two comparison sets from the seed's `artists`: its non-blank artist
  ids, and its normalised names `name.trim().lowercase()` with empty results dropped. Name matching
  is whitespace-trimmed and case-insensitive.
- **Source reads and window.** The use case uses the first emission of `getLikedSongs()` and the
  first emission of `getRecentlyPlayed(window)`, where `window = requested * 3`. The 3× window is
  observable and normative: for `limit = 4` the repository is asked for 12 (C7). When
  `requested * 3` would exceed `Int.MAX_VALUE` it must saturate rather than overflow. Whether the two
  sources are read sequentially or concurrently, and in which order, is not part of the contract.
- **Candidate set and order.** Candidates are all liked-source songs except the seed id, followed by
  recent-source songs that are neither the seed nor already present, de-duplicated by `Song.id`
  keeping the first occurrence (the liked-source copy wins for a duplicated id). Candidate order is
  liked-source order first, then recent-only songs in recent-source order. This order is externally
  observable through the tie rule below.
- **Score.** For a candidate:
  - `artistBonus` = `100.0` when the candidate shares an artist with the seed, else `0.0`. "Shares an
    artist" means some candidate artist `a` satisfies either
    `a.id.isNotBlank() && a.id in seedArtistIds`, or `a.name.trim().lowercase() in seedArtistNames`
    (normalised names allow matches when ids differ).
  - `likedBonus` = `30.0` when `song.liked` is `true`, else `0.0`. Unlike §4.1 this reads the entity
    field, not membership in the liked source.
  - `minutes` = `min(totalPlayTimeMs / 60_000.0, 60.0)`.
  - `score = artistBonus + likedBonus + minutes` (one point per listening minute, capped at 60).
- **Ordering.** Results are ordered by score descending. Equal scores keep candidate order — liked
  songs before recent-only songs. This tie behaviour is normative.
- **Truncation.** At most `requested` items are returned. The seed id never appears in the result,
  even when present in the liked or recent sources.
- **Errors.** Exceptions from any repository propagate; they are not swallowed. Nothing is cached
  between calls.

Consequences to preserve:

- A shared-artist song (100) always outranks a merely liked song (30).
- A liked song (30) outranks an unliked, unplayed song (0).
- Heavily played songs saturate at 60 points, so they beat liked-but-unplayed songs but lose to
  shared-artist songs.

### 4.3 Edge rules and normative rule data

The following edge behaviours are externally observable and normative. They were singled out by the
clean-room review and must survive any implementation redesign:

- **Non-positive limits.** A negative limit is identical to zero. Zero yields an empty result; in
  §4.2 a zero limit also performs no repository read.
- **Overflow safety.** For a limit large enough that `requested * 2` (§4.1) or `requested * 3` (§4.2)
  would exceed `Int.MAX_VALUE`, the computation must not overflow, throw, or hang; it must return a
  correct bounded list (B9).
- **Tie-breaking.** Equal scores preserve first-insertion order: recent before liked-only in §4.1;
  liked before recent-only in §4.2.
- **De-duplication.** Always by `Song.id`, first occurrence wins; §4.1 keeps the recent-sourced copy,
  §4.2 keeps the liked-sourced copy.
- **Seed exclusion.** In §4.2 the seed id never appears in the result, even when it is present in the
  liked or recent sources.
- **Artist matching.** Blank artist ids never match (C10); names match after trim + lowercase
  (C2, C3); an id match and a name match are alternatives (OR).
- **Which liked signal is used.** §4.1 scores by membership in the liked source and ignores the
  entity's `liked` field (B8); §4.2 scores by the entity's `liked` field and ignores source
  membership.
- **Play-time saturation.** Each song's listening-time contribution is capped at 60 minutes, so
  scores saturate (B5, C4).

**Normative rule data.** The numeric constants and vocabulary below are part of the contract and must
not be retuned by an implementer: liked bonus `50.0` and play-time weight `2.0` per minute (§4.1);
artist bonus `100.0`, entity-liked bonus `30.0`, and play-time weight `1.0` per minute (§4.2); the
60-minute cap; the `2×` history window (§4.1) and `3×` history window (§4.2); and the default limits
`20` and `25`. The terms "recent source", "liked source", "candidate", and "score" have exactly the
meanings given here. The names, types, defaults, and repository signatures in §2–§3 are likewise
normative and form the hard compatibility contract.

---

## 5. Cross-module compatibility contract

`:core` and `:app` compile against this module. The following usage must remain valid (names
including parameter names and defaults):

- Construct `Song(id=, title=, artists=, album=, durationSeconds=, thumbnailUrl=, isExplicit=,
  isLocal=, liked=, totalPlayTimeMs=)` with every argument named.
- Construct `Playlist(id=, name=, songCount=, thumbnailUrl=)` with every argument named.
- Construct `ArtistRef(id=, name=)` and `AlbumRef(id=, title=)` with named arguments.
- `:core` classes implement `SongRepository` / `PlaylistRepository` with exactly the signatures in
  §3 (return types are `Flow<DomainSong?>`, `Flow<List<DomainSong>>`, `Flow<List<DomainPlaylist>>`).
  Any added parameter, changed nullability, or removed method breaks compilation.
- Downstream code imports `com.revenge.domain.models.{Song, Playlist, ArtistRef, AlbumRef}` and
  `com.revenge.domain.repositories.{SongRepository, PlaylistRepository}`. Do not rename packages or
  move these types.

Dependency direction: `:domain` must not import any other project module.

---

## 6. Target files the implementer must write

Main sources (namespace preserved):

- `domain/src/main/kotlin/com/revenge/domain/models/Song.kt` — `Song`, `ArtistRef`, `AlbumRef`.
- `domain/src/main/kotlin/com/revenge/domain/models/Playlist.kt` — `Playlist`.
- `domain/src/main/kotlin/com/revenge/domain/repositories/SongRepository.kt`.
- `domain/src/main/kotlin/com/revenge/domain/repositories/PlaylistRepository.kt`.
- `domain/src/main/kotlin/com/revenge/domain/usecase/GetQuickPicksUseCase.kt`.
- `domain/src/main/kotlin/com/revenge/domain/usecase/ResolveLocalMixUseCase.kt`.

Tests:

- `domain/src/test/kotlin/com/revenge/domain/models/SongModelTest.kt`.
- `domain/src/test/kotlin/com/revenge/domain/usecase/GetQuickPicksUseCaseTest.kt`.
- `domain/src/test/kotlin/com/revenge/domain/usecase/ResolveLocalMixUseCaseTest.kt`.

Build file (unchanged): `domain/build.gradle.kts` — `org.jetbrains.kotlin.jvm`, toolchain 21,
`implementation(coroutines-core)`, `testImplementation(junit)`, `testImplementation(coroutines-test)`.

Test fakes may be private nested classes implementing `SongRepository`; they must implement all five
methods, including no-op `updateLiked` / `incrementPlayTime`.

---

## 7. Acceptance tests

Deterministic, requirement-driven cases. Each should be expressible against a fake
`SongRepository` driven by in-memory lists. Use `runTest` and collect with `.first()`.

Helper entities for tests: a song factory with `id`, `liked`, `playMinutes` (converted to
`totalPlayTimeMs = playMinutes * 60_000`), and `artists`.

### 7.1 Entities (`SongModelTest`)

A1. Constructing `Song` with only the required six arguments yields `isExplicit == false`,
`isLocal == false`, `liked == false`, `totalPlayTimeMs == 0L`, and `album`/`thumbnailUrl` as
supplied (`null` allowed).
A2. A fully populated `Song` preserves the exact `artists` list instance/values, `album.title`,
`totalPlayTimeMs`, and `liked`.
A3. `Playlist(id, name)` defaults to `songCount == 0` and `thumbnailUrl == null`; `copy()` equals the
original; `copy(name = "...")` changes only the name.
A4. Data-class structural equality: two `Song`s with identical fields are equal and share a hash
code; differing one field makes them unequal.

### 7.2 `GetQuickPicksUseCase`

B1. **Ranking.** Liked source `[liked(liked, 2 min)]`, recent `[rarely(0.5 min), heavy(45 min)]`;
limit 3 ⇒ ids in order `[heavy, liked, rarely]` (90 vs 52 vs 1 points).
B2. **De-duplication.** A song present in both liked and recent appears exactly once; result size 2
when liked `[shared]` and recent `[shared, extra]`, limit 10.
B3. **History window.** limit 4 ⇒ repository receives `getRecentlyPlayed(8)`.
B4. **Limit respected.** Ten recent, limit 3 ⇒ 3 results.
B5. **Play-time cap and stable ties.** Recent `[oneHour(60 min), tenHours(600 min)]`, limit 2 ⇒ both
score 120; result order `[one-hour, ten-hours]` (insertion order preserved).
B6. **Empty sources** ⇒ empty list.
B7. **Non-positive limit.** limit 0 and limit -3 each ⇒ empty list.
B8. **Liked-source membership, not field.** A recent song with `liked == false` that is also in the
liked source scores with the 50-point bonus.
B9. **Overflow guard (behavioural).** A very large limit must not throw and must complete; the
returned list length is bounded by available candidates.

### 7.3 `ResolveLocalMixUseCase`

C1. **Shared artist first, seed excluded.** Seed with artist Radiohead (liked); candidates
`related(Radiohead, unliked, 0 min)` and `unrelated(Daft Punk, liked, 0 min)` ⇒ `[related,
unrelated]`; seed absent.
C2. **Unknown seed** ⇒ empty list.
C3. **Normalised artist-name match.** Seed artist `("seed-id", "  The Beatles ")`; candidate artist
`("other-id", "the beatles")` ranks first; an unrelated liked song ranks after it ⇒
`[name-match, liked]`.
C4. **Play time beats liked when no artist match.** Seed artist "Seed"; candidates
`liked(liked, 0 min)` and `heavy(unliked, 90 min)` ⇒ `[heavy, liked]` (60 vs 30).
C5. **Limit respected.** Seed plus six others, limit 4 ⇒ 4 results, seed not among them.
C6. **Non-positive limit.** limit 0 and limit -2 ⇒ empty list, and no repository method is consulted
(observable via fakes that record calls, or simply no failure when the fake is empty).
C7. **History window.** limit 4 ⇒ `getRecentlyPlayed(12)`.
C8. **Liked/recent overlap de-duplicated** by id, liked entry retained.
C9. **Seed appearing in liked and recent is excluded** from the result.
C10. **Blank artist ids ignored.** A seed artist with `id == ""` must only match by normalised name,
never by an empty-id match.
C11. **Stable ordering for equal scores** follows liked order before recent-only songs.

---

## 8. Non-goals

- No persistence, caching, logging, threading policy, or serialisation annotations belong in this
  module.
- No additional public API (extra fields, helpers, overloads) is required; adding any is allowed only
  if it preserves §2–§5 exactly.
