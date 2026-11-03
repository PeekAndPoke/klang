/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.Oversampler
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_bridge.constants.SILENCE_FLOOR
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * An oversampling REGION of the bus (`KatalystStageDsl.OversampleBy`, `OversampleAtLeast`; KLANG-00214 (oversampling
 * regions)): the bus at this position is converted up by [steps] 2x steps, [body] runs on it at that rate, and the
 * result is converted down into the bus again. One trip up and one down for every stage inside, whatever the voices on
 * the orbit are.
 *
 * [body] is a [KatalystChain] of its own, built by `KatalystChainBuilder` at the region's rate and block size (its
 * delay rings and reverb units sized for it, its knob modulators on the orbit's clock), so every stage inside keeps
 * its sound in seconds and Hz. The conversions are the engine's [Oversampler], the cascade continued from [firstStage]
 * (the chain the region sits in), the same kernel as a `distort` stage's: a region at the base rate is matched in a
 * `parallel` by the same phase twin as a `distort` with that factor ([oversamplers]).
 *
 * **Latency.** The round trip's group delay plus the body's own latency, which runs in region frames and is divided
 * down, rounded once ([latencyFrames]). Only the round trip is an oversampler a `parallel` can twin; the body's part
 * counts as pure delay, so a nested oversampler or a lookahead inside is matched by whole frames.
 *
 * **Allocation:** the region's bus and the four converters, here at build; [process] allocates nothing.
 *
 * **Lifecycle:** every question the chain asks is passed to [body]. The region reports a tail of its own while the
 * down conversion may still ring out what the body last produced ([Oversampler.tailFrames] quiet frames).
 */
class KatalystRegionEffect internal constructor(
    /** The stages inside, built at the region's rate; never holds a duck (the builder takes it out). */
    internal val body: KatalystChain,
    /** The 2x steps the region takes, at least 1 (a region with none is built as its body's stages in place). */
    private val steps: Int,
    /** The cascade stage of the region's first step: the steps of the chain it sits in (0 on an orbit). */
    firstStage: Int,
    /** The frames of one block of the chain the region sits in. */
    blockFrames: Int,
) : KatalystEffect, KatalystLatentEffect {

    /** The region's bus, [steps] times twice as long as a block of the chain around it: the body's in-place buffer. */
    private val inner: KatalystContext = (blockFrames shl steps).let { frames ->
        KatalystContext(blockFrames = frames, mixBuffer = StereoBuffer(frames))
    }

    // One converter per channel; each holds its up and its down half.
    private val left = Oversampler(stages = steps, firstStage = firstStage)
    private val right = Oversampler(stages = steps, firstStage = firstStage)

    /** The round trip, the one oversampler of this stage a `parallel` can twin. */
    override val oversamplers: List<Int> = listOf(steps)

    /** The round trip's group delay and the body's exact latency in this chain's frames ([KatalystLatentEffect.exactLatency]). */
    override val exactLatency: Double =
        Oversampler.groupDelaySamples(stages = steps, firstStage = firstStage) + body.exactLatency / (1 shl steps)

    /** [exactLatency] rounded once, here. */
    override val latencyFrames: Int = exactLatency.roundToInt()

    /** How long the down conversion may ring after the body's output goes quiet, in this chain's frames. */
    private val holdFrames: Int = Oversampler.tailFrames(steps)

    /** Frames since the last block in which the body's output rose above [SILENCE_FLOOR], held at [holdFrames]. */
    private var quietFrames: Int = holdFrames

    /** True while the body holds a tail that cannot end on its own ([KatalystChain.sustainsItself]). */
    fun sustainsItself(): Boolean = body.sustainsItself()

    override val deniedRents: Int get() = body.deniedRents

    override fun process(ctx: KatalystContext) {
        val frames = ctx.blockFrames
        val mix = ctx.mixBuffer
        val region = inner.mixBuffer

        left.upsample(source = mix.left, offset = 0, length = frames, work = region.left)
        right.upsample(source = mix.right, offset = 0, length = frames, work = region.right)

        body.process(inner)

        countQuiet(region = region, frames = frames)

        left.decimate(work = region.left, target = mix.left, offset = 0, length = frames)
        right.decimate(work = region.right, target = mix.right, offset = 0, length = frames)
    }

    /** One block of the body's output against [SILENCE_FLOOR]: a loud one starts the hold again, a quiet one counts. */
    private fun countQuiet(region: StereoBuffer, frames: Int) {
        val l = region.left
        val r = region.right

        for (i in 0 until inner.blockFrames) {
            if (abs(l[i]) > SILENCE_FLOOR || abs(r[i]) > SILENCE_FLOOR) {
                quietFrames = 0

                return
            }
        }

        quietFrames = (quietFrames + frames).coerceAtMost(holdFrames)
    }

    override fun reset() {
        clear()
        body.reset()
    }

    override fun hasTail(): Boolean = body.hasTail() || quietFrames < holdFrames

    /** The body measures its own stages: the region's input is silent when the chain's is ([KatalystChain.measureTails]). */
    override fun measureTail() {
        body.measureTails()
    }

    /** The body hands back what it rented; the converters are the stage's own and only clear. */
    override fun retire() {
        clear()
        body.retire()
    }

    private fun clear() {
        left.reset()
        right.reset()
        inner.mixBuffer.clear()
        quietFrames = holdFrames
    }
}
