/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.Oversampler
import io.peekandpoke.klang.audio_be.applyDistortionShape
import io.peekandpoke.klang.audio_be.filters.LowPassHighPassFilters
import io.peekandpoke.klang.audio_be.parseDistortionShape
import io.peekandpoke.klang.audio_be.voices.strip.BlockRenderer
import io.peekandpoke.klang.audio_be.voices.strip.filter.CrushRenderer
import io.peekandpoke.klang.audio_be.voices.strip.filter.DistortionRenderer
import io.peekandpoke.klang.audio_be.voices.strip.filter.renderInPlace
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin

/**
 * The LAWS of the two cores phase 3 step 4 made shared (`CrushCore`, decision D1; `DistortionCore`,
 * decision D2 option A), pinned against ORACLES written out in this file, on BOTH hosts: the voice
 * strip's renderer and the Ignitor node.
 *
 * Why oracles and not the parity spec: `ClassicStripParitySpec` compares the two hosts with each
 * other, so a change INSIDE a shared core moves both sides together and that spec stays green. Only a
 * copy of the law that lives outside the core can see it. Each oracle below is the law as the step-4
 * brief states it, written the plain way:
 *
 *  - crush: `floor(x * hl) / hl`, clamped to `[-1, 1]`, a NaN out as 0, `hl = 2^amount / 2`;
 *  - distort: `shape(x * 10^(1.2 * amount))` with the drive INSIDE the oversampler, NaN out as 0, then
 *    the DC blocker, and no soft cap; the strip bypasses at an amount at or below 0, the node runs a
 *    MODULATED amount at or below 0 at unity drive, contiguously (ledger W5's hazard designed out).
 */
