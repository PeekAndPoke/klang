/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

/**
 * THE tremolo law, one copy for both hosts (phase 3 step 3b, 2026-09-25): the voice strip's
 * `TremoloRenderer` and the Ignitor's `Tremolo` node are thin wrappers that only adapt their host's
 * block contract (the strip reads its knobs once per note, the node once per block). Before this
 * the node carried its own sine-only loop, bit-identical to the strip's only at the neutral
 * settings; now it renders what the strip renders at every shape, skew and start phase, which is
 * what `classic()` needs to rebuild the strip tremolo (step 6). `TremoloNodeStripParitySpec` proves
 * it in raw bits.
 *
 * The per-sample loop in [apply] is the strip's loop, moved verbatim: the phase advances every
 * sample through `wrapPhase` (ledger W2), the level is [lfoNorm] (whose unskewed-sine fast path IS
 * the shipped sine tremolo), and the gain is `1 - depth * (1 - level)`.
 *
 * Unit conversions live here, once: a rate in Hz becomes a radian increment in [increment], a start
 * phase in cycles becomes radians at construction.
 *
 * @param shape the LFO waveform, resolved by the host (a name through `parseLfoShape`, an index knob
 *   through `lfoShapeAt`).
 * @param startPhase where the LFO starts in its own cycle, in CYCLES (`0..1` is one cycle). Folded
 *   by `wrapPhase`, which also turns a non-finite seed into 0.0, so a hostile value cannot poison
 *   the accumulator.
 * @param skew the initial skew, see [setSkew].
 */
internal class TremoloCore(
    private val shape: LfoShape,
    startPhase: Double,
    skew: Double,
) {
    private var phase: Double = (startPhase * TWO_PI).wrapPhase(TWO_PI)

    private var skew: Double = skew

    /** Cycle position the waveform's two halves are split at; [LfoShape] owns the skew law. */
    private var duty: Double = lfoDutyOf(skew, shape)

    private var scaleFirst: Double = lfoScaleFirst(duty)

    private var scaleSecond: Double = lfoScaleSecond(duty)

    /**
     * Sets the skew (-1..+1, 0 symmetric; a non-finite one reads as symmetric, see [lfoDutyOf]). The
     * duty and its two scales are recomputed only when the value CHANGES, so a host that reads the
     * skew every block pays two divides only on a block where it moved.
     */
    fun setSkew(value: Double) {
        // Two NaNs are the same "unset" skew; `NaN == NaN` is false, so say it explicitly.
        if (value == skew || (value.isNaN() && skew.isNaN())) {
            return
        }

        skew = value
        duty = lfoDutyOf(value, shape)
        scaleFirst = lfoScaleFirst(duty)
        scaleSecond = lfoScaleSecond(duty)
    }

    /**
     * Advances the clock by [frames] samples without rendering: the LFO is a clock (ledger W2), so a
     * host that bypasses a block (the node at a depth of 0 or less) resumes exactly where an unbypassed
     * LFO would be.
     */
    fun skip(frames: Int, increment: Double) {
        phase = (phase + increment * frames).wrapPhase(TWO_PI)
    }

    /**
     * Renders `output[i] = input[i] * gain(i)` for `i` in `[from, to)`, advancing the clock one sample
     * each. [input] and [output] may be the same buffer (the strip renders in place).
     */
    fun apply(input: AudioBuffer, output: AudioBuffer, from: Int, to: Int, increment: Double, depth: Double) {
        // Everything the LFO needs is loop-invariant except the phase: hoisted into locals, because
        // a field read inside the loop is one neither the JIT nor Kotlin/JS can hoist for us.
        val amount = depth
        val waveform = shape
        val splitAt = duty
        val first = scaleFirst
        val second = scaleSecond
        var p = phase

        for (i in from until to) {
            // wrapPhase over the bare subtract (ledger W2): identical in range; a non-finite or
            // negative rate can no longer kill the phase for the voice's life.
            p = (p + increment).wrapPhase(TWO_PI)

            val level = lfoNorm(waveform, p, splitAt, first, second)
            val gain = 1.0 - (amount * (1.0 - level))

            output[i] = (input[i] * gain)
        }

        phase = p
    }

    companion object {
        /** The per-sample radian increment of an LFO at [rateHz]: the one Hz-to-radians conversion. */
        fun increment(rateHz: Double, sampleRate: Int): Double = (rateHz * TWO_PI) / sampleRate
    }
}
