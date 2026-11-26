@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.revenge.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.io.InterruptedIOException

@UnstableApi
class ChunkingDataSourceTest {

    @Test
    fun reassemblesEveryByteInOrder() {
        val body = ByteArray(97) { (it % 251).toByte() }
        val upstream = FakeSource(body)
        val source = ChunkingDataSource(upstream, chunkSize = 16)

        val opened = source.open(DataSpec(uri(), 0, body.size.toLong()))
        assertEquals(97L, opened)

        val result = readAll(source, 10)
        assertArrayEquals(body, result)
        assertEquals(97, upstream.requestedLengths.sum())
        assertEquals(C.RESULT_END_OF_INPUT, source.read(ByteArray(1), 0, 1))
    }

    @Test
    fun knownLengthSmallerThanChunkNeverReadsPastBody() {
        val body = ByteArray(10) { it.toByte() }
        val upstream = FakeSource(body)
        val source = ChunkingDataSource(upstream, chunkSize = 16)

        assertEquals(10L, source.open(DataSpec(uri(), 0, 10)))
        assertArrayEquals(body, readAll(source, 64))
        assertTrue(upstream.requestedLengths.all { it <= 10 })
        assertEquals(C.RESULT_END_OF_INPUT, source.read(ByteArray(1), 0, 1))
    }

    @Test
    fun unknownLengthUsesChunkSizeAndUpstreamEnd() {
        val body = ByteArray(20) { (it * 3).toByte() }
        val source = ChunkingDataSource(FakeSource(body), chunkSize = 8)

        assertEquals(-1L, source.open(DataSpec(uri(), 0, C.LENGTH_UNSET.toLong())))
        assertArrayEquals(body, readAll(source, 64))
        assertEquals(C.RESULT_END_OF_INPUT, source.read(ByteArray(1), 0, 1))
    }

    @Test
    fun recoversFromSingleConnectionDrop() {
        val body = ByteArray(100) { (it + 1).toByte() }
        val upstream = FakeSource(body, dropAt = 55)
        val source = ChunkingDataSource(upstream, chunkSize = 16)

        source.open(DataSpec(uri(), 0, 100))
        assertArrayEquals(body, readAll(source, 32))
        assertTrue(upstream.openCount >= 2)
    }

    @Test
    fun persistentShortStreamThrowsPrematureEof() {
        val body = ByteArray(100) { it.toByte() }
        val upstream = FakeSource(body, stopAt = 40)
        val source = ChunkingDataSource(upstream, chunkSize = 16, maxRetries = 2)

        source.open(DataSpec(uri(), 0, 100))
        val error = assertThrows(IOException::class.java) {
            readAll(source, 32)
        }
        assertFalse(error is InterruptedIOException)
        assertEquals(40, upstream.position)
    }

    @Test
    fun http416OnReopenIsTerminalEndOfInput() {
        assertTerminalReopenIsEnd(
            HttpDataSource.InvalidResponseCodeException(
                416,
                null,
                null,
                emptyMap(),
                DataSpec(uri()),
                ByteArray(0),
            ),
        )
    }

    @Test
    fun positionOutOfRangeOnReopenIsTerminalEndOfInput() {
        assertTerminalReopenIsEnd(DataSourceException(DataSourceException.POSITION_OUT_OF_RANGE))
    }

    private fun assertTerminalReopenIsEnd(terminal: IOException) {
        val upstream = FakeSource(ByteArray(100), stopAt = 10)
        upstream.failOpenOnCall = 2
        upstream.openFailure = terminal
        val source = ChunkingDataSource(upstream, chunkSize = 16)

        assertEquals(100L, source.open(DataSpec(uri(), 0, 100)))
        assertEquals(C.RESULT_END_OF_INPUT, source.read(ByteArray(64), 0, 64))
    }

    @Test
    fun interruptedIoPropagatesWithoutRetry() {
        val upstream = FakeSource(ByteArray(100), readFailure = InterruptedIOException("interrupted"))
        val source = ChunkingDataSource(upstream, chunkSize = 16)

        source.open(DataSpec(uri(), 0, 100))
        assertThrows(InterruptedIOException::class.java) {
            source.read(ByteArray(16), 0, 16)
        }
        assertEquals(1, upstream.openCount)
    }

