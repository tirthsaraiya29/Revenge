package com.revenge.playback.audio

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.log2
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sin

@UnstableApi
object BeatAnalyzer {

    data class Result(
        val bpm: Float,
        val firstBeatOffsetMs: Long,
        val confidence: Float,
        val mixInPointMs: Long? = null,
        val mixOutPointMs: Long? = null,
        val keyPitchClass: Int? = null,
        val keyIsMinor: Boolean? = null,
    )

    class CachedAnalysis(
        val result: Result?,
        val complete: Boolean,
    )

    fun analyzeUri(
        context: Context,
        uri: Uri,
        shouldCancel: () -> Boolean = { false },
    ): Result? {
        return try {
            if (shouldCancel()) return null
            analyzeSource({ extractorForUri(context, uri) }, shouldCancel)
        } catch (e: Throwable) {
            null
        }
    }

    fun analyzeStream(
        dataSourceFactory: DataSource.Factory,
        mediaId: String,
        tempDir: File,
        shouldCancel: () -> Boolean = { false },
    ): CachedAnalysis? {
        val tempFile = File(tempDir, "beat_${mediaId.hashCode()}.tmp")
        var complete = false
        try {
            if (shouldCancel()) return null
            val source = try {
                dataSourceFactory.createDataSource()
            } catch (e: Throwable) {
                return null
            }

            var copied = 0L
            var reachedEnd = false
            try {
                val spec = DataSpec.Builder()
                    .setUri(Uri.parse(mediaId))
                    .setKey(mediaId)
                    .build()
                try {
                    source.open(spec)
                    FileOutputStream(tempFile).use { stream ->
                        val buffer = ByteArray(READ_BUFFER_BYTES)
                        while (copied < MAX_FETCH_BYTES) {
                            if (shouldCancel()) return null
                            val request = min(
                                buffer.size.toLong(),
                                MAX_FETCH_BYTES - copied,
                            ).toInt()
                            val read = source.read(buffer, 0, request)
                            if (read == C.RESULT_END_OF_INPUT) {
                                reachedEnd = true
                                break
                            }
                            if (read <= 0) {
                                reachedEnd = true
                                break
                            }
                            stream.write(buffer, 0, read)
                            copied += read
                        }
                    }
                } catch (e: Throwable) {
                    // Partially copied bytes remain analyzable.
                }
            } finally {
                runCatching { source.close() }
            }

            complete = reachedEnd
            if (copied < MIN_FETCH_BYTES) return null
            if (shouldCancel()) return null
            return CachedAnalysis(analyzeFile(tempFile, shouldCancel), complete)
        } catch (e: Throwable) {
            return null
        } finally {
            tempFile.delete()
        }
    }

    private fun analyzeFile(file: File, shouldCancel: () -> Boolean): Result? = try {
        analyzeSource({ extractorForFile(file) }, shouldCancel)
    } catch (e: Throwable) {
        null
    }

    private fun analyzeSource(
        openExtractor: () -> MediaExtractor?,
        shouldCancel: () -> Boolean,
    ): Result? {
        if (shouldCancel()) return null
        val probe = openExtractor() ?: return null
        val durationMs = try {
            durationMillis(probe)
        } catch (e: Throwable) {
            -1L
        } finally {
            runCatching { probe.release() }
        }

        val main = if (durationMs > 0L) {
            decodeWindow(
                openExtractor,
                max(0L, durationMs * 1000L / 2L - MIDDLE_WINDOW_HALF_US),
                MIDDLE_WINDOW_US,
                shouldCancel,
            )
        } else {
            decodeWindow(openExtractor, 0L, UNKNOWN_DURATION_WINDOW_US, shouldCancel)
        } ?: return null
        if (main.samples.size < MIN_DECODED_SAMPLES) return null
        if (shouldCancel()) return null

        val mixIn = if (durationMs > 16_000L) {
            decodeWindow(openExtractor, 0L, 16_000_000L, shouldCancel)
        } else {
            null
        }
        if (shouldCancel()) return null

        val mixOutStartMs = if (durationMs > 24_000L) max(0L, durationMs - 24_000L) else 0L
        val mixOut = if (durationMs > 24_000L) {
            decodeWindow(openExtractor, mixOutStartMs * 1000L, 24_000_000L, shouldCancel)
        } else {
            null
        }
        if (shouldCancel()) return null

        return combine(main, mixIn?.samples, mixOut?.samples, mixOutStartMs, durationMs)
    }

