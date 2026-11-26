package com.revenge.music.localmedia

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SupportedLocalAudioTest {
    @Test
    fun supportedExtensionIsAccepted() {
        assertTrue(SupportedLocalAudio.isSupported("song.flac", "application/octet-stream"))
        assertTrue(SupportedLocalAudio.isSupported("song.MP3", null))
    }

    @Test
    fun blockedExtensionWinsOverSupportedMime() {
        assertFalse(SupportedLocalAudio.isSupported("song.ape", "audio/mpeg"))
        assertFalse(SupportedLocalAudio.isSupported("song.midi", "audio/midi"))
    }

    @Test
    fun mimeIsNormalisedAndParameterised() {
        assertTrue(SupportedLocalAudio.isSupported("song.unknown", " Audio/MPEG ; charset=utf-8"))
        assertTrue(SupportedLocalAudio.isSupported("song.unknown", "audio/x-m4a"))
    }

    @Test
    fun unsupportedEverythingIsRejected() {
        assertFalse(SupportedLocalAudio.isSupported("song.xyz", "application/zip"))
        assertFalse(SupportedLocalAudio.isSupported(null, null))
    }

    @Test
    fun wildcardAudioOnlyForMimeHelper() {
        assertTrue(SupportedLocalAudio.isSupportedMimeType("audio/anything"))
        assertFalse(SupportedLocalAudio.isSupported("song.xyz", "audio/anything"))
    }

    @Test
    fun folderNormalisationDeduplicatesCaseInsensitively() {
        val config = LocalSongScanConfig(
            excludedFolders = listOf(
                "Music\\Albums",
                "/music/albums/",
                "   ",
                "MUSIC/Albums",
                "Podcasts",
            ),
        )
        assertEquals(
            listOf("Music/Albums", "Podcasts"),
            config.normalizedExcludedFolders,
        )
    }

    @Test
    fun activeFilterDependsOnModeAndContent() {
        assertFalse(LocalSongScanConfig().hasActiveFolderFilter)
        assertTrue(
            LocalSongScanConfig(excludedFolders = listOf("music")).hasActiveFolderFilter,
        )
        assertFalse(
            LocalSongScanConfig(
                filterMode = LocalFolderFilterMode.INCLUDE,
                excludedFolders = listOf("music"),
            ).hasActiveFolderFilter,
        )
        assertTrue(
            LocalSongScanConfig(
                filterMode = LocalFolderFilterMode.INCLUDE,
                includedFolders = listOf("music"),
            ).hasActiveFolderFilter,
        )
    }

    @Test
    fun minimumDurationIsSanitised() {
        assertEquals(0, LocalSongScanConfig(minimumDurationSeconds = -5)
            .sanitizedMinimumDurationSeconds)
        assertEquals(30, LocalSongScanConfig(minimumDurationSeconds = 30)
            .sanitizedMinimumDurationSeconds)
    }
}
