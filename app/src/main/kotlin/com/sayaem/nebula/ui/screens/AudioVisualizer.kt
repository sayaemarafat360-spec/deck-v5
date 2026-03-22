package com.sayaem.nebula.ui.screens

import android.media.audiofx.Visualizer
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.unit.dp
import com.sayaem.nebula.ui.theme.*
import kotlinx.coroutines.*

/**
 * Real-time audio visualizer using Android's Visualizer API.
 * Captures actual FFT data from the audio output and renders it as animated bars.
 * This is the real frequency response of whatever is playing — not fake animation.
 */
@Composable
fun AudioVisualizer(
    audioSessionId: Int,
    modifier: Modifier = Modifier,
    barCount: Int = 32,
    color1: Color = NebulaViolet,
    color2: Color = NebulaPink,
) {
    // FFT magnitudes — updated by Visualizer callback on audio thread
    val magnitudes = remember { mutableStateOf(FloatArray(barCount) { 0f }) }

    // Create and manage the Visualizer
    DisposableEffect(audioSessionId) {
        if (audioSessionId == 0) return@DisposableEffect onDispose {}

        var visualizer: Visualizer? = null
        try {
            visualizer = Visualizer(audioSessionId).apply {
                captureSize = Visualizer.getCaptureSizeRange()[1]  // max size
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(v: Visualizer, waveform: ByteArray, samplingRate: Int) {}

                        override fun onFftDataCapture(v: Visualizer, fft: ByteArray, samplingRate: Int) {
                            // FFT data: fft[0] = DC, fft[1] = Nyquist, rest = complex pairs
                            // Extract magnitudes from complex FFT output
                            val bars = FloatArray(barCount)
                            val bucketSize = (fft.size / 2) / barCount
                            for (i in 0 until barCount) {
                                var sum = 0f
                                val start = i * bucketSize + 1
                                val end   = minOf(start + bucketSize, fft.size / 2)
                                for (j in start until end) {
                                    val real = fft[j * 2].toFloat()
                                    val imag = if (j * 2 + 1 < fft.size) fft[j * 2 + 1].toFloat() else 0f
                                    sum += kotlin.math.sqrt(real * real + imag * imag)
                                }
                                // Normalize to 0..1, with some boost for visibility
                                bars[i] = (sum / (bucketSize * 128f)).coerceIn(0f, 1f)
                            }
                            magnitudes.value = bars
                        }
                    },
                    16000, // 16kHz capture rate
                    false,
                    true   // FFT
                )
                enabled = true
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        onDispose {
            try { visualizer?.enabled = false; visualizer?.release() } catch (_: Exception) {}
        }
    }

    // Smooth the bars with Compose animation
    val smoothedBars = remember(barCount) { Array(barCount) { Animatable(0f) } }
    val mags = magnitudes.value

    LaunchedEffect(mags) {
        mags.forEachIndexed { i, target ->
            launch {
                smoothedBars[i].animateTo(
                    target,
                    animationSpec = tween(80, easing = FastOutLinearInEasing)
                )
            }
        }
    }

    Canvas(modifier = modifier) {
        val barWidth  = size.width / barCount
        val maxHeight = size.height * 0.9f

        smoothedBars.forEachIndexed { i, anim ->
            val barH    = maxHeight * anim.value.coerceAtLeast(0.04f)
            val x       = i * barWidth + barWidth * 0.15f
            val w       = barWidth * 0.7f
            val centerY = size.height / 2f

            // Gradient from color1 to color2 across the bar width
            val t     = i.toFloat() / barCount
            val color = lerp(color1, color2, t)

            // Draw bar (centered vertically — grows up and down from center)
            drawRoundRect(
                color        = color.copy(alpha = 0.7f + anim.value * 0.3f),
                topLeft      = Offset(x, centerY - barH / 2),
                size         = Size(w, barH),
                cornerRadius = CornerRadius(w / 2)
            )
        }
    }
}