    private fun combine(
        main: Segment,
        mixInSamples: FloatArray?,
        mixOutSamples: FloatArray?,
        mixOutStartMs: Long,
        durationMs: Long,
    ): Result? {
        val tempo = estimateTempo(main.samples, main.sampleRate) ?: return null
        val key = estimateKey(main.samples, main.sampleRate)
        val mixIn = mixInSamples?.let {
            mixInPoint(it, main.sampleRate, durationMs, tempo.bpm, tempo.offsetMs)
        }
        val mixOut = mixOutSamples?.let {
            mixOutPoint(it, main.sampleRate, mixOutStartMs, durationMs)
        }
        return Result(
            bpm = tempo.bpm,
            firstBeatOffsetMs = tempo.offsetMs,
            confidence = tempo.confidence,
            mixInPointMs = mixIn,
            mixOutPointMs = mixOut,
            keyPitchClass = key?.pitchClass,
            keyIsMinor = key?.isMinor,
        )
    }

    private class Tempo(val bpm: Float, val offsetMs: Long, val confidence: Float)

    private class Key(val pitchClass: Int, val isMinor: Boolean)

    private class Segment(val samples: FloatArray, val sampleRate: Int)

    private fun estimateTempo(samples: FloatArray, sampleRate: Int): Tempo? {
        if (sampleRate <= 0 || samples.size < MIN_DECODED_SAMPLES) return null
        val frameCount = (samples.size - FFT_SIZE) / FFT_HOP + 1
        if (frameCount < MIN_TEMPO_FRAMES) return null

        val window = DoubleArray(FFT_SIZE) { index ->
            0.5 - 0.5 * cos(2.0 * PI * index / (FFT_SIZE - 1))
        }
        val flux = DoubleArray(frameCount)
        val previous = DoubleArray(FFT_SIZE / 2 + 1)
        val re = DoubleArray(FFT_SIZE)
        val im = DoubleArray(FFT_SIZE)

        for (frame in 0 until frameCount) {
            val offset = frame * FFT_HOP
            for (i in 0 until FFT_SIZE) {
                re[i] = samples[offset + i] * window[i]
                im[i] = 0.0
            }
            fft(re, im)
            var sum = 0.0
            for (bin in 1..FFT_SIZE / 2) {
                val magnitude = hypot(re[bin], im[bin])
                val delta = magnitude - previous[bin]
                if (delta > 0.0) sum += delta
                previous[bin] = magnitude
            }
            flux[frame] = sum
        }

        if (flux.sum() <= ENERGY_EPSILON) return null

        val mean = flux.average()
        val centered = DoubleArray(frameCount) { flux[it] - mean }
        val frameRate = sampleRate.toDouble() / FFT_HOP
        val minLag = max(1, floor(60.0 * frameRate / MAX_BPM).toInt())
        val maxLag = min(frameCount - 2, ceil(60.0 * frameRate / MIN_BPM).toInt())
        if (maxLag <= minLag) return null

        fun correlation(lag: Int): Double {
            var acc = 0.0
            for (i in 0 until frameCount - lag) acc += centered[i] * centered[i + lag]
            return acc / (frameCount - lag)
        }

        var bestLag = -1
        var bestValue = Double.NEGATIVE_INFINITY
        for (lag in minLag..maxLag) {
            val value = correlation(lag)
            if (value > bestValue) {
                bestValue = value
                bestLag = lag
            }
        }
        if (bestLag < 0 || bestValue <= 0.0) return null

        var refinedLag = bestLag.toDouble()
        if (bestLag > minLag && bestLag < maxLag) {
            val before = correlation(bestLag - 1)
            val center = bestValue
            val after = correlation(bestLag + 1)
            val denominator = before - 2.0 * center + after
            if (abs(denominator) > 1e-12) {
                refinedLag += 0.5 * (before - after) / denominator
            }
        }
        if (refinedLag < 1.0) return null

        val bpm = foldBpm(60.0 * frameRate / refinedLag)
        if (!bpm.isFinite() || bpm <= 0.0) return null

        val periodFrames = 60.0 * frameRate / bpm
        val periodLength = max(1, periodFrames.roundToInt())
        val phases = DoubleArray(periodLength)
        for (i in flux.indices) phases[i % periodLength] += flux[i]
        var bestPhase = 0
        for (p in 1 until periodLength) {
            if (phases[p] > phases[bestPhase]) bestPhase = p
        }

        val periodMs = 60_000.0 / bpm
        var offsetMs = bestPhase * FFT_HOP * 1000.0 / sampleRate
        offsetMs %= periodMs
        if (offsetMs < 0.0) offsetMs += periodMs
        val offset = offsetMs.toLong().coerceIn(0L, ceil(periodMs).toLong() - 1L)

        val sortedFlux = flux.sortedArray()
        val peak = sortedFlux.last()
        val median = sortedFlux[sortedFlux.size / 2]
        val confidence = if (peak > 0.0) {
            ((peak - median) / peak).coerceIn(0.0, 1.0).toFloat()
        } else {
            0f
        }

        return Tempo(bpm.toFloat(), offset, confidence)
    }

