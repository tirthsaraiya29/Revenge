package com.revenge.music.extensions

import com.revenge.music.db.entities.Album
import com.revenge.music.db.entities.Playlist
import com.revenge.music.db.entities.Song

fun <T> List<T>.reversed(reversed: Boolean): List<T> = if (reversed) asReversed() else this

fun <T> MutableList<T>.move(fromIndex: Int, toIndex: Int): MutableList<T> {
    add(toIndex, removeAt(fromIndex))
    return this
}

fun <T : Any> List<T>.mergeNearbyElements(
    key: (T) -> Any = { it },
    merge: (first: T, second: T) -> T = { first, _ -> first },
): List<T> {
    if (isEmpty()) return emptyList()
    val result = ArrayList<T>(size)
    var pending = first()
    for (index in 1 until size) {
        val candidate = this[index]
        if (key(pending) == key(candidate)) {
            pending = merge(pending, candidate)
        } else {
            result.add(pending)
            pending = candidate
        }
    }
    result.add(pending)
    return result
}

fun List<Song>.filterExplicit(enabled: Boolean = true): List<Song> =
    if (enabled) filterNot { it.song.explicit } else this

fun List<Song>.filterVideoSongs(enabled: Boolean = true): List<Song> =
    if (enabled) filterNot { it.song.isVideo } else this

fun List<Album>.filterExplicitAlbums(enabled: Boolean = true): List<Album> =
    if (enabled) filterNot { it.album.explicit } else this

fun List<Playlist>.filterYoutubeShorts(enabled: Boolean = false): List<Playlist> =
    if (enabled) filterNot { it.playlist.browseId?.startsWith("SS") == true } else this
