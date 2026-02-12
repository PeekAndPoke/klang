package io.peekandpoke.klang.script

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.script.runtime.BooleanValue

/**
 * Tests for edge cases in boolean logic operators
 *
 * Covers:
 * - Double NOT (!!)
 * - Triple NOT (!!!)
 * - Chained boolean operations
 * - Boolean logic in arrow functions
 * - Boolean logic with variables
 * - Complex nested expressions
 * - NaN handling
 */
class BooleanEdgeCasesTest : StringSpec({

    // ============================================================
    // Double NOT (!!) Tests
    // ============================================================

    "double NOT converts true to true" {
        val engine = klangScript()
        val result = engine.execute("!!true")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    "double NOT converts false to false" {
        val engine = klangScript()
        val result = engine.execute("!!false")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe false
    }

    "double NOT converts null to false" {
        val engine = klangScript()
        val result = engine.execute("!!null")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe false
    }

    "double NOT converts 0 to false" {
        val engine = klangScript()
        val result = engine.execute("!!0")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe false
    }

    "double NOT converts non-zero number to true" {
        val engine = klangScript()
        val result = engine.execute("!!5")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    "double NOT converts negative number to true" {
        val engine = klangScript()
        val result = engine.execute("!!(-1)")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    "double NOT converts empty string to false" {
        val engine = klangScript()
        val result = engine.execute("!!\"\"")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe false
    }

    "double NOT converts non-empty string to true" {
        val engine = klangScript()
        val result = engine.execute("!!\"hello\"")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    "double NOT converts empty object to true" {
        val engine = klangScript()
        val result = engine.execute("!!{}")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    "double NOT converts empty array to true" {
        val engine = klangScript()
        val result = engine.execute("!![]")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    // ============================================================
    // Triple NOT Tests
    // ============================================================

    "triple NOT works correctly for true" {
        val engine = klangScript()
        val result = engine.execute("!!!true")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe false
    }

    "triple NOT works correctly for false" {
        val engine = klangScript()
        val result = engine.execute("!!!false")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    "triple NOT works correctly for truthy value" {
        val engine = klangScript()
        val result = engine.execute("!!!5")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe false
    }

    // ============================================================
    // Chained Boolean Logic
    // ============================================================

    "long AND chain: all true" {
        val engine = klangScript()
        val result = engine.execute("true && true && true && true")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    "long AND chain: one false in middle" {
        val engine = klangScript()
        val result = engine.execute("true && true && false && true")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe false
    }

    "long OR chain: all false except last" {
        val engine = klangScript()
        val result = engine.execute("false || false || false || true")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    "long OR chain: all false" {
        val engine = klangScript()
        val result = engine.execute("false || false || false || false")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe false
    }

    "mixed long chain: AND and OR" {
        val engine = klangScript()
        val result = engine.execute("true && false || true && true")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    // ============================================================
    // Boolean Logic in Arrow Functions
    // ============================================================

    "boolean logic in arrow function: range check" {
        val engine = klangScript()
        engine.execute("let inRange = x => x >= 5 && x < 10")

        val result1 = engine.execute("inRange(7)")
        result1.shouldBeInstanceOf<BooleanValue>()
        (result1 as BooleanValue).value shouldBe true

        val result2 = engine.execute("inRange(3)")
        result2.shouldBeInstanceOf<BooleanValue>()
        (result2 as BooleanValue).value shouldBe false

        val result3 = engine.execute("inRange(15)")
        result3.shouldBeInstanceOf<BooleanValue>()
        (result3 as BooleanValue).value shouldBe false
    }

    "boolean logic in arrow function: compound condition" {
        val engine = klangScript()
        engine.execute("let isValid = x => x > 0 && x < 100 || x == 0")

        val result1 = engine.execute("isValid(50)")
        result1.shouldBeInstanceOf<BooleanValue>()
        (result1 as BooleanValue).value shouldBe true

        val result2 = engine.execute("isValid(0)")
        result2.shouldBeInstanceOf<BooleanValue>()
        (result2 as BooleanValue).value shouldBe true

        val result3 = engine.execute("isValid(-5)")
        result3.shouldBeInstanceOf<BooleanValue>()
        (result3 as BooleanValue).value shouldBe false
    }

    "arrow function returning boolean logic" {
        val engine = klangScript()
        val result = engine.execute("let fn = (a, b) => a && b\nfn(true, false)")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe false
    }

    // ============================================================
    // Boolean Logic with Variables
    // ============================================================

    "boolean logic with multiple variables" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let a = true
            let b = false
            let c = true
            a && b || c
        """.trimIndent()
        )
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    "boolean logic with comparison of variables" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let x = 10
            let y = 5
            let z = 15
            x > y && x < z
        """.trimIndent()
        )
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    // ============================================================
    // NaN Handling
    // ============================================================

    // Note: NaN testing would require special handling since 0/0 throws an error in KlangScript
    // The truthiness logic for NaN is implemented in toBoolean() but cannot be easily tested
    // without a way to create NaN values

    // ============================================================
    // Complex Nested Expressions
    // ============================================================

    "nested boolean logic with parentheses" {
        val engine = klangScript()
        val result = engine.execute("(true && false) || (false || true)")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    "deeply nested boolean expressions" {
        val engine = klangScript()
        val result = engine.execute("((true && true) || false) && ((false || true) && true)")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    "complex expression with all operators" {
        val engine = klangScript()
        val result = engine.execute("!false && (5 > 3 || 2 == 1) && !(10 < 5)")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    // ============================================================
    // Mixed Type Coercion
    // ============================================================

    "mixing different truthy types in AND" {
        val engine = klangScript()
        val result = engine.execute("5 && \"hello\" && true")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    "mixing different falsy types in OR" {
        val engine = klangScript()
        val result = engine.execute("0 || \"\" || null || true")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    "object and array truthiness in boolean logic" {
        val engine = klangScript()
        val result = engine.execute("{} && [] && true")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }
})
