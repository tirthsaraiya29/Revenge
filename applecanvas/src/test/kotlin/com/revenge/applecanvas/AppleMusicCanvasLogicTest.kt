package com.revenge.applecanvas

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.addJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppleMusicCanvasLogicTest {

  private fun attributes(
    name: String,
    artist: String,
    collection: String? = null,
  ): JsonObject =
    buildJsonObject {
      put("name", name)
      put("artistName", artist)
      if (collection != null) put("collectionName", collection)
    }

  private fun albumResponse(
    name: String,
    artist: String,
    editorial: JsonObject?,
  ): JsonObject =
    buildJsonObject {
      put(
        "data",
        buildJsonArray {
          addJsonObject {
            put("id", "1441164426")
            put(
              "attributes",
              buildJsonObject {
                put("name", name)
                put("artistName", artist)
                if (editorial != null) put("editorialVideo", editorial)
              },
            )
          }
        },
      )
    }

  // --- query building ---

  @Test
  fun `query keeps term when it already contains the artist`() {
    assertEquals("The Beatles Abbey Road", buildQuery("The Beatles Abbey Road", "The Beatles", null))
  }

  @Test
  fun `query prepends artist and appends album once`() {
    assertEquals(
      "The Beatles Come Together Abbey Road",
      buildQuery("Come Together", "The Beatles", "Abbey Road"),
    )
  }

  @Test
  fun `query does not duplicate album already present`() {
    assertEquals("Adele 25", buildQuery("25", "Adele", "25"))
  }

  // --- scoring ---

  @Test
  fun `exact album result scores above threshold`() {
    val score = scoreCandidate(attributes("Abbey Road", "The Beatles"), "Abbey Road", "The Beatles", null)
    val exactScore = score!!
    assertEquals(25, exactScore)
    assertTrue(exactScore >= 12)
  }

  @Test
  fun `exact song result on matching album scores highest`() {
    val score =
      scoreCandidate(
        attributes("Come Together", "The Beatles", "Abbey Road"),
        "Come Together",
        "The Beatles",
        "Abbey Road",
      )
    assertEquals(45, score!!)
  }

  @Test
  fun `wrong artist is rejected`() {
    assertNull(scoreCandidate(attributes("Abbey Road", "Someone Else"), "Abbey Road", "The Beatles", null))
  }

  @Test
  fun `playlist and compilation results are rejected`() {
    assertNull(scoreCandidate(attributes("Today's Hits", "Various Artists"), "Today's Hits", "Various Artists", null))
    assertNull(
      scoreCandidate(
        attributes("Abbey Road", "The Beatles", "The Beatles Essentials"),
        "Abbey Road",
        "The Beatles",
        "Abbey Road",
      ),
    )
  }

  @Test
  fun `unexpected deluxe marker is penalized`() {
    val plain = scoreCandidate(attributes("Abbey Road", "The Beatles"), "Abbey Road", "The Beatles", null)
    val deluxe =
      scoreCandidate(attributes("Abbey Road (Deluxe)", "The Beatles"), "Abbey Road", "The Beatles", null)
    assertTrue(deluxe!! < plain!!)
  }

  // --- album id resolution ---

  @Test
  fun `song resolves album from relationship`() {
    val item =
      buildJsonObject {
        put("type", "songs")
        put(
          "relationships",
          buildJsonObject {
            put(
              "albums",
              buildJsonObject {
                put(
                  "data",
                  buildJsonArray {
                    addJsonObject { put("id", "1441164426") }
                  },
                )
              },
            )
          },
        )
      }
    assertEquals("1441164426", resolveAlbumId(item, buildJsonObject { put("name", "Come Together") }))
  }

  @Test
  fun `song falls back to collectionId then url`() {
    val item = buildJsonObject { put("type", "songs") }
    assertEquals(
      "1441164426",
      resolveAlbumId(item, buildJsonObject { put("collectionId", "1441164426") }),
    )
    assertEquals(
      "1441164426",
      resolveAlbumId(
        item,
        buildJsonObject {
          put("url", "https://music.apple.com/us/album/abbey-road/1441164426?i=1441164495")
        },
      ),
    )
  }

  @Test
  fun `playlist ids are rejected`() {
    val item = buildJsonObject {
      put("type", "albums")
      put("id", "pl.abc")
    }
    assertNull(resolveAlbumId(item, buildJsonObject {}))
  }

  @Test
  fun `album id parsed from url`() {
    assertEquals(
      "1441164426",
      albumIdFromUrl("https://music.apple.com/us/album/abbey-road/1441164426?i=1441164495"),
    )
    assertNull(albumIdFromUrl("https://music.apple.com/us/album/abbey-road"))
    assertNull(albumIdFromUrl(null))
  }

  // --- editorial video extraction ---

  @Test
  fun `preferred motion asset wins over others`() {
    val editorial =
      buildJsonObject {
        put("motionDetailSquare", buildJsonObject { put("video", "detail-square") })
        put("motionSquareVideo1x1", buildJsonObject { put("hlsUrl", "square-hls") })
      }
    assertEquals("square-hls", extractEditorialVideoUrl(editorial))
  }

  @Test
  fun `unknown motion asset is used as fallback`() {
    val editorial =
      buildJsonObject {
        put("customMotion", buildJsonObject { put("videoUrl", "https://cdn/custom.m3u8") })
      }
    assertEquals("https://cdn/custom.m3u8", extractEditorialVideoUrl(editorial))
  }

  @Test
  fun `editorial video without links yields null`() {
    val editorial = buildJsonObject { put("motionSquareVideo1x1", buildJsonObject { put("width", "100") }) }
    assertNull(extractEditorialVideoUrl(editorial))
  }

  // --- album response mapping ---

  @Test
  fun `album response maps motion artwork`() {
    val editorial = buildJsonObject { put("motionSquareVideo1x1", buildJsonObject { put("video", "hls") }) }
    val artwork = albumArtworkFromResponse(albumResponse("Abbey Road", "The Beatles", editorial), "1441164426", null)
    assertEquals("Abbey Road", artwork?.name)
    assertEquals("The Beatles", artwork?.artist)
    assertEquals("1441164426", artwork?.albumId)
    assertEquals("Abbey Road", artwork?.albumName)
    assertEquals("hls", artwork?.animated)
  }

  @Test
  fun `song lookup keeps song title and album name`() {
    val editorial = buildJsonObject { put("motionSquareVideo1x1", buildJsonObject { put("video", "hls") }) }
    val artwork =
      albumArtworkFromResponse(
        albumResponse("Abbey Road", "The Beatles", editorial),
        "1441164426",
        "The Beatles",
        titleOverride = "Come Together",
        artistOverride = "The Beatles",
      )
    assertEquals("Come Together", artwork?.name)
    assertEquals("Abbey Road", artwork?.albumName)
  }

  @Test
  fun `blacklisted album response is rejected`() {
    val editorial = buildJsonObject { put("motionSquareVideo1x1", buildJsonObject { put("video", "hls") }) }
    assertNull(
      albumArtworkFromResponse(albumResponse("The Beatles Essentials", "The Beatles", editorial), "1", null),
    )
  }

  @Test
  fun `album without motion yields null`() {
    assertNull(albumArtworkFromResponse(albumResponse("Abbey Road", "The Beatles", null), "1", null))
  }

  // --- cache key ---

  @Test
  fun `cache key is trimmed and case insensitive`() {
    assertEquals("song|song|artist|us", cacheKey("song", "  Song ", "Artist", "US"))
  }
}
