package com.revenge.innertube.models

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RunsTest {
  private fun runs(vararg texts: String) = Runs(texts.map { Run(it, null) })

  @Test
  fun `extractCountText finds compact counts`() {
    assertEquals("1.2M", runs("1.2M views").extractCountText())
    assertEquals("1,234", runs("1,234 likes").extractCountText())
    assertNull(runs("no numbers here").extractCountText())
    assertNull((null as Runs?).extractCountText())
  }

  @Test
  fun `splitBySeparator splits on the bullet run`() {
    val parts = listOf(Run("Song", null), Run(" • ", null), Run("Artist", null)).splitBySeparator()
    assertEquals(2, parts.size)
    assertEquals("Song", parts[0].single().text)
    assertEquals("Artist", parts[1].single().text)
  }

  @Test
  fun `oddElements keeps every other run`() {
    val result = listOf(Run("A", null), Run(" • ", null), Run("B", null)).oddElements()
    assertEquals(listOf("A", "B"), result.map { it.text })
  }

  @Test
  fun `clean drops a plain leading segment but keeps a linked one`() {
    val plain = listOf(listOf(Run("Artist", null)), listOf(Run("Song", null)))
    assertEquals(listOf("Song"), plain.clean().map { it.first().text })

    val linked = listOf(listOf(Run("Artist", NavigationEndpoint())), listOf(Run("Song", null)))
    assertEquals(listOf("Artist", "Song"), linked.clean().map { it.first().text })
  }
}
