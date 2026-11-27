package com.revenge.innertube.utils

import com.revenge.innertube.YouTube
import com.revenge.innertube.pages.LibraryPage
import com.revenge.innertube.pages.PlaylistPage
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException

private const val MAX_CONTINUATION_PAGES = 50

/**
 * Walks a playlist's [PlaylistPage.songsContinuation] chain and returns one page containing every
 * song. A failed continuation aborts the walk (the tail must never be silently dropped); a repeated
 * token or two consecutive empty pages ends it.
 */
@JvmName("completedLibrary")
suspend fun Result<PlaylistPage>.completed(): Result<PlaylistPage> {
  val first = getOrElse { error ->
    if (error is CancellationException) throw error
    return Result.failure(error)
  }

  val songs = first.songs.toMutableList()
  val visited = HashSet<String>()
  var token = first.songsContinuation
  var emptyStreak = 0
  var fetches = 0

  while (token != null && fetches < MAX_CONTINUATION_PAGES && visited.add(token)) {
    val page =
      try {
        YouTube.playlistContinuation(token)
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (error: Throwable) {
        return Result.failure(IllegalStateException("Playlist pagination failed", error))
      }
    val value = page.getOrElse { error ->
      if (error is CancellationException) throw error
      return Result.failure(IllegalStateException("Playlist pagination failed", error))
    }
    fetches++
    if (value.songs.isEmpty()) {
      if (++emptyStreak >= 2) break
    } else {
      emptyStreak = 0
      songs += value.songs
    }
    token = value.continuation
  }

  return Result.success(first.copy(songs = songs, songsContinuation = null))
}

/** Library variant of [completed]; items append in order and the resulting continuation is null. */
@JvmName("completedPlaylist")
suspend fun Result<LibraryPage>.completed(): Result<LibraryPage> {
  val first = getOrElse { error ->
    if (error is CancellationException) throw error
    return Result.failure(error)
  }

  val items = first.items.toMutableList()
  val visited = HashSet<String>()
  var token = first.continuation
  var emptyStreak = 0
  var fetches = 0

  while (token != null && fetches < MAX_CONTINUATION_PAGES && visited.add(token)) {
    val page =
      try {
        YouTube.libraryContinuation(token)
      } catch (cancellation: CancellationException) {
        throw cancellation
      } catch (error: Throwable) {
        return Result.failure(IllegalStateException("Library pagination failed", error))
      }
    val value = page.getOrElse { error ->
      if (error is CancellationException) throw error
      return Result.failure(IllegalStateException("Library pagination failed", error))
    }
    fetches++
    if (value.items.isEmpty()) {
      if (++emptyStreak >= 2) break
    } else {
      emptyStreak = 0
      items += value.items
    }
    token = value.continuation
  }

  return Result.success(LibraryPage(items = items, continuation = null))
}

fun ByteArray.toHex(): String {
  val digits = "0123456789abcdef"
  return buildString(size * 2) {
    for (byte in this@toHex) {
      val value = byte.toInt() and 0xFF
      append(digits[value ushr 4])
      append(digits[value and 0x0F])
    }
  }
}

fun sha1(str: String): String =
  MessageDigest.getInstance("SHA-1").digest(str.toByteArray(Charsets.UTF_8)).toHex()

/** Parses a `; `-separated cookie string. Malformed parts are skipped; later duplicates win. */
fun parseCookieString(cookie: String): Map<String, String> =
  cookie
    .split("; ")
    .mapNotNull { part ->
      val separator = part.indexOf('=')
      if (separator <= 0) null else part.substring(0, separator) to part.substring(separator + 1)
    }
    .toMap()

/** `mm:ss` or `hh:mm:ss` to seconds. Values with any other number of numeric groups are null. */
fun String.parseTime(): Int? {
  val numbers = split(':').mapNotNull { part -> part.trim().toIntOrNull() }
  return when (numbers.size) {
    2 -> numbers[0] * 60 + numbers[1]
    3 -> numbers[0] * 3600 + numbers[1] * 60 + numbers[2]
    else -> null
  }
}

fun isPrivateId(browseId: String): Boolean = browseId.contains("privately")
