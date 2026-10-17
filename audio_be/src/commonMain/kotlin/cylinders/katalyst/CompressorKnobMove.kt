/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.KnobGlide
import io.peekandpoke.klang.audio_be.effects.Compressor
import io.peekandpoke.klang.audio_be.utils.isFiniteInline

/**
 * The three gain-computer knobs of the orbit compressor (threshold, inverse ratio, knee) moving to new values as ONE
 * move: every knob that moves travels on one shared fraction, so the path through the gain curve is always the
 * straight line the linear glide takes, and only its TIMING depends on the direction (decided by ear, maintainer,
 * 2026-10-17, `docs/tasks/by-ear/knob-glide-listening.md` item 2, and `audio/ref/knob-glide.md`: "the hard swell is
 * not really acceptable, or at least not that fast").
 *
 * - **Which law is decided by the move's NET effect** (review round 2), once, at the retarget: the gain reduction of
 *   the values in force against that of the targets, both on the compressor's curve at a FIXED loud input,
 *   `Compressor.KNOB_MOVE_REFERENCE_DB` (0 dBFS; the maintainer's decision of 2026-10-17, review round 3), never at
 *   the follower's envelope, so a move after a rest is judged like the same move mid-phrase. Less
 *   reduction at the targets (a rising threshold, a lower ratio, a narrower knee, or a mix of moves that relaxes on
 *   balance) RELEASES: the remaining fraction decays as a one-pole with the compressor's own release time constant,
 *   so the gain comes back the way it does when the input falls. Anything else, more reduction or the same (a tie, as
 *   when both thresholds sit above 0 dBFS), GLIDES linearly over
 *   [io.peekandpoke.klang.audio_bridge.constants.KNOB_GLIDE_SECONDS] in whole blocks, the [KnobGlide] law. Judging by
 *   any one knob instead ran a compressor-to-limiter swap (the knee narrows) at the release: a new ceiling exceeded
 *   by +2.9 dB 50 ms in. Above the knee the curve is linear in the threshold and the inverse ratio, so a released
 *   threshold or ratio decays the reduction in dB like the envelope's release, matched at every block end; inside
 *   the knee (a parabola) it runs up to about twice as fast in dB, as the envelope's own release does there. Before,
 *   a 40 dB threshold rise brought 32.2 dB of gain back with 10 to 90 percent in 46 ms.
 * - **Judged by loud material.** A crossover, threshold and ratio moving in opposite directions, takes the law of its
 *   behaviour at 0 dBFS; a quiet passage may then see the other direction for a moment, bounded by the two curves'
 *   difference at that level. The judgement reads no signal and runs once per new move, nothing per block.
 * - **All three knobs on one fraction** (review round 1): with a fraction per knob an adding knob would arrive in
 *   50 ms while a relaxing one lagged at the release time, and the path would dip far below both ends (-29 dB against
 *   ends of -17.5 and -14.2 dB, the reviewer's model). On one fraction the path is the glide's path.
 * - **Never faster than the glide.** A release's fraction is the larger (the slower) of the one-pole's and the linear
 *   glide's at every block end, so a release time under about 50 ms still takes the glide's time: the glide is the
 *   click safety net (a knob jump measured -9 to -36 dB above 8 kHz). A floor on the speed of the TRANSITION, not a
 *   clamp on the release: the compressor's release is untouched and still sets its envelope.
 *
 * Inside a block the compressor ramps linearly per sample from the value the last block ended on (`Compressor
 * .processGliding`). A new move starts from the values IN FORCE. A new target while a move runs
 * starts a new move from the values in force, classified afresh (two owners flip-flopping chain smoothly). A config
 * that names the targets that stand is free. A release LANDS once the glide's time has passed and the one-pole's
 * fraction is at most [LANDING]: the values are then the targets bit for bit and the effect takes the settled path
 * again. CPU: until then (about 16 release time constants) every block takes the gliding path.
 *
 * **A knob that holds a modulator is not part of the move**: it keeps its own one-block [KnobGlide] (decision D2 of
 * the knob expressions), because a release would smear the modulation. The first values after construction or
 * [reset] snap, as on every [KnobGlide].
 */
