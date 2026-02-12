package io.peekandpoke.klang.script

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.script.runtime.BooleanValue

/**
 * Tests for operator precedence with boolean logic operators
 *
 * Precedence hierarchy (lowest to highest):
 * 1. Arrow functions (x => ...)
 * 2. OR (||)
 * 3. AND (&&)
 * 4. Comparison (==, !=, <, >, <=, >=)
 * 5. Addition/Subtraction (+, -)
 * 6. Multiplication/Division (*, /)
 * 7. Unary (!, -, +)
 * 8. Call/Member (func(), obj.prop)
 *
 * This means:
 * - a || b && c parses as a || (b && c)
 * - a && b == c parses as a && (b == c)
 * - a + b > c && d parses as ((a + b) > c) && d
 */
class BooleanPrecedenceTest : StringSpec({

    // ============================================================
    // AND vs OR Precedence
    // ============================================================

    "AND has higher precedence than OR: false || true && false" {
        val engine = klangScript()
        // Should parse as: false || (true && false)
        // = false || false
        // = false
        val result = engine.execute("false || true && false")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe false
    }

    "AND has higher precedence than OR: true || false && false" {
        val engine = klangScript()
        // Should parse as: true || (false && false)
        // = true || false
        // = true
        val result = engine.execute("true || false && false")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    "AND has higher precedence than OR: false && true || true" {
        val engine = klangScript()
        // Should parse as: (false && true) || true
        // = false || true
        // = true
        val result = engine.execute("false && true || true")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    "AND has higher precedence than OR: true && false || false" {
        val engine = klangScript()
        // Should parse as: (true && false) || false
        // = false || false
        // = false
        val result = engine.execute("true && false || false")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe false
    }

    "multiple AND/OR: a || b && c || d" {
        val engine = klangScript()
        // Should parse as: a || (b && c) || d
        // false || (false && true) || true
        // = false || false || true
        // = true
        val result = engine.execute("false || false && true || true")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    // ============================================================
    // Comparison vs AND/OR Precedence
    // ============================================================

    "comparison has higher precedence than AND: 5 > 3 && 2 < 4" {
        val engine = klangScript()
        // Should parse as: (5 > 3) && (2 < 4)
        // = true && true
        // = true
        val result = engine.execute("5 > 3 && 2 < 4")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    "comparison has higher precedence than AND: 5 > 3 && 4 < 2" {
        val engine = klangScript()
        // Should parse as: (5 > 3) && (4 < 2)
        // = true && false
        // = false
        val result = engine.execute("5 > 3 && 4 < 2")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe false
    }

    "comparison has higher precedence than OR: 5 > 10 || 2 < 4" {
        val engine = klangScript()
        // Should parse as: (5 > 10) || (2 < 4)
        // = false || true
        // = true
        val result = engine.execute("5 > 10 || 2 < 4")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    "equality has higher precedence than AND: 5 == 5 && 3 == 3" {
        val engine = klangScript()
        // Should parse as: (5 == 5) && (3 == 3)
        // = true && true
        // = true
        val result = engine.execute("5 == 5 && 3 == 3")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    "inequality has higher precedence than OR: 5 != 3 || 2 != 2" {
        val engine = klangScript()
        // Should parse as: (5 != 3) || (2 != 2)
        // = true || false
        // = true
        val result = engine.execute("5 != 3 || 2 != 2")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    // ============================================================
    // Arithmetic vs Comparison vs AND/OR
    // ============================================================

    "arithmetic and comparison with AND: 1 + 2 > 2 && 5 - 1 < 5" {
        val engine = klangScript()
        // Should parse as: ((1 + 2) > 2) && ((5 - 1) < 5)
        // = (3 > 2) && (4 < 5)
        // = true && true
        // = true
        val result = engine.execute("1 + 2 > 2 && 5 - 1 < 5")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    "complex precedence: 1 + 2 > 2 && 5 - 1 < 5 || false" {
        val engine = klangScript()
        // Should parse as: (((1 + 2) > 2) && ((5 - 1) < 5)) || false
        // = ((3 > 2) && (4 < 5)) || false
        // = (true && true) || false
        // = true || false
        // = true
        val result = engine.execute("1 + 2 > 2 && 5 - 1 < 5 || false")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    "multiplication with comparison and AND: 3 * 2 == 6 && 10 / 2 == 5" {
        val engine = klangScript()
        // Should parse as: ((3 * 2) == 6) && ((10 / 2) == 5)
        // = (6 == 6) && (5 == 5)
        // = true && true
        // = true
        val result = engine.execute("3 * 2 == 6 && 10 / 2 == 5")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    // ============================================================
    // NOT Operator Precedence
    // ============================================================

    "NOT has higher precedence than AND: !false && true" {
        val engine = klangScript()
        // Should parse as: (!false) && true
        // = true && true
        // = true
        val result = engine.execute("!false && true")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    "NOT has higher precedence than OR: !false || false" {
        val engine = klangScript()
        // Should parse as: (!false) || false
        // = true || false
        // = true
        val result = engine.execute("!false || false")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    "NOT has higher precedence than AND: !true && false" {
        val engine = klangScript()
        // Should parse as: (!true) && false
        // = false && false
        // = false
        val result = engine.execute("!true && false")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe false
    }

    "multiple NOTs with AND: !false && !false" {
        val engine = klangScript()
        // Should parse as: (!false) && (!false)
        // = true && true
        // = true
        val result = engine.execute("!false && !false")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    // ============================================================
    // Parentheses Override Precedence
    // ============================================================

    "parentheses override precedence: (false || true) && false" {
        val engine = klangScript()
        // Explicit grouping: (false || true) && false
        // = true && false
        // = false
        val result = engine.execute("(false || true) && false")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe false
    }

    "parentheses override precedence: false || (true && false)" {
        val engine = klangScript()
        // Explicit grouping: false || (true && false)
        // = false || false
        // = false
        val result = engine.execute("false || (true && false)")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe false
    }

    "parentheses with comparison: (5 > 3) && (2 < 4)" {
        val engine = klangScript()
        // Explicit grouping (same as natural precedence)
        // = true && true
        // = true
        val result = engine.execute("(5 > 3) && (2 < 4)")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }
})
