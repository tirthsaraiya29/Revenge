package com.revenge.metadata.cleaner

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiTextTest {

  @Test
  fun `decodes named ampersand entity`() {
    assertEquals("Sabin Rai & The Pharaoh", ApiText.clean("Sabin Rai &amp; The Pharaoh"))
  }

  @Test
  fun `removes orphan fragment at start plus following whitespace`() {
    assertEquals("The Pharaoh", ApiText.clean("amp; The Pharaoh"))
  }

  @Test
  fun `removes orphan fragment after whitespace inside parentheses`() {
    assertEquals(
      "Dhanyavaad (feat. The Pharaoh)",
      ApiText.clean("Dhanyavaad (feat. amp; The Pharaoh)"),
    )
  }

  @Test
  fun `decodes decimal and hexadecimal numeric references`() {
    assertEquals("A A", ApiText.clean("&#65; &#x41;"))
    assertEquals("é", ApiText.clean("&#233;"))
    assertEquals("é", ApiText.clean("&#xE9;"))
  }

  @Test
  fun `decodes quote and apostrophe entities`() {
    assertEquals("\"Roll\" 'King'", ApiText.clean("&quot;Roll&quot; &apos;King&apos;"))
  }

  @Test
  fun `decodes angle bracket entities`() {
    assertEquals("a < b > c", ApiText.clean("a &lt; b &gt; c"))
  }

  @Test
  fun `leaves plain text untouched`() {
    assertEquals("Alan Walker", ApiText.clean("Alan Walker"))
    assertEquals("R&B", ApiText.clean("R&B"))
  }

  @Test
  fun `preserves numeric fragments that are not references`() {
    assertEquals("Issue #123; please check", ApiText.clean("Issue #123; please check"))
    assertEquals("Episode #5; the finale", ApiText.clean("Episode #5; the finale"))
  }

  @Test
  fun `empty string stays empty`() {
    assertEquals("", ApiText.clean(""))
  }

  @Test
  fun `out of range reference is returned literally`() {
    assertEquals("&#1114112;", ApiText.clean("&#1114112;"))
  }

  @Test
  fun `does not strip a decoded entity`() {
    assertEquals("\"x\"", ApiText.clean("&quot;x&quot;"))
  }

  @Test
  fun `decodes ampersand last so double escaped text loses one level`() {
    assertEquals("&quot;", ApiText.clean("&amp;quot;"))
  }
}
