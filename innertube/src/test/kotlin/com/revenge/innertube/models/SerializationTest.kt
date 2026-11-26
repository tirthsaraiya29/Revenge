package com.revenge.innertube.models

import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SerializationTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Test
    fun aliasedFixedColumnDecodes() {
        val fixture = """
            {
              "flexColumns": [{"musicResponsiveListItemFlexColumnRenderer": {"text": {"runs": [{"text": "Never Gonna Give You Up"}]}}}],
              "fixedColumns": [{"musicResponsiveListItemFixedColumnRenderer": {"text": {"runs": [{"text": "3:33"}]}}}],
              "thumbnail": {"musicThumbnailRenderer": {"thumbnail": {"thumbnails": [{"url": "https://img/1", "width": 60, "height": 60}]}}},
              "navigationEndpoint": {"watchEndpoint": {"videoId": "dQw4w9WgXcQ", "watchEndpointMusicSupportedConfigs": {"watchEndpointMusicConfig": {"musicVideoType": "MUSIC_VIDEO_TYPE_ATV"}}}}
            }
        """.trimIndent()

        val renderer = json.decodeFromString<MusicResponsiveListItemRenderer>(fixture)
        assertTrue(renderer.isSong)
        assertEquals("dQw4w9WgXcQ", renderer.navigationEndpoint?.watchEndpoint?.videoId)
        assertEquals("MUSIC_VIDEO_TYPE_ATV", renderer.musicVideoType)
        assertEquals(
            "Never Gonna Give You Up",
            renderer.flexColumns[0].musicResponsiveListItemFlexColumnRenderer.text?.runs?.first()?.text,
        )
        assertEquals(
            "3:33",
            renderer.fixedColumns?.get(0)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.first()?.text,
        )
    }

    @Test
    fun croppedSquareThumbnailResolvesUrl() {
        val fixture = """
            {"croppedSquareThumbnailRenderer": {"thumbnail": {"thumbnails": [{"url": "https://img/sq", "width": 60, "height": 60}]}}}
        """.trimIndent()

        val renderer = json.decodeFromString<ThumbnailRenderer>(fixture)
        val url = renderer.musicThumbnailRenderer?.getThumbnailUrl()
            ?: renderer.croppedSquareThumbnailRenderer?.getThumbnailUrl()
        assertEquals("https://img/sq", url)
    }

    @Test
    fun nextRadioContinuationAliasDecodes() {
        val continuation = json.decodeFromString<Continuation>(
            """{"nextRadioContinuationData": {"continuation": "token123"}}""",
        )
        assertEquals("token123", listOf(continuation).getContinuation())
    }

    @Test
    fun immersiveCarouselAliasDecodes() {
        val fixture = """
            {"contents": [{"musicImmersiveCarouselShelfRenderer": {"contents": [], "itemSize": "MEDIUM"}}]}
        """.trimIndent()

        val sectionList = json.decodeFromString<SectionListRenderer>(fixture)
        assertNotNull(sectionList.contents?.get(0)?.musicCarouselShelfRenderer)
    }

    @Test
    fun unknownFieldsAreIgnored() {
        val fixture = """
            {"title": {"runs": [{"text": "Shelf"}]}, "someFutureRenderer": {"x": 1}}
        """.trimIndent()

        val shelf = json.decodeFromString<MusicShelfRenderer>(fixture)
        assertEquals("Shelf", shelf.title?.runs?.first()?.text)
        assertNull(shelf.contents)
    }

    @Test
    fun absentNullableFieldsDecodeToNull() {
        val renderer = json.decodeFromString<MusicResponsiveListItemRenderer>(
            """{"flexColumns": []}""",
        )
        assertNull(renderer.thumbnail)
        assertNull(renderer.menu)
        assertNull(renderer.badges)
        assertNull(renderer.playlistItemData)
        assertNull(renderer.overlay)
        assertNull(renderer.navigationEndpoint)
    }

    @Test(expected = MissingFieldException::class)
    fun missingRequiredFieldFails() {
        json.decodeFromString<MusicResponsiveListItemRenderer>("{}")
    }

    @Test(expected = SerializationException::class)
    fun wrongFieldTypeFails() {
        json.decodeFromString<MusicResponsiveListItemRenderer>("""{"flexColumns": {}}""")
    }

    @Test
    fun runsEmptyVersusAbsent() {
        val empty = json.decodeFromString<Runs>("""{"runs": []}""")
        assertNotNull(empty.runs)
        assertTrue(empty.runs!!.isEmpty())

        val absent = json.decodeFromString<Runs>("{}")
        assertNull(absent.runs)
    }

    @Test
    fun navigationEndpointPrecedence() {
        val both = json.decodeFromString<NavigationEndpoint>(
            """{"watchEndpoint": {"videoId": "w"}, "browseEndpoint": {"browseId": "b"}}""",
        )
        assertTrue(both.endpoint is WatchEndpoint)
        assertEquals("w", (both.endpoint as WatchEndpoint).videoId)

        val feedbackOnly = json.decodeFromString<NavigationEndpoint>(
            """{"feedbackEndpoint": {"feedbackToken": "t"}}""",
        )
        assertNull(feedbackOnly.endpoint)
        assertEquals("t", feedbackOnly.feedbackEndpoint?.feedbackToken)
    }

    @Test
    fun browseEndpointFlags() {
        val audiobook = json.decodeFromString<BrowseEndpoint>(
            """{"browseId": "b", "browseEndpointContextSupportedConfigs": {"browseEndpointContextMusicConfig": {"pageType": "MUSIC_PAGE_TYPE_AUDIOBOOK"}}}""",
        )
        assertTrue(audiobook.isAlbumEndpoint)

        val podcast = json.decodeFromString<BrowseEndpoint>("""{"browseId": "MPSP123"}""")
        assertTrue(podcast.isPodcastEndpoint)
    }
}
