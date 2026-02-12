package io.peekandpoke.klang.script

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.script.runtime.BooleanValue

/**
 * Tests for JavaScript-like truthiness conversion in KlangScript
 *
 * Covers:
 * - Falsy values: null, false, 0, NaN, ""
 * - Truthy values: non-zero numbers, non-empty strings, objects, arrays, functions
 * - Truthiness with NOT operator
 * - Truthiness with AND/OR operators
 */
class TruthinessTest : StringSpec({

    // ============================================================
    // Falsy Values
    // ============================================================

    "null is falsy" {
        val engine = klangScript()
        val result = engine.execute("!null")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    "null in OR expression" {
        val engine = klangScript()
        val result = engine.execute("null || true")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    "null in AND expression" {
        val engine = klangScript()
        val result = engine.execute("null && true")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe false
    }

    "false is falsy" {
        val engine = klangScript()
        val result = engine.execute("!false")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    "0 is falsy" {
        val engine = klangScript()
        val result = engine.execute("!0")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    "0 in OR expression" {
        val engine = klangScript()
        val result = engine.execute("0 || true")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    "0 in AND expression" {
        val engine = klangScript()
        val result = engine.execute("0 && true")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe false
    }

    "empty string is falsy" {
        val engine = klangScript()
        val result = engine.execute("!\"\"")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    "empty string in OR expression" {
        val engine = klangScript()
        val result = engine.execute("\"\" || true")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    "empty string in AND expression" {
        val engine = klangScript()
        val result = engine.execute("\"\" && true")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe false
    }

    // Note: NaN testing would require special handling since 0/0 throws an error in KlangScript

    // ============================================================
    // Truthy Values - Numbers
    // ============================================================

    "positive number is truthy" {
        val engine = klangScript()
        val result = engine.execute("!5")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe false
    }

    "positive number in AND expression" {
        val engine = klangScript()
        val result = engine.execute("5 && true")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    "negative number is truthy" {
        val engine = klangScript()
        val result = engine.execute("!(-1)")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe false
    }

    "negative number in AND expression" {
        val engine = klangScript()
        val result = engine.execute("(-1) && true")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    "decimal is truthy" {
        val engine = klangScript()
        val result = engine.execute("!0.1")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe false
    }

    // ============================================================
    // Truthy Values - Strings
    // ============================================================

    "non-empty string is truthy" {
        val engine = klangScript()
        val result = engine.execute("!\"hello\"")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe false
    }

    "non-empty string in AND expression" {
        val engine = klangScript()
        val result = engine.execute("\"hello\" && true")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    "single character string is truthy" {
        val engine = klangScript()
        val result = engine.execute("!\"x\"")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe false
    }

    // ============================================================
    // Truthy Values - Objects and Arrays
    // ============================================================

    "empty object is truthy" {
        val engine = klangScript()
        val result = engine.execute("!{}")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe false
    }

    "object in AND expression" {
        val engine = klangScript()
        val result = engine.execute("{} && true")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    "non-empty object is truthy" {
        val engine = klangScript()
        val result = engine.execute("!{ x: 10 }")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe false
    }

    "empty array is truthy" {
        val engine = klangScript()
        val result = engine.execute("![]")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe false
    }

    "array in AND expression" {
        val engine = klangScript()
        val result = engine.execute("[] && true")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    "non-empty array is truthy" {
        val engine = klangScript()
        val result = engine.execute("![1, 2, 3]")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe false
    }

    // ============================================================
    // Truthy Values - Functions
    // ============================================================

    "function is truthy" {
        val engine = klangScript()
        val result = engine.execute("let fn = () => 1\n!fn")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe false
    }

    "function in AND expression" {
        val engine = klangScript()
        val result = engine.execute("let fn = () => 1\nfn && true")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    "arrow function is truthy" {
        val engine = klangScript()
        val result = engine.execute("!(x => x + 1)")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe false
    }
})
