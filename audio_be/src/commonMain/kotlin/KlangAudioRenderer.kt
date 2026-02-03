package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.effects.Compressor
import io.peekandpoke.klang.audio_be.orbits.Orbits
import io.peekandpoke.klang.audio_be.voices.VoiceScheduler

/**
 * Standard renderer that drives the DSP graph (VoiceScheduler -> Orbits -> Stereo Output).
 *
 * This class is stateless regarding the playback cursor. It simply renders "what is requested".
 */
class KlangAudioRenderer(
    sampleRate: Int,
    private val blockFrames: Int,
    private val voices: VoiceScheduler,
    private val orbits: Orbits,
) {
    private val mix = StereoBuffer(blockFrames)

    private val limiter = Compressor(
        sampleRate = sampleRate,
        thresholdDb = -1.0,    // Very hot threshold (brick-wall limiting)
        ratio = 20.0,           // 20:1 ratio (hard limiting)
        kneeDb = 0.0,           // No knee (instant response)
        attackSeconds = 0.001,  // 1ms attack (fast response)
        releaseSeconds = 0.1,   // 100ms release
    )

    /**
     * Fast soft clipping approximation (replaces expensive tanh).
     * Since the limiter now handles dynamics, this is only a safety clipper for rare peaks.
     *
     * Uses rational function: x * (27 + x²) / (27 + 9x²)
     * - Fast (no transcendental functions)
     * - Approaches ±1 asymptotically
     * - Good enough for final safety clipping
     */
    private fun fastSoftClip(x: Double): Double {
        if (x < -3.0) return -1.0
        if (x > 3.0) return 1.0
        val x2 = x * x
        return x * (27.0 + x2) / (27.0 + 9.0 * x2)
    }

    /**
     * Renders one block of audio starting at [cursorFrame] into the [out] short array.
     */
    fun renderBlock(
        cursorFrame: Long,
        out: ShortArray,
    ) {
        // 1. Reset Mix Buffers
        mix.clear()
        orbits.clearAll()

        // 2. Process Voices (Oscillators, Samples, Envelopes)
        voices.process(cursorFrame)

        // 3. Mix Voices into Orbits -> Main Mix
        orbits.processAndMix(mix)

        // 4. Apply dynamic limiter BEFORE conversion (prevents distortion)
        limiter.process(mix.left, mix.right, blockFrames)

        // 5. Post-Process (Safety Clipper + Interleave to 16-bit PCM)
        val left = mix.left
        val right = mix.right

        for (i in 0 until blockFrames) {
            // Safety clipper (fast approximation - rarely triggered thanks to limiter)
            val l = (fastSoftClip(left[i]).coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
            val r = (fastSoftClip(right[i]).coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()

            // Interleave L/R into ShortArray
            val idx = i * 2
            out[idx] = l
            out[idx + 1] = r
        }
    }
}
