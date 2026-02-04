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
        thresholdDb = -1.0,    // Ceiling at -1dB
        ratio = 20.0,          // Brickwall ratio
        kneeDb = 0.0,
        attackSeconds = 0.001, // 1ms allows transients to retain punch before clamping
        releaseSeconds = 0.1,
    )

    fun renderBlock(
        cursorFrame: Long,
        out: ShortArray,
    ) {
        // 1. Reset Mix Buffers
        mix.clear()
        orbits.clearAll()

        // 2. Process Voices
        voices.process(cursorFrame)

        // 3. Mix Voices into Orbits -> Main Mix
        orbits.processAndMix(mix)

        // 4. Apply dynamic limiter
        // This handles the bulk of the loudness management musically
        limiter.process(mix.left, mix.right, blockFrames)

        // 5. Post-Process (Transparent Clip + Interleave)
        val left = mix.left
        val right = mix.right

        // Cache max value to avoid repeated field access/casting
        val maxShort = Short.MAX_VALUE.toDouble()

        for (i in 0 until blockFrames) {
            val lSample = left[i]
            val rSample = right[i]

            // OPTIMIZATION: Branching Logic
            // Most samples are within safe bounds [-1.0, 1.0].
            // We skip all math for them to preserve CPU and Transparency (Unity Gain).

            val lOut = if (lSample >= -1.0 && lSample <= 1.0) {
                (lSample * maxShort).toInt()
            } else if (lSample > 1.0) {
                Short.MAX_VALUE.toInt()
            } else {
                Short.MIN_VALUE.toInt()
            }

            val rOut = if (rSample >= -1.0 && rSample <= 1.0) {
                (rSample * maxShort).toInt()
            } else if (rSample > 1.0) {
                Short.MAX_VALUE.toInt()
            } else {
                Short.MIN_VALUE.toInt()
            }

            // Interleave
            val idx = i * 2
            out[idx] = lOut.toShort()
            out[idx + 1] = rOut.toShort()
        }
    }
}
