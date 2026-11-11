/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.katalyst

import io.peekandpoke.klang.audio_be.KnobGlide
import io.peekandpoke.klang.audio_be.effects.Compressor
import io.peekandpoke.klang.audio_be.utils.isFiniteInline

/**
 * The three gain-computer knobs of the cylinder compressor (threshold, inverse ratio, knee) moving to new values as ONE
 * move: every knob that moves travels on one shared fraction, so the path through the gain curve is always the
 * straight line the linear glide takes, and only its TIMING depends on the direction.
 *
 * - **Which law is decided by the move's NET effect** (review round 2), once, at the retarget: the gain reduction of
 *   the values in force against that of the targets, both on the compressor's curve at a FIXED loud input,
 *   `Compressor.KNOB_MOVE_REFERENCE_DB` (0 dBFS; the maintainer's decision of 2026-10-17, review round 3), never at
 *   the follower's envelope, so a move after a rest is judged like the same move mid-phrase.
 * - **Less reduction at the targets SNAPS the knobs** (a rising threshold, a lower ratio, a narrower knee, or a mix
 *   of moves that relaxes on balance; KLANG-00428, maintainer 2026-11-11: "as recommended, no ear test needed"): the
 *   values in force are the targets from the next block on, and the compressor's follower carries the swell, exactly
 *   as when the input falls: since KLANG-00428 it smooths the gain REDUCTION, so a reduction the curve no longer asks
 *   for releases at the release time constant. Until then this move released its knobs at the release time itself
 *   (by ear, 2026-10-17, KLANG-00316 (knob glide listening) item 2: "the hard swell is not really acceptable, or at
 *   least not that fast"), and with the reduction follower behind it the release was paid twice: 10 to 90 percent in
 *   about 2.65 release time constants. Judged at the curve, not at one knob: a compressor-to-limiter swap (the knee
 *   narrows, the threshold and ratio add) glides.
 * - **Never faster than the glide.** A relaxing move whose compressor releases faster than the glide's span
 *   ([io.peekandpoke.klang.audio_bridge.constants.KNOB_GLIDE_SECONDS] in whole blocks) GLIDES instead of snapping: the
 *   glide is the click safety net (a knob jump measured -9 to -36 dB above 8 kHz) and the floor under a short release.
 *   A floor on the speed of the TRANSITION, not a clamp on the release: the compressor's release is untouched.
 * - **Anything else GLIDES**, more reduction or the same (a tie, as when both thresholds sit above 0 dBFS): linearly
 *   over [io.peekandpoke.klang.audio_bridge.constants.KNOB_GLIDE_SECONDS] in whole blocks, the [KnobGlide] law.
 * - **All three knobs on one fraction** (review round 1): with a fraction per knob an adding knob would arrive in
 *   50 ms while a lagging one trailed, and the path would dip far below both ends. On one fraction the path is the
 *   glide's path.
 *
 * The classic path only: a lookahead instance runs [Compressor.process] whatever the move does
 * (`Compressor.processGliding`), its hold, release and box smoother keeping a knob step off the output.
 *
 * Inside a block the compressor ramps linearly per sample from the value the last block ended on (`Compressor
 * .processGliding`). A new move starts from the values IN FORCE. A new target while a move runs starts a new move from
 * the values in force, classified afresh (two owners flip-flopping chain smoothly). A config that names the targets
 * that stand is free.
 *
 * **A knob that holds a modulator is not part of the move**: it keeps its own one-block [KnobGlide] (decision D2 of
 * the knob expressions), because a slower law would smear the modulation. The first values after construction or
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

    /** The glide's span in seconds: a relaxing move snaps only for a release at least this long (see the class KDoc). */
    internal val glideSeconds: Double = blocks * blockFrames.toDouble() / sampleRate

    /** Values in force [0, 3), the move's starts [3, 6), its targets [6, 9). */
    internal val state = DoubleArray(9)

    /** Blocks since the move started. */
    internal var k: Int = 0

    /** [SETTLED] or [GLIDING]. */
    internal var mode: Int = SETTLED

    /** True until an [advance] has consumed a value: the first values snap. */
    internal var snapNext: Boolean = true

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
        // reduction at the targets snaps (or glides under a release shorter than the glide), anything else (more, or a
        // tie) glides. Through the compressor's holder, so no double crosses a call; [compress] rewrites the holder
        // before the block runs.
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

        if (!moves) {
            mode = SETTLED

            return
        }

        // A relaxing move snaps and the follower releases; under a release shorter than the glide it glides. NaN-guard:
        // the stored release is finite (the setter drops a non-finite one), so the compare settles.
        if (relaxes && compressor.releaseSeconds >= glideSeconds) {
            for (i in 0 until 3) {
                if (modulated[i] == null) {
                    s[i] = s[TARGET + i]
                }
            }

            mode = SETTLED

            return
        }

        mode = GLIDING
    }

    /** Moves the knobs by one block. Call once per block, then read [value]. */
    @Suppress("NOTHING_TO_INLINE")
    inline fun advance() {
        for (i in 0 until 3) {
            modulated[i]?.advance()
        }

        snapNext = false

        if (mode == SETTLED) {
            return
        }

        val s = state

        k++

        // The progress of the linear glide
        val land = k >= blocks
        val progress = if (land) 1.0 else k * invBlocks

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

        const val SETTLED: Int = 0
        const val GLIDING: Int = 1
    }
}
