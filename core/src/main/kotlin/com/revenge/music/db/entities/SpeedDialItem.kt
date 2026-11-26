package com.revenge.music.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.revenge.innertube.models.AlbumItem
import com.revenge.innertube.models.Artist
import com.revenge.innertube.models.ArtistItem
import com.revenge.innertube.models.PlaylistItem
import com.revenge.innertube.models.SongItem
import com.revenge.innertube.models.YTItem

@Entity(tableName = "speed_dial_item")
data class SpeedDialItem(
    @PrimaryKey val id: String,
    val secondaryId: String? = null,
    val title: String,
    val subtitle: String? = null,
    val thumbnailUrl: String? = null,
    val type: String,
    val explicit: Boolean = false,
    val createDate: Long = System.currentTimeMillis(),
) {
    fun toYTItem(): YTItem = when (type) {
        TYPE_SONG -> SongItem(
            id = id,
            title = title,
            artists = subtitle?.split(", ")?.map { Artist(it, null) } ?: emptyList(),
            thumbnail = thumbnailUrl.orEmpty(),
            explicit = explicit,
        )
        TYPE_ALBUM -> AlbumItem(
            browseId = id,
            playlistId = secondaryId.orEmpty(),
            title = title,
            artists = subtitle?.split(", ")?.map { Artist(it, null) },
            thumbnail = thumbnailUrl.orEmpty(),
            explicit = explicit,
        )
        TYPE_ARTIST -> ArtistItem(
            id = id,
            title = title,
            thumbnail = thumbnailUrl,
            shuffleEndpoint = null,
            radioEndpoint = null,
        )
        TYPE_PLAYLIST -> PlaylistItem(
            id = id,
            title = title,
            author = subtitle?.let { Artist(it, null) },
            songCountText = null,
            thumbnail = thumbnailUrl,
            playEndpoint = null,
            shuffleEndpoint = null,
            radioEndpoint = null,
        )
        else -> throw IllegalArgumentException("Unknown speed dial type: $type")
    }

    companion object {
        private const val TYPE_SONG = "SONG"
        private const val TYPE_ALBUM = "ALBUM"
        private const val TYPE_ARTIST = "ARTIST"
        private const val TYPE_PLAYLIST = "PLAYLIST"

        fun fromYTItem(item: YTItem): SpeedDialItem = when (item) {
            is SongItem -> SpeedDialItem(
                id = item.id,
                title = item.title,
                subtitle = item.artists.joinToString(", ") { it.name },
                thumbnailUrl = item.thumbnail,
                type = TYPE_SONG,
                explicit = item.explicit,
            )
            is AlbumItem -> SpeedDialItem(
                id = item.browseId,
                secondaryId = item.playlistId,
                title = item.title,
                subtitle = item.artists?.joinToString(", ") { it.name },
                thumbnailUrl = item.thumbnail,
                type = TYPE_ALBUM,
                explicit = item.explicit,
            )
            is ArtistItem -> SpeedDialItem(
                id = item.id,
                title = item.title,
                thumbnailUrl = item.thumbnail,
                type = TYPE_ARTIST,
            )
            is PlaylistItem -> SpeedDialItem(
                id = item.id,
                title = item.title,
                subtitle = item.author?.name,
                thumbnailUrl = item.thumbnail,
                type = TYPE_PLAYLIST,
                explicit = item.explicit,
            )
            else -> throw IllegalArgumentException("Unsupported item: ${item::class.java.name}")
        }
    }
}
