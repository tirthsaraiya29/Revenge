package com.revenge.lyrics.lyrics

import android.content.Context
import com.revenge.core.constants.LyricsProviderOrderKey
import com.revenge.core.db.entities.LyricsEntity
import com.revenge.core.models.MediaMetadata
import com.revenge.core.playback.ILyricsHelper
import com.revenge.core.playback.LyricsWithProvider
import com.revenge.core.utils.NetworkConnectivityObserver
import com.revenge.core.utils.dataStore
import com.revenge.core.utils.get
import com.revenge.core.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

class LyricsHelper
@Inject
constructor(
    @ApplicationContext private val context: Context,
    private val networkConnectivity: NetworkConnectivityObserver,
) : ILyricsHelper {

    // Internal seams used by deterministic tests. They do not alter the public surface.
    internal var fetchScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    internal var providerTimeoutMs: Long = 5_000L
    internal var providersOverride: List<LyricsProvider>? = null
    internal var connectivityOverride: (() -> Boolean)? = null
    internal var cache: LruResultCache = LruResultCache(20)

    private val inFlight = ConcurrentHashMap<String, Deferred<LyricsWithProvider>>()
    private val inFlightLock = Any()

    private var alternateJob: Job? = null

    override suspend fun getLyrics(mediaMetadata: MediaMetadata): LyricsWithProvider {
        cancelCurrentLyricsJob()

        val cacheKey = mediaMetadata.id
        cache.get(cacheKey)?.firstOrNull()?.let { cached ->
            return LyricsWithProvider(cached.lyrics, cached.providerName)
        }

        if (!isOnline()) return notFound()

        val deferred = synchronized(inFlightLock) {
            inFlight[cacheKey] ?: fetchScope
                .async(start = CoroutineStart.LAZY) { fetchBest(mediaMetadata) }
                .also { created ->
                    created.invokeOnCompletion { inFlight.remove(cacheKey, created) }
                    inFlight[cacheKey] = created
                    created.start()
                }
        }
        return deferred.await()
    }

    suspend fun getAllLyrics(
        mediaId: String,
        songTitle: String,
        songArtists: String,
        duration: Int,
        album: String?,
        callback: (LyricsResult) -> Unit,
    ) {
        cancelCurrentLyricsJob()

        val cacheKey = "$songArtists-$songTitle".replace(" ", "")
        cache.get(cacheKey)?.let { cached ->
            cached.forEach(callback)
            return
        }
        if (!isOnline()) return

        val providers = enabledProviders()
        if (providers.isEmpty()) return

        val collected = CopyOnWriteArrayList<LyricsResult>()
        val job = fetchScope.launch {
            try {
                coroutineScope {
                    providers.forEach { provider ->
                        launch {
                            try {
                                provider.getAllLyrics(
                                    mediaId,
                                    songTitle,
                                    songArtists,
                                    duration,
                                    album,
                                ) { text ->
                                    val result = LyricsResult(provider.name, text)
                                    collected.add(result)
                                    callback(result)
                                }
                            } catch (cancellation: CancellationException) {
                                throw cancellation
                            } catch (error: Throwable) {
                                reportSafely(error)
                            }
                        }
                    }
                }
                cache.put(cacheKey, collected.toList())
            } catch (cancellation: CancellationException) {
                throw cancellation
            }
        }
        alternateJob = job
        try {
            job.join()
        } finally {
            if (alternateJob === job) alternateJob = null
        }
    }

    fun cancelCurrentLyricsJob() {
        alternateJob?.cancel()
        alternateJob = null
    }

    private suspend fun fetchBest(mediaMetadata: MediaMetadata): LyricsWithProvider {
        val providers = enabledProviders()
        if (providers.isEmpty()) return notFound()

        val args = Args.from(mediaMetadata)
        val priority = providers.map { it.name }
        val outcomes = ConcurrentHashMap<String, ProviderOutcome>()
        val texts = ConcurrentHashMap<String, String>()
        val completion = CompletableDeferred<LyricsWithProvider>()
        val outcomeLock = Any()

        providers.forEach { provider ->
            fetchScope.launch {
                val outcome = fetchOne(provider, args)
                if (outcome.text != null) texts[provider.name] = outcome.text
                synchronized(outcomeLock) {
                    outcomes[provider.name] = outcome.outcome
                    when (val choice = LyricsProviderRegistry.chooseProvider(priority, outcomes)) {
                        is ProviderChoice.Selected -> {
                            texts[choice.name]?.let { text ->
                                completion.complete(LyricsWithProvider(text, choice.name))
                            }
                        }

                        ProviderChoice.None -> completion.complete(notFound())
                        ProviderChoice.Pending -> Unit
                    }
                }
            }
        }

        val result = completion.await()
        val lyricsText = result.lyrics
        if (result.providerName != UNKNOWN && lyricsText != null) {
            cache.put(mediaMetadata.id, listOf(LyricsResult(result.providerName, lyricsText)))
        }
        return result
    }

    private suspend fun fetchOne(provider: LyricsProvider, args: Args): ProviderResult = try {
        val result = withTimeout(providerTimeoutMs) {
            provider.getLyrics(args.id, args.title, args.artist, args.duration, args.album)
        }
        val text = result.getOrNull()
        if (text.isNullOrBlank() || text == LyricsEntity.LYRICS_NOT_FOUND) {
            result.exceptionOrNull()?.let { reportSafely(it) }
            ProviderResult(ProviderOutcome.FAILED, null)
        } else {
            val outcome = if (LyricsUtils.hasSyncedLyrics(text)) {
                ProviderOutcome.SYNCED
            } else {
                ProviderOutcome.UNSYNCED
            }
            ProviderResult(outcome, text)
        }
    } catch (timeout: TimeoutCancellationException) {
        ProviderResult(ProviderOutcome.FAILED, null)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        reportSafely(error)
        ProviderResult(ProviderOutcome.FAILED, null)
    }

    private fun enabledProviders(): List<LyricsProvider> {
        providersOverride?.let { return it }
        return try {
            val savedOrder = context.dataStore[LyricsProviderOrderKey] ?: ""
            LyricsProviderRegistry.orderedProviderNames(savedOrder)
                .mapNotNull(LyricsProviderRegistry::getProviderByName)
                .filter { provider ->
                    try {
                        provider.isEnabled(context)
                    } catch (_: Exception) {
                        true
                    }
                }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun isOnline(): Boolean =
        connectivityOverride?.invoke() ?: try {
            networkConnectivity.isCurrentlyConnected()
        } catch (_: Throwable) {
            true
        }

    private fun reportSafely(error: Throwable) {
        try {
            reportException(error)
        } catch (_: Throwable) {
        }
    }

    private fun notFound(): LyricsWithProvider = LyricsWithProvider(LyricsEntity.LYRICS_NOT_FOUND, UNKNOWN)

    private data class Args(
        val id: String,
        val title: String,
        val artist: String,
        val duration: Int,
        val album: String?,
    ) {
        companion object {
            fun from(metadata: MediaMetadata): Args = Args(
                id = metadata.id,
                title = metadata.title,
                artist = metadata.artists.joinToString(", ") { it.name },
                duration = metadata.duration,
                album = metadata.album?.title,
            )
        }
    }

    private data class ProviderResult(val outcome: ProviderOutcome, val text: String?)

    private companion object {
        const val UNKNOWN = "Unknown"
    }
}

/** A tiny synchronized LRU keyed by request identity. */
internal class LruResultCache(private val capacity: Int) {

    private val entries = object : LinkedHashMap<String, List<LyricsResult>>(capacity, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, List<LyricsResult>>,
        ): Boolean = size > capacity
    }

    @Synchronized
    fun get(key: String): List<LyricsResult>? = entries[key]

    @Synchronized
    fun put(key: String, value: List<LyricsResult>) {
        entries[key] = value
    }

    @Synchronized
    fun clear() {
        entries.clear()
    }
}
