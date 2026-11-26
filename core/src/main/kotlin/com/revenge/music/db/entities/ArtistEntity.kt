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
import org.apache.commons.lang3.RandomStringUtils

@Immutable
@Entity(tableName = "artist")
data class ArtistEntity(
  @PrimaryKey val id: String,
  val name: String,
  val thumbnailUrl: String? = null,
  val channelId: String? = null,
  val lastUpdateTime: LocalDateTime = LocalDateTime.now(),
  val bookmarkedAt: LocalDateTime? = null,
  @ColumnInfo(name = "isLocal", defaultValue = "false") val isLocal: Boolean = false,
) {
  val isYouTubeArtist: Boolean
    get() = id.startsWith("UC") || id.startsWith(PRIVATE_ARTIST_PREFIX)

  val isPrivatelyOwnedArtist: Boolean
    get() = id.startsWith(PRIVATE_ARTIST_PREFIX)

  fun localToggleLike() =
    copy(bookmarkedAt = if (bookmarkedAt == null) LocalDateTime.now() else null)

  fun toggleLike() =
    localToggleLike().also { updated ->
      val shouldSubscribe = updated.bookmarkedAt != null
      CoroutineScope(Dispatchers.IO).launch {
        val target = channelId ?: YouTube.getChannelId(id)
        if (target.isNotEmpty()) YouTube.subscribeChannel(target, shouldSubscribe)
      }
    }

  companion object {
    private const val PRIVATE_ARTIST_PREFIX = "FEmusic_library_privately_owned_artist"

    /** Local artists have no channel id, so they get a random library-prefixed id. */
    fun generateArtistId(): String = "LA" + RandomStringUtils.insecure().next(8, true, false)
  }
}
