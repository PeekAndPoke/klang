/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.ignitor.ConstantIgnitor
import io.peekandpoke.klang.audio_be.ignitor.IgniteContext
import io.peekandpoke.klang.audio_be.ignitor.Ignitor
import io.peekandpoke.klang.audio_be.ignitor.ScratchBuffers
import io.peekandpoke.klang.audio_be.ignitor.abs
import io.peekandpoke.klang.audio_be.ignitor.div
import io.peekandpoke.klang.audio_be.ignitor.max
import io.peekandpoke.klang.audio_be.ignitor.min
import io.peekandpoke.klang.audio_be.ignitor.minus
import io.peekandpoke.klang.audio_be.ignitor.neg
import io.peekandpoke.klang.audio_be.ignitor.plus
import io.peekandpoke.klang.audio_be.ignitor.pow
import io.peekandpoke.klang.audio_be.ignitor.times
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.constants.KNOB_GLIDE_SECONDS
import io.peekandpoke.klang.common.infra.klangMapOf
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sin
import kotlin.random.Random

/**
 * The arithmetic stages on the bus ([KatalystArithmeticEffect],
 * `docs/tasks-archive/2026-10/20261011-katalyst-arithmetic.md`):
 * every sample is
 * the Ignitor's sample for the same numbers, and a moving operand glides.
 *
 * The parity oracle is the Ignitor RUNTIME (the nodes `IgnitorDslRuntime` builds for each operator, fed the same
 * samples through a source node), not the law functions this stage calls, so a stage that applies the wrong law, swaps
 * the operands or reaches the wrong channel is caught. The laws themselves are pinned by a few numbers worked out here
 * by hand, and by the Ignitor's own suite.
 */
