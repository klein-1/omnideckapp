package com.example.services.sound

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Generates tiny 8-bit square-wave UI blips (off by default; switchable in Settings -> Look).
 */
object PixelSoundEngine {
    private val scope = CoroutineScope(Dispatchers.Default)

    fun playBlip(enabled: Boolean, freqHz: Int = 660, durationMs: Int = 22) {
        if (!enabled) return
        scope.launch {
            try {
                val sampleRate = 22050
                val numSamples = (sampleRate * durationMs) / 1000
                val buffer = ShortArray(numSamples)
                val period = (sampleRate / freqHz.coerceAtLeast(120)).coerceAtLeast(2)
                for (i in 0 until numSamples) {
                    val env = 1f - (i.toFloat() / numSamples.toFloat())
                    val amp = (2800 * env).toInt().toShort()
                    buffer[i] = if ((i % period) < (period / 2)) amp else (-amp).toShort()
                }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(buffer.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(buffer, 0, buffer.size)
                track.play()
                Thread.sleep((durationMs + 15).toLong())
                track.release()
            } catch (_: Throwable) {
                // Ignore audio hardware unavailability in headless/unit environments
            }
        }
    }
}
