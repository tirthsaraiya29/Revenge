# Tier 5 Adversarial Hardening Plan — Revenge

**Baseline:** `11ae62071f96db3b70557867ab1254dbcc5b125e`
**Scope:** Hostile input, hostile peers, partial failure, process death, resource exhaustion, and
component/IPC abuse across all 21 Gradle modules.
**Companion gates:** `docs/provenance/VALIDATION_AND_RELEASE_GATES.md` §5 (F15 Tiers 1–4, F16 Tier 5),
G-SEC-1 (Semgrep), G-SEC-2 (manifest/PendingIntent hygiene), G-BUILD-5 (FOSS purity).

This document is a plan only. It contains no implementation code. Every item is written so that a
test author can build the fixture without further design decisions.

---

## 1. Purpose

The Tiers 1–4 suite proves the product works when inputs and infrastructure behave. This plan proves
it fails **safely, boundedly, and observably** when they do not. A Tier 5 finding is a release
blocker when it causes any of: uncaught crash, ANR, silent data loss, unbounded memory/CPU/disk,
unauthorised data exposure, unauthorised egress, or a secret written where another app/backup can
read it.

Non-negotiable: adversarial tests assert **what the app does after** the hostile input (degrades,
reports, recovers), not merely that an exception was thrown.

---

## 2. Tier model

Each test row names its **primary execution tier**. A test may also run at a lower tier; the primary
is where it must run before release.

| Tier | Name | Environment | Budget |
|---|---|---|---|
| **T1** | Unit / property | Pure JVM or Robolectric, no device | ≤ 60 s per suite |
| **T2** | Component / instrumented | `androidTest` on emulator, real Android services | ≤ 15 min per suite |
| **T3** | Device E2E | Scripted ADB/UIAutomator on a physical device (API 30/33/34/36) | ≤ 60 min |
| **T4** | Chaos / soak | Device lab: process kill, network shaping, storage fill, doze, reboot, 24 h soak | ≤ 24 h |
| **T5** | Adversarial | Fuzz, property generators, hostile peer app, malformed corpus | ≤ 60 min CI / 24 h nightly |

**Evidence per run:** test id, tier, build SHA, device/emulator image, seed, corpus hash, observed
metric (time, RSS, bytes, request count), pass/fail, and the artifact (logcat slice, `dumpsys`,
malloc trace, pcap). A Tier 5 result without a seed and corpus hash is not reproducible and does not
count.

**Exit rule:** every row marked `Required` in §6 must pass on the release build, on a **fresh
install and an upgrade install**, before M6 sign-off.

---

## 3. Baseline observations this plan targets

These were read from the current tree and drive specific rows below. They are not hypothetical.

