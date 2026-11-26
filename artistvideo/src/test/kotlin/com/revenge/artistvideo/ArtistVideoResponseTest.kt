package com.revenge.artistvideo

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtistVideoResponseTest {
    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        isLenient = true
    }

    @Test
    fun av1_decodesAllSixFields() {
        val payload = """
            {"name":"N","artist":"A","albumId":"42","static":"s","animated":"an","videoUrl":"v"}
        """.trimIndent()

        val response = json.decodeFromString(ArtistVideoResponse.serializer(), payload)

        assertEquals("N", response.name)
        assertEquals("A", response.artist)
        assertEquals("42", response.albumId)
        assertEquals("s", response.static)
        assertEquals("an", response.animated)
        assertEquals("v", response.videoUrl)
    }

    @Test
    fun av2_emptyObjectYieldsNulls() {
        val response = json.decodeFromString(ArtistVideoResponse.serializer(), "{}")
        assertNull(response.name)
        assertNull(response.albumId)
        assertNull(response.preferredAnimationUrl)
    }

    @Test
    fun av3_toleratesNullsAndUnknownKeys() {
        val response = json.decodeFromString(
            ArtistVideoResponse.serializer(),
            """{"name":null,"animated":null,"extra":{"nested":[1,2]},"unknown":"x"}""",
        )
        assertNull(response.name)
        assertNull(response.animated)
        assertNull(response.preferredAnimationUrl)
    }

    @Test
    fun av4_prefersAnimated() {
        assertEquals(
            "an",
            ArtistVideoResponse(animated = "an", videoUrl = "v").preferredAnimationUrl,
        )
    }

    @Test
    fun av5_fallsBackToVideoUrl() {
        assertEquals("v", ArtistVideoResponse(videoUrl = "v").preferredAnimationUrl)
    }

    @Test
    fun av6_blankAnimatedStillWins() {
        assertEquals("", ArtistVideoResponse(animated = "", videoUrl = "v").preferredAnimationUrl)
    }

    @Test
    fun av7_mimeClassification() {
        assertEquals("application/x-mpegURL", videoMimeType("https://cdn/master.m3u8"))
        assertEquals("application/x-mpegURL", videoMimeType("https://cdn/MASTER.M3U8"))
        assertEquals("video/mp4", videoMimeType("https://cdn/clip.mp4?token=abc"))
        assertEquals("application/x-mpegURL", videoMimeType("https://cdn/stream/playlist"))
        assertEquals("application/x-mpegURL", videoMimeType(""))
    }
}
