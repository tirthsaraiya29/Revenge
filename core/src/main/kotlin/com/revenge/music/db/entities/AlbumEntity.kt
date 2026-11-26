package com.revenge.music.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.revenge.innertube.YouTube
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Immutable
@Entity(tableName = "album")
data class AlbumEntity(
  @PrimaryKey val id: String,
  val playlistId: String? = null,
  val title: String,
  val year: Int? = null,
  val thumbnailUrl: String? = null,
  val themeColor: Int? = null,
  val songCount: Int,
  val duration: Int,
  @ColumnInfo(defaultValue = "0") val explicit: Boolean = false,
  val lastUpdateTime: LocalDateTime = LocalDateTime.now(),
  val bookmarkedAt: LocalDateTime? = null,
  val likedDate: LocalDateTime? = null,
  val inLibrary: LocalDateTime? = null,
  val description: String? = null,
  @ColumnInfo(name = "isLocal", defaultValue = "false") val isLocal: Boolean = false,
  @ColumnInfo(name = "isUploaded", defaultValue = "false") val isUploaded: Boolean = false,
) {
  private fun flipsBookmark() = bookmarkedAt == null

  fun localToggleLike() = copy(bookmarkedAt = if (flipsBookmark()) LocalDateTime.now() else null)

  fun toggleUploaded() = copy(isUploaded = !isUploaded)

  fun toggleLibrary() = copy(inLibrary = if (inLibrary == null) LocalDateTime.now() else null)

  fun toggleLike() =
    localToggleLike().also { updated ->
      val becameLiked = updated.bookmarkedAt != null
      CoroutineScope(Dispatchers.IO).launch {
        playlistId?.let { YouTube.likePlaylist(it, becameLiked) }
      }
    }
}
