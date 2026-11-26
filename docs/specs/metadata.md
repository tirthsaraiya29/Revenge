# `:metadata` Clean-Room Specification

Status: M2 specification (implementation-independent)
Module: `:metadata`
Target namespace: `com.revenge.metadata`
Source root: `metadata/src`

---

## 1. Purpose and Scope

`:metadata` is a pure-JVM, dependency-free utility module that normalises raw YouTube Music /
InnerTube display metadata into structured, search-friendly values.

It owns exactly two behaviours:

1. **Text de-escaping** for short API display strings (`ApiText`).
2. **Title/artist cleaning** that removes upload junk, extracts featuring credits and version
   markers, normalises channel names, and splits multi-artist strings (`MetadataCleaner`), plus the
   immutable result model (`CleanedMetadata`, `VersionInfo`, `VersionType`).

The module performs no I/O, no networking, no Android/Compose/Hilt use, and no persistence. All
entry points are synchronous, pure, deterministic functions over their arguments.

---

## 2. Build and Dependency Contract

`metadata/build.gradle.kts` must declare:

- Kotlin JVM plugin (`org.jetbrains.kotlin.jvm`).
- `jvmToolchain(21)`.
- `testImplementation(libs.junit)` only.

Hard constraints:

- **No `:domain` dependency is declared, and none may be added.** The module defines every type it
  needs locally; it has no cross-module type dependencies in either direction at compile time.
- No Android SDK, Compose, coroutines runtime, serialization, or third-party runtime libraries.
- Production sources must compile as ordinary JVM Kotlin 21.

Downstream modules (`:core`, `:innertube`, `:lrclib`, `:paxsenixlyrics`, `:unison`, and through
them `:lyrics`/`:app`) consume this module; the public API below must remain source-compatible.

---

## 3. Public API Summary

| FQN | Kind | Members consumed externally |
|---|---|---|
| `com.revenge.metadata.cleaner.ApiText` | `object` | `clean(raw: String): String` |
| `com.revenge.metadata.cleaner.MetadataCleaner` | `object` | `clean(rawTitle: String, rawArtist: String = ""): CleanedMetadata` |
| `com.revenge.metadata.cleaner.CleanerRules` | `object` | `JUNK_PATTERNS`, `FEATURING_PATTERNS`, `VERSION_PATTERNS`, `ARTIST_SEPARATORS`, `CHANNEL_JUNK_SUFFIXES` |
| `com.revenge.metadata.models.CleanedMetadata` | `data class` | all constructor fields plus `toDisplayTitle()`, `toSearchQuery()` |
| `com.revenge.metadata.models.VersionInfo` | `data class` | `type`, `description` |
| `com.revenge.metadata.models.VersionType` | `enum class` | all 13 constants in the declared order |

All four `object`s are stateless singletons. Every function is referentially transparent; concurrent
calls from multiple threads are safe and return identical results for identical arguments.

---

## 4. Data Model (must be preserved exactly)

### 4.1 `enum class VersionType`

Declared in this exact order (ordinals are part of the source contract; do not reorder):

1. `ORIGINAL`
2. `REMIX`
3. `LIVE`
4. `ACOUSTIC`
5. `REMASTER`
6. `INSTRUMENTAL`
7. `COVER`
8. `EDIT`
9. `EXTENDED`
10. `RADIO_EDIT`
11. `SLOWED`
12. `SPED_UP`
13. `OTHER_VERSION`

`ORIGINAL` means "no variant marker"; the cleaner never produces a non-null `VersionInfo` with this
type. `EDIT` and `OTHER_VERSION` are API surface for callers/consumers and are not emitted by the
built-in rule table (`EDIT` is distinct from `RADIO_EDIT`; the built-in "edit" keyword maps to
`RADIO_EDIT`).

### 4.2 `data class VersionInfo`

```
data class VersionInfo(
  val type: VersionType,
  val description: String,
)
```

- `type` — classification of the variant.
- `description` — the trimmed text of the bracket/tag that produced the match, e.g. for
  `"Faded (Live at Wembley)"` → `type = LIVE`, `description = "Live at Wembley"`. It excludes the
  surrounding brackets and is never blank when produced by the cleaner.
- Value semantics (equals/hashCode/copy/componentN) are the Kotlin data-class defaults.

### 4.3 `data class CleanedMetadata`

