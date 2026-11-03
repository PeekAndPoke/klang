/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.Oversampler
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.ignitor.REGION_MAX_STAGES

/**
 * Brings an orbit's rate buses ([KatalystContext.buses]) above [level] down to it (KLANG-00214 (oversampling regions)
 * step 3b): the highest bus is stepped down one 2x step at a time, in place, and each bus on the way is added at its
 * own rate, so every rate takes one conversion per orbit, never one per voice. Stage k steps from k+1 to k with the
 * [Oversampler]'s own stage k, the stage a voice's region would have decimated with, so a voice handed over comes out
 * as it would have on its own (to rounding: its gain and pan now apply before the step down).
 *
 * [level] is the rate the chain runs at: 0 for a chain at the base rate, the head region's steps for a chain that
 * begins with one; the buses below it are that region's to convert up ([KatalystRegionEffect.process]).
 */
internal class KatalystBusLadder(private val level: Int) {

    // One converter per channel and stage, stages level until REGION_MAX_STAGES; only the down halves run.
    private val left = Array(REGION_MAX_STAGES - level) { Oversampler(stages = 1, firstStage = level + it) }
    private val right = Array(REGION_MAX_STAGES - level) { Oversampler(stages = 1, firstStage = level + it) }

    /** The highest bus whose steps may still ring: the highest written within the last [holdFrames]. */
    private var ringTop: Int = level

    /** Frames since a bus as high as [ringTop] was last written. */
    private var quietFrames: Int = 0

    /** How long the steps may ring after their input stops ([Oversampler.tailFrames]). */
    private val holdFrames: Int = Oversampler.tailFrames(REGION_MAX_STAGES - level)

    /**
     * Adds every bus of [ctx] above [level], stepped down to it, into [target] (`blockFrames shl level` frames).
     *
     * The steps run on for [holdFrames] after the last voice above [level] has gone, on a silent bus, so they ring
     * out instead of keeping their state for the next voice. That bus is marked as holding audio, so the host zeroes
     * it with the others ([KatalystContext.clearBuses]).
     */
    fun addAbove(ctx: KatalystContext, target: StereoBuffer) {
        val active = ctx.activeBuses
        val frames = ctx.blockFrames
        val top = if (active ushr (level + 1) == 0) level else Int.SIZE_BITS - 1 - active.countLeadingZeroBits()

        if (top >= ringTop) {
            ringTop = top
            quietFrames = 0
        } else {
            quietFrames += frames

            if (quietFrames >= holdFrames) {
                ringTop = top
                quietFrames = 0
            }
        }

        if (ringTop <= level) {
            return
        }

        val work = ctx.busAt(ringTop)

        for (stage in ringTop - 1 downTo level) {
            val length = frames shl stage

            // In place: the stage's output is the first half of the bus it read (the copy onto itself moves nothing).
            left[stage - level].decimate(work = work.left, target = work.left, offset = 0, length = length)
            right[stage - level].decimate(work = work.right, target = work.right, offset = 0, length = length)

            if (stage > level && active and (1 shl stage) != 0) {
                ctx.buses[stage]?.let { work.addFrom(source = it, frames = length) }
            }
        }

        target.addFrom(source = work, frames = frames shl level)
    }

    fun reset() {
        ringTop = level
        quietFrames = 0

        for (i in left.indices) {
            left[i].reset()
            right[i].reset()
        }
    }
}
