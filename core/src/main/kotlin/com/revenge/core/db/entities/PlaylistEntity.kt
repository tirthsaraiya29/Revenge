package com.revenge.core.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.revenge.innertube.YouTube
import java.time.LocalDateTime
import org.apache.commons.lang3.RandomStringUtils

@Immutable
@Entity(tableName = "playlist")
data class PlaylistEntity(
  @PrimaryKey val id: String = generatePlaylistId(),
  val name: String,
  val browseId: String? = null,
  val createdAt: LocalDateTime? = LocalDateTime.now(),
  val lastUpdateTime: LocalDateTime? = LocalDateTime.now(),
  @ColumnInfo(name = "isEditable", defaultValue = "true") val isEditable: Boolean = true,
  val bookmarkedAt: LocalDateTime? = null,
  val remoteSongCount: Int? = null,
  val playEndpointParams: String? = null,
  val thumbnailUrl: String? = null,
  val shuffleEndpointParams: String? = null,
  val radioEndpointParams: String? = null,
  @ColumnInfo(name = "isLocal", defaultValue = "false") val isLocal: Boolean = false,
  @ColumnInfo(name = "isAutoSync", defaultValue = "false") val isAutoSync: Boolean = false,
  @ColumnInfo(name = "isPinned", defaultValue = "false") val isPinned: Boolean = false,
) {
  val shareLink: String?
    get() = browseId?.let { "https://music.youtube.com/playlist?list=$it" }

  fun localToggleLike(): PlaylistEntity =
    copy(bookmarkedAt = if (bookmarkedAt == null) LocalDateTime.now() else null)

  fun toggleLike(): PlaylistEntity {
    val next = localToggleLike()
    val liked = next.bookmarkedAt != null
    browseId?.let { launchBestEffort { YouTube.likePlaylist(it, liked) } }
    return next
  }

  companion object {
    const val LIKED_PLAYLIST_ID = "LP_LIKED"
    const val DOWNLOADED_PLAYLIST_ID = "LP_DOWNLOADED"

    fun generatePlaylistId(): String = "LP" + RandomStringUtils.randomAlphanumeric(8)
  }
}
