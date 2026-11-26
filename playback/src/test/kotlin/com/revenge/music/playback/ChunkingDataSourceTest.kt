package com.revenge.music.playback

import android.net.Uri
import androidx.media3.common.C
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ChunkingDataSourceTest {

  private class FakeHttpSource(
    private val body: ByteArray,
    private val dropAt: Int? = null,
    private val hardStopAt: Int? = null,
  ) : DataSource {
    private var position = 0
    private var droppedOnce = false

    override fun addTransferListener(transferListener: TransferListener) = Unit

    override fun open(dataSpec: DataSpec): Long {
      position = dataSpec.position.toInt()
      val available = (body.size - position).coerceAtLeast(0).toLong()
      return if (dataSpec.length == C.LENGTH_UNSET.toLong()) available
      else minOf(dataSpec.length, available)
    }

    override fun read(buffer: ByteArray, offset: Int, readLength: Int): Int {
      if (hardStopAt != null && position >= hardStopAt) return C.RESULT_END_OF_INPUT
      if (position >= body.size) return C.RESULT_END_OF_INPUT
      if (!droppedOnce && dropAt != null && position >= dropAt) {
        droppedOnce = true
        return C.RESULT_END_OF_INPUT
      }
      val count = minOf(readLength, body.size - position)
      body.copyInto(buffer, offset, position, position + count)
      position += count
      return count
    }

    override fun getUri(): Uri? = null

    override fun close() = Unit
  }

  private fun readAll(source: ChunkingDataSource): ByteArray {
    val output = java.io.ByteArrayOutputStream()
    val buffer = ByteArray(7)
    while (true) {
      val read = source.read(buffer, 0, buffer.size)
      if (read == C.RESULT_END_OF_INPUT) break
      output.write(buffer, 0, read)
    }
    return output.toByteArray()
  }

  private fun spec(length: Long) =
    DataSpec.Builder().setUri(Uri.parse("https://example.test/audio")).setLength(length).build()

  @Test
  fun reassemblesAStreamAcrossChunkBoundaries() {
    val body = ByteArray(97) { it.toByte() }
    val source = ChunkingDataSource(FakeHttpSource(body), chunkSize = 16)
    source.open(spec(body.size.toLong()))
    assertArrayEquals(body, readAll(source))
    source.close()
  }

  @Test
  fun reconnectsAfterAPrematureDrop() {
    val body = ByteArray(100) { it.toByte() }
    val source = ChunkingDataSource(FakeHttpSource(body, dropAt = 55), chunkSize = 20)
    source.open(spec(body.size.toLong()))

    val read = readAll(source)

    assertEquals(body.size, read.size)
    assertArrayEquals(body, read)
    source.close()
  }

  @Test(expected = IOException::class)
  fun givesUpWhenTheStreamNeverCompletes() {
    val body = ByteArray(100) { it.toByte() }
    val source = ChunkingDataSource(FakeHttpSource(body, hardStopAt = 40), chunkSize = 20, maxRetries = 2)
    source.open(spec(body.size.toLong()))
    readAll(source)
  }
}
