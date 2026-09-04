/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.effects

import io.peekandpoke.klang.audio_be.StereoBuffer

/**
 * "Does this delay or reverb still hold audible energy?" — answered from a running CEILING on its
 * content, maintained from the unit's INPUT, instead of by reading its memory.
 *
 * **Why not scan.** The orbit and master tail checks used to read every cell of the ring / every
 * comb (`DelayLine.hasTail`, `Reverb.hasTail`) to decide whether an orbit may deactivate. That is
 * O(unit) on the audio thread, and since the resource warehouse removed the ring ceiling it is
 * O(whatever the user asked for): a 20 s master ring is a two-million-sample read inside one block
 * (review rounds 2–4 of `docs/plans/resource-warehouse.md`). A first attempt replaced the scan
 * with one bounded read at each silence onset (`tapWindowPeakAbs`); review found that this fires
 * once per note gap and is still O(delay), a net loss on sparse material with long delays.
 *
 * **Why a ceiling from the input is exact enough.** What a unit stores is bounded by what went in
 * and by what came back around: a delay ring cell is `softCap(input + feedback · tap)`, a comb cell
 * `input + feedback · lpf(older cell)`, and neither the soft cap, the interpolating tap nor the
 * one-pole damping can EXPAND a value. Cut time into windows one recirculation long — the delay
 * time plus one sample (the interpolation neighbour), the longest comb plus one — so that every
 * read reaches into the previous window or the current one, never further back. Then the content
 * written in the current window is at most
 *
 *     current = inputPeak · (1 + fb + … + fb^(laps−1))  +  |fb| · previous
 *
 * where `laps` is how many times any sample can pass the shortest recirculation path within one
 * window (2 for a ring: only its very first sample comes back inside the same window; 2 for
 * Freeverb, whose comb lengths span less than 2×). The previous-window term needs no such series:
 * a re-lapped previous sample is `fb² · previous + input`, and for |fb| ≤ 1 `fb² ≤ fb` folds it
 * into the terms above; for |fb| > 1 the recurrence is not a bound but is monotone from the first
 * input, which is all "pinned" needs. With silent input this is a geometric decay by |fb| per
 * window — the same proof
 * `drainSamplesUntilSilent` uses for the Draining state — and with sub-threshold input it
 * converges to the true steady floor `input / (1 − fb)` instead of pretending the unit is empty.
 *
 * **How.** The owner calls [observe] once per block with the block's input peak (`peakOf` on the
 * send buffer, O(block) and unavoidable — it replaces the silence test) and the unit's current
 * window and feedback. Everything else is O(1). [hasTail] is two compares. A never-fed unit has
 * ceiling 0: no tail, no cost. A parameter change needs no invalidation: the next window decays by
 * the feedback in force when the content actually recirculates, which is what the ring does too.
 * |feedback| ≥ 1 never decays and pins the orbit until the owner turns it off — the raw engine's
 * intent, and what the scan did for a charged self-oscillating ring.
 *
 * **When the answer differs from the scan.** The invariant, precisely: the ceiling bounds every
 * cell written under the CURRENT period within the current and the previous window, so "no
 * tail" here implies `tapWindowPeakAbs` would say so too. A delay-time LENGTHENING can reach
 * older cells the ceiling has already decayed (up to the ring's capacity, 0.5 s at class 0) —
 * raw live-change behaviour the delay effect's own KDoc already files, and the cut, if an orbit
 * deactivates right then, lands at `quiet/loud · 1e-5`, −60 to −90 dBFS. The comb damping's
 * one-pole carries a small weight (≤ d², 0.16 at most) of windows older than the previous one,
 * folded into the bound's own slack (its steady state overstates a comb by ~1.7× at fb 0.7).
 * On the other side it can hold the unit longer — up to one window, and by that slack (up to 2×
 * at high feedback, i.e. one extra halving-time) — which costs an idle orbit a moment, never
 * audio. The scans stay as test oracles for exactly that claim.
 */
class TailCeiling {
    private var previous = 0.0
    private var current = 0.0

    /** The largest input peak seen in the current window. */
    private var inputPeakInWindow = 0.0

    /** Samples into the current window. */
    private var elapsed = 0.0

    /** True while the unit can still contribute audio. O(1). */
    val hasTail: Boolean get() = previous > SILENCE || current > SILENCE