    private fun estimateKey(samples: FloatArray, sampleRate: Int): Key? {
        if (sampleRate <= 0) return null
        val frameSize = KEY_FFT_SIZE
        if (samples.size < frameSize) return null
        val frameCount = (samples.size - frameSize) / KEY_FFT_HOP + 1
        if (frameCount < 1) return null

        val window = DoubleArray(frameSize) { index ->
            0.5 - 0.5 * cos(2.0 * PI * index / (frameSize - 1))
        }
        val chroma = DoubleArray(12)
        val re = DoubleArray(frameSize)
        val im = DoubleArray(frameSize)

        for (frame in 0 until frameCount) {
            val offset = frame * KEY_FFT_HOP
            for (i in 0 until frameSize) {
                re[i] = samples[offset + i] * window[i]
                im[i] = 0.0
            }
            fft(re, im)
            for (bin in 1..frameSize / 2) {
                val frequency = bin * sampleRate.toDouble() / frameSize
                if (frequency < MIN_KEY_FREQUENCY || frequency > MAX_KEY_FREQUENCY) continue
                val magnitude = hypot(re[bin], im[bin])
                val midi = 69.0 + 12.0 * log2(frequency / 440.0)
                val pitchClass = ((midi.roundToInt() % 12) + 12) % 12
                chroma[pitchClass] += magnitude * magnitude
            }
        }

        val total = chroma.sum()
        if (total <= ENERGY_EPSILON) return null

        var bestScore = Double.NEGATIVE_INFINITY
        var bestRoot = 0
        var bestIsMinor = false
        for (root in 0 until 12) {
            var major = 0.0
            var minor = 0.0
            for (i in 0 until 12) {
                val value = chroma[(root + i) % 12]
                major += value * MAJOR_PROFILE[i]
                minor += value * MINOR_PROFILE[i]
            }
            if (major > bestScore) {
                bestScore = major
                bestRoot = root
                bestIsMinor = false
            }
            if (minor > bestScore) {
                bestScore = minor
                bestRoot = root
                bestIsMinor = true
            }
        }

        return Key(bestRoot, bestIsMinor)
    }

