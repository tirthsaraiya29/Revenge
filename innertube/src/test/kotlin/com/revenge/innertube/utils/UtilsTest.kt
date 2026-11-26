package com.revenge.innertube.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UtilsTest {
  @Test
  fun `parses cookie pairs and keeps equals signs in values`() {
    val cookies = parseCookieString("SID=abc; SAPISID=def=ghi; malformed; empty=")
    assertEquals("abc", cookies["SID"])
    assertEquals("def=ghi", cookies["SAPISID"])
    assertEquals("", cookies["empty"])
    assertFalse(cookies.containsKey("malformed"))
  }

  @Test
  fun `sha1 hashes a known input`() {
    assertEquals("a9993e364706816aba3e25717850c26c9cd0d89d", sha1("abc"))
  }

  @Test
  fun `parseTime handles minutes and hours`() {
    assertEquals(65, "1:05".parseTime())
    assertEquals(3723, "1:02:03".parseTime())
    assertNull("nope".parseTime())
    assertNull("1:2:3:4".parseTime())
  }

  @Test
  fun `isPrivateId detects privately owned browse ids`() {
    assertTrue(isPrivateId("FEmusic_privately_owned_songs"))
    assertFalse(isPrivateId("MPREb_test"))
  }
}
