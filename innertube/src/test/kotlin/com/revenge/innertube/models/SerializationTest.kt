package com.revenge.innertube.models

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalSerializationApi::class)
class SerializationTest {
  // Matches the transport configuration: missing nullable keys decode as null.
  private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
  }

  @Test
  fun `parses a responsive list item with aliased flex columns`() {
    val payload =
      """
      {
        "flexColumns": [
          {
            "musicResponsiveListItemFlexColumnRenderer": {
              "text": { "runs": [ { "text": "Never Gonna Give You Up" } ] }
            }
          }
        ],
        "fixedColumns": [
          {
            "musicResponsiveListItemFixedColumnRenderer": {
              "text": { "runs": [ { "text": "3:33" } ] }
            }
          }
        ],
        "thumbnail": {
          "musicThumbnailRenderer": {
            "thumbnail": { "thumbnails": [ { "url": "https://img/1", "width": 60, "height": 60 } ] }
          }
        },
        "navigationEndpoint": {
          "watchEndpoint": {
            "videoId": "dQw4w9WgXcQ",
            "watchEndpointMusicSupportedConfigs": {
              "watchEndpointMusicConfig": { "musicVideoType": "MUSIC_VIDEO_TYPE_ATV" }
            }
          }
        }
      }
      """.trimIndent()

    val item = json.decodeFromString<MusicResponsiveListItemRenderer>(payload)
    assertTrue(item.isSong)
    assertEquals("dQw4w9WgXcQ", item.navigationEndpoint?.watchEndpoint?.videoId)
    assertEquals("MUSIC_VIDEO_TYPE_ATV", item.musicVideoType)
    assertEquals(
      "Never Gonna Give You Up",
      item.flexColumns.first().musicResponsiveListItemFlexColumnRenderer.text?.runs?.first()?.text,
    )
    assertEquals("3:33", item.fixedColumns?.first()?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.first()?.text)
  }

  @Test
  fun `thumbnail renderer accepts the cropped square key`() {
    val payload =
      """
      {
        "croppedSquareThumbnailRenderer": {
          "thumbnail": { "thumbnails": [ { "url": "https://img/sq" } ] }
        }
      }
      """.trimIndent()

    val renderer = json.decodeFromString<ThumbnailRenderer>(payload)
    // Either the aliased or the explicit property may carry the key; both are valid mappings.
    val url =
      renderer.musicThumbnailRenderer?.getThumbnailUrl()
        ?: renderer.croppedSquareThumbnailRenderer?.getThumbnailUrl()
    assertEquals("https://img/sq", url)
  }

  @Test
  fun `continuation accepts the radio alias`() {
    val payload = """{ "nextRadioContinuationData": { "continuation": "token123" } }"""
    val continuation = json.decodeFromString<Continuation>(payload)
    assertEquals("token123", listOf(continuation).getContinuation())
  }

  @Test
  fun `section list accepts the immersive carousel alias`() {
    val payload =
      """
      {
        "contents": [
          {
            "musicImmersiveCarouselShelfRenderer": {
              "contents": [],
              "itemSize": "MEDIUM"
            }
          }
        ]
      }
      """.trimIndent()

    val section = json.decodeFromString<SectionListRenderer>(payload)
    assertNotNull(section.contents?.first()?.musicCarouselShelfRenderer)
  }
}