| ID | Observation | File / evidence | Consequence if untested |
|---|---|---|---|
| OBS-01 | Network security config permits cleartext **globally** | `app/src/main/res/xml/network_security_config.xml` — `<base-config cleartextTrafficPermitted="true">` | Any remote/AI endpoint can downgrade to HTTP; MITM of metadata and API keys |
| OBS-02 | AI endpoint check is a denylist, not an allowlist | `lyrics/.../api/OpenRouterService.kt` `isUrlSafe()` blocks only `169.254.*`, `metadata.google.internal`, `instance-data`; allows `http://`, loopback, RFC1918, `.local`, redirects | SSRF to localhost/LAN, DNS rebinding, secret exfiltration, metadata theft on other clouds |
| OBS-03 | Exported content provider fetches remote URLs | `app/src/main/AndroidManifest.xml` `.utils.AutomotiveArtworkProvider` `exported=true`; `AutomotiveArtwork.kt` signs a base64 URL with HMAC | Forged URI or signing bug = SSRF proxy reachable by any installed app |
| OBS-04 | FileProvider roots are the entire external storage and cache | `app/src/main/res/xml/provider_paths.xml` — `external-path path="."`, `cache-path path="."` | Over-broad grants; traversal surface; accidental sharing of unrelated files |
| OBS-05 | Settings DataStore (holds AI/OAuth keys) is **not** excluded from cloud backup | `app/src/main/res/xml/backup_rules.xml` and `data_extraction_rules.xml` exclude only navidrome/jellyfin/automotive prefs | API keys enter Google cloud backup |
| OBS-06 | TTML parsing builds a DOM with no size/depth cap | `betterlyrics/.../TTMLParser.kt` (XXE features are disabled — good — but `DocumentBuilderFactory` has no entity/size limit) | Memory blow-up on large/deep documents before any node is inspected |
| OBS-07 | `withRetry` is a fixed 3-attempt exponential with no jitter and no status classification | `innertube/.../InnerTube.kt` `withRetry` | Thundering herd; retries on non-transient failures under load |
| OBS-08 | Queue snapshot is Java `Serializable` | `core/.../models/PersistQueue.kt` | Deserialisation of corrupt/hostile blobs; no size bound visible at the model |
| OBS-09 | `DataStore.get` swallows all read failures | `core/.../utils/DataStore.kt` — `runCatching { … }.getOrNull()` | Corrupt preferences silently reset settings and lose secrets with no user signal |
| OBS-10 | Room has 48 versions of auto-migrations and `exportSchema=true`, no declared destructive fallback | `core/.../db/MusicDatabase.kt` | A bad migration either preserves data or must be explicitly, visibly handled |

---

## 4. Suite A — Untrusted content and provider resilience

Covers malformed LRC/TTML/JSON, absent ONNX model, provider timeouts, cancellation, retry storms.

| ID | Threat | Test approach | Pass criteria | Tier |
|---|---|---|---|---|
| **A-01** | Malformed LRC crashes the lyrics screen or hangs the parser | Corpus: BOM, CRLF-only, invalid UTF-8, lone `\r`, 10 000 timestamp tags, `[00:-1.00]`, `[9999999999:99.99]`, `NaN`, `1e309`, 10 MB single line, empty. Fuzz `LyricsUtils` parse/serialise 1 M cases (60 s). | Never throws to UI; returns empty or partial lyrics. 1 MB parses in ≤ 250 ms; peak alloc ≤ 32 MB. Timestamps clamped to `[0, durationMax]`, non-finite rejected. No OOM/StackOverflow. | T1, T5 |
| **A-02** | TTML entity expansion, external entity, deep nesting, or oversized document exhausts memory | Cases: DOCTYPE, external entity `file:///etc/hosts` and `http://…`, billion-laughs, 20 MB body, nesting depth 10 000, 1 M `<p>` nodes, invalid namespaces, duplicate `xml:id`. Fuzz `TTMLParser.parseTTML`. | DOCTYPE rejected; zero file/network access (verified with a trap file and a mock server). Input capped at 5 MB and depth 64 **before** DOM construction. Failure returns `emptyList()` in ≤ 500 ms, peak alloc ≤ 64 MB. | T1, T5 |
| **A-03** | Malformed/hostile AI JSON damages state or blows the parser | Truncated JSON, markdown fences, duplicate keys, 10 000-deep nesting, 50 MB body, non-UTF-8, valid JSON wrong shape, `songs` = 1 M entries. Property generator over `AiTranslationParsing` and playlist/recommendation parsers. | Response body cap 2 MB; nesting cap 32; unknown keys ignored; invalid shape yields a user-visible error and no DB write. No exception escapes; no ANR; one attempt, no retry. | T1, T2, T5 |
| **A-04** | Beat tracker crashes or retries forever when the ONNX asset is absent or corrupt | M2 removed both models (`M2_GATE_DECISIONS.md` §1). Build without assets; call `BeatTracker.session()`; repeat 1 000×; also truncate/corrupt a test asset if injected. | Returns `null` deterministically; Smart Audio beat features report unavailable; zero exceptions; no repeated load attempts or log spam (> 3 lines). Feature disclosure present. | T2, T3 |
| **A-05** | A hung or slow-drip provider pins a worker, blocks playback/UI, or leaks a thread | MockWebServer: no response, 1 byte/10 s, TLS handshake stall, DNS blackhole. Exercise every provider in `LyricsProviderRegistry` and the AI client. | Every call bounded by explicit connect/read/call timeouts (interactive ≤ 10 s, batch ≤ 90 s). Cancellation frees the thread within 1 s. UI offers retry; no ANR. Thread count returns to baseline. | T1, T2 |
| **A-06** | Cancellation is swallowed, converted to a retry, or leaks work | Cancel at each stage: before send, mid-body, during backoff delay, during DB write. Assert `CancellationException` is never caught by generic `IOException`/`Exception` handlers. 100 cancel/restart cycles. | No retry after cancellation; temp files/buffers/streams released; coroutine/job count returns to baseline; no duplicate DB rows or downloads. | T1, T2 |
| **A-07** | Retry storm / thundering herd amplifies load and battery drain | 200 concurrent calls against MockWebServer returning 500, 429 + `Retry-After`, 403, connection reset. Count requests and inspect timings. | Retry only on transient IO/5xx/429; max 3 attempts; exponential **with jitter**; honours `Retry-After`; zero retry on 4xx/auth; global in-flight cap enforced. Total request count ≤ documented bound. | T1, T4 |
| **A-08** | One failing lyrics provider blocks the fallback chain | Provider 1 hangs, provider 2 throws, provider 3 succeeds. Registry traversal timing. | Per-provider timeout isolates failure; chain returns provider 3's result within the aggregate budget; no unbounded sequential waits. | T1, T2 |
| **A-09** | Hostile provider response is trusted as a URL/path | Provider returns artwork/audio URL with `file://`, `content://`, `javascript:`, `../`, 100 KB URL, credentials in authority. | Scheme allowlist (https only for remote); host validated; length cap; no scheme downgrade; rejected values produce placeholder, no fetch. | T1, T5 |

