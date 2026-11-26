package com.revenge.music.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSourceException
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource.InvalidResponseCodeException
import androidx.media3.datasource.TransferListener
import java.io.IOException
import java.io.InterruptedIOException

/**
 * Fetches a stream in bounded chunks and re-opens at the current position when the upstream drops
 * early, which makes partially cached media resumable. A 416 or out-of-range answer means the
 * stream really ended.
 */
class ChunkingDataSource(
  private val upstream: DataSource,
  private val chunkSize: Long,
  private val maxRetries: Int = 3,
) : DataSource {

  private var spec: DataSpec? = null
  private var expectedLength: Long = C.LENGTH_UNSET.toLong()
  private var consumed: Long = 0L
  private var opened = false

  override fun addTransferListener(transferListener: TransferListener) {
    upstream.addTransferListener(transferListener)
  }

  override fun open(dataSpec: DataSpec): Long {
    spec = dataSpec
    expectedLength = dataSpec.length
    consumed = 0L
    opened = true
    openChunk()
    return expectedLength
  }

  private fun bytesRemaining(): Long =
    if (expectedLength == C.LENGTH_UNSET.toLong()) Long.MAX_VALUE else expectedLength - consumed

  private fun openChunk() {
    val data = spec ?: throw IOException("DataSpec is null")
    val left = bytesRemaining()
    if (left <= 0L) return
    val length =
      if (expectedLength == C.LENGTH_UNSET.toLong()) chunkSize else minOf(chunkSize, left)
    upstream.open(
      data.buildUpon().setPosition(data.position + consumed).setLength(length).build(),
    )
  }

  override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
    if (readLength == 0) return 0
    if (!opened) return C.RESULT_END_OF_INPUT
    if (bytesRemaining() <= 0L) return C.RESULT_END_OF_INPUT

    var attempts = 0
    var lastError: Exception? = null
    val end = C.RESULT_END_OF_INPUT

    while (attempts <= maxRetries) {
      val bytes =
        try {
          upstream.read(buffer, offset, readLength)
        } catch (e: InterruptedIOException) {
          throw e
        } catch (e: Exception) {
          lastError = e
          -1
        }
      if (bytes > 0) {
        consumed += bytes
        return bytes
      }
      if (bytesRemaining() <= 0L) return end

      runCatching { upstream.close() }

      val chunkOpened =
        try {
          openChunk()
          true
        } catch (e: InvalidResponseCodeException) {
          if (e.responseCode == HTTP_RANGE_NOT_SATISFIABLE) return end
          lastError = e
          attempts++
          if (attempts > maxRetries) throw e
          false
        } catch (e: DataSourceException) {
          @Suppress("DEPRECATION")
          if (e.reason == DataSourceException.POSITION_OUT_OF_RANGE) return end
          lastError = e
          attempts++
          if (attempts > maxRetries) throw e
          false
        } catch (e: Exception) {
          lastError = e
          attempts++
          if (attempts > maxRetries) {
            throw IOException("Failed to reopen chunk after $maxRetries retries", e)
          }
          false
        }

      if (chunkOpened) {
        val nextBytes =
          try {
            upstream.read(buffer, offset, readLength)
          } catch (e: InterruptedIOException) {
            throw e
          } catch (e: Exception) {
            lastError = e
            -1
          }
        if (nextBytes > 0) {
          consumed += nextBytes
          return nextBytes
        }
        if (bytesRemaining() <= 0L) return end
        attempts++
      }

      if (attempts <= maxRetries) {
        try {
          Thread.sleep((attempts * BACKOFF_STEP_MS).coerceAtMost(MAX_BACKOFF_MS))
        } catch (e: InterruptedException) {
          Thread.currentThread().interrupt()
          throw InterruptedIOException("Interrupted during chunk retry backoff").apply {
            initCause(e)
          }
        }
      }
    }

    if (expectedLength != C.LENGTH_UNSET.toLong() && consumed < expectedLength) {
      throw IOException(
        "Premature EOF: expected $expectedLength bytes, got $consumed after $maxRetries retries",
        lastError,
      )
    }
    lastError?.let { throw IOException("Failed to read chunk after $maxRetries retries", it) }
    return end
  }

  override fun getUri(): Uri? = upstream.uri

  override fun close() {
    opened = false
    upstream.close()
  }

  private companion object {
    const val HTTP_RANGE_NOT_SATISFIABLE = 416
    const val BACKOFF_STEP_MS = 50L
    const val MAX_BACKOFF_MS = 250L
  }
}

/** Wraps an upstream factory so every created source fetches in [chunkSize] pieces. */
class ChunkingDataSourceFactory(
  private val upstreamFactory: DataSource.Factory,
  private val chunkSize: Long = DEFAULT_CHUNK_SIZE,
) : DataSource.Factory {
  override fun createDataSource(): DataSource =
    ChunkingDataSource(upstreamFactory.createDataSource(), chunkSize)

  private companion object {
    const val DEFAULT_CHUNK_SIZE = 5L * 1024 * 1024
  }
}