    private fun mixInPoint(
        samples: FloatArray,
        sampleRate: Int,
        durationMs: Long,
        bpm: Float,
        offsetMs: Long,
    ): Long? {
        if (durationMs <= 16_000L || sampleRate <= 0) return null
        val blockSamples = sampleRate * BLOCK_MS / 1000
        if (blockSamples <= 0) return null
        val blocks = min(samples.size / blockSamples, 32)
        if (blocks < 2) return null

        val energies = FloatArray(blocks) { index ->
            rms(samples, index * blockSamples, blockSamples)
        }
        val percentile = percentile(energies, 0.75)
        if (percentile <= 0f) return null
        if (energies[0] >= percentile) return null

        var found = -1
        for (index in 1 until blocks - 1) {
            if (energies[index] >= percentile && energies[index + 1] >= percentile) {
                found = index
                break
            }
        }
        if (found < 0) return null

        val bodyStartMs = found * BLOCK_MS.toLong()
        var point = bodyStartMs
        if (bpm > 0f) {
            val periodMs = 60_000.0 / bpm
            val beats = ceil((bodyStartMs - offsetMs) / periodMs)
            point = (offsetMs + beats * periodMs).roundToLong().coerceAtLeast(bodyStartMs)
        }
        if (point < 0L || point > 20_000L) return null
        return point
    }

    private fun mixOutPoint(
        samples: FloatArray,
        sampleRate: Int,
        startMs: Long,
        durationMs: Long,
    ): Long? {
        if (durationMs <= 24_000L || sampleRate <= 0) return null
        val blockSamples = sampleRate * BLOCK_MS / 1000
        if (blockSamples <= 0) return null
        val blocks = samples.size / blockSamples
        if (blocks < 2) return null

        val energies = FloatArray(blocks) { index ->
            rms(samples, index * blockSamples, blockSamples)
        }
        val percentile = percentile(energies, 0.75)
        if (percentile <= 0f) return null

        val threshold = percentile * 0.5f
        var lastLoud = -1
        for (index in energies.indices) {
            if (energies[index] >= threshold) lastLoud = index
        }
        if (lastLoud < 0) return null

        val endMs = startMs + (lastLoud + 1) * BLOCK_MS.toLong()
        if (endMs >= durationMs - 3_000L) return null
        return endMs
            .coerceAtLeast(durationMs - 45_000L)
            .coerceAtMost(durationMs)
    }

    private fun foldBpm(raw: Double): Double {
        var value = raw
        while (value >= MAX_BPM) value /= 2.0
        while (value < MIN_BPM) value *= 2.0
        return value
    }

    private fun rms(samples: FloatArray, start: Int, count: Int): Float {
        if (count <= 0) return 0f
        var sum = 0.0
        val end = min(start + count, samples.size)
        for (i in start until end) {
            val value = samples[i].toDouble()
            sum += value * value
        }
        return kotlin.math.sqrt(sum / count).toFloat()
    }

