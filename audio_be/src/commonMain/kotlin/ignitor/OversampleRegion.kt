/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.Oversampler
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.childNodes
import io.peekandpoke.klang.audio_bridge.withChildNodes
import kotlin.math.abs

/**
 * The most 2x steps a region may reach, counted from the base rate: 16x. A guard against absurd values only, not an
 * error (maintainer, 2026-10-31; KLANG-00214 (oversampling regions) section 10): a deeper request stops here.
 */
internal const val REGION_MAX_STAGES: Int = 4

/**
 * One oversampling region's place in the build (KLANG-00214 (oversampling regions)): [stages] is the region's rate,
 * counted in 2x steps from the base rate (the absolute rate, nesting included), [input] the holder its input is
 * converted into, and [outer] the region around it (null at the outermost). A
 * [io.peekandpoke.klang.audio_bridge.IgnitorDsl.RegionInput] with `up` = k reads the input of the region k steps out
 * along [outer]. Identity is part of the build cache key, as the detune scope is: a node built inside a region is never
 * shared with the same node built outside it or in another region, because one stateful instance rendered at two rates
 * would advance twice per block.
 *
 * [inputDelay] is how late the input enters the region, in BASE-RATE samples after the voice's own time: the outer
 * region's, plus the inner's latency. Inside the body every latency counts from it, in the region's samples
 * ([relativeLatency]): the input is 0, a source converted up from the base rate is usually earlier (negative), so a
 * `parallel` in the body lines its branches up wherever they come from. The trips up are left out: a source and an
 * outer input are converted up through exactly the stages the input was, so they cancel in every comparison.
 *
 * [inputOversamplers] is the oversampler list ([BuiltIgnitor.oversamplers]) the input ran through before the OUTERMOST
 * region, at the base rate (a nested region inherits it); null when unknown. A source in the body takes their phase
 * twins before it is converted up, so it matches the input at every frequency, not only in the bass and the mids
 * (maintainer, 2026-11-01). What a nested region's input ran through inside the outer bodies is not twinned: there the
 * source is padded by whole samples.
 */
internal class RegionScope(
    val stages: Int,
    val input: RegionInputHolder,
    val outer: RegionScope?,
    val inputDelay: Double,
    val inputOversamplers: List<Int>?,
) {
    /** A delay in base-rate samples after the voice's own time, as a latency in this body (see [inputDelay]). */
    fun relativeLatency(baseDelay: Double): Double = (baseDelay - inputDelay) * (1 shl stages)
}

/**
 * Whether a [RegionScope.relativeLatency] is the region input's own: then the signal ran through the same cascade as the
 * input and a `parallel` can twin it exactly (an empty oversampler list); otherwise it reports none (`null`), and the
 * `parallel` pads whole samples, matching the bass and the mids. The margin absorbs rounding only.
 */
internal fun isAlignedWithRegionInput(relativeLatency: Double): Boolean = abs(relativeLatency) < 1e-9

/**
 * Where a region hands its body the input, converted up: [buffer] holds `length * factor` samples from index 0 for the
 * block being rendered, written by [OversampleRegionIgnitor] before its body renders. A field, not an argument: the
 * body reaches the input through any depth of nodes.
 */
internal class RegionInputHolder {
    var buffer: AudioBuffer = EMPTY_BUFFER

    private companion object {
        val EMPTY_BUFFER = AudioBuffer(0)
    }
}

/**
 * The region's input inside its body: copies what the region converted up for this block. Read by every reference to
 * the body's `RegionInput` (a `parallel` in the body reads it once per branch); the holder is the region's.
 */
internal class RegionInputIgnitor(private val holder: RegionInputHolder) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        val input = holder.buffer
        val end = ctx.windowEnd

        // The holder is the region's work buffer, written from index 0 (`Oversampler.upsample`); the child context's
        // window starts at 0 too, so the indices meet.
        for (i in ctx.offset until end) {
            buffer[i] = input[i]
        }
    }
}

/**
 * An outer region's input read inside a nested region's body (`RegionInput(up > 0)`, a body lambda's parameter used
 * in a lambda nested in it): the outer region's [holder], written at the outer rate for this block, converted up by
 * [oversampler] to the rate it is read at. The outer region renders its body, and so this nested one, after it has filled
 * the holder, so the holder is current; its window starts at 0 and is this window's length divided by the factor.
 */
