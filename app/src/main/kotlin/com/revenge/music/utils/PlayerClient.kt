package com.revenge.music.utils

import com.revenge.innertube.NewPipeExtractor
import com.revenge.innertube.YouTube
import com.revenge.innertube.models.YouTubeClient
import com.revenge.innertube.models.response.PlayerResponse
import kotlin.coroutines.cancellation.CancellationException

/**
 * Per-client player-response retrieval and stream URL resolution. All network work is suspend;
 * NewPipe URL de-obfuscation is blocking and callers run this off the main thread.
 */
object PlayerClient {

    data class ResolvedStream(
        val url: String,
        val format: PlayerResponse.StreamingData.Format,
    )

    suspend fun playerResponse(
        videoId: String,
        client: YouTubeClient,
        signatureTimestamp: Int? = null,
    ): PlayerResponse? = try {
        YouTube.player(
            videoId = videoId,
            client = client,
            signatureTimestamp = signatureTimestamp,
        ).getOrNull()
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        null
    }

    fun audioFormats(response: PlayerResponse?): List<PlayerResponse.StreamingData.Format> =
        response?.streamingData?.adaptiveFormats.orEmpty()
            .filter { it.isAudio && it.isOriginal }

    fun bestAudioFormat(
        formats: List<PlayerResponse.StreamingData.Format>,
    ): PlayerResponse.StreamingData.Format? =
        formats.maxByOrNull { it.bitrate }

    fun resolveUrl(format: PlayerResponse.StreamingData.Format, videoId: String): String? =
        format.url ?: NewPipeExtractor.getStreamUrl(format, videoId)

    /** Uses NewPipe as a last resort, filling the response formats with its resolved URLs. */
    suspend fun newPipeResponse(videoId: String, response: PlayerResponse?): PlayerResponse? {
        if (response == null) return null
        return YouTube.newPipePlayer(videoId, response)
    }

    fun signatureTimestamp(videoId: String): Int? =
        NewPipeExtractor.getSignatureTimestamp(videoId).getOrNull()
}
