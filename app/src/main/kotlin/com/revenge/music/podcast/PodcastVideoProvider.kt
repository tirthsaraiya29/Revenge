package com.revenge.music.podcast

import com.revenge.innertube.YouTube
import com.revenge.innertube.models.NewPipeExtractor
import com.revenge.innertube.models.NewPipeVideoTrack
import com.revenge.innertube.models.SongItem
import com.revenge.innertube.models.YouTubeClient
import com.revenge.innertube.models.response.PlayerResponse
import java.util.Locale

/**
 * Resolves a playable video for a podcast episode, preferring official music videos and falling
 * back to raw streams.
 */
object PodcastVideoProvider {
    private const val ATV = "MUSIC_VIDEO_TYPE_ATV"
    private const val OMV = "MUSIC_VIDEO_TYPE_OMV"

    private val bracketGroups = Regex("\\([^)]*\\)|\\[[^\\]]*]|\\{[^}]*}")
    private val featureMarkers = Regex(
        "\\b(feat\\.?|ft\\.?|featuring|with)\\b[^-]*",
        RegexOption.IGNORE_CASE,
    )
    private val noiseMarkers = Regex(
        "\\b(official\\s*(music\\s*)?video|official\\s*lyrics?|lyrics?|audio|visuali[sz]er|" +
            "live|remaster(ed)?|version|edit|mix|remix)\\b",
        RegexOption.IGNORE_CASE,
    )
    private val trailingSuffix = Regex(
        "\\s*[-–—]\\s*(official\\s*)?(music\\s*)?video\\s*$",
        RegexOption.IGNORE_CASE,
    )
    private val officialTitle = Regex(
        "\\b(official|video|mv|lyrics?|audio)\\b",
        RegexOption.IGNORE_CASE,
    )

    suspend fun findOfficialMusicVideoId(title: String, artist: String): String? {
        if (title.isBlank()) return null
        val cleanTitle = cleanTitle(title)
        val cleanArtist = cleanArtist(artist)
        val queries = buildList {
            if (cleanArtist.isNotBlank()) add("$cleanTitle $cleanArtist")
            else add(cleanTitle)
            if (cleanArtist.isNotBlank()) add("$cleanTitle $cleanArtist official music video")
            else add("$cleanTitle official music video")
        }
        return runCatching {
            val results = ArrayList<SongItem>()
            for (query in queries) {
                val page = YouTube.search(query, YouTube.SearchFilter.FILTER_VIDEO).getOrNull()
                page?.items?.filterIsInstance<SongItem>()?.let { results += it }
            }
            if (results.isEmpty()) return@runCatching null
            val wanted = cleanTitle.lowercase(Locale.ROOT)
            results.firstOrNull { song ->
                song.musicVideoType == OMV && titleMatches(song.title, wanted)
            } ?: results.firstOrNull { it.musicVideoType == OMV }
                ?: results.firstOrNull { song ->
                    officialTitle.containsMatchIn(song.title) && song.musicVideoType != ATV
                }
                ?: results.firstOrNull { it.musicVideoType != ATV }
        }.getOrNull()
    }

    suspend fun resolveVideoTracks(videoId: String): List<NewPipeVideoTrack> {
        if (videoId.isBlank()) return emptyList()
        return runCatching {
            val clients = listOf(
                YouTubeClient.VISIONOS,
                YouTubeClient.TVHTML5,
                YouTubeClient.ANDROID_VR_1_65_10,
            )
            for (client in clients) {
                val response = YouTube.player(videoId, client = client).getOrNull() ?: continue
                val tracks = tracksFrom(response, videoId)
                if (tracks.isNotEmpty()) return@runCatching tracks
            }
            NewPipeExtractor.getVideoTracks(videoId)
        }.getOrDefault(emptyList())
    }

    private fun tracksFrom(
        response: PlayerResponse,
        videoId: String,
    ): List<NewPipeVideoTrack> {
        val streaming = response.streamingData ?: return emptyList()
        val audio = streaming.adaptiveFormats
            .filter { it.isAudio }
            .mapNotNull { format ->
                val url = streamUrl(format, videoId) ?: return@mapNotNull null
                format.bitrate to url
            }
            .maxByOrNull { it.first }
            ?.second

        val byHeight = LinkedHashMap<Int, MutableList<Candidate>>()
        streaming.formats.orEmpty()
            .filter { it.height != null && it.height >= 144 }
            .forEach { format ->
                val url = streamUrl(format, videoId) ?: return@forEach
                val height = format.height ?: return@forEach
                byHeight.getOrPut(height) { ArrayList() } +=
                    Candidate(url, null, isMuxed = true, mime = format.mimeType)
            }
        if (audio != null) {
            streaming.adaptiveFormats
                .filter { !it.isAudio && it.height != null && it.height >= 144 }
                .forEach { format ->
                    val url = streamUrl(format, videoId) ?: return@forEach
                    val height = format.height ?: return@forEach
                    byHeight.getOrPut(height) { ArrayList() } +=
                        Candidate(url, audio, isMuxed = false, mime = format.mimeType)
                }
        }

        return byHeight.entries
            .sortedByDescending { it.key }
            .map { (height, candidates) ->
                val chosen = candidates.firstOrNull {
                    it.isMuxed && it.mime.contains("mp4", ignoreCase = true)
                } ?: candidates.firstOrNull { it.mime.contains("mp4", ignoreCase = true) }
                    ?: candidates.first()
                NewPipeVideoTrack(
                    label = "${height}p",
                    height = height,
                    videoUrl = chosen.videoUrl,
                    audioUrl = chosen.audioUrl,
                    isMuxed = chosen.isMuxed,
                )
            }
    }

    private data class Candidate(
        val videoUrl: String,
        val audioUrl: String?,
        val isMuxed: Boolean,
        val mime: String,
    )

    private fun streamUrl(format: PlayerResponse.StreamingData.Format, videoId: String): String? =
        format.url ?: NewPipeExtractor.getStreamUrl(format, videoId)

    private fun titleMatches(candidate: String, expected: String): Boolean {
        val normalized = candidate.lowercase(Locale.ROOT)
        return normalized.contains(expected) || expected.contains(normalized)
    }

    internal fun cleanTitle(raw: String): String {
        var text = raw
        text = bracketGroups.replace(text, " ")
        text = featureMarkers.replace(text, " ")
        text = noiseMarkers.replace(text, " ")
        text = trailingSuffix.replace(text, " ")
        return text.replace(Regex("\\s+"), " ").trim()
    }

    internal fun cleanArtist(raw: String): String {
        val first = raw.split(
            Regex(
                "[,&×]|\\bx\\b|\\bfeat\\b|\\bft\\b|\\bfeaturing\\b|\\bwith\\b",
                RegexOption.IGNORE_CASE,
            ),
        )
            .firstOrNull()
            .orEmpty()
        return first.replace(Regex("\\s+"), " ").trim()
    }
}
