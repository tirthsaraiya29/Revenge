package com.revenge.artistvideo

import org.junit.Assert.assertEquals
import org.junit.Test

class VideoMimeTypeTest {
  private val hls = "application/x-mpegURL"
  private val mp4 = "video/mp4"

  @Test
  fun detectsHlsFromUrl() {
    assertEquals(hls, videoMimeType("https://cdn.example/artist/master.m3u8"))
  }

  @Test
  fun detectionIsCaseInsensitive() {
    assertEquals(hls, videoMimeType("HTTPS://CDN.EXAMPLE/ARTIST/MASTER.M3U8"))
    assertEquals(mp4, videoMimeType("https://cdn.example/ARTIST/CLIP.MP4"))
  }

  @Test
  fun detectsMp4WithQueryString() {
    assertEquals(mp4, videoMimeType("https://cdn.example/clip.mp4?token=abc"))
  }

  @Test
  fun unknownSchemeDefaultsToHls() {
    assertEquals(hls, videoMimeType("https://cdn.example/stream/playlist"))
    assertEquals(hls, videoMimeType(""))
  }
}
