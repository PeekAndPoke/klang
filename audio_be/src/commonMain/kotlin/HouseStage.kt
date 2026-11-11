/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.effects.Compressor
import io.peekandpoke.klang.audio_be.filters.LowPassHighPassFilters
import io.peekandpoke.klang.audio_be.filters.houseDcBlockCoeff
import io.peekandpoke.klang.audio_bridge.constants.LIMITER_KNEE_DB
import io.peekandpoke.klang.audio_bridge.constants.LIMITER_RATIO
import io.peekandpoke.klang.audio_bridge.constants.LIMITER_RELEASE_SECONDS
import io.peekandpoke.klang.audio_bridge.constants.LIMITER_THRESHOLD_DB

/**
 * The house stage: the always-on output stage after every playback is summed. Output DC blockers, a
 * brick-wall safety limiter, and the transparent clip into the engine's floating-point stereo output.
 * Nothing authors it. Named `MasterStage` until 2026-11-06 (KLANG-00290 (engine follow ups) item 27, D2):
 * "master" is the authored chain at a playback's output, `MasterBus`.
 *
 * The output is a [StereoBuffer] of doubles: Web Audio takes floats per channel, so the browser
 * worklet copies each channel straight into its output. The edges that need 16-bit integers (the JVM
 * `SourceDataLine` player, the WAV writer) convert there, through [pcm16] and [writePcm16].
 *
 * Extracted from [KlangAudioRenderer] so the per-playback mixdown can run it **once** on the summed
 * mix: the safety brick belongs on the final output, not per engine. See
 * KLANG-00176 (per playback engine) (D2).
 */
