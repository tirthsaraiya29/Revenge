package com.revenge.music.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.revenge.innertube.YouTube
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Immutable
@Entity(tableName = "song", indices = [Index(value = ["albumId"])])
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
  fun localToggleLike() =
    copy(liked = !liked, likedDate = if (liked) null else LocalDateTime.now())

  fun toggleLike() =
    copy(
        liked = !liked,
        likedDate = if (liked) null else LocalDateTime.now(),
        inLibrary = if (liked) inLibrary else inLibrary ?: LocalDateTime.now(),
      )
      .also { CoroutineScope(Dispatchers.IO).launch { YouTube.likeVideo(id, !liked) } }

  fun toggleLibrary(syncToYouTube: Boolean = true) =
    copy(
        liked = if (inLibrary == null) liked else false,
        inLibrary = if (inLibrary == null) LocalDateTime.now() else null,
        likedDate = if (inLibrary == null) likedDate else null,
      )
      .also { updated ->
        if (syncToYouTube) {
          CoroutineScope(Dispatchers.IO).launch {
            YouTube.toggleSongLibrary(id, updated.inLibrary != null)
          }
        }
      }

  fun toggleUploaded() = copy(isUploaded = !isUploaded)

  fun toggleHideFromQuickPicks() = copy(hideFromQuickPicks = !hideFromQuickPicks)
}
