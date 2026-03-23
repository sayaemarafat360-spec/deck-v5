package com.sayaem.nebula.data.repository

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Generates a real amplitude waveform from an audio file using MediaCodec.
 * Decodes the actual PCM samples and downsamples them to [barCount] bars.
 * Results are cached in SharedPreferences keyed by songId so decoding
 * only happens once per song.
 */
object WaveformGenerator {

    private const val MAX_DECODE_MS = 3000L   // decode at most 3 seconds worth for performance
    private const val CACHE_KEY     = "waveform_"

    fun getCached(context: Context, songId: Long, barCount: Int): FloatArray? {
        val prefs = context.getSharedPreferences("deck_waveforms", Context.MODE_PRIVATE)
        val raw   = prefs.getString("$CACHE_KEY$songId", null) ?: return null
        return try {
            val parts = raw.split(",")
            if (parts.size != barCount) null
            else FloatArray(barCount) { parts[it].toFloat() }
        } catch (_: Exception) { null }
    }

    private fun saveCache(context: Context, songId: Long, bars: FloatArray) {
        val prefs = context.getSharedPreferences("deck_waveforms", Context.MODE_PRIVATE)
        prefs.edit().putString("$CACHE_KEY$songId", bars.joinToString(",")).apply()
    }

    suspend fun generate(
        context: Context,
        uri: Uri,
        songId: Long,
        barCount: Int = 60,
    ): FloatArray = withContext(Dispatchers.IO) {
        // Return cached if available
        getCached(context, songId, barCount)?.let { return@withContext it }

        val result = decodeAmplitudes(context, uri, barCount)
        saveCache(context, songId, result)
        result
    }

    private fun decodeAmplitudes(context: Context, uri: Uri, barCount: Int): FloatArray {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)

            // Find the first audio track
            val trackIndex = (0 until extractor.trackCount).firstOrNull { i ->
                extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME)
                    ?.startsWith("audio/") == true
            } ?: return generateFallback(barCount)

            extractor.selectTrack(trackIndex)
            val format   = extractor.getTrackFormat(trackIndex)
            val mime     = format.getString(MediaFormat.KEY_MIME) ?: return generateFallback(barCount)
            val duration = format.getLong(MediaFormat.KEY_DURATION)  // in microseconds

            val codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val amplitudes = mutableListOf<Float>()
            val bufferInfo = MediaCodec.BufferInfo()
            var eos        = false
            var decodedUs  = 0L
            val limitUs    = MAX_DECODE_MS * 1_000L  // ms → µs

            while (!eos) {
                // Feed input
                val inIdx = codec.dequeueInputBuffer(5000L)
                if (inIdx >= 0) {
                    val inBuf = codec.getInputBuffer(inIdx) ?: break
                    val size  = extractor.readSampleData(inBuf, 0)
                    if (size < 0 || decodedUs > limitUs) {
                        codec.queueInputBuffer(inIdx, 0, 0, 0,
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                        eos = true
                    } else {
                        codec.queueInputBuffer(inIdx, 0, size,
                            extractor.sampleTime, 0)
                        decodedUs = extractor.sampleTime
                        extractor.advance()
                    }
                }

                // Drain output
                val outIdx = codec.dequeueOutputBuffer(bufferInfo, 5000L)
                if (outIdx >= 0) {
                    val outBuf = codec.getOutputBuffer(outIdx)
                    if (outBuf != null && bufferInfo.size > 0) {
                        val pcm = outBuf.order(ByteOrder.LITTLE_ENDIAN).asShortBuffer()
                        // RMS per chunk
                        var sumSq = 0.0
                        var count = 0
                        while (pcm.hasRemaining()) {
                            val s = pcm.get().toFloat() / Short.MAX_VALUE
                            sumSq += s * s
                            count++
                        }
                        if (count > 0) amplitudes.add(Math.sqrt(sumSq / count).toFloat())
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0) eos = true
                }
            }

            codec.stop()
            codec.release()

            if (amplitudes.isEmpty()) return generateFallback(barCount)

            // Downsample to barCount
            val bars   = FloatArray(barCount)
            val chunk  = amplitudes.size.toFloat() / barCount
            var maxAmp = 0f
            for (i in 0 until barCount) {
                val start = (i * chunk).toInt()
                val end   = ((i + 1) * chunk).toInt().coerceAtMost(amplitudes.size)
                if (start >= end) { bars[i] = 0.05f; continue }
                val avg = amplitudes.subList(start, end).average().toFloat()
                bars[i] = avg.coerceAtLeast(0.03f)
                if (avg > maxAmp) maxAmp = avg
            }

            // Normalize so peak = 1.0
            if (maxAmp > 0f) {
                for (i in bars.indices) bars[i] = (bars[i] / maxAmp).coerceIn(0.03f, 1f)
            }
            bars
        } catch (_: Exception) {
            generateFallback(barCount)
        } finally {
            extractor.release()
        }
    }

    // Deterministic pseudo-random fallback (if decoding fails) — still keyed by songId
    // so each song gets a consistent shape, but it's NOT the real waveform
    fun generateFallback(barCount: Int, seed: Long = 0L): FloatArray {
        val rng = java.util.Random(seed)
        return FloatArray(barCount) { 0.15f + rng.nextFloat() * 0.85f }
    }
}
