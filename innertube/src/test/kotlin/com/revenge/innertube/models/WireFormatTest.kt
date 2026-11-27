package com.revenge.innertube.models

import com.revenge.innertube.models.response.GetTranscriptResponse
import kotlinx.serialization.MissingFieldException
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WireFormatTest {
  private val json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
  }

  @Test
  fun continuationAcceptsBothWireNames() {
    val primary =
      json.decodeFromString<Continuation>(
        """{"nextContinuationData": {"continuation": "a"}}""",
      )
    val radio =
      json.decodeFromString<Continuation>(
        """{"nextRadioContinuationData": {"continuation": "b"}}""",
      )
    assertEquals("a", listOf(primary).getContinuation())
    assertEquals("b", listOf(radio).getContinuation())
  }

  @Test
  fun thumbnailWithBothKeysDoesNotThrow() {
    val fixture =
      """
      {
        "musicThumbnailRenderer": {"thumbnail": {"thumbnails": [{"url": "m", "width": 1, "height": 1}]}},
        "croppedSquareThumbnailRenderer": {"thumbnail": {"thumbnails": [{"url": "sq", "width": 1, "height": 1}]}}
      }
      """
        .trimIndent()

    val renderer = json.decodeFromString<ThumbnailRenderer>(fixture)
    val url =
      renderer.musicThumbnailRenderer?.getThumbnailUrl()
        ?: renderer.croppedSquareThumbnailRenderer?.getThumbnailUrl()
    assertTrue(url == "m" || url == "sq")
  }

  @Test(expected = MissingFieldException::class)
  fun playlistPanelVideoMissingSelectedFails() {
    json.decodeFromString<PlaylistPanelVideoRenderer>(
      """{"thumbnail": {"thumbnails": []}, "navigationEndpoint": {}}""",
    )
  }

  @Test
  fun transcriptActionsMayBeNull() {
    val response = json.decodeFromString<GetTranscriptResponse>("""{"actions": null}""")
    assertNull(response.actions)
  }

  @Test
  fun transcriptOffsetDecodesAsLong() {
    val fixture =
      """
      {
        "actions": [{
          "updateEngagementPanelAction": {
            "content": {
              "transcriptRenderer": {
                "body": {
                  "transcriptBodyRenderer": {
                    "cueGroups": [{
                      "transcriptCueGroupRenderer": {
                        "cues": [{
                          "transcriptCueRenderer": {
                            "cue": {"simpleText": "hello"},
                            "startOffsetMs": 1000,
                            "durationMs": 2000
                          }
                        }]
                      }
                    }]
                  }
                }
              }
            }
          }
        }]
      }
      """
        .trimIndent()

    val response = json.decodeFromString<GetTranscriptResponse>(fixture)
    val cue =
      response.actions
        ?.firstOrNull()
        ?.updateEngagementPanelAction
        ?.content
        ?.transcriptRenderer
        ?.body
        ?.transcriptBodyRenderer
        ?.cueGroups
        ?.firstOrNull()
        ?.transcriptCueGroupRenderer
        ?.cues
        ?.firstOrNull()
        ?.transcriptCueRenderer
    assertEquals(1000L, cue?.startOffsetMs)
    assertEquals("hello", cue?.cue?.simpleText)
  }

  @Test(expected = MissingFieldException::class)
  fun transcriptCueWithoutCueTextFails() {
    json.decodeFromString<GetTranscriptResponse>(
      """{"actions": [{"updateEngagementPanelAction": {"content": {"transcriptRenderer": {"body": {"transcriptBodyRenderer": {"cueGroups": [{"transcriptCueGroupRenderer": {"cues": [{"transcriptCueRenderer": {"startOffsetMs": 1, "durationMs": 2}}]}}]}}}}}}}]}""",
    )
  }

  @Test
  fun nestedUnknownKeysAreIgnored() {
    val fixture =
      """
      {
        "title": {"runs": [{"text": "T", "futureRun": 1}], "futureRunsField": true},
        "contents": [{"musicResponsiveListItemRenderer": {"flexColumns": [], "futureRenderer": {}}}],
        "futureShelfField": {"deep": {"value": 1}}
      }
      """
        .trimIndent()

    val shelf = json.decodeFromString<MusicShelfRenderer>(fixture)
    assertEquals("T", shelf.title?.runs?.first()?.text)
    assertEquals(1, shelf.contents?.size)
  }
}
