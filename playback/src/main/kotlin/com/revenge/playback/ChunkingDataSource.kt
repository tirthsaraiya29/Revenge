@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.revenge.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.TransferListener
import java.io.IOException
import java.io.InterruptedIOException

class ChunkingDataSource(
    private val upstream: DataSource,
    private val chunkSize: Long,
    private val maxRetries: Int = 3,
) : DataSource {

    private var dataSpec: DataSpec? = null
    private var expectedLength: Long = C.LENGTH_UNSET.toLong()
    private var consumed: Long = 0L
    private var chunkRemaining: Long = 0L
    private var opened: Boolean = false
    private var closed: Boolean = false
    private var ended: Boolean = false

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        this.dataSpec = dataSpec
        expectedLength = dataSpec.length
        consumed = 0L
        chunkRemaining = 0L
        opened = true
        closed = false
        ended = false
        openChunk()
        return expectedLength
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        if (length == 0) return 0
        if (!opened || closed || ended) return C.RESULT_END_OF_INPUT
        val expected = expectedLength
        if (expected != C.LENGTH_UNSET.toLong() && consumed >= expected) {
            return C.RESULT_END_OF_INPUT
        }

        var attempt = 0
        var failure: IOException? = null
        var reopen = false
        while (true) {
            try {
                if (reopen || chunkRemaining <= 0L) {
                    openChunk()
                    reopen = false
                }
                val request = minOf(length.toLong(), chunkRemaining).toInt()
                val read = upstream.read(buffer, offset, request)
                if (read > 0) {
                    consumed += read
                    chunkRemaining -= read
                    return read
                }
                if (read == C.RESULT_END_OF_INPUT && chunkRemaining > 0L) {
                    if (expected == C.LENGTH_UNSET.toLong()) return C.RESULT_END_OF_INPUT
                    failure = IOException("Unexpected end of stream at $consumed bytes")
                } else if (read == C.RESULT_END_OF_INPUT) {
                    if (expected != C.LENGTH_UNSET.toLong() && consumed >= expected) {
                        return C.RESULT_END_OF_INPUT
                    }
                    if (expected == C.LENGTH_UNSET.toLong()) return C.RESULT_END_OF_INPUT
                    failure = IOException("Unexpected end of stream at $consumed bytes")
                } else {
                    failure = IOException("Empty read at $consumed bytes")
                }
            } catch (e: InterruptedIOException) {
                throw e
            } catch (e: IOException) {
                if (isTerminalEnd(e)) {
                    ended = true
                    return C.RESULT_END_OF_INPUT
                }
                failure = e
            }

            reopen = true
            attempt++
            if (attempt > maxRetries) {
                if (expected != C.LENGTH_UNSET.toLong() && consumed < expected) {
                    throw failure ?: IOException("Premature end of stream")
                }
                failure?.let { throw it }
                return C.RESULT_END_OF_INPUT
            }
            backOff(attempt)
        }
    }

    private fun openChunk() {
        val spec = dataSpec ?: return
        val expected = expectedLength
        val remaining = if (expected == C.LENGTH_UNSET.toLong()) {
            C.LENGTH_UNSET.toLong()
        } else {
            expected - consumed
        }
        val requestLength = if (remaining == C.LENGTH_UNSET.toLong()) {
            chunkSize
        } else {
            minOf(chunkSize, remaining)
        }
        upstream.open(spec.subrange(consumed, requestLength))
        chunkRemaining = requestLength
    }

    private fun backOff(attempt: Int) {
        val delayMs = minOf(attempt * 50L, 250L)
        try {
            Thread.sleep(delayMs)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            val interrupted = InterruptedIOException("Interrupted during retry backoff")
            interrupted.initCause(e)
            throw interrupted
        }
    }

    private fun isTerminalEnd(e: IOException): Boolean {
        if (e is HttpDataSource.InvalidResponseCodeException && e.responseCode == 416) return true
        return e is DataSourceException && e.reason == DataSourceException.POSITION_OUT_OF_RANGE
    }

    override fun getUri(): Uri? = upstream.uri

    override fun close() {
        if (closed) return
        closed = true
        opened = false
        upstream.close()
    }
}

class ChunkingDataSourceFactory(
    private val upstreamFactory: DataSource.Factory,
    private val chunkSize: Long = 5L * 1024 * 1024,
) : DataSource.Factory {

    override fun createDataSource(): DataSource =
        ChunkingDataSource(upstreamFactory.createDataSource(), chunkSize)
}