    /**
     * Once per block, before or after the unit processes it (the bound holds either way).
     *
     * A block's peak is credited to EVERY window the block touches — the one it closes and the
     * one it continues — so the bound holds whatever the block/window ratio (a window shorter
     * than a block is unreachable today: the smallest delay is 10 ms, 441 samples at 44.1 kHz,
     * against 128 frames; the reverb's is ~1 641). A period that shrinks under a running window
     * seals that window once, undecayed (see the body).
     *
     * @param inputPeak the block's largest |sample| going INTO the unit (after any send scaling)
     * @param frames the block's length in samples
     * @param windowSamples one recirculation plus the read's reach (see the unit's `tailWindowSamples`)
     * @param feedback the feedback in force right now (signed; only |feedback| matters)
     * @param lapsPerWindow how many times a sample can pass the shortest path within one window
     */
    fun observe(inputPeak: Double, frames: Int, windowSamples: Double, feedback: Double, lapsPerWindow: Int) {
        val fb = if (feedback < 0.0) -feedback else feedback
        val window = if (windowSamples >= 1.0) windowSamples else 1.0

        // Under a constant window the loop below always leaves `elapsed < window`, so arriving here
        // with `elapsed >= window` means the period SHRANK under time already spent in the running
        // window. Seal that window once, undecayed: its ceiling bounds every cell the shorter tap
        // can now reach, and the time was spent under the OLD period — closing it as several new
        // windows would decay content that never recirculated (review round 3; round 2's cap-and-
        // fold implemented this principle at the wrong boundary).
        if (elapsed >= window) {
            previous = current
            inputPeakInWindow = 0.0
            elapsed = 0.0
        }

        // A non-finite send (a poisoned voice) must not reach the arithmetic: `Inf · 0` is NaN, and
        // a NaN ceiling compares false — "no tail" while the ring rings, the one cut this class
        // promises never to make. It counts as "louder than any audio" instead (review round 2).
        val peak = if (inputPeak <= CEILING_MAX) inputPeak else CEILING_MAX
        if (peak > inputPeakInWindow) {
            inputPeakInWindow = peak
        }
        current = saturate(fresh(inputPeakInWindow, fb, lapsPerWindow) + fb * previous)

        // Bounded by `frames / window + 1` by construction: one close through both 10 ms doors.
        elapsed += frames
        while (elapsed >= window) {
            previous = current
            elapsed -= window
            // The next window holds this block's peak only if the block reaches into it: a block
            // that ends exactly on the boundary contributes nothing to what follows.
            inputPeakInWindow = if (elapsed > 0.0) peak else 0.0
            current = saturate(fresh(inputPeakInWindow, fb, lapsPerWindow) + fb * previous)
        }
    }

    /** Keeps the ceiling finite: |feedback| > 1 grows it without bound, and Inf arithmetic breeds NaN. */
    private fun saturate(value: Double): Double = if (value <= CEILING_MAX) value else CEILING_MAX

    /** The unit was reset: it holds nothing. */
    fun reset() {
        previous = 0.0
        current = 0.0
        inputPeakInWindow = 0.0
        elapsed = 0.0
    }

    /** `peak · (1 + fb + … + fb^(laps−1))`: what one window's input can pile up through the shortest path. */
    private fun fresh(peak: Double, fb: Double, laps: Int): Double {
        var sum = 0.0
        var term = peak
        for (i in 0 until laps) {
            sum += term
            term *= fb
        }
        return sum
    }

    companion object {
        /** ~-100 dBFS: the silence threshold the drain math and the old scans share. */
        const val SILENCE: Double = 0.00001

        /**
         * Louder than any audio the engine stores (the soft cap keeps cells near the user's cap,
         * 1.0 by default): the ceiling saturates here, so it stays finite under self-oscillation
         * and under a non-finite input peak, and "pinned" means exactly this value.
         */
        const val CEILING_MAX: Double = 1.0e6


        /** The largest |sample| in the first [frames] of [buffer], both channels. O(frames). */
        fun peakOf(buffer: StereoBuffer, frames: Int): Double {
            val left = buffer.left
            val right = buffer.right
            var peak = 0.0
            for (i in 0 until frames) {
                val l = if (left[i] < 0.0) -left[i] else left[i]
                val r = if (right[i] < 0.0) -right[i] else right[i]
                if (l > peak) {
                    peak = l
                }
                if (r > peak) {
                    peak = r
                }
            }

            return peak
        }
    }
}
