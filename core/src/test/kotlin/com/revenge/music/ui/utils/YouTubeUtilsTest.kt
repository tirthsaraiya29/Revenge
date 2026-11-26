package com.revenge.music.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeUtilsTest {

    @Test
    fun `ytimg thumbnails are rewritten by size`() {
        assertEquals(
            "https://i.ytimg.com/vi/abc/maxresdefault.jpg",
            "https://i.ytimg.com/vi/abc/default.jpg".resize(1200, 1200),
        )
        assertEquals(
            "https://i.ytimg.com/vi/abc/hqdefault.jpg",
            "https://i.ytimg.com/vi/abc/maxresdefault.jpg".resize(544, 544),
        )
        assertEquals(
            "https://i.ytimg.com/vi/abc/hqdefault.jpg?w=1",
            "https://i.ytimg.com/vi/abc/sddefault.jpg?w=1".resize(100, 100),
        )
    }

    @Test
    fun `googleusercontent sized urls use the square variant`() {
        assertEquals(
            "https://lh3.googleusercontent.com/abc=w1200-h1200",
            "https://lh3.googleusercontent.com/abc=w60-h60".resize(1200, 1200),
        )
        assertEquals(
            "https://lh3.googleusercontent.com/abc=w500-h500",
            "https://lh3.googleusercontent.com/abc=w60-h60".resize(500, 500),
        )
        assertEquals(
            "https://yt3.googleusercontent.com/xyz=w1200-h1200",
            "https://yt3.googleusercontent.com/xyz=w10-h10".resize(1200, 1200),
        )
    }

    @Test
    fun `ggpht avatars use a single edge size`() {
        assertEquals(
            "https://yt3.ggpht.com/abc=s544",
            "https://yt3.ggpht.com/abc=s60".resize(544, 544),
        )
        assertEquals(
            "https://yt3.ggpht.com/abc=s300",
            "https://yt3.ggpht.com/abc=s60".resize(null, 300),
        )
        assertEquals(
            "https://yt3.ggpht.com/some-id-s60=s544",
            "https://yt3.ggpht.com/some-id-s60=s60".resize(544, null),
        )
    }

    @Test
    fun `unknown hosts and null sizes are untouched`() {
        val unknown = "https://example.com/art.png?width=60"
        assertEquals(unknown, unknown.resize(1200, 1200))
        val anything = "https://i.ytimg.com/vi/abc/default.jpg"
        assertEquals(anything, anything.resize())
    }

    @Test
    fun `fallback walks down the still sizes and drops the query`() {
        assertEquals(
            "https://i.ytimg.com/vi/abc/hq720.jpg",
            ytimgFallbackUrl("https://i.ytimg.com/vi/abc/maxresdefault.jpg"),
        )
        assertEquals(
            "https://i.ytimg.com/vi/abc/hq720.jpg",
            ytimgFallbackUrl("https://i.ytimg.com/vi/abc/sddefault.jpg?x=1"),
        )
        assertEquals(
            "https://i.ytimg.com/vi/abc/hqdefault.jpg",
            ytimgFallbackUrl("https://i.ytimg.com/vi/abc/hq720.jpg"),
        )
        assertNull(ytimgFallbackUrl("https://i.ytimg.com/vi/abc/hqdefault.jpg"))
        assertNull(ytimgFallbackUrl("https://example.com/vi/abc/maxresdefault.jpg"))
    }
}
