/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_bridge.constants.FILTER_CUTOFF_OFFSET_PER_ANALOG
import io.peekandpoke.klang.audio_bridge.constants.FILTER_DRIFT_RELATIVE_TO_OSC
import kotlin.random.Random

/**
 * Computes a per-voice cutoff offset multiplier. At `analog = 0` returns `1.0`
 * (bit-identical to no offset) and draws NOTHING. At `analog > 0` returns
 * `1 + uniform(-1, 1) * analog * cutoffOffsetPerAnalog` and draws exactly one `nextDouble()`.
 * At the shipped default (0.0002) that is about +/- 0.02 % at `analog = 1` (about +/- 0.35
 * cents); about +/- 0.06 % at `analog = 3` (about +/- 1 cent); about +/- 0.2 % at `analog = 10`
 * (about +/- 3.5 cents).
 *
 * **One implementation, two callers, because it is a DRAW.** `VoiceFactory` calls it for the
 * voice strip's filters and [buildFilterHumanization] for the Ignitor DSL's; a second copy of
 * the expression would be a second rng consumer that could drift from this one silently, which
 * is the hazard step 1 of phase 3 found in this very function.
 */
internal fun perVoiceCutoffOffsetMul(analog: Double, cutoffOffsetPerAnalog: Double, rng: Random): Double {
    if (analog <= 0.0) {
        return 1.0
    }

    return 1.0 + (rng.nextDouble() - 0.5) * 2.0 * cutoffOffsetPerAnalog * analog
}

/**
 * One tree filter's per-voice analog humanization: the FIXED cutoff tolerance drawn once for
 * this voice, and the slow drift lane that wanders it for as long as the note sounds.
 *
 * It exists as an object rather than as two knobs because both halves are per-voice RANDOM
 * DRAWS, and no number a pattern writes can carry a draw. The node's side of that is the
 * structural `humanize` flag (`IgnitorDsl.Lowpass.humanize`).
 *
 * **Why the cascade shares ONE of these.** `passes = N` chains N `SvfIgnitor`s, and the voice
 * strip's equivalent is ONE `AudioFilter` whose `sweepCutoff` fans out to its N stages: one
 * tolerance, one drift lane, one multiplier per block for the whole cascade. So the build hands
 * the same instance to every stage of a cascade, and [blockDriftMultiplier] steps the lane only
 * when the block moved on, keyed on `IgniteContext.voiceElapsedFrames`.
 *
 * **What that key actually guarantees, stated exactly.** The test is "the frame counter CHANGED",
 * not "a block elapsed": the lane steps once for each DISTINCT value of `voiceElapsedFrames` it is
 * asked at, however many stages ask. That is the property the cascade needs. It is the per-block
 * property as well only because the renderer advances the counter once per block and never
 * rewinds it (`IgniteContext.voiceElapsedFrames`, monotonic, updated once per block by
 * `IgniteRenderer`), so two consecutive calls at the same value are two stages of one block and
 * never two blocks. A block of zero length does not advance the counter, so it does not step the
 * lane; it also writes no samples, so there is nothing for the lane to have moved for.
 *
 * **The drift is held across the block, not ramped, which is the strip's law too**
 * (`FilterModRenderer` takes one `nextMultiplier()` per block and does not ramp it; the
 * oscillator lanes are the ones that ramp). Both surfaces also switch it the same way at the block
 * boundary (decision D3, the sampling): the coefficients snap to the new value there (or, with an
 * envelope, the block's sweep starts from it). The drift has no LAW half, unlike the envelope: both
 * surfaces step the same `AnalogDrift`, so the multiplier sequence itself is the same process.
 */
class FilterHumanization(
    /** This voice's fixed cutoff tolerance, `1.0` when `analog` was at or below 0. */
    val cutoffOffsetMul: Double,
    private val drift: AnalogDrift?,
) {
    /** True when a drift lane is attached, i.e. the coefficients have to be recomputed per block. */
    val hasDrift: Boolean = drift != null

    private var lastSteppedAtFrame: Int = -1
    private var driftMul: Double = 1.0

    /**
     * This block's drift multiplier, `1.0` when there is no lane. Stepped ONCE per block even
     * when several cascade stages share this instance.
     */
    fun blockDriftMultiplier(ctx: IgniteContext): Double {
        val lane = drift ?: return 1.0

        if (ctx.voiceElapsedFrames != lastSteppedAtFrame) {
            lastSteppedAtFrame = ctx.voiceElapsedFrames
            driftMul = lane.nextMultiplier()
        }

        return driftMul
    }
}

/**
 * Builds one filter's humanization, **and owns the DRAW ORDER**, which is the whole point of
 * this function having a name.
 *
 * The voice strip's order, per filter, is: `perVoiceCutoffOffsetMul` takes one `nextDouble()`,
 * then `AnalogDrift`'s init takes three (two `nextDouble()` for its Box-Muller seed, one
 * `nextInt()` for the xorshift state). At `analog` at or below 0 neither draws at all and the
 * lane is absent. Reproduced here exactly, because a tree that drew a different NUMBER of times
 * would shift every later noise source and every supersaw jitter on the same voice, silently.
 * `IgnitorFilterKnobsSpec` pins the sequence against a literal `Random` replay.
 *
 * Two differences from `VoiceFactory` that a tree cannot avoid and that are NOT this function's
 * to hide (they land with phase 3 step 6, where the strip's filters go away):
 *
 *  - the factory draws every filter's TOLERANCE first and every filter's DRIFT afterwards, in
 *    two passes over the filter list; a tree builds one filter at a time, so the two interleave
 *    once a voice has more than one filter;
 *  - the factory draws before the exciter is built, a tree during it.
 *
 * The scales are the shared constants, not a `StageDsl.Filter`: a tree filter has no pipeline
 * stage to carry per-engine overrides, the same asymmetry `FILTER_DRIVE_PER_ANALOG` already has
 * in [svf]. `PipelineDsl` retires in phase 3 step 9 and takes the question with it.
 *
 * @param analog the filter's resolved analog amount; at or below 0 (or non-finite, which reads
 *   as unset) nothing is drawn and the result is null.
 * @param rng the voice's own stream (`IgnitorBuildCache.random`).
 */
internal fun buildFilterHumanization(
    analog: Double,
    sampleRate: Int,
    blockFrames: Int,
    rng: Random,
): FilterHumanization? {
    if (!analog.isFinite() || analog <= 0.0) {
        return null
    }

    // DRAW 1 of 4: the fixed tolerance.
    val offsetMul = perVoiceCutoffOffsetMul(analog, FILTER_CUTOFF_OFFSET_PER_ANALOG, rng)
    // DRAWS 2, 3 and 4: the lane's Box-Muller seed and its xorshift state, inside the init.
    val drift = AnalogDrift(analog * FILTER_DRIFT_RELATIVE_TO_OSC, analogDriftStepRate(sampleRate, blockFrames), rng)

    // `takeIf { it.active }` is alignment with the strip, which tests `drift.active` before every
    // `nextMultiplier()` (`FilterModRenderer`), not a live case: `analog > 0` here, and
    // `AnalogDrift.active` is `analog * FILTER_DRIFT_RELATIVE_TO_OSC > 0`, which only a
    // denormal-scale `analog` could underflow to zero. The draws above have already happened
    // either way, so this cannot move the stream.
    return FilterHumanization(cutoffOffsetMul = offsetMul, drift = drift.takeIf { it.active })
}