```
data class CleanedMetadata(
  val rawTitle: String,
  val rawArtist: String,
  val cleanTitle: String,
  val primaryArtist: String,
  val cleanArtist: String = primaryArtist,
  val featuredArtists: List<String> = emptyList(),
  val version: VersionInfo? = null,
  val junkTags: List<String> = emptyList(),
)
```

Field contract:

- `rawTitle` — the caller-supplied title argument, byte-for-byte (never sanitised or trimmed).
- `rawArtist` — the caller-supplied artist argument, byte-for-byte.
- `cleanTitle` — normalised song title used for matching and lyric search. Never `null`; may be
  empty when the input has no usable title.
- `primaryArtist` — first artist after splitting. May be empty.
- `cleanArtist` — full display artist string after channel scrubbing and sanitising, including
  additional artists (e.g. `"Calvin Harris & Dua Lipa"`). Defaults to `primaryArtist`. Set to the
  full scrubbed artist when non-blank, otherwise to `primaryArtist`.
- `featuredArtists` — ordered, de-duplicated list of featured artist names, excluding blanks and
  excluding any name equal (case-insensitive) to `primaryArtist`. Never `null`.
- `version` — `null` when the title is an original; otherwise a `VersionInfo`.
- `junkTags` — ordered list of extracted non-song fragments (bracket contents, trailing status,
  hashtags, etc.). Never `null`; elements may retain their original case and may include a leading
  `#` for hashtags.

Derived members:

```
fun toDisplayTitle(): String
```
Returns `"$cleanTitle (${version.description})"` when `version != null` **and**
`version.type != VersionType.ORIGINAL`; otherwise returns `cleanTitle` unchanged. The parentheses
are always added; `description` is stored without brackets. Examples:
`("Faded", VersionInfo(LIVE, "Live at Wembley"))` → `"Faded (Live at Wembley)"`;
`("Faded", VersionInfo(ORIGINAL, "Original"))` → `"Faded"`; `("Faded", null)` → `"Faded"`.

```
fun toSearchQuery(): Pair<String, String>
```
Returns `cleanTitle to cleanArtist` (the full artist string, not `primaryArtist`). Used for lyric
provider queries.

Equality/copy/`toString` follow Kotlin data-class defaults so the class behaves as a value object.

---

## 5. `CleanerRules` Contract

`CleanerRules` publishes the rule tables used by `MetadataCleaner` and available to callers who
parse titles themselves. The exposed values and their ordering are API. Matching is
case-insensitive wherever keyword matching is described.

### 5.1 `val JUNK_PATTERNS: List<Regex>`

Patterns identifying text that describes the *upload*, not the song. The list must cover, in this
semantic order:

1. Parenthesised `( ... )` content containing an upload/quality tag word.
2. Square-bracket `[ ... ]` content containing an upload/quality tag word.
3. CJK corner-bracket `【 ... 】` content containing an official/video/audio/lyrics marker.
4. Japanese corner-bracket `「 ... 」` content containing an official/video/audio/lyrics marker.
5. A trailing `| ...` segment.
6. A trailing `// ...` segment.
7. A hashtag token (`#` followed by word characters).
8. A trailing `- <marker> ...` segment where the marker is an official/video/audio/lyric/
   visualizer word.

Upload/quality tag vocabulary (case-insensitive): `official music video`, `official video`,
`official audio`, `music video`, `lyric video`, `lyrics video`, `official visualizer`,
`visualizer`, `video`, `audio`, `mv`, `pv`, `hd`, `hq`, `4k`, `1080p`, `720p`, `60fps`, `uhd`.

Each pattern is a `Regex`. Callers only require that these patterns match the described fragments;
the concrete pattern text is not part of the ABI.

### 5.2 `val FEATURING_PATTERNS: List<Regex>`

Patterns that locate a featuring credit and expose the artist list in **capture group 1**. Covered
forms, in order:

1. `(feat. ...)`, `(feat ...)`, `(ft. ...)`, `(ft ...)` inside parentheses.
2. `[feat. ...]` / `[ft. ...]` inside square brackets.
3. Inline `feat.`, `ft.`, or `featuring` followed by names, terminated before punctuation such as
   brackets, hyphens, or pipes.

Keywords are case-insensitive and the trailing period is optional.

### 5.3 `val VERSION_PATTERNS: List<Pair<Regex, VersionType>>`

Version-marker vocabulary with **precedence order = list order; first matching entry wins**. The
list must contain exactly these entries in this order:

