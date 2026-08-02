/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.effects.Compressor
import io.peekandpoke.klang.audio_be.filters.LowPassHighPassFilters

/**
 * The final master / output stage: a brick-wall safety limiter, master-out DC blockers, and the
 * transparent clip + stereo interleave into the platform's 16-bit PCM buffer.
 *
 * Extracted from [KlangAudioRenderer] so the per-playback mixdown can run it **once** on the summed
 * mix — the safety brick belongs on the final output, not per engine. See
 * `docs/tasks/per-playback-engine.md` (D2). Behaviour is identical to the old inline post-chain.
 */
class MasterStage(
    sampleRate: Int,
    private val blockFrames: Int,
) {
    /**
     * The house limiter character. Also the defaults of the *opt-in* `MasterStageDsl.Limiter`
     * stage (a user-authored master chain) — kept in sync by `MasterDefaultsSyncSpec`.
     */
    companion object {
        /** Ceiling at -1 dB. */
        const val LIMITER_THRESHOLD_DB: Double = -1.0

        /** Brickwall ratio. */
        const val LIMITER_RATIO: Double = 20.0

        /**
         * 2 dB soft knee — 2026-04-30 fix for britzeling on heavily-distorted content.
         * With kneeDb=0 the gain curve had a C¹ kink at the threshold corner; every
         * envelope crossing of -1 dBFS injected high-order harmonics at audio rate.
         * The 2 dB knee makes the corner smooth without changing the brickwall character.
         */
        const val LIMITER_KNEE_DB: Double = 2.0

        /** 1 ms allows transients to retain punch before clamping. */
        const val LIMITER_ATTACK_SECONDS: Double = 0.001

        const val LIMITER_RELEASE_SECONDS: Double = 0.1
    }

    private val limiter = Compressor(
        sampleRate = sampleRate,
        thresholdDb = LIMITER_THRESHOLD_DB,
        ratio = LIMITER_RATIO,
        kneeDb = LIMITER_KNEE_DB,
        attackSeconds = LIMITER_ATTACK_SECONDS,
        releaseSeconds = LIMITER_RELEASE_SECONDS,
    )

    // Master-out DC blockers. ~7 Hz cutoff (coefficient = 0.999 at 44.1k / ~7.6 Hz at 48k).
    // Run AFTER the limiter so input is already ±1-bounded — no rail-edge transient, no need
    // for downstream softCap. Removes any DC bias from the mix that would otherwise eat headroom
    // asymmetrically through the clip-and-interleave step.
    private val dcBlockerL = LowPassHighPassFilters.DcBlocker(coefficient = 0.999)
    private val dcBlockerR = LowPassHighPassFilters.DcBlocker(coefficient = 0.999)

    /**
     * Clears stateful post-chain elements (limiter envelope + DC blocker IIR state). Used at the
     * end of the warmup handshake so post-chain state does not survive into the first real block.
     */
    fun reset() {
        limiter.reset()
        dcBlockerL.reset()
        dcBlockerR.reset()
    }

    /**
     * Applies the master limiter + DC block to [mix] in place, then writes a transparent
     * clip + stereo interleave into [out] (which must hold `2 * blockFrames` shorts).
     */
    fun process(mix: StereoBuffer, out: ShortArray) {
        // Apply dynamic limiter — handles the bulk of loudness management musically.
        limiter.process(mix.left, mix.right, blockFrames)

        // Master-out DC blockers (per channel, in-place). Input is post-limiter (already ±1).
        dcBlockerL.process(mix.left, 0, blockFrames)
        dcBlockerR.process(mix.right, 0, blockFrames)

        // Transparent clip + interleave. Most samples are within [-1, 1]; we skip all math for
        // them to preserve CPU and unity-gain transparency.
        val left = mix.left
        val right = mix.right
        val maxShort = Short.MAX_VALUE

        for (i in 0 until blockFrames) {
            val lSample = left[i]
            val rSample = right[i]

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

            val idx = i * 2
            out[idx] = lOut.toShort()
            out[idx + 1] = rOut.toShort()
        }
    }
}
