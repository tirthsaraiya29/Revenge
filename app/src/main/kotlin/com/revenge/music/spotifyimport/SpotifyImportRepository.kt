package com.revenge.music.spotifyimport

import android.content.Context
import com.revenge.innertube.YouTube
import com.revenge.innertube.YouTube.SearchFilter
import com.revenge.innertube.models.SongItem
import com.revenge.music.db.MusicDatabase
import com.revenge.music.db.entities.PlaylistEntity
import com.revenge.music.db.entities.PlaylistSongMap
import com.revenge.music.models.toMediaMetadata
import com.revenge.music.spotify.Spotify
import com.revenge.music.spotify.SpotifyAuth
import com.revenge.music.spotify.SpotifyMapper
import com.revenge.music.spotify.SpotifySessionManager
import com.revenge.music.spotify.models.SpotifyPlaylist
import com.revenge.music.spotify.models.SpotifyPlaylistTracksRef
import com.revenge.music.spotify.models.SpotifySession
import com.revenge.music.spotify.models.SpotifyTrack
import com.revenge.music.spotify.models.SpotifyUser
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.roundToInt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

@Singleton
class SpotifyImportRepository
@Inject
constructor(
  @ApplicationContext private val context: Context,
  private val database: MusicDatabase,
  private val sessionManager: SpotifySessionManager,
) {
  private companion object {
    const val PAGE = 50
    const val GRACE_MS = 60_000L
    const val MATCH_CONCURRENCY = 4
    const val ENRICH_CONCURRENCY = 4
    const val NOT_CONNECTED = "Spotify is not connected"
    const val INVALID_LINK = "Invalid playlist link"
  }

  private val mutationMutex = Mutex()

  suspend fun restoreSession(): SpotifyImportSession {
    val session = sessionManager.getSession(context)
      ?: return SpotifyImportSession(isAuthenticated = false)
    val cachedValid =
      !session.accessToken.isNullOrBlank() &&
        session.expiresAt > System.currentTimeMillis() + GRACE_MS
    if (cachedValid) {
      Spotify.accessToken = session.accessToken
      return session.toImportSession()
    }
    val refreshed =
      SpotifyAuth.fetchAccessToken(session.spDc, session.spKey.orEmpty())
    return refreshed.fold(
      onSuccess = { token ->
        Spotify.accessToken = token.accessToken
        sessionManager.saveSession(
          context,
          session.copy(
            accessToken = token.accessToken,
            expiresAt = token.accessTokenExpirationTimestampMs,
          ),
        )
        refreshProfile()
        (sessionManager.getSession(context) ?: session).toImportSession()
      },
      onFailure = { failure ->
        if (failure is Spotify.SpotifyException && failure.statusCode == 401) {
          logout()
          SpotifyImportSession(isAuthenticated = false)
        } else {
          Spotify.accessToken = session.accessToken
          session.toImportSession()
        }
      },
    )
  }

  suspend fun connectWithCookies(spDc: String, spKey: String): SpotifyImportSession {
    val existing = sessionManager.getSession(context)
    sessionManager.saveSession(
      context,
      (existing ?: SpotifySession(spDc = spDc)).copy(spDc = spDc, spKey = spKey),
    )
    val token = SpotifyAuth.fetchAccessToken(spDc, spKey).getOrThrow()
    Spotify.accessToken = token.accessToken
    sessionManager.saveSession(
      context,
      SpotifySession(
        spDc = spDc,
        spKey = spKey,
        accessToken = token.accessToken,
        expiresAt = token.accessTokenExpirationTimestampMs,
      ),
    )
    val profile = refreshProfile()
    return SpotifyImportSession(
      isAuthenticated = true,
      accountName = profile?.displayName ?: profile?.id.orEmpty(),
      accountAvatarUrl = profile?.images?.firstOrNull()?.url,
    )
  }

  suspend fun logout() {
    sessionManager.clearSession(context)
    Spotify.accessToken = null
  }

  suspend fun loadSources(): List<SpotifyImportSource> = withContext(Dispatchers.IO) {
    requireToken()
    refreshProfile()
    val liked =
      withAuthRetry { Spotify.likedSongs(limit = 1, offset = 0) }
        .map { it.total }
        .getOrDefault(0)
    val playlists = mutableListOf<SpotifyPlaylist>()
    var offset = 0
    while (true) {
      val page =
        withAuthRetry { Spotify.myPlaylists(limit = PAGE, offset = offset) }
          .getOrElse { break }
      playlists += page.items
      offset += page.items.size
      if (page.items.isEmpty() || page.items.size < PAGE || offset >= page.total) break
    }
    val enriched = enrichCounts(playlists)
    buildList {
      add(SpotifyImportSource.LikedSongs("Liked Songs", liked))
      enriched.filter { it.id.isNotBlank() }.forEach { add(SpotifyImportSource.Playlist(it)) }
    }
  }

  suspend fun addPlaylistByUrl(url: String): SpotifyImportSource.Playlist =
    withContext(Dispatchers.IO) {
      requireToken()
      val id = parsePlaylistId(url) ?: throw IllegalArgumentException(INVALID_LINK)
      val playlist =
        withAuthRetry { Spotify.playlist(id) }.getOrElse {
          throw IllegalArgumentException(INVALID_LINK)
        }
      val withCount =
        if (playlist.tracks?.total == null) {
          val total =
            withAuthRetry { Spotify.playlistTracks(id, limit = 1, offset = 0) }
              .getOrNull()
              ?.total
          playlist.copy(
            tracks = playlist.tracks?.copy(total = total)
              ?: SpotifyPlaylistTracksRef(total = total),
          )
        } else {
          playlist
        }
      SpotifyImportSource.Playlist(withCount)
    }

  suspend fun importSources(
    sources: List<SpotifyImportSource>,
    onProgress: (SpotifyImportProgressUi) -> Unit,
  ): SpotifyImportSummaryUi =
    withContext(Dispatchers.IO) {
      requireToken()
      val summaries = mutableListOf<SpotifyImportSourceSummaryUi>()
      val totalSources = sources.size
      for ((index, source) in sources.withIndex()) {
        onProgress(progress(source.title, index, totalSources, 0, 0))
        val tracks = fetchTracks(source)
        val matched =
          matchInOrder(tracks) { matchedInSource ->
            onProgress(progress(source.title, index, totalSources, matchedInSource, tracks.size))
          }
        mutationMutex.withLock {
          mirror(
            localPlaylistId = source.localPlaylistId,
            title = source.title,
            thumbnailUrl = source.thumbnailUrl,
            songs = matched,
          )
        }
        summaries +=
          SpotifyImportSourceSummaryUi(
            title = source.title,
            totalTracks = tracks.size,
            importedTracks = matched.size,
            failedTracks = tracks.size - matched.size,
          )
        onProgress(progress(source.title, index + 1, totalSources, 0, 0))
      }
      SpotifyImportSummaryUi(summaries)
    }

  suspend fun syncPlaylistFast(localPlaylistId: String) = withContext(Dispatchers.IO) {
    val source =
      when {
        localPlaylistId == "SPOTIFY_LIKED_SONGS" ->
          SpotifyImportSource.LikedSongs("Liked Songs", 0)
        localPlaylistId.startsWith("SPOTIFY_PLAYLIST_") -> {
          val id = localPlaylistId.removePrefix("SPOTIFY_PLAYLIST_")
          if (id.isBlank()) return@withContext
          val playlist = withAuthRetry { Spotify.playlist(id) }.getOrNull()
            ?: return@withContext
          SpotifyImportSource.Playlist(playlist)
        }
        else -> return@withContext
      }
    val tracks = fetchTracks(source)
    val existing = loadLocalSongs(localPlaylistId)
    val matched = mutableListOf<SongItem>()
    for (track in tracks) {
      val reusable =
        existing.firstOrNull { song ->
          SpotifyMapper.matchScore(
            spotifyTitle = track.name,
            spotifyArtist = track.artists.firstOrNull()?.name.orEmpty(),
            spotifyDurationMs = track.durationMs,
            candidateTitle = song.title,
            candidateArtist = song.artists.joinToString(", ") { it.name },
            candidateDurationSec = song.duration,
          ) > 80.0
        }
      val resolved = reusable ?: matchSearch(track)
      if (resolved != null) matched += resolved
    }
    mutationMutex.withLock {
      mirror(localPlaylistId, source.title, source.thumbnailUrl, matched)
    }
  }

  // ------------------------------------------------------------------ implementation

  internal fun parsePlaylistId(input: String): String? {
    val value = input.trim()
    if (value.isBlank()) return null
    Regex("^[A-Za-z0-9]{16,}$").matchEntire(value)?.let { return value }
    if (value.startsWith("spotify:playlist:")) {
      val id = value.removePrefix("spotify:playlist:").substringBefore("?")
      return id.takeIf { it.matches(Regex("[A-Za-z0-9]{16,}")) }
    }
    val uri = runCatching { java.net.URI(value) }.getOrNull() ?: return null
    if (uri.scheme != "https" && uri.scheme != "http") return null
    if (uri.host != "open.spotify.com") return null
    val segments = uri.path.orEmpty().trim('/').split('/')
    val playlistIndex = segments.indexOf("playlist")
    if (playlistIndex < 0 || playlistIndex == segments.lastIndex) return null
    // Reject track URLs and any deeper resource.
    if (playlistIndex != 0 && segments[0].startsWith("intl-").not()) return null
    val id = segments[playlistIndex + 1].substringBefore("?")
    return id.takeIf { it.matches(Regex("[A-Za-z0-9]{16,}")) }
  }

  private suspend fun requireToken(): String {
    refreshTokenIfNeeded()
    return Spotify.accessToken ?: throw IllegalStateException(NOT_CONNECTED)
  }

  private suspend fun refreshTokenIfNeeded() {
    if (Spotify.accessToken != null) return
    val session = sessionManager.getSession(context) ?: return
    if (session.expiresAt > System.currentTimeMillis() + GRACE_MS &&
      !session.accessToken.isNullOrBlank()
    ) {
      Spotify.accessToken = session.accessToken
      return
    }
    val token = SpotifyAuth.fetchAccessToken(session.spDc, session.spKey.orEmpty()).getOrNull()
      ?: return
    Spotify.accessToken = token.accessToken
    sessionManager.saveSession(
      context,
      session.copy(accessToken = token.accessToken, expiresAt = token.accessTokenExpirationTimestampMs),
    )
  }

  private suspend fun refreshProfile(): SpotifyUser? =
    Spotify.me().getOrNull()?.also { user ->
      val session = sessionManager.getSession(context) ?: return@also
      sessionManager.saveSession(
        context,
        session.copy(
          accountName = user.displayName ?: user.id,
          accountAvatarUrl = user.images.firstOrNull()?.url,
        ),
      )
    }

  private suspend fun enrichCounts(playlists: List<SpotifyPlaylist>): List<SpotifyPlaylist> {
    val semaphore = Semaphore(ENRICH_CONCURRENCY)
    return coroutineScope {
      playlists
        .map { playlist ->
          async {
            if (playlist.tracks?.total != null) return@async playlist
            semaphore.withPermit {
              val total =
                withAuthRetry { Spotify.playlistTracks(playlist.id, limit = 1, offset = 0) }
                  .getOrNull()
                  ?.total
              total?.let { playlist.copy(tracks = SpotifyPlaylistTracksRef(total = it)) }
                ?: playlist
            }
          }
        }
        .awaitAll()
    }
  }

  private suspend fun fetchTracks(source: SpotifyImportSource): List<SpotifyTrack> {
    val tracks = mutableListOf<SpotifyTrack>()
    var offset = 0
    while (true) {
      val page =
        when (source) {
          is SpotifyImportSource.LikedSongs ->
            withAuthRetry { Spotify.likedSongs(limit = PAGE, offset = offset) }
              .getOrElse { break }
              .let { paging -> paging.items.mapNotNull { it.track } to paging.total }
          is SpotifyImportSource.Playlist ->
            withAuthRetry { Spotify.playlistTracks(source.playlist.id, PAGE, offset) }
              .getOrElse { break }
              .let { paging -> paging.items.mapNotNull { it.track } to paging.total }
        }
      val (items, total) = page
      if (items.isEmpty()) break
      tracks += items.filter { it.name.isNotBlank() }
      offset += items.size
      if (items.size < PAGE || offset >= total) break
    }
    return tracks
  }

  private suspend fun matchInOrder(
    tracks: List<SpotifyTrack>,
    onMatched: (Int) -> Unit,
  ): List<SongItem> = coroutineScope {
    val semaphore = Semaphore(MATCH_CONCURRENCY)
    val counter = java.util.concurrent.atomic.AtomicInteger(0)
    tracks
      .map { track ->
        async {
          semaphore.withPermit {
            val result = matchSearch(track)
            if (result != null) onMatched(counter.incrementAndGet())
            result
          }
        }
      }
      .awaitAll()
      .filterNotNull()
  }

  private suspend fun matchSearch(track: SpotifyTrack): SongItem? {
    val query = SpotifyMapper.buildSearchQuery(track)
    val results = YouTube.search(query, SearchFilter.FILTER_SONG).getOrNull() ?: return null
    val candidates = results.items.filterIsInstance<SongItem>()
    if (candidates.isEmpty()) return null
    return candidates.maxByOrNull { candidate ->
      SpotifyMapper.matchScore(
        spotifyTitle = track.name,
        spotifyArtist = track.artists.firstOrNull()?.name.orEmpty(),
        spotifyDurationMs = track.durationMs,
        candidateTitle = candidate.title,
        candidateArtist = candidate.artists.joinToString(", ") { it.name },
        candidateDurationSec = candidate.duration,
      )
    }
  }

  private suspend fun mirror(
    localPlaylistId: String,
    title: String,
    thumbnailUrl: String?,
    songs: List<SongItem>,
  ) {
    database.withTransaction {
      val existing = getPlaylistById(localPlaylistId)
      val timestamp = LocalDateTime.now()
      insert(
        PlaylistEntity(
          id = localPlaylistId,
          name = title,
          thumbnailUrl = thumbnailUrl,
          isEditable = true,
          bookmarkedAt = existing?.playlist?.bookmarkedAt ?: timestamp,
          createdAt = existing?.playlist?.createdAt ?: timestamp,
          lastUpdateTime = timestamp,
        ),
      )
      update(
        PlaylistEntity(
          id = localPlaylistId,
          name = title,
          thumbnailUrl = thumbnailUrl,
          isEditable = true,
          bookmarkedAt = existing?.playlist?.bookmarkedAt ?: timestamp,
          createdAt = existing?.playlist?.createdAt ?: timestamp,
          lastUpdateTime = timestamp,
        ),
      )
      clearPlaylist(localPlaylistId)
      songs.forEachIndexed { position, song ->
        insert(song.toMediaMetadata())
        insert(
          PlaylistSongMap(
            playlistId = localPlaylistId,
            songId = song.id,
            position = position,
            setVideoId = song.setVideoId,
          ),
        )
      }
    }
  }

  private suspend fun loadLocalSongs(localPlaylistId: String): List<SongItem> = emptyList()

  private fun progress(
    title: String,
    completedSources: Int,
    totalSources: Int,
    matched: Int,
    sourceTotal: Int,
  ): SpotifyImportProgressUi {
    val fraction =
      if (sourceTotal <= 0) {
        if (totalSources == 0) 0.0 else completedSources.toDouble() / totalSources
      } else {
        (completedSources + matched.toDouble() / sourceTotal) / totalSources.coerceAtLeast(1)
      }
    return SpotifyImportProgressUi(
      sourceTitle = title,
      completedSources = completedSources,
      totalSources = totalSources,
      matchedTracks = matched,
      totalTracks = sourceTotal,
      percent = (fraction * 100).roundToInt().coerceIn(0, 100),
    )
  }

  private suspend fun <T> withAuthRetry(block: suspend () -> Result<T>): Result<T> {
    val first = block()
    val failure = first.exceptionOrNull()
    if (failure !is Spotify.SpotifyException || failure.statusCode != 401) return first
    val session = sessionManager.getSession(context) ?: return first
    val refreshed =
      SpotifyAuth.fetchAccessToken(session.spDc, session.spKey.orEmpty()).getOrNull()
        ?: return first
    Spotify.accessToken = refreshed.accessToken
    sessionManager.saveSession(
      context,
      session.copy(
        accessToken = refreshed.accessToken,
        expiresAt = refreshed.accessTokenExpirationTimestampMs,
      ),
    )
    return block()
  }

  private fun SpotifySession.toImportSession() =
    SpotifyImportSession(
      isAuthenticated = true,
      accountName = accountName.orEmpty(),
      accountAvatarUrl = accountAvatarUrl,
    )
}
