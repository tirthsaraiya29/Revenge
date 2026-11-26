package com.revenge.music.musicflow

import com.revenge.innertube.models.Album
import com.revenge.innertube.models.Artist
import com.revenge.innertube.models.SongItem
import com.revenge.music.models.MediaMetadata

enum class MusicFlowSource { NOW_PLAYING, HISTORY, LIKED, QUICK_PICKS, CATALOG, OFFLINE }

enum class TakeoverBlock { CASTING, LISTEN_TOGETHER_GUEST }

enum class MusicFlowFeedError { OFFLINE, EMPTY, FAILED }

sealed class MusicFlowPlaybackStatus {
    data object Idle : MusicFlowPlaybackStatus()
    data object Switching : MusicFlowPlaybackStatus()
    data object Synced : MusicFlowPlaybackStatus()
    data class Blocked(val reason: TakeoverBlock) : MusicFlowPlaybackStatus()
    data object ExternalOverride : MusicFlowPlaybackStatus()
}

data class MusicFlowItem(
    val song: SongItem,
    val source: MusicFlowSource,
) {
    val songId: String get() = song.id

    val primaryArtistKey: String?
        get() = song.artists.firstOrNull()?.let { artist ->
            artist.id?.takeIf { it.isNotBlank() } ?: artist.name.lowercase()
        }
}

data class MusicFlowFeedState(
    val items: List<MusicFlowItem> = emptyList(),
    val selectedIndex: Int = 0,
    val isInitialLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: MusicFlowFeedError? = null,
    val hasMore: Boolean = true,
    val isOffline: Boolean = false,
)

data class MusicFlowUiState(
    val items: List<MusicFlowItem> = emptyList(),
    val selectedIndex: Int = 0,
    val isInitialLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val error: MusicFlowFeedError? = null,
    val hasMore: Boolean = true,
    val playbackStatus: MusicFlowPlaybackStatus = MusicFlowPlaybackStatus.Idle,
    val canRestorePreviousQueue: Boolean = false,
    val isOfflineFeed: Boolean = false,
) {
    val selectedSongId: String? get() = items.getOrNull(selectedIndex)?.songId
}

/** Signals gathered from the local library and preferences for candidate ranking. */
data class MusicFlowSignals(
    val likedArtistKeys: Set<String> = emptySet(),
    val excludedTrackKeys: Set<String> = emptySet(),
    val hiddenSongIds: Set<String> = emptySet(),
    val recentSongIds: Set<String> = emptySet(),
    val seenSongIds: Set<String> = emptySet(),
    val seenTrackKeys: Set<String> = emptySet(),
    val hideExplicit: Boolean = false,
    val hideVideoSongs: Boolean = false,
    val blockedArtists: Set<String> = emptySet(),
    val dislikedSongs: Set<String> = emptySet(),
)

fun MediaMetadata.toMusicFlowSongItem(): SongItem = SongItem(
    id = id,
    title = title,
    artists = artists.map { Artist(it.name, it.id) },
    album = album?.let { Album(it.title, it.id) },
    duration = duration.takeIf { it > 0 },
    thumbnail = thumbnailUrl.orEmpty(),
    musicVideoType = musicVideoType,
    explicit = explicit,
)
