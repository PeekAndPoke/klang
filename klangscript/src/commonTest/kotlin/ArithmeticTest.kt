package io.peekandpoke.klang.script

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.script.runtime.NumberValue

/**
 * Tests for arithmetic operations in KlangScript
 *
 * Covers:
 * - Basic operations: +, -, *, /
 * - Operator precedence
 * - Parenthesized expressions
 * - Nested operations
 * - Use in function arguments
 */
class ArithmeticTest : StringSpec({

    "should evaluate simple addition" {
        val engine = klangScript()
        val result = engine.execute("1 + 2")

        (result as NumberValue).value shouldBe 3.0
    }

    "should evaluate simple subtraction" {
        val engine = klangScript()
        val result = engine.execute("5 - 3")

        (result as NumberValue).value shouldBe 2.0
    }

    "should evaluate simple multiplication" {
        val engine = klangScript()
        val result = engine.execute("3 * 4")

        (result as NumberValue).value shouldBe 12.0
    }

    "should evaluate simple division" {
        val engine = klangScript()
        val result = engine.execute("10 / 2")

        (result as NumberValue).value shouldBe 5.0
    }

    "should respect operator precedence - multiplication before addition" {
        val engine = klangScript()
        val result = engine.execute("1 + 2 * 3")

        // Should be 1 + (2 * 3) = 1 + 6 = 7
        (result as NumberValue).value shouldBe 7.0
    }

    "should respect operator precedence - division before subtraction" {
        val engine = klangScript()
        val result = engine.execute("10 - 6 / 2")

        // Should be 10 - (6 / 2) = 10 - 3 = 7
        (result as NumberValue).value shouldBe 7.0
    }

    "should evaluate parenthesized expressions correctly" {
        val engine = klangScript()
        val result = engine.execute("(1 + 2) * 3")

        // Should be (1 + 2) * 3 = 3 * 3 = 9
        (result as NumberValue).value shouldBe 9.0
    }

    "should handle left associativity for subtraction" {
        val engine = klangScript()
        val result = engine.execute("10 - 3 - 2")

        // Should be (10 - 3) - 2 = 7 - 2 = 5
        (result as NumberValue).value shouldBe 5.0
    }

    "should handle left associativity for division" {
        val engine = klangScript()
        val result = engine.execute("12 / 3 / 2")

        // Should be (12 / 3) / 2 = 4 / 2 = 2
        (result as NumberValue).value shouldBe 2.0
    }

    "should handle complex nested expressions" {
        val engine = klangScript()
        val result = engine.execute("(10 + 5) * 2 - 8 / 4")

        // Should be (10 + 5) * 2 - 8 / 4
        // = 15 * 2 - 8 / 4
        // = 30 - 2
        // = 28
        (result as NumberValue).value shouldBe 28.0
    }

    "should handle decimal numbers in arithmetic" {
        val engine = klangScript()
        val result = engine.execute("3.14 + 2.86")

        (result as NumberValue).value shouldBe 6.0
    }

    "should work with arithmetic in function arguments" {
        var receivedValue: Double? = null

        val engine = klangScript {
            registerFunctionRaw("check") { value ->
                receivedValue = (value.first() as NumberValue).value
                value.first()
            }
        }

        engine.execute("check(10 + 5)")

        receivedValue shouldBe 15.0
    }

    "should handle division in function arguments (like setCps)" {
        var receivedValue: Double? = null

        val engine = klangScript {
            registerFunctionRaw("setCps") { value ->
                receivedValue = (value.first() as NumberValue).value
                value.first()
            }
        }

        engine.execute("setCps(120 / 60)")

        receivedValue shouldBe 2.0
    }

    "should handle multiple arithmetic expressions in nested calls" {
        val results = mutableListOf<Double>()

        val engine = klangScript {
            registerFunctionRaw("record") { value ->
                results.add((value.first() as NumberValue).value)
                value.first()
            }
        }

        engine.execute(
            """
                record(1 + 1)
                record(2 * 3)
                record(10 / 2)
            """.trimIndent()
        )

        results shouldBe listOf(2.0, 6.0, 5.0)
    }
})