class StripLawCoresSpec : StringSpec({

    val blockFrames = 128
    val sampleRate = 48000

    /** Plays [data] from its own cursor, one window per call: a source whose samples the test chose. */
    class ArraySource(private val data: DoubleArray) : Ignitor {
        private var cursor = 0

        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
            for (i in ctx.offset until ctx.windowEnd) {
                buffer[i] = data[cursor]
                cursor++
            }
        }
    }

    /** A control whose value is scripted per block: the node reads it once per block. */
    class PerBlock(private val values: List<Double>) : Ignitor {
        private var block = 0

        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
            val v = values[block]
            block++

            for (i in ctx.offset until ctx.windowEnd) {
                buffer[i] = v
            }
        }
    }

    fun sine(blocks: Int, amplitude: Double, hz: Double): DoubleArray =
        DoubleArray(blocks * blockFrames) { amplitude * sin(2.0 * PI * hz * it / sampleRate) }

    fun renderNode(node: Ignitor, blocks: Int): DoubleArray {
        val ctx = IgniteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = blocks * blockFrames,
            gateEndFrame = blocks * blockFrames,
            releaseFrames = 0,
            scratchBuffers = ScratchBuffers(blockFrames),
        )
        val out = DoubleArray(blocks * blockFrames)
        val buffer = AudioBuffer(blockFrames)

        for (b in 0 until blocks) {
            ctx.voiceElapsedFrames = b * blockFrames
            ctx.updateOffsetAndLength(0, blockFrames)
            node.generate(buffer, 220.0, ctx)
            buffer.copyInto(out, b * blockFrames)
        }

        return out
    }

    /** The strip's contract: one renderer for the note, every block rendered in place. */
    fun renderStrip(renderer: BlockRenderer, input: DoubleArray): DoubleArray {
        val out = input.copyOf()

        for (b in 0 until input.size / blockFrames) {
            val block = out.copyOfRange(b * blockFrames, (b + 1) * blockFrames)

            renderer.renderInPlace(block, sampleRate)
            block.copyInto(out, b * blockFrames)
        }

        return out
    }

    fun DoubleArray.bits(): List<Long> = map { it.toRawBits() }

    // ── The oracles ──────────────────────────────────────────────────────────────────────────────

    fun clampNan(q: Double): Double {
        val c = if (q < -1.0) -1.0 else if (q > 1.0) 1.0 else q

        return if (c.isNaN()) 0.0 else c
    }

    fun oracleFloor(x: Double, hl: Double): Double = clampNan(floor(x * hl) / hl)

    fun oracleRound(x: Double, hl: Double): Double = clampNan(round(x * hl) / hl)

    /** The crush law over blocks, with the strip's optional oversampler around the quantizer. */
    fun oracleCrush(input: DoubleArray, amount: Double, stages: Int): DoubleArray {
        val hl = 2.0.pow(amount) / 2.0
        val out = input.copyOf()
        val os = if (stages > 0) Oversampler(stages) else null
        val scratch = ScratchBuffers(blockFrames)

        for (b in 0 until input.size / blockFrames) {
            val from = b * blockFrames

            if (os != null) {
                os.process(out, from, blockFrames, scratch) { work, count ->
                    for (i in 0 until count) {
                        work[i] = oracleFloor(work[i], hl)
                    }
                }
            } else {
                for (i in from until from + blockFrames) {
                    out[i] = oracleFloor(out[i], hl)
                }
            }
        }

        return out
    }

    /**
     * The distort law over blocks at a per-block DRIVE GAIN. [driveInside] false is the doors'
     * placement (drive at the base rate, then upsample), kept only to prove the placement is observable.
     */
    fun oracleDistort(input: DoubleArray, drives: List<Double>, shapeName: String, stages: Int, driveInside: Boolean = true): DoubleArray {
        val shape = parseDistortionShape(shapeName)
        val out = input.copyOf()
        val os = if (stages > 0) Oversampler(stages) else null
        val dc = LowPassHighPassFilters.DcBlocker()
        val scratch = ScratchBuffers(blockFrames)

        for (b in 0 until input.size / blockFrames) {
            val from = b * blockFrames
            val d = drives[b]

            if (os != null) {
                if (!driveInside) {
                    for (i in from until from + blockFrames) {
                        out[i] = out[i] * d
                    }
                }

                val inside = if (driveInside) d else 1.0

                os.process(out, from, blockFrames, scratch) { work, count ->
                    for (i in 0 until count) {
                        val y = applyDistortionShape(shape, work[i] * inside)
                        work[i] = if (y.isNaN()) 0.0 else y
                    }
                }
            } else {
                for (i in from until from + blockFrames) {
                    val y = applyDistortionShape(shape, out[i] * d)
                    out[i] = if (y.isNaN()) 0.0 else y
                }
            }

            dc.process(out, from, blockFrames)
        }

        return out
    }

    fun driveOf(amount: Double): Double = 10.0.pow(amount * 1.2)

    // ── crush (D1) ───────────────────────────────────────────────────────────────────────────────

    "crush: both hosts quantize with FLOOR, the oracle's law, not round (D1)" {
        val blocks = 4
        val input = sine(blocks, 0.95, 440.0)

        for (amount in listOf(1.0, 2.5, 4.0, 8.0)) {
            val hl = 2.0.pow(amount) / 2.0
            val expected = oracleCrush(input, amount, stages = 0)

            withClue("amount $amount: not vacuous, floor and round disagree on this input") {
                expected.bits() shouldNotBe input.map { oracleRound(it, hl) }.toDoubleArray().bits()
            }
            withClue("amount $amount: the strip") {
                renderStrip(CrushRenderer(amount), input).bits() shouldBe expected.bits()
            }
            withClue("amount $amount: the Ignitor node") {
                renderNode(ArraySource(input).crush(ConstantIgnitor(amount)), blocks).bits() shouldBe expected.bits()
            }
        }
    }

    "crush: the strip's oversampled path runs the same quantizer inside the oversampler" {
        // Also the row for the transform built once per note (was a closure per block): same bits.
        val input = sine(4, 0.95, 440.0)

        for (stages in listOf(1, 2)) {
            withClue("stages $stages") {
                renderStrip(CrushRenderer(4.0, stages), input).bits() shouldBe oracleCrush(input, 4.0, stages).bits()
            }
        }
    }

    "crush: the Ignitor node takes the strip's NaN handling and bypass rule (release notes, step 4)" {
        val blocks = 2
        val input = sine(blocks, 0.8, 440.0).also { it[5] = Double.NaN; it[130] = Double.NaN }

        for ((host, render) in listOf<Pair<String, (Double) -> DoubleArray>>(
            "strip" to { a -> renderStrip(CrushRenderer(a), input) },
            "node" to { a -> renderNode(ArraySource(input).crush(ConstantIgnitor(a)), blocks) },
        )) {
            withClue("$host: a NaN sample comes out as 0.0") {
                val out = render(4.0)

                out[5] shouldBe 0.0
                out[130] shouldBe 0.0
                out.all { it.isFinite() } shouldBe true
            }
            withClue("$host: a NaN amount is a bypass") {
                render(Double.NaN).bits() shouldBe input.bits()
            }
            withClue("$host: an amount below 1.0 is a bypass, 1.0 is not") {
                render(0.999).bits() shouldBe input.bits()
                render(1.0).bits() shouldNotBe input.bits()
            }
            withClue("$host: an infinite amount is silence, not NaN") {
                render(Double.POSITIVE_INFINITY).all { it == 0.0 } shouldBe true
            }
        }
    }

    // ── distort (D2) ─────────────────────────────────────────────────────────────────────────────

    "distort: both hosts render the strip's law, the drive INSIDE the oversampler and no cap (D2)" {
        val blocks = 6
        val input = sine(blocks, 0.9, 220.0)

        for (shapeName in listOf("soft", "gentle", "tube")) {
            for (stages in listOf(0, 1, 2)) {
                for (amount in listOf(0.3, 1.0)) {
                    val drives = List(blocks) { driveOf(amount) }
                    val expected = oracleDistort(input, drives, shapeName, stages)
                    val clue = "$shapeName x$stages amount $amount"

                    withClue("$clue: the strip") {
                        renderStrip(DistortionRenderer(amount, shapeName, stages), input).bits() shouldBe expected.bits()
                    }
                    withClue("$clue: the fused node") {
                        val node = ArraySource(input).fusedDistort(ConstantIgnitor(amount), parseDistortionShape(shapeName), stages)

                        renderNode(node, blocks).bits() shouldBe expected.bits()
                    }
                    if (stages > 0) {
                        withClue("$clue: not vacuous, the drive's place is observable") {
                            oracleDistort(input, drives, shapeName, stages, driveInside = false).bits() shouldNotBe expected.bits()
                        }
                    }
                }
            }
        }

        withClue("no cap: gentle (doubled) at amount 1.0 leaves [-1, 1] on both hosts") {
            (renderStrip(DistortionRenderer(1.0, "gentle", 1), input).maxOf { abs(it) } > 1.0) shouldBe true
            val node = ArraySource(input).fusedDistort(ConstantIgnitor(1.0), parseDistortionShape("gentle"), 1)
            (renderNode(node, blocks).maxOf { abs(it) } > 1.0) shouldBe true
        }
    }

    "distort: the strip stage at an amount at or below 0 is the identity (the strip's bypass)" {
        val input = sine(2, 0.9, 220.0)

        for (amount in listOf(0.0, -0.5)) {
            withClue("amount $amount") {
                renderStrip(DistortionRenderer(amount, "soft", 1), input).bits() shouldBe input.bits()
            }
        }
    }

    "distort: a MODULATED amount through 0 keeps the node contiguous, unity drive and no reset (W5's hazard)" {
        // D2 superseded W5's "delete it" for the fused node; W5's REASON was a per-block bypass that
        // left the oversampler and the DC blocker stale when a modulated amount crossed 0, and popped.
        // Here the blocks at or below 0 run the SAME core at unity drive, so the render is ONE
        // uninterrupted core with a per-block gain. The primary guard is bit-equality to the oracle run
        // the same way; the click metric is the audio reviewer's (round 1): the second difference AT a
        // crossing boundary must not exceed the largest at an ORDINARY block boundary (see below).
        //
        // A realistic LFO: +-0.5 at 2 Hz, sampled per block, with a phase offset so that it crosses 0
        // twice inside the render (near blocks 85 and 179), drive between unity and 10^0.6.
        val blocks = 200
        val amounts = List(blocks) { 0.5 * sin(2.0 * PI * 2.0 * (it * blockFrames).toDouble() / sampleRate + 0.3) }
        val input = sine(blocks, 0.9, 220.0)
        val drives = amounts.map { if (it <= 0.0) 1.0 else driveOf(it) }
        val expected = oracleDistort(input, drives, "soft", 1)

        val node = renderNode(ArraySource(input).fusedDistort(PerBlock(amounts), parseDistortionShape("soft"), 1), blocks)

        node.bits() shouldBe expected.bits()

        val firstOff = amounts.indexOfFirst { it <= 0.0 }

        withClue("not a bypass: a block at or below 0 is still shaped") {
            val range = firstOff * blockFrames until (firstOff + 1) * blockFrames

            node.slice(range) shouldNotBe input.slice(range)
        }

        // The click metric compares a crossing with an ORDINARY block boundary. Every boundary of this
        // render steps the drive (the LFO moves per block), and the oversampler's decimator smears that
        // step over its first few output samples, so the fair bar for "no state reset" is what an
        // ordinary boundary does, not the smooth interior of a block. Measured (round 1 of the review,
        // the reviewer's metric "inside the blocks" tried both ways first):
        //  - interior taken as every frame 2 or more past a boundary: the bar is the render's start
        //    transient in block 0 (0.032, crossing 0.0015), but under the per-block bypass it also includes
        //    the reset's own smear, so the mutant passed only by 0.64 against 0.50;
        //  - interior taken as 8 or more frames past a boundary: 0.008, below the correct build's
        //    crossings (0.017), which are ordinary gain steps at the LFO's steepest slope. A correct build
        //    failed it.
        // "At" a boundary is its first EDGE frames, the decimator's reach.
        val edge = 8
        val crossings = (1 until blocks).filter { (amounts[it - 1] <= 0.0) != (amounts[it] <= 0.0) }
        val ordinary = (1 until blocks).filter { it !in crossings }

        fun d2(n: Int): Double = abs(node[n] - 2.0 * node[n - 1] + node[n - 2])

        fun atBoundaries(boundaries: List<Int>): Double = boundaries.flatMap { b -> (0 until edge).map { b * blockFrames + it } }.maxOf { d2(it) }

        val atCrossings = atBoundaries(crossings)
        val atOrdinary = atBoundaries(ordinary)

        println("STRIP-LAW | continuity | crossings at blocks $crossings | second difference at them $atCrossings, at ordinary boundaries $atOrdinary")

        withClue("not vacuous: the LFO crosses 0 in both directions inside the render") {
            crossings.size shouldBe 2
        }
        withClue("no click at a crossing: its second difference is within the largest at an ordinary block boundary") {
            (atCrossings <= atOrdinary) shouldBe true
        }
    }

    "distort: a NaN modulated amount silences the shaper for that block, and the node recovers" {
        // `amt <= 0.0` is false for a NaN, so the block drives with NaN, `shape(x * NaN)` is NaN and
        // the core's guard writes 0 for every shaped sample. What leaves the block is the oversampler's
        // and the DC blocker's decaying tail from the block before (review round 1 asked for "all 0.0";
        // measured, it is not, see the print), bit-equal to the oracle run with that block's drive NaN.
        // The next block is finite and renders the signal again.
        val amounts = listOf(0.3, 0.3, Double.NaN, 0.3, 0.3)
        val blocks = amounts.size
        val input = sine(blocks, 0.9, 220.0)
        val drives = amounts.map { if (it <= 0.0) 1.0 else driveOf(it) }
        val expected = oracleDistort(input, drives, "soft", 1)

        val node = renderNode(ArraySource(input).fusedDistort(PerBlock(amounts), parseDistortionShape("soft"), 1), blocks)
        val nanBlock = node.copyOfRange(2 * blockFrames, 3 * blockFrames)
        val nextBlock = node.copyOfRange(3 * blockFrames, 4 * blockFrames)

        println(
            "STRIP-LAW | NaN amount | NaN block max ${nanBlock.maxOf { abs(it) }}, first ${nanBlock.take(6)}, " +
                "last ${nanBlock.last()} | next block max ${nextBlock.maxOf { abs(it) }}",
        )

        node.bits() shouldBe expected.bits()

        withClue("every sample is finite") {
            node.all { it.isFinite() } shouldBe true
        }
        withClue("the next block recovers: it carries the signal again") {
            (nextBlock.maxOf { abs(it) } > 0.5) shouldBe true
        }
    }
})