### Fixtures

- Deterministic corpus under `app/src/test/resources/adversarial/` (hash-pinned).
- Fuzz harness: Jazzer/JQF for JVM targets; `kotlin.test` property generators where a fuzzer is not
  available. Seed files minimised and committed.
- MockWebServer with a programmable fault script (status, delay, byte drip, close, redirect).

---

## 5. Suite B — Persistence, migration, and recovery

| ID | Threat | Test approach | Pass criteria | Tier |
|---|---|---|---|---|
| **B-01** | Corrupt database causes crash loop or silent total wipe | (a) byte-flip / truncate a valid v48 DB; (b) zero-length file; (c) random 4 KB. Launch app; inspect recovery path. | App starts or shows an explicit recoverable error with export/backup option. No crash loop (> 1 crash = fail). No silent deletion of user data. Wipe only via an explicit, user-confirmed action. | T2, T4 |
| **B-02** | Migration loses rows or violates constraints on some upgrade path | `MigrationTestHelper` across all declared auto-migrations 1→48; for each, seed every table with boundary rows (empty string, max int, null FK), migrate, compare. `PRAGMA integrity_check` and `foreign_key_check`. | Every path succeeds; row counts and field values preserved except for documented transformations; both pragmas clean; exported schema identity matches. | T1, T2 |
| **B-03** | Process killed mid-migration leaves a half-upgraded DB | Inject a crash (kill -9) at each migration step using a fault-injecting `SupportSQLiteOpenHelper`; relaunch. | SQLite transaction rollback leaves the previous version intact; relaunch either retries cleanly or reports recoverable error. Never a half-migrated schema that later writes corrupt data. | T4 |
| **B-04** | Downgrade (newer DB opened by older build) corrupts data | Open v48 DB with a v47 build. | Explicit "database is newer" handling; no destructive fallback; no writes. | T2 |
| **B-05** | Corrupt/partial DataStore silently resets all settings including secrets | Truncate `settings.preferences_pb`, fill with random bytes, valid-protobuf-wrong-types. Exercise `DataStore.get` helpers. | `CorruptionException` handled explicitly — not silently swallowed (OBS-09 must be fixed). App starts with defaults; corruption logged without secret contents; user informed that settings were reset. No crash. | T1, T2 |
| **B-06** | Concurrent DataStore writers lose updates or corrupt the file | 100 parallel `edit {}` on distinct and same keys; kill process between temp-write and rename. | No lost updates for distinct keys; last-writer-wins deterministic for same key; file always parses after kill; no partial protobuf. | T1, T4 |
| **B-07** | Queue snapshot corrupt/oversized/hostile breaks recovery | Cases: truncated serialized blob, 5 MB blob, 10 000-item queue, `mediaItemIndex` −1 / 999 999, `shuffledIndices` with duplicates/out-of-range/negatives, null `queueData`, stale continuation. Kill process mid-write 50×. | Recovery yields a consistent queue or a clean empty state. Index clamped; invalid shuffle indices dropped; size cap (≤ 5 MB / 10 000 items) enforced before deserialise; no gadget/arbitrary class instantiation; no crash loop; restore ≤ 1 s. | T2, T4, T5 |
| **B-08** | Playback state after process death resumes wrong item/position | Kill process during play/pause/seek/queue-change transitions; relaunch. | Resumes the same item at the saved position (± 1 s), or empty state if snapshot invalid — never a different item. No double resume. | T4 |
| **B-09** | Backup/restore leaks secrets or restores an incompatible DB | Inspect `backup_rules.xml` / `data_extraction_rules.xml`; run `adb backup` then restore onto a different install. | AI/OAuth tokens and provider credentials excluded from cloud backup and device transfer (OBS-05 must be fixed); restored DB migrates or is rejected safely; no plaintext secret in the backup stream. | T2, T4 |
| **B-10** | Large lyric/recognition caches grow without bound | Populate lyrics cache and recognition history to 10× expected; measure. | Documented cap enforced with LRU/age eviction; DB size bounded; eviction does not corrupt rows. | T2, T4 |

