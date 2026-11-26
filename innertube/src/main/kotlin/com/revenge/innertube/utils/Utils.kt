package com.revenge.innertube.utils

import com.revenge.innertube.YouTube
import com.revenge.innertube.pages.LibraryPage
import com.revenge.innertube.pages.PlaylistPage
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException

/**
 * `runCatching` swallows a [CancellationException] like any other failure, which would stop the
 * surrounding coroutine from cancelling. These two pagination helpers rethrow it after collecting
 * a result so real cancellation still propagates.
 */
private fun <T> Result<T>.propagateCancellation(): Result<T> = also {
  val cause = exceptionOrNull()
  if (cause is CancellationException) throw cause
}

/** Walks every page of a playlist, stopping on a repeated token or after a sane request budget. */
@JvmName("completedLibrary")
suspend fun Result<PlaylistPage>.completed(): Result<PlaylistPage> = runCatching {
  val first = getOrThrow()
  val songs = first.songs.toMutableList()
  val visited = mutableSetOf<String>()
  var token = first.songsContinuation
  var emptyStreak = 0

  while (token != null && visited.size < MAX_PAGES && visited.add(token)) {
    // A transient failure here must fail the whole result rather than look like the end of the
    // list: callers reconcile local state against the returned songs and would drop the tail.
    val page =
      YouTube.playlistContinuation(token).getOrElse {
        if (it is CancellationException) throw it
        throw IllegalStateException("Playlist pagination failed", it)
      }
    if (page.songs.isEmpty()) {
      if (++emptyStreak >= 2) break
    } else {
      emptyStreak = 0
      songs += page.songs
    }
    token = page.continuation
  }

  PlaylistPage(first.playlist, songs, songsContinuation = null, continuation = first.continuation)
}
  .propagateCancellation()

/** The same walk for a library shelf. */
@JvmName("completedPlaylist")
suspend fun Result<LibraryPage>.completed(): Result<LibraryPage> = runCatching {
  val first = getOrThrow()
  val items = first.items.toMutableList()
  val visited = mutableSetOf<String>()
  var token = first.continuation
  var emptyStreak = 0

  while (token != null && visited.size < MAX_PAGES && visited.add(token)) {
    val page =
      YouTube.libraryContinuation(token).getOrElse {
        if (it is CancellationException) throw it
        throw IllegalStateException("Library pagination failed", it)
      }
    if (page.items.isEmpty()) {
      if (++emptyStreak >= 2) break
    } else {
      emptyStreak = 0
      items += page.items
    }
    token = page.continuation
  }

  LibraryPage(items, continuation = null)
}
  .propagateCancellation()

private const val MAX_PAGES = 50

fun ByteArray.toHex(): String = joinToString(separator = "") { "%02x".format(it) }

fun sha1(str: String): String =
  MessageDigest.getInstance("SHA-1").digest(str.toByteArray()).toHex()

/** Parses a `a=b; c=d` cookie header into a map, skipping malformed pairs. */
fun parseCookieString(cookie: String): Map<String, String> =
  cookie
    .split("; ")
    .filter { it.isNotEmpty() }
    .mapNotNull { part ->
      val equals = part.indexOf('=')
      if (equals == -1) null else part.substring(0, equals) to part.substring(equals + 1)
    }
    .toMap()

/** Parses `mm:ss` or `hh:mm:ss` into seconds. */
fun String.parseTime(): Int? {
  val parts = split(":").mapNotNull { it.toIntOrNull() }
  return when (parts.size) {
    2 -> parts[0] * 60 + parts[1]
    3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
    else -> null
  }
}

fun isPrivateId(browseId: String): Boolean = browseId.contains("privately")
