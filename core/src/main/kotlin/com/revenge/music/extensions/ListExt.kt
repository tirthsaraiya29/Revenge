package com.revenge.music.extensions

import com.revenge.music.db.entities.Album
import com.revenge.music.db.entities.Playlist
import com.revenge.music.db.entities.Song

fun <T> List<T>.reversed(reversed: Boolean) = if (reversed) asReversed() else this

fun <T> MutableList<T>.move(
  fromIndex: Int,
  toIndex: Int,
): MutableList<T> {
  add(toIndex, removeAt(fromIndex))
  return this
}

/**
 * Collapses consecutive elements sharing [key] into one using [merge]. Used to fold duplicate
 * section headers produced by paged API responses.
 */
fun <T : Any> List<T>.mergeNearbyElements(
  key: (T) -> Any = { it },
  merge: (first: T, second: T) -> T = { first, _ -> first },
): List<T> {
  if (isEmpty()) return emptyList()

  val merged = ArrayList<T>(size)
  var current = first()
  for (i in 1 until size) {
    val next = this[i]
    current = if (key(current) == key(next)) merge(current, next) else next.also { merged += current }
  }
  merged += current
  return merged
}

fun List<Song>.filterExplicit(enabled: Boolean = true) =
  if (enabled) {
    filter { !it.song.explicit }
  } else {
    this
  }

fun List<Song>.filterVideoSongs(enabled: Boolean = true) =
  if (enabled) {
    filter { !it.song.isVideo }
  } else {
    this
  }

fun List<Album>.filterExplicitAlbums(enabled: Boolean = true) =
  if (enabled) {
    filter { !it.album.explicit }
  } else {
    this
  }

fun List<Playlist>.filterYoutubeShorts(enabled: Boolean = false) =
  if (enabled) {
    filterNot { it.playlist.browseId?.startsWith("SS") == true }
  } else {
    this
  }