    @Test
    fun interruptedBackoffRestoresInterruptFlagAndThrows() {
        val upstream = FakeSource(ByteArray(100), stopAt = 0)
        val source = ChunkingDataSource(upstream, chunkSize = 16, maxRetries = 2)

        source.open(DataSpec(uri(), 0, 100))
        Thread.currentThread().interrupt()
        try {
            assertThrows(InterruptedIOException::class.java) {
                source.read(ByteArray(16), 0, 16)
            }
            assertTrue(Thread.currentThread().isInterrupted)
        } finally {
            Thread.interrupted()
        }
    }

    @Test
    fun closeIsIdempotentAndEndsReads() {
        val upstream = FakeSource(ByteArray(32))
        val source = ChunkingDataSource(upstream, chunkSize = 8)

        source.open(DataSpec(uri(), 0, 32))
        source.close()
        source.close()
        assertEquals(1, upstream.closeCount)
        assertEquals(C.RESULT_END_OF_INPUT, source.read(ByteArray(8), 0, 8))
    }

    @Test
    fun factoryCreatesIndependentSources() {
        val body = ByteArray(20) { it.toByte() }
        val factory = ChunkingDataSourceFactory(
            object : DataSource.Factory {
                override fun createDataSource(): DataSource = FakeSource(body)
            },
            chunkSize = 8,
        )

        val first = factory.createDataSource()
        val second = factory.createDataSource()
        assertNotSame(first, second)

        first.open(DataSpec(uri(), 0, 20))
        second.open(DataSpec(uri(), 0, 20))
        assertEquals(8, first.read(ByteArray(64), 0, 64))
        assertEquals(8, second.read(ByteArray(64), 0, 64))
    }

    private fun readAll(source: DataSource, bufferSize: Int): ByteArray {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(bufferSize)
        while (true) {
            val read = source.read(buffer, 0, buffer.size)
            if (read == C.RESULT_END_OF_INPUT) break
            assertTrue(read > 0)
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun uri(): Uri = Uri.parse("https://example.test/media")

    private class FakeSource(
        private val body: ByteArray,
        private val dropAt: Int = -1,
        private val stopAt: Int = -1,
        private val readFailure: IOException? = null,
    ) : DataSource {

        var openCount: Int = 0
        var closeCount: Int = 0
        var position: Int = 0
        var failOpenOnCall: Int = -1
        var openFailure: IOException? = null
        val requestedLengths = mutableListOf<Int>()

        private var end: Int = body.size
        private var dropped = false

        override fun addTransferListener(transferListener: TransferListener) = Unit

        override fun open(dataSpec: DataSpec): Long {
            openCount++
            if (failOpenOnCall > 0 && openCount == failOpenOnCall) {
                throw openFailure ?: IOException("open failure")
            }
            position = dataSpec.position.toInt()
            end = if (dataSpec.length == C.LENGTH_UNSET.toLong()) {
                body.size
            } else {
                minOf(body.size, (dataSpec.position + dataSpec.length).toInt())
            }
            return dataSpec.length
        }

        override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
            readFailure?.let { throw it }
            if (stopAt >= 0 && position >= stopAt) return C.RESULT_END_OF_INPUT
            if (position >= end) return C.RESULT_END_OF_INPUT

            var count = minOf(length, end - position)
            if (dropAt >= 0 && !dropped) {
                val untilDrop = dropAt - position
                if (untilDrop <= 0) {
                    dropped = true
                    throw IOException("connection dropped")
                }
                count = minOf(count, untilDrop)
            }
            if (count <= 0) return C.RESULT_END_OF_INPUT
            requestedLengths.add(count)
            System.arraycopy(body, position, buffer, offset, count)
            position += count
            return count
        }

        override fun getUri(): Uri = Uri.parse("https://example.test/media")

        override fun close() {
            closeCount++
        }
    }
}