---

## 6. Suite C — IPC, components, and deep links

| ID | Threat | Test approach | Pass criteria | Tier |
|---|---|---|---|---|
| **C-01** | Mutable `PendingIntent` lets another app rewrite the target | Static scan of every `PendingIntent.get*`; instrumented enumeration of created instances; malicious test app attempts `fillIn`. | 100 % of created PendingIntents carry `FLAG_IMMUTABLE` (baseline audit shows all current call sites do); 0 mutable; lint rule enforced. | T1, T2 |
| **C-02** | Exported receivers/services/providers perform privileged work for any caller | Second test APK invokes every exported component: 10 widget receivers, `MusicWidgetReceiver` actions, `MediaButtonReceiver`, `MusicService`, `PlatformSchedulerService`, QS tile, `AutomotiveArtworkProvider`. Fuzz actions/extras. | Each exported component either requires a signature/BIND permission or ignores unauthenticated hostile input. No privileged mutation, no credential/metadata disclosure, no crash. Manifest audit sheet lists every exported component with its justification (G-SEC-2). | T2, T3, T5 |
| **C-03** | Forged/tampered automotive artwork URI turns the exported provider into an SSRF proxy | Call `AutomotiveArtwork.originalUrl` with: missing signature, wrong signature, truncated/extra segments, `..` segment, non-`art` path, uppercase scheme, 100 KB URL, unicode/percent tricks, duplicate slashes. Cross-app `content://` query with forged URI. Verify HMAC comparison is constant-time. | Signature required and constant-time (`MessageDigest.isEqual`); only `http`/`https` accepted, and the **provider policy** (D-01) still applies to the fetched URL; unsigned/oversized/malformed → `null`; provider performs zero fetches for rejected URIs; secret is 32 random bytes persisted once. | T1, T2, T5 |
| **C-04** | FileProvider exposes files outside the intended share | Request sibling paths, `../` and percent-encoded traversal, symlink, root alias, directory listing. Inspect `provider_paths.xml` roots. | Only the explicitly granted file is readable; traversal rejected; no directory listing; roots narrowed to dedicated share/download directories (OBS-04 must be fixed); grants are per-URI, never per-directory. | T2, T3 |
| **C-05** | Deep links and App Links are abused for session hijack, intent redirection, or oversized payloads | Fuzz `revenge://listen`, `https://metroserverx.meowery.eu/listen`, YouTube hosts/paths, `MUSIC_PLAY_FROM_SEARCH`, `SEND` text, NFC `NDEF_DISCOVERED`. Include 1 MB extras, nested `Intent`, `javascript:`/`file:` in text, malformed IDs, missing verification. | All parameters validated by format/length; joining a Listen Together session always requires explicit user confirmation; extras capped; YouTube/text inputs parsed by allowlist; no intent redirection; unverified App Link falls back safely; no file read. | T2, T3, T5 |
| **C-06** | Exported `MusicService` lets another app control playback or exfiltrate data | Connect a `MediaController` from the test app; call play/skip/setMediaItem/queue ops with hostile `mediaId` values. | Documented policy: either playback control is intentionally public and harmless, or the session rejects it. No file path, token, or local file disclosure; `mediaId` never used as a raw path/URL. | T2, T3 |
| **C-07** | Widget/cluster binders trust stale or hostile state | Kill the service, then trigger widget updates and RemoteViews actions. | Widgets render an explicit stopped/empty state, never stale credentials or a crash; action intents validated; no ANR. | T3 |
| **C-08** | Component "confused deputy": a URI grant is reused after revoke | Grant, revoke, then re-open the content URI. | Access denied after revoke; no cached handle bypass. | T2 |

