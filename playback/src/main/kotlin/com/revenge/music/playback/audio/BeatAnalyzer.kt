package com.revenge.music.playback.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin
import kotlin.math.sqrt
import timber.log.Timber

/**
 * Offline beat/key analyser built from plain DSP (no ML):
 *
 * 1. decode a window of the track to mono PCM,
 * 2. build a spectral-flux onset envelope,
 * 3. estimate the tempo by autocorrelation and the beat phase by a comb filter,
 * 4. estimate a key from chroma energy,
 * 5. find head/tail energy boundaries for mix points.
 *
 * The beat grid is `firstBeatOffsetMs + k * 60_000 / bpm`.
 */
object BeatAnalyzer {

  data class Result(
    val bpm: Float,
    val firstBeatOffsetMs: Long,
    val confidence: Float,
    val mixInPointMs: Long? = null,
    val mixOutPointMs: Long? = null,
    /** 0 = C, 1 = C#, ... 11 = B; null when there was not enough tonal energy. */
    val keyPitchClass: Int? = null,
    val keyIsMinor: Boolean? = null,
  )

  /** [result] is null when the (possibly partial) data could not be analysed. */
  class CachedAnalysis(val result: Result?, val complete: Boolean)

  private const val TAG = "BeatAnalyzer"

  private const val FFT_SIZE = 1024
  private const val HOP_SIZE = 512
  private const val BIN_COUNT = FFT_SIZE / 2

  private const val MIN_BPM = 60f
  private const val MAX_BPM = 180f
  private const val CANONICAL_MIN_BPM = 70f
  private const val CANONICAL_MAX_BPM = 140f

  /** Window analysed from the middle of the track. */
  private const val WINDOW_US = 18_000_000L

  private const val HEAD_WINDOW_US = 16_000_000L
  private const val TAIL_WINDOW_US = 24_000_000L

  private const val ENERGY_BLOCK_MS = 500
  private const val MAX_INTRO_SKIP_MS = 20_000L
  private const val MAX_OUTRO_CUT_MS = 45_000L

  /** Cap on stream bytes fetched for one analysis, keeping automix responsive. */
  private const val MAX_FETCH_BYTES = 5L * 1024 * 1024
  private const val MIN_FETCH_BYTES = 256 * 1024

  private const val MAGNITUDE_SCALE = 10f
  private const val KEY_MIN_HZ = 65f
  private const val KEY_MAX_HZ = 2000f

  private const val CODEC_TIMEOUT_US = 10_000L

  fun analyzeUri(
    context: Context,
    uri: Uri,
    shouldCancel: () -> Boolean = { false },
  ): Result? = analyze(shouldCancel) { extractor -> extractor.setDataSource(context, uri, null) }

  /**
   * Downloads the track through the playback data-source chain into a temp file and analyses it.
   * Cached sources are read locally, so this normally costs at most one audio download.
   */
  @UnstableApi
  fun analyzeStream(
    dataSourceFactory: DataSource.Factory,
    mediaId: String,
    tempDir: File,
    shouldCancel: () -> Boolean = { false },
  ): CachedAnalysis? {
    val tempFile = File(tempDir, "beat_${mediaId.hashCode()}.tmp")
    try {
      var copied = 0L
      var reachedEnd = false
      val source = dataSourceFactory.createDataSource()
      try {
        source.open(DataSpec.Builder().setUri(Uri.parse(mediaId)).setKey(mediaId).build())
        FileOutputStream(tempFile).use { output ->
          val chunk = ByteArray(64 * 1024)
          while (copied < MAX_FETCH_BYTES) {
            if (shouldCancel()) {
              Timber.tag(TAG).d("Fetch for %s cancelled at %d bytes", mediaId, copied)
              return null
            }
            val read = source.read(chunk, 0, chunk.size)
            if (read == C.RESULT_END_OF_INPUT) {
              reachedEnd = true
              break
            }
            output.write(chunk, 0, read)
            copied += read
          }
        }
      } catch (e: Exception) {
        Timber.tag(TAG).d("Fetch for %s stopped at %d bytes: %s", mediaId, copied, e.message)
      } finally {
        runCatching { source.close() }
      }

      Timber.tag(TAG).d("Fetched %s: %d bytes, complete=%s", mediaId, copied, reachedEnd)
      if (copied < MIN_FETCH_BYTES) return null
      if (shouldCancel()) return null

      val result = analyze(shouldCancel) { it.setDataSource(tempFile.absolutePath) }
      return CachedAnalysis(result, reachedEnd)
    } finally {
      tempFile.delete()
    }
  }

