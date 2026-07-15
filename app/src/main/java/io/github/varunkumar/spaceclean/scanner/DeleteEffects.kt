package io.github.varunkumar.spaceclean.scanner

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Plays a short, subtle "swoosh" confirmation after a successful delete.
 *
 * The tone is synthesized on the fly (a soft 1.1 kHz → 260 Hz sine sweep with an
 * exponential fade, ~200 ms), so the app ships no audio asset and stays 100% offline.
 * Uses USAGE_ASSISTANCE_SONIFICATION so it respects the system's sound settings and
 * never ducks the user's music for long.
 */
object DeleteEffects {

    private const val SAMPLE_RATE = 44_100
    private const val DURATION_MS = 200
    private const val VOLUME      = 0.30

    @Volatile private var pcm: ShortArray? = null

    private fun buildPcm(): ShortArray {
        val n   = SAMPLE_RATE * DURATION_MS / 1_000
        val out = ShortArray(n)
        var phase = 0.0
        for (i in 0 until n) {
            val t    = i.toDouble() / n                          // 0..1
            val freq = 1_100.0 * exp(-1.45 * t)                  // sweep down to ~260 Hz
            phase   += 2.0 * PI * freq / SAMPLE_RATE
            val env  = exp(-4.2 * t) * (if (i < 128) i / 128.0 else 1.0)  // fade + declick
            out[i]   = (sin(phase) * env * VOLUME * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    /** Fire-and-forget; safe from any thread. Failures are ignored (sound is cosmetic). */
    fun playDeleteSound() {
        thread(isDaemon = true, name = "delete-sfx") {
            runCatching {
                val samples = pcm ?: buildPcm().also { pcm = it }
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(samples.size * 2)
                    .build()
                track.write(samples, 0, samples.size)
                track.play()
                Thread.sleep(DURATION_MS + 80L)
                track.release()
            }
        }
    }
}
