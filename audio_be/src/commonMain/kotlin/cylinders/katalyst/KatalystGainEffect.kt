/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

/**
 * The orbit's **group fader**: one multiply of the summed mix, at the stage's list position.
 *
 * The structural fix for mixing an orbit deliberately low, to keep its compressor out of plop
 * territory, and bringing the level back at the end of its chain (the signal-flow plan's D5:
 * `gain` means the tone-neutral level on every surface).
 *
 * **Raw.** A negative factor (a polarity flip) and one far above unity are the author's business;
 * nothing here clamps. The two guards are reads of "unset" rather than clamps: a non-finite factor
 * is unity, at this stage's door ([configure]) and at the writer that feeds it, which is what
 * `MasterChain.buildGain` does with the same knob on the master bus (see [KatalystGainWriter]).
 *
 * **Unity is bit-transparent**: the multiply is skipped entirely, so a chain that declares
 * `gain(1.0)` cannot change one sample. There is no `-0.0` hazard in that skip, unlike the
 * additive identities elsewhere in the engine, because this is a multiply by exactly 1.0, which
 * returns `-0.0` for `-0.0` anyway; skipping it is a saving, not a different answer.
 *
 * **A change ramps across ONE block, per sample.** The master snaps its gain instead: its factor
 * is resolved at chain build and a new number is a new chain, so the 60 ms bus crossfade covers
 * it. Here the factor is a slot, so `.katp("gain", "<0.5 1.0>")` moves it while the stage stands,
 * and a snap would step the whole mix by the difference in one sample. One block is not an
 * arbitrary window: the orbit's param state is re-read at most once per block, so a block is
 * exactly the interval between two possible changes, and the longest ramp that always finishes
 * before the next one can start. It bounds the per-sample step at `|new - old| / blockFrames`
 * times the signal, 128 times smaller than the snap the master accepts.
 *
 * An ARRIVING factor is not a move, so it does not ramp: see [snapNext] for the buzz that made
 * that a rule rather than a nicety.
 */
class KatalystGainEffect : KatalystEffect {

    /** The factor the last processed sample was multiplied by. */
    private var currentGain: Double = 1.0

    /** What [process] is ramping towards, written by the chain's writer every block. */
    private var targetGain: Double = 1.0

    /**
     * True while this stage has not multiplied a single sample yet: the arriving factor is then a
     * STARTING point, not a destination, and [configure] snaps to it instead of ramping from unity.
     *
     * Without it the clean slate is a buzz generator. `k.classic().gain(0)` is the mute idiom: the
     * orbit's mix is silent while its voices play, so `Cylinder.tryDeactivate` reaches its silence
     * grace, deactivates and calls [reset]; the next voice to claim the orbit reconfigures the
     * stage, and a ramp would take the mix from unity down to zero across that block. Every
     * eleventh block, at nearly full level: a 31 Hz tone out of an orbit the author muted. The
     * ramp exists to make a MOVE continuous, and there is nothing to be continuous with before the
     * first sample or after a reset (the orbit was silent for ten blocks by then).
     */
    private var snapNext: Boolean = true

    /** Test seam: the factor in force right now, which is the target once a ramp has finished. */
    internal val gain: Double get() = currentGain

    /**
     * Test seam for the one OUTPUT-INVISIBLE property of this stage: how many blocks it spent
     * ramping. A steady fader must ramp ONCE, when it arrives, and then multiply by a constant;
     * a stage that ramped every block would sound the same and do the work forever.
     */
    internal var ramps: Int = 0
        private set

    /**
     * Sets the fader. Takes effect over the next block's ramp, or immediately when no sample has
     * been multiplied yet (see [snapNext]); the same number twice is free.
     *
     * A non-finite factor is UNSET, and this stage's unset is unity, which is what it already
     * holds until something sets it: the call is ignored and the current target stands. The
     * writer ([KatalystGainWriter]) reads unset the same way, so today nothing can reach here with
     * one; the guard is at the door because the cost of missing it is unbounded, see below.
     */
    fun configure(gain: Double) {
        if (!gain.isFinite()) { // NaN-guard: a non-finite factor is unset, and unset is unity
            return
        }

        if (snapNext) {
            currentGain = gain
        }

        targetGain = gain
    }

    override fun reset() {
        currentGain = 1.0
        targetGain = 1.0
        snapNext = true
    }

    /** Nothing time-based at all: one multiply of the mix, no memory of the block before. */
    override fun hasTail(): Boolean = false

    /** Rents nothing, so retiring is the clean slate. */
    override fun retire() {
        reset()
    }

    override fun process(ctx: KatalystContext) {
        // From here on there IS a factor the next one has to be continuous with. Set before the
        // unity early return, because leaving the buffer alone is producing a block at unity.
        snapNext = false

        val from = currentGain
        val to = targetGain

        if (from == to) {
            if (to == 1.0) {
                return // unity: bit-transparent, see the class KDoc
            }

            scale(ctx, to)

            return
        }

        ramp(ctx, from, to)
        currentGain = to
        ramps++
    }

    private fun scale(ctx: KatalystContext, gain: Double) {
        val left = ctx.mixBuffer.left
        val right = ctx.mixBuffer.right

        for (i in 0 until ctx.blockFrames) {
            left[i] = left[i] * gain
            right[i] = right[i] * gain
        }
    }

    private fun ramp(ctx: KatalystContext, from: Double, to: Double) {
        val left = ctx.mixBuffer.left
        val right = ctx.mixBuffer.right
        val frames = ctx.blockFrames
        // The first sample of the ramp is already one step in, so the block's last sample is the
        // new factor: the seam to the block before is continuous (it ended on `from`) and the
        // seam to the block after is too (it starts on `to`).
        val step = (to - from) / frames

        for (i in 0 until frames) {
            val gain = from + step * (i + 1)

            left[i] = left[i] * gain
            right[i] = right[i] * gain
        }
    }
}
