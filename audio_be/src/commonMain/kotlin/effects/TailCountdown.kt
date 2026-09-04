/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.effects

import io.peekandpoke.klang.audio_be.StereoBuffer

/**
 * "Does this delay or reverb still hold audible energy?" — answered in closed form, for a unit
 * that is RUNNING (its owner has not said off), instead of by scanning its memory.
 *
 * **Why not scan.** The orbit and master tail checks used to read every cell of the ring / every
 * comb (`DelayLine.hasTail`, `Reverb.hasTail`) to decide whether an orbit may deactivate. That is
 * O(unit) on the audio thread, and since the resource warehouse removed the ring ceiling it is
 * O(whatever the user asked for): a 20 s master ring is a two-million-sample read inside one block
 * (review rounds 2–4 of `docs/plans/resource-warehouse.md`).
 *
 * **Why a countdown is exact enough.** The drain math already trusted for the Draining state
 * (`DelayLine.drainSamplesUntilSilent`, `Reverb.drainSamplesUntilSilent`) is a proof, not an
 * estimate: with SILENT input, the content ceiling of a delay ring is multiplied by `|feedback|`
 * every delay period (the tap is a convex combination and the soft cap never expands), and the
 * comb network's by its feedback every revolution, so after a computable number of periods the
 * unit is provably below the silence threshold and — for |feedback| < 1 — can never come back up.
 * The only input the formula needs is the peak at the moment the input went silent, and that is
 * one bounded read (`tapWindowPeakAbs`, proportional to the DELAY TIME, not the ring; `combPeakAbs`,
 * the comb cells) taken ONCE per silence onset, not once per check.
 *
 * **How.** The owner calls [observe] once per block with whether the unit's input block is silent,
 * BEFORE processing it. Input audible → the tail trivially exists, nothing is measured. Input
 * silent for the first time → the peak is read and the countdown set; every silent block after
 * that subtracts its frames. [hasTail] is then a compare. A self-oscillating unit (|feedback| ≥ 1)
 * gets an infinite countdown and pins its orbit until the owner turns it off — the raw engine's
 * intent, and what the scan did too (a charged self-osc ring never reads as silent).
 *
 * **When the answer differs from the scan.** Never on the audible side: the countdown holds the
 * unit for the whole proof, which can be up to one period longer than a scan would. It can be
 * SHORTER in one case, and correctly so: content older than the tap window is overwritten before
 * the tap ever reaches it, so a scan that "saw" it was holding the orbit for audio that could not
 * be emitted (the round-4 insight behind `tapWindowPeakAbs`).
 *
 * The owner must [invalidate] when the unit's parameters change while silent (a longer delay or a
 * higher feedback decays slower than the countdown assumed); the next silent block re-measures.
 */
class TailCountdown {
    private enum class State {
        /** Never observed since construction or [reset]: the unit holds nothing. */
        Idle,

        /** The last observed input block was audible: a tail exists, nothing to prove. */
        Audible,

        /** Input has been silent since the proof started; [remaining] samples until provably silent. */
        Counting,
    }

    private var state = State.Idle
    private var remaining = 0.0

    /** True while the unit can still contribute audio. O(1). */
    val hasTail: Boolean
        get() = when (state) {
            State.Idle -> false
            State.Audible -> true
            State.Counting -> remaining > 0.0
        }

    /**
     * Once per block, before the unit processes [frames] of input that is [silent] or not.
     * [drainSamples] is evaluated only on the block the input turns silent.
     */
    inline fun observe(silent: Boolean, frames: Int, drainSamples: () -> Double) {
        if (!silent) {
            markAudible()
            return
        }
        if (!isCounting()) {
            // Only a unit that WAS fed has anything to prove; an idle one is empty and the read
            // (O(delay) / O(combs)) is not spent on it.
            startCounting(if (wasAudible()) drainSamples() else 0.0)
        }
        // This block's silent input counts. Infinity minus a block stays Infinity (self-oscillation).
        advance(frames)
    }

    /**
     * The unit's parameters changed while it was counting: the proof assumed the old decay.
     * Back to "audible" so the next silent block re-measures; an idle unit stays idle.
     */
    fun invalidate() {
        if (state == State.Counting) {
            state = State.Audible
            remaining = 0.0
        }
    }

    /** With the unit's own reset: it holds nothing now. */
    fun reset() {
        state = State.Idle
        remaining = 0.0
    }

    @PublishedApi
    internal fun markAudible() {
        state = State.Audible
        remaining = 0.0
    }

    @PublishedApi
    internal fun isCounting(): Boolean = state == State.Counting

    @PublishedApi
    internal fun wasAudible(): Boolean = state == State.Audible

    @PublishedApi
    internal fun startCounting(drainSamples: Double) {
        remaining = drainSamples
        state = State.Counting
    }

    @PublishedApi
    internal fun advance(frames: Int) {
        remaining -= frames
    }

    companion object {
        /** ~-100 dBFS: the silence threshold the drain math and the old scans share. */
        const val SILENCE: Double = 0.00001

        /** True when no sample of the first [frames] of [buffer] exceeds [SILENCE]. Early exit; O(frames). */
        fun isSilent(buffer: StereoBuffer, frames: Int): Boolean {
            val left = buffer.left
            val right = buffer.right
            for (i in 0 until frames) {
                val l = left[i]
                val r = right[i]
                if (l > SILENCE || l < -SILENCE || r > SILENCE || r < -SILENCE) {
                    return false
                }
            }

            return true
        }
    }
}
