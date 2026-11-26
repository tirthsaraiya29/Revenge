package com.revenge.music.utils.lastfm

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LastFMSigningTest {

  @Test
  fun signatureIsSortedKeyValueConcatPlusSecret() {
    val params = mapOf("method" to "auth.getToken", "api_key" to "key")
    val expected = LastFM.md5Hex("api_key" + "key" + "method" + "auth.getToken" + "secret")
    assertEquals(expected, LastFM.sign(params, "secret"))
  }

  @Test
  fun signedFormAddsSignatureAndJsonFormat() {
    LastFM.initialize("key", "secret")
    val form = LastFM.signedForm(mapOf("method" to "auth.getToken", "api_key" to "key"))
    assertTrue(form.containsKey("api_sig"))
    assertEquals("json", form["format"])
    assertFalse(form.containsKey("secret"))
  }

  @Test
  fun initializedStateRequiresBothSecrets() {
    LastFM.initialize("", "")
    assertFalse(LastFM.isInitialized())
    LastFM.initialize("key", "secret")
    assertTrue(LastFM.isInitialized())
    assertEquals("key", LastFM.getApiKey())
  }
}
