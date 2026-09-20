/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_bridge.constants.KNOB_GLIDE_SECONDS
import kotlin.math.round

/**
 * One orbit knob moving to a new value over [KNOB_GLIDE_SECONDS] instead of jumping there
 * (`docs/plans/knob-glide.md`). One instance per knob, created with the effect that owns the knob;
 * nothing here allocates after construction. The sibling of [Crossfade], which ramps between two
 * whole chains the same way this ramps one number.
 *
 * **Use.** The owner calls [retarget] whenever a setting arrives (as often as it likes: the same
 * value again is free) and ONE of the two advances exactly once per rendered block, before it uses
 * the value:
 *  - [advance] for a COEFFICIENT knob, whose math stays at block rate: [value] holds for the whole
 *    block.
 *  - [advanceScaled] for a LEVEL knob, a gain that would zip if it stepped once per block: it moves
 *    the knob by the same block and multiplies a buffer by it, ramping linearly per sample from the
 *    value the previous block ended on to the one this block ends on.
 *
 * **Linear, whole blocks, exact landing.** The glide spans the glide time rounded to the nearest
 * whole block (17 blocks, 49.3 ms, at 44.1 kHz and 128 frames; 19, 50.7 ms, at 48 kHz), so every
 * block of a glide moves by the same amount and no glide ends inside a block. The value after `k`
 * of those blocks is interpolated from the glide's start, not accumulated, and after the last one
 * it IS the target, bit for bit; from then on [advance] does no arithmetic, so a settled knob is
 * bit-transparent and costs a compare per block. Blocks are counted, not samples: an owner driven
 * with fewer frames per block than [blockFrames] glides for longer in time (unreachable in
 * production, where every block is the pinned 128 frames).
 *
 * **A new target mid-glide starts from where the knob IS**, the current [value], and takes the
 * full glide time again: two owners flip-flopping on one orbit chain smoothly and never jump.
 *
 * **The first value snaps.** After construction and after [reset], every [retarget] sets the
 * value outright until an [advance] has consumed one: there is nothing to be continuous with
 * before the first block (the rule and the reason are in `KatalystGainEffect`'s KDoc, "why an
 * ARRIVING factor snaps").
 * Without it every orbit would open with a glide from a meaningless default, and every render
 * would change.
 *
 * **A non-finite target is ignored**, and the target that stands is the one already set (or, before
 * any, the constructor's zero). Not the knob's default: this class does not know what a knob means,
 * and "unset" means something different per knob (the reverb's size reads it as OFF). The owner
 * substitutes its own meaning BEFORE it calls; this guard is the second wall, and what it
 * guarantees is that nothing non-finite is ever stored, so a glide can never be stuck on a NaN
 * (the review ledger's cached-config rule).
 *
 * `-0.0` and `0.0` compare equal here, so a retarget between the two is no move.
 */