internal class CompressorKnobMove(
    sampleRate: Int,
    blockFrames: Int,
    thresholdModulated: Boolean,
    ratioModulated: Boolean,
    kneeModulated: Boolean,
) {
    // `internal`, not private: [value], [retarget] and [advance] are `inline` (no double crosses a call on V8,
    // `audio/ref/performance.md`), and an inline body may only touch what its own visibility can see.

    /** Per knob: its own one-block glide when it holds a modulator, else null (the knob is part of the move). */
    internal val modulated: Array<KnobGlide?> = arrayOf(
        if (thresholdModulated) KnobGlide(sampleRate = sampleRate, blockFrames = blockFrames, modulated = true) else null,
        if (ratioModulated) KnobGlide(sampleRate = sampleRate, blockFrames = blockFrames, modulated = true) else null,
        if (kneeModulated) KnobGlide(sampleRate = sampleRate, blockFrames = blockFrames, modulated = true) else null,
    )

    /** The glide's span in whole blocks, the same rounding as [KnobGlide]. */
    internal val blocks: Int = KnobGlide(sampleRate = sampleRate, blockFrames = blockFrames).blocks

    internal val invBlocks: Double = 1.0 / blocks

    /** Values in force [0, 3), the move's starts [3, 6), its targets [6, 9), the one-pole's fraction at [9]. */
    internal val state = DoubleArray(10)

    /** Blocks since the move started. */
    internal var k: Int = 0

    /** [SETTLED], [GLIDING] or [RELEASING]. */
    internal var mode: Int = SETTLED

    /** True until an [advance] has consumed a value: the first values snap. */
    internal var snapNext: Boolean = true

    /** True while a move releases: the caller then computes the decay for [advance]. */
    val releasing: Boolean get() = mode == RELEASING

    /** The value in force of knob [i] ([THRESHOLD], [INVERSE_RATIO], [KNEE]). */
    @Suppress("NOTHING_TO_INLINE")
    inline fun value(i: Int): Double {
        val m = modulated[i]

        return if (m != null) m.value else state[i]
    }

    /**
     * Sets where the three knobs go, in force from the next [advance]. Non-finite targets are ignored (the target that
     * stands is kept). A new move is classified by its NET effect on [compressor]'s curve at 0 dBFS (see the class KDoc). Kotlin `inline`: a modulated knob retargets every block.
     */
    @Suppress("NOTHING_TO_INLINE")
    inline fun retarget(threshold: Double, inverseRatio: Double, knee: Double, compressor: Compressor) {
        val s = state
        var changed = false

        for (i in 0 until 3) {
            val next = if (i == THRESHOLD) threshold else if (i == INVERSE_RATIO) inverseRatio else knee
            val m = modulated[i]

            if (m != null) {
                m.retarget(next)
            } else if (next.isFiniteInline()) { // NaN-guard (and Inf), the wall of [KnobGlide.retarget]
                if (snapNext) {
                    s[i] = next
                    s[TARGET + i] = next
                } else if (next != s[TARGET + i]) {
                    changed = true
                }
            }
        }

        if (!changed) {
            return
        }

        // A new move from the values in force.
        var moves = false

        for (i in 0 until 3) {
            val next = if (i == THRESHOLD) threshold else if (i == INVERSE_RATIO) inverseRatio else knee

            if (modulated[i] == null) {
                val target = if (next.isFiniteInline()) next else s[TARGET + i]

                s[START + i] = s[i]
                s[TARGET + i] = target

                if (target != s[i]) {
                    moves = true
                }
            }
        }

        // The net effect at the fixed reference level (0 dBFS), a modulated knob at its value in force on both sides: less
        // reduction at the targets releases, anything else (more, or a tie) glides. Through the compressor's holder, so
        // no double crosses a call; [compress] rewrites the holder before the block runs.
        val args = compressor.glideArgs
        val thresholdNow = value(THRESHOLD)
        val inverseRatioNow = value(INVERSE_RATIO)
        val kneeNow = value(KNEE)

        args[Compressor.GLIDE_THRESHOLD_FROM] = thresholdNow
        args[Compressor.GLIDE_INVERSE_RATIO_FROM] = inverseRatioNow
        args[Compressor.GLIDE_KNEE_FROM] = kneeNow
        args[Compressor.GLIDE_THRESHOLD_TO] = if (modulated[THRESHOLD] == null) s[TARGET + THRESHOLD] else thresholdNow
        args[Compressor.GLIDE_INVERSE_RATIO_TO] =
            if (modulated[INVERSE_RATIO] == null) s[TARGET + INVERSE_RATIO] else inverseRatioNow
        args[Compressor.GLIDE_KNEE_TO] = if (modulated[KNEE] == null) s[TARGET + KNEE] else kneeNow

        val relaxes = compressor.reducesLessAtGlideEnds()

        k = 0
        s[RELEASE_FRACTION] = 1.0
        mode = if (!moves) SETTLED else if (relaxes) RELEASING else GLIDING
    }

    /**
     * Moves the knobs by one block. Call once per block, then read [value]. [decay] is the release's share of one
     * block, `exp(-frames / (release * sampleRate))`, read only while [releasing].
     */
    @Suppress("NOTHING_TO_INLINE")
    inline fun advance(decay: Double) {
        for (i in 0 until 3) {
            modulated[i]?.advance()
        }

        snapNext = false

        if (mode == SETTLED) {
            return
        }

        val s = state

        k++

        // The progress of the move: the linear glide's, and for a release the slower of the glide's and the one-pole's.
        var progress = if (k >= blocks) 1.0 else k * invBlocks
        var land = k >= blocks

        if (mode == RELEASING) {
            val fraction = s[RELEASE_FRACTION] * decay

            s[RELEASE_FRACTION] = fraction

            if (1.0 - fraction < progress) {
                progress = 1.0 - fraction
            }

            land = land && fraction <= LANDING
        }

        for (i in 0 until 3) {
            if (modulated[i] == null) {
                s[i] = if (land) s[TARGET + i] else s[START + i] + (s[TARGET + i] - s[START + i]) * progress
            }
        }

        if (land) {
            mode = SETTLED
        }
    }

    /** Forgets the move: the next [retarget] snaps. */
    fun reset() {
        mode = SETTLED
        snapNext = true

        for (i in 0 until 3) {
            modulated[i]?.reset()
        }
    }

    internal companion object {
        const val THRESHOLD: Int = 0
        const val INVERSE_RATIO: Int = 1
        const val KNEE: Int = 2

        const val START: Int = 3
        const val TARGET: Int = 6
        const val RELEASE_FRACTION: Int = 9

        const val SETTLED: Int = 0
        const val GLIDING: Int = 1
        const val RELEASING: Int = 2

        /**
         * The one-pole's remaining fraction at which a release lands: at most 1e-7 of the move is left (4e-6 dB of a
         * 40 dB threshold move), under the 1e-4 dB the compressor treats as no reduction at all.
         */
        const val LANDING: Double = 1e-7
    }
}
