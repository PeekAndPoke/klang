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
import io.peekandpoke.klang.audio_be.utils.fastExp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.sqrt
import kotlin.math.tanh
import kotlin.random.Random

/**
 * Each arithmetic law against an oracle formula written HERE, on every arm of the constant-fold ladder and on the
 * scalar path (engine tidy-up step 11, audit B4.8 and B2.15).
 *
 * `ConstantFoldParitySpec` and `ControlRateScalarParitySpec` compare the node's paths with each other, so a law
 * changed the same way in every arm passes them. This spec does not ask the node what its law is: the oracle below
 * is the contract (`audio/ref/numerical-safety.md`), the clamp constants are literals, and the edges are literal
 * values. Samples are compared by their bits (`-0.0` is not `0.0`); a NaN is compared as "is NaN", since a NaN's
 * sign and payload are not portable between the JVM and JS.
 */
class ArithmeticLawSpec : StringSpec({

    val block = 64
    val offset = 3

    /** The operands: NaN, both zeros, both infinities, the clamp edges, a denormal, negative bases and round ties. */
    val values = doubleArrayOf(
        Double.NaN, 0.0, -0.0, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1e308, -1e308, 1e15, -1e15,
        4.9e-324, -2.2e-310, 1e-16, -1e-16, -2.0, -0.5, 0.5, 1.5, -1.5, 2.5, 3.0, -8.0, 1.0 / 3.0, 40.0, -40.0,
    )

    // ── The oracle, written out ───────────────────────────────────────────────────

    /** The output clamp: NaN reads 0, magnitude at most 1e15. */
    fun clampOut(v: Double): Double = when {
        v.isNaN() -> 0.0
        v > 1e15 -> 1e15
        v < -1e15 -> -1e15
        else -> v
    }

    /** The divisor guard: NaN and both zeros read +1e-15, a magnitude below 1e-15 keeps its sign at 1e-15. */
    fun guardDivisor(d: Double): Double = when {
        d.isNaN() -> 1e-15
        d > 1e-15 || d < -1e-15 -> d
        d < 0.0 -> -1e-15
        else -> 1e-15
    }

    val binaryOracle: Map<String, (Double, Double) -> Double> = mapOf(
        "plus" to { x, y -> x + y },
        "minus" to { x, y -> x - y },
        "times" to { x, y -> clampOut(x * y) },
        "div" to { x, y -> if (y == 0.0) 0.0 else clampOut(x / guardDivisor(y)) },
        "pow" to { x, y -> clampOut(if (x >= 0.0) x.pow(y) else -((-x).pow(y))) },
        "min" to { x, y -> if (x < y) x else y },
        "max" to { x, y -> if (x > y) x else y },
        "mod" to { x, y -> x % guardDivisor(y) },
    )

    val binaryDoors: Map<String, (Ignitor, Ignitor) -> Ignitor> = mapOf(
        "plus" to { a, b -> a + b },
        "minus" to { a, b -> a.minus(b) },
        "times" to { a, b -> a * b },
        "div" to { a, b -> a.div(b) },
        "pow" to { a, b -> a.pow(b) },
        "min" to { a, b -> a.min(b) },
        "max" to { a, b -> a.max(b) },
        "mod" to { a, b -> a.mod(b) },
    )

    val unaryOracle: Map<String, (Double) -> Double> = mapOf(
        "abs" to { v -> if (v < 0.0) -v else v },
        "exp" to { v -> clampOut(fastExp(v)) },
        "log" to { v -> if (v > 0.0) ln(v) else if (v < 0.0) -ln(-v) else 0.0 },
        "sqrt" to { v -> if (v >= 0.0) sqrt(v) else -sqrt(-v) },
        "sign" to { v -> if (v > 0.0) 1.0 else if (v < 0.0) -1.0 else 0.0 },
        "tanh" to { v -> tanh(v) },
        "floor" to { v -> floor(v) },
        "ceil" to { v -> ceil(v) },
        "round" to { v -> round(v) },
        "frac" to { v -> v - floor(v) },
        "recip" to { v -> clampOut(1.0 / guardDivisor(v)) },
        "sq" to { v -> clampOut(v * v) },
    )

    val unaryDoors: Map<String, (Ignitor) -> Ignitor> = mapOf(
        "abs" to { a -> a.abs() },
        "exp" to { a -> a.exp() },
        "log" to { a -> a.log() },
        "sqrt" to { a -> a.sqrt() },
        "sign" to { a -> a.sign() },
        "tanh" to { a -> a.tanh() },
        "floor" to { a -> a.floor() },
        "ceil" to { a -> a.ceil() },
        "round" to { a -> a.round() },
        "frac" to { a -> a.frac() },
        "recip" to { a -> a.recip() },
        "sq" to { a -> a.sq() },
    )

    // ── Rendering ─────────────────────────────────────────────────────────────────

    fun ctx(length: Int): IgniteContext = IgniteContext(
        sampleRate = 48000,
        voiceDurationFrames = block * 4,
        gateEndFrame = block * 4,
        scratchBuffers = ScratchBuffers(block),
        random = Random(1),
    ).apply {
        updateOffsetAndLength(offset = offset, length = length)
    }

    /** One window of [node], `length` samples from [offset]. */
    fun render(node: Ignitor, length: Int): DoubleArray {
        val buffer = AudioBuffer(block)

        node.generate(buffer, 220.0, ctx(length))

        return DoubleArray(length) { i -> buffer[offset + i] }
    }

    fun sameBits(actual: Double, expected: Double, clue: String) {
        withClue(clue) {
            if (expected.isNaN()) {
                actual.isNaN() shouldBe true
            } else {
                actual.toRawBits() shouldBe expected.toRawBits()
            }
        }
    }

    // ── The rows ──────────────────────────────────────────────────────────────────

    "every binary law on every arm of the ladder and on the scalar path matches the oracle" {
        for ((name, door) in binaryDoors) {
            val law = binaryOracle.getValue(name)

            for (y in values) {
                // the right operand constant: the left renders every value
                val rightDead = (name == "times" && y == 0.0) || (name == "div" && (y == 0.0 || y.isInfinite()))
                val right = render(door(Source(values), ConstantIgnitor(y)), values.size)

                for (i in values.indices) {
                    val expected = if (rightDead) 0.0 else law(values[i], y)

                    sameBits(actual = right[i], expected = expected, clue = "$name right-constant: ${values[i]} and $y")
                }

                // the left operand constant: the right renders every value
                val x = y
                val leftDead = name == "times" && x == 0.0
                val left = render(door(ConstantIgnitor(x), Source(values)), values.size)

                for (i in values.indices) {
                    val expected = if (leftDead) 0.0 else law(x, values[i])

                    sameBits(actual = left[i], expected = expected, clue = "$name left-constant: $x and ${values[i]}")
                }

                for (x2 in values) {
                    val both = door(ConstantIgnitor(x2), ConstantIgnitor(y))

                    // both constant: the law, never a dead branch
                    sameBits(actual = render(both, 5)[2], expected = law(x2, y), clue = "$name both-constant: $x2 and $y")

                    // the scalar path
                    val scalar = both.controlRateValueOrNull(220.0)

                    withClue("$name scalar: $x2 and $y answers") { scalar shouldNotBe null }
                    sameBits(actual = scalar ?: 0.0, expected = law(x2, y), clue = "$name scalar: $x2 and $y")
                }
            }

            // neither constant: every pair meets once over the rotations
            for (shift in values.indices) {
                val rotated = DoubleArray(values.size) { i -> values[(i + shift) % values.size] }
                val scratch = render(door(Source(values), Source(rotated)), values.size)

                for (i in values.indices) {
                    sameBits(
                        actual = scratch[i],
                        expected = law(values[i], rotated[i]),
                        clue = "$name scratch: ${values[i]} and ${rotated[i]}",
                    )
                }
            }
        }
    }

    "every unary law on the audio path and on the scalar path matches the oracle" {
        for ((name, door) in unaryDoors) {
            val law = unaryOracle.getValue(name)
            val out = render(door(Source(values)), values.size)

            for (i in values.indices) {
                sameBits(actual = out[i], expected = law(values[i]), clue = "$name audio: ${values[i]}")

                val scalar = door(ConstantIgnitor(values[i])).controlRateValueOrNull(220.0)

                withClue("$name scalar: ${values[i]} answers") { scalar shouldNotBe null }
                sameBits(actual = scalar ?: 0.0, expected = law(values[i]), clue = "$name scalar: ${values[i]}")
            }
        }
    }

    "the edges, as literal values" {
        fun unary(door: (Ignitor) -> Ignitor, v: Double): Double = render(door(Source(doubleArrayOf(v))), 1)[0]

        fun binary(door: (Ignitor, Ignitor) -> Ignitor, x: Double, y: Double): Double =
            render(door(Source(doubleArrayOf(x)), Source(doubleArrayOf(y))), 1)[0]

        // Abs keeps -0.0; Log and Sign map NaN and both zeros to +0.0; Sqrt keeps NaN and is signed
        sameBits(actual = unary({ it.abs() }, -0.0), expected = -0.0, clue = "abs(-0.0)")
        sameBits(actual = unary({ it.log() }, Double.NaN), expected = 0.0, clue = "log(NaN)")
        sameBits(actual = unary({ it.log() }, -0.0), expected = 0.0, clue = "log(-0.0)")
        sameBits(actual = unary({ it.sign() }, Double.NaN), expected = 0.0, clue = "sign(NaN)")
        sameBits(actual = unary({ it.sign() }, -0.0), expected = 0.0, clue = "sign(-0.0)")
        sameBits(actual = unary({ it.sqrt() }, Double.NaN), expected = Double.NaN, clue = "sqrt(NaN)")
        sameBits(actual = unary({ it.sqrt() }, -4.0), expected = -2.0, clue = "sqrt(-4)")

        // Round ties to even
        sameBits(actual = unary({ it.round() }, 0.5), expected = 0.0, clue = "round(0.5)")
        sameBits(actual = unary({ it.round() }, 1.5), expected = 2.0, clue = "round(1.5)")
        sameBits(actual = unary({ it.round() }, 2.5), expected = 2.0, clue = "round(2.5)")
        sameBits(actual = unary({ it.round() }, -0.5), expected = -0.0, clue = "round(-0.5)")

        // the clamps: Recip substitutes at zero, Sq and Exp clamp, Plus and Minus are bare, Times clamps
        sameBits(actual = unary({ it.recip() }, 0.0), expected = 9.999999999999999e14, clue = "recip(0)")
        sameBits(actual = unary({ it.sq() }, 1e8), expected = 1e15, clue = "sq(1e8)")
        sameBits(actual = unary({ it.exp() }, 40.0), expected = 1e15, clue = "exp(40)")
        sameBits(actual = binary(door = { a, b -> a + b }, x = 1e15, y = 1e15), expected = 2e15, clue = "plus is bare")
        sameBits(
            actual = binary(door = { a, b -> a.minus(b) }, x = -1e308, y = 1e308),
            expected = Double.NEGATIVE_INFINITY,
            clue = "minus is bare",
        )
        sameBits(actual = binary(door = { a, b -> a * b }, x = 1e10, y = 1e10), expected = 1e15, clue = "times clamps")

        // Div: a zero divisor is zero, a tiny one is guarded; Mod guards its divisor but has no output clamp, so a NaN
        // passes (a clamp would read it as 0)
        sameBits(actual = binary(door = { a, b -> a.div(b) }, x = 5.0, y = 0.0), expected = 0.0, clue = "div by 0")
        // 1 / 1e-15 is 9.999999999999999e14, just below the output clamp
        sameBits(actual = binary(door = { a, b -> a.div(b) }, x = 1.0, y = 1e-20), expected = 9.999999999999999e14, clue = "div by 1e-20")
        sameBits(actual = binary(door = { a, b -> a.mod(b) }, x = 5.0, y = 0.0), expected = 5.0 % 1e-15, clue = "mod by 0")
        sameBits(actual = binary(door = { a, b -> a.mod(b) }, x = Double.NaN, y = 7.0), expected = Double.NaN, clue = "mod(NaN, 7)")
        sameBits(
            actual = binary(door = { a, b -> a.mod(b) }, x = Double.POSITIVE_INFINITY, y = 7.0),
            expected = Double.NaN,
            clue = "mod(Inf, 7)",
        )

        // Pow is signed magnitude, and 0 to a negative power clamps
        sameBits(actual = binary(door = { a, b -> a.pow(b) }, x = -8.0, y = 2.0), expected = -64.0, clue = "pow(-8, 2)")
        sameBits(actual = binary(door = { a, b -> a.pow(b) }, x = 0.0, y = -1.0), expected = 1e15, clue = "pow(0, -1)")

        // Min and Max keep the first operand's role under NaN: a NaN on either side gives the SECOND operand
        sameBits(actual = binary(door = { a, b -> a.min(b) }, x = Double.NaN, y = 1.0), expected = 1.0, clue = "min(NaN, 1)")
        sameBits(actual = binary(door = { a, b -> a.min(b) }, x = 1.0, y = Double.NaN), expected = Double.NaN, clue = "min(1, NaN)")
        sameBits(actual = binary(door = { a, b -> a.max(b) }, x = Double.NaN, y = 1.0), expected = 1.0, clue = "max(NaN, 1)")
        sameBits(actual = binary(door = { a, b -> a.max(b) }, x = 1.0, y = Double.NaN), expected = Double.NaN, clue = "max(1, NaN)")
    }

    "a dead branch fills +0.0 by its bits and the other side renders nothing" {
        // -3 would give -0.0 through the law (-3 * 0, -3 / Inf), so +0.0 is the dead branch's own fill
        val negative = doubleArrayOf(-3.0, -3.0, -3.0, -3.0)
        val cases: List<Pair<String, (Source) -> Ignitor>> = listOf(
            "times by 0 on the right" to { s -> s * ConstantIgnitor(0.0) },
            "times by -0.0 on the right" to { s -> s * ConstantIgnitor(-0.0) },
            "times by 0 on the left" to { s -> ConstantIgnitor(0.0) * s },
            "div by 0" to { s -> s.div(ConstantIgnitor(0.0)) },
            "div by +Inf" to { s -> s.div(ConstantIgnitor(Double.POSITIVE_INFINITY)) },
            "div by -Inf" to { s -> s.div(ConstantIgnitor(Double.NEGATIVE_INFINITY)) },
        )

        for ((name, build) in cases) {
            val source = Source(negative)
            val out = render(build(source), negative.size)

            withClue(name) {
                out.map { it.toRawBits() } shouldBe List(negative.size) { 0.0.toRawBits() }
                source.rendered shouldBe 0
            }
        }

        // the law itself, where no branch is dead: both constant, and a finite divisor
        withClue("both constant runs the law: -3 * 0 is -0.0") {
            render(ConstantIgnitor(-3.0) * ConstantIgnitor(0.0), 1)[0].toRawBits() shouldBe (-0.0).toRawBits()
        }

        withClue("a large finite divisor is not dead: -3 / 1e308 is -0.0 through the law") {
            val source = Source(negative)

            render(source.div(ConstantIgnitor(1e308)), negative.size)[0].toRawBits() shouldBe (-3.0 / 1e308).toRawBits()
            source.rendered shouldBe negative.size
        }
    }

    "Div's constant 0 on the LEFT is not a dead branch: the divisor renders and the law runs" {
        val divisors = doubleArrayOf(-2.0, 4.0, 0.0, Double.NaN)
        val source = Source(divisors)
        val out = render(ConstantIgnitor(0.0).div(source), divisors.size)

        source.rendered shouldBe divisors.size
        // 0 / -2 is -0.0 through the law; a dead branch would have filled +0.0
        out[0].toRawBits() shouldBe (-0.0).toRawBits()
        out[1].toRawBits() shouldBe 0.0.toRawBits()
        out[2].toRawBits() shouldBe 0.0.toRawBits()
        out[3].toRawBits() shouldBe 0.0.toRawBits()

        // and Times, for contrast, IS dead on the left
        val other = Source(divisors)

        render(ConstantIgnitor(0.0) * other, divisors.size)[0].toRawBits() shouldBe 0.0.toRawBits()
        other.rendered shouldBe 0
    }
})

/** TEST ONLY. Plays [data] from the start of each window, and counts the samples it rendered. */
private class Source(private val data: DoubleArray) : Ignitor {
    var rendered = 0

    override fun generate(buffer: AudioBuffer, freqHz: Double, ctx: IgniteContext) {
        for (i in ctx.offset until ctx.windowEnd) {
            buffer[i] = data[(i - ctx.offset) % data.size]
            rendered++
        }
    }
}