internal class KnobGlide(
    sampleRate: Int,
    /** The frames of one render block, pinned to 128 in the engine. */
    blockFrames: Int,
) {
    /** How many blocks one glide spans: the glide time in samples, rounded to whole blocks, at least one. */
    private val blocks: Int = round(KNOB_GLIDE_SECONDS * sampleRate / blockFrames).toInt().coerceAtLeast(1)

    private val invBlocks: Double = 1.0 / blocks

    /** The value in force: after [advance], the value for the block just advanced. */
    var value: Double = 0.0
        private set

    /** Where the knob is going; equal to [value] once the glide has landed. */
    var target: Double = 0.0
        private set

    /** Where the running glide started: the value in force at the [retarget] that began it. */
    private var start: Double = 0.0

    /** Blocks left in the running glide; zero means settled. */
    private var remaining: Int = 0

    /** True until an [advance] has consumed a value: see the class KDoc, "the first value snaps". */
    private var snapNext: Boolean = true

    /** True while the knob is still on its way to [target]. */
    val isGliding: Boolean get() = remaining > 0

    /** Sets where the knob goes. Called as often as settings arrive; an unchanged target is free. */
    fun retarget(next: Double) {
        if (!next.isFinite()) { // NaN-guard (and Inf): the owner substitutes its meaning first, see the KDoc
            return
        }

        if (snapNext) {
            value = next
            target = next

            return
        }

        if (next == target) {
            return
        }

        target = next
        start = value
        remaining = blocks
    }

    /** COEFFICIENT use: moves the knob by one block and returns the value in force for it. Call once per block. */
    fun advance(): Double {
        snapNext = false

        if (remaining == 0) {
            return value
        }

        remaining--
        value = if (remaining == 0) target else start + (target - start) * ((blocks - remaining) * invBlocks)

        return value
    }

    /**
     * LEVEL use: moves the knob by one block, like [advance], and writes [source] times the knob
     * into [into] for [frames] samples, ramping per sample from the value the previous block ended
     * on to the one this block ends on (`docs/plans/knob-glide.md`, pilot log entry 10).
     *
     * **Written from the END**, `end - step * (frames - 1 - i)`, so the block's last sample is the
     * block's value bit for bit, and after the last block of a glide that is the target. A settled
     * knob (and the first, snapped value) takes the fast path, one multiply per sample and nothing
     * else, which is what keeps a steady level identical to a constant gain.
     *
     * **A block shorter than `blockFrames`** spreads that block's share of the glide over the frames
     * it has: the ramp still starts where the previous block ended and still lands exactly. The glide
     * is counted in blocks either way (see the class KDoc). Unreachable in production, where every
     * block is the pinned 128 frames.
     *
     * [into] and [source] may be the same buffer: each sample is read before it is written.
     */
    fun advanceScaled(into: StereoBuffer, source: StereoBuffer, frames: Int) {
        val from = value
        val end = advance()
        val targetLeft = into.left
        val targetRight = into.right
        val sourceLeft = source.left
        val sourceRight = source.right

        if (end == from) {
            for (i in 0 until frames) {
                targetLeft[i] = sourceLeft[i] * end
                targetRight[i] = sourceRight[i] * end
            }

            return
        }

        val step = (end - from) / frames
        val last = frames - 1

        for (i in 0 until frames) {
            val level = end - step * (last - i)

            targetLeft[i] = sourceLeft[i] * level
            targetRight[i] = sourceRight[i] * level
        }
    }

    /**
     * Puts the knob AT [next] with nothing left to travel, without touching the snap window.
     *
     * For a stage that has established by other means that its output already IS what [next]
     * describes, so there is nothing to ride: the duck's life ends this way when no pass ran at
     * all last block and the orbit's mix therefore went out unducked. [reset] would be the wrong
     * tool there, because re-arming the snap makes the stage's NEXT switch instant, which is the
     * click by another door.
     *
     * A non-finite value is ignored, like [retarget]'s.
     */
    fun settleAt(next: Double) {
        if (!next.isFinite()) { // NaN-guard, the same wall as [retarget]'s
            return
        }

        value = next
        target = next
        remaining = 0
    }

    /**
     * Takes [other]'s position over, mid-glide and all, and SPENDS the snap window.
     *
     * For a knob that MOVES between owners with the thing it describes: the duck's stage weight and
     * its depth when a chain swap hands a live envelope to the arriving chain's duck
     * (`KatalystDuckEffect.takeOver`, this method's one caller). The arriving knob must carry on
     * from where the leaving one stood, so the next [retarget] turns the glide around instead of
     * snapping to the target and stepping the output by whatever was not covered yet.
     *
     * The glide's START comes too, not only its value and countdown: without it a glide carried
     * over halfway would interpolate from the wrong end.
     *
     * **PRECONDITION: [other] spans its glide over the same number of blocks**, which means the
     * same sample rate and the same block size. The countdown is in blocks and [blocks] is per
     * instance, so a longer glide's countdown read on a shorter one would drive the value OUTSIDE
     * `[start, target]`: 17 blocks reading a countdown of 19 from 1.0 towards 0.0 lands on 1.0588,
     * which for the duck's weight is over-ducking and for its mirror a boost. Unreachable today
     * (one builder and one sample rate per cylinder), so the countdown is CLAMPED rather than
     * rejected: this is a shared helper and the next caller should not have to rediscover the rule.
     */
    fun carryOver(other: KnobGlide) {
        value = other.value
        target = other.target
        start = other.start
        remaining = other.remaining.coerceAtMost(blocks)
        snapNext = false
    }

    /** Forgets the glide: the next [retarget] snaps. For a stage whose content has just been cleared. */
    fun reset() {
        remaining = 0
        snapNext = true
    }
}
