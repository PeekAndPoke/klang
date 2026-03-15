package io.peekandpoke.klang.script

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.script.runtime.BooleanValue
import io.peekandpoke.klang.script.runtime.NumberValue

/**
 * Tests for operator precedence with bitwise, shift, and nullish coalescing operators.
 */
class OperatorPrecedenceTest : StringSpec({

    "AND binds tighter than OR: 2 | 3 & 5 should be 2 | (3 & 5) = 2 | 1 = 3" {
        val engine = klangScript()
        val result = engine.execute("2 | 3 & 5")
        (result as NumberValue).value shouldBe 3.0
    }

    "AND binds tighter than XOR: 3 ^ 5 & 7 should be 3 ^ (5 & 7) = 3 ^ 5 = 6" {
        val engine = klangScript()
        val result = engine.execute("3 ^ 5 & 7")
        (result as NumberValue).value shouldBe 6.0
    }

    "XOR binds tighter than OR: 1 | 2 ^ 3 should be 1 | (2 ^ 3) = 1 | 1 = 1" {
        val engine = klangScript()
        val result = engine.execute("1 | 2 ^ 3")
        (result as NumberValue).value shouldBe 1.0
    }

    "addition binds tighter than shift: 1 + 2 << 3 should be (1 + 2) << 3 = 24" {
        val engine = klangScript()
        val result = engine.execute("1 + 2 << 3")
        (result as NumberValue).value shouldBe 24.0
    }

    "addition binds tighter than shift (reverse): 1 << 2 + 3 should be 1 << (2 + 3) = 32" {
        val engine = klangScript()
        val result = engine.execute("1 << 2 + 3")
        (result as NumberValue).value shouldBe 32.0
    }

    "bitwise OR binds tighter than logical AND: true && 1 | 2 should be true && (1 | 2) = true && 3 = true" {
        val engine = klangScript()
        val result = engine.execute("true && 1 | 2")
        (result as BooleanValue).value shouldBe true
    }

    "nullish coalescing with logical OR: null ?? 0 || 1 should be (null ?? 0) || 1 = 0 || 1 = true" {
        val engine = klangScript()
        val result = engine.execute("null ?? 0 || 1")
        // null ?? 0 = 0, then 0 || 1 = true (|| returns boolean in this lang)
        (result as BooleanValue).value shouldBe true
    }

    "unary ~ binds tighter than addition: ~5 + 1 should be (~5) + 1 = -6 + 1 = -5" {
        val engine = klangScript()
        val result = engine.execute("~5 + 1")
        (result as NumberValue).value shouldBe -5.0
    }

    "shift precedence vs comparison: 2 << 3 < 100 should be (2 << 3) < 100 = 8 < 100 = true" {
        val engine = klangScript()
        val result = engine.execute("2 << 3 < 100")
        (result as BooleanValue).value shouldBe true
    }
})