| # | VersionType | Recognised keywords (case-insensitive, whole-word) |
|---|---|---|
| 1 | `REMIX` | `remix`, `flip`, `bootleg`, `vip`, `re-edit`, `club mix`, `dub` |
| 2 | `LIVE` | `live`, `session`, `unplugged`, `in concert`, `live at <alphanumeric words>` |
| 3 | `ACOUSTIC` | `acoustic`, `orchestral`, `symphonic` |
| 4 | `REMASTER` | `remaster`, `remastered`, `deluxe`, `anniversary` |
| 5 | `INSTRUMENTAL` | `instrumental`, `karaoke` |
| 6 | `COVER` | `cover` |
| 7 | `RADIO_EDIT` | `radio edit`, `edit` |
| 8 | `EXTENDED` | `extended`, `extended mix` |
| 9 | `SLOWED` | `slowed`, `slowed + reverb`, `reverb` |
| 10 | `SPED_UP` | `sped up`, `nightcore` |

Matching is whole-word/case-insensitive. Because of precedence, a title containing both "remix" and
"live" classifies as `REMIX`. `VERSION_PATTERNS` is never used to produce `ORIGINAL`, `OTHER_VERSION`,
or the standalone `EDIT` constant.

### 5.4 `val ARTIST_SEPARATORS: List<String>`

Ordered multi-artist delimiters. Splitting is performed with each separator in this order (later
splits apply to every token produced by earlier splits):

`" & "`, `", "`, `" feat. "`, `" feat "`, `" ft. "`, `" ft "`, `" featuring "`, `" and "`, `" x "`,
`" X "`.

Case sensitivity for `" and "`, `" x "`, and `" X "` is literal as written; the `feat`/`ft`
separators are lowercase literals. Splitting is exact-substring (no regex).

### 5.5 `val CHANNEL_JUNK_SUFFIXES: List<Regex>`

Case-insensitive uploader suffixes: trailing `- Topic`, trailing `VEVO`, trailing ` Records`, and
trailing ` Official` (each recognised at the end of the channel/artist string).

---

## 6. `MetadataCleaner.clean` Contract

```
fun clean(rawTitle: String, rawArtist: String = ""): CleanedMetadata
```

Pure function. `rawArtist` may be omitted, in which case it is `""`. Both arguments are required to
be non-null Kotlin `String`; `null` is a caller error (Kotlin type system). The function never throws
for any pair of strings, including empty, blank, or malformed input.

### 6.1 Observable pipeline

The result is computed in the following stages. Stage order is observable and part of the contract.
"Sanitise" means the Unicode/entity normalisation described in §6.2; "scrub" means §6.6; "scan"
means §6.4; "split" means §6.7; "normalise title" means §6.8.

1. Sanitise `rawTitle` and `rawArtist` independently.
2. **Artist-prefix peel** (§6.3) on the sanitised title, returning a possibly replaced title and
   artist.
3. **Tag scan** (§6.4) on the peeled title → title fragment, junk tags, featured names, version.
4. **Channel scrub** (§6.6) on the peeled artist.
5. **Artist split** (§6.7) on the scrubbed artist.
6. Assemble `CleanedMetadata`:
   - `rawTitle` / `rawArtist` = the original, untouched arguments.
   - `cleanTitle` = normalise-title(scan title).
   - `primaryArtist` = first split token (or `""`).
   - `cleanArtist` = scrubbed artist if non-blank, else `primaryArtist`.
   - `featuredArtists` = `(scan.featured + split.others).distinct()`, then remove blank entries and
     entries equal to `primaryArtist` ignoring case. Order is first-seen order.
   - `version` = scan version (nullable).
   - `junkTags` = scan junk in extraction order.

The cleaner is idempotent on already-clean input: cleaning a plain title and plain artist (no
entities, brackets, dash-prefix, hashtags, pipe suffix, or channel suffix) returns the same title,
the same single artist, empty `featuredArtists`, empty `junkTags`, and `version == null`.

### 6.2 Unicode and entity sanitisation

Applied to both inputs before anything else:

- Decode the named entities `&amp;` → `&`, `&quot;` → `"`, `&apos;` → `'`, `&lt;` → `<`,
  `&gt;` → `>`; decode the literal decimal reference `&#39;` → `'`.
