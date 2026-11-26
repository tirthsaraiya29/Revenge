package com.revenge.artistvideo

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoMimeTypeTest {
    @Test
    fun m3u8IsHls() {
        assertEquals("application/x-mpegURL", videoMimeType("https://cdn/master.m3u8"))
        assertEquals("application/x-mpegURL", videoMimeType("https://cdn/MASTER.M3U8"))
    }

    @Test
    fun mp4SubstringIsMp4() {
        assertEquals("video/mp4", videoMimeType("https://cdn/clip.mp4?token=abc"))
        assertEquals("video/mp4", videoMimeType("https://cdn/CLIP.MP4"))
    }

    @Test
    fun unknownAndEmptyDefaultToHls() {
        assertEquals("application/x-mpegURL", videoMimeType("https://cdn/stream/playlist"))
        assertEquals("application/x-mpegURL", videoMimeType(""))
    }
}
