package com.revenge.canvas

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TidalCanvasProviderTest {

  @Test
  fun formatsFiveSegmentCoverId() {
    assertEquals(
      "https://resources.tidal.com/videos/aa/bb/cc/dd/ee/1280x1280.mp4",
      TidalCanvasProvider.formatVideoUrl("aa-bb-cc-dd-ee"),
    )
  }

  @Test
  fun rejectsMalformedCoverIds() {
    assertNull(TidalCanvasProvider.formatVideoUrl(""))
    assertNull(TidalCanvasProvider.formatVideoUrl("aa-bb-cc"))
    assertNull(TidalCanvasProvider.formatVideoUrl("aa-bb-cc-dd-ee-ff"))
    assertNull(TidalCanvasProvider.formatVideoUrl("aa-bb--dd-ee"))
  }

  @Test
  fun exactArtistNameMatches() {
    assertTrue(TidalCanvasProvider.artistsMatch("The Weeknd", "The Weeknd"))
    assertTrue(TidalCanvasProvider.artistsMatch("the weeknd", "THE WEEKND"))
  }

  @Test
  fun billingSuffixesAreSplit() {
    assertTrue(TidalCanvasProvider.artistsMatch("The Weeknd feat. Daft Punk", "The Weeknd"))
    assertTrue(TidalCanvasProvider.artistsMatch("Calvin Harris & Dua Lipa", "Dua Lipa"))
    assertTrue(TidalCanvasProvider.artistsMatch("Drake, Future", "Future"))
    assertTrue(TidalCanvasProvider.artistsMatch("Charli xcx", "Charli XCX"))
  }

  @Test
  fun unrelatedArtistsDoNotMatch() {
    assertFalse(TidalCanvasProvider.artistsMatch("The Weeknd", "Daft Punk"))
    assertFalse(TidalCanvasProvider.artistsMatch("", "Daft Punk"))
  }
}
