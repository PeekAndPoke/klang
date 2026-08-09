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
     * The house limiter character: what runs on the summed mix, always.
     *
     * `MasterStageDsl.Limiter` (the *opt-in*, per-playback stage) shares threshold, ratio, knee and
     * release, but has its own `AUTHORED_*` constants for attack and lookahead — see those below for
     * why they must differ. `MasterDefaultsSyncSpec` asserts both the shared values and the
     * divergence, so neither drifts by accident.
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

        /**
         * Lookahead: the limiter delays the mix by this much so it can start closing the gain
         * *before* a transient arrives. Without it the limiter contributes ~0 dB during a transient
         * and the hard clip below does the work — 2-6 ms of clipping per hit, audible as a "knock".
         *
         * Uniform on the whole output (this stage runs once, after every playback is summed), so the
         * delay shifts everything together and nothing can desync. That is why lookahead lives here
         * and not on a per-orbit or per-playback compressor.
         *
         * See `docs/tasks/master-limiter-lookahead.md`.
         */
        const val LIMITER_LOOKAHEAD_SECONDS: Double = 0.005

        /**
         * The gain-smoothing length — how fast the gain closes inside the lookahead window.
         *
         * Equal to the window: peak performance is *invariant* to this value (the min-hold does the
         * anticipating), while low-frequency cleanliness tracks it directly, so at a fixed latency
         * more smoothing is strictly better. It also collapses the "flat bottom" of the gain dip,
         * which is the pre-duck people hear as the mix flinching ahead of a kick.
         */
        const val LIMITER_ATTACK_SECONDS: Double = 0.005

        const val LIMITER_RELEASE_SECONDS: Double = 0.1

        // ── Authored-limiter defaults (the opt-in `MasterFx.limiter()` stage) ────────────────
        // These deliberately DIFFER from the house constants above, and the difference is the
        // point: that stage is per-playback, upstream of the summed mix.

        /**
         * **0, on purpose.** An authored master limiter lives on one playback's `MasterBus`, so any
         * lookahead there would delay that playback against every other one — the same desync that
         * keeps lookahead off per-orbit compressors. Latency is opt-in per author, never a default.
         */
        const val AUTHORED_LIMITER_LOOKAHEAD_SECONDS: Double = 0.0

        /**
         * **The one-pole attack**, not a smoothing length: with no lookahead the authored limiter
         * takes the classic path, where 1 ms is what lets transients keep their punch.
         */
        const val AUTHORED_LIMITER_ATTACK_SECONDS: Double = 0.001
    }

    /**
     * Latency this stage adds, in frames — the limiter's lookahead delay.
     *
     * The whole output is delayed by this, uniformly, so nothing desyncs *within* the audio. But it
     * is invisible to `AudioContext.outputLatency` (it happens inside the worklet, downstream of the
     * clock), so anything aligning visuals to audio has to add it explicitly. See
     * `docs/tasks/master-limiter-lookahead.md` Phase 5.
     */
    val latencyFrames: Int get() = limiter.latencyFrames

    /** [latencyFrames] in milliseconds — the unit the FE latency budget works in. */
    val latencyMs: Double get() = limiter.latencyMs

    private val limiter = Compressor(
        sampleRate = sampleRate,
        thresholdDb = LIMITER_THRESHOLD_DB,
        ratio = LIMITER_RATIO,
        kneeDb = LIMITER_KNEE_DB,
        attackSeconds = LIMITER_ATTACK_SECONDS,
        releaseSeconds = LIMITER_RELEASE_SECONDS,
        lookaheadSeconds = LIMITER_LOOKAHEAD_SECONDS,
    )

    // Master-out DC blockers. ~7 Hz cutoff (coefficient = 0.999 at 44.1k / ~7.6 Hz at 48k).
    //
    // Run BEFORE the limiter, so the LIMITER is what feeds the clip below. Two reasons, and the
    // second is why this is required rather than tidy:
    //   1. DC eats headroom asymmetrically and inflates what the detector sees, so removing it
    //      first is simply the correct order.
    //   2. A DC blocker is NOT level-safe. It is a ~7 Hz high-pass whose pole transient overshoots
    //      on onsets — measured, a limited (-1 dBFS) 55 Hz sine switched on cold comes out at
    //      -0.47 dBFS, a +0.53 dB gain decaying over ~21 ms. Downstream of the limiter that
    //      overshoot lands straight on the clip and eats most of the margin the limiter leaves.
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
        // Master-out DC blockers (per channel, in-place), ahead of the limiter — see above.
        dcBlockerL.process(mix.left, 0, blockFrames)
        dcBlockerR.process(mix.right, 0, blockFrames)

        // Apply dynamic limiter — handles the bulk of loudness management musically. With lookahead
        // it also delays the mix by LIMITER_LOOKAHEAD_SECONDS; uniform, so nothing desyncs.
        limiter.process(mix.left, mix.right, blockFrames)

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