  private fun analyze(
    shouldCancel: () -> Boolean,
    setSource: (MediaExtractor) -> Unit,
  ): Result? {
    val extractor = MediaExtractor()
    try {
      if (shouldCancel()) return null
      setSource(extractor)

      var audioTrack = -1
      var format: MediaFormat? = null
      for (track in 0 until extractor.trackCount) {
        val candidate = extractor.getTrackFormat(track)
        if (candidate.getString(MediaFormat.KEY_MIME)?.startsWith("audio/") == true) {
          audioTrack = track
          format = candidate
          break
        }
      }
      val audioFormat = format ?: return null
      if (audioTrack < 0) return null

      extractor.selectTrack(audioTrack)
      val durationUs =
        if (audioFormat.containsKey(MediaFormat.KEY_DURATION)) {
          audioFormat.getLong(MediaFormat.KEY_DURATION)
        } else 0L

      // Middle window: usually a steady, full-band section.
      val windowStartUs = max(0L, durationUs / 2 - WINDOW_US / 2)
      extractor.seekTo(windowStartUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
      val windowStartMs = max(0L, extractor.sampleTime) / 1000

      if (shouldCancel()) return null
      val pcm = decodeMono(extractor, audioFormat, WINDOW_US, shouldCancel) ?: return null
      if (pcm.samples.size < FFT_SIZE * 8) return null

      if (shouldCancel()) return null
      val key = estimateKey(pcm.samples, pcm.sampleRate)

      if (shouldCancel()) return null
      val flux = spectralFlux(pcm.samples)
      val frameRate = pcm.sampleRate.toFloat() / HOP_SIZE
      val tempo = estimateTempo(flux, frameRate) ?: return null
      val periodFrames = tempo.first
      val confidence = tempo.second
      val phaseFrames = estimateBeatPhase(flux, periodFrames)

      var periodMs = periodFrames / frameRate * 1000f
      var bpm = 60_000f / periodMs
      // Fold octaves into the canonical range; doubling/halving keeps the same phase anchor.
      while (bpm < CANONICAL_MIN_BPM) {
        bpm *= 2f
        periodMs /= 2f
      }
      while (bpm >= CANONICAL_MAX_BPM) {
        bpm /= 2f
        periodMs *= 2f
      }

      val anchorMs = windowStartMs + (phaseFrames / frameRate * 1000f).roundToLong()
      val periodRounded = periodMs.roundToLong().coerceAtLeast(1L)
      val firstBeatOffsetMs = (anchorMs % periodRounded + periodRounded) % periodRounded

      val mixInPointMs =
        if (durationUs > HEAD_WINDOW_US) {
          extractor.seekTo(0L, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
          decodeMono(extractor, audioFormat, HEAD_WINDOW_US, shouldCancel)?.let { head ->
            detectMixIn(
              energyEnvelope(head.samples, head.sampleRate),
              firstBeatOffsetMs,
              periodMs,
            )
          }
        } else null

      val durationMs = durationUs / 1000
      val mixOutPointMs =
        if (durationUs > TAIL_WINDOW_US) {
          extractor.seekTo(durationUs - TAIL_WINDOW_US, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
          val tailStartMs = max(0L, extractor.sampleTime) / 1000
          decodeMono(extractor, audioFormat, TAIL_WINDOW_US, shouldCancel)?.let { tail ->
            detectMixOut(energyEnvelope(tail.samples, tail.sampleRate), tailStartMs, durationMs)
          }
        } else null

      return Result(
        bpm = bpm,
        firstBeatOffsetMs = firstBeatOffsetMs,
        confidence = confidence,
        mixInPointMs = mixInPointMs,
        mixOutPointMs = mixOutPointMs,
        keyPitchClass = key?.first,
        keyIsMinor = key?.second,
      )
    } catch (e: Exception) {
      Timber.tag(TAG).w(e, "Beat analysis failed")
      return null
    } finally {
      extractor.release()
    }
  }

  private class Pcm(val samples: FloatArray, val sampleRate: Int)

  private fun decodeMono(
    extractor: MediaExtractor,
    format: MediaFormat,
    limitUs: Long,
    shouldCancel: () -> Boolean,
  ): Pcm? {
    val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
    val codec = MediaCodec.createDecoderByType(mime)
    val chunks = ArrayList<FloatArray>()
    var sampleRate = 0
    var decodedUs = 0L

    try {
      codec.configure(format, null, null, 0)
      codec.start()
      var inputFinished = false
      var outputFinished = false
      val info = MediaCodec.BufferInfo()

      while (!outputFinished && decodedUs < limitUs) {
        if (shouldCancel()) return null

        if (!inputFinished) {
          val inputIndex = codec.dequeueInputBuffer(CODEC_TIMEOUT_US)
          if (inputIndex >= 0) {
            val input = codec.getInputBuffer(inputIndex)!!
            val size = extractor.readSampleData(input, 0)
            if (size < 0) {
              codec.queueInputBuffer(inputIndex, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
              inputFinished = true
            } else {
              codec.queueInputBuffer(inputIndex, 0, size, extractor.sampleTime, 0)
              extractor.advance()
            }
          }
        }

        val outputIndex = codec.dequeueOutputBuffer(info, CODEC_TIMEOUT_US)
        if (outputIndex >= 0) {
          if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) outputFinished = true
          if (info.size > 0) {
            val outputFormat = codec.outputFormat
            sampleRate = outputFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE)
            val channels = outputFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT)
            val mono = extractMono(codec.getOutputBuffer(outputIndex)!!, info, outputFormat, channels)
            chunks.add(mono)
            decodedUs += mono.size.toLong() * 1_000_000L / max(1, sampleRate)
          }
          codec.releaseOutputBuffer(outputIndex, false)
        }
      }
    } catch (e: Exception) {
      Timber.tag(TAG).w(e, "PCM decode failed")
      return null
    } finally {
      runCatching { codec.stop() }
      codec.release()
    }

    if (sampleRate == 0 || chunks.isEmpty()) return null

    val total = chunks.sumOf { it.size }
    val samples = FloatArray(total)
    var cursor = 0
    for (chunk in chunks) {
      chunk.copyInto(samples, cursor)
      cursor += chunk.size
    }
    return Pcm(samples, sampleRate)
  }

  private fun extractMono(
    buffer: java.nio.ByteBuffer,
    info: MediaCodec.BufferInfo,
    format: MediaFormat,
    channels: Int,
  ): FloatArray {
    buffer.position(info.offset)
    buffer.limit(info.offset + info.size)
    buffer.order(ByteOrder.LITTLE_ENDIAN)

    val encoding =
      if (format.containsKey(MediaFormat.KEY_PCM_ENCODING)) {
        format.getInteger(MediaFormat.KEY_PCM_ENCODING)
      } else {
        C.ENCODING_PCM_16BIT
      }

    if (encoding == C.ENCODING_PCM_FLOAT) {
      val floats = buffer.asFloatBuffer()
      val frames = floats.remaining() / channels
      return FloatArray(frames) { frame ->
        var sum = 0f
        for (channel in 0 until channels) sum += floats.get(frame * channels + channel)
        sum / channels
      }
    }

    val shorts = buffer.asShortBuffer()
    val frames = shorts.remaining() / channels
    return FloatArray(frames) { frame ->
      var sum = 0f
      for (channel in 0 until channels) {
        sum += shorts.get(frame * channels + channel) / 32768f
      }
      sum / channels
    }
  }

  /** RMS energy per [ENERGY_BLOCK_MS] block. */
  private fun energyEnvelope(samples: FloatArray, sampleRate: Int): FloatArray {
    val blockSize = (sampleRate * ENERGY_BLOCK_MS / 1000).coerceAtLeast(1)
    val blocks = samples.size / blockSize
    return FloatArray(blocks) { block ->
      val start = block * blockSize
      var energy = 0f
      for (i in 0 until blockSize) {
        val sample = samples[start + i]
        energy += sample * sample
      }
      sqrt(energy / blockSize)
    }
  }

  private fun percentile(values: FloatArray, fraction: Float): Float {
    if (values.isEmpty()) return 0f
    val sorted = values.sorted()
    val index = ((sorted.size - 1) * fraction).roundToInt().coerceIn(0, sorted.size - 1)
    return sorted[index]
  }

  /** First sustained body-level block, snapped forward to the beat grid. */
  private fun detectMixIn(env: FloatArray, firstBeatOffsetMs: Long, periodMs: Float): Long? {
    if (env.size < 8) return null
    val reference = percentile(env, 0.75f)
    if (reference <= 0f) return null

    var firstBlock = -1
    for (block in 0 until env.size - 4) {
      if (
        env[block] >= 0.55f * reference &&
          env[block + 1] >= 0.4f * reference &&
          env[block + 2] >= 0.4f * reference &&
          env[block + 3] >= 0.4f * reference
      ) {
        firstBlock = block
        break
      }
    }
    // No intro worth skipping: the track already starts at body level.
    if (firstBlock <= 0) return null

    val firstBlockMs = firstBlock.toLong() * ENERGY_BLOCK_MS
    if (firstBlockMs > MAX_INTRO_SKIP_MS) return null

    val beats =
      ceil((firstBlockMs - firstBeatOffsetMs) / periodMs.toDouble()).toLong().coerceAtLeast(0L)
    return (firstBeatOffsetMs + beats * periodMs.toDouble()).roundToLong()
  }

  /** Last moment the tail window is still loud; everything after is outro. */
  private fun detectMixOut(env: FloatArray, windowStartMs: Long, durationMs: Long): Long? {
    if (env.size < 8 || durationMs <= 0L) return null
    val reference = percentile(env, 0.75f)
    if (reference <= 0f) return null

    var lastLoud = -1
    for (block in env.indices.reversed()) {
      if (env[block] >= 0.5f * reference) {
        lastLoud = block
        break
      }
    }
    if (lastLoud < 0) return null

    val mixOutMs = windowStartMs + (lastLoud + 1).toLong() * ENERGY_BLOCK_MS
    // Still loud almost to the end: nothing to cut.
    if (durationMs - mixOutMs < 3_000L) return null
    return max(mixOutMs, durationMs - MAX_OUTRO_CUT_MS)
  }

  /** Half-wave-rectified, log-compressed spectral flux per hop, with the local mean removed. */
  private fun spectralFlux(samples: FloatArray): FloatArray {
    val window = hannWindow()
    val frameCount = (samples.size - FFT_SIZE) / HOP_SIZE
    val flux = FloatArray(frameCount)
    val previousMagnitudes = FloatArray(BIN_COUNT)
    val real = FloatArray(FFT_SIZE)
    val imaginary = FloatArray(FFT_SIZE)

    for (frame in 0 until frameCount) {
      val offset = frame * HOP_SIZE
      for (i in 0 until FFT_SIZE) {
        real[i] = samples[offset + i] * window[i]
        imaginary[i] = 0f
      }
      fft(real, imaginary)

      var frameFlux = 0f
      for (bin in 0 until BIN_COUNT) {
        val magnitude =
          ln(1f + MAGNITUDE_SCALE * sqrt(real[bin] * real[bin] + imaginary[bin] * imaginary[bin]))
        val rise = magnitude - previousMagnitudes[bin]
        if (rise > 0f) frameFlux += rise
        previousMagnitudes[bin] = magnitude
      }
      flux[frame] = frameFlux
    }

    // Remove slow dynamics so autocorrelation sees onsets rather than loudness.
    val windowRadius = (0.5f * FFT_SIZE / HOP_SIZE * 8).roundToInt().coerceAtLeast(8)
    return FloatArray(frameCount) { index ->
      val lo = max(0, index - windowRadius)
      val hi = min(frameCount - 1, index + windowRadius)
      var mean = 0f
      for (j in lo..hi) mean += flux[j]
      mean /= (hi - lo + 1)
      max(0f, flux[index] - mean)
    }
  }

  /**
   * Autocorrelation over the plausible beat-period lags. Candidates whose doubled lag also
   * correlates are preferred, which rejects half-beat picks. Returns (periodInFrames, confidence).
   */
  private fun estimateTempo(flux: FloatArray, frameRate: Float): Pair<Float, Float>? {
    val minLag = (frameRate * 60f / MAX_BPM).roundToInt()
    val maxLag = (frameRate * 60f / MIN_BPM).roundToInt()
    if (flux.size < maxLag * 2) return null

    val correlation = FloatArray(maxLag + 1)
    for (lag in minLag..maxLag) {
      var sum = 0f
      for (i in 0 until flux.size - lag) sum += flux[i] * flux[i + lag]
      correlation[lag] = sum / (flux.size - lag)
    }

    var mean = 0f
    for (lag in minLag..maxLag) mean += correlation[lag]
    mean /= (maxLag - minLag + 1)
    if (mean <= 0f) return null

    var bestLag = -1
    var bestScore = Float.NEGATIVE_INFINITY
    for (lag in minLag..maxLag) {
      val isInterior = lag > minLag && lag < maxLag
      if (isInterior && (correlation[lag] < correlation[lag - 1] ||
          correlation[lag] < correlation[lag + 1])
      ) {
        continue
      }
      var score = correlation[lag]
      val doubleLag = lag * 2
      if (doubleLag <= maxLag) score += 0.5f * correlation[doubleLag]
      if (score > bestScore) {
        bestScore = score
        bestLag = lag
      }
    }
    if (bestLag < 0) return null

    val refined =
      if (bestLag in (minLag + 1) until maxLag) {
        val before = correlation[bestLag - 1]
        val peak = correlation[bestLag]
        val after = correlation[bestLag + 1]
        val denominator = before - 2 * peak + after
        if (denominator != 0f) bestLag + 0.5f * (before - after) / denominator
        else bestLag.toFloat()
      } else {
        bestLag.toFloat()
      }

    val sorted = correlation.copyOfRange(minLag, maxLag + 1).sorted()
    val median = sorted[sorted.size / 2]
    val peak = correlation[bestLag]
    val confidence = if (peak > 0f) ((peak - median) / peak).coerceIn(0f, 1f) else 0f
    return refined to confidence
  }

  /** Comb filter: the phase (in frames) whose beat-spaced samples carry the most flux. */
  private fun estimateBeatPhase(flux: FloatArray, periodFrames: Float): Float {
    val period = periodFrames.roundToInt().coerceAtLeast(1)
    var bestPhase = 0
    var bestSum = Float.NEGATIVE_INFINITY
    for (phase in 0 until period) {
      var sum = 0f
      var index = phase
      while (index < flux.size) {
        sum += flux[index]
        index += period
      }
      if (sum > bestSum) {
        bestSum = sum
        bestPhase = phase
      }
    }
    return bestPhase.toFloat()
  }

  // Krumhansl-Schmuckler key profiles: emphasis each scale degree puts on the tonic.
  private val majorProfile =
    floatArrayOf(6.35f, 2.23f, 3.48f, 2.33f, 4.38f, 4.09f, 2.52f, 5.19f, 2.39f, 3.66f, 2.29f, 2.88f)
  private val minorProfile =
    floatArrayOf(6.33f, 2.68f, 3.52f, 5.38f, 2.60f, 3.53f, 2.54f, 4.75f, 3.98f, 2.69f, 3.34f, 3.17f)

  /** Correlates 12-bin chroma against both profiles for all 12 tonics. */
  private fun estimateKey(samples: FloatArray, sampleRate: Int): Pair<Int, Boolean>? {
    if (samples.size < FFT_SIZE * 4) return null
    val window = hannWindow()
    val hop = FFT_SIZE / 2
    val frames = (samples.size - FFT_SIZE) / hop
    if (frames < 4) return null

    val binHz = sampleRate.toFloat() / FFT_SIZE
    val minBin = (KEY_MIN_HZ / binHz).toInt().coerceAtLeast(1)
    val maxBin = (KEY_MAX_HZ / binHz).toInt().coerceAtMost(BIN_COUNT - 1)

    val chroma = FloatArray(12)
    val real = FloatArray(FFT_SIZE)
    val imaginary = FloatArray(FFT_SIZE)

    for (frame in 0 until frames) {
      val offset = frame * hop
      for (i in 0 until FFT_SIZE) {
        real[i] = samples[offset + i] * window[i]
        imaginary[i] = 0f
      }
      fft(real, imaginary)

      for (bin in minBin..maxBin) {
        val magnitude = sqrt(real[bin] * real[bin] + imaginary[bin] * imaginary[bin])
        if (magnitude <= 0f) continue
        val midi = 69.0 + 12.0 * (ln((bin * binHz) / 440.0) / ln(2.0))
        val pitchClass = ((midi.roundToInt() % 12) + 12) % 12
        chroma[pitchClass] += magnitude
      }
    }

    val total = chroma.sum()
    if (total <= 0f) return null
    for (i in 0 until 12) chroma[i] /= total

    var bestScore = Float.NEGATIVE_INFINITY
    var bestPitchClass = 0
    var isMinor = false
    for (tonic in 0 until 12) {
      val major = correlate(chroma, majorProfile, tonic)
      if (major > bestScore) {
        bestScore = major
        bestPitchClass = tonic
        isMinor = false
      }
      val minor = correlate(chroma, minorProfile, tonic)
      if (minor > bestScore) {
        bestScore = minor
        bestPitchClass = tonic
        isMinor = true
      }
    }
    return bestPitchClass to isMinor
  }

  private fun correlate(chroma: FloatArray, profile: FloatArray, tonic: Int): Float {
    var sum = 0f
    for (degree in 0 until 12) sum += chroma[(degree + tonic) % 12] * profile[degree]
    return sum
  }

  private fun hannWindow(): FloatArray =
    FloatArray(FFT_SIZE) { 0.5f - 0.5f * cos(2.0 * PI * it / FFT_SIZE).toFloat() }

  /** In-place iterative radix-2 Cooley-Tukey FFT; both arrays must have a power-of-two length. */
  private fun fft(real: FloatArray, imaginary: FloatArray) {
    val size = real.size

    var reversed = 0
    for (i in 1 until size) {
      var bit = size ushr 1
      while (reversed and bit != 0) {
        reversed = reversed xor bit
        bit = bit ushr 1
      }
      reversed = reversed or bit
      if (i < reversed) {
        val tempReal = real[i]
        real[i] = real[reversed]
        real[reversed] = tempReal
        val tempImaginary = imaginary[i]
        imaginary[i] = imaginary[reversed]
        imaginary[reversed] = tempImaginary
      }
    }

    var blockSize = 2
    while (blockSize <= size) {
      val angle = -2.0 * PI / blockSize
      val stepReal = cos(angle).toFloat()
      val stepImaginary = sin(angle).toFloat()
      var start = 0
      while (start < size) {
        var twiddleReal = 1f
        var twiddleImaginary = 0f
        val half = blockSize / 2
        for (k in 0 until half) {
          val evenReal = real[start + k]
          val evenImaginary = imaginary[start + k]
          val oddIndex = start + k + half
          val oddReal = real[oddIndex] * twiddleReal - imaginary[oddIndex] * twiddleImaginary
          val oddImaginary = real[oddIndex] * twiddleImaginary + imaginary[oddIndex] * twiddleReal

          real[start + k] = evenReal + oddReal
          imaginary[start + k] = evenImaginary + oddImaginary
          real[oddIndex] = evenReal - oddReal
          imaginary[oddIndex] = evenImaginary - oddImaginary

          val nextReal = twiddleReal * stepReal - twiddleImaginary * stepImaginary
          twiddleImaginary = twiddleReal * stepImaginary + twiddleImaginary * stepReal
          twiddleReal = nextReal
        }
        start += blockSize
      }
      blockSize = blockSize shl 1
    }
  }
}