- Map typographic single quotes (`‘`, `’`, `´`, and backtick `` ` ``) to ASCII `'`.
- Map typographic double quotes (`“`, `”`) to ASCII `"`.
- Remove all characters in Unicode categories **So** (Symbol, other — this includes emoji) and
  **Cn** (unassigned).
- Trim leading/trailing whitespace.

Sanitisation does **not** strip orphan entity fragments such as `amp;`; that behaviour belongs only
to `ApiText` (§7).

### 6.3 Artist-prefix peel (`"Artist - Title"`)

Split the sanitised title on the delimiter `" - "` (space, hyphen-minus, space). If fewer than two
parts are produced, leave title and artist unchanged.

Otherwise:

- `candidateArtist` = first part, trimmed.
- `candidateTitle` = remaining parts re-joined with `" - "`, trimmed.
- Accept the split (returning `candidateTitle` as the new title and `candidateArtist` as the new
  artist) only when the right-hand side is a plausible real title, defined as:
  - `candidateTitle` is not blank, **and**
  - `candidateTitle` does not "look like junk" (§6.3.1), **and**
  - either the incoming artist is blank, or the candidate artist agrees with the known artist:
    one string contains the other case-insensitively, or the known artist contains `Topic` or
    `VEVO` (case-insensitive).

If accepted, the returned artist is the **candidate artist from the title**, replacing the incoming
artist (the incoming artist is discarded even if it also carried information). If not accepted,
both inputs pass through unchanged (the dash stays part of the title).

#### 6.3.1 `looksLikeJunk(candidateTitle)`

True if any of:

- starts with any opening bracket character: `(`, `[`, `【`, `「`;
- running the tag scan (§6.4) over the candidate produces a blank title;
- the candidate matches one of `VERSION_PATTERNS` entirely;
- the candidate matches one of `JUNK_PATTERNS` entirely;
- the candidate consists solely of upload/quality tag words (the §5.1 vocabulary).

Consequences: `"Alone - (Official Video)"` is not split (title keeps the dash, then the tag scan
removes the bracket and trailing dash); `"Part - Two"` with artist `"Someone"` is not split;
`"Major Lazer & DJ Snake - Lean On feat. MØ"` with blank artist **is** split.

### 6.4 Tag scan

Operates on one title string and returns `(title, junkTags, featuredArtists, version)`.

**a. Trailing pipe suffix.** Locate the first `|` or `//` and the text after it. If that trailing
text contains a junk pattern match or any of the hint words `official`, `video`, `visualizer`,
`audio`, `mv`, `pv`, `lyric`, `lyrics`, `remaster`, `hd`, `4k` (case-insensitive, whole-word), then:

- append the trimmed trailing text to `junkTags`;
- remove the separator and trailing text from the title.

Otherwise the pipe/`//` text is left in place.

**b. Hashtags.** Every token of the form `#` + word characters, anywhere in the title, is appended
(trimmed, `#` retained) to `junkTags` and removed from the title.

**c. Bracketed tags.** For every balanced bracket group of form `( ... )`, `[ ... ]`, `【 ... 】`,
or `「 ... 」`, process the trimmed inner content in this precedence:

1. **Featuring**: if the content begins with `feat.`, `feat`, `ft.`, `ft`, or `featuring`
   (case-insensitive) followed by names, split those names (§6.7); append the first non-blank name
   and all remaining names to `featuredArtists`; remove the whole bracket (replaced by a space).
2. **Version**: else, if a `VERSION_PATTERNS` entry matches the content (first entry by precedence),
   record `VersionInfo(type, content)` **only if no version has been recorded yet**, and remove the
   whole bracket.
3. **Junk**: else, if any `JUNK_PATTERNS` entry matches the whole bracket or its inner content,
   append the inner content (or the whole bracket when content is blank) to `junkTags`, and remove
   the whole bracket.
4. Otherwise leave the bracket untouched.

Nested/unbalanced brackets follow ordinary regex scanning behaviour: the first balanced group is
processed and removed; unmatched brackets remain. A bracket removed by any rule is replaced with
whitespace, so adjacent words do not merge.

**d. Inline featuring.** After bracket processing, the first remaining inline `feat.` / `ft.` /
`featuring` clause (at start of string or preceded by whitespace, artist text terminated before
brackets, hyphens, or pipes) is split (§6.7), its first non-blank name plus remaining names are
appended to `featuredArtists`, and the matched clause is removed from the title.

The returned title is trimmed. `junkTags`/`featuredArtists` preserve extraction order and may
contain duplicates at this stage; final de-duplication happens in §6.1.

### 6.5 Examples of scan precedence (normative)

- `"(Official Music Video)"` → junk, no version.
- `"(4K)"` / `"[HD]"` → junk.
- `"(The Glitch Mob Remix)"` → version `REMIX`, description `"The Glitch Mob Remix"`.
- `"(Live on MTV 1994)"` → version `LIVE`, description `"Live on MTV 1994"`.
- `"(Slowed + Reverb)"` → version `SLOWED`, description `"Slowed + Reverb"`.
- `"(feat. MØ)"` → featured `MØ`, no version, not junk.
- `"(Acoustic Version)"` → version `ACOUSTIC`, description `"Acoustic Version"`.
- Only the **first** version-bearing bracket sets `version`; later version brackets are removed but
  do not overwrite it.

### 6.6 Channel scrub

Applied to the peeled artist string:

1. Trim.
2. Strip a trailing `- Topic` suffix (case-insensitive) and trim.
3. If the result ends with `VEVO` (case-insensitive): drop the four trailing characters, trim, then
   split camel case: insert a space at each lowercase-to-uppercase boundary, except leave the text
   untouched when it already contains a space, is entirely uppercase, or is entirely lowercase.
   Example: `"TaylorSwiftVEVO"` → `"Taylor Swift"`; `"AlanWalkerVEVO"` → `"Alan Walker"`.
4. Strip trailing ` Records` and trailing ` Official` (case-insensitive), trim.

No other channel rewriting occurs.

### 6.7 Artist splitting

```
internal behaviour, exposed through clean()
```

Given a string:

- Blank input → primary `""`, others empty.
- Split sequentially on every entry of `ARTIST_SEPARATORS` (§5.4) in list order; each split applies
  to all tokens accumulated so far.
- Trim every resulting token and drop empty tokens.
- The first token is the primary artist; the rest are "other" artists in order.

Examples:

- `"Calvin Harris & Dua Lipa"` → primary `"Calvin Harris"`, others `["Dua Lipa"]`.
- `"Earth, Wind & Fire"` → primary `"Earth"`, others `["Wind", "Fire"]`; note `cleanArtist`
  remains the full scrubbed string.
- `"Simon & Garfunkel"` → primary `"Simon"`, others `["Garfunkel"]`.
- `"Florence and the Machine"` → primary `"Florence"`, others `["the Machine"]`.

Because `cleanArtist` is the full string, splitting never degrades the display/search artist.

### 6.8 Title normalisation

For the scanned title: collapse every run of whitespace to a single space, trim, then remove any
trailing characters from the set hyphen-minus `-`, en dash `–`, em dash `—`, `|`, `/`, and trim
again. Internal punctuation and internal spaces are preserved. Examples: `"Alone - "` → `"Alone"`;
`"  Song   Name  "` → `"Song Name"`; `"Part - Two"` stays `"Part - Two"` when no peel occurred.

### 6.9 Errors and edge cases

- Empty title + empty artist → `cleanTitle = ""`, `primaryArtist = ""`, `cleanArtist = ""`,
  `featuredArtists` empty, `junkTags` empty, `version = null`. No exception.
- Whitespace-only inputs behave as empty after cleaning.
- A title made entirely of junk may legitimately produce an empty `cleanTitle`; callers treat that
  as "no usable title". The cleaner must not throw.
- `rawTitle`/`rawArtist` always echo the exact original arguments regardless of cleaning.
- No locale-dependent behaviour: case folding uses `ignoreCase` semantics independent of the JVM
  default locale (use Kotlin/Java `ignoreCase`, not `lowercase()`/`uppercase()` with a locale).
- Results are deterministic across runs and threads.

---

## 7. `ApiText.clean` Contract

```
fun clean(raw: String): String
```

Normalises a short API display string. Pure, non-throwing, non-null in and out; `""` → `""`.

Processing order (observable):

1. **Numeric character references.** Replace every `&#<decimal>;` and every `&#x<hex>;` (lowercase
   `x`; hex digits either case) with the character for that code point when the value is in
   `0..0x10FFFF`. The replacement is the UTF-16 representation of the code point (so astral values
   yield a surrogate pair). If the value is out of range or cannot be parsed, leave the reference
   literally unchanged. `&#65;` → `A`, `&#x41;` → `A`, `&#233;` → `é`, `&#xE9;` → `é`.
2. **Named entities.** Decode, in this order: `&quot;` → `"`, `&apos;` → `'`, `&lt;` → `<`,
   `&gt;` → `>`, `&nbsp;` → space, and finally `&amp;` → `&`. Decoding `&amp;` last means a
   doubly-escaped sequence such as `&amp;quot;` becomes `&quot;`, not `"`.
3. **Orphan-fragment removal.** Remove occurrences of the literal fragments `amp;`, `quot;`,
   `apos;`, `lt;`, `gt;`, `nbsp;` that appear either at the start of the string or immediately after
   whitespace, together with any whitespace that directly follows the fragment. The whitespace
   *before* the fragment is preserved. Only these six lowercase named fragments are treated this
   way; numeric fragments such as `#123;` and ordinary text are retained.

Examples (normative):

| Input | Output |
|---|---|
| `"Sabin Rai &amp; The Pharaoh"` | `"Sabin Rai & The Pharaoh"` |
| `"amp; The Pharaoh"` | `"The Pharaoh"` |
| `"Dhanyavaad (feat. amp; The Pharaoh)"` | `"Dhanyavaad (feat. The Pharaoh)"` |
| `"&quot;Roll&quot; &apos;King&apos;"` | `"\"Roll\" 'King'"` |
| `"a &lt; b &gt; c"` | `"a < b > c"` |
| `"Alan Walker"` | `"Alan Walker"` |
| `"R&B"` | `"R&B"` |
| `"Issue #123; please check"` | `"Issue #123; please check"` |
| `"Episode #5; the finale"` | `"Episode #5; the finale"` |
| `""` | `""` |

`ApiText` does not trim, collapse whitespace, strip emoji, or reinterpret smart quotes; those are
`MetadataCleaner` concerns. It must remain safe to call on every string returned by the API.

---

## 8. Cross-Module Contracts (exact shapes downstream compiles against)

`:metadata` must not introduce new required dependencies. Callers use the following forms; the
implementer should verify these compile without touching caller code:

| Caller | Required shape |
|---|---|
| `core/.../db/entities/MetadataExtensions.kt` | `MetadataCleaner.clean(title, "")`, `MetadataCleaner.clean(title, artist.orEmpty())`, `CleanedMetadata.toDisplayTitle()`, `.primaryArtist` |
| `innertube/.../models/MetadataExtensions.kt` | same as above |
| `innertube/.../InnerTube.kt`, `YouTube.kt`, `pages/*.kt` | `ApiText.clean(String): String`, including method references `ApiText::clean` used inside `let { }` on non-null strings |
| `lrclib/.../LrcLib.kt` | `MetadataCleaner.clean(title, artist)`, `CleanedMetadata.toSearchQuery()` destructured as `Pair<String, String>`, `.primaryArtist` |
| `paxsenixlyrics/.../Paxsenix.kt` | `MetadataCleaner.clean(title, artist).toSearchQuery()` |
| `unison/.../Unison.kt` | `MetadataCleaner.clean(title, artist).toSearchQuery()` |

Consequences for the implementer:

- `toSearchQuery()` **must** return `kotlin.Pair<String, String>` so Kotlin destructuring works.
- `clean` must expose the two-argument overload and the one-argument default.
- `VersionInfo`, `VersionType`, and `CleanerRules` are public but currently have no external call
  sites; keep them public with the shapes above so future consumers can rely on them.
- Do not move these types out of `com.revenge.metadata.cleaner` / `com.revenge.metadata.models`.

---

## 9. Threading, Ordering, and Side-Effect Guarantees

- All entry points are synchronous and stateless; no shared mutable state, caches, clocks, random
  sources, or locale-dependent formatting.
- Concurrent invocation is safe; results depend only on arguments.
- Collection ordering in `CleanedMetadata` (`featuredArtists`, `junkTags`) is deterministic and
  follows extraction/first-seen order as specified.
- No I/O, logging, network, Android framework, or process-global mutation.

---

## 10. Target Files

The implementer must write exactly these files (module namespace `com.revenge.metadata`):

Main:
- `metadata/src/main/kotlin/com/revenge/metadata/cleaner/ApiText.kt`
- `metadata/src/main/kotlin/com/revenge/metadata/cleaner/CleanerRules.kt`
- `metadata/src/main/kotlin/com/revenge/metadata/cleaner/MetadataCleaner.kt`
- `metadata/src/main/kotlin/com/revenge/metadata/models/CleanedMetadata.kt`
- `metadata/src/main/kotlin/com/revenge/metadata/models/VersionInfo.kt`
- `metadata/src/main/kotlin/com/revenge/metadata/models/VersionType.kt`

Tests:
- `metadata/src/test/kotlin/com/revenge/metadata/cleaner/ApiTextTest.kt`
- `metadata/src/test/kotlin/com/revenge/metadata/cleaner/MetadataCleanerTest.kt`
- `metadata/src/test/kotlin/com/revenge/metadata/models/CleanedMetadataTest.kt`

No other production source file is required. Internal helper types may be private within these
files; anything public must match §3–§5.

---

## 11. Acceptance Tests

All cases below are deterministic, observable through the public API, and should be expressible by
an opaque-box test engineer using JUnit. Each is a separate test case.

### 11.1 `ApiText`

1. `clean("Sabin Rai &amp; The Pharaoh") == "Sabin Rai & The Pharaoh"`.
2. `clean("amp; The Pharaoh") == "The Pharaoh"` (leading orphan removed).
3. `clean("Dhanyavaad (feat. amp; The Pharaoh)") == "Dhanyavaad (feat. The Pharaoh)"`.
4. `clean("&#65; &#x41;") == "A A"`; `clean("&#233;") == "é"`; `clean("&#xE9;") == "é"`.
5. `clean("&quot;Roll&quot; &apos;King&apos;") == "\"Roll\" 'King'"`.
6. `clean("a &lt; b &gt; c") == "a < b > c"`.
7. `clean("Alan Walker") == "Alan Walker"`; `clean("R&B") == "R&B"` (idempotent on plain text).
8. `clean("Issue #123; please check")` and `clean("Episode #5; the finale")` are unchanged
   (numeric fragments preserved).
9. `clean("") == ""`.
10. Out-of-range reference, e.g. `clean("&#1114112;")` (0x110000), is returned literally.
11. `clean("x&quot;y")` removes only the orphan `quot;` after whitespace/at start, not the decoded
    entity: `clean("&quot;x&quot;") == "\"x\""` (no over-stripping of decoded output).

### 11.2 `CleanerRules` surface

12. `VERSION_PATTERNS` starts with `REMIX` and its `VersionType` sequence is exactly the 10-entry
    precedence table in §5.3; `ARTIST_SEPARATORS` equals the 10-element ordered list in §5.4.
13. For each vocab entry in §5.3, matching a bracket containing exactly that keyword yields the
    corresponding `VersionType`; `remix` beats `live` when both appear.
14. Each `JUNK_PATTERNS` entry matches its described fragment; `FEATURING_PATTERNS` capture group 1
    returns the featured artist text for `(feat. X)`, `[ft. Y]`, and inline `featuring Z`.

### 11.3 `MetadataCleaner.clean` — junk and brackets

15. `clean("Alan Walker - Faded (Official Music Video) [4K]", "Alan Walker")` →
    `cleanTitle == "Faded"`, `primaryArtist == "Alan Walker"`, `cleanArtist == "Alan Walker"`,
    `version == null`, and `junkTags` contains an entry containing `"Official Music Video"` and one
    containing `"4K"`.
16. `clean("Song Name | Official Visualizer 2024", "Artist")` → `cleanTitle == "Song Name"`,
    `junkTags` contains `"Visualizer"` (case-insensitive).
17. `clean("Best Song Ever #shorts #trending", "Artist")` → `cleanTitle == "Best Song Ever"`,
    `junkTags` contains `"#shorts"` and `"#trending"`.
18. `clean("RADWIMPS - 前前前世 【Official MV】", "RADWIMPS")` → `cleanTitle == "前前前世"`,
    `primaryArtist == "RADWIMPS"`.
19. `clean("Alone - (Official Video)", "Marshmello")` → `cleanTitle == "Alone"`,
    `primaryArtist == "Marshmello"` (junk-only right side is not peeled as a title).
20. `clean("Alan Walker - Official Music Video", "Alan Walker - Topic")` →
    `primaryArtist == "Alan Walker"` and `cleanTitle != "Official Music Video"` (junk right side
    never becomes the title; Topic suffix removed).

### 11.4 Versions

21. `clean("Illenium - Crawl Outta Love (The Glitch Mob Remix) [Official Audio]", "Illenium")` →
    `cleanTitle == "Crawl Outta Love"`, `version!!.type == REMIX`,
    `version.description == "The Glitch Mob Remix"`,
    `toDisplayTitle() == "Crawl Outta Love (The Glitch Mob Remix)"`,
    `toSearchQuery().first == "Crawl Outta Love"`.
22. `clean("Hotel California (Live on MTV 1994) [HD]", "Eagles")` → `cleanTitle == "Hotel California"`,
    `version.type == LIVE`, `version.description == "Live on MTV 1994"`.
23. `clean("Take On Me (Acoustic Version)", "a-ha")` → `version.type == ACOUSTIC`.
24. `clean("After Dark (Slowed + Reverb)", "Mr. Kitty")` → `version.type == SLOWED`,
    `version.description == "Slowed + Reverb"`.
25. `clean("Tune (Extended Mix)", "Artist")` → `EXTENDED`;
    `clean("Tune (Instrumental)", "Artist")` → `INSTRUMENTAL`.
26. A title with two version brackets records the first version and removes both brackets.

### 11.5 Featuring and artist splitting

27. `clean("Major Lazer - Lean On (feat. MØ) (Official Lyric Video)", "Major Lazer")` →
    `cleanTitle == "Lean On"`, `featuredArtists` contains `"MØ"`.
28. `clean("Major Lazer & DJ Snake - Lean On feat. MØ", "")` → `cleanTitle == "Lean On"`,
    `primaryArtist == "Major Lazer"`, `featuredArtists` contains both `"DJ Snake"` and `"MØ"`.
29. `clean("Title", "Calvin Harris & Dua Lipa")` → `primaryArtist == "Calvin Harris"`,
    `cleanArtist == "Calvin Harris & Dua Lipa"`, `featuredArtists` contains `"Dua Lipa"`,
    `toSearchQuery().second == "Calvin Harris & Dua Lipa"`.
30. `toSearchQuery().second` stays `"Simon & Garfunkel"`, `"Earth, Wind & Fire"`, and
    `"Florence and the Machine"` for those artists (full display artist preserved while primary is
    the first token).
31. A featured name identical (ignoring case) to the primary artist is removed from
    `featuredArtists`; duplicates across title and artist field appear once.

### 11.6 Channel scrubbing

32. `clean("Yellow", "Coldplay - Topic")` → `primaryArtist == "Coldplay"`.
33. `clean("Blank Space", "TaylorSwiftVEVO")` → `primaryArtist == "Taylor Swift"`.
34. `clean("X", "SomeBandVEVO")` → `primaryArtist == "Some Band"`; `clean("X", "ABCVEVO")` leaves
    `"ABC"` untouched (all-uppercase camel split suppressed); `clean("X", "Some Band Records")` →
    `"Some Band"`; `clean("X", "Some Band Official")` → `"Some Band"`.

### 11.7 Sanitisation and dash handling

35. `clean("Rock &amp; Roll &#39;King&#39; “Edition”", "Artist")` →
    `cleanTitle == "Rock & Roll 'King' \"Edition\""`.
36. Emoji/symbol characters are removed from title and artist; surrounding text is preserved.
37. `clean("Part - Two", "Someone")` → `cleanTitle == "Part - Two"`,
    `primaryArtist == "Someone"` (dash not peeled when the candidate artist does not match).
38. `clean("Simple Song", "Simple Artist")` → `cleanTitle == "Simple Song"`,
    `primaryArtist == "Simple Artist"`, empty `featuredArtists`, empty `junkTags`, `version == null`
    (idempotence on clean input).
39. `clean("", "")` → `cleanTitle == ""`, `primaryArtist == ""`, `cleanArtist == ""`, empty
    `featuredArtists`; no exception.
40. `rawTitle` and `rawArtist` equal the exact original arguments even when cleaning changes the
    display values (e.g. entities or emoji present).

### 11.8 `CleanedMetadata` API

41. `CleanedMetadata(..., version = VersionInfo(LIVE, "Live at Wembley")).toDisplayTitle() ==
    "Faded (Live at Wembley)"`.
42. `VersionInfo(ORIGINAL, "Original")` produces `toDisplayTitle() == cleanTitle`.
43. `version == null` produces `toDisplayTitle() == cleanTitle`.
44. `toSearchQuery()` returns `cleanTitle to cleanArtist` and `cleanArtist` defaults to
    `primaryArtist` when the constructor argument is omitted.
45. Data-class value semantics: two instances with equal fields are `equals`, share `hashCode`, and
    `copy` preserves unspecified fields.

### 11.9 Non-functional

46. Calling every public entry point concurrently from multiple threads produces the same results as
    sequential calls (statelessness).
47. `clean` and `ApiText.clean` return `String` (non-null) for every non-null input and never throw,
    including blank strings, lone brackets, lone `&`, and malformed entity references.
