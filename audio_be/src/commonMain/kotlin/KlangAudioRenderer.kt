package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.cylinders.Cylinders
import io.peekandpoke.klang.audio_be.effects.Compressor
import io.peekandpoke.klang.audio_be.filters.LowPassHighPassFilters
import io.peekandpoke.klang.audio_be.voices.VoiceScheduler

/**
 * Standard renderer that drives the DSP graph (VoiceScheduler -> Cylinders -> Stereo Output).
 *
 * This class is stateless regarding the playback cursor. It simply renders "what is requested".
 */
class KlangAudioRenderer(
    sampleRate: Int,
    private val blockFrames: Int,
    private val voices: VoiceScheduler,
    private val cylinders: Cylinders,
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

    // Master-out DC blockers. ~7 Hz cutoff (coefficient = 0.999 at 44.1k / ~7.6 Hz at 48k).
    // Run AFTER the limiter so input is already ±1-bounded — no rail-edge transient,
    // no need for downstream softCap. Removes any DC bias from the cylinder mix that
    // would otherwise eat headroom asymmetrically through the clip-and-interleave step.
    // Added 2026-04-29; see filters/LowPassHighPassFilters.kt header for DcBlocker history.
    private val dcBlockerL = LowPassHighPassFilters.DcBlocker(coefficient = 0.999)
    private val dcBlockerR = LowPassHighPassFilters.DcBlocker(coefficient = 0.999)

    /**
     * Clears stateful post-chain elements (limiter envelope + DC blocker IIR state).
     * Used at the end of the warmup handshake so post-chain state does not survive
     * into the first real playback block.
     */
    fun resetPostChain() {
        limiter.reset()
        dcBlockerL.reset()
        dcBlockerR.reset()
    }

    /**
     * Pre-allocates every cylinder up to the configured `maxCylinders`, so the first
     * note of a song that touches a new orbit doesn't pay the allocation cost during
     * its audio block. Called from the warmup handshake.
     */
    fun preallocateCylinders() {
        cylinders.preallocateAll()
    }

    fun renderBlock(
        cursorFrame: Int,
        out: ShortArray,
    ) {
        // 1. Reset Mix Buffers
        mix.clear()
        cylinders.clearAll()

        // 2. Process Voices
        voices.process(cursorFrame)

        // 3. Mix Voices into Cylinders -> Main Mix
        cylinders.processAndMix(mix)

        // 4. Apply dynamic limiter
        // This handles the bulk of the loudness management musically
        limiter.process(mix.left, mix.right, blockFrames)

        // 5. Master-out DC blockers (per channel, in-place).
        // Strips DC bias accumulated through the cylinder mix so the clip-and-interleave
        // below has full ±1 headroom symmetrically. Input is post-limiter (already ±1),
        // so no rail-edge transient and no softCap needed.
        dcBlockerL.process(mix.left, 0, blockFrames)
        dcBlockerR.process(mix.right, 0, blockFrames)

        // 6. Post-Process (Transparent Clip + Interleave)
        val left = mix.left
        val right = mix.right

        // Cache max value to avoid repeated field access/casting
        val maxShort = Short.MAX_VALUE

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