internal class OuterRegionInputIgnitor(
    private val holder: RegionInputHolder,
    private val oversampler: Oversampler,
) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        ctx.scratchBuffers.use { work ->
            oversampler.upsample(source = holder.buffer, offset = 0, length = ctx.length / oversampler.factor, work = work)

            val end = ctx.windowEnd

            for (i in ctx.offset until end) {
                buffer[i] = work[i]
            }
        }
    }
}

/**
 * An oversampling region at render: [inner] (the signal from outside, at the incoming rate) is converted up by
 * [oversampler], [body] renders at the region's rate in a CHILD context, and the result comes back down once.
 *
 * The child context ([child]) is the voice's context at the region's rate: its sample rate, its frame counters and its
 * window times the factor, its scratch from the voice pool's oversampled sub-pool (so the body's own oversamplers and
 * scratch stack onto the same pool), its random stream the voice's, and [IgniteContext.rootContext] the voice's own
 * context, for the sources inside, which stay at the base rate ([LiftedSourceIgnitor]). It is made at the first block,
 * when the voice's context is known: one allocation per region per voice. Its pitch modulation is null: the sources
 * that read one render in the voice's context.
 *
 * The frame counters are multiplied with saturation at the block start: a held realtime note's gate (`Int.MAX_VALUE`)
 * stays the largest Int instead of wrapping negative. A voice past about 46 minutes at 16x (12.4 hours / 16)
 * saturates its elapsed count, and an envelope counting on from it within the block wraps, as the voice's own context
 * does after 12.4 hours.
 */
internal class OversampleRegionIgnitor(
    private val inner: Ignitor,
    private val body: Ignitor,
    private val oversampler: Oversampler,
    private val input: RegionInputHolder,
) : Ignitor {
    private var child: IgniteContext? = null

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        val factor = oversampler.factor
        val c = child ?: IgniteContext(
            sampleRate = ctx.sampleRate * factor,
            voiceDurationFrames = timesSaturated(ctx.voiceDurationFrames, factor),
            gateEndFrame = timesSaturated(ctx.gateEndFrame, factor),
            scratchBuffers = ctx.scratchBuffers.oversample(factor),
            random = ctx.random,
        ).also { child = it }

        c.updateOffsetAndLength(offset = 0, length = ctx.length * factor)
        c.voiceElapsedFrames = timesSaturated(ctx.voiceElapsedFrames, factor)
        c.gateEndFrame = timesSaturated(ctx.gateEndFrame, factor)
        c.rootContext = ctx.rootContext ?: ctx

        // Both leases from the oversampled sub-pool, which is also the child's scratch: the body's own leases stack on top.
        ctx.scratchBuffers.oversample(factor).use { work ->
            ctx.scratchBuffers.use { source ->
                inner.generate(source, freqHz, ctx)
                oversampler.upsample(source = source, offset = ctx.offset, length = ctx.length, work = work)
            }

            ctx.scratchBuffers.oversample(factor).use { out ->
                input.buffer = work
                body.generate(out, freqHz, c)
                oversampler.decimate(work = out, target = buffer, offset = ctx.offset, length = ctx.length)
            }
        }
    }

    private companion object {
        /** `frames * factor`, held at `Int.MAX_VALUE` instead of wrapping (no Long on the audio path). */
        fun timesSaturated(frames: Int, factor: Int): Int =
            if (frames > Int.MAX_VALUE / factor) Int.MAX_VALUE else frames * factor
    }
}

/**
 * A SOURCE inside a region (an oscillator, a noise, an LFO in a knob, with its pitch modulation): it renders at the base
 * rate, in the voice's context ([IgniteContext.rootContext]), and is converted up once to the rate it is read at
 * (maintainer, 2026-10-31: sources stay at 1x; KLANG-00214 (oversampling regions) section 10). [oversampler] steps from
 * the base rate to the region's, all of the region's 2x steps.
 */
