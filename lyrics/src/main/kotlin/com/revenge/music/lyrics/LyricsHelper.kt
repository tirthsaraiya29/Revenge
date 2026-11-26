package com.revenge.music.lyrics

import android.content.Context
import android.util.LruCache
import com.revenge.music.constants.LyricsProviderOrderKey
import com.revenge.music.db.entities.LyricsEntity.Companion.LYRICS_NOT_FOUND
import com.revenge.music.models.MediaMetadata
import com.revenge.music.playback.LyricsWithProvider
import com.revenge.music.utils.NetworkConnectivityObserver
import com.revenge.music.utils.dataStore
import com.revenge.music.utils.get
import com.revenge.music.utils.reportException
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class LyricsHelper
@Inject
constructor(
  @ApplicationContext private val context: Context,
  private val networkConnectivity: NetworkConnectivityObserver,
) {
  private val cache = LruCache<String, List<LyricsResult>>(MAX_CACHE_SIZE)
  private val inFlight = ConcurrentHashMap<String, Deferred<LyricsWithProvider>>()

  // Provider fetches outlive the caller's scope so a song switch cannot fail parallel callers.
  private val fetchScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private var currentLyricsJob: Job? = null

  private suspend fun enabledProviders(): List<LyricsProvider> {
    val order = context.dataStore[LyricsProviderOrderKey].orEmpty()
    return LyricsProviderRegistry.orderedProviderNames(order)
      .mapNotNull(LyricsProviderRegistry::getProviderByName)
      .filter { it.isEnabled(context) }
  }

  private fun isOnline(): Boolean =
    try {
      networkConnectivity.isCurrentlyConnected()
    } catch (e: Exception) {
      true
    }

  suspend fun getLyrics(mediaMetadata: MediaMetadata): LyricsWithProvider {
    currentLyricsJob?.cancel()

    cache.get(mediaMetadata.id)?.firstOrNull()?.let {
      return LyricsWithProvider(it.lyrics, it.providerName)
    }
    if (!isOnline()) return LyricsWithProvider(LYRICS_NOT_FOUND, "Unknown")

    val fresh =
      fetchScope.async(start = CoroutineStart.LAZY) { fetchBestLyrics(mediaMetadata) }
    val existing = inFlight.putIfAbsent(mediaMetadata.id, fresh)
    if (existing != null) {
      fresh.cancel()
      return existing.await()
    }
    fresh.invokeOnCompletion { inFlight.remove(mediaMetadata.id, fresh) }
    fresh.start()
    return fresh.await()
  }

  private suspend fun fetchBestLyrics(mediaMetadata: MediaMetadata): LyricsWithProvider {
    val providers = enabledProviders()
    if (providers.isEmpty()) return LyricsWithProvider(LYRICS_NOT_FOUND, "Unknown")

    return coroutineScope {
      val outcomes = ConcurrentHashMap<String, ProviderOutcome>()
      val lyrics = ConcurrentHashMap<String, String>()
      val finished = Channel<Unit>(providers.size)
      val order = providers.map { it.name }

      providers.forEach { provider ->
        launch {
          val text = fetchFromProvider(provider, mediaMetadata)
          if (text.isNullOrBlank() || text == LYRICS_NOT_FOUND) {
            outcomes[provider.name] = ProviderOutcome.FAILED
          } else {
            lyrics[provider.name] = text
            outcomes[provider.name] =
              if (LyricsUtils.hasSyncedLyrics(text)) ProviderOutcome.SYNCED
              else ProviderOutcome.UNSYNCED
          }
          finished.send(Unit)
        }
      }

      repeat(providers.size) {
        finished.receive()
        when (val choice = LyricsProviderRegistry.chooseProvider(order, outcomes)) {
          is ProviderChoice.Selected -> {
            val text = lyrics[choice.name] ?: return@repeat
            coroutineContext.cancelChildren()
            cache.put(mediaMetadata.id, listOf(LyricsResult(choice.name, text)))
            return@coroutineScope LyricsWithProvider(text, choice.name)
          }
          ProviderChoice.None -> return@coroutineScope LyricsWithProvider(LYRICS_NOT_FOUND, "Unknown")
          ProviderChoice.Pending -> Unit
        }
      }
      LyricsWithProvider(LYRICS_NOT_FOUND, "Unknown")
    }
  }

  private suspend fun fetchFromProvider(
    provider: LyricsProvider,
    mediaMetadata: MediaMetadata,
  ): String? =
    withTimeoutOrNull(PROVIDER_TIMEOUT_MS) {
      try {
        provider
          .getLyrics(
            id = mediaMetadata.id,
            title = mediaMetadata.title,
            artist = mediaMetadata.artists.joinToString { it.name },
            duration = mediaMetadata.duration,
            album = mediaMetadata.album?.title,
          )
          .getOrElse {
            if (it is CancellationException) throw it
            reportException(it)
            null
          }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        reportException(e)
        null
      }
    }

  suspend fun getAllLyrics(
    mediaId: String,
    songTitle: String,
    songArtists: String,
    duration: Int,
    album: String? = null,
    callback: (LyricsResult) -> Unit,
  ) {
    currentLyricsJob?.cancel()

    val cacheKey = "$songArtists-$songTitle".replace(" ", "")
    cache.get(cacheKey)?.let { results ->
      results.forEach(callback)
      return
    }
    if (!isOnline()) return

    val collected = java.util.concurrent.CopyOnWriteArrayList<LyricsResult>()
    val providers = enabledProviders()
    currentLyricsJob =
      CoroutineScope(SupervisorJob()).launch {
        val jobs =
          providers.map { provider ->
            launch {
              try {
                provider.getAllLyrics(mediaId, songTitle, songArtists, duration, album) { text ->
                  val result = LyricsResult(provider.name, text)
                  collected += result
                  callback(result)
                }
              } catch (e: CancellationException) {
                throw e
              } catch (e: Exception) {
                reportException(e)
              }
            }
          }
        jobs.forEach { it.join() }
        cache.put(cacheKey, collected.toList())
      }
    currentLyricsJob?.join()
  }

  fun cancelCurrentLyricsJob() {
    currentLyricsJob?.cancel()
    currentLyricsJob = null
  }

  private companion object {
    const val MAX_CACHE_SIZE = 20
    const val PROVIDER_TIMEOUT_MS = 5_000L
  }
}

data class LyricsResult(
  val providerName: String,
  val lyrics: String,
)
