package com.revenge.music.db.entities

import androidx.room.DatabaseView

@DatabaseView(
    viewName = "sorted_song_album_map",
    value = "SELECT * FROM song_album_map ORDER BY `index`",
)
data class SortedSongAlbumMap(
    val songId: String,
    val albumId: String,
    val index: Int,
)