class KatalystArithmeticEffectSpec : StringSpec({

    val blockFrames = 128
    val sampleRate = 44100
    val glideBlocks = round(KNOB_GLIDE_SECONDS * sampleRate / blockFrames).toInt()

    fun build(vararg stages: KatalystStageDsl) = KatalystChainBuilder.build(
        dsl = KatalystDsl.of(*stages),
        sampleRate = sampleRate,
        blockFrames = blockFrames,
        rings = SizedBuffers.forRings(sampleRate),
        reverbs = ReverbUnits(sampleRate),
    )

    fun ctx() = KatalystContext(blockFrames = blockFrames, mixBuffer = StereoBuffer(blockFrames))

    /** Plays a fixed block of samples, the bus as the Ignitor sees it. */
    class Source(private val samples: DoubleArray) : Ignitor {
        override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
            for (i in ctx.offset until ctx.offset + ctx.length) {
                buffer[i] = samples[i]
            }
        }
    }

    fun igniteContext() = IgniteContext(
        sampleRate = sampleRate,
        voiceDurationFrames = blockFrames * 8,
        gateEndFrame = blockFrames * 8,
        scratchBuffers = ScratchBuffers(blockFrames),
        random = Random(7),
    ).apply {
        updateOffsetAndLength(offset = 0, length = blockFrames)
    }

    fun render(ignitor: Ignitor): DoubleArray {
        val out = DoubleArray(blockFrames)
        ignitor.generate(out, 220.0, igniteContext())

        return out
    }

    /**
     * A block with what the laws care about: both signs, exact zeros, -0.0, quiet and loud samples, and two near the
     * `safeOut` bound so a product or a power clamps.
     */
    fun input(seed: Int): DoubleArray {
        val random = Random(seed)

        return DoubleArray(blockFrames) { i ->
            when (i % 16) {
                0 -> 0.0
                1 -> -0.0
                2 -> 3e14
                3 -> -3e14
                4 -> 1e-9
                else -> sin(i * 0.37 + seed) * (0.2 + random.nextDouble())
            }
        }
    }

    /** The chain's output for [stage] on the two input blocks, one block, operands as authored. */
    fun bus(stage: KatalystStageDsl, left: DoubleArray, right: DoubleArray): Pair<DoubleArray, DoubleArray> {
        val chain = build(stage)
        val c = ctx()
        left.copyInto(c.mixBuffer.left)
        right.copyInto(c.mixBuffer.right)
        chain.applyParams(null)
        chain.process(c)

        return c.mixBuffer.left.copyOf() to c.mixBuffer.right.copyOf()
    }

    // ── Parity with the Ignitor ──────────────────────────────────────────────────────────────────

    "every operator gives the Ignitor's sample for the same numbers, both channels, clamps and zeros included" {
        val left = input(seed = 1)
        val right = input(seed = 2)

        // Each row: the stage, and the Ignitor node the runtime builds for that operator over a source.
        val rows: List<Triple<String, KatalystStageDsl, (Ignitor) -> Ignitor>> = listOf(
            Triple("plus 0.25", KatalystStageDsl.Plus(IgnitorDsl.Constant(0.25)), { s -> s + ConstantIgnitor(0.25) }),
            Triple("minus 0.25", KatalystStageDsl.Minus(IgnitorDsl.Constant(0.25)), { s -> s.minus(ConstantIgnitor(0.25)) }),
            Triple("times 10", KatalystStageDsl.Times(IgnitorDsl.Constant(10.0)), { s -> s * ConstantIgnitor(10.0) }),
            Triple("times -1 (neg)", KatalystStageDsl.Times(IgnitorDsl.Constant(-1.0)), { s -> s.neg() }),
            Triple("div 3", KatalystStageDsl.Div(IgnitorDsl.Constant(3.0)), { s -> s.div(ConstantIgnitor(3.0)) }),
            Triple("div 0", KatalystStageDsl.Div(IgnitorDsl.Constant(0.0)), { s -> s.div(ConstantIgnitor(0.0)) }),
            Triple("div 1e-20", KatalystStageDsl.Div(IgnitorDsl.Constant(1e-20)), { s -> s.div(ConstantIgnitor(1e-20)) }),
            Triple("pow 2", KatalystStageDsl.Pow(IgnitorDsl.Constant(2.0)), { s -> s.pow(ConstantIgnitor(2.0)) }),
            Triple("pow 0.5", KatalystStageDsl.Pow(IgnitorDsl.Constant(0.5)), { s -> s.pow(ConstantIgnitor(0.5)) }),
            Triple("min 0.3", KatalystStageDsl.Min(IgnitorDsl.Constant(0.3)), { s -> s.min(ConstantIgnitor(0.3)) }),
            Triple("max -0.3", KatalystStageDsl.Max(IgnitorDsl.Constant(-0.3)), { s -> s.max(ConstantIgnitor(-0.3)) }),
            Triple("abs", KatalystStageDsl.Abs, { s -> s.abs() }),
        )

        for ((name, stage, node) in rows) {
            val (busLeft, busRight) = bus(stage, left, right)
            val voiceLeft = render(node(Source(left)))
            val voiceRight = render(node(Source(right)))

            for (i in 0 until blockFrames) {
                withClue("$name, left frame $i (in ${left[i]})") { busLeft[i] shouldBe voiceLeft[i] }
                withClue("$name, right frame $i (in ${right[i]})") { busRight[i] shouldBe voiceRight[i] }
            }
        }
    }

    "the laws, by hand: a clamped product, a quotient by zero, a signed power, a cap, a floor, a rectifier" {
        fun one(stage: KatalystStageDsl, x: Double): Double {
            val samples = DoubleArray(blockFrames) { x }

            return bus(stage, samples, samples).first[0]
        }

        one(KatalystStageDsl.Plus(IgnitorDsl.Constant(0.25)), 0.5) shouldBe 0.75
        one(KatalystStageDsl.Minus(IgnitorDsl.Constant(0.25)), 0.5) shouldBe 0.25
        one(KatalystStageDsl.Times(IgnitorDsl.Constant(10.0)), 3e14) shouldBe 1e15
        one(KatalystStageDsl.Times(IgnitorDsl.Constant(-1.0)), 0.5) shouldBe -0.5
        one(KatalystStageDsl.Div(IgnitorDsl.Constant(4.0)), 0.5) shouldBe 0.125
        one(KatalystStageDsl.Div(IgnitorDsl.Constant(0.0)), 0.5) shouldBe 0.0
        one(KatalystStageDsl.Pow(IgnitorDsl.Constant(2.0)), -0.5) shouldBe -0.25
        one(KatalystStageDsl.Min(IgnitorDsl.Constant(0.3)), 0.5) shouldBe 0.3
        one(KatalystStageDsl.Min(IgnitorDsl.Constant(0.3)), 0.2) shouldBe 0.2
        one(KatalystStageDsl.Max(IgnitorDsl.Constant(-0.3)), -0.5) shouldBe -0.3
        one(KatalystStageDsl.Abs, -0.5) shouldBe 0.5
    }

    // ── The operand as an orbit knob ─────────────────────────────────────────────────────────────

    /** Runs one block of a constant [probe] through [chain] and returns the left channel. */
    fun block(chain: KatalystChain, probe: Double): DoubleArray {
        val c = ctx()
        c.mixBuffer.left.fill(probe)
        c.mixBuffer.right.fill(probe)
        chain.process(c)

        return c.mixBuffer.left.copyOf()
    }

    "a slot operand's first value snaps, and a katp move glides per sample over the glide time, landing exactly" {
        val probe = 0.5
        val chain = build(KatalystStageDsl.Times(IgnitorDsl.Param("t", 1.0)))
        chain.applyParams(klangMapOf("t" to 0.8))

        // Fresh: the first value arrives at once.
        block(chain, probe).forEach { it shouldBe probe * 0.8 }

        chain.applyParams(klangMapOf("t" to 0.2))
        val glide = (1..glideBlocks).map { block(chain, probe) }

        // Block k ends at 0.8 + (0.2 - 0.8) * k / G, and each block ramps there from where the last ended.
        for ((index, samples) in glide.withIndex()) {
            val k = index + 1
            val end = 0.8 + (0.2 - 0.8) * k / glideBlocks

            withClue("glide block $k, last frame") {
                samples[blockFrames - 1] shouldBe (probe * end plusOrMinus 1e-12)
            }
        }

        // A ramp, not a staircase: no two neighbouring samples apart by more than a few per-sample steps, across the
        // block boundaries too (a per-block step here would be 0.5 * 0.6 / G, about 0.018).
        val joined = doubleArrayOf(probe * 0.8) + glide.reduce { a, b -> a + b }

        for (i in 1 until joined.size) {
            withClue("sample $i of the glide") { abs(joined[i] - joined[i - 1]) shouldBeLessThan 1e-3 }
        }

        glide.last()[blockFrames - 1] shouldBe probe * 0.2
        block(chain, probe).forEach { it shouldBe probe * 0.2 }
    }

    "a pow operand ramps per sample too: the law is taken with the operand at each sample" {
        val probe = 0.5
        val chain = build(KatalystStageDsl.Pow(IgnitorDsl.Param("e", 1.0)))
        chain.applyParams(klangMapOf("e" to 1.0))
        block(chain, probe)

        chain.applyParams(klangMapOf("e" to 2.0))
        val first = block(chain, probe)
        val step = (1.0 / glideBlocks) / blockFrames

        // The first glide block ramps the exponent from 1 to 1 + 1/G, so its first sample is 0.5^(1 + step).
        first[0] shouldBe (probe.pow(1.0 + step) plusOrMinus 1e-12)
        first[blockFrames - 1] shouldBe (probe.pow(1.0 + 1.0 / glideBlocks) plusOrMinus 1e-12)
    }

    "an unset operand is the operator's identity, reached by a glide; an unset cap passes the bus and its first value arrives at once" {
        val probe = 0.5

        // A constant NaN: plus reads 0, the bus passes.
        val plus = build(KatalystStageDsl.Plus(IgnitorDsl.Constant(Double.NaN)))
        plus.applyParams(null)
        block(plus, probe).forEach { it shouldBe probe }

        // A slot cleared to NaN: times glides back to 1.
        val times = build(KatalystStageDsl.Times(IgnitorDsl.Param("t", 0.5)))
        times.applyParams(null)
        block(times, probe).forEach { it shouldBe probe * 0.5 }
        times.applyParams(klangMapOf("t" to Double.NaN))
        val back = (1..glideBlocks).map { block(times, probe) }
        back.first()[blockFrames - 1] shouldBe (probe * (0.5 + 0.5 / glideBlocks) plusOrMinus 1e-12)
        back.last()[blockFrames - 1] shouldBe probe

        // A cap with no finite operand passes the bus, and its first operand arrives at once.
        val cap = build(KatalystStageDsl.Min(IgnitorDsl.Param("c", Double.NaN)))
        cap.applyParams(null)
        block(cap, probe).forEach { it shouldBe probe }
        cap.applyParams(klangMapOf("c" to 0.3))
        block(cap, probe).forEach { it shouldBe 0.3 }

        // A move of the cap after that glides: halfway between blocks, not a jump to the new bound.
        cap.applyParams(klangMapOf("c" to 0.1))
        val moving = block(cap, probe)
        moving[blockFrames - 1] shouldBe (0.3 + (0.1 - 0.3) / glideBlocks plusOrMinus 1e-12)
    }

    "a moving divisor glides as a level: never past the louder settled quotient, a ramp not a step, and it divides once settled" {
        val probe = 0.7

        // Each case: where the divisor stands, where katp moves it, and the true quotient it lands on (0 for 0), written
        // out. The last one lands where the true division (0.07777777777777778) and a multiply by the reciprocal
        // (0.07777777777777777) round apart.
        val cases = listOf(
            Triple(1.0, -1.0, -0.7),
            Triple(1.0, 0.0, 0.0),
            Triple(0.0, 1.0, 0.7),
            Triple(-1.0, 4.0, 0.7 / 4),
            Triple(1.0, 9.0, 0.07777777777777778),
        )

        for ((from, to, lands) in cases) {
            val chain = build(KatalystStageDsl.Div(IgnitorDsl.Param("d", 1.0)))
            chain.applyParams(klangMapOf("d" to from))
            val before = block(chain, probe)
            chain.applyParams(klangMapOf("d" to to))

            val glide = (1..glideBlocks + 1).map { block(chain, probe) }
            val joined = doubleArrayOf(before.last()) + glide.reduce { a, b -> a + b }
            // The loudest the bus may get: the louder of the two settled quotients (a divisor of 0 is silence).
            val ceiling = listOf(from, to).maxOf { if (it == 0.0) 0.0 else abs(probe / it) }

            for (i in joined.indices) {
                withClue("div $from -> $to, sample $i: within the settled quotients") {
                    abs(joined[i]) shouldBeLessThan ceiling + 1e-12
                }
            }

            // A ramp, not a step: a one-block step here would move by at least 0.7 / 4 / G, about 0.01.
            for (i in 1 until joined.size) {
                withClue("div $from -> $to, sample $i: a ramp") { abs(joined[i] - joined[i - 1]) shouldBeLessThan 1e-3 }
            }

            withClue("div $from -> $to divides once settled") { glide.last().forEach { it shouldBe lands } }
        }

        // 1 to -1 by hand: the level ends the first glide block at 1 + (-1 - 1) / G, and passes through silence.
        val chain = build(KatalystStageDsl.Div(IgnitorDsl.Param("d", 1.0)))
        chain.applyParams(klangMapOf("d" to 1.0))
        block(chain, probe)
        chain.applyParams(klangMapOf("d" to -1.0))
        val glide = (1..glideBlocks).map { block(chain, probe) }

        glide.first()[blockFrames - 1] shouldBe (probe * (1.0 - 2.0 / glideBlocks) plusOrMinus 1e-12)
        glide.minOf { samples -> samples.minOf { abs(it) } } shouldBeLessThan 1e-3
    }

    "every operator's unset operand is its identity: a slot cleared to NaN lands on the bus unchanged, either sign" {
        // Each stage starts on a real operand, so a wrong identity cannot hide behind an operand that already was one.
        val stages = listOf(
            "plus" to KatalystStageDsl.Plus(IgnitorDsl.Param("o", 0.25)),
            "minus" to KatalystStageDsl.Minus(IgnitorDsl.Param("o", 0.25)),
            "times" to KatalystStageDsl.Times(IgnitorDsl.Param("o", 0.5)),
            "div" to KatalystStageDsl.Div(IgnitorDsl.Param("o", 4.0)),
            "pow" to KatalystStageDsl.Pow(IgnitorDsl.Param("o", 2.0)),
            "min (cap)" to KatalystStageDsl.Min(IgnitorDsl.Param("o", 0.3)),
            "max (floor)" to KatalystStageDsl.Max(IgnitorDsl.Param("o", -0.3)),
        )

        for ((name, stage) in stages) {
            for (probe in listOf(0.5, -0.5)) {
                val chain = build(stage)
                chain.applyParams(null)
                block(chain, probe)
                chain.applyParams(klangMapOf("o" to Double.NaN))

                val settled = (1..glideBlocks + 1).map { block(chain, probe) }.last()

                withClue("$name cleared, probe $probe") { settled.forEach { it shouldBe probe } }
            }
        }
    }

    "a cap cleared to NaN lets go at the next block, and its next operand arrives at once" {
        val probe = 0.5
        val cap = build(KatalystStageDsl.Min(IgnitorDsl.Param("c", 0.3)))
        cap.applyParams(null)
        block(cap, probe).forEach { it shouldBe 0.3 }

        cap.applyParams(klangMapOf("c" to Double.NaN))
        block(cap, probe).forEach { it shouldBe probe }

        cap.applyParams(klangMapOf("c" to 0.2))
        block(cap, probe).forEach { it shouldBe 0.2 }
    }

    "a reset forgets the glide: the next life's first operand snaps" {
        val probe = 0.5
        val chain = build(KatalystStageDsl.Times(IgnitorDsl.Param("t", 1.0)))
        chain.applyParams(klangMapOf("t" to 0.8))
        block(chain, probe)
        chain.applyParams(klangMapOf("t" to 0.2))
        block(chain, probe)

        chain.reset()
        chain.applyParams(klangMapOf("t" to 0.4))
        block(chain, probe).forEach { it shouldBe probe * 0.4 }
    }
})