internal class LiftedSourceIgnitor(
    private val source: Ignitor,
    private val oversampler: Oversampler,
) : Ignitor {
    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        val root = ctx.rootContext ?: ctx

        root.scratchBuffers.use { base ->
            source.generate(base, freqHz, root)

            root.scratchBuffers.oversample(oversampler.factor).use { work ->
                oversampler.upsample(source = base, offset = root.offset, length = root.length, work = work)

                val end = ctx.windowEnd

                // The child window starts at 0, as the work buffer does.
                for (i in ctx.offset until end) {
                    buffer[i] = work[i]
                }
            }
        }
    }
}

/**
 * This body with [input] in place of its region's input: what a region with no step to take builds (`oversampleAtLeast(4)`
 * inside a 4x region), the body on its input in the scope it is in, as if the region were not written. The region
 * boundary goes, so the indices of [IgnitorDsl.RegionInput] follow it (de Bruijn substitution): at nesting depth d in
 * the body, `up == d` is this region's input and becomes [input], shifted by d so that ITS region inputs still point
 * where they did; `up > d` points past this region and loses one; `up < d` is a nested region's own and stays. Shared
 * nodes stay shared: each node is rewritten once per depth (identity), and an unchanged subtree is returned as it is.
 */
internal fun IgnitorDsl.withRegionInput(input: IgnitorDsl): IgnitorDsl {
    val doneNodes = ArrayList<IgnitorDsl>()
    val doneDepths = ArrayList<Int>()
    val made = ArrayList<IgnitorDsl>()

    // The input placed at each depth, made once: every reference at one depth gets the same node, so the build shares it.
    val shiftedInputs = ArrayList<IgnitorDsl?>()

    fun inputAt(depth: Int): IgnitorDsl {
        while (shiftedInputs.size <= depth) {
            shiftedInputs.add(null)
        }

        return shiftedInputs[depth] ?: input.shiftedRegionInputs(by = depth).also { shiftedInputs[depth] = it }
    }

    fun IgnitorDsl.substituted(depth: Int): IgnitorDsl {
        if (this is IgnitorDsl.RegionInput) {
            return when {
                up == depth -> inputAt(depth)
                up > depth -> IgnitorDsl.RegionInput(up = up - 1)
                else -> this
            }
        }

        for (i in doneNodes.indices) {
            if (doneNodes[i] === this && doneDepths[i] == depth) {
                return made[i]
            }
        }

        val result = mapRegionChildren(depth) { child, childDepth -> child.substituted(childDepth) }

        doneNodes.add(this)
        doneDepths.add(depth)
        made.add(result)

        return result
    }

    return substituted(depth = 0)
}

/**
 * This tree with every [IgnitorDsl.RegionInput] that points out of it (`up` at least its nesting depth) moved [by]
 * regions further out: the tree is placed [by] region bodies deeper than it was written (see [withRegionInput]).
 */
private fun IgnitorDsl.shiftedRegionInputs(by: Int): IgnitorDsl {
    if (by == 0) {
        return this
    }

    val doneNodes = ArrayList<IgnitorDsl>()
    val doneDepths = ArrayList<Int>()
    val made = ArrayList<IgnitorDsl>()

    // Shared nodes stay shared, as in [withRegionInput]: each node is rewritten once per depth.
    fun IgnitorDsl.shifted(depth: Int): IgnitorDsl {
        if (this is IgnitorDsl.RegionInput) {
            return if (up >= depth) IgnitorDsl.RegionInput(up = up + by) else this
        }

        for (i in doneNodes.indices) {
            if (doneNodes[i] === this && doneDepths[i] == depth) {
                return made[i]
            }
        }

        val result = mapRegionChildren(depth) { child, childDepth -> child.shifted(childDepth) }

        doneNodes.add(this)
        doneDepths.add(depth)
        made.add(result)

        return result
    }

    return shifted(depth = 0)
}

/**
 * This node with each child mapped by [map], which gets the child's nesting depth: a region's body one deeper than the
 * node, everything else at [depth]. Returns the node itself when no child changed (identity kept).
 */
private inline fun IgnitorDsl.mapRegionChildren(depth: Int, map: (IgnitorDsl, Int) -> IgnitorDsl): IgnitorDsl = when (this) {
    is IgnitorDsl.OversampleBy -> {
        val i = map(inner, depth)
        val b = map(body, depth + 1)
        val f = map(factor, depth)

        if (i === inner && b === body && f === factor) this else copy(inner = i, body = b, factor = f)
    }

    is IgnitorDsl.OversampleAtLeast -> {
        val i = map(inner, depth)
        val b = map(body, depth + 1)
        val f = map(factor, depth)

        if (i === inner && b === body && f === factor) this else copy(inner = i, body = b, factor = f)
    }

    else -> {
        val children = childNodes()
        val replaced = children.map { map(it, depth) }

        if (children.indices.all { children[it] === replaced[it] }) this else withChildNodes(replaced)
    }
}

