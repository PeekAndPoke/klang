package io.peekandpoke.klang.strudel.math

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

class RationalSpec : StringSpec({

    "construction from integers" {
        Rational(5).toDouble() shouldBe 5.0
        Rational(5L).toDouble() shouldBe 5.0
        Rational(0) shouldBe Rational.ZERO
        Rational(-3).toDouble() shouldBe -3.0
    }

    "construction from double" {
        Rational(0.5).toDouble() shouldBeExactly 0.5
        Rational(0.25).toDouble() shouldBeExactly 0.25
        Rational(0.75).toDouble() shouldBeExactly 0.75
        Rational(2.5).toDouble() shouldBeExactly 2.5
        Rational(-1.5).toDouble() shouldBeExactly -1.5

        // Test precision (16-bit fraction is ~0.000015 resolution)
        val pi = Rational(3.14159265359)
        pi.toDouble() shouldBe (3.14159265359 plusOrMinus 0.0001)
    }

    "addition" {
        (Rational(0.5) + Rational(0.25)) shouldBe Rational(0.75)
        (Rational(1.0) + Rational(2.0)) shouldBe Rational(3.0)
        (Rational(0.5) + Rational(-0.5)) shouldBe Rational.ZERO
    }

    "subtraction" {
        (Rational(0.5) - Rational(0.25)) shouldBe Rational(0.25)
        (Rational(1.0) - Rational(1.0)) shouldBe Rational.ZERO
        (Rational(0.0) - Rational(0.5)) shouldBe Rational(-0.5)
    }

    "multiplication" {
        (Rational(0.5) * Rational(0.5)) shouldBe Rational(0.25)
        (Rational(2.0) * Rational(3.0)) shouldBe Rational(6.0)
        (Rational(-1.0) * Rational(0.5)) shouldBe Rational(-0.5)
    }

    "division" {
        (Rational(1.0) / Rational(2.0)) shouldBe Rational(0.5)
        (Rational(0.5) / Rational(0.25)) shouldBe Rational(2.0)
        (Rational(1.0) / Rational(3.0)).toDouble() shouldBe (0.33333 plusOrMinus 0.0001)
    }

    "modulo" {
        (Rational(3.5) % Rational(2.0)) shouldBe Rational(1.5)
        (Rational(5.0) % Rational(3.0)) shouldBe Rational(2.0)
    }

    "unary minus" {
        -Rational(0.5) shouldBe Rational(-0.5)
        -Rational(-0.75) shouldBe Rational(0.75)
        -Rational.ZERO shouldBe Rational.ZERO
    }

    "comparison operators" {
        (Rational(0.5) < Rational(0.75)) shouldBe true
        (Rational(0.75) > Rational(0.5)) shouldBe true
        (Rational(0.5) <= Rational(0.5)) shouldBe true
        (Rational(-0.5) < Rational(0.5)) shouldBe true
    }

    "equals and hashCode" {
        // In value classes, equality is based on the underlying 'bits'
        Rational(0.5) shouldBe Rational(0.5)
        Rational(0.5).hashCode() shouldBe Rational(0.5).hashCode()

        Rational(0.5) shouldNotBe Rational(0.333)
    }

    "absolute value" {
        Rational(0.5).abs() shouldBe Rational(0.5)
        Rational(-0.5).abs() shouldBe Rational(0.5)
        Rational.ZERO.abs() shouldBe Rational.ZERO
    }

    "floor operation" {
        Rational(3.5).floor() shouldBe Rational(3.0)
        Rational(1.9).floor() shouldBe Rational(1.0)
        Rational(-3.5).floor() shouldBe Rational(-4.0)
        Rational(0.5).floor() shouldBe Rational(0.0)
    }

    "ceil operation" {
        Rational(3.5).ceil() shouldBe Rational(4.0)
        Rational(1.1).ceil() shouldBe Rational(2.0)
        Rational(2.0).ceil() shouldBe Rational(2.0)
        Rational(-3.5).ceil() shouldBe Rational(-3.0)
        Rational(0.5).ceil() shouldBe Rational(1.0)
    }

    "fractional part" {
        Rational(3.5).frac() shouldBe Rational(0.5)
        Rational(1.25).frac() shouldBe Rational(0.25)
        Rational(2.0).frac() shouldBe Rational.ZERO
    }

    "conversion to primitives" {
        Rational(0.5).toDouble() shouldBeExactly 0.5
        Rational(3.5).toLong() shouldBe 3L
        Rational(1.9).toInt() shouldBe 1
    }

    "euclidean pattern timing edge case" {
        // 1/8 step duration
        val stepDuration = Rational(1.0) / Rational(8.0)

        // Summing 8 steps should equal exactly 1.0 within fixed-point resolution
        var total = Rational.ZERO
        repeat(8) { total += stepDuration }

        total.toDouble() shouldBe (1.0 plusOrMinus 0.0001)
    }

    "weight-based timing calculations" {
        val weights = listOf(1.0, 2.0, 1.0)
        val totalWeight = weights.sum()

        val offsets = mutableListOf(Rational.ZERO)
        weights.forEach { w ->
            val rational = Rational(w)
            val proportion = rational / Rational(totalWeight)
            offsets.add(offsets.last() + proportion)
        }

        offsets[0].toDouble() shouldBe 0.0
        offsets[1].toDouble() shouldBe 0.25
        offsets[2].toDouble() shouldBe 0.75
        offsets[3].toDouble() shouldBe (1.0 plusOrMinus 0.0001)
    }

    "Number.toRational() extension function" {
        5.toRational() shouldBe Rational(5.0)
        0.5.toRational() shouldBe Rational(0.5)
        100L.toRational() shouldBe Rational(100.0)
    }

    "rem(Number) operator convenience method" {
        (Rational(3.5) % 2) shouldBe Rational(1.5)
        (Rational(1.33) % 1.0).toDouble() shouldBe (0.33 plusOrMinus 0.001)
    }

    "large number arithmetic (32.32 safety checks)" {
        // Test large integers (millions of cycles)
        val million = Rational(1_000_000)
        val twoMillion = Rational(2_000_000)

        // Addition
        (million + million) shouldBe twoMillion

        // Subtraction
        (twoMillion - million) shouldBe million

        // Multiplication with large numbers
        // 1,000,000 * 2.5 = 2,500,000
        (million * Rational(2.5)).toDouble() shouldBe (2_500_000.0 plusOrMinus EPSILON)

        // Division with large numbers
        // 2,500_000 / 1,000,000 = 2.5
        (Rational(2_500_000.0) / million).toDouble() shouldBe (2.5 plusOrMinus EPSILON)

        // Ensure we can handle values up to the reasonable limit of a 32-bit integer part
        // 1 billion cycles
        val billion = Rational(1_000_000_000L)
        (billion + Rational.ONE).toLong() shouldBe 1_000_000_001L

        // Test that very small numbers multiplied by large ones stay accurate
        val small = Rational(1.0) / Rational(1_000_000)
        (billion * small).toDouble() shouldBe (1000.0 plusOrMinus 0.3)
    }

    "overflow safety: chained multiplication" {
        var res = Rational(10.0)
        // Repeatedly multiply and divide to ensure we don't accumulate junk or overflow Long
        repeat(10) {
            res *= Rational(1.5)
            res /= Rational(1.2)
        }
        res.toDouble() shouldBe (res.toDouble() plusOrMinus EPSILON)
        res.isNaN shouldBe false
    }
})
