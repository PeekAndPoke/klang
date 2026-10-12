/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.booleans.shouldBeFalse
import io.kotest.matchers.booleans.shouldBeTrue
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.StereoBuffer
import io.peekandpoke.klang.audio_be.ignitor.ConstantIgnitor
import io.peekandpoke.klang.audio_be.ignitor.Ignitor
import io.peekandpoke.klang.audio_be.ignitor.abs
import io.peekandpoke.klang.audio_be.ignitor.controlRateValueOrNull
import io.peekandpoke.klang.audio_be.ignitor.div
import io.peekandpoke.klang.audio_be.ignitor.max
import io.peekandpoke.klang.audio_be.ignitor.min
import io.peekandpoke.klang.audio_be.ignitor.minus
import io.peekandpoke.klang.audio_be.ignitor.neg
import io.peekandpoke.klang.audio_be.ignitor.plus
import io.peekandpoke.klang.audio_be.ignitor.pow
import io.peekandpoke.klang.audio_be.ignitor.range
import io.peekandpoke.klang.audio_be.ignitor.times
import io.peekandpoke.klang.audio_be.warehouse.ReverbUnits
import io.peekandpoke.klang.audio_be.warehouse.SizedBuffers
import io.peekandpoke.klang.audio_bridge.KatalystDsl
import io.peekandpoke.klang.audio_bridge.KatalystKnobDsl
import io.peekandpoke.klang.audio_bridge.KatalystStageDsl
import io.peekandpoke.klang.audio_bridge.constants.DELAY_FEEDBACK
import io.peekandpoke.klang.audio_bridge.constants.SLOT_UNSET
import io.peekandpoke.klang.common.infra.KlangMap
import io.peekandpoke.klang.common.infra.klangMapOf

/**
 * The Katalyst knob expressions of step 4 (`docs/tasks/in-progress/katalyst-knob-expressions.md`, D4 and Q5): the
 * evaluator ([KatalystKnobProgram]) and the knob around it ([KatalystKnob]).
 *
 * **The oracle is the Ignitor runtime**, not the law functions: the Ignitor node of the same operator, built over
 * [ConstantIgnitor]s holding the same numbers, asked its control-rate value. So a wrong law, a law on the wrong arm or
 * swapped operands turns a row red, where an oracle written with the laws themselves would follow the mistake. Every
 * leaf placement is run: all constants (the compile-time FOLD), and every mix of slot leaves with finite values (the
 * EVALUATOR, the slots loaded from a `katp` state). A non-finite operand can only be a constant there, because a
 * non-finite slot unsets the knob (Q5, its own rows below).
 *
 * Equal means equal raw bits, -0.0 apart from 0.0, with one exception: two NaNs are equal whatever their payload. The
 * payload of a NaN sum is not observable in the engine (the `binaryLadder` KDoc), and Kotlin/JS does not promise to
 * keep it through a `Float64Array`.
 */
