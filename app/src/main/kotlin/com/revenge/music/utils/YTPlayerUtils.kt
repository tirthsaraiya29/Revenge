package com.revenge.music.utils

import android.content.Context
import com.revenge.core.constants.PlaybackEngine
import com.revenge.core.playback.IYTPlayerUtils
import com.revenge.innertube.models.YouTubeClient
import com.revenge.innertube.models.response.PlayerConfig
import com.revenge.innertube.models.response.PlayerResponse
import timber.log.Timber

/**
 * Playback stream resolution. Each engine describes which InnerTube clients/transports to try;
 * the first client that yields a playable audio URL wins.
 */
object YTPlayerUtils : IYTPlayerUtils {

    @Volatile
    var playbackEngine: PlaybackEngine = PlaybackEngine.AUTO

    private var initialized: Boolean = false

    fun initialize() {
        initialized = true
    }

    data class PlaybackData(
        val url: String,
        val mimeType: String = "audio/mp4",
        val format: String = mimeType,
        val codec: String? = null,
        val bitrate: Int? = null,
        val sampleRate: Int? = null,
        val channels: Int? = null,
        val quality: String = "",
        val qualityLabel: String = quality,
        val loudnessDb: Double? = null,
        val expiresAt: Long = 0L,
        val itag: Int? = null,
        val isMuxed: Boolean = false,
        val audioUrl: String? = null,
        val audioConfig: PlayerConfig.AudioConfig? = null,
    )

    override suspend fun getStreamInfo(videoId: String, context: Context): PlaybackData? =
        resolve(videoId)

    suspend fun getPlaybackData(videoId: String): PlaybackData? = resolve(videoId)

    suspend fun getStreamUrl(videoId: String): String? = resolve(videoId)?.url

    override suspend fun getAudioConfig(videoId: String): PlayerConfig.AudioConfig? {
        for (client in clients().take(2)) {
            val response = PlayerClient.playerResponse(
                videoId = videoId,
                client = client,
                signatureTimestamp = PlayerClient.signatureTimestamp(videoId),
            ) ?: continue
            response.playerConfig?.audioConfig?.let { return it }
        }
        return null
    }

    private suspend fun resolve(videoId: String): PlaybackData? {
        if (videoId.isBlank()) return null
        val clients = clients()
        var refreshed = false

        for (client in clients) {
            var response = PlayerClient.playerResponse(
                videoId = videoId,
                client = client,
                signatureTimestamp = PlayerClient.signatureTimestamp(videoId),
            )
            if (response == null) continue

            if (response.playabilityStatus.status != STATUS_OK &&
                Fix403.isBotDetectionMessage(response.playabilityStatus.reason) &&
                !refreshed
            ) {
                refreshed = Fix403.refreshVisitorData()
                response = PlayerClient.playerResponse(
                    videoId = videoId,
                    client = client,
                    signatureTimestamp = PlayerClient.signatureTimestamp(videoId),
                ) ?: continue
            }
            if (response.playabilityStatus.status != STATUS_OK) continue

            bestStream(response, videoId)?.let { return it }
        }

        // Last resort: ask NewPipe to fill the URLs of a response from a permissive client.
        val permissive = PlayerClient.playerResponse(
            videoId = videoId,
            client = YouTubeClient.ANDROID_VR_NO_AUTH,
        )
        val filled = PlayerClient.newPipeResponse(videoId, permissive) ?: return null
        return bestStream(filled, videoId)
    }

    private fun bestStream(response: PlayerResponse, videoId: String): PlaybackData? {
        val streaming = response.streamingData ?: return null
        val originals = streaming.adaptiveFormats.filter { it.isAudio && it.isOriginal }
        val candidates = originals.ifEmpty { streaming.adaptiveFormats.filter { it.isAudio } }
        val format = PlayerClient.bestAudioFormat(candidates) ?: return null
        val url = PlayerClient.resolveUrl(format, videoId) ?: return null
        return format.toPlaybackData(url, streaming.expiresInSeconds)
    }

    private fun PlayerResponse.StreamingData.Format.toPlaybackData(
        url: String,
        expiresInSeconds: Int,
    ): PlaybackData {
        val separator = mimeType.indexOf(';')
        val mime = if (separator >= 0) mimeType.substring(0, separator).trim() else mimeType
        val codec = Regex("codecs?=([^;]+)").find(mimeType)
            ?.groupValues?.getOrNull(1)?.trim()?.trim('"')
        val qualityText = audioQuality ?: quality
        return PlaybackData(
            url = url,
            mimeType = mime,
            format = mimeType,
            codec = codec,
            bitrate = bitrate,
            sampleRate = audioSampleRate,
            channels = audioChannels,
            quality = qualityText.orEmpty(),
            qualityLabel = qualityText.orEmpty(),
            loudnessDb = loudnessDb,
            expiresAt = System.currentTimeMillis() + expiresInSeconds * 1000L,
            itag = itag,
        )
    }

    private fun clients(): List<YouTubeClient> = when (playbackEngine) {
        PlaybackEngine.POTOKEN -> listOf(
            YouTubeClient.WEB_REMIX,
            YouTubeClient.TVHTML5,
            YouTubeClient.ANDROID_VR_1_65_10,
        )
        PlaybackEngine.BRAVEPIPE -> listOf(
            YouTubeClient.ANDROID_VR_1_65_10,
            YouTubeClient.ANDROID_VR_NO_AUTH,
            YouTubeClient.WEB_REMIX,
        )
        PlaybackEngine.AUTO -> listOf(
            YouTubeClient.WEB_REMIX,
            YouTubeClient.ANDROID_VR_1_65_10,
            YouTubeClient.TVHTML5,
            YouTubeClient.ANDROID_VR_NO_AUTH,
        )
    }

    private const val STATUS_OK = "OK"
}
