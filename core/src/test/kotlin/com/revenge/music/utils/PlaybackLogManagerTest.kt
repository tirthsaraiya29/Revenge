package com.revenge.music.utils

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class PlaybackLogManagerTest {

    @Before
    fun reset() = PlaybackLogManager.clearLogs()

    @After
    fun cleanUp() = PlaybackLogManager.clearLogs()

    @Test
    fun retentionKeepsNewestFiveHundred() {
        repeat(501) { index ->
            PlaybackLogManager.log(PlaybackLogLevel.INFO, index.toString())
        }

        val entries = PlaybackLogManager.logs.value
        assertEquals(500, entries.size)
        assertEquals("1", entries.first().message)
        assertEquals("500", entries.last().message)
    }

    @Test
    fun entryKeepsFieldsAndFormatsTimestamp() {
        PlaybackLogManager.log(PlaybackLogLevel.WARNING, "message", "details")

        val entry = PlaybackLogManager.logs.value.single()
        assertEquals(PlaybackLogLevel.WARNING, entry.level)
        assertEquals("message", entry.message)
        assertEquals("details", entry.details)
        assertTrue(entry.timestamp.matches(Regex("\\d{2}:\\d{2}:\\d{2}\\.\\d{3}")))

        PlaybackLogManager.log(PlaybackLogLevel.ERROR, "no details")
        assertNull(PlaybackLogManager.logs.value.last().details)
    }

    @Test
    fun clearEmitsEmptyList() {
        PlaybackLogManager.log(PlaybackLogLevel.INFO, "x")
        PlaybackLogManager.clearLogs()
        assertTrue(PlaybackLogManager.logs.value.isEmpty())
    }
}
