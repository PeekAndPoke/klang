package io.peekandpoke.klang.script

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.script.runtime.BooleanValue

/**
 * Tests for boolean logic operators (&&, ||) in KlangScript
 *
 * Covers:
 * - Basic AND (&&) operations
 * - Basic OR (||) operations
 * - Truth tables for both operators
 * - Chained boolean operations
 * - Boolean logic with variables
 */
class BooleanLogicTest : StringSpec({

    // ============================================================
    // AND Operator Tests
    // ============================================================

    "true && true should return true" {
        val engine = klangScript()
        val result = engine.execute("true && true")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    "true && false should return false" {
        val engine = klangScript()
        val result = engine.execute("true && false")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe false
    }

    "false && true should return false" {
        val engine = klangScript()
        val result = engine.execute("false && true")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe false
    }

    "false && false should return false" {
        val engine = klangScript()
        val result = engine.execute("false && false")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe false
    }

    // ============================================================
    // OR Operator Tests
    // ============================================================

    "true || true should return true" {
        val engine = klangScript()
        val result = engine.execute("true || true")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    "true || false should return true" {
        val engine = klangScript()
        val result = engine.execute("true || false")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    "false || true should return true" {
        val engine = klangScript()
        val result = engine.execute("false || true")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    "false || false should return false" {
        val engine = klangScript()
        val result = engine.execute("false || false")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe false
    }

    // ============================================================
    // Chained Operations
    // ============================================================

    "chained AND operations: true && true && true" {
        val engine = klangScript()
        val result = engine.execute("true && true && true")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    "chained AND operations: true && false && true" {
        val engine = klangScript()
        val result = engine.execute("true && false && true")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe false
    }

    "chained OR operations: false || false || true" {
        val engine = klangScript()
        val result = engine.execute("false || false || true")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }

    "chained OR operations: false || false || false" {
        val engine = klangScript()
        val result = engine.execute("false || false || false")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe false
    }

    // ============================================================
    // With Variables
    // ============================================================

    "boolean logic with variables: a && b" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let a = true
            let b = false
            a && b
        """.trimIndent()
        )
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe false
    }

    "boolean logic with variables: a || b" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let a = false
            let b = true
            a || b
        """.trimIndent()
        )
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
    }
})
