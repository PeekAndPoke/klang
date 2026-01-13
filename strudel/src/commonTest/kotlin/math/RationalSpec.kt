package io.peekandpoke.klang.strudel.math

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

class RationalSpec : StringSpec({

    // Helper to construct exact fractions for testing since the constructor is private
    fun r(n: Int, d: Int = 1): Rational = Rational(n) / Rational(d)

    "Construction and Simplification" {
        withClue("Should simplify fractions automatically") {
            r(2, 4) shouldBe r(1, 2)
            r(100, 20) shouldBe r(5)
            r(-2, 4) shouldBe r(-1, 2)
            r(2, -4) shouldBe r(-1, 2) // Sign normalization
            r(-2, -4) shouldBe r(1, 2)
        }

        withClue("Zero handling") {
            r(0, 5) shouldBe Rational.ZERO
            r(0, -100) shouldBe Rational.ZERO
        }

        withClue("Integers") {
            Rational(5).toDouble() shouldBe 5.0
            Rational(5L).toDouble() shouldBe 5.0
            Rational(0) shouldBe Rational.ZERO
            Rational(-3).toDouble() shouldBe -3.0
        }
    }

    "Double Conversion (Continued Fractions)" {
        withClue("Exact decimals") {
            Rational(0.5) shouldBe r(1, 2)
            Rational(0.25) shouldBe r(1, 4)
            Rational(0.75) shouldBe r(3, 4)
            Rational(0.125) shouldBe r(1, 8)
            Rational(-1.5) shouldBe r(-3, 2)
        }

        withClue("Recurring decimals (Smart approximation)") {
            // 1/3 is approx 0.3333333333333333
            Rational(1.0 / 3.0) shouldBe r(1, 3)
            Rational(2.0 / 3.0) shouldBe r(2, 3)
            Rational(1.0 / 6.0) shouldBe r(1, 6)
            Rational(1.0 / 7.0) shouldBe r(1, 7)
            Rational(1.0 / 9.0) shouldBe r(1, 9)
        }

        withClue("Precision limits") {
            val pi = Rational(3.14159265359)
            pi.toDouble() shouldBe (3.14159265359 plusOrMinus 1e-10)
        }

        withClue("Small scientific notation") {
            Rational(1e-5) shouldBe r(1, 100_000)
            Rational(1e-9).toDouble() shouldBe (1e-9 plusOrMinus 1e-15)
        }
    }

    "Arithmetic Operations" {
        withClue("addition") {
            (r(1, 2) + r(1, 4)) shouldBe r(3, 4)
            (r(1, 3) + r(1, 3)) shouldBe r(2, 3)
            (r(1, 2) + r(-1, 2)) shouldBe Rational.ZERO
        }

        withClue("subtraction") {
            (r(1, 2) - r(1, 4)) shouldBe r(1, 4)
            (r(1) - r(1)) shouldBe Rational.ZERO
            (r(0) - r(1, 2)) shouldBe r(-1, 2)
        }

        withClue("multiplication") {
            (r(1, 2) * r(1, 2)) shouldBe r(1, 4)
            (r(2) * r(3)) shouldBe r(6)
            (r(-1) * r(1, 2)) shouldBe r(-1, 2)
            // Cross cancellation check
            (r(2, 3) * r(3, 4)) shouldBe r(1, 2) // 2/3 * 3/4 = 6/12 = 1/2
        }

        withClue("division") {
            (r(1) / r(2)) shouldBe r(1, 2)
            (r(1, 2) / r(1, 4)) shouldBe r(2) // 1/2 * 4/1 = 2
            (r(1) / r(3)) shouldBe r(1, 3)
        }

        withClue("modulo") {
            // 3.5 % 2.0 = 1.5
            (r(7, 2) % r(2)) shouldBe r(3, 2)
            // 5 % 3 = 2
            (r(5) % r(3)) shouldBe r(2)
        }

        withClue("unary minus") {
            -r(1, 2) shouldBe r(-1, 2)
            -r(-3, 4) shouldBe r(3, 4)
            -Rational.ZERO shouldBe Rational.ZERO
        }
    }

    "Comparisons" {
        withClue("equality") {
            r(1, 2) shouldBe r(2, 4)
            r(1, 3) shouldNotBe 0.3333.toRational() // 0.3333 is 3333/10000, not 1/3
        }

        withClue("ordering") {
            (r(1, 2) < r(3, 4)) shouldBe true
            (r(3, 4) > r(1, 2)) shouldBe true
            (r(1, 2) <= r(1, 2)) shouldBe true
            (r(-1, 2) < r(1, 2)) shouldBe true
        }

        withClue("sorting") {
            val list = listOf(r(1), r(0), r(-1), r(1, 2), Rational.NaN)
            val sorted = list.sorted()

            // Expected: -1, 0, 1/2, 1, NaN (NaN is usually largest in Kotlin CompareTo for doubles, implementation pushes it to end)
            sorted[0] shouldBe r(-1)
            sorted[1] shouldBe r(0)
            sorted[2] shouldBe r(1, 2)
            sorted[3] shouldBe r(1)
            sorted[4].isNaN shouldBe true
        }
    }

    "Math Utilities" {
        withClue("abs") {
            r(1, 2).abs() shouldBe r(1, 2)
            r(-1, 2).abs() shouldBe r(1, 2)
            Rational.ZERO.abs() shouldBe Rational.ZERO
        }

        withClue("floor") {
            r(7, 2).floor() shouldBe r(3)   // 3.5 -> 3
            r(19, 10).floor() shouldBe r(1) // 1.9 -> 1
            r(-7, 2).floor() shouldBe r(-4) // -3.5 -> -4
            r(1, 2).floor() shouldBe r(0)   // 0.5 -> 0
        }

        withClue("ceil") {
            r(7, 2).ceil() shouldBe r(4)    // 3.5 -> 4
            r(11, 10).ceil() shouldBe r(2)  // 1.1 -> 2
            r(2).ceil() shouldBe r(2)       // 2.0 -> 2
            r(-7, 2).ceil() shouldBe r(-3)  // -3.5 -> -3
            r(1, 2).ceil() shouldBe r(1)    // 0.5 -> 1
        }

        withClue("frac") {
            r(7, 2).frac() shouldBe r(1, 2) // 3.5 - 3 = 0.5
            r(5, 4).frac() shouldBe r(1, 4) // 1.25 - 1 = 0.25
            r(2).frac() shouldBe Rational.ZERO
        }
    }

    "Edge Cases & Safety" {
        withClue("Division by zero") {
            (r(1) / r(0)) shouldBe Rational.NaN
            Rational(1.0) / Rational(0.0) shouldBe Rational.NaN
        }

        withClue("Operations with NaN") {
            (r(1) + Rational.NaN) shouldBe Rational.NaN
            (Rational.NaN * r(5)) shouldBe Rational.NaN
            (Rational.NaN / Rational.NaN) shouldBe Rational.NaN
        }

        withClue("Long.MIN_VALUE safety") {
            // abs(Long.MIN_VALUE) fails in standard math, Rational should handle it
            val min = Rational(Long.MIN_VALUE)
            val one = Rational(1)

            // Should not crash
            val res = min / one
            res.numerator shouldBe Long.MIN_VALUE

            // GCD involving MIN_VALUE
            // if we have MIN_VALUE / MIN_VALUE it should simplify to 1
            val selfDiv = Rational(Long.MIN_VALUE) / Rational(Long.MIN_VALUE)
            selfDiv shouldBe Rational.ONE
        }

        withClue("Accumulated precision (Euclidean pattern)") {
            // 1/8 step duration
            val stepDuration = r(1, 8)

            // Summing 8 steps should equal exactly 1.0 (no floating point drift)
            var total = Rational.ZERO
            repeat(8) { total += stepDuration }

            total shouldBe Rational.ONE
        }

        withClue("Large number arithmetic (Overflow checks)") {
            val million = Rational(1_000_000)

            // 1M * 1M = 1T (fits in Long)
            (million * million) shouldBe Rational(1_000_000_000_000L)

            // Check that huge cancellation works
            // (1M / 1) * (1 / 1M) = 1
            (million * (Rational(1) / million)) shouldBe Rational.ONE
        }
    }

    "Number Interop" {
        withClue("Number.toRational()") {
            5.toRational() shouldBe r(5)
            0.5.toRational() shouldBe r(1, 2)
            100L.toRational() shouldBe r(100)
        }

        withClue("rem(Number) operator") {
            (r(7, 2) % 2) shouldBe r(3, 2)
        }
    }
})