> Note on OBS-03: the provider is intentionally exported because Android Auto/cluster processes load
> `content://` artwork. C-03 plus D-01 are the compensating controls; neither alone is sufficient.

---

## 7. Suite D — Network egress, endpoint trust, and secrets

| ID | Threat | Test approach | Pass criteria | Tier |
|---|---|---|---|---|
| **D-01** | SSRF via user-configurable AI base URL (OBS-02) | Host table: `127.0.0.1`, `localhost`, `[::1]`, `10.0.0.1`, `172.16.0.1`, `192.168.1.1`, `169.254.169.254`, `0.0.0.0`, `*.local`, `*.internal`, decimal/octal/hex IPs, `user@host`, trailing dot, DNS rebinding (resolve public then private), HTTP 302 to a private host. | Endpoint policy is an **allowlist of approved HTTPS hosts**, or a public-IP-only policy that checks the **resolved address after DNS and re-checks every redirect**. Loopback/RFC1918/link-local/ULA/multicast/0.0.0.0 rejected. HTTPS required for non-local endpoints. Custom endpoints require explicit user consent and are labelled unverified. | T1, T5 |
| **D-02** | Secret leakage via storage, logs, backups, or errors (OBS-05) | Inspect on-disk files, `logcat` across all app paths, crash buffer, exported provider responses, backup stream. Grep for key prefixes and bearer tokens. | Secrets live in Keystore-backed storage or are excluded from backup; never appear in logcat, Timber tags, exception messages, analytics, or provider output; errors redact `Authorization`/`x-api-key`; key rotation possible. | T2, T4 |
| **D-03** | App-wide cleartext allows MITM (OBS-01) | MITM proxy: force `http://` to a remote host and to the AI endpoint; static check of network security config. | Cleartext permitted **only** for explicit local Listen Together hosts via `domain-config`; all remote metadata, artwork, update, and AI endpoints are HTTPS-only. Static gate fails the build if `base-config` allows cleartext. | T2, T3 |
| **D-04** | Custom TLS trust bypasses hostname verification | Static scan for `HostnameVerifier`, `X509TrustManager`, `ALLOW_ALL`; MITM with self-signed cert. | No insecure verifier or all-trust manager; self-signed rejected; certificate errors surfaced, not swallowed. | T1, T3 |
| **D-05** | Updater downloads an unverified artifact or trusts a hostile redirect | Point updater at a redirect to HTTP / different host / oversized file; serve corrupt APK. | HTTPS only, host allowlisted, redirects re-validated, size cap, checksum/signature verified before install prompt; corrupt artifact discarded. | T2, T3 |
| **D-06** | Listen Together WebSocket trusts server-supplied URLs (SSRF pivot) | Mock server sends artwork/host URLs pointing at localhost/LAN/`file:`. | Client does not fetch server-supplied URLs outside the D-01 policy; no local file read. | T1, T2 |
| **D-07** | Authentication cookies/tokens forwarded to a non-allowlisted host | Inspect request construction for absolute-URL overrides. | Auth headers only sent to the intended allowlisted origin; no cross-origin credential forwarding. | T1, T2 |

