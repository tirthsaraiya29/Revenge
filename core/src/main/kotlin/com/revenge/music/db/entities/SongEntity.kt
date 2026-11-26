package com.revenge.music.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.revenge.innertube.YouTube
import java.time.LocalDateTime

@Immutable
@Entity(tableName = "song", indices = [Index("albumId")])
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val duration: Int = -1,
    val thumbnailUrl: String? = null,
    val albumId: String? = null,
    val albumName: String? = null,
    @ColumnInfo(defaultValue = "0") val explicit: Boolean = false,
    val year: Int? = null,
    val date: LocalDateTime? = null,
    val dateModified: LocalDateTime? = null,
    val liked: Boolean = false,
    val likedDate: LocalDateTime? = null,
    val totalPlayTime: Long = 0,
    val inLibrary: LocalDateTime? = null,
    val dateDownload: LocalDateTime? = null,
    @ColumnInfo(name = "isLocal", defaultValue = "false") val isLocal: Boolean = false,
    val libraryAddToken: String? = null,
    val libraryRemoveToken: String? = null,
    @ColumnInfo(defaultValue = "0") val lyricsOffset: Int = 0,
    @ColumnInfo(defaultValue = "true") val romanizeLyrics: Boolean = true,
    @ColumnInfo(defaultValue = "0") val isDownloaded: Boolean = false,
    @ColumnInfo(name = "isUploaded", defaultValue = "false") val isUploaded: Boolean = false,
    @ColumnInfo(name = "isVideo", defaultValue = "false") val isVideo: Boolean = false,
    @ColumnInfo(defaultValue = "0") val hideFromQuickPicks: Boolean = false,
) {
    fun localToggleLike(): SongEntity {
        val nowLiked = !liked
        return copy(liked = nowLiked, likedDate = if (nowLiked) LocalDateTime.now() else null)
    }

    fun toggleLike(): SongEntity {
        val next = localToggleLike()
        val result = if (next.liked && next.inLibrary == null) next.copy(inLibrary = next.likedDate) else next
        launchBestEffort { YouTube.likeVideo(id, !liked) }
        return result
    }

    fun toggleLibrary(syncToYouTube: Boolean = true): SongEntity {
        val entering = inLibrary == null
        val next = copy(
            inLibrary = if (entering) LocalDateTime.now() else null,
            liked = if (entering) liked else false,
            likedDate = if (entering) likedDate else null,
        )
        if (syncToYouTube) launchBestEffort { YouTube.toggleSongLibrary(id, entering) }
        return next
    }

    fun toggleUploaded(): SongEntity = copy(isUploaded = !isUploaded)

    fun toggleHideFromQuickPicks(): SongEntity = copy(hideFromQuickPicks = !hideFromQuickPicks)
}
