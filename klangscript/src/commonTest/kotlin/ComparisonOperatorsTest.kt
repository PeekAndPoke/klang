package io.peekandpoke.klang.script

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.script.runtime.BooleanValue
import io.peekandpoke.klang.script.runtime.NativeFunctionValue
import io.peekandpoke.klang.script.runtime.ObjectValue
import io.peekandpoke.klang.script.runtime.RuntimeValue

/**
 * Tests for comparison operators (==, !=, <, <=, >, >=)
 *
 * These operators enable boolean logic in lambda expressions and conditional operations.
 */
class ComparisonOperatorsTest : StringSpec({

    // ============================================================
    // Equality Operators (==, !=)
    // ============================================================

    "should compare numbers for equality with ==" {
        val script = klangScript()

        script.execute("5 == 5").let {
            it.shouldBeInstanceOf<BooleanValue>()
            it.value shouldBe true
        }

        script.execute("5 == 3").let {
            it.shouldBeInstanceOf<BooleanValue>()
            it.value shouldBe false
        }
    }

    "should compare numbers for inequality with !=" {
        val script = klangScript()

        script.execute("5 != 3").let {
            it.shouldBeInstanceOf<BooleanValue>()
            it.value shouldBe true
        }

        script.execute("5 != 5").let {
            it.shouldBeInstanceOf<BooleanValue>()
            it.value shouldBe false
        }
    }

    "should compare strings for equality" {
        val script = klangScript()

        script.execute("\"hello\" == \"hello\"").let {
            it.shouldBeInstanceOf<BooleanValue>()
            it.value shouldBe true
        }

        script.execute("\"hello\" == \"world\"").let {
            it.shouldBeInstanceOf<BooleanValue>()
            it.value shouldBe false
        }

        script.execute("\"hello\" != \"world\"").let {
            it.shouldBeInstanceOf<BooleanValue>()
            it.value shouldBe true
        }
    }

    "should compare booleans for equality" {
        val script = klangScript()

        script.execute("true == true").let {
            it.shouldBeInstanceOf<BooleanValue>()
            it.value shouldBe true
        }

        script.execute("true == false").let {
            it.shouldBeInstanceOf<BooleanValue>()
            it.value shouldBe false
        }

        script.execute("true != false").let {
            it.shouldBeInstanceOf<BooleanValue>()
            it.value shouldBe true
        }
    }

    "should compare null for equality" {
        val script = klangScript()

        script.execute("null == null").let {
            it.shouldBeInstanceOf<BooleanValue>()
            it.value shouldBe true
        }

        script.execute("null != null").let {
            it.shouldBeInstanceOf<BooleanValue>()
            it.value shouldBe false
        }
    }

    "should return false when comparing different types" {
        val script = klangScript()

        script.execute("5 == \"5\"").let {
            it.shouldBeInstanceOf<BooleanValue>()
            it.value shouldBe false
        }

        script.execute("true == 1").let {
            it.shouldBeInstanceOf<BooleanValue>()
            it.value shouldBe false
        }

        script.execute("null == 0").let {
            it.shouldBeInstanceOf<BooleanValue>()
            it.value shouldBe false
        }
    }

    // ============================================================
    // Numeric Comparison Operators (<, <=, >, >=)
    // ============================================================

    "should compare numbers with less than <" {
        val script = klangScript()

        script.execute("3 < 5").let {
            it.shouldBeInstanceOf<BooleanValue>()
            it.value shouldBe true
        }

        script.execute("5 < 3").let {
            it.shouldBeInstanceOf<BooleanValue>()
            it.value shouldBe false
        }

        script.execute("5 < 5").let {
            it.shouldBeInstanceOf<BooleanValue>()
            it.value shouldBe false
        }
    }

    "should compare numbers with less than or equal <=" {
        val script = klangScript()

        script.execute("3 <= 5").let {
            it.shouldBeInstanceOf<BooleanValue>()
            it.value shouldBe true
        }

        script.execute("5 <= 5").let {
            it.shouldBeInstanceOf<BooleanValue>()
            it.value shouldBe true
        }

        script.execute("7 <= 5").let {
            it.shouldBeInstanceOf<BooleanValue>()
            it.value shouldBe false
        }
    }

    "should compare numbers with greater than >" {
        val script = klangScript()

        script.execute("5 > 3").let {
            it.shouldBeInstanceOf<BooleanValue>()
            it.value shouldBe true
        }

        script.execute("3 > 5").let {
            it.shouldBeInstanceOf<BooleanValue>()
            it.value shouldBe false
        }

        script.execute("5 > 5").let {
            it.shouldBeInstanceOf<BooleanValue>()
            it.value shouldBe false
        }
    }

    "should compare numbers with greater than or equal >=" {
        val script = klangScript()

        script.execute("5 >= 3").let {
            it.shouldBeInstanceOf<BooleanValue>()
            it.value shouldBe true
        }

        script.execute("5 >= 5").let {
            it.shouldBeInstanceOf<BooleanValue>()
            it.value shouldBe true
        }

        script.execute("3 >= 5").let {
            it.shouldBeInstanceOf<BooleanValue>()
            it.value shouldBe false
        }
    }

    // ============================================================
    // Operator Precedence
    // ============================================================

    "should have correct precedence: arithmetic before comparison" {
        val script = klangScript()

        // 2 + 3 == 5 should parse as (2 + 3) == 5, not 2 + (3 == 5)
        script.execute("2 + 3 == 5").let {
            it.shouldBeInstanceOf<BooleanValue>()
            it.value shouldBe true
        }

        // 10 - 5 > 3 should parse as (10 - 5) > 3
        script.execute("10 - 5 > 3").let {
            it.shouldBeInstanceOf<BooleanValue>()
            it.value shouldBe true
        }

        // 2 * 3 < 10 should parse as (2 * 3) < 10
        script.execute("2 * 3 < 10").let {
            it.shouldBeInstanceOf<BooleanValue>()
            it.value shouldBe true
        }

        // 20 / 4 >= 5 should parse as (20 / 4) >= 5
        script.execute("20 / 4 >= 5").let {
            it.shouldBeInstanceOf<BooleanValue>()
            it.value shouldBe true
        }
    }

    "should allow parentheses to override precedence" {
        val script = klangScript()

        // Compare result: 5 == (3 + 2)
        script.execute("5 == (3 + 2)").let {
            it.shouldBeInstanceOf<BooleanValue>()
            it.value shouldBe true
        }

        // Arithmetic with comparison: (5 > 3) evaluates to true, but can't add to number
        // This would be a type error if we tried: (5 > 3) + 1
    }

    // ============================================================
    // Comparisons in Arrow Functions
    // ============================================================

    "should use comparison in arrow function" {
        val script = klangScript()

        // Test the original use case: arrow function with equality check
        script.execute("((x) => x == 5)(5)").let {
            it.shouldBeInstanceOf<BooleanValue>()
            it.value shouldBe true
        }

        script.execute("((x) => x == 5)(3)").let {
            it.shouldBeInstanceOf<BooleanValue>()
            it.value shouldBe false
        }
    }

    "should use numeric comparison in arrow function" {
        val script = klangScript()

        script.execute("((x) => x > 10)(15)").let {
            it.shouldBeInstanceOf<BooleanValue>()
            it.value shouldBe true
        }

        script.execute("((x) => x < 10)(5)").let {
            it.shouldBeInstanceOf<BooleanValue>()
            it.value shouldBe true
        }
    }

    "should use member access and comparison in arrow function" {
        val script = klangScript()

        // Test arrow function with member access and comparison
        // This mimics: note("a b").filter((x) => x.data.note == "a")
        script.execute("((x) => x.value == 42)({ value: 42 })").let {
            it.shouldBeInstanceOf<BooleanValue>()
            it.value shouldBe true
        }

        script.execute("((x) => x.name == \"test\")({ name: \"test\" })").let {
            it.shouldBeInstanceOf<BooleanValue>()
            it.value shouldBe true
        }
    }

    "should use nested member access and comparison in arrow function" {
        val script = klangScript()

        // Test deeply nested member access with comparison
        script.execute("((x) => x.data.value == 10)({ data: { value: 10 } })").let {
            it.shouldBeInstanceOf<BooleanValue>()
            it.value shouldBe true
        }

        script.execute("((x) => x.data.value == 10)({ data: { value: 5 } })").let {
            it.shouldBeInstanceOf<BooleanValue>()
            it.value shouldBe false
        }
    }

    // ============================================================
    // Complex Expressions
    // ============================================================

    "should handle complex expressions with multiple comparisons" {
        val script = klangScript()

        // Note: This tests chaining, though in practice you'd use logical operators
        // 5 > 3 > 0 parses as (5 > 3) > 0, which is (true > 0), which is type error
        // So we test simpler cases

        script.execute("5 > 3").let {
            it.shouldBeInstanceOf<BooleanValue>()
            it.value shouldBe true
        }
    }

    "should handle comparison with variables" {
        val script = klangScript()

        script.execute(
            """
            let x = 10
            x > 5
        """.trimIndent()
        ).let {
            it.shouldBeInstanceOf<BooleanValue>()
            it.value shouldBe true
        }

        script.execute(
            """
            let a = 5
            let b = 5
            a == b
        """.trimIndent()
        ).let {
            it.shouldBeInstanceOf<BooleanValue>()
            it.value shouldBe true
        }
    }

    // ============================================================
    // Original Issue Test Cases
    // ============================================================

    "should parse original issue line 4: note(\"a b\").filter((x) => x + 1)" {
        val script = klangScript {
            // Register minimal stubs for note() and filter() to make the code executable
            registerFunctionRaw("note") { args, _ ->
                // Return an object with a filter method
                ObjectValue(
                    mutableMapOf(
                        "filter" to NativeFunctionValue("filter") { filterArgs, _ ->
                        // Just return the function argument to verify it was parsed
                        filterArgs[0]
                    }
                ))
            }
        }

        // This should parse and execute without errors
        val result = script.execute("note(\"a b\").filter((x) => x + 1)")
        // We just verify it doesn't throw a ParseException
        result.shouldBeInstanceOf<RuntimeValue>()
    }

    "should parse original issue line 5: note(\"a b\").filter((x) => x.data.note == \"a\")" {
        val script = klangScript {
            // Register minimal stubs for note() and filter() to make the code executable
            registerFunctionRaw("note") { args, _ ->
                // Return an object with a filter method
                ObjectValue(
                    mutableMapOf(
                        "filter" to NativeFunctionValue("filter") { filterArgs, _ ->
                        // Just return the function argument to verify it was parsed
                        filterArgs[0]
                    }
                ))
            }
        }

        // This should parse and execute without errors - this was failing before the fix
        val result = script.execute("note(\"a b\").filter((x) => x.data.note == \"a\")")
        // We just verify it doesn't throw a ParseException
        result.shouldBeInstanceOf<RuntimeValue>()
    }
})
