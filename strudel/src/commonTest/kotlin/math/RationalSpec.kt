package io.peekandpoke.klang.strudel.math

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class RationalSpec : StringSpec({

    "construction and simplification" {
        Rational(4, 8).simplified() shouldBe Rational(1, 2)
        Rational(6, 9).simplified() shouldBe Rational(2, 3)
        Rational(0, 5) shouldBe Rational.ZERO
        Rational(-4, 8).simplified() shouldBe Rational(-1, 2)
        Rational(4, -8).simplified() shouldBe Rational(-1, 2)
        Rational(-4, -8).simplified() shouldBe Rational(1, 2)
    }

    "construction from integers" {
        Rational(5) shouldBe Rational(5, 1)
        Rational(5L) shouldBe Rational(5, 1)
        Rational(0) shouldBe Rational.ZERO
        Rational(-3) shouldBe Rational(-3, 1)
    }

    "construction from double" {
        Rational(0.5) shouldBe Rational(1, 2)
        Rational(0.25) shouldBe Rational(1, 4)
        Rational(0.75) shouldBe Rational(3, 4)
        Rational(2.5) shouldBe Rational(5, 2)
        Rational(-1.5) shouldBe Rational(-3, 2)

        // Test approximation
        val pi = Rational(3.14159265359)
        (pi.toDouble() - 3.14159265359).let { diff ->
            diff < 0.0001 && diff > -0.0001
        } shouldBe true
    }

    "addition" {
        (Rational(1, 2) + Rational(1, 3)) shouldBe Rational(5, 6)
        (Rational(1, 4) + Rational(1, 4)) shouldBe Rational(1, 2)
        (Rational(2, 3) + Rational(1, 6)) shouldBe Rational(5, 6)
        (Rational(1, 2) + Rational(-1, 2)) shouldBe Rational.ZERO
        (Rational(3) + Rational(2)) shouldBe Rational(5)
    }

    "subtraction" {
        (Rational(1, 2) - Rational(1, 3)) shouldBe Rational(1, 6)
        (Rational(3, 4) - Rational(1, 4)) shouldBe Rational(1, 2)
        (Rational(1, 2) - Rational(1, 2)) shouldBe Rational.ZERO
        (Rational(5) - Rational(3)) shouldBe Rational(2)
        (Rational(1, 3) - Rational(1, 2)) shouldBe Rational(-1, 6)
    }

    "multiplication" {
        (Rational(1, 2) * Rational(1, 3)) shouldBe Rational(1, 6)
        (Rational(2, 3) * Rational(3, 4)) shouldBe Rational(1, 2)
        (Rational(5) * Rational(3)) shouldBe Rational(15)
        (Rational(2, 5) * Rational(5, 2)) shouldBe Rational.ONE
        (Rational(-1, 2) * Rational(2, 3)) shouldBe Rational(-1, 3)
    }

    "division" {
        (Rational(1, 2) / Rational(1, 3)) shouldBe Rational(3, 2)
        (Rational(2, 3) / Rational(4, 5)) shouldBe Rational(10, 12).simplified()
        (Rational(6) / Rational(3)) shouldBe Rational(2)
        (Rational(1, 4) / Rational(1, 2)) shouldBe Rational(1, 2)
        (Rational(-1, 2) / Rational(2, 3)) shouldBe Rational(-3, 4)
    }

    "modulo" {
        (Rational(7, 2) % Rational(2)) shouldBe Rational(3, 2)
        (Rational(5) % Rational(3)) shouldBe Rational(2)
        (Rational(1, 2) % Rational(1, 3)) shouldBe Rational(1, 6)
        (Rational(10, 3) % Rational(1)) shouldBe Rational(1, 3)
    }

    "unary minus" {
        -Rational(1, 2) shouldBe Rational(-1, 2)
        -Rational(-3, 4) shouldBe Rational(3, 4)
        -Rational.ZERO shouldBe Rational.ZERO
    }

    "comparison operators" {
        (Rational(1, 2) < Rational(2, 3)) shouldBe true
        (Rational(3, 4) > Rational(1, 2)) shouldBe true
        (Rational(1, 2) <= Rational(1, 2)) shouldBe true
        (Rational(2, 3) >= Rational(1, 2)) shouldBe true
        (Rational(1, 2) == Rational(2, 4)) shouldBe true
        (Rational(1, 2) != Rational(1, 3)) shouldBe true

        (Rational(5) > Rational(3)) shouldBe true
        (Rational(-1, 2) < Rational(1, 2)) shouldBe true
        (Rational(0) < Rational(1, 100)) shouldBe true
    }

    "equals and hashCode" {
        Rational(1, 2) shouldBe Rational(2, 4)
        Rational(1, 2) shouldBe Rational(3, 6)
        Rational(1, 2).hashCode() shouldBe Rational(2, 4).hashCode()

        Rational(1, 2) shouldNotBe Rational(1, 3)
        Rational(2, 3) shouldNotBe Rational(3, 4)
    }

    "absolute value" {
        Rational(1, 2).abs() shouldBe Rational(1, 2)
        Rational(-1, 2).abs() shouldBe Rational(1, 2)
        Rational(-5, 3).abs() shouldBe Rational(5, 3)
        Rational.ZERO.abs() shouldBe Rational.ZERO
    }

    "floor operation" {
        Rational(7, 2).floor() shouldBe Rational(3)
        Rational(5, 3).floor() shouldBe Rational(1)
        Rational(8, 4).floor() shouldBe Rational(2)
        Rational(-7, 2).floor() shouldBe Rational(-4)
        Rational(-5, 3).floor() shouldBe Rational(-2)
        Rational(1, 2).floor() shouldBe Rational(0)
    }

    "ceil operation" {
        Rational(7, 2).ceil() shouldBe Rational(4)
        Rational(5, 3).ceil() shouldBe Rational(2)
        Rational(8, 4).ceil() shouldBe Rational(2)
        Rational(-7, 2).ceil() shouldBe Rational(-3)
        Rational(-5, 3).ceil() shouldBe Rational(-1)
        Rational(1, 2).ceil() shouldBe Rational(1)
    }

    "fractional part" {
        Rational(7, 2).frac() shouldBe Rational(1, 2)
        Rational(5, 3).frac() shouldBe Rational(2, 3)
        Rational(8, 4).frac() shouldBe Rational.ZERO
        Rational(1, 2).frac() shouldBe Rational(1, 2)
    }

    "conversion to primitives" {
        Rational(1, 2).toDouble() shouldBeExactly 0.5
        Rational(3, 4).toDouble() shouldBeExactly 0.75
        Rational(5, 2).toDouble() shouldBeExactly 2.5
        Rational(7, 2).toLong() shouldBe 3L
        Rational(5, 3).toInt() shouldBe 1
        Rational(-7, 2).toLong() shouldBe -3L
    }

    "toString representation" {
        Rational(1, 2).toString() shouldBe "1/2"
        Rational(3, 4).toString() shouldBe "3/4"
        Rational(5).toString() shouldBe "5"
        Rational(0).toString() shouldBe "0"
        Rational(-1, 2).toString() shouldBe "-1/2"
    }

    "euclidean pattern timing edge case" {
        // This is the real-world case that was causing the bug
        // euclid(3, 8) means dividing 1 cycle into 8 steps
        val stepDuration = Rational(1) / Rational(8)
        stepDuration shouldBe Rational(1, 8)

        // Step indices 0-7, cycle 0
        val step0Start = Rational(0) + (Rational(0) * stepDuration)
        val step1Start = Rational(0) + (Rational(1) * stepDuration)
        val step7Start = Rational(0) + (Rational(7) * stepDuration)

        step0Start shouldBe Rational(0)
        step1Start shouldBe Rational(1, 8)
        step7Start shouldBe Rational(7, 8)

        // These should be exact with no floating point drift
        (step7Start + stepDuration) shouldBe Rational(1) // Exactly 1.0
    }

    "chained arithmetic preserves precision" {
        // Complex calculation that would accumulate errors with doubles
        var result = Rational(1, 3)
        repeat(100) {
            result += Rational(1, 7)
            result *= Rational(2)
            result /= Rational(3)
        }

        // Result should still be a valid rational with no drift
        result.den shouldNotBe 0L
        result.toDouble().isFinite() shouldBe true
    }

    "weight-based timing calculations" {
        // Simulates SequencePattern weight calculations
        val weights = listOf(1.0, 2.0, 1.0)
        val totalWeight = weights.sum()

        val offsets = mutableListOf(Rational.ZERO)
        weights.forEach { w ->
            val rational = Rational(w)
            val proportion = rational / Rational(totalWeight)
            offsets.add(offsets.last() + proportion)
        }

        offsets[0] shouldBe Rational(0)
        offsets[1] shouldBe Rational(1, 4)
        offsets[2] shouldBe Rational(3, 4)
        offsets[3] shouldBe Rational(1) // Should be exactly 1.0
    }
})
