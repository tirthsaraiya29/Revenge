package com.revenge.metadata.cleaner

import org.junit.Assert.assertEquals
import org.junit.Test

class ApiTextTest {

    @Test
    fun decodesNamedAmpersand() {
        assertEquals("Sabin Rai & The Pharaoh", ApiText.clean("Sabin Rai &amp; The Pharaoh"))
    }

    @Test
    fun removesLeadingOrphanFragment() {
        assertEquals("The Pharaoh", ApiText.clean("amp; The Pharaoh"))
    }

    @Test
    fun removesOrphanFragmentAfterWhitespace() {
        assertEquals(
            "Dhanyavaad (feat. The Pharaoh)",
            ApiText.clean("Dhanyavaad (feat. amp; The Pharaoh)"),
        )
    }

    @Test
    fun decodesDecimalAndHexReferences() {
        assertEquals("A A", ApiText.clean("&#65; &#x41;"))
        assertEquals("é", ApiText.clean("&#233;"))
        assertEquals("é", ApiText.clean("&#xE9;"))
    }

    @Test
    fun decodesQuoteEntities() {
        assertEquals("\"Roll\" 'King'", ApiText.clean("&quot;Roll&quot; &apos;King&apos;"))
    }

    @Test
    fun decodesAngleBracketEntities() {
        assertEquals("a < b > c", ApiText.clean("a &lt; b &gt; c"))
    }

    @Test
    fun leavesPlainTextUnchanged() {
        assertEquals("Alan Walker", ApiText.clean("Alan Walker"))
        assertEquals("R&B", ApiText.clean("R&B"))
    }

    @Test
    fun preservesNumericFragments() {
        assertEquals("Issue #123; please check", ApiText.clean("Issue #123; please check"))
        assertEquals("Episode #5; the finale", ApiText.clean("Episode #5; the finale"))
    }

    @Test
    fun emptyInputReturnsEmpty() {
        assertEquals("", ApiText.clean(""))
    }

    @Test
    fun outOfRangeReferenceIsLiteral() {
        assertEquals("&#1114112;", ApiText.clean("&#1114112;"))
    }

    @Test
    fun doesNotOverStripDecodedEntities() {
        assertEquals("\"x\"", ApiText.clean("&quot;x&quot;"))
    }

    @Test
    fun doesNotTrimOrCollapseWhitespace() {
        assertEquals("  spaced  out  ", ApiText.clean("  spaced  out  "))
    }
}