    private fun percentile(values: FloatArray, fraction: Double): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.sortedArray()
        val index = ((sorted.size - 1) * fraction).roundToInt().coerceIn(0, sorted.size - 1)
        return sorted[index]
    }

    private fun fft(re: DoubleArray, im: DoubleArray) {
        val size = re.size
        var j = 0
        for (i in 1 until size) {
            var bit = size shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tempRe = re[i]
                re[i] = re[j]
                re[j] = tempRe
                val tempIm = im[i]
                im[i] = im[j]
                im[j] = tempIm
            }
        }

        var length = 2
        while (length <= size) {
            val angle = -2.0 * PI / length
            val unitRe = cos(angle)
            val unitIm = sin(angle)
            var offset = 0
            while (offset < size) {
                var curRe = 1.0
                var curIm = 0.0
                for (k in 0 until length / 2) {
                    val evenRe = re[offset + k]
                    val evenIm = im[offset + k]
                    val oddRe = re[offset + k + length / 2] * curRe -
                        im[offset + k + length / 2] * curIm
                    val oddIm = re[offset + k + length / 2] * curIm +
                        im[offset + k + length / 2] * curRe
                    re[offset + k] = evenRe + oddRe
                    im[offset + k] = evenIm + oddIm
                    re[offset + k + length / 2] = evenRe - oddRe
                    im[offset + k + length / 2] = evenIm - oddIm
                    val nextRe = curRe * unitRe - curIm * unitIm
                    curIm = curRe * unitIm + curIm * unitRe
                    curRe = nextRe
                }
                offset += length
            }
            length = length shl 1
        }
    }

    private fun durationMillis(extractor: MediaExtractor): Long {
        val track = audioTrack(extractor) ?: return -1L
        val format = extractor.getTrackFormat(track)
        if (!format.containsKey(MediaFormat.KEY_DURATION)) return -1L
        val durationUs = try {
            format.getLong(MediaFormat.KEY_DURATION)
        } catch (e: Throwable) {
            return -1L
        }
        return if (durationUs > 0L) durationUs / 1000L else -1L
    }

    private fun audioTrack(extractor: MediaExtractor): Int? {
        val count = try {
            extractor.trackCount
        } catch (e: Throwable) {
            return null
        }
        for (index in 0 until count) {
            val format = extractor.getTrackFormat(index)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: continue
            if (mime.startsWith("audio/")) return index
        }
        return null
    }

    private fun decodeWindow(
        openExtractor: () -> MediaExtractor?,
        startUs: Long,
        windowUs: Long,
        shouldCancel: () -> Boolean,
    ): Segment? {
        val extractor = try {
            openExtractor()
        } catch (e: Throwable) {
            null
        } ?: return null
        return try {
            decodeFromExtractor(extractor, startUs, windowUs, shouldCancel)
        } catch (e: Throwable) {
            null
        } finally {
            runCatching { extractor.release() }
        }
    }

    private fun decodeFromExtractor(
        extractor: MediaExtractor,
        startUs: Long,
        windowUs: Long,
        shouldCancel: () -> Boolean,
    ): Segment? {
        val track = audioTrack(extractor) ?: return null
        val format = extractor.getTrackFormat(track)
        val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
        val inputRate = integerOr(format, MediaFormat.KEY_SAMPLE_RATE, 0)
        val inputChannels = integerOr(format, MediaFormat.KEY_CHANNEL_COUNT, 0)
        if (inputRate <= 0 || inputChannels <= 0) return null

        val codec = try {
            MediaCodec.createDecoderByType(mime)
        } catch (e: Throwable) {
            return null
        }

        try {
            codec.configure(format, null, null, 0)
            codec.start()
            extractor.selectTrack(track)
            if (startUs > 0L) extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)

            val info = MediaCodec.BufferInfo()
            val collected = ByteArrayOutputStream()
            var inputDone = false
            var outputDone = false
            var outputRate = inputRate
            var outputChannels = inputChannels
            var outputEncoding = C.ENCODING_PCM_16BIT
            val maxFrames = if (windowUs >= Long.MAX_VALUE / 2) {
                60L * inputRate
            } else {
                (windowUs / 1_000_000.0 * inputRate).toLong()
            }

            while (!outputDone) {
                if (shouldCancel()) return null
                if (!inputDone) {
                    val inputIndex = codec.dequeueInputBuffer(10_000L)
                    if (inputIndex >= 0) {
                        val input = codec.getInputBuffer(inputIndex)
                        if (input == null) {
                            inputDone = true
                        } else {
                            val size = extractor.readSampleData(input, 0)
                            if (size < 0) {
                                codec.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    0,
                                    0L,
                                    MediaCodec.BUFFER_FLAG_END_OF_STREAM,
                                )
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(
                                    inputIndex,
                                    0,
                                    size,
                                    extractor.sampleTime,
                                    0,
                                )
                                extractor.advance()
                            }
                        }
                    }
                }

                val outputIndex = codec.dequeueOutputBuffer(info, 10_000L)
                if (outputIndex == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    val outputFormat = codec.outputFormat
                    outputRate = integerOr(outputFormat, MediaFormat.KEY_SAMPLE_RATE, outputRate)
                    outputChannels = integerOr(
                        outputFormat,
                        MediaFormat.KEY_CHANNEL_COUNT,
                        outputChannels,
                    )
                    outputEncoding = integerOr(
                        outputFormat,
                        MediaFormat.KEY_PCM_ENCODING,
                        outputEncoding,
                    )
                } else if (outputIndex >= 0) {
                    val output = codec.getOutputBuffer(outputIndex)
                    if (output != null && info.size > 0) {
                        output.position(info.offset)
                        output.limit(info.offset + info.size)
                        val chunk = ByteArray(info.size)
                        output.get(chunk)
                        collected.write(chunk)
                    }
                    codec.releaseOutputBuffer(outputIndex, false)
                    if (info.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) {
                        outputDone = true
                    }
                    if (frameCount(collected.size(), outputEncoding, outputChannels) >= maxFrames) {
                        outputDone = true
                    }
                }
            }

            val samples = pcmToMono(collected.toByteArray(), outputEncoding, outputChannels)
            return Segment(samples, outputRate)
        } finally {
            runCatching { codec.stop() }
            runCatching { codec.release() }
        }
    }

    private fun frameCount(bytes: Int, encoding: Int, channels: Int): Long {
        if (channels <= 0) return 0L
        val bytesPerSample = if (encoding == C.ENCODING_PCM_FLOAT) 4 else 2
        return bytes.toLong() / (bytesPerSample.toLong() * channels)
    }

    private fun pcmToMono(bytes: ByteArray, encoding: Int, channels: Int): FloatArray {
        if (channels <= 0 || bytes.isEmpty()) return FloatArray(0)
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.nativeOrder())
        return if (encoding == C.ENCODING_PCM_FLOAT) {
            val floats = buffer.asFloatBuffer()
            val frames = floats.remaining() / channels
            FloatArray(frames) { frame ->
                var sum = 0f
                for (channel in 0 until channels) {
                    sum += floats.get(frame * channels + channel)
                }
                sum / channels
            }
        } else {
            val shorts = buffer.asShortBuffer()
            val frames = shorts.remaining() / channels
            FloatArray(frames) { frame ->
                var sum = 0
                for (channel in 0 until channels) {
                    sum += shorts.get(frame * channels + channel).toInt()
                }
                (sum.toFloat() / channels) / 32768f
            }
        }
    }

    private fun integerOr(format: MediaFormat, key: String, fallback: Int): Int =
        try {
            if (format.containsKey(key)) format.getInteger(key) else fallback
        } catch (e: Throwable) {
            fallback
        }

    private fun extractorForFile(file: File): MediaExtractor? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(file.absolutePath)
            extractor
        } catch (e: Throwable) {
            runCatching { extractor.release() }
            null
        }
    }

    private fun extractorForUri(context: Context, uri: Uri): MediaExtractor? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            extractor
        } catch (e: Throwable) {
            runCatching { extractor.release() }
            null
        }
    }

    private const val FFT_SIZE = 1024
    private const val FFT_HOP = 512
    private const val KEY_FFT_SIZE = 4096
    private const val KEY_FFT_HOP = 2048
    private const val MIN_TEMPO_FRAMES = 16
    private const val MIN_DECODED_SAMPLES = 1024 * 8
    private const val MIN_BPM = 70.0
    private const val MAX_BPM = 140.0
    private const val BLOCK_MS = 500
    private const val MIDDLE_WINDOW_HALF_US = 9_000_000L
    private const val MIDDLE_WINDOW_US = 18_000_000L
    private const val UNKNOWN_DURATION_WINDOW_US = 60_000_000L
    private const val MIN_KEY_FREQUENCY = 65.0
    private const val MAX_KEY_FREQUENCY = 2000.0
    private const val ENERGY_EPSILON = 1e-6
    private const val MAX_FETCH_BYTES = 5L * 1024 * 1024
    private const val MIN_FETCH_BYTES = 256L * 1024
    private const val READ_BUFFER_BYTES = 64 * 1024

    private val MAJOR_PROFILE = doubleArrayOf(
        6.35, 2.23, 3.48, 2.33, 4.38, 4.09, 2.52, 5.19, 2.39, 3.66, 2.29, 2.88,
    )
    private val MINOR_PROFILE = doubleArrayOf(
        6.33, 2.68, 3.52, 5.38, 2.60, 3.53, 2.54, 4.75, 3.98, 2.69, 3.34, 3.17,
    )
}