---

## 8. Suite E — Resource pressure and background execution

| ID | Threat | Test approach | Pass criteria | Tier |
|---|---|---|---|---|
| **E-01** | 100 000-item playlist causes OOM/ANR | Seed 100 k songs and a 100 k-item playlist; open, scroll, reorder, search, shuffle. Measure first frame, RSS, main-thread stalls. | Paged queries only; first frame ≤ 2 s; RSS ≤ 256 MB; zero main-thread DB/IO; reorder of 100 k items completes with progress and no ANR. | T3, T4 |
| **E-02** | Storage full mid-download promotes a partial file or corrupts state | Fill the volume to 0 bytes; start download and export; also delete free space while writing. | Explicit storage-full error; temp file deleted or quarantined, never renamed to the final name; existing files untouched; retry after space is freed succeeds; no crash. | T2, T4 |
| **E-03** | Concurrent downloads exhaust connections, duplicate work, or collide on paths | 50 simultaneous downloads; same song requested twice; pause/resume; screen off. | Documented concurrency cap enforced; duplicate requests deduped (`ExportTaskQueue` semantics preserved); atomic temp-then-rename; no duplicate network fetches for the same item. | T2, T4 |
| **E-04** | Metadata-derived filenames traverse directories or overwrite unrelated files | Titles/artists: `../../x`, absolute paths, NUL, newline, reserved Windows names, 4 KB names, emoji, RTL marks, `.`/`..`. | Filenames sanitised, length-capped, confined under the download root, collision-suffixed; no write outside root; no overwrite of unrelated files. | T1, T2, T5 |
| **E-05** | Background restrictions kill or starve downloads/exports | Android 14 `dataSync` FGS timeout, Doze, App Standby buckets, battery saver, reboot mid-download. | Work delegated to WorkManager/PlatformScheduler survives reboot and Doze; no crash/ANR at FGS timeout; user-visible paused/failed state; no silent loss. | T3, T4 |
| **E-06** | Caches exceed their cap or evict while in use | Populate automotive artwork cache past 64 MB; lyric and cover caches past expectation; evict during read. | Trim to the documented 48 MB low-water mark; eviction never corrupts an in-flight read; total cache footprint within budget. | T2, T4 |
| **E-07** | Recognition captures audio forever or leaks temp PCM | Deny, cancel, time out, and kill during recognition; inspect temp dir before/after. | Capture stops on every path; temp PCM deleted in `finally`; no file survives a cancel/kill; microphone released; no battery drain after stop. | T2, T4 |
| **E-08** | Memory/FD leak over long use | 24 h soak: random play/pause/skip/search/lyrics/download; sample RSS, FDs, thread count hourly. | RSS growth ≤ 10 % after warm-up; FD and thread counts stable; zero ANR; zero OOM. | T4 |

---

## 9. Suite F — Permission denial and degraded modes

Each case is run on API 30 (legacy storage), 33 (granular media + notifications), and 36.