class KatalystKnobExpressionSpec : StringSpec({

    /** The operands: signs, both zeros, the clamp's neighbourhood, a subnormal, huge values and the non-finite ones. */
    val numbers = doubleArrayOf(
        0.37, -2.6, 2.0, -1.0, 0.5, 0.0, -0.0, 1e-16, 5e-324, 3e15, 1e300, -1e300,
        Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY,
    )

    fun c(v: Double) = KatalystKnobDsl.Constant(v)
    fun slot(name: String) = KatalystKnobDsl.Param(name = name, default = 0.0)
    fun ign(v: Double): Ignitor = ConstantIgnitor(v)

    fun shouldBeSameNumber(actual: Double, expected: Double) {
        if (expected.isNaN()) {
            withClue("expected NaN, got $actual") { actual.isNaN().shouldBeTrue() }
        } else {
            withClue("expected $expected (${expected.toRawBits()}), got $actual (${actual.toRawBits()})") {
                actual.toRawBits() shouldBe expected.toRawBits()
            }
        }
    }

    fun oracle(node: Ignitor): Double = node.controlRateValueOrNull(220.0).shouldNotBeNull()

    /** The value of a knob built from [node] after resolving [state]. */
    fun knobValue(node: KatalystKnobDsl, state: KlangMap<Double>?): Double =
        KatalystKnob(node).also { it.resolve(state) }.value

    /**
     * Runs [knob] over [operands] in every leaf placement: leaf `i` is a slot when bit `i` of the mask is set (the
     * placements with a non-finite slot are skipped, Q5 owns those), a constant otherwise. Asserts each against
     * [expected].
     */
    fun everyPlacement(operands: DoubleArray, expected: Double, knob: (List<KatalystKnobDsl>) -> KatalystKnobDsl) {
        for (mask in 0 until (1 shl operands.size)) {
            val state = KlangMap<Double>()
            var skip = false
            val leaves = operands.indices.map { i ->
                if (mask and (1 shl i) != 0) {
                    if (!operands[i].isFinite()) {
                        skip = true
                    }

                    state["s$i"] = operands[i]
                    slot("s$i")
                } else {
                    c(operands[i])
                }
            }

            if (skip) {
                continue
            }

            withClue("placement ${mask.toString(2)} (1 = a slot), operands ${operands.toList()}") {
                shouldBeSameNumber(actual = knobValue(knob(leaves), state), expected = expected)
            }
        }
    }

    class Binary(
        val name: String,
        val knob: (KatalystKnobDsl, KatalystKnobDsl) -> KatalystKnobDsl,
        val ignitor: (Ignitor, Ignitor) -> Ignitor,
    )

    val binaries = listOf(
        Binary("plus", { a, b -> KatalystKnobDsl.Plus(left = a, right = b) }, { a, b -> a + b }),
        Binary("minus", { a, b -> KatalystKnobDsl.Minus(left = a, right = b) }, { a, b -> a.minus(b) }),
        Binary("times", { a, b -> KatalystKnobDsl.Times(left = a, right = b) }, { a, b -> a * b }),
        Binary("div", { a, b -> KatalystKnobDsl.Div(left = a, right = b) }, { a, b -> a.div(b) }),
        Binary("pow", { a, b -> KatalystKnobDsl.Pow(base = a, exp = b) }, { a, b -> a.pow(b) }),
        Binary("min", { a, b -> KatalystKnobDsl.Min(left = a, right = b) }, { a, b -> a.min(b) }),
        Binary("max", { a, b -> KatalystKnobDsl.Max(left = a, right = b) }, { a, b -> a.max(b) }),
    )

    // ── Every op against the Ignitor, bit for bit ────────────────────────────────────────────────────────────────

    binaries.forEach { op ->
        "${op.name}: the knob is the Ignitor's control-rate value of the same tree, folded and evaluated" {
            for (x in numbers) {
                for (y in numbers) {
                    val expected = oracle(op.ignitor(ign(x), ign(y)))

                    withClue("${op.name}($x, $y)") {
                        everyPlacement(doubleArrayOf(x, y), expected) { (a, b) -> op.knob(a, b) }
                    }
                }
            }
        }
    }

    "neg: a multiply by -1 as the Ignitor's neg() builds it, clamp included, folded and evaluated" {
        for (x in numbers) {
            withClue("neg($x)") {
                everyPlacement(doubleArrayOf(x), oracle(ign(x).neg())) { (a) -> KatalystKnobDsl.Neg(inner = a) }
            }
        }
    }

    "abs: the Ignitor's abs, -0.0 kept, folded and evaluated" {
        for (x in numbers) {
            withClue("abs($x)") {
                everyPlacement(doubleArrayOf(x), oracle(ign(x).abs())) { (a) -> KatalystKnobDsl.Abs(inner = a) }
            }
        }
    }

    "range: the Ignitor's range over the value and both bounds, folded and evaluated" {
        val few = doubleArrayOf(0.37, -2.6, -1.0, 1.0, 0.0, -0.0, 1e300, -1e300, Double.NaN, Double.POSITIVE_INFINITY)

        for (v in few) {
            for (from in few) {
                for (to in few) {
                    val expected = oracle(ign(v).range(from = ign(from), to = ign(to)))

                    withClue("range($v, $from, $to)") {
                        everyPlacement(doubleArrayOf(v, from, to), expected) { (a, b, d) ->
                            KatalystKnobDsl.Range(inner = a, from = b, to = d)
                        }
                    }
                }
            }
        }
    }

    "a nested tree wires every register to the right operand, folded and evaluated" {
        // range(x * 2, y - 1, |-(z)|), each leaf in every placement: a wrong register index in a deeper node shows here
        // even where a one-node row cannot see it.
        val triples = listOf(
            doubleArrayOf(0.25, 3.0, -7.5),
            doubleArrayOf(-0.75, -2.0, 1e300),
            doubleArrayOf(0.0, -0.0, 0.5),
        )

        for ((x, y, z) in triples) {
            val expected = oracle((ign(x) * ign(2.0)).range(from = ign(y).minus(ign(1.0)), to = ign(z).neg().abs()))

            withClue("x=$x y=$y z=$z") {
                everyPlacement(doubleArrayOf(x, y, z), expected) { (a, b, d) ->
                    KatalystKnobDsl.Range(
                        inner = KatalystKnobDsl.Times(left = a, right = c(2.0)),
                        from = KatalystKnobDsl.Minus(left = b, right = c(1.0)),
                        to = KatalystKnobDsl.Abs(inner = KatalystKnobDsl.Neg(inner = d)),
                    )
                }
            }
        }
    }

    // ── The fold ─────────────────────────────────────────────────────────────────────────────────────────────────

    "a constant subtree folds at compile into one register and emits no instruction, a constant tree folds whole" {
        val inner = KatalystKnobDsl.Times(left = c(2.0), right = c(3.0))
        val mixed = KatalystKnobProgram.compile(KatalystKnobDsl.Plus(left = slot("a"), right = inner))

        withClue("the slot's plus is the one instruction left") { mixed.instructionCount shouldBe 1 }
        mixed.isConstant.shouldBeFalse()

        val whole = KatalystKnobProgram.compile(KatalystKnobDsl.Abs(inner = KatalystKnobDsl.Neg(inner = inner)))

        whole.isConstant.shouldBeTrue()
        whole.registers[whole.resultRegister] shouldBe 6.0

        // A folded knob is a constant knob: it never evaluates, at build or at a resolve.
        val knob = KatalystKnob(KatalystKnobDsl.Abs(inner = KatalystKnobDsl.Neg(inner = inner)))
        knob.resolve(klangMapOf("a" to 1.0))

        knob.evaluations shouldBe 0
        knob.value shouldBe 6.0
        knob.written.shouldBeFalse()
    }

    "a bare Param root is a slot register and no instruction, and it is not a constant" {
        // `isConstant` asks for the slot leaves, not the instructions: a caller that took a no-op program for a
        // folded one would freeze the slot at its default.
        val bare = KatalystKnobProgram.compile(KatalystKnobDsl.Param("a", 0.5))

        bare.instructionCount shouldBe 0
        bare.isConstant.shouldBeFalse()
    }

    "the fold gives what the evaluator gives for the same numbers" {
        // The same tree twice, once all constants (folded at compile) and once over slots (evaluated at resolve).
        fun tree(a: KatalystKnobDsl, b: KatalystKnobDsl) = KatalystKnobDsl.Div(
            left = KatalystKnobDsl.Pow(base = a, exp = c(1.5)),
            right = KatalystKnobDsl.Max(left = b, right = c(-0.25)),
        )

        for (a in doubleArrayOf(0.37, -2.6, 1e300)) {
            for (b in doubleArrayOf(0.0, -0.0, -3.0, 4.5)) {
                val folded = knobValue(tree(c(a), c(b)), null)
                val evaluated = knobValue(tree(slot("a"), slot("b")), klangMapOf("a" to a, "b" to b))

                withClue("a=$a b=$b") { shouldBeSameNumber(actual = evaluated, expected = folded) }
            }
        }
    }

    // ── Q5: an unset slot leaf unsets the knob ───────────────────────────────────────────────────────────────────

    "a non-finite slot leaf makes the whole knob unset, where the law would have read it as a set number" {
        // `times` scrubs a NaN to 0: without the rule a cleared slot would read as a SET 0.
        val knob = KatalystKnob(KatalystKnobDsl.Times(left = KatalystKnobDsl.Param("w", 0.5), right = c(2.0)))

        knob.value shouldBe 1.0

        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY).forEach { unset ->
            withClue("w = $unset") {
                knob.resolve(klangMapOf("w" to unset))

                knob.value.isNaN().shouldBeTrue()
                knob.written.shouldBeFalse()
            }
        }
    }

    "an unset leaf unsets the knob whatever the others say, a default that is unset included" {
        // `a` is written, `b` is never written and its default is unset: the knob is unset, and not written.
        val knob = KatalystKnob(
            KatalystKnobDsl.Plus(left = KatalystKnobDsl.Param("a", 1.0), right = KatalystKnobDsl.Param("b", SLOT_UNSET))
        )

        withClue("at build, from the defaults: unset, and nothing was evaluated") {
            knob.value.isNaN().shouldBeTrue()
            knob.evaluations shouldBe 0
        }

        knob.resolve(klangMapOf("a" to 2.0))

        knob.value.isNaN().shouldBeTrue()
        knob.written.shouldBeFalse()

        knob.resolve(klangMapOf("a" to 2.0, "b" to 0.25))

        knob.value shouldBe 2.25
        knob.written.shouldBeTrue()
    }

    "a constant is not a slot: a non-finite constant follows the laws" {
        val scrubbed = KatalystKnob(
            KatalystKnobDsl.Times(left = KatalystKnobDsl.Param("a", 1.0), right = c(Double.NaN))
        )
        scrubbed.resolve(klangMapOf("a" to 2.0))

        withClue("times scrubs the NaN product to 0, a set number the pattern wrote") {
            scrubbed.value.toRawBits() shouldBe 0.0.toRawBits()
            scrubbed.written.shouldBeTrue()
        }

        val kept = KatalystKnob(KatalystKnobDsl.Plus(left = KatalystKnobDsl.Param("a", 1.0), right = c(Double.NaN)))
        kept.resolve(klangMapOf("a" to 2.0))

        withClue("plus keeps the NaN: the value is not finite, so it is no write") {
            kept.value.isNaN().shouldBeTrue()
            kept.written.shouldBeFalse()
        }
    }

    "written is true exactly when a slot leaf took a finite value from the state" {
        val knob = KatalystKnob(
            KatalystKnobDsl.Plus(left = KatalystKnobDsl.Param("a", 1.0), right = KatalystKnobDsl.Param("b", 2.0))
        )

        knob.resolve(null)
        withClue("no state: the defaults, not written") {
            knob.value shouldBe 3.0
            knob.written.shouldBeFalse()
        }

        knob.resolve(klangMapOf("other" to 9.0))
        withClue("a state that names neither leaf") {
            knob.value shouldBe 3.0
            knob.written.shouldBeFalse()
        }

        knob.resolve(klangMapOf("b" to 0.0))
        withClue("one leaf written, a zero included") {
            knob.value shouldBe 1.0
            knob.written.shouldBeTrue()
        }

        knob.resolve(klangMapOf("a" to -4.0, "b" to 0.5))
        withClue("both written") {
            knob.value shouldBe -3.5
            knob.written.shouldBeTrue()
        }
    }

    "an unset expression on a stage knob takes the stage's constant, as an unset plain slot does" {
        val chain = KatalystChainBuilder.build(
            dsl = KatalystDsl.of(
                KatalystStageDsl.Delay(
                    time = c(0.25),
                    feedback = KatalystKnobDsl.Times(left = KatalystKnobDsl.Param("fb", 0.5), right = c(1.5)),
                )
            ),
            sampleRate = 44100,
            blockFrames = 128,
            rings = SizedBuffers.forRings(44100),
            reverbs = ReverbUnits(44100),
        )

        chain.applyParams(null)
        chain.delay.shouldNotBeNull().delayLine.shouldNotBeNull().feedback shouldBe 0.75

        chain.applyParams(klangMapOf("fb" to Double.NaN))
        chain.delay.shouldNotBeNull().delayLine.shouldNotBeNull().feedback shouldBe DELAY_FEEDBACK
    }

    "a WRITTEN expression wet runs the delay, an authored zero rents nothing: stageAskedFor keeps its meaning" {
        fun chain() = KatalystChainBuilder.build(
            dsl = KatalystDsl.of(
                KatalystStageDsl.Delay(
                    wet = KatalystKnobDsl.Times(left = KatalystKnobDsl.Param("w", 0.0), right = c(0.5)),
                    time = c(0.25),
                )
            ),
            sampleRate = 44100,
            blockFrames = 128,
            rings = SizedBuffers.forRings(44100),
            reverbs = ReverbUnits(44100),
        )

        val authored = chain().also { it.applyParams(null) }
        withClue("authored 0 * 0.5, nobody wrote it: no ring") {
            (authored.delay.shouldNotBeNull().delayLine == null).shouldBeTrue()
        }

        val written = chain().also { it.applyParams(klangMapOf("w" to 0.0)) }
        withClue("a pattern wrote 0: the line runs, fed nothing") {
            written.delay.shouldNotBeNull().delayLine.shouldNotBeNull()
        }
    }

    // ── The cost rule: an expression over slots evaluates at resolve, never per block ─────────────────────────────

    "an expression knob evaluates once per resolve, and never while the stage runs its blocks" {
        val knob = KatalystKnob(KatalystKnobDsl.Times(left = KatalystKnobDsl.Param("fader", 1.0), right = c(0.5)))
        val fx = KatalystGainEffect(sampleRate = 44100, blockFrames = 128)
        val writer = KatalystGainWriter(fx = fx, gain = knob)
        val ctx = KatalystContext(blockFrames = 128, mixBuffer = StereoBuffer(128))

        withClue("once at build, from the defaults") { knob.evaluations shouldBe 1 }

        writer.resolve(klangMapOf("fader" to 0.8))

        repeat(50) {
            writer.apply()
            fx.process(ctx)
        }

        withClue("one resolve, fifty blocks") { knob.evaluations shouldBe 2 }

        writer.resolve(klangMapOf("fader" to 0.6))

        knob.evaluations shouldBe 3
        knob.value shouldBe 0.3
    }
})
