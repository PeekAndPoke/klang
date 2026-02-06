package io.peekandpoke.klang.script

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.script.runtime.BooleanValue
import io.peekandpoke.klang.script.runtime.NullValue
import io.peekandpoke.klang.script.runtime.NumberValue

/**
 * Tests for arrow functions with block bodies
 *
 * Block bodies allow arrow functions to have multiple statements and explicit return.
 */
class ArrowFunctionBlockBodyTest : StringSpec({

    // ============================================================
    // Basic Block Body Tests
    // ============================================================

    "should execute arrow function with simple block body and return" {
        val script = klangScript()

        val result = script.execute("((x) => { return x + 1 })(5)")
        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe 6.0
    }

    "should execute arrow function with multiple statements" {
        val script = klangScript()

        val result = script.execute(
            """
            ((x) => {
                let doubled = x * 2
                return doubled + 1
            })(5)
        """.trimIndent()
        )
        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe 11.0
    }

    "should return null if no return statement in block body" {
        val script = klangScript()

        val result = script.execute(
            """
            ((x) => {
                let temp = x + 1
            })(5)
        """.trimIndent()
        )
        result.shouldBeInstanceOf<NullValue>()
    }

    "should support early return" {
        val script = klangScript()

        val result = script.execute(
            """
            ((x) => {
                let doubled = x * 2
                return doubled
                let unreachable = x + 100
            })(5)
        """.trimIndent()
        )
        result.shouldBeInstanceOf<NumberValue>()
        result.value shouldBe 10.0
    }

    // ============================================================
    // Return Statement Tests
    // ============================================================

    "should return value from return statement" {
        val script = klangScript()

        script.execute(
            """
            ((x) => {
                return x * 2
            })(21)
        """.trimIndent()
        ).let {
            it.shouldBeInstanceOf<NumberValue>()
            it.value shouldBe 42.0
        }
    }

    "should return null for return without value" {
        val script = klangScript()

        val result = script.execute(
            """
            (() => {
                return
            })()
        """.trimIndent()
        )
        result.shouldBeInstanceOf<NullValue>()
    }

    "should return complex expressions" {
        val script = klangScript()

        script.execute(
            """
            ((a, b) => {
                return a * b + a - b
            })(5, 3)
        """.trimIndent()
        ).let {
            it.shouldBeInstanceOf<NumberValue>()
            it.value shouldBe 17.0 // 5*3 + 5 - 3 = 15 + 5 - 3 = 17
        }
    }

    "should return specific value from block body and ignore unreachable code" {
        val script = klangScript()

        // Test that return actually exits the function with the correct value
        script.execute(
            """
            ((x) => {
                let result = x * 10
                return result
                let unreachable = 999
                return unreachable
            })(7)
        """.trimIndent()
        ).let {
            it.shouldBeInstanceOf<NumberValue>()
            it.value shouldBe 70.0 // Must be 70, not 999 - proves return exits immediately
        }
    }

    "should return different values based on computation in block" {
        val script = klangScript()

        // Verify the actual computed value is returned
        script.execute(
            """
            ((a, b, c) => {
                let sum = a + b
                let product = sum * c
                return product
            })(3, 4, 5)
        """.trimIndent()
        ).let {
            it.shouldBeInstanceOf<NumberValue>()
            it.value shouldBe 35.0 // (3 + 4) * 5 = 35
        }
    }

    // ============================================================
    // Variables and Scope
    // ============================================================

    "should support let declarations in block body" {
        val script = klangScript()

        script.execute(
            """
            ((x) => {
                let result = x * 2
                let final = result + 1
                return final
            })(10)
        """.trimIndent()
        ).let {
            it.shouldBeInstanceOf<NumberValue>()
            it.value shouldBe 21.0
        }
    }

    "should support const declarations in block body" {
        val script = klangScript()

        script.execute(
            """
            ((x) => {
                const multiplier = 2
                return x * multiplier
            })(15)
        """.trimIndent()
        ).let {
            it.shouldBeInstanceOf<NumberValue>()
            it.value shouldBe 30.0
        }
    }

    // ============================================================
    // Closures with Block Bodies
    // ============================================================

    "should capture closure variables in block body" {
        val script = klangScript()

        script.execute(
            """
            let offset = 10
            let addOffset = (x) => {
                let doubled = x * 2
                return doubled + offset
            }
            addOffset(5)
        """.trimIndent()
        ).let {
            it.shouldBeInstanceOf<NumberValue>()
            it.value shouldBe 20.0 // 5*2 + 10 = 20
        }
    }

    // ============================================================
    // Comparison Operators in Block Bodies
    // ============================================================

    "should use comparison operators in block body" {
        val script = klangScript()

        script.execute(
            """
            ((x) => {
                let isPositive = x > 0
                return isPositive
            })(5)
        """.trimIndent()
        ).let {
            it.shouldBeInstanceOf<BooleanValue>()
            it.value shouldBe true
        }
    }

    "should use equality in block body with member access" {
        val script = klangScript()

        script.execute(
            """
            ((obj) => {
                let note = obj.data.note
                return note == "a"
            })({ data: { note: "a" } })
        """.trimIndent()
        ).let {
            it.shouldBeInstanceOf<BooleanValue>()
            it.value shouldBe true
        }
    }

    // ============================================================
    // Original User Example
    // ============================================================

    "should parse the original user example: note filter with block body" {
        val script = klangScript()

        // Just test that it parses successfully - we'll test execution separately
        val code = """
            ((x) => {
                let note = x.data.note
                return note == "a"
            })({ data: { note: "a" } })
        """.trimIndent()

        val result = script.execute(code)

        // Verify it executed without parsing/runtime errors and returned a boolean
        result.shouldBeInstanceOf<BooleanValue>()
        result.value shouldBe true
    }

    // ============================================================
    // Mixed Expression and Block Bodies
    // ============================================================

    "should support both expression and block bodies in same code" {
        val script = klangScript()

        script.execute(
            """
            let simpleAdd = (x) => x + 1
            let complexAdd = (x) => {
                let result = x + 1
                return result
            }
            simpleAdd(5) + complexAdd(5)
        """.trimIndent()
        ).let {
            it.shouldBeInstanceOf<NumberValue>()
            it.value shouldBe 12.0 // 6 + 6
        }
    }
})
