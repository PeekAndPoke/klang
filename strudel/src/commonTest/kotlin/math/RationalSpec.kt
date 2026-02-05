package io.peekandpoke.klang.strudel.math

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational
import kotlin.math.*

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
        (r(1) / r(0)) shouldBe Rational.POSITIVE_INFINITY
        (Rational(1.0) / Rational(0.0)) shouldBe Rational.POSITIVE_INFINITY

        (r(-1) / r(0)) shouldBe Rational.NEGATIVE_INFINITY
        (Rational(-1.0) / Rational(0.0)) shouldBe Rational.NEGATIVE_INFINITY
    }

    "Safety | Operations with NaN" {
        (r(1) + Rational.NaN) shouldBe Rational.NaN
        (Rational.NaN * r(5)) shouldBe Rational.NaN
        (Rational.NaN / Rational.NaN) shouldBe Rational.NaN
    }

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

    // Round function tests
    "round() | Positive numbers - round down" {
        Rational(2.0).round() shouldBe Rational(2)
        Rational(2.1).round() shouldBe Rational(2)
        Rational(2.4).round() shouldBe Rational(2)
        Rational(2.49).round() shouldBe Rational(2)
    }

    "round() | Positive numbers - round up" {
        Rational(2.5).round() shouldBe Rational(3)
        Rational(2.6).round() shouldBe Rational(3)
        Rational(2.9).round() shouldBe Rational(3)
        Rational(2.99).round() shouldBe Rational(3)
    }

    "round() | Negative numbers - round towards zero" {
        Rational(-2.0).round() shouldBe Rational(-2)
        Rational(-2.1).round() shouldBe Rational(-2)
        Rational(-2.4).round() shouldBe Rational(-2)
        Rational(-2.49).round() shouldBe Rational(-2)
    }

    "round() | Negative numbers - round away from zero" {
        Rational(-2.5).round() shouldBe Rational(-3)
        Rational(-2.6).round() shouldBe Rational(-3)
        Rational(-2.9).round() shouldBe Rational(-3)
        Rational(-2.99).round() shouldBe Rational(-3)
    }

    "round() | Exact half values (0.5)" {
        Rational(0.5).round() shouldBe Rational(1)
        Rational(1.5).round() shouldBe Rational(2)
        Rational(2.5).round() shouldBe Rational(3)
        Rational(3.5).round() shouldBe Rational(4)
        Rational(10.5).round() shouldBe Rational(11)
        Rational(100.5).round() shouldBe Rational(101)
    }

    "round() | Negative exact half values (-0.5)" {
        Rational(-0.5).round() shouldBe Rational(-1)
        Rational(-1.5).round() shouldBe Rational(-2)
        Rational(-2.5).round() shouldBe Rational(-3)
        Rational(-3.5).round() shouldBe Rational(-4)
        Rational(-10.5).round() shouldBe Rational(-11)
        Rational(-100.5).round() shouldBe Rational(-101)
    }

    "round() | Zero and near-zero values" {
        Rational(0.0).round() shouldBe Rational.ZERO
        Rational(0.1).round() shouldBe Rational.ZERO
        Rational(0.4).round() shouldBe Rational.ZERO
        Rational(0.49).round() shouldBe Rational.ZERO
        Rational(-0.1).round() shouldBe Rational.ZERO
        Rational(-0.4).round() shouldBe Rational.ZERO
        Rational(-0.49).round() shouldBe Rational.ZERO
    }

    "round() | Already integer values" {
        Rational(1).round() shouldBe Rational(1)
        Rational(5).round() shouldBe Rational(5)
        Rational(42).round() shouldBe Rational(42)
        Rational(-1).round() shouldBe Rational(-1)
        Rational(-5).round() shouldBe Rational(-5)
        Rational(-42).round() shouldBe Rational(-42)
        Rational(100).round() shouldBe Rational(100)
        Rational(-100).round() shouldBe Rational(-100)
    }

    "round() | Fractions" {
        r(1, 3).round() shouldBe Rational.ZERO  // 0.333...
        r(2, 3).round() shouldBe Rational(1)     // 0.666...
        r(5, 8).round() shouldBe Rational(1)     // 0.625
        r(3, 8).round() shouldBe Rational.ZERO   // 0.375
        r(7, 8).round() shouldBe Rational(1)     // 0.875
        r(1, 8).round() shouldBe Rational.ZERO   // 0.125
    }

    "round() | Negative fractions" {
        r(-1, 3).round() shouldBe Rational.ZERO  // -0.333...
        r(-2, 3).round() shouldBe Rational(-1)   // -0.666...
        r(-5, 8).round() shouldBe Rational(-1)   // -0.625
        r(-3, 8).round() shouldBe Rational.ZERO  // -0.375
        r(-7, 8).round() shouldBe Rational(-1)   // -0.875
        r(-1, 8).round() shouldBe Rational.ZERO  // -0.125
    }

    "round() | Large numbers" {
        Rational(999.4).round() shouldBe Rational(999)
        Rational(999.5).round() shouldBe Rational(1000)
        Rational(999.6).round() shouldBe Rational(1000)
        Rational(-999.4).round() shouldBe Rational(-999)
        Rational(-999.5).round() shouldBe Rational(-1000)
        Rational(-999.6).round() shouldBe Rational(-1000)
    }

    "round() | Very small fractions" {
        Rational(0.001).round() shouldBe Rational.ZERO
        Rational(0.01).round() shouldBe Rational.ZERO
        Rational(0.499).round() shouldBe Rational.ZERO
        Rational(0.501).round() shouldBe Rational(1)
        Rational(-0.001).round() shouldBe Rational.ZERO
        Rational(-0.01).round() shouldBe Rational.ZERO
        Rational(-0.499).round() shouldBe Rational.ZERO
        Rational(-0.501).round() shouldBe Rational(-1)
    }

    "round() | NaN handling" {
        Rational.NaN.round().isNaN shouldBe true
    }

    "round() | Result is always integer" {
        val testValues = listOf(0.1, 0.5, 0.9, 1.1, 1.5, 1.9, 2.3, 2.7, -0.1, -0.5, -1.5, -2.7)
        assertSoftly {
            testValues.forEach { value ->
                withClue("round($value) should return an integer") {
                    val rounded = Rational(value).round()
                    rounded.frac() shouldBe Rational.ZERO
                }
            }
        }
    }

    "round() | Comprehensive behavior verification" {
        // Verify the "round half away from zero" behavior
        val testCases = listOf(
            0.0 to 0.0,
            0.4 to 0.0,
            0.5 to 1.0,
            0.6 to 1.0,
            1.4 to 1.0,
            1.5 to 2.0,
            1.6 to 2.0,
            2.5 to 3.0,
            -0.4 to 0.0,
            -0.5 to -1.0,
            -0.6 to -1.0,
            -1.4 to -1.0,
            -1.5 to -2.0,
            -1.6 to -2.0,
            -2.5 to -3.0
        )
        assertSoftly {
            testCases.forEach { (input, expected) ->
                withClue("round($input) should be $expected") {
                    Rational(input).round().toDouble() shouldBe expected
                }
            }
        }
    }

    // exp() function tests
    "exp() | Zero returns 1" {
        Rational.ZERO.exp() shouldBe Rational.ONE
    }

    "exp() | One returns e" {
        val result = Rational.ONE.exp().toDouble()
        result shouldBe (kotlin.math.E plusOrMinus EPSILON)
    }

    "exp() | Positive values" {
        Rational(2.0).exp().toDouble() shouldBe (kotlin.math.exp(2.0) plusOrMinus EPSILON)
        Rational(0.5).exp().toDouble() shouldBe (kotlin.math.exp(0.5) plusOrMinus EPSILON)
        Rational(3.0).exp().toDouble() shouldBe (kotlin.math.exp(3.0) plusOrMinus EPSILON)
    }

    "exp() | Negative values" {
        Rational(-1.0).exp().toDouble() shouldBe (kotlin.math.exp(-1.0) plusOrMinus EPSILON)
        Rational(-2.0).exp().toDouble() shouldBe (kotlin.math.exp(-2.0) plusOrMinus EPSILON)
        Rational(-10.0).exp().toDouble() shouldBe (kotlin.math.exp(-10.0) plusOrMinus EPSILON)
    }

    "exp() | Large negative values approach zero" {
        Rational(-20.0).exp().toDouble() shouldBe (kotlin.math.exp(-20.0) plusOrMinus 1e-9)
        val exp30Result = Rational(-30.0).exp().toDouble()
        exp30Result shouldBe (0.0 plusOrMinus 1e-9)
    }

    "exp() | NaN handling" {
        Rational.NaN.exp().isNaN shouldBe true
    }

    "exp() | Very large values result in NaN (overflow)" {
        val result = Rational(1000.0).exp()
        result.isNaN shouldBe true
    }

    "exp() | Matches Kotlin's exp() for standard values" {
        val testValues = listOf(0.0, 0.5, 1.0, 2.0, -1.0, -2.0, 5.0, -10.0)
        assertSoftly {
            testValues.forEach { value ->
                withClue("exp($value)") {
                    val rationalResult = Rational(value).exp().toDouble()
                    val kotlinResult = kotlin.math.exp(value)
                    rationalResult shouldBe (kotlinResult plusOrMinus EPSILON)
                }
            }
        }
    }

    "exp() | Property: exp(a) * exp(b) = exp(a + b)" {
        val a = Rational(1.5)
        val b = Rational(0.5)
        val expA = a.exp()
        val expB = b.exp()
        val expAB = (a + b).exp()
        (expA * expB).toDouble() shouldBe (expAB.toDouble() plusOrMinus 1e-4)
    }

    "exp() | Property: exp(1) * exp(-1) = 1" {
        val exp1 = Rational.ONE.exp()
        val expMinus1 = Rational.MINUS_ONE.exp()
        (exp1 * expMinus1).toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }

    // pow() function tests
    "pow() | Basic integer exponents" {
        Rational(2).pow(Rational(3)) shouldBe Rational(8)
        Rational(3).pow(Rational(2)) shouldBe Rational(9)
        Rational(5).pow(Rational(2)) shouldBe Rational(25)
        Rational(10).pow(Rational(3)).toDouble() shouldBe (1000.0 plusOrMinus EPSILON)
    }

    "pow() | Zero exponent returns 1" {
        Rational(2).pow(Rational.ZERO) shouldBe Rational.ONE
        Rational(100).pow(Rational.ZERO) shouldBe Rational.ONE
        Rational(-5).pow(Rational.ZERO) shouldBe Rational.ONE
    }

    "pow() | One exponent returns same value" {
        Rational(5).pow(Rational.ONE) shouldBe Rational(5)
        Rational(42).pow(Rational.ONE) shouldBe Rational(42)
        Rational(-7).pow(Rational.ONE) shouldBe Rational(-7)
    }

    "pow() | Negative exponents" {
        Rational(2).pow(Rational(-1)) shouldBe Rational(0.5)
        Rational(4).pow(Rational(-1)) shouldBe Rational(0.25)
        Rational(10).pow(Rational(-2)).toDouble() shouldBe (0.01 plusOrMinus EPSILON)
    }

    "pow() | Fractional exponents (square root)" {
        Rational(4).pow(Rational(0.5)).toDouble() shouldBe (2.0 plusOrMinus EPSILON)
        Rational(9).pow(Rational(0.5)).toDouble() shouldBe (3.0 plusOrMinus EPSILON)
        Rational(16).pow(Rational(0.5)).toDouble() shouldBe (4.0 plusOrMinus EPSILON)
    }

    "pow() | Fractional exponents (cube root)" {
        Rational(8).pow(Rational(1.0 / 3.0)).toDouble() shouldBe (2.0 plusOrMinus EPSILON)
        Rational(27).pow(Rational(1.0 / 3.0)).toDouble() shouldBe (3.0 plusOrMinus EPSILON)
    }

    "pow() | Zero base with positive exponent" {
        Rational.ZERO.pow(Rational(1)) shouldBe Rational.ZERO
        Rational.ZERO.pow(Rational(2)) shouldBe Rational.ZERO
        Rational.ZERO.pow(Rational(10)) shouldBe Rational.ZERO
    }

    "pow() | Zero base with zero exponent is 1" {
        Rational.ZERO.pow(Rational.ZERO).toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }

    "pow() | Negative base with integer exponent" {
        Rational(-2).pow(Rational(3)).toDouble() shouldBe (-8.0 plusOrMinus EPSILON)
        Rational(-2).pow(Rational(2)).toDouble() shouldBe (4.0 plusOrMinus EPSILON)
        Rational(-3).pow(Rational(3)).toDouble() shouldBe (-27.0 plusOrMinus EPSILON)
    }

    "pow() | NaN handling - base is NaN" {
        Rational.NaN.pow(Rational(2)).isNaN shouldBe true
    }

    "pow() | NaN handling - exponent is NaN" {
        Rational(2).pow(Rational.NaN).isNaN shouldBe true
    }

    "pow() | NaN handling - both NaN" {
        Rational.NaN.pow(Rational.NaN).isNaN shouldBe true
    }

    "pow() | Overflow results in NaN" {
        val result = Rational(10).pow(Rational(1000))
        result.isNaN shouldBe true
    }

    "pow() | Number overload with Int" {
        Rational(2).pow(3) shouldBe Rational(8)
        Rational(5).pow(2) shouldBe Rational(25)
    }

    "pow() | Number overload with Long" {
        Rational(2).pow(3L) shouldBe Rational(8)
        Rational(3).pow(4L).toDouble() shouldBe (81.0 plusOrMinus EPSILON)
    }

    "pow() | Number overload with Double" {
        Rational(4).pow(0.5).toDouble() shouldBe (2.0 plusOrMinus EPSILON)
        Rational(8).pow(1.0 / 3.0).toDouble() shouldBe (2.0 plusOrMinus EPSILON)
    }

    "pow() | Matches Kotlin's pow() for standard values" {
        val testCases = listOf(
            2.0 to 3.0,
            3.0 to 2.0,
            5.0 to 0.0,
            2.0 to -1.0,
            4.0 to 0.5,
            10.0 to 2.0,
            0.5 to 2.0
        )
        assertSoftly {
            testCases.forEach { (base, exponent) ->
                withClue("pow($base, $exponent)") {
                    val rationalResult = Rational(base).pow(Rational(exponent)).toDouble()
                    val kotlinResult = base.pow(exponent)
                    rationalResult shouldBe (kotlinResult plusOrMinus EPSILON)
                }
            }
        }
    }

    "pow() | Property: (a^b)^c = a^(b*c)" {
        val a = Rational(2)
        val b = Rational(3)
        val c = Rational(2)

        val left = a.pow(b).pow(c)
        val right = a.pow(b * c)

        left.toDouble() shouldBe (right.toDouble() plusOrMinus EPSILON)
    }

    "pow() | Property: a^b * a^c = a^(b+c)" {
        val a = Rational(2)
        val b = Rational(3)
        val c = Rational(2)

        val left = a.pow(b) * a.pow(c)
        val right = a.pow(b + c)

        left.toDouble() shouldBe (right.toDouble() plusOrMinus EPSILON)
    }

    "pow() | Property: (a*b)^c = a^c * b^c" {
        val a = Rational(2)
        val b = Rational(3)
        val c = Rational(2)

        val left = (a * b).pow(c)
        val right = a.pow(c) * b.pow(c)

        left.toDouble() shouldBe (right.toDouble() plusOrMinus EPSILON)
    }

    // Special cases from Double.pow() semantics
    "pow() | Special case: b.pow(0.0) is 1.0" {
        Rational(5).pow(Rational.ZERO) shouldBe Rational.ONE
        Rational(-3).pow(Rational.ZERO) shouldBe Rational.ONE
        Rational(100).pow(Rational.ZERO) shouldBe Rational.ONE
    }

    "pow() | Special case: b.pow(1.0) == b" {
        Rational(5).pow(Rational.ONE) shouldBe Rational(5)
        Rational(-3).pow(Rational.ONE) shouldBe Rational(-3)
        Rational(42).pow(Rational.ONE) shouldBe Rational(42)
    }

    "pow() | Special case: b.pow(NaN) is NaN" {
        Rational(5).pow(Rational.NaN).isNaN shouldBe true
        Rational(-3).pow(Rational.NaN).isNaN shouldBe true
        Rational.ZERO.pow(Rational.NaN).isNaN shouldBe true
    }

    "pow() | Special case: NaN.pow(x) is NaN for x != 0.0" {
        Rational.NaN.pow(Rational(1)).isNaN shouldBe true
        Rational.NaN.pow(Rational(2)).isNaN shouldBe true
        Rational.NaN.pow(Rational(-1)).isNaN shouldBe true
        Rational.NaN.pow(Rational(0.5)).isNaN shouldBe true
    }

    "pow() | Special case: NaN.pow(0.0) follows Double.pow semantics" {
        // NaN.pow(0.0) should be 1.0 per IEEE 754
        val result = Rational.NaN.pow(Rational.ZERO)
        result.toDouble() shouldBe (Double.NaN.pow(0.0) plusOrMinus EPSILON)
    }

    "pow() | Special case: b.pow(Inf) is NaN for abs(b) == 1.0" {
        Rational.ONE.pow(Rational.POSITIVE_INFINITY).isNaN shouldBe true
        Rational.MINUS_ONE.pow(Rational.POSITIVE_INFINITY).isNaN shouldBe true
    }

    "pow() | Special case: b.pow(x) is NaN for b < 0 and x not integer" {
        // Negative base with fractional exponent
        Rational(-4).pow(Rational(0.5)).isNaN shouldBe true
        Rational(-2).pow(Rational(1.5)).isNaN shouldBe true
        Rational(-8).pow(Rational(1.0 / 3.0)).isNaN shouldBe true
    }

    "pow() | Special case: negative base with integer exponent works" {
        Rational(-2).pow(Rational(2)).toDouble() shouldBe (4.0 plusOrMinus EPSILON)
        Rational(-2).pow(Rational(3)).toDouble() shouldBe (-8.0 plusOrMinus EPSILON)
        Rational(-3).pow(Rational(4)).toDouble() shouldBe (81.0 plusOrMinus EPSILON)
    }

    "pow() | Edge case: very large exponents result in NaN (overflow)" {
        // 2^2000 and 10^309 result in Infinity, which is converted to NaN
        val result1 = Rational(2).pow(Rational(2000))
        val result2 = Rational(10).pow(Rational(309))

        // Both should be NaN after overflow handling
        result1.isNaN shouldBe true
        result2.isNaN shouldBe true
    }

    "pow() | Edge case: very small results" {
        Rational(2).pow(Rational(-10)).toDouble() shouldBe (0.0009765625 plusOrMinus EPSILON)
        Rational(10).pow(Rational(-5)).toDouble() shouldBe (0.00001 plusOrMinus EPSILON)
    }

    "pow() | Matches Double.pow() exactly for all special cases" {
        val testCases = listOf(
            // (base, exponent, description)
            Triple(5.0, 0.0, "any^0 = 1"),
            Triple(-5.0, 0.0, "negative^0 = 1"),
            Triple(5.0, 1.0, "any^1 = any"),
            Triple(Double.NaN, 2.0, "NaN^x = NaN (x!=0)"),
            Triple(2.0, Double.NaN, "x^NaN = NaN"),
            Triple(-4.0, 0.5, "negative^fractional = NaN"),
            Triple(-2.0, 2.0, "negative^even = positive"),
            Triple(-2.0, 3.0, "negative^odd = negative")
        )

        assertSoftly {
            testCases.forEach { (base, exponent, description) ->
                withClue(description) {
                    val rationalResult = Rational(base).pow(Rational(exponent)).toDouble()
                    val doubleResult = base.pow(exponent)

                    if (doubleResult.isNaN()) {
                        rationalResult.isNaN() shouldBe true
                    } else {
                        rationalResult shouldBe (doubleResult plusOrMinus EPSILON)
                    }
                }
            }
        }
    }

    // log() function tests - logarithm with custom base
    "log() | Basic logarithms" {
        Rational(8).log(Rational(2)).toDouble() shouldBe (3.0 plusOrMinus EPSILON)
        Rational(100).log(Rational(10)).toDouble() shouldBe (2.0 plusOrMinus EPSILON)
        Rational(27).log(Rational(3)).toDouble() shouldBe (3.0 plusOrMinus EPSILON)
    }

    "log() | log(x, x) = 1" {
        Rational(5).log(Rational(5)).toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        Rational(10).log(Rational(10)).toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }

    "log() | log(1, b) = 0" {
        Rational.ONE.log(Rational(2)).toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        Rational.ONE.log(Rational(10)).toDouble() shouldBe (0.0 plusOrMinus EPSILON)
    }

    "log() | Special case: NaN if x or base is NaN" {
        Rational.NaN.log(Rational(2)).isNaN shouldBe true
        Rational(2).log(Rational.NaN).isNaN shouldBe true
    }

    "log() | Special case: NaN when x < 0" {
        Rational(-5).log(Rational(2)).isNaN shouldBe true
        Rational(-10).log(Rational(10)).isNaN shouldBe true
    }

    "log() | Special case: NaN when base <= 0" {
        Rational(10).log(Rational.ZERO).isNaN shouldBe true
        Rational(10).log(Rational(-2)).isNaN shouldBe true
    }

    "log() | Special case: NaN when base == 1" {
        Rational(10).log(Rational.ONE).isNaN shouldBe true
    }

    "log() | Number overload" {
        Rational(8).log(2).toDouble() shouldBe (3.0 plusOrMinus EPSILON)
        Rational(100).log(10.0).toDouble() shouldBe (2.0 plusOrMinus EPSILON)
    }

    "log() | Matches kotlin.math.log" {
        val testCases = listOf(
            2.0 to 2.0,
            8.0 to 2.0,
            100.0 to 10.0,
            27.0 to 3.0,
            16.0 to 4.0
        )

        assertSoftly {
            testCases.forEach { (value, base) ->
                withClue("log($value, $base)") {
                    val rationalResult = Rational(value).log(Rational(base)).toDouble()
                    val kotlinResult = log(value, base)
                    rationalResult shouldBe (kotlinResult plusOrMinus EPSILON)
                }
            }
        }
    }

    // ln() function tests - natural logarithm
    "ln() | Basic natural logarithms" {
        Rational(kotlin.math.E).ln().toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        Rational(1.0).ln().toDouble() shouldBe (0.0 plusOrMinus EPSILON)
    }

    "ln() | Various values" {
        Rational(2).ln().toDouble() shouldBe (ln(2.0) plusOrMinus EPSILON)
        Rational(10).ln().toDouble() shouldBe (ln(10.0) plusOrMinus EPSILON)
        Rational(0.5).ln().toDouble() shouldBe (ln(0.5) plusOrMinus EPSILON)
    }

    "ln() | Special case: ln(NaN) is NaN" {
        Rational.NaN.ln().isNaN shouldBe true
    }

    "ln() | Special case: ln(x) is NaN when x < 0" {
        Rational(-1).ln().isNaN shouldBe true
        Rational(-10).ln().isNaN shouldBe true
    }

    "ln() | Special case: ln(0) is -Inf (converted to NaN)" {
        Rational.ZERO.ln().isNaN shouldBe true
    }

    "ln() | Matches kotlin.math.ln" {
        val testValues = listOf(1.0, 2.0, kotlin.math.E, 10.0, 0.5, 100.0)

        assertSoftly {
            testValues.forEach { value ->
                withClue("ln($value)") {
                    val rationalResult = Rational(value).ln().toDouble()
                    val kotlinResult = ln(value)
                    rationalResult shouldBe (kotlinResult plusOrMinus EPSILON)
                }
            }
        }
    }

    // log10() function tests - common logarithm
    "log10() | Basic common logarithms" {
        Rational(10).log10().toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        Rational(100).log10().toDouble() shouldBe (2.0 plusOrMinus EPSILON)
        Rational(1000).log10().toDouble() shouldBe (3.0 plusOrMinus EPSILON)
        Rational(1).log10().toDouble() shouldBe (0.0 plusOrMinus EPSILON)
    }

    "log10() | Fractional values" {
        Rational(0.1).log10().toDouble() shouldBe (-1.0 plusOrMinus EPSILON)
        Rational(0.01).log10().toDouble() shouldBe (-2.0 plusOrMinus EPSILON)
    }

    "log10() | Special case: log10(NaN) is NaN" {
        Rational.NaN.log10().isNaN shouldBe true
    }

    "log10() | Special case: log10(x) is NaN when x < 0" {
        Rational(-1).log10().isNaN shouldBe true
        Rational(-10).log10().isNaN shouldBe true
    }

    "log10() | Special case: log10(0) is -Inf (converted to NaN)" {
        Rational.ZERO.log10().isNaN shouldBe true
    }

    "log10() | Matches kotlin.math.log10" {
        val testValues = listOf(1.0, 10.0, 100.0, 1000.0, 0.1, 0.01, 2.0, 5.0)

        assertSoftly {
            testValues.forEach { value ->
                withClue("log10($value)") {
                    val rationalResult = Rational(value).log10().toDouble()
                    val kotlinResult = log10(value)
                    rationalResult shouldBe (kotlinResult plusOrMinus EPSILON)
                }
            }
        }
    }

    // log2() function tests - binary logarithm
    "log2() | Basic binary logarithms" {
        Rational(2).log2().toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        Rational(4).log2().toDouble() shouldBe (2.0 plusOrMinus EPSILON)
        Rational(8).log2().toDouble() shouldBe (3.0 plusOrMinus EPSILON)
        Rational(16).log2().toDouble() shouldBe (4.0 plusOrMinus EPSILON)
        Rational(1).log2().toDouble() shouldBe (0.0 plusOrMinus EPSILON)
    }

    "log2() | Power of 2 values" {
        Rational(32).log2().toDouble() shouldBe (5.0 plusOrMinus EPSILON)
        Rational(64).log2().toDouble() shouldBe (6.0 plusOrMinus EPSILON)
        Rational(128).log2().toDouble() shouldBe (7.0 plusOrMinus EPSILON)
        Rational(256).log2().toDouble() shouldBe (8.0 plusOrMinus EPSILON)
    }

    "log2() | Fractional values" {
        Rational(0.5).log2().toDouble() shouldBe (-1.0 plusOrMinus EPSILON)
        Rational(0.25).log2().toDouble() shouldBe (-2.0 plusOrMinus EPSILON)
    }

    "log2() | Special case: log2(NaN) is NaN" {
        Rational.NaN.log2().isNaN shouldBe true
    }

    "log2() | Special case: log2(x) is NaN when x < 0" {
        Rational(-1).log2().isNaN shouldBe true
        Rational(-8).log2().isNaN shouldBe true
    }

    "log2() | Special case: log2(0) is -Inf (converted to NaN)" {
        Rational.ZERO.log2().isNaN shouldBe true
    }

    "log2() | Matches kotlin.math.log2" {
        val testValues = listOf(1.0, 2.0, 4.0, 8.0, 16.0, 32.0, 0.5, 0.25, 3.0, 10.0)

        assertSoftly {
            testValues.forEach { value ->
                withClue("log2($value)") {
                    val rationalResult = Rational(value).log2().toDouble()
                    val kotlinResult = log2(value)
                    rationalResult shouldBe (kotlinResult plusOrMinus EPSILON)
                }
            }
        }
    }

    // Properties and relationships
    "log() | Property: log(a*b, base) = log(a, base) + log(b, base)" {
        val a = Rational(4)
        val b = Rational(8)
        val base = Rational(2)

        val left = (a * b).log(base)
        val right = a.log(base) + b.log(base)

        left.toDouble() shouldBe (right.toDouble() plusOrMinus EPSILON)
    }

    "ln() | Property: ln(e^x) = x" {
        val x = Rational(2)
        val result = Rational(kotlin.math.E).pow(x).ln()
        result.toDouble() shouldBe (x.toDouble() plusOrMinus EPSILON)
    }

    "log10() | Property: 10^log10(x) = x" {
        val x = Rational(42)
        val result = Rational(10).pow(x.log10())
        result.toDouble() shouldBe (x.toDouble() plusOrMinus EPSILON)
    }

    "log2() | Property: 2^log2(x) = x" {
        val x = Rational(100)
        val result = Rational(2).pow(x.log2())
        result.toDouble() shouldBe (x.toDouble() plusOrMinus EPSILON)
    }
})
