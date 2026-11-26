package com.revenge.music.generate

import android.content.Context
import com.revenge.core.constants.LastFMUsernameKey
import com.revenge.core.db.MusicDatabase
import com.revenge.core.db.entities.TasteProfileEntity
import com.revenge.core.utils.dataStore
import com.revenge.music.utils.lastfm.LastFmTasteApi
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

data class TasteProfile(
    val topArtistNames: List<String>,
    val topTrackKeys: List<String>,
    val topGenres: List<String>,
    val fetchedAtMillis: Long,
)

@Singleton
open class TasteProfileProvider @Inject constructor(
    private val database: MusicDatabase,
    private val lastFmTasteApi: LastFmTasteApi,
    @ApplicationContext private val context: Context,
) {
    private val mutex = Mutex()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }
    private var memory: TasteProfile? = null

    suspend fun get(forceRefresh: Boolean = false): TasteProfile? = mutex.withLock {
        withContext(Dispatchers.IO) {
            val now = System.currentTimeMillis()
            val cached = memory
            if (!forceRefresh && cached != null &&
                now - cached.fetchedAtMillis < TASTE_PROFILE_TTL_MILLIS
            ) {
                return@withContext cached
            }

            val stored = runCatching { database.tasteProfileDao.get() }.getOrNull()
            val storedProfile = stored?.let { decodeEntity(it) }
            if (!forceRefresh && storedProfile != null &&
                now - storedProfile.fetchedAtMillis < TASTE_PROFILE_TTL_MILLIS
            ) {
                memory = storedProfile
                return@withContext storedProfile
            }

            val username = runCatching {
                context.dataStore.data.first()[LastFMUsernameKey]
            }.getOrNull().orEmpty().trim()
            if (username.isNotBlank()) {
                val fetched = fetch(username)
                if (fetched != null) {
                    memory = fetched
                    runCatching {
                        database.tasteProfileDao.upsert(fetched.toEntity())
                    }
                    return@withContext fetched
                }
            }

            if (storedProfile != null) {
                memory = storedProfile
                return@withContext storedProfile
            }

            val local = localProfile(now)
            if (local != null) memory = local
            local
        }
    }

    suspend fun clear() = mutex.withLock {
        memory = null
        runCatching { database.tasteProfileDao.clear() }
        Unit
    }

    private suspend fun fetch(username: String): TasteProfile? = coroutineScope {
        val artistsDeferred = async { lastFmTasteApi.getUserTopArtists(username, 30) }
        val tracksDeferred = async { lastFmTasteApi.getUserTopTracks(username, 50) }
        val tagsDeferred = async { lastFmTasteApi.getUserTopTags(username, 15) }

        val artists = artistsDeferred.await().getOrNull().orEmpty()
        val tracks = tracksDeferred.await().getOrNull().orEmpty()
        val tags = tagsDeferred.await().getOrNull().orEmpty()

        if (artists.isEmpty() && tracks.isEmpty()) return@coroutineScope null

        TasteProfile(
            topArtistNames = artists.map { it.trim().lowercase() }
                .filter { it.isNotBlank() }.distinct(),
            topTrackKeys = tracks.map { trackKeyOf(it.name, it.artist) }.distinct(),
            topGenres = tags.map { it.name.trim().lowercase() }
                .filter { it.isNotBlank() }.distinct(),
            fetchedAtMillis = System.currentTimeMillis(),
        )
    }

    private suspend fun localProfile(now: Long): TasteProfile? {
        val stats = runCatching {
            database.songPlayStatsDao.mostPlayedSince(now - THIRTY_DAYS_MS, 50)
        }.getOrDefault(emptyList())
        if (stats.isEmpty()) return null
        return TasteProfile(
            topArtistNames = stats.map { it.artist.trim().lowercase() }
                .filter { it.isNotBlank() }.distinct(),
            topTrackKeys = stats.map { it.trackKey }.distinct(),
            topGenres = emptyList(),
            fetchedAtMillis = now,
        )
    }

    private fun decodeEntity(entity: TasteProfileEntity): TasteProfile? {
        val artists = decodeList(entity.topArtistsJson)
        val tracks = decodeList(entity.topTracksJson)
        val genres = decodeList(entity.topGenresJson)
        return TasteProfile(
            topArtistNames = artists.map { it.lowercase() },
            topTrackKeys = tracks,
            topGenres = genres.map { it.lowercase() },
            fetchedAtMillis = entity.updatedAtMillis,
        )
    }

    private fun TasteProfile.toEntity(): TasteProfileEntity = TasteProfileEntity(
        id = 1,
        topArtistsJson = json.encodeToString(ListSerializer(String.serializer()), topArtistNames),
        topTracksJson = json.encodeToString(ListSerializer(String.serializer()), topTrackKeys),
        topGenresJson = json.encodeToString(ListSerializer(String.serializer()), topGenres),
        confidence = 1.0f,
        updatedAtMillis = fetchedAtMillis,
    )

    private fun decodeList(value: String): List<String> {
        if (value.isBlank()) return emptyList()
        return runCatching {
            json.decodeFromString(ListSerializer(String.serializer()), value)
        }.getOrDefault(emptyList())
    }

    companion object {
        const val TASTE_PROFILE_TTL_MILLIS = 3_600_000L
        private const val THIRTY_DAYS_MS = 30L * 24 * 60 * 60 * 1000
    }
}
