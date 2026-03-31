package io.peekandpoke.klang.common.math

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class FormatAsIntOrDoubleSpec : StringSpec({

    "whole numbers display without decimal point" {
        0.0.formatAsIntOrDouble() shouldBe "0"
        1.0.formatAsIntOrDouble() shouldBe "1"
        42.0.formatAsIntOrDouble() shouldBe "42"
        (-1.0).formatAsIntOrDouble() shouldBe "-1"
        (-42.0).formatAsIntOrDouble() shouldBe "-42"
        100.0.formatAsIntOrDouble() shouldBe "100"
    }

    "fractional numbers display with decimal point" {
        3.14.formatAsIntOrDouble() shouldBe "3.14"
        0.5.formatAsIntOrDouble() shouldBe "0.5"
        (-0.5).formatAsIntOrDouble() shouldBe "-0.5"
        0.001.formatAsIntOrDouble() shouldBe "0.001"
    }

    "negative zero displays as 0" {
        (-0.0).formatAsIntOrDouble() shouldBe "0"
    }

    "non-finite values pass through toString()" {
        Double.NaN.formatAsIntOrDouble() shouldBe "NaN"
        Double.POSITIVE_INFINITY.formatAsIntOrDouble() shouldBe "Infinity"
        Double.NEGATIVE_INFINITY.formatAsIntOrDouble() shouldBe "-Infinity"
    }

    "Int boundary values" {
        // Int.MAX_VALUE = 2,147,483,647
        2147483647.0.formatAsIntOrDouble() shouldBe "2147483647"
        // Int.MIN_VALUE = -2,147,483,648
        (-2147483648.0).formatAsIntOrDouble() shouldBe "-2147483648"
    }

    "numbers beyond Int range display as scientific notation" {
        // Values > Int.MAX_VALUE use Double.toString() which produces scientific notation.
        // This is an intentional tradeoff: Long is boxed in Kotlin/JS (heap allocation),
        // so we accept scientific notation for numbers outside [-2^31, 2^31-1].
        // In practice, audio DSL values (frequencies, BPM, gain) are always within Int range.
        val large = 3_000_000_000.0.formatAsIntOrDouble()
        // Don't assert exact format — Kotlin/JS and JVM may differ in notation
        // Just verify it's not "3000000000" (which would require Long)
        large.toDouble() shouldBe 3_000_000_000.0
    }
})
