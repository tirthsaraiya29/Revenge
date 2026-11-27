package com.revenge.music.spotify

import com.revenge.core.db.MusicDatabase
import com.revenge.core.db.entities.Artist
import com.revenge.core.db.entities.ArtistEntity
import com.revenge.core.models.SimilarRecommendation
import com.revenge.innertube.models.SongItem
import com.revenge.music.spotifyimport.SpotifyImportRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first

@Singleton
class LoadSpotifyRecommendationsUseCase
@Inject
constructor(
  private val repository: SpotifyImportRepository,
  private val database: MusicDatabase,
  private val resolver: SpotifyPlaybackResolver,
) {

  suspend operator fun invoke(
    hideExplicit: Boolean,
    fromTimeStamp: Long,
  ): List<SimilarRecommendation>? {
    val session = repository.restoreSession()
    if (!session.isAuthenticated) return null

    val local = collectLocalSeeds(fromTimeStamp)
    val fromHistory = buildFromSeeds(local, hideExplicit)
    if (fromHistory.isNotEmpty()) return fromHistory

    val top = Spotify.topArtists(limit = 3).getOrNull()?.items.orEmpty()
    val synthetic = top.map { spotify ->
      Seed(
        local =
          Artist(
            artist =
              ArtistEntity(
                id = spotify.id,
                name = spotify.name,
                thumbnailUrl = spotify.images.firstOrNull()?.url,
                bookmarkedAt = null,
              ),
            songCount = 0,
          ),
        query = spotify.name,
        spotifyId = spotify.id,
      )
    }
    val fallback = buildFromSeeds(synthetic, hideExplicit)
    return fallback.ifEmpty { null }
  }

  private data class Seed(
    val local: com.revenge.core.db.entities.LocalItem,
    val query: String,
    val spotifyId: String? = null,
  )

  private suspend fun collectLocalSeeds(fromTimeStamp: Long): List<Seed> {
    val artists = runCatching {
      database.mostPlayedArtists(fromTimeStamp, limit = 10).first().shuffled().take(3)
    }
      .getOrDefault(emptyList())
      .map { artist -> Seed(artist, artist.title) }
    val songs = runCatching {
      database
        .mostPlayedSongs(fromTimeStamp, limit = 10)
        .first()
        .filter { it.song.albumName != null }
        .shuffled()
        .take(2)
    }
      .getOrDefault(emptyList())
      .map { song ->
        Seed(song, "${song.title} ${song.artists.joinToString(" ") { it.name }}".trim())
      }
    return artists + songs
  }

  private suspend fun buildFromSeeds(
    seeds: List<Seed>,
    hideExplicit: Boolean,
  ): List<SimilarRecommendation> = coroutineScope {
    seeds
      .map { seed ->
        async {
          try {
            val spotifyId = seed.spotifyId ?: resolveSeedId(seed) ?: return@async null
            val recommendations =
              Spotify.recommendations(seedArtistIds = listOf(spotifyId), limit = 8).getOrNull()
                ?: return@async null
            val items: List<SongItem> =
              resolver.resolveAndFilter(recommendations.tracks, hideExplicit)
            if (items.isEmpty()) null else SimilarRecommendation(title = seed.local, items = items)
          } catch (e: CancellationException) {
            throw e
          } catch (e: Throwable) {
            null
          }
        }
      }
      .awaitAll()
      .filterNotNull()
  }

  private suspend fun resolveSeedId(seed: Seed): String? {
    val track =
      Spotify.search(seed.query, types = listOf("track"), limit = 1)
        .getOrNull()
        ?.tracks
        ?.items
        ?.firstOrNull()
    if (track != null) return track.id
    return Spotify.search(seed.query, types = listOf("artist"), limit = 1)
      .getOrNull()
      ?.artists
      ?.items
      ?.firstOrNull()
      ?.id
  }
}
