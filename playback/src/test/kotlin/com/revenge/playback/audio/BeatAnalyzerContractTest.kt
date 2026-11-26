@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.revenge.playback.audio

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.junit.rules.TemporaryFolder
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@UnstableApi
class BeatAnalyzerContractTest {

    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun cancellationBeforeFetchReturnsNullAndCleansTempFile() {
        val result = BeatAnalyzer.analyzeStream(
            dataSourceFactory = factory(ByteArray(0)),
            mediaId = "cancelled",
            tempDir = folder.root,
            shouldCancel = { true },
        )

        assertNull(result)
        assertNoBeatFiles()
    }

    @Test
    fun fewerThanMinimumBytesYieldsNull() {
        val result = BeatAnalyzer.analyzeStream(
            dataSourceFactory = factory(ByteArray(1_000)),
            mediaId = "tiny",
            tempDir = folder.root,
        )

        assertNull(result)
        assertNoBeatFiles()
    }

    @Test
    fun endOfInputMarksFetchCompleteAndCapMarksIncomplete() {
        val complete = BeatAnalyzer.analyzeStream(
            dataSourceFactory = factory(ByteArray(1024 * 1024)),
            mediaId = "complete",
            tempDir = folder.root,
        )
        assertNotNull(complete)
        assertTrue(complete!!.complete)

        val capped = BeatAnalyzer.analyzeStream(
            dataSourceFactory = factory(ByteArray(5 * 1024 * 1024), neverEnd = true),
            mediaId = "capped",
            tempDir = folder.root,
        )
        assertNotNull(capped)
        assertFalse(capped!!.complete)
    }

    @Test
    fun readFailureKeepsUsablePartialData() {
        val large = BeatAnalyzer.analyzeStream(
            dataSourceFactory = factory(ByteArray(1024 * 1024), failAfter = 512 * 1024),
            mediaId = "partial-large",
            tempDir = folder.root,
        )
        assertNotNull(large)
        assertFalse(large!!.complete)

        val small = BeatAnalyzer.analyzeStream(
            dataSourceFactory = factory(ByteArray(1024 * 1024), failAfter = 100 * 1024),
            mediaId = "partial-small",
            tempDir = folder.root,
        )
        assertNull(small)
    }

    @Test
    fun openFailureReturnsNullAndDeletesTempFile() {
        val result = BeatAnalyzer.analyzeStream(
            dataSourceFactory = object : DataSource.Factory {
                override fun createDataSource(): DataSource =
                    FakeSource(ByteArray(0), openFailure = IOException("cannot open"))
            },
            mediaId = "open-failure",
            tempDir = folder.root,
        )

        assertNull(result)
        assertNoBeatFiles()
    }

    private fun assertNoBeatFiles() {
        val leftovers = folder.root.listFiles()?.filter { it.name.startsWith("beat_") }.orEmpty()
        assertEquals(emptyList<String>(), leftovers.map { it.name })
    }

    private fun factory(
        body: ByteArray,
        failAfter: Int = -1,
        neverEnd: Boolean = false,
    ): DataSource.Factory = object : DataSource.Factory {
        override fun createDataSource(): DataSource =
            FakeSource(body, failAfter = failAfter, neverEnd = neverEnd)
    }

    private class FakeSource(
        private val body: ByteArray,
        private val failAfter: Int = -1,
        private val neverEnd: Boolean = false,
        private val openFailure: IOException? = null,
    ) : DataSource {

        private var position: Int = 0

        override fun addTransferListener(transferListener: TransferListener) = Unit

        override fun open(dataSpec: DataSpec): Long {
            openFailure?.let { throw it }
            position = 0
            return C.LENGTH_UNSET.toLong()
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            if (failAfter >= 0 && position >= failAfter) {
                throw IOException("read failed")
            }
            if (position >= body.size) {
                return if (neverEnd) 0 else C.RESULT_END_OF_INPUT
            }
            var count = minOf(length, body.size - position)
            if (failAfter >= 0) count = minOf(count, failAfter - position)
            if (count <= 0) throw IOException("read failed")
            System.arraycopy(body, position, buffer, offset, count)
            position += count
            return count
        }

        override fun getUri(): Uri? = null

        override fun close() = Unit
    }
}
