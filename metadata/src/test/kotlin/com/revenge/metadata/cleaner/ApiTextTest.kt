package com.revenge.metadata.cleaner

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiTextTest {

  @Test
  fun `decodes escaped ampersand`() {
    assertEquals("Sabin Rai & The Pharaoh", ApiText.clean("Sabin Rai &amp; The Pharaoh"))
  }

  @Test
  fun `drops orphan amp fragment at start`() {
    assertEquals("The Pharaoh", ApiText.clean("amp; The Pharaoh"))
  }

  @Test
  fun `drops orphan fragment after whitespace`() {
    assertEquals(
      "Dhanyavaad (feat. The Pharaoh)",
      ApiText.clean("Dhanyavaad (feat. amp; The Pharaoh)"),
    )
  }

  @Test
  fun `decodes decimal and hex code points`() {
    assertEquals("A A", ApiText.clean("&#65; &#x41;"))
    assertEquals("é", ApiText.clean("&#233;"))
    assertEquals("é", ApiText.clean("&#xE9;"))
  }

  @Test
  fun `decodes named entities`() {
    assertEquals("\"Roll\" 'King'", ApiText.clean("&quot;Roll&quot; &apos;King&apos;"))
    assertEquals("a < b > c", ApiText.clean("a &lt; b &gt; c"))
  }

  @Test
  fun `leaves plain text untouched`() {
    assertEquals("Alan Walker", ApiText.clean("Alan Walker"))
    assertEquals("R&B", ApiText.clean("R&B"))
  }

  @Test
  fun `does not strip numeric hash fragments`() {
    assertEquals("Issue #123; please check", ApiText.clean("Issue #123; please check"))
    assertEquals("Episode #5; the finale", ApiText.clean("Episode #5; the finale"))
  }

  @Test
  fun `handles empty input`() {
    assertEquals("", ApiText.clean(""))
  }
}
