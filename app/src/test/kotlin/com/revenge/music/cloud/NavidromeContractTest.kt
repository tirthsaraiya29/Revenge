package com.revenge.music.cloud

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NavidromeContractTest {

  @Test
  fun credentialValidityRequiresAllNavidromeFields() {
    assertFalse(NavidromeCredentials("", "u", "p").isValid)
    assertFalse(NavidromeCredentials("https://x", "", "p").isValid)
    assertFalse(NavidromeCredentials("https://x", "u", "").isValid)
    assertTrue(NavidromeCredentials("https://x", "u", "p").isValid)
  }

  @Test
  fun jellyfinTokenIsRequired() {
    assertFalse(JellyfinCredentials("https://x", "u").isValid)
    assertFalse(JellyfinCredentials("https://x", "u", "").isValid)
    assertTrue(JellyfinCredentials("https://x", "u", "token").isValid)
  }

  @Test
  fun tokenAuthUrlUsesSaltAndMd5() {
    val api = NavidromeApiService()
    api.saltFactory = { "abcdef" }
    val credentials =
      NavidromeCredentials("https://music.example/", "user", "secret", NavidromeAuthMethod.TOKEN)

    val url = api.buildUrl(credentials, "stream.view", mapOf("id" to "s1", "format" to "raw"))

    assertTrue(url.startsWith("https://music.example/rest/stream.view?"))
    assertTrue(url.contains("u=user"))
    assertTrue(url.contains("v=1.16.1"))
    assertTrue(url.contains("c=Revenge"))
    assertTrue(url.contains("f=json"))
    assertTrue(url.contains("id=s1"))
    assertTrue(url.contains("format=raw"))
    assertTrue(url.contains("s=abcdef"))
    assertTrue(url.contains("t=${NavidromeApiService.md5Hex("secret" + "abcdef")}"))
  }

  @Test
  fun passwordAuthUsesEncPrefixAndNoToken() {
    val api = NavidromeApiService()
    val credentials =
      NavidromeCredentials(
        "https://music.example",
        "user",
        "secret",
        NavidromeAuthMethod.PASSWORD,
      )

    val url = api.buildUrl(credentials, "ping.view", emptyMap())

    assertTrue(url.contains("p=enc%3A${NavidromeApiService.hex("secret".toByteArray())}"))
    assertFalse(url.contains("t="))
    assertFalse(url.contains("s=abcdef"))
  }
}
