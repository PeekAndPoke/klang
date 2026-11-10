/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.probes

import kotlin.math.log10

/** A frame count that never happened inside the render: the output never got there. */
const val NEVER: Int = -1

/**
 * The facts the stage probe reads off ONE output channel ([measureOutput] is the one place they are computed).
 * Frames are counts at the probe's sample rate; [NEVER] means "not inside the render".
 */
data class OutputFacts(
    /** Frames rendered. */
    val frames: Int,
    /** The largest finite |output|; 0 when no sample is finite. */
    val peak: Double,
    /** The largest finite |input| of the same channel, the reference for [gainDb]. */
    val inputPeak: Double,
    /** Mean of the output over the last [DC_WINDOW_FRAMES] of the input (NaN for a signal without input). */
    val dcInput: Double,
    /** Mean of the output over the last [DC_WINDOW_FRAMES] of the render, deep in the silence after the input. */
    val dcEnd: Double,
    /** Output samples that are NaN or infinite. */
    val nonFiniteCount: Int,
    /** The first non-finite output frame, or [NEVER]. */
    val firstNonFiniteFrame: Int,
    /** The last non-finite output frame, or [NEVER]. */
    val lastNonFiniteFrame: Int,
    /**
     * Frames from the poison sample (or, without one, from the first non-finite output) until the output is finite FOR
     * GOOD; 0 when every sample is finite; [NEVER] when the last sample is still not finite.
     */
    val finiteAgainFrames: Int,
    /**
     * Frames from the input's end until the output STAYS below [SILENCE_LEVEL] (a non-finite sample counts as loud);
     * [NEVER] when the last sample is not below it.
     */
    val tailFrames: Int,
    /** Frames from the input's end until the output STAYS exactly 0.0; [NEVER] when the last sample is not 0.0. */
    val zeroFrames: Int,
    /** Output samples whose magnitude is subnormal: above 0 and below [DOUBLE_MIN_NORMAL]. */
    val subnormalCount: Int,
    /** The first subnormal output frame, or [NEVER]. */
    val firstSubnormalFrame: Int,
    /** The smallest non-zero finite |output| from the input's end on (how deep the tail sinks before it stops); +Inf if none. */
    val minTailAbs: Double,
) {
    val allFinite: Boolean get() = nonFiniteCount == 0

    /** The peak re the input's peak, in dB; NaN when either is 0. */
    val gainDb: Double
        get() = if (peak > 0.0 && inputPeak > 0.0) 20.0 * log10(peak / inputPeak) else Double.NaN

    companion object {
        /** The level the tail is measured to: -100 dBFS. */
        const val SILENCE_LEVEL: Double = 1e-5

        /** Frames each DC mean covers: 0.1 s at 48 kHz. */
        const val DC_WINDOW_FRAMES: Int = 4800
    }
}

/**
 * The facts of one stereo stage run: [left] carries the poison of a poison signal, [right] is the clean twin (null for
 * a mono stage).
 */
class StageFacts(
    val left: OutputFacts,
    val right: OutputFacts?,
) {
    /** Both channels folded into one row, the worse value of each fact ([worstOf]). */
    val worst: OutputFacts get() = if (right == null) left else worstOf(left, right)

    /** True when a non-finite sample reached the CLEAN channel: the poison crossed. Null for a mono stage. */
    val crossed: Boolean? get() = right?.let { it.nonFiniteCount > 0 }
}