| ID | Threat | Test approach | Pass criteria | Tier |
|---|---|---|---|---|
| **F-01** | `RECORD_AUDIO` denied crashes recognition or leaves the mic open | Deny on first ask; start recognition from QS tile, widget, and in-app; check service and temp files. | No crash; rationale + Settings deep link shown; feature disabled cleanly; no capture started; no temp PCM; QS tile/widget show unavailable state. | T3 |
| **F-02** | Notification permission denied breaks playback/FGS | Deny `POST_NOTIFICATIONS` on API 33+; start playback, download, recognition. | Playback and FGS still function (FGS notification is exempt); in-app explanation shown; no crash or silent failure; controls reachable in-app. | T3 |
| **F-03** | Storage/media permission partial or denied breaks library/downloads | Deny `READ_MEDIA_AUDIO`; grant only some media types; revoke after grant; test SAF fallback. | Clear empty/degraded state, no crash; SAF picker fallback works; downloads confined to app dirs; no repeated permission nagging. | T3 |
| **F-04** | "Don't ask again" / permanent denial causes a prompt loop | Select permanent denial; re-enter the feature 5×. | At most one prompt; Settings path offered; feature stays degraded without repeated dialogs or ANR. | T3 |
| **F-05** | Permission revoked mid-session leaves a service running with stale authority | Revoke while recognition/download is active. | Service stops the privileged operation immediately, cleans temp state, updates UI; no crash; no SecurityException loop. | T3, T4 |
| **F-06** | `WRITE_SETTINGS` denial crashes a settings toggle | Deny and toggle the dependent feature. | Toggle reverts with an explanation; no crash; no partial write. | T3 |

---

## 10. Suite G — Fuzz/property targets and resource-exhaustion limits

### 10.1 Fuzz target register

| ID | Target | Harness | Seed corpus | Time budget |
|---|---|---|---|---|
| **G-01** | `TTMLParser.parseTTML` + `toLRC` | Jazzer (JVM) | 5 valid TTML, 5 hostile XML | 60 min nightly |
| **G-02** | LRC parse/serialise (`LyricsUtils`) | Jazzer | 10 real LRC, 5 malformed | 60 min nightly |
| **G-03** | AI JSON extraction (`AiTranslationParsing`, playlist/recommendation parsers) | Jazzer | 10 real model outputs, 10 hostile | 60 min nightly |
| **G-04** | Listen Together protobuf decode (`listentogether.proto`) | Jazzer/protobuf mutator | valid messages | 30 min nightly |
| **G-05** | Parametric EQ parser/validator (`dsp-core`) | Jazzer | valid configs | 30 min nightly |
| **G-06** | `AutomotiveArtwork.originalUrl` | Property generator | URI shapes from C-03 | 15 min PR |
| **G-07** | `OpenRouterService.isUrlSafe` + endpoint policy | Property generator | host table from D-01 | 15 min PR |
| **G-08** | Queue snapshot deserialisation | Jazzer on a bounded reader | valid snapshots | 30 min nightly |
| **G-09** | Filename sanitiser | Property generator | E-04 corpus | 15 min PR |
| **G-10** | DataStore enum/int parsing | Property generator | all enum values + junk | 15 min PR |

Fuzz failures are minimised and committed as regression seeds. A fuzzer that only finds
`OOM`/`timeout` without a minimised input is treated as a resource-limit failure (G-11) and still
files a bug.

### 10.2 Property tests

| ID | Property |
|---|---|
| **G-11** | LRC→TTML→LRC round-trip preserves line count, ordering, and monotonic timestamps within 1 ms |
| **G-12** | Queue save→restore is identity for all valid `PersistQueue` values |
| **G-13** | Filename sanitiser is idempotent and never returns an absolute or parent-relative path |
| **G-14** | JSON encode(decode(x)) == x for every accepted model output; decode never throws |
| **G-15** | `isUrlSafe`/endpoint policy is total: returns a decision for every string, never throws |

### 10.3 Resource-exhaustion limit register

These are the only accepted limits. A test that exceeds a limit fails; a product path that has no
limit is a finding.

