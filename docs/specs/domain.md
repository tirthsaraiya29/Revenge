# Clean-Room Specification — `:domain`

Status: M2 specification (behavioural contract only).
Module: `:domain` — directory `domain`, Gradle path `:domain`.
Declared dependencies: none between project modules (pure JVM).
External dependencies: Kotlin stdlib, Kotlin coroutines core (`Flow`, `combine`, `first`), JUnit 4 and `kotlinx-coroutines-test` for tests only.
JVM target: toolchain 21, Kotlin JVM plugin.
Clean-room namespace (must be preserved exactly): `com.revenge.domain.{models,repositories,usecase}`.

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

Normative behaviour:

1. **Limit guard.** Compute `requested = max(limit, 0)` (negative values behave like `0`).
2. **History window.** Request `requested * 2` recent songs, saturating at `Int.MAX_VALUE` when
   `requested > Int.MAX_VALUE / 2` so the multiplication can never overflow. The doubling exists so
   that liked songs displaced by history can still be recovered from the liked source.
3. **Emission.** Return a cold `Flow` that combines `getLikedSongs()` and
   `getRecentlyPlayed(historyWindow)`. The returned flow emits a new list each time either source
   emits, using the latest emission of each.
4. **Candidate set.**
   - Start with the recent list in its emitted order.
   - Then append liked songs that are not already present.
   - De-duplicate by `Song.id`, keeping the **first** occurrence (recent entries win over liked
     entries for the same id, including their field values).
5. **Score.** For each candidate:
   - `likedBonus` = `50.0` if the candidate's `id` appears among the liked-source songs' ids,
     otherwise `0.0`. (Membership in the liked source is authoritative; the candidate's own `liked`
     field is not used here.)
   - `minutes` = `min(totalPlayTimeMs / 60_000.0, 60.0)` (listening time in minutes, capped at one
     hour; negative play time yields a non-positive contribution).
   - `score = likedBonus + minutes * 2.0`.
6. **Ordering.** Sort candidates by score descending using a **stable** sort: equal scores preserve
   candidate insertion order (recent order first, then liked-only songs in liked order).
7. **Truncation.** Take the first `requested` items. If `requested == 0`, the emitted list is empty
   (do not short-circuit the flow creation; the flow may still be collected).
8. **Empty sources.** Both sources empty ⇒ empty list. No exception.
9. **Errors.** Exceptions from either repository flow propagate to the collector; they are not
   swallowed.

### 4.2 `com.revenge.domain.usecase.ResolveLocalMixUseCase`

```kotlin
class ResolveLocalMixUseCase(songRepository: SongRepository) {
  suspend fun invoke(seedSongId: String, limit: Int = 25): List<Song>
}
```

Expands a seed song into an endless-mix style queue of up to `limit` songs drawn from liked and
recently played tracks, using each source's current value.

Normative behaviour:

1. **Limit guard.** `requested = max(limit, 0)`. If `requested == 0`, return `emptyList()`
   immediately without touching any repository.
2. **Seed lookup.** Read the first emission of `getSong(seedSongId)`. If it is `null`, return
   `emptyList()`. If the underlying flow completes without emitting, treat as `null` via `first`'s
   normal no-element behaviour.
3. **Seed artist keys.**
   - `seedArtistIds` = ids of `seed.artists` with blank ids dropped, as a set.
   - `seedArtistNames` = `artist.name.trim().lowercase()` for each seed artist, empty results
     dropped, as a set. (Whitespace-trimmed, case-insensitive comparison.)
4. **Source reads (sequential, in this order).** Read the first emission of `getLikedSongs()`, then
   the first emission of `getRecentlyPlayed(window)` where `window = requested * 3` saturated at
   `Int.MAX_VALUE` when `requested > Int.MAX_VALUE / 3`. The larger window compensates for the seed
   exclusion and for liked/recent overlap.
5. **Candidate set.**
   - Start with liked songs in their emitted order, excluding any song whose `id == seedSongId`.
   - Then append recent songs in their emitted order, excluding the seed and any id already present.
   - De-duplicate by `Song.id`, keeping the first occurrence (liked entries win for the same id).
6. **Score.** For each candidate:
   - `artistBonus` = `100.0` if the candidate shares an artist with the seed, else `0.0`.
     "Shares an artist" means at least one candidate artist `a` satisfies either:
     `a.id.isNotBlank() && a.id in seedArtistIds`, or
     `a.name.trim().lowercase() in seedArtistNames` (normalised names allow matches when ids differ).
   - `likedBonus` = `30.0` if `song.liked` is `true`, else `0.0`. (Unlike §4.1, this uses the
     entity field, not membership in the liked source.)
   - `minutes` = `min(totalPlayTimeMs / 60_000.0, 60.0)`.
   - `score = artistBonus + likedBonus + minutes` (one point per listening minute, capped at 60).
7. **Ordering.** Stable descending sort by score; ties preserve candidate order (liked order before
   recent-only songs).
8. **Truncation.** Return the first `requested` items. The seed itself is never included, even if it
   appears in liked/recent.
9. **Errors.** Exceptions from any repository propagate; they are not swallowed. No caching between
   calls.

Consequences to preserve:

- A shared-artist song (100) always outranks a merely liked song (30).
- A liked song (30) outranks an unliked, unplayed song (0).
- Heavily played songs saturate at 60 points, so they beat liked-but-unplayed songs but lose to
  shared-artist songs.

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
