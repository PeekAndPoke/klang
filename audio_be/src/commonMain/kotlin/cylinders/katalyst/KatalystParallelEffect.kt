/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.StereoBuffer

/**
 * Branches side by side (`KatalystStageDsl.Parallel`): every branch, a [KatalystChain] of its own, runs on a copy of the
 * bus at this position, and the stage writes the SUM of the branches back into the bus.
 *
 * **Aligned by latency.** A branch that delays the bus (a compressor's lookahead, an oversampled distort) would comb
 * against the others in the sum: at a delay of 4 frames the first notch sits at `sampleRate / 8`, 6 kHz at 48 kHz. So
 * every branch is delayed to the longest one by a ring of its own ([pads]), sized here, and the stage reports the
 * longest branch's latency ([latencyFrames]). Whole frames, as every latency in the chain is.
 *
 * **Allocation:** one block buffer per branch and the pad rings, all here at build; [process] allocates nothing.
 *
 * **Lifecycle:** every question the chain asks is passed to the branches. A pad ring is not reported as a tail of its
 * own: it holds what the latent branch also holds, and the branches' own tails cover its content.
 *
 * The branches' slots are resolved through [KatalystParallelWriter]; a branch reads the orbit's param state like the
 * chain around it, so a slot named in a branch is the same slot as one of that name outside it.
 */
class KatalystParallelEffect internal constructor(
    /** The branches, in the order written; at least one (the builder skips an empty stage). */
    internal val branches: Array<KatalystChain>,
    /** The frames of one render block, pinned to 128 in the engine. */
    blockFrames: Int,
) : KatalystEffect, KatalystLatentEffect {

    /** The longest branch's latency: every other branch is padded to it. */
    override val latencyFrames: Int = branches.maxOf { it.latencyFrames }

    /** Each branch's own bus, the copy of the input it processes in place. */
    private val contexts: Array<KatalystContext> = Array(branches.size) {
        KatalystContext(blockFrames = blockFrames, mixBuffer = StereoBuffer(blockFrames))
    }

    /** Each branch's pad ring, or null for a branch already at [latencyFrames]. */
    private val pads: Array<PadRing?> = Array(branches.size) {
        val pad = latencyFrames - branches[it].latencyFrames

        if (pad > 0) PadRing(pad) else null
    }

    /** True when a branch declares a stage that can ring on, see [KatalystChain.declaresTail]. */
    val declaresTail: Boolean = branches.any { it.declaresTail }

    /** True while a branch holds a tail that cannot end on its own, see [KatalystChain.sustainsItself]. */
    fun sustainsItself(): Boolean {
        for (i in branches.indices) {
            if (branches[i].sustainsItself()) {
                return true
            }
        }

        return false
    }

    override val deniedRents: Int
        get() {
            var total = 0

            for (i in branches.indices) {
                total += branches[i].deniedRents
            }

            return total
        }

    override fun process(ctx: KatalystContext) {
        val frames = ctx.blockFrames
        val mix = ctx.mixBuffer

        for (i in branches.indices) {
            val own = contexts[i].mixBuffer

            mix.left.copyInto(own.left, destinationOffset = 0, startIndex = 0, endIndex = frames)
            mix.right.copyInto(own.right, destinationOffset = 0, startIndex = 0, endIndex = frames)

            branches[i].process(contexts[i])
        }

        mix.left.fill(0.0, fromIndex = 0, toIndex = frames)
        mix.right.fill(0.0, fromIndex = 0, toIndex = frames)

        for (i in branches.indices) {
            val own = contexts[i].mixBuffer
            val pad = pads[i]

            if (pad == null) {
                for (f in 0 until frames) {
                    mix.left[f] += own.left[f]
                    mix.right[f] += own.right[f]
                }
            } else {
                pad.addDelayed(own = own, into = mix, frames = frames)
            }
        }
    }

    override fun reset() {
        clearPads()

        for (i in branches.indices) {
            branches[i].reset()
        }
    }

    override fun hasTail(): Boolean {
        for (i in branches.indices) {
            if (branches[i].hasTail()) {
                return true
            }
        }

        return false
    }

    /** The branches hand back what they rented; the pad rings are the stage's own and only clear. */
    override fun retire() {
        clearPads()

        for (i in branches.indices) {
            branches[i].retire()
        }
    }

    private fun clearPads() {
        for (i in pads.indices) {
            pads[i]?.clear()
        }
    }

    /** A fixed delay of [frames] frames on both channels, added into the sum. */
    private class PadRing(frames: Int) {
        private val left = DoubleArray(frames)
        private val right = DoubleArray(frames)
        private var pos = 0

        fun addDelayed(own: StereoBuffer, into: StereoBuffer, frames: Int) {
            val size = left.size

            for (f in 0 until frames) {
                into.left[f] += left[pos]
                into.right[f] += right[pos]
                left[pos] = own.left[f]
                right[pos] = own.right[f]

                pos++

                if (pos == size) {
                    pos = 0
                }
            }
        }

        fun clear() {
            left.fill(0.0)
            right.fill(0.0)
            pos = 0
        }
    }
}