/**
 * Whether this node PRODUCES a signal of its own, a source: an oscillator, a noise, a string, the sample, and silence
 * (which takes a pitch modulation like a source, so it must read it at the same rate). Inside an oversampling region a
 * source stays at the base rate (maintainer, 2026-10-31; KLANG-00214 (oversampling regions) section 10), and the build
 * converts it up where it enters. An exhaustive `when` with no `else` on purpose: a new node type must decide here which
 * it is.
 */
internal fun IgnitorDsl.isSource(): Boolean = when (this) {
    is IgnitorDsl.Sine, is IgnitorDsl.Saw, is IgnitorDsl.Ramp, is IgnitorDsl.Square, is IgnitorDsl.Pulze, is IgnitorDsl.Tri,
    is IgnitorDsl.Zawtooth, is IgnitorDsl.Zamp, is IgnitorDsl.RawPulze, is IgnitorDsl.Impulse,
    is IgnitorDsl.SuperSaw, is IgnitorDsl.SuperSine, is IgnitorDsl.SuperSquare, is IgnitorDsl.SuperTri, is IgnitorDsl.SuperRamp,
    is IgnitorDsl.WhiteNoise, is IgnitorDsl.PinkNoise, is IgnitorDsl.BrownNoise, is IgnitorDsl.PerlinNoise, is IgnitorDsl.BerlinNoise,
    is IgnitorDsl.Crackle, is IgnitorDsl.Dust,
    is IgnitorDsl.Pluck, is IgnitorDsl.SuperPluck,
    is IgnitorDsl.Sample, is IgnitorDsl.Silence,
    -> true

    is IgnitorDsl.Abs, is IgnitorDsl.Accelerate, is IgnitorDsl.Adsr, is IgnitorDsl.Affine, is IgnitorDsl.Bandpass,
    is IgnitorDsl.Ceil, is IgnitorDsl.Clamp, is IgnitorDsl.Coarse, is IgnitorDsl.Constant, is IgnitorDsl.Crush,
    is IgnitorDsl.Detune, is IgnitorDsl.Distort, is IgnitorDsl.Div, is IgnitorDsl.Drive, is IgnitorDsl.Eq, is IgnitorDsl.Exp,
    is IgnitorDsl.Floor, is IgnitorDsl.Fm, is IgnitorDsl.Frac, is IgnitorDsl.Freq, is IgnitorDsl.Highpass, is IgnitorDsl.Lerp,
    is IgnitorDsl.Log, is IgnitorDsl.Lowpass, is IgnitorDsl.Max, is IgnitorDsl.Min, is IgnitorDsl.Minus, is IgnitorDsl.Mod,
    is IgnitorDsl.Neg, is IgnitorDsl.Notch, is IgnitorDsl.OnePoleLowpass, is IgnitorDsl.OptimizerHint, is IgnitorDsl.Param,
    is IgnitorDsl.Phaser, is IgnitorDsl.PitchEnvelope, is IgnitorDsl.PitchMod, is IgnitorDsl.PitchModSemitones,
    is IgnitorDsl.Plus, is IgnitorDsl.Pow, is IgnitorDsl.Range, is IgnitorDsl.Recip, is IgnitorDsl.Round, is IgnitorDsl.Select,
    is IgnitorDsl.Shape, is IgnitorDsl.Shimmer, is IgnitorDsl.Sign, is IgnitorDsl.Sq, is IgnitorDsl.Sqrt,
    is IgnitorDsl.Tanh, is IgnitorDsl.Times, is IgnitorDsl.Tremolo, is IgnitorDsl.Variants, is IgnitorDsl.Parallel,
    is IgnitorDsl.Vibrato, is IgnitorDsl.OversampleBy, is IgnitorDsl.OversampleAtLeast, is IgnitorDsl.RegionInput,
    -> false
}
