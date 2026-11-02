/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_be.utils.isFiniteInline
import kotlin.math.cbrt
import kotlin.math.round

/**
 * A playback's output gain and its ramp (KLANG-00406, step 5, `Cmd.SetGain`): a song faded out under a code example
 * and in again after it, a preview fading in. One per [PlaybackEngine], created with it; nothing allocates after
 * construction.
 *
 * **The law.** The ramp moves a POSITION p linearly and the gain is [gainOf] p, today `p³` (Klang-Main-Dev's
 * proposal, 2026-11-02, the shape is the maintainer's to decide by ear): about even in dB over the top 40 to 50 dB, it
 * lands exactly on 0 and on any target, two multiplies per sample. [gainOf] and [positionOf] are the law's only home;
 * the ramp around them does not depend on it.
 *
 * **Whole blocks, per sample, exact landing** (`KnobGlide`'s law): the ramp spans its time rounded to whole blocks, the
 * position after `k` of them is interpolated from the ramp's start, not accumulated, and within a block it moves per
 * sample from where the previous block ended, so there is no per-block zipper. The landing block's last sample is the
 * target gain bit for bit, and from there on [applyTo] multiplies by the target itself.
 *
 * **A new target mid-ramp starts from where the gain IS** (the position the last block ended on) and takes its full
 * time again. `rampSec <= 0`, or a ramp shorter than half a block, sets the gain at once. A non-finite gain or ramp is
 * ignored and the values that stand stay (the review ledger's cached-config rule); nothing else is clamped (the Motor
 * stays raw).
 */
internal class PlaybackGain(private val sampleRate: Int, private val blockFrames: Int) {

    /**
     * The four doubles a block reads and writes, by [POSITION], [START], [TARGET_POSITION] and [TARGET]: an array, as
     * in `KnobGlide`, because on V8 double fields written per block can be stored as fresh heap numbers, while an
     * element access in optimized code never allocates (`audio/ref/performance.md`).
     */
    private val state = DoubleArray(STATE_SIZE).also {
        it[POSITION] = 1.0
        it[START] = 1.0
        it[TARGET_POSITION] = 1.0
        it[TARGET] = 1.0
    }

    /** Blocks the running ramp spans. */
    private var blocks: Int = 0

    /** Blocks left in the running ramp; zero means settled. */
    private var remaining: Int = 0

    /** The gain the ramp goes to, or stands at once settled. */
    val target: Double get() = state[TARGET]

    /** True when the gain is exactly 1 and settled: the engine's output passes untouched. */
    val isUnity: Boolean get() = remaining == 0 && state[TARGET] == 1.0

    /** Moves the gain to [gain] over [rampSec] seconds from where it is now; at once when the ramp is under half a block. */
    fun retarget(gain: Double, rampSec: Double) {
        if (!gain.isFiniteInline() || !rampSec.isFiniteInline()) { // NaN-guard (and Inf): the values that stand stay
            return
        }

        val span = round(rampSec * sampleRate / blockFrames).toInt()

        if (span <= 0) {
            settle(gain)
            return
        }

        if (gain == state[TARGET]) {
            return
        }

        state[START] = state[POSITION]
        state[TARGET_POSITION] = positionOf(gain)
        state[TARGET] = gain
        blocks = span
        remaining = span
    }

    /** Sets the gain to [gain] at once, settled; [retarget] has guarded it. */
    private fun settle(gain: Double) {
        val position = positionOf(gain)

        state[POSITION] = position
        state[START] = position
        state[TARGET_POSITION] = position
        state[TARGET] = gain
        remaining = 0
    }

    /** Multiplies the first `blockFrames` frames of [bus] by the gain, and moves the ramp by one block. */
    fun applyTo(bus: StereoBuffer) {
        val left = bus.left
        val right = bus.right
        val frames = blockFrames

        if (remaining == 0) {
            val g = state[TARGET]

            for (i in 0 until frames) {
                left[i] *= g
                right[i] *= g
            }

            return
        }

        val from = state[POSITION]

        remaining--

        val to = if (remaining == 0) {
            state[TARGET_POSITION]
        } else {
            val start = state[START]

            start + (state[TARGET_POSITION] - start) * ((blocks - remaining).toDouble() / blocks)
        }
        val step = (to - from) / frames

        // Written from the END, `to - step * (frames - 1 - i)`, so the block's last sample is the block's position
        for (i in 0 until frames - 1) {
            val g = gainOf(to - step * (frames - 1 - i))

            left[i] *= g
            right[i] *= g
        }

        // The last sample: the target itself in the landing block, bit for bit
        val last = if (remaining == 0) state[TARGET] else gainOf(to)

        left[frames - 1] *= last
        right[frames - 1] *= last

        state[POSITION] = to
    }

    private companion object {
        const val POSITION = 0
        const val START = 1
        const val TARGET_POSITION = 2
        const val TARGET = 3
        const val STATE_SIZE = 4

        /** The law: the gain at ramp position [p]. Its inverse is [positionOf]. */
        @Suppress("NOTHING_TO_INLINE")
        inline fun gainOf(p: Double): Double = p * p * p

        /** The ramp position of [gain], the inverse of [gainOf]; called once per ramp, never per sample. */
        fun positionOf(gain: Double): Double = cbrt(gain)
    }
}
