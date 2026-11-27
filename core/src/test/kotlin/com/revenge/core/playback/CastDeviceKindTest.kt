package com.revenge.core.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class CastDeviceKindTest {

  @Test
  fun televisionNamesAndModels() {
    assertEquals(CastDeviceKind.TV, CastDeviceKind.fromName("Living Room TV", null))
    assertEquals(CastDeviceKind.TV, CastDeviceKind.fromName("SMART TV", "MODEL"))
  }

  @Test
  fun speakersByNameOrModel() {
    assertEquals(CastDeviceKind.SPEAKER, CastDeviceKind.fromName("Kitchen Speaker", null))
    assertEquals(CastDeviceKind.SPEAKER, CastDeviceKind.fromName("Nest Mini", null))
    assertEquals(CastDeviceKind.SPEAKER, CastDeviceKind.fromName("Den", "Speaker Model"))
  }

  @Test
  fun chromecastModel() {
    assertEquals(CastDeviceKind.CHROMECAST, CastDeviceKind.fromName("Bedroom", "Chromecast Ultra"))
    assertEquals(CastDeviceKind.CHROMECAST, CastDeviceKind.fromName("Chromecast", null))
  }

  @Test
  fun unknownFallback() {
    assertEquals(CastDeviceKind.UNKNOWN, CastDeviceKind.fromName("", ""))
    assertEquals(CastDeviceKind.UNKNOWN, CastDeviceKind.fromName("Random", null))
  }

  @Test
  fun precedenceTvBeforeSpeakerAndChromecast() {
    assertEquals(CastDeviceKind.TV, CastDeviceKind.fromName("TV speaker", "chromecast"))
  }
}