| Resource | Limit | Enforcement point | Behaviour at limit |
|---|---|---|---|
| Lyric text | 5 MB | before parse | reject with user-visible "too large" |
| TTML DOM depth | 64 | before DOM construction | return empty |
| TTML nodes | 100 000 | during parse | stop, return partial |
| AI response body | 2 MB | streaming read | abort, error |
| JSON nesting | 32 | parser | abort, error |
| Queue snapshot | 5 MB / 10 000 items | before deserialise | discard, empty state |
| Playlist render | paged, 100 k max | DAO/paging | paging |
| Concurrent downloads | documented cap (≤ 3) | scheduler | queue |
| Concurrent provider calls | documented cap | dispatcher | queue |
| Cache (automotive) | 64 MB hard / 48 MB low-water | cache manager | trim |
| Recognition capture | documented wall-clock max | service | stop + cleanup |
| Retry attempts | 3, jittered | retry policy | surface error |
| Intent extras / deep-link params | 64 KB | entry validation | reject |
| Filename | 200 bytes | sanitiser | truncate + hash |

### 10.4 Soak

**G-16:** 24 h randomized operation soak (playback, search, lyrics, downloads, recognition, theme
changes, background/foreground, process kill every 2 h). Pass = no crash, no ANR, RSS growth ≤ 10 %,
FD/thread stable, no orphaned foreground service.

---

## 11. Execution, cadence, and release gates

| Stage | Runs | Required rows |
|---|---|---|
| **PR / pre-merge** | T1 fast + 15 min fuzz + static | A-01, A-02, A-03, A-06, B-01, B-05, C-01, C-03, D-01, D-02, E-04, G-06, G-07, G-09, G-10 |
| **Nightly** | T2 + 1 h fuzz + property | all A, B, C, D, E, F automated rows; G-01…G-05, G-08, G-11…G-15 |
| **Release candidate** | T3 + T4 chaos + soak | every row in §4–§10; fresh install **and** upgrade install |
| **M6 sign-off** | Evidence review | 100 % Required rows pass; seed/corpus hash recorded; residual risk in §12 accepted and disclosed |

Mapping to existing gates: C-01 satisfies **G-SEC-2**; D-03/D-04 support **G-SEC-1**; C-02/C-03/C-04/D-02
support **G-SEC-2**; the whole suite is the **F16 Tier-5** evidence required by
`VALIDATION_AND_RELEASE_GATES.md` §5.2.

**Known baseline failures to expect on first run** (these are real, not test bugs): OBS-01 fails
D-03; OBS-02 fails D-01; OBS-04 fails C-04; OBS-05 fails D-02/B-09; OBS-06 fails A-02 unless the
implementation caps input. Record them as findings, do not weaken the assertion.

---

## 12. Out of scope and residual risk

- **Physical hardware:** USB DAC enumeration/fallback, real Android Auto and Automotive OS head
  units, Bluetooth stack quirks. Covered by manual smoke plus T3 where a device is available; not
  fully automatable in CI.
- **Upstream services:** InnerTube, Spotify, Jellyfin, Navidrome, LRCLIB, KuGou, Shazam, Last.fm,
  ListenBrainz. Tests use mockable provider boundaries; live-service drift is out of scope and tracked
  under the R5 external-terms disclosure.
- **Vendor background-policy drift:** OEM-specific FGS/Doze behaviour is sampled on the device matrix,
  not exhaustive.
- **Side-channel/timing** beyond the constant-time HMAC comparison (C-03) and credential redaction
  (D-02).
- **Rooted/adversarial-device scenarios** (e.g. hooking, backup extraction on a rooted device) are
  accepted risk; Android's threat model excludes them.

Residual risk after this plan: a hostile app on the same device can still drive any intentionally
exported control surface within its documented policy (C-02/C-06); and a user-configured custom AI
endpoint remains trusted by the user's own choice (D-01). Both are disclosed rather than "solved".
