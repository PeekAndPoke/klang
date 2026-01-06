package io.peekandpoke.klang.script

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.script.runtime.BooleanValue
import io.peekandpoke.klang.script.runtime.NumberValue
import io.peekandpoke.klang.script.runtime.ObjectValue

/**
 * Tests for Step 1.5: Parser - Arithmetic Expressions (Unary Operators)
 *
 * This test suite verifies:
 * - Negation operator (-)
 * - Plus operator (+)
 * - Logical NOT operator (!)
 * - Operator precedence with unary and binary operators
 */
class UnaryOperatorsTest : StringSpec({

    // ============================================================
    // Negation Operator (-)
    // ============================================================

    "should negate positive number" {
        val script = klangScript()

        val result = script.execute("-5")

        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe -5.0
    }

    "should negate negative number" {
        val script = klangScript()

        val result = script.execute("-(-3)")

        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe 3.0
    }

    "should negate expression" {
        val script = klangScript()

        val result = script.execute("-(2 + 3)")

        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe -5.0
    }

    "should handle negation in binary expression" {
        val script = klangScript()

        val result = script.execute("-5 + 3")

        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe -2.0
    }

    "should handle double negation 2" {
        val script = klangScript()

        val result = script.execute("--10")

        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe 10.0
    }

    "should negate variable" {
        val script = klangScript()

        script.execute("let x = 7")
        val result = script.execute("-x")

        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe -7.0
    }

    // ============================================================
    // Plus Operator (+)
    // ============================================================

    "should apply unary plus to number" {
        val script = klangScript()

        val result = script.execute("+42")

        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe 42.0
    }

    "should handle unary plus in expression" {
        val script = klangScript()

        val result = script.execute("+5 + +3")

        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe 8.0
    }

    // ============================================================
    // Logical NOT Operator (!)
    // ============================================================

    "should negate true" {
        val script = klangScript()

        val result = script.execute("!true")

        result.shouldBeInstanceOf<BooleanValue>()
        result.value shouldBe false
    }

    "should negate false" {
        val script = klangScript()

        val result = script.execute("!false")

        result.shouldBeInstanceOf<BooleanValue>()
        result.value shouldBe true
    }

    "should negate null as falsy" {
        val script = klangScript()

        val result = script.execute("!null")

        result.shouldBeInstanceOf<BooleanValue>()
        result.value shouldBe true
    }

    "should negate zero as falsy" {
        val script = klangScript()

        val result = script.execute("!0")

        result.shouldBeInstanceOf<BooleanValue>()
        result.value shouldBe true
    }

    "should negate non-zero number as truthy" {
        val script = klangScript()

        val result = script.execute("!5")

        result.shouldBeInstanceOf<BooleanValue>()
        result.value shouldBe false
    }

    "should negate empty string as falsy" {
        val script = klangScript()

        val result = script.execute("""!""" + """""""")

        result.shouldBeInstanceOf<BooleanValue>()
        result.value shouldBe true
    }

    "should negate non-empty string as truthy" {
        val script = klangScript()

        val result = script.execute("""!"hello"""")

        result.shouldBeInstanceOf<BooleanValue>()
        result.value shouldBe false
    }

    "should handle double negation" {
        val script = klangScript()

        val result = script.execute("!!true")

        result.shouldBeInstanceOf<BooleanValue>()
        result.value shouldBe true
    }

    // ============================================================
    // Mixed Operators
    // ============================================================

    "should handle negation with multiplication" {
        val script = klangScript()

        val result = script.execute("-2 * 3")

        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe -6.0
    }

    "should handle negation with division" {
        val script = klangScript()

        val result = script.execute("-10 / 2")

        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe -5.0
    }

    "should handle complex expression with unary operators" {
        val script = klangScript()

        val result = script.execute("-(3 + 2) * 2 + 10")

        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe 0.0
    }

    "should use NOT in object literal" {
        val script = klangScript()

        val result = script.execute("{ negated: !true, identity: !false }")

        result.shouldBeInstanceOf<ObjectValue>()

        (result.getProperty("negated") as BooleanValue).value shouldBe false
        (result.getProperty("identity") as BooleanValue).value shouldBe true
    }
})
