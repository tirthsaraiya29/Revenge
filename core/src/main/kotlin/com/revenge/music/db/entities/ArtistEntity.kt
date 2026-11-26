package com.revenge.music.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.revenge.innertube.YouTube
import java.time.LocalDateTime
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
        get() = id.startsWith("UC") || id.startsWith(PRIVATELY_OWNED_ARTIST_PREFIX)

    val isPrivatelyOwnedArtist: Boolean
        get() = id.startsWith(PRIVATELY_OWNED_ARTIST_PREFIX)

    fun localToggleLike(): ArtistEntity =
        copy(bookmarkedAt = if (bookmarkedAt == null) LocalDateTime.now() else null)

    fun toggleLike(): ArtistEntity {
        val next = localToggleLike()
        val subscribed = next.bookmarkedAt != null
        launchBestEffort {
            val resolved = channelId ?: YouTube.getChannelId(id)
            if (resolved.isNotEmpty()) YouTube.subscribeChannel(resolved, subscribed)
        }
        return next
    }

    companion object {
        private const val PRIVATELY_OWNED_ARTIST_PREFIX = "FEmusic_library_privately_owned_artist"

        fun generateArtistId(): String = "LA" + RandomStringUtils.randomAlphanumeric(8)
    }
}
