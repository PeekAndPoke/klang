/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.katalyst

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.Oversampler
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystKnobDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * The Katalyst oversampling region ([KatalystRegionEffect], KLANG-00214 (oversampling regions) step 3a): the bus
 * converted up, the body's stages at the region's rate, converted down; the steps a factor takes, the cascade continued
 * from the chain's rate, every stage inside keeping its sound in seconds and Hz.
 *
 * **The oracles are bare [Oversampler]s**: a body of linear stages (a gain, a nested region with an empty body) is the
 * input through the converters
 * the region should hold, written out here; a stage that keeps its time or its room is held against the same stage
 * outside a region. No expected value comes from the region under test.
 */
class KatalystRegionEffectSpec : StringSpec({

    val sampleRate = 44100
    val blockFrames = 128

    fun c(value: Double) = KatalystKnobDsl.Constant(value)

    fun body(vararg stages: KatalystStageDsl) = KatalystDsl.of(*stages)

    fun by(factor: Int, vararg stages: KatalystStageDsl) = KatalystStageDsl.OversampleBy(factor = factor, body = body(*stages))

    fun atLeast(factor: Int, vararg stages: KatalystStageDsl) =
        KatalystStageDsl.OversampleAtLeast(factor = factor, body = body(*stages))

    fun gain(value: Double) = KatalystStageDsl.Gain(gain = c(value))

    fun chain(vararg stages: KatalystStageDsl, reverbs: ReverbUnits = ReverbUnits(sampleRate)): KatalystChain =
        KatalystChainBuilder.build(
            dsl = KatalystDsl.of(*stages),
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            rings = SizedBuffers.forRings(sampleRate),
            reverbs = reverbs,
        ).also { it.applyParams(null) }

    fun left(k: Int): Double =
        if (k < 0) 0.0 else 0.4 * sin(2.0 * PI * 110.0 * k / sampleRate) + 0.15 * sin(2.0 * PI * 9750.0 * k / sampleRate + 0.3)

    fun right(k: Int): Double = if (k < 0) 0.0 else 0.35 * sin(2.0 * PI * 165.0 * k / sampleRate + 1.0)

    class Take(val l: DoubleArray, val r: DoubleArray)

    /** Runs [chain] over [blocks] blocks of [left] / [right]. */
    fun run(
        chain: KatalystChain,
        blocks: Int,
        left: (Int) -> Double = ::left,
        right: (Int) -> Double = ::right,
    ): Take {
        val ctx = KatalystContext(blockFrames = blockFrames, mixBuffer = StereoBuffer(blockFrames))
        val l = DoubleArray(blocks * blockFrames)
        val r = DoubleArray(blocks * blockFrames)

        for (b in 0 until blocks) {
            for (i in 0 until blockFrames) {
                ctx.mixBuffer.left[i] = left(b * blockFrames + i)
                ctx.mixBuffer.right[i] = right(b * blockFrames + i)
            }

            chain.process(ctx)
            ctx.mixBuffer.left.copyInto(destination = l, destinationOffset = b * blockFrames, startIndex = 0, endIndex = blockFrames)
            ctx.mixBuffer.right.copyInto(destination = r, destinationOffset = b * blockFrames, startIndex = 0, endIndex = blockFrames)
        }

        return Take(l = l, r = r)
    }

    /**
     * The oracle: one channel of the input through nested converters, written with bare [Oversampler]s. [levels] are
     * the regions from the outside in, each `(steps, firstStage)`; at the innermost rate [inside] runs on the block.
     */
    fun nested(
        input: (Int) -> Double,
        blocks: Int,
        levels: List<Pair<Int, Int>>,
        inside: (AudioBuffer, Int) -> Unit,
    ): DoubleArray {
        val converters = levels.map { (steps, first) -> Oversampler(stages = steps, firstStage = first) }
        val out = DoubleArray(blocks * blockFrames)
        // One buffer per rate: the block, then each region's
        val buffers = mutableListOf(AudioBuffer(blockFrames))
        var frames = blockFrames

        for ((steps, _) in levels) {
            frames = frames shl steps
            buffers.add(AudioBuffer(frames))
        }

        for (b in 0 until blocks) {
            for (i in 0 until blockFrames) {
                buffers[0][i] = input(b * blockFrames + i)
            }

            var length = blockFrames

            for (n in converters.indices) {
                converters[n].upsample(source = buffers[n], offset = 0, length = length, work = buffers[n + 1])
                length = length shl converters[n].stages
            }

            inside(buffers.last(), length)

            for (n in converters.indices.reversed()) {
                length = length shr converters[n].stages
                converters[n].decimate(work = buffers[n + 1], target = buffers[n], offset = 0, length = length)
            }

            buffers[0].copyInto(destination = out, destinationOffset = b * blockFrames, startIndex = 0, endIndex = blockFrames)
        }

        return out
    }

    fun Take.shouldMatch(expectLeft: DoubleArray, expectRight: DoubleArray) {
        for (k in l.indices) {
            withClue("sample $k") {
                l[k] shouldBe (expectLeft[k] plusOrMinus 1e-12)
                r[k] shouldBe (expectRight[k] plusOrMinus 1e-12)
            }
        }
    }

    fun scale(by: Double): (AudioBuffer, Int) -> Unit = { buffer, length ->
        for (i in 0 until length) {
            buffer[i] *= by
        }
    }

    /** An unshaped round trip of [steps] from cascade stage [first], in place: a `distort` at amount 0 at that rate. */
    fun twin(steps: Int, first: Int): (AudioBuffer, Int) -> Unit {
        val os = Oversampler(stages = steps, firstStage = first)
        var work = AudioBuffer(0)

        return { buffer, length ->
            if (work.size < length shl steps) {
                work = AudioBuffer(length shl steps)
            }

            os.upsample(source = buffer, offset = 0, length = length, work = work)
            os.decimate(work = work, target = buffer, offset = 0, length = length)
        }
    }

    // ── The conversions ──────────────────────────────────────────────────────────────────────────

    "a region is the bus through one round trip, the body at its rate: a gain of 0.5 at 4x" {
        val blocks = 6
        val out = run(chain(by(4, gain(0.5))), blocks = blocks)

        out.shouldMatch(
            expectLeft = nested(::left, blocks, levels = listOf(2 to 0), inside = scale(0.5)),
            expectRight = nested(::right, blocks, levels = listOf(2 to 0), inside = scale(0.5)),
        )
    }

    "the factor is rounded down to a power of two, and the rate stops at 16x" {
        listOf(2 to 1, 3 to 1, 4 to 2, 7 to 2, 8 to 3, 16 to 4, 64 to 4, 1_000_000 to 4).forEach { (factor, steps) ->
            withClue("oversampleBy($factor)") {
                val blocks = 3
                val out = run(chain(by(factor, gain(0.5))), blocks = blocks)

                out.shouldMatch(
                    expectLeft = nested(::left, blocks, levels = listOf(steps to 0), inside = scale(0.5)),
                    expectRight = nested(::right, blocks, levels = listOf(steps to 0), inside = scale(0.5)),
                )
            }
        }
    }

    "a region with no step to take is no region: its stages in place, bit for bit" {
        // A factor of 1 or less, and a floor of 1x
        listOf(by(1, gain(0.5)), by(0, gain(0.5)), by(-4, gain(0.5)), atLeast(1, gain(0.5))).forEach { region ->
            withClue(region) {
                val inlined = chain(region)

                inlined.pipeline.single().shouldBeInstanceOf<KatalystGainEffect>()

                val expected = run(chain(gain(0.5)), blocks = 3)
                val out = run(inlined, blocks = 3)

                out.l shouldBe expected.l
                out.r shouldBe expected.r
            }
        }
    }

    "nested, a region steps on from the rate it is in: oversampleBy(2) inside oversampleBy(2) is the cascade's two stages" {
        val blocks = 5
        val out = run(chain(by(2, by(2, gain(0.5)))), blocks = blocks)

        // The inner step is the cascade's SECOND stage (firstStage 1), not a second first stage
        out.shouldMatch(
            expectLeft = nested(::left, blocks, levels = listOf(1 to 0, 1 to 1), inside = scale(0.5)),
            expectRight = nested(::right, blocks, levels = listOf(1 to 0, 1 to 1), inside = scale(0.5)),
        )
    }

    "oversampleAtLeast takes only the missing steps, and inside a region at or above its floor it converts nothing" {
        val blocks = 4

        // 2x, then a floor of 8x: the two missing steps, from the cascade's second stage
        run(chain(by(2, atLeast(8, gain(0.5)))), blocks = blocks).shouldMatch(
            expectLeft = nested(::left, blocks, levels = listOf(1 to 0, 2 to 1), inside = scale(0.5)),
            expectRight = nested(::right, blocks, levels = listOf(1 to 0, 2 to 1), inside = scale(0.5)),
        )

        // 8x then a floor of 4x: nothing; the body runs in the outer region's
        val within = chain(by(8, atLeast(4, gain(0.5))))
        val region = within.pipeline.single().shouldBeInstanceOf<KatalystRegionEffect>()

        region.body.pipeline.single().shouldBeInstanceOf<KatalystGainEffect>()

        run(within, blocks = blocks).shouldMatch(
            expectLeft = nested(::left, blocks, levels = listOf(3 to 0), inside = scale(0.5)),
            expectRight = nested(::right, blocks, levels = listOf(3 to 0), inside = scale(0.5)),
        )
    }

    // ── The distort stage inside ─────────────────────────────────────────────────────────────────

    "a distort inside a region keeps its DC blocker in Hz: a DC step decays in the same time at 4x as at 1x" {
        // A small amount of the soft shape is nearly linear; the house DC blocker then takes the step down over about
        // 23 ms, at the bus's rate and at the region's alike. Measured from 64 frames after the peak, past the ring of
        // the region's converters on the step's edge (which the stage outside a region does not have)
        val stage = KatalystStageDsl.Distort(amount = c(0.1))
        val step: (Int) -> Double = { 0.1 }
        val later = 441

        fun decay(chain: KatalystChain): Double {
            val out = run(chain, blocks = 8, left = step, right = step).l
            val from = out.indices.maxBy { abs(out[it]) } + 64

            return out[from + later] / out[from]
        }

        val outside = decay(chain(stage))
        val inside = decay(chain(by(4, stage)))

        withClue("left after 10 ms: outside $outside, at 4x $inside") {
            outside shouldBeGreaterThan 0.5
            inside shouldBe (outside plusOrMinus 0.02)
        }
    }

    // ── Latency and the parallel ─────────────────────────────────────────────────────────────────

    "the region reports its round trip as an oversampler, and a latency within half a frame of its impulse's centroid" {
        // A linear region delays the bass by the centroid of its impulse response (the oversampler's group delay at
        // DC, `OversamplerGroupDelaySpec`), measured here, never computed from the region's own formula
        val at = 300
        val impulse: (Int) -> Double = { k -> if (k == at) 1.0 else 0.0 }

        fun centroid(chain: KatalystChain): Double {
            val out = run(chain, blocks = 40, left = impulse, right = impulse).l
            var weight = 0.0
            var moment = 0.0

            for (k in out.indices) {
                weight += out[k]
                moment += k * out[k]
            }

            return moment / weight - at
        }

        listOf(
            by(4, gain(1.0)),
            // An 8x floor inside a 4x region: its step at the region's rate, divided down
            by(4, atLeast(8)),
            // A 4x floor inside a 2x region: its 2.66 region frames rounded first would be 1.5, not 1.33
            by(2, atLeast(4)),
            // The same floor in a parallel beside a dry branch: the parallel's latency unrounded too
            by(2, KatalystStageDsl.Parallel(branches = listOf(KatalystDsl(emptyList()), body(atLeast(4))))),
            // Nested: the inner round trip from the cascade's second stage
            by(2, by(2, gain(1.0))),
            by(16, gain(1.0)),
        ).forEach { stage ->
            val built = chain(stage)
            val region = built.pipeline.single().shouldBeInstanceOf<KatalystRegionEffect>()
            val measured = centroid(built)

            withClue("$stage: reports ${region.latencyFrames}, the centroid is $measured") {
                abs(region.latencyFrames - measured) shouldBeLessThan 0.5
            }
        }

        val plain = chain(by(4, gain(0.5))).pipeline.single().shouldBeInstanceOf<KatalystRegionEffect>()

        plain.oversamplers shouldBe listOf(2)
    }

    "in a parallel, the dry branch gets the region's round trip as its phase twin: the sum is twice the twin" {
        val blocks = 6
        val out = run(chain(KatalystStageDsl.Parallel(branches = listOf(KatalystDsl(emptyList()), body(by(4, gain(1.0)))))), blocks)

        out.shouldMatch(
            expectLeft = nested(::left, blocks, levels = listOf(2 to 0), inside = scale(2.0)),
            expectRight = nested(::right, blocks, levels = listOf(2 to 0), inside = scale(2.0)),
        )
    }

    "a parallel pads a branch's region by the nearest whole frame of its body's lookahead: the sum as flat as at 1x" {
        // A compressor that compresses nothing is its lookahead, a pure delay: 3 ms is 529.2 frames at 4x, so 132.3 base
        // frames, padded by 132. Rounded twice (the region's latency, then less its round trip) the pad was 133
        val linear = KatalystStageDsl.Compressor(
            threshold = c(40.0),
            ratio = c(1.0),
            knee = c(0.0),
            attack = c(0.01),
            release = c(0.1),
            lookahead = 0.003,
        )
        val dry = KatalystDsl(emptyList())

        fun peak(chain: KatalystChain, freq: Double): Double {
            val tone: (Int) -> Double = { k -> 0.25 * sin(2.0 * PI * freq * k / sampleRate) }
            val out = run(chain, blocks = 60, left = tone, right = tone).l
            var peak = 0.0

            for (k in 30 * blockFrames until out.size) {
                peak = maxOf(peak, abs(out[k]))
            }

            return peak
        }

        for (freq in listOf(2000.0, 10000.0, 15000.0, 18000.0)) {
            val atBase = peak(chain(KatalystStageDsl.Parallel(branches = listOf(dry, body(linear)))), freq)
            val inRegion = peak(chain(KatalystStageDsl.Parallel(branches = listOf(dry, body(by(4, linear))))), freq)

            withClue("$freq Hz: the sum at 1x $atBase, with the region $inRegion") {
                (inRegion / atBase) shouldBeGreaterThan 0.9
            }
        }
    }

    "a parallel inside a region twins from the region's stage: a dry branch beside an 8x floor in a 4x region" {
        val blocks = 6
        val stage = by(4, KatalystStageDsl.Parallel(branches = listOf(KatalystDsl(emptyList()), body(atLeast(8)))))
        val out = run(chain(stage), blocks)
        val twinL = twin(steps = 1, first = 2)
        val twinR = twin(steps = 1, first = 2)

        // Both branches: the input through the cascade's third stage's round trip at 4x; summed, twice that
        out.shouldMatch(
            expectLeft = nested(::left, blocks, levels = listOf(2 to 0)) { b, n -> twinL(b, n); scale(2.0)(b, n) },
            expectRight = nested(::right, blocks, levels = listOf(2 to 0)) { b, n -> twinR(b, n); scale(2.0)(b, n) },
        )
    }

    // ── Stages that keep their time and their room ───────────────────────────────────────────────

    "a delay inside a region keeps its time in seconds: the echo 10 ms after the dry, at 4x as at 1x" {
        val delay = KatalystStageDsl.Delay(wet = c(1.0), time = c(0.010), feedback = c(0.0))
        val impulse: (Int) -> Double = { k -> if (k == 3 * blockFrames) 1.0 else 0.0 }

        fun gap(chain: KatalystChain): Int {
            val out = run(chain, blocks = 12, left = impulse, right = impulse).l
            val peaks = out.indices.sortedByDescending { abs(out[it]) }
            // The two strongest samples far apart: the dry and its echo
            val first = peaks[0]
            val second = peaks.first { abs(it - first) > 100 }

            return abs(second - first)
        }

        val outside = gap(chain(delay))

        outside shouldBe 441
        gap(chain(by(4, delay))) shouldBe outside
    }

    "a reverb inside a region keeps its room: its tail as long and as bright as outside, from a unit at the region's rate" {
        val units = ReverbUnits(sampleRate)
        val reverb = KatalystStageDsl.Reverb(wet = c(1.0), size = c(5.0))
        val impulse: (Int) -> Double = { k -> if (k == 0) 1.0 else 0.0 }
        val blocks = 300

        /** The tail after the dry: its energy late against early (length) and its first difference against itself (brightness). */
        fun room(chain: KatalystChain): Pair<Double, Double> {
            val out = run(chain, blocks = blocks, left = impulse, right = impulse).l
            var early = 0.0
            var late = 0.0
            var energy = 0.0
            var edges = 0.0

            for (k in 2000 until out.size) {
                val e = out[k] * out[k]

                if (k < 12000) {
                    early += e
                } else {
                    late += e
                }

                energy += e
                edges += (out[k] - out[k - 1]) * (out[k] - out[k - 1])
            }

            return late / early to edges / energy
        }

        // The default damping, and a lowpass, which is read against the base rate's Nyquist; at 4x, and at 4x reached
        // as 2x inside 2x, whose unit is mapped by the whole factor
        listOf(reverb, reverb.copy(lowpass = c(5000.0))).forEach { stage ->
            val (lengthOut, brightnessOut) = room(chain(stage))

            listOf("4x" to by(4, stage), "2x in 2x" to by(2, by(2, stage))).forEach { (name, region) ->
                val (lengthIn, brightnessIn) = room(chain(region, reverbs = units))

                withClue("lowpass ${stage.lowpass}, $name: length late/early outside $lengthOut, inside $lengthIn") {
                    (lengthIn / lengthOut) shouldBe (1.0 plusOrMinus 0.1)
                }

                withClue("lowpass ${stage.lowpass}, $name: brightness outside $brightnessOut, inside $brightnessIn") {
                    (brightnessIn / brightnessOut) shouldBe (1.0 plusOrMinus 0.1)
                }
            }
        }

        // The unit came from the shelf for 4x
        val scaled = units.atFactor(4)

        // The units came from the one shelf for 4x, the nested regions' too
        scaled.sampleRate shouldBe 4 * sampleRate
        scaled.allocations shouldBe 4
        units.allocations shouldBe 0
        units.atFactor(2).allocations shouldBe 0
    }

    "a knob modulator inside a region keeps the cylinder's clock: a perlin gain moves as it does outside, block for block" {
        // A modulator ticks once a block at any rate; a clock built on the region's rate would walk a noise 4x faster.
        // The gain in force after each block, inside and outside, is the same number.
        val moving = KatalystStageDsl.Gain(gain = KatalystKnobDsl.Plus(c(1.0), KatalystKnobDsl.PerlinNoise(rate = c(3.0))))
        val outside = chain(moving)
        val inside = chain(by(4, moving))
        val outsideGain = outside.pipeline.single().shouldBeInstanceOf<KatalystGainEffect>()
        val insideGain = inside.pipeline.single().shouldBeInstanceOf<KatalystRegionEffect>()
            .body.pipeline.single().shouldBeInstanceOf<KatalystGainEffect>()
        val seen = mutableSetOf<Double>()

        for (b in 0 until 200) {
            run(outside, blocks = 1)
            run(inside, blocks = 1)

            withClue("block $b") {
                insideGain.gain shouldBe outsideGain.gain
            }

            seen.add(outsideGain.gain)
        }

        // The knob did move
        seen.size shouldBeGreaterThan 100
    }

    // ── The duck and the lifecycle ───────────────────────────────────────────────────────────────

    "a duck in a region's body is the cylinder's duck: taken out of the body, run by the chain" {
        val duck = KatalystStageDsl.Duck(cylinder = c(1.0), depth = c(0.5))
        val built = chain(by(4, gain(1.0), duck))

        built.duck.shouldNotBeNull()

        val region = built.pipeline.single().shouldBeInstanceOf<KatalystRegionEffect>()

        region.body.duck.shouldBeNull()
        region.body.pipeline.single().shouldBeInstanceOf<KatalystGainEffect>()
    }

    "the region holds its tail while the down conversion rings out, and reset forgets it" {
        val built = chain(by(4, gain(1.0)))
        val region = built.pipeline.single().shouldBeInstanceOf<KatalystRegionEffect>()
        val burst: (Int) -> Double = { k -> if (k < blockFrames) 0.5 else 0.0 }

        region.hasTail() shouldBe false

        run(built, blocks = 1, left = burst, right = burst)
        region.hasTail() shouldBe true

        // Quiet blocks: still holding until the ring-out has passed, then not
        val hold = Oversampler.tailFrames(stages = 2)
        val quietBlocks = (hold + blockFrames - 1) / blockFrames

        hold shouldBeGreaterThan 0
        run(built, blocks = quietBlocks + 2, left = { 0.0 }, right = { 0.0 })
        region.hasTail() shouldBe false

        run(built, blocks = 1, left = burst, right = burst)
        region.hasTail() shouldBe true
        built.reset()
        region.hasTail() shouldBe false
    }

    "a region passes the chain's questions to its body: a delay that sustains itself, a declared tail" {
        val endless = KatalystStageDsl.Delay(wet = c(1.0), time = c(0.05), feedback = c(1.0))
        val built = chain(by(2, endless))

        built.declaresTail shouldBe true

        run(built, blocks = 8)
        built.sustainsItself() shouldBe true
        // A region declares one whatever its body: its down conversion rings
        chain(by(2, gain(1.0))).declaresTail shouldBe true
        chain(gain(1.0)).declaresTail shouldBe false
    }

    "a stepless region inside a stepped one is inlined there: one region, one gain" {
        val built = chain(by(4, by(1, gain(0.5))))
        val region = built.pipeline.single().shouldBeInstanceOf<KatalystRegionEffect>()

        region.body.pipeline.single().shouldBeInstanceOf<KatalystGainEffect>()
        region.body.firstStage shouldBe 2
        built.firstStage shouldBe 0
    }

    "the converters run a 0.5 sine through unchanged in level: a region of unity is flat to 18 kHz" {
        // A sweep, every 1 kHz: a misplaced first stage or a wrong kernel shows as a level change at the top
        for (freq in 1000..18000 step 1000) {
            val tone: (Int) -> Double = { k -> 0.5 * sin(2.0 * PI * freq * k / sampleRate) }
            val out = run(chain(by(8, gain(1.0))), blocks = 40, left = tone, right = tone).l
            var peak = 0.0

            for (k in 20 * blockFrames until out.size) {
                peak = maxOf(peak, abs(out[k]))
            }

            withClue("$freq Hz: peak $peak") {
                peak shouldBeGreaterThan 0.49
                peak shouldBeLessThan 0.51
            }
        }
    }
})