class HouseStage(
    sampleRate: Int,
    private val blockFrames: Int,
) {
    /**
     * The house limiter's own TIMING: what runs on the summed mix, always.
     *
     * The *character* it shares with the authored `limiter(...)` door of the Katalyst builder (threshold,
     * ratio, knee, release) lives in `audio_bridge/constants/MasterLimiterDefaults.kt`, because those
     * four are also that door's defaults and must have exactly one declaration.
     *
     * The two constants below are **house-only**: no DSL field carries them, because the house
     * limiter is not authorable. The opt-in stage has its own `AUTHORED_*` timing (also in the
     * bridge) which deliberately differs (see `MasterDefaultsSyncSpec` for why, and for the
     * assertions that keep the divergence intentional rather than accidental).
     */
    companion object {
        /**
         * Lookahead: the limiter delays the mix by this much so it can start closing the gain
         * *before* a transient arrives. Without it the limiter contributes ~0 dB during a transient
         * and the hard clip below does the work: 2 to 6 ms of clipping per hit, audible as a "knock".
         *
         * Uniform on the whole output (this stage runs once, after every playback is summed), so the
         * delay shifts everything together and nothing can desync. That is why the HOUSE lookahead
         * lives here, always on, and why an authored one upstream (a Katalyst `compressor` or
         * `limiter` with `lookahead`, since phase 3 step 12 C2) is opt-in: it makes its cylinder or its
         * playback late by that much, uncompensated.
         *
         * See KLANG-00225 (master limiter lookahead).
         */
        const val HOUSE_LIMITER_LOOKAHEAD_SECONDS: Double = 0.005

        /**
         * The gain-smoothing length: how fast the gain closes inside the lookahead window.
         *
         * Equal to the window: peak performance is *invariant* to this value (the min-hold does the
         * anticipating), while low-frequency cleanliness tracks it directly, so at a fixed latency
         * more smoothing is strictly better. It also collapses the "flat bottom" of the gain dip,
         * which is the pre-duck people hear as the mix flinching ahead of a kick.
         */
        const val HOUSE_LIMITER_ATTACK_SECONDS: Double = 0.005
    }

    /**
     * Latency this stage adds, in frames: the limiter's lookahead delay.
     *
     * The whole output is delayed by this, uniformly, so nothing desyncs *within* the audio. But it
     * is invisible to `AudioContext.outputLatency` (it happens inside the worklet, downstream of the
     * clock), so anything aligning visuals to audio has to add it explicitly. See
     * KLANG-00225 (master limiter lookahead) Phase 5.
     */
    val latencyFrames: Int get() = limiter.latencyFrames

    private val limiter = Compressor(
        sampleRate = sampleRate,
        thresholdDb = LIMITER_THRESHOLD_DB,
        ratio = LIMITER_RATIO,
        kneeDb = LIMITER_KNEE_DB,
        attackSeconds = HOUSE_LIMITER_ATTACK_SECONDS,
        releaseSeconds = LIMITER_RELEASE_SECONDS,
        lookaheadSeconds = HOUSE_LIMITER_LOOKAHEAD_SECONDS,
    )

    // Master-out DC blockers, a corner of 7.64 Hz at every sample rate ([houseDcBlockCoeff]: 0.999 at 48 kHz, audit
    // L8-07; the fixed 0.999 put it at 7.0 Hz at 44.1 kHz and 15.3 Hz at 96 kHz).
    //
    // Run BEFORE the limiter, so the LIMITER is what feeds the clip below. Two reasons, and the
    // second is why this is required rather than tidy:
    //   1. DC eats headroom asymmetrically and inflates what the detector sees, so removing it
    //      first is simply the correct order.
    //   2. A DC blocker is NOT level-safe. It is a ~7 Hz high-pass whose pole transient overshoots
    //      on onsets: measured, a limited (-1 dBFS) 55 Hz sine switched on cold comes out at
    //      -0.47 dBFS, a +0.53 dB gain decaying over ~21 ms. Downstream of the limiter that
    //      overshoot lands straight on the clip and eats most of the margin the limiter leaves.
    private val dcBlockerL = LowPassHighPassFilters.DcBlocker(coefficient = houseDcBlockCoeff(sampleRate = sampleRate))
    private val dcBlockerR = LowPassHighPassFilters.DcBlocker(coefficient = houseDcBlockCoeff(sampleRate = sampleRate))

    /**
     * "Poison seen" (KLANG-00425, L8-04 (c), maintainer 2026-11-11: "We make the failures visible but keep the motor
     * raw"): the non-finite samples the house limiter has stored as 0 in its delay ring since this stage was built,
     * each channel's counted apart. Nothing is clamped or scrubbed for it: the count rides on the test the ring write
     * already makes. One bad sample on the mix reads as 2 per channel it reached, because the DC blocker ahead of the
     * limiter passes it out twice before its state is flushed (`x - xPrev` with the bad sample as `xPrev`). A huge
     * FINITE sample is not counted (audit L8-01, L8-02). Monotone, never reset ([reset] leaves it); a Double, exact to
     * 2^53, so a backend that sees NaN on every sample for years still counts right (no Long, the engine rule). The
     * dispatcher carries it in every diagnostics report.
     */
    var nonFiniteSamples: Double = 0.0
        private set

    /**
     * Clears stateful post-chain elements (limiter envelope + DC blocker IIR state). Used at the
     * end of the warmup handshake so post-chain state does not survive into the first real block.
     * The [nonFiniteSamples] count is not audio state and stays.
     */
    fun reset() {
        limiter.reset()
        dcBlockerL.reset()
        dcBlockerR.reset()
    }

    /**
     * Applies the DC blockers and the limiter to [mix] in place, then writes the transparent clip of
     * it into [out] (which must hold `blockFrames` frames per channel). [mix] keeps the unclipped
     * doubles; [out] is the engine's output.
     */
    fun process(mix: StereoBuffer, out: StereoBuffer) {
        // Master-out DC blockers (per channel, in-place), ahead of the limiter. See above.
        dcBlockerL.process(buffer = mix.left, offset = 0, length = blockFrames)
        dcBlockerR.process(buffer = mix.right, offset = 0, length = blockFrames)

        // Apply dynamic limiter: handles the bulk of loudness management musically. With lookahead
        // it also delays the mix by HOUSE_LIMITER_LOOKAHEAD_SECONDS; uniform, so nothing desyncs.
        limiter.process(left = mix.left, right = mix.right, blockSize = blockFrames)

        // Poison seen: the limiter's ring counted what it sterilised in this block. Once per block, one compare.
        val seen = limiter.nonFiniteSamples

        if (seen != 0) {
            nonFiniteSamples += seen
            limiter.nonFiniteSamples = 0
        }

        // Transparent clip into the floating-point output.
        clipOutput(mix = mix, frames = blockFrames, out = out)
    }
}

/**
 * The master's clip: writes [clipSample] of the first [frames] frames of [mix] into [out], channel by
 * channel. [HouseStage.process] runs it once per block, after the limiter; it is its own function so
 * the specs exercise the real loop, not a copy.
 */
internal fun clipOutput(mix: StereoBuffer, frames: Int, out: StereoBuffer) {
    val inL = mix.left
    val inR = mix.right
    val outL = out.left
    val outR = out.right

    for (i in 0 until frames) {
        outL[i] = clipSample(inL[i])
        outR[i] = clipSample(inR[i])
    }
}

/**
 * One sample of the engine's output: in `[-1, 1]` it passes untouched (no quantisation); above 1 it is
 * 1.0, anything else -1.0. Most samples are in range, and they take the first branch with no clamp math.
 *
 * NaN fails both comparisons and lands on -1.0, a full-scale negative click. It cannot arrive through
 * [HouseStage.process]: the house limiter's delay ring stores every non-finite sample as 0.0
 * (`Compressor.processLookahead`), so the clip only ever sees finite values.
 *
 * Inline so the hot loop in [clipOutput] stays one flat body on both platforms.
 */
@Suppress("NOTHING_TO_INLINE")
internal inline fun clipSample(sample: AudioSample): AudioSample {
    return if (sample >= -1.0 && sample <= 1.0) {
        sample
    } else if (sample > 1.0) {
        1.0
    } else {
        -1.0
    }
}
