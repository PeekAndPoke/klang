/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.Oversampler
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_bridge.constants.SILENCE_FLOOR
import kotlin.math.min

/**
 * Branches side by side (`KatalystStageDsl.Parallel`): every branch, a [KatalystChain] of its own, runs on a copy of the
 * bus at this position, and the stage writes the SUM of the branches back into the bus.
 *
 * **Matched in phase.** A branch that delays the bus (a compressor's lookahead, an oversampled distort) would comb
 * against the others in the sum: at a delay of 4 frames the first notch sits at `sampleRate / 8`, 6 kHz at 48 kHz. The
 * two kinds of delay are matched apart ([pads]). An oversampler's IIR round trip delays the top more than the bass, so
 * no whole-frame pad matches it above about 10 kHz: every branch gets an unshaped round trip of each oversampler it
 * lacks against the union of the branches' oversamplers (the PHASE TWINS, `Oversampler.unionOf`), and then all hold
 * the same all-pass cascade. What is left is pure delay (a lookahead), matched by a ring to the longest one. The stage
 * reports the union's latency plus that longest pure delay ([latencyFrames], [oversamplers]).
 *
 * **Allocation:** one block buffer per branch, the twins and the pad rings, all here at build; [process] allocates
 * nothing.
 *
 * **Lifecycle:** every question the chain asks is passed to the branches. A ring is not reported as a tail of its own:
 * it holds what a latent branch also holds. A twin is: its input is its branch's OUTPUT, which a lookahead holds back
 * by up to 2400 frames after the distort stage it twins has gone quiet, so it reports a tail until
 * [Oversampler.tailFrames] quiet frames have entered it.
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

    /** Every branch's oversamplers together: each branch is padded to this cascade by twins. */
    override val oversamplers: List<Int> = Oversampler.unionOf(branches.map { it.oversamplers })

    /** The longest pure delay of a branch: every other branch is padded to it by a ring. */
    private val pureDelayFrames: Int = branches.maxOf { it.pureDelayFrames }

    /** The latency of every padded branch: the union's oversamplers and the longest pure delay. */
    override val latencyFrames: Int = oversamplers.sumOf { Oversampler.latencyFrames(it) } + pureDelayFrames

    /** Each branch's own bus, the copy of the input it processes in place. */
    private val contexts: Array<KatalystContext> = Array(branches.size) {
        KatalystContext(blockFrames = blockFrames, mixBuffer = StereoBuffer(blockFrames))
    }

    /** Each branch's pad: the twins of the oversamplers it lacks, and a ring for the pure delay it lacks. */
    private val pads: Array<Pad> = Array(branches.size) {
        Pad(
            twinStages = Oversampler.missingFrom(have = branches[it].oversamplers, union = oversamplers),
            delayFrames = pureDelayFrames - branches[it].pureDelayFrames,
        )
    }

    /** The twins' oversampled work buffers, one lease per factor a pad uses, built here so the audio thread never builds one. */
    private val scratch: ScratchBuffers = ScratchBuffers(blockFrames).also { scratch ->
        pads.flatMap { it.twinStages }.distinct().forEach { scratch.oversample(1 shl it) }
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
            pads[i].addPadded(own = contexts[i].mixBuffer, into = mix, frames = frames, scratch = scratch)
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
            if (branches[i].hasTail() || pads[i].hasTail()) {
                return true
            }
        }

        return false
    }

    /** The branches hand back what they rented; the pads are the stage's own and only clear. */
    override fun retire() {
        clearPads()

        for (i in branches.indices) {
            branches[i].retire()
        }
    }

    private fun clearPads() {
        for (i in pads.indices) {
            pads[i].clear()
        }
    }

    /**
     * One branch's pad: an unshaped round trip per entry of [twinStages] (a stage count) on both channels, in place, then
     * a fixed delay of [delayFrames] frames, added into the sum. With neither, the branch is added as it is.
     */
    private class Pad(val twinStages: List<Int>, delayFrames: Int) {
        private val twinsL = Array(twinStages.size) { Oversampler(twinStages[it]) }
        private val twinsR = Array(twinStages.size) { Oversampler(twinStages[it]) }
        private val left = DoubleArray(delayFrames)
        private val right = DoubleArray(delayFrames)
        private var pos = 0

        /** How long the twins may ring after their input goes quiet; 0 without twins. */
        private val holdFrames = twinStages.maxOfOrNull { Oversampler.tailFrames(it) } ?: 0

        /** Frames since the last block that entered the twins with a sample above [SILENCE_FLOOR], held at [holdFrames]. */
        private var quietFrames = holdFrames

        /** True while the twins may still ring out what entered them. */
        fun hasTail(): Boolean = quietFrames < holdFrames

        fun addPadded(own: StereoBuffer, into: StereoBuffer, frames: Int, scratch: ScratchBuffers) {
            if (holdFrames > 0) {
                countQuiet(own = own, frames = frames)
            }

            for (t in twinsL.indices) {
                val tL = twinsL[t]
                val tR = twinsR[t]

                scratch.oversample(tL.factor).use { work ->
                    tL.upsample(source = own.left, offset = 0, length = frames, work = work)
                    tL.decimate(work = work, target = own.left, offset = 0, length = frames)
                    tR.upsample(source = own.right, offset = 0, length = frames, work = work)
                    tR.decimate(work = work, target = own.right, offset = 0, length = frames)
                }
            }

            val size = left.size

            if (size == 0) {
                for (f in 0 until frames) {
                    into.left[f] += own.left[f]
                    into.right[f] += own.right[f]
                }

                return
            }

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

        private fun countQuiet(own: StereoBuffer, frames: Int) {
            val floor = SILENCE_FLOOR

            for (f in 0 until frames) {
                val l = own.left[f]
                val r = own.right[f]

                if (l > floor || l < -floor || r > floor || r < -floor) {
                    quietFrames = 0

                    return
                }
            }

            quietFrames = min(quietFrames + frames, holdFrames)
        }

        fun clear() {
            quietFrames = holdFrames

            for (t in twinsL.indices) {
                twinsL[t].reset()
                twinsR[t].reset()
            }

            left.fill(0.0)
            right.fill(0.0)
            pos = 0
        }
    }
}
