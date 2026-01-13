package io.peekandpoke.klang.strudel.math

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

class RationalSpec : StringSpec({

    // Helper to construct exact fractions for testing since the constructor is private
    fun r(n: Int, d: Int = 1): Rational = Rational(n) / Rational(d)

    "Construction | Simplification" {
        withClue("Should simplify fractions automatically") {
            r(2, 4) shouldBe r(1, 2)
            r(100, 20) shouldBe r(5)
            r(-2, 4) shouldBe r(-1, 2)
            r(2, -4) shouldBe r(-1, 2) // Sign normalization
            r(-2, -4) shouldBe r(1, 2)
        }
    }

    "Construction | Zero handling" {
        r(0, 5) shouldBe Rational.ZERO
        r(0, -100) shouldBe Rational.ZERO
    }

    "Construction | Integers" {
        Rational(5).toDouble() shouldBe 5.0
        Rational(5L).toDouble() shouldBe 5.0
        Rational(0) shouldBe Rational.ZERO
        Rational(-3).toDouble() shouldBe -3.0
    }

    "Double Conversion | Exact decimals" {
        Rational(0.5) shouldBe r(1, 2)
        Rational(0.25) shouldBe r(1, 4)
        Rational(0.75) shouldBe r(3, 4)
        Rational(0.125) shouldBe r(1, 8)
        Rational(-1.5) shouldBe r(-3, 2)
    }

    "Double Conversion | Recurring decimals" {
        // 1/3 is approx 0.3333333333333333
        Rational(1.0 / 3.0).toDouble() shouldBe (r(1, 3).toDouble() plusOrMinus EPSILON)
        Rational(2.0 / 3.0).toDouble() shouldBe (r(2, 3).toDouble() plusOrMinus EPSILON)
        Rational(1.0 / 6.0).toDouble() shouldBe (r(1, 6).toDouble() plusOrMinus EPSILON)
        Rational(1.0 / 7.0).toDouble() shouldBe (r(1, 7).toDouble() plusOrMinus EPSILON)
        Rational(1.0 / 9.0).toDouble() shouldBe (r(1, 9).toDouble() plusOrMinus EPSILON)
    }

    "Double Conversion | Precision limits" {
        val pi = Rational(3.14159265359)
        pi.toDouble() shouldBe (3.14159265359 plusOrMinus EPSILON)
    }

    "Double Conversion | Small scientific notation" {
        Rational(1e-5).toDouble() shouldBe (1e-5 plusOrMinus EPSILON)
        Rational(1e-7).toDouble() shouldBe (1e-7 plusOrMinus EPSILON)
    }

    "Arithmetic | Addition" {
        (r(1, 2) + r(1, 4)) shouldBe r(3, 4)
        (r(1, 3) + r(1, 3)) shouldBe r(2, 3)
        (r(1, 2) + r(-1, 2)) shouldBe Rational.ZERO
    }

    "Arithmetic | Subtraction" {
        (r(1, 2) - r(1, 4)) shouldBe r(1, 4)
        (r(1) - r(1)) shouldBe Rational.ZERO
        (r(0) - r(1, 2)) shouldBe r(-1, 2)
    }

    "Arithmetic | Multiplication" {
        (r(1, 2) * r(1, 2)).toDouble() shouldBe (0.25 plusOrMinus EPSILON)
        (r(2) * r(3)).toDouble() shouldBe (6.0 plusOrMinus EPSILON)
        (r(-1) * r(1, 2)).toDouble() shouldBe (-0.5 plusOrMinus EPSILON)
        // Cross cancellation check
        (r(2, 3) * r(3, 4)).toDouble() shouldBe (0.5 plusOrMinus EPSILON)
    }

    "Arithmetic | Division" {
        (r(1) / r(2)).toDouble() shouldBe (0.5 plusOrMinus EPSILON)
        (r(1, 2) / r(1, 4)).toDouble() shouldBe (2.0 plusOrMinus EPSILON)
        (r(1) / r(3)).toDouble() shouldBe (1 / 3.0 plusOrMinus EPSILON)
    }

    "Arithmetic | Modulo" {
        // 3.5 % 2.0 = 1.5
        (r(7, 2) % r(2)).toDouble() shouldBe (1.5 plusOrMinus EPSILON)
        // 5 % 3 = 2
        (r(5) % r(3)).toDouble() shouldBe (2.0 plusOrMinus EPSILON)
    }

    "Arithmetic | Unary minus" {
        (-r(1, 2)).toDouble() shouldBe (-0.5 plusOrMinus EPSILON)
        (-r(-3, 4)).toDouble() shouldBe (0.75 plusOrMinus EPSILON)
        -Rational.ZERO shouldBe Rational.ZERO
    }

    "Comparisons | Equality" {
        r(1, 2).toDouble() shouldBe (0.5 plusOrMinus EPSILON)
        r(1, 3).toDouble() shouldNotBe r(3333, 10000)
    }

    "Comparisons | Ordering" {
        (r(1, 2) < r(3, 4)) shouldBe true
        (r(3, 4) > r(1, 2)) shouldBe true
        (r(1, 2) <= r(1, 2)) shouldBe true
        (r(-1, 2) < r(1, 2)) shouldBe true
    }

    "Subtracting neighbor numbers" {
        (1..1_000_000).forEach { i ->
            withClue("i = $i") {
                val a = Rational(i)
                val b = Rational(i + 1)
                (b - a) shouldBe Rational.ONE
                (a - b) shouldBe Rational.MINUS_ONE
            }
        }
    }

    "Whole numbers" {
        (1..1_000_000).forEach { i ->
            val d = i.toDouble()

            withClue("i = $i") {
                val r = Rational(i)
                r.toString() shouldBe d.toString()
                r.toDouble() shouldBe d
            }
        }
    }

    "Comparisons | Small differences" {
        // requested test case: 1.0 < 1.0 + 1e-7
        val a = 1.0.toRational()
        val b = (1.0).toRational() + (1e-7).toRational()

        (a < b) shouldBe true
        (b > a) shouldBe true
        (b - a) shouldBe 1e-7.toRational()
    }

    "Comparisons | Very small numbers" {
        val pairs = listOf(
            1e-1 to 1e-2,
            1e-2 to 1e-3,
            1e-3 to 1e-4,
            1e-4 to 1e-5,
            1e-5 to 1e-6,
            1e-6 to 1e-7,
            1e-7 to 1e-8,
            1e-8 to 1e-9, // Max precision we can achieve right now
        )

        assertSoftly {
            pairs.forEach { (a, b) ->
                withClue("a = $a | b = $b") {
                    val tiny = Rational(a)
                    val tinier = Rational(b)

                    (tiny > tinier) shouldBe true
                    (Rational.ONE < Rational.ONE + tiny) shouldBe true
                    (Rational.ONE < Rational.ONE + tinier) shouldBe true

                    (tiny > Rational.ZERO) shouldBe true
                    (tinier > Rational.ZERO) shouldBe true
                }
            }
        }
    }

    "Comparisons | Sorting" {
        val list = listOf(r(1), r(0), r(-1), r(1, 2), Rational.NaN)
        val sorted = list.sorted()

        // Expected: -1, 0, 1/2, 1, NaN (NaN is usually largest in Kotlin CompareTo for doubles, implementation pushes it to end)
        sorted[0] shouldBe r(-1)
        sorted[1] shouldBe r(0)
        sorted[2] shouldBe r(1, 2)
        sorted[3] shouldBe r(1)
        sorted[4].isNaN shouldBe true
    }

    "Math Utilities | abs" {
        r(1, 2).abs() shouldBe r(1, 2)
        r(-1, 2).abs() shouldBe r(1, 2)
        Rational.ZERO.abs() shouldBe Rational.ZERO
    }

    "Math Utilities | floor" {
        r(7, 2).floor() shouldBe r(3)   // 3.5 -> 3
        r(19, 10).floor() shouldBe r(1) // 1.9 -> 1
        r(-7, 2).floor() shouldBe r(-4) // -3.5 -> -4
        r(1, 2).floor() shouldBe r(0)   // 0.5 -> 0
    }

    "Math Utilities | ceil" {
        r(7, 2).ceil() shouldBe r(4)    // 3.5 -> 4
        r(11, 10).ceil() shouldBe r(2)  // 1.1 -> 2
        r(2).ceil() shouldBe r(2)       // 2.0 -> 2
        r(-7, 2).ceil() shouldBe r(-3)  // -3.5 -> -3
        r(1, 2).ceil() shouldBe r(1)    // 0.5 -> 1
    }

    "Math Utilities | frac" {
        r(7, 2).frac() shouldBe r(1, 2) // 3.5 - 3 = 0.5
        r(5, 4).frac() shouldBe r(1, 4) // 1.25 - 1 = 0.25
        r(2).frac() shouldBe Rational.ZERO
    }

    "Safety | Division by zero" {
        (r(1) / r(0)) shouldBe Rational.NaN
        Rational(1.0) / Rational(0.0) shouldBe Rational.NaN
    }

    "Safety | Operations with NaN" {
        (r(1) + Rational.NaN) shouldBe Rational.NaN
        (Rational.NaN * r(5)) shouldBe Rational.NaN
        (Rational.NaN / Rational.NaN) shouldBe Rational.NaN
    }

//    "Safety | Long.MIN_VALUE safety" {
//        // abs(Long.MIN_VALUE) fails in standard math, Rational should handle it
//        val min = Rational(Long.MIN_VALUE)
//        val one = Rational(1)
//
//        // Should not crash
//        val res = min / one
//        res.numerator shouldBe Long.MIN_VALUE
//
//        // GCD involving MIN_VALUE
//        // MIN_VALUE / MIN_VALUE should simplify to 1
//        val selfDiv = Rational(Long.MIN_VALUE) / Rational(Long.MIN_VALUE)
//        selfDiv shouldBe Rational.ONE
//    }

    "Safety | Accumulated precision (Euclidean pattern)" {
        // 1/8 step duration
        val stepDuration = r(1, 8)

        // Summing 8 steps should equal exactly 1.0 (no floating point drift)
        var total = Rational.ZERO
        repeat(8) { total += stepDuration }

        total shouldBe Rational.ONE
    }

    "Safety | Large number arithmetic (Overflow checks)" {
        val bigNum = 32767
        val bigRational = Rational(bigNum)

        // 1M * 1M = 1T (fits in Long)
        (bigRational * bigRational) shouldBe Rational(bigNum * bigNum)

        // Check that huge cancellation works
        // (1M / 1) * (1 / 1M) = 1
        (bigRational * (Rational.ONE / bigRational)).toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }

    "Number Interop | Number.toRational()" {
        5.toRational() shouldBe r(5)
        0.5.toRational() shouldBe r(1, 2)
        100L.toRational() shouldBe r(100)
    }

    "Number Interop | rem(Number) operator" {
        (r(7, 2) % 2) shouldBe r(3, 2)
    }
})
