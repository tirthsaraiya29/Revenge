package com.revenge.music.db.entities

import androidx.room.DatabaseView

@DatabaseView(
    viewName = "sorted_song_artist_map",
    value = "SELECT * FROM song_artist_map ORDER BY position",
)
data class SortedSongArtistMap(
    val songId: String,
    val artistId: String,
    val position: Int,
)
