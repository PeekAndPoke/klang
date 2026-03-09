package io.peekandpoke.klang.script

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.script.runtime.NullValue
import io.peekandpoke.klang.script.runtime.NumberValue
import io.peekandpoke.klang.script.runtime.StringValue

/**
 * Tests for control flow features:
 * - if/else as expression
 * - while loop
 * - do/while loop
 * - classic for loop
 * - break and continue statements
 * - variable assignment expressions
 */
class ControlFlowTest : StringSpec({

    // ============================================================
    // if/else as Expression
    // ============================================================

    "if-else: should evaluate then branch when condition is true" {
        val engine = klangScript()
        val result = engine.execute("if (true) { 42 } else { 0 }")
        (result as NumberValue).value shouldBe 42.0
    }

    "if-else: should evaluate else branch when condition is false" {
        val engine = klangScript()
        val result = engine.execute("if (false) { 42 } else { 99 }")
        (result as NumberValue).value shouldBe 99.0
    }

    "if-else: should return null when no else branch and condition is false" {
        val engine = klangScript()
        val result = engine.execute("if (false) { 42 }")
        result shouldBe NullValue
    }

    "if-else: should work as value in let declaration" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let x = 10
            let label = if (x > 5) { "big" } else { "small" }
            label
            """.trimIndent()
        )
        (result as StringValue).value shouldBe "big"
    }

    "if-else: should support else-if chain" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let x = 0
            if (x < 0) { "negative" } else if (x == 0) { "zero" } else { "positive" }
            """.trimIndent()
        )
        (result as StringValue).value shouldBe "zero"
    }

    "if-else: should support else-if chain (positive branch)" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let x = 5
            if (x < 0) { "negative" } else if (x == 0) { "zero" } else { "positive" }
            """.trimIndent()
        )
        (result as StringValue).value shouldBe "positive"
    }

    "if-else: should support else-if chain (negative branch)" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let x = -3
            if (x < 0) { "negative" } else if (x == 0) { "zero" } else { "positive" }
            """.trimIndent()
        )
        (result as StringValue).value shouldBe "negative"
    }

    "if-else: should work with side effects (native function calls)" {
        val results = mutableListOf<String>()
        val engine = klangScript {
            registerFunctionRaw("record") { args, _ ->
                results.add((args[0] as StringValue).value)
                NullValue
            }
        }
        engine.execute(
            """
            let x = true
            if (x) { record("yes") } else { record("no") }
            """.trimIndent()
        )
        results shouldBe listOf("yes")
    }

    "if-else: should evaluate condition expression" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let a = 3
            let b = 5
            if (a + b > 7) { "sum is large" } else { "sum is small" }
            """.trimIndent()
        )
        (result as StringValue).value shouldBe "sum is large"
    }

    "if-else: should support nested if expressions" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let x = 5
            let y = 10
            if (x > 0) {
                if (y > 0) { "both positive" } else { "only x" }
            } else {
                "x not positive"
            }
            """.trimIndent()
        )
        (result as StringValue).value shouldBe "both positive"
    }

    // ============================================================
    // Variable Assignment Expression
    // ============================================================

    "assignment: should assign value to existing variable" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let x = 0
            x = 42
            x
            """.trimIndent()
        )
        (result as NumberValue).value shouldBe 42.0
    }

    "assignment: should evaluate to the assigned value" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let x = 0
            let y = (x = 5)
            y
            """.trimIndent()
        )
        (result as NumberValue).value shouldBe 5.0
    }

    "assignment: should support compound assignment via expression" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let count = 0
            count = count + 1
            count = count + 1
            count
            """.trimIndent()
        )
        (result as NumberValue).value shouldBe 2.0
    }

    "assignment: should assign to object property" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let obj = { x: 1, y: 2 }
            obj.x = 99
            obj.x
            """.trimIndent()
        )
        (result as NumberValue).value shouldBe 99.0
    }

    // ============================================================
    // while loop
    // ============================================================

    "while: should loop while condition is true" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let i = 0
            let sum = 0
            while (i < 5) {
                sum = sum + i
                i = i + 1
            }
            sum
            """.trimIndent()
        )
        (result as NumberValue).value shouldBe 10.0
    }

    "while: should not execute body if condition is initially false" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let i = 10
            let sum = 0
            while (i < 5) {
                sum = sum + 1
            }
            sum
            """.trimIndent()
        )
        (result as NumberValue).value shouldBe 0.0
    }

    "while: should accumulate results" {
        val results = mutableListOf<Double>()
        val engine = klangScript {
            registerFunctionRaw("collect") { args, _ ->
                results.add((args[0] as NumberValue).value)
                NullValue
            }
        }
        engine.execute(
            """
            let i = 1
            while (i <= 3) {
                collect(i)
                i = i + 1
            }
            """.trimIndent()
        )
        results shouldBe listOf(1.0, 2.0, 3.0)
    }

    "while: break should exit loop early" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let i = 0
            while (i < 100) {
                if (i == 5) { break }
                i = i + 1
            }
            i
            """.trimIndent()
        )
        (result as NumberValue).value shouldBe 5.0
    }

    "while: continue should skip to next iteration" {
        val results = mutableListOf<Double>()
        val engine = klangScript {
            registerFunctionRaw("collect") { args, _ ->
                results.add((args[0] as NumberValue).value)
                NullValue
            }
        }
        engine.execute(
            """
            let i = 0
            while (i < 6) {
                i = i + 1
                if (i == 3) { continue }
                collect(i)
            }
            """.trimIndent()
        )
        // Should collect 1,2,4,5,6 (skipping 3)
        results shouldBe listOf(1.0, 2.0, 4.0, 5.0, 6.0)
    }

    "while: return inside loop should exit function" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let findFirst = (target) => {
                let i = 0
                while (i < 10) {
                    if (i == target) { return i }
                    i = i + 1
                }
                return -1
            }
            findFirst(3)
            """.trimIndent()
        )
        (result as NumberValue).value shouldBe 3.0
    }

    // ============================================================
    // do/while loop
    // ============================================================

    "do-while: should execute body at least once" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let i = 0
            let count = 0
            do {
                count = count + 1
                i = i + 1
            } while (i < 0)
            count
            """.trimIndent()
        )
        (result as NumberValue).value shouldBe 1.0
    }

    "do-while: should loop while condition holds" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let i = 0
            let sum = 0
            do {
                sum = sum + i
                i = i + 1
            } while (i < 5)
            sum
            """.trimIndent()
        )
        (result as NumberValue).value shouldBe 10.0
    }

    "do-while: break should exit loop" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let i = 0
            do {
                i = i + 1
                if (i == 3) { break }
            } while (i < 100)
            i
            """.trimIndent()
        )
        (result as NumberValue).value shouldBe 3.0
    }

    "do-while: continue should skip rest of body" {
        val results = mutableListOf<Double>()
        val engine = klangScript {
            registerFunctionRaw("collect") { args, _ ->
                results.add((args[0] as NumberValue).value)
                NullValue
            }
        }
        engine.execute(
            """
            let i = 0
            do {
                i = i + 1
                if (i == 3) { continue }
                collect(i)
            } while (i < 5)
            """.trimIndent()
        )
        // Should collect 1,2,4,5 (skipping 3)
        results shouldBe listOf(1.0, 2.0, 4.0, 5.0)
    }

    // ============================================================
    // for loop
    // ============================================================

    "for: basic for loop" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let sum = 0
            for (let i = 0; i < 5; i = i + 1) {
                sum = sum + i
            }
            sum
            """.trimIndent()
        )
        (result as NumberValue).value shouldBe 10.0
    }

    "for: for loop with postfix ++" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let sum = 0
            for (let i = 0; i < 5; i++) {
                sum = sum + i
            }
            sum
            """.trimIndent()
        )
        (result as NumberValue).value shouldBe 10.0
    }

    "for: for loop collects values" {
        val results = mutableListOf<Double>()
        val engine = klangScript {
            registerFunctionRaw("collect") { args, _ ->
                results.add((args[0] as NumberValue).value)
                NullValue
            }
        }
        engine.execute(
            """
            for (let i = 0; i < 4; i = i + 1) {
                collect(i)
            }
            """.trimIndent()
        )
        results shouldBe listOf(0.0, 1.0, 2.0, 3.0)
    }

    "for: loop variable is scoped to the loop" {
        val engine = klangScript()
        // 'i' should not be visible after the loop
        var threw = false
        try {
            engine.execute(
                """
                for (let i = 0; i < 3; i = i + 1) { }
                i
                """.trimIndent()
            )
        } catch (e: Exception) {
            threw = true
        }
        threw shouldBe true
    }

    "for: break exits for loop" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let result = 0
            for (let i = 0; i < 100; i = i + 1) {
                if (i == 5) { break }
                result = i
            }
            result
            """.trimIndent()
        )
        (result as NumberValue).value shouldBe 4.0
    }

    "for: continue skips to next iteration" {
        val results = mutableListOf<Double>()
        val engine = klangScript {
            registerFunctionRaw("collect") { args, _ ->
                results.add((args[0] as NumberValue).value)
                NullValue
            }
        }
        engine.execute(
            """
            for (let i = 0; i < 6; i = i + 1) {
                if (i == 3) { continue }
                collect(i)
            }
            """.trimIndent()
        )
        // Should collect 0,1,2,4,5 (skipping 3)
        results shouldBe listOf(0.0, 1.0, 2.0, 4.0, 5.0)
    }

    "for: omitted init and update" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let i = 0
            let sum = 0
            for (; i < 5; ) {
                sum = sum + i
                i = i + 1
            }
            sum
            """.trimIndent()
        )
        (result as NumberValue).value shouldBe 10.0
    }

    "for: return inside for loop exits function" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let findFirst = (arr, target) => {
                for (let i = 0; i < 5; i = i + 1) {
                    if (i == target) { return i }
                }
                return -1
            }
            findFirst(null, 3)
            """.trimIndent()
        )
        (result as NumberValue).value shouldBe 3.0
    }

    // ============================================================
    // Nested loops
    // ============================================================

    "nested: nested while loops" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let sum = 0
            let i = 0
            while (i < 3) {
                let j = 0
                while (j < 3) {
                    sum = sum + 1
                    j = j + 1
                }
                i = i + 1
            }
            sum
            """.trimIndent()
        )
        (result as NumberValue).value shouldBe 9.0
    }

    "nested: break only exits inner loop" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let count = 0
            let i = 0
            while (i < 3) {
                let j = 0
                while (j < 10) {
                    if (j == 2) { break }
                    count = count + 1
                    j = j + 1
                }
                i = i + 1
            }
            count
            """.trimIndent()
        )
        // Each outer iteration: j runs 0,1 (2 iterations), then breaks
        (result as NumberValue).value shouldBe 6.0
    }

    // ============================================================
    // Postfix ++ and --
    // ============================================================

    "postfix++: i++ increments variable" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let i = 0
            i++
            i
            """.trimIndent()
        )
        (result as NumberValue).value shouldBe 1.0
    }

    "postfix--: i-- decrements variable" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let i = 5
            i--
            i
            """.trimIndent()
        )
        (result as NumberValue).value shouldBe 4.0
    }

    // ============================================================
    // Per-iteration block scoping
    // ============================================================

    "while: let inside body does not leak into outer scope" {
        val engine = klangScript()
        engine.execute(
            """
            let i = 0
            while (i < 3) {
                let inner = i * 10
                i = i + 1
            }
            """.trimIndent()
        )
        // 'inner' must not be visible here — accessing it must throw
        var threw = false
        try {
            engine.execute("inner")
        } catch (e: Exception) {
            threw = true
        }
        threw shouldBe true
    }

    "while: let inside body re-bound each iteration" {
        val collected = mutableListOf<Double>()
        val engine = klangScript {
            registerFunctionRaw("collect") { args, _ ->
                collected.add((args[0] as io.peekandpoke.klang.script.runtime.NumberValue).value)
                io.peekandpoke.klang.script.runtime.NullValue
            }
        }
        engine.execute(
            """
            let i = 0
            while (i < 3) {
                let snap = i
                collect(snap)
                i = i + 1
            }
            """.trimIndent()
        )
        collected shouldBe listOf(0.0, 1.0, 2.0)
    }

    "for: let inside body does not leak into outer scope" {
        val engine = klangScript()
        engine.execute(
            """
            for (let i = 0; i < 3; i++) {
                let inner = i * 10
            }
            """.trimIndent()
        )
        var threw = false
        try {
            engine.execute("inner")
        } catch (e: Exception) {
            threw = true
        }
        threw shouldBe true
    }

    "do-while: let inside body does not leak into outer scope" {
        val engine = klangScript()
        engine.execute(
            """
            let i = 0
            do {
                let inner = i
                i = i + 1
            } while (i < 3)
            """.trimIndent()
        )
        var threw = false
        try {
            engine.execute("inner")
        } catch (e: Exception) {
            threw = true
        }
        threw shouldBe true
    }

    // ============================================================
    // If-expression block scoping
    // ============================================================

    "if: let in then-branch does not leak to outer scope" {
        val engine = klangScript()
        engine.execute(
            """
            let outer = 1
            if (true) {
                let inner = 99
            }
            outer
            """.trimIndent()
        )
        var threw = false
        try {
            engine.execute("inner")
        } catch (e: Exception) {
            threw = true
        }
        threw shouldBe true
    }

    "if: let in then-branch does not shadow outer variable" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let x = 1
            if (true) {
                let x = 2
            }
            x
            """.trimIndent()
        )
        (result as NumberValue).value shouldBe 1.0
    }

    "if: let in else-branch does not leak to outer scope" {
        val engine = klangScript()
        engine.execute(
            """
            if (false) {
                let a = 1
            } else {
                let inner = 99
            }
            """.trimIndent()
        )
        var threw = false
        try {
            engine.execute("inner")
        } catch (e: Exception) {
            threw = true
        }
        threw shouldBe true
    }

    "if: assignment to outer variable works across branch boundary" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let counter = 0
            if (true) {
                counter = counter + 1
            }
            counter
            """.trimIndent()
        )
        (result as NumberValue).value shouldBe 1.0
    }

    "if: branch returns value of last expression" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let result = if (true) { let tmp = 40; tmp + 2 } else { 0 }
            result
            """.trimIndent()
        )
        (result as NumberValue).value shouldBe 42.0
    }

    "if: nested ifs each have their own scope" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let x = 0
            if (true) {
                let x = 1
                if (true) {
                    let x = 2
                }
                x
            }
            x
            """.trimIndent()
        )
        (result as NumberValue).value shouldBe 0.0
    }

    "if: else-if chain has independent scopes" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let x = 0
            if (false) {
                let x = 1
            } else if (true) {
                let x = 2
            } else {
                let x = 3
            }
            x
            """.trimIndent()
        )
        (result as NumberValue).value shouldBe 0.0
    }

    // ============================================================
    // Const in scoped blocks
    // ============================================================

    "const: defined in if-branch not visible outside" {
        val engine = klangScript()
        engine.execute(
            """
            if (true) {
                const PI = 3
            }
            """.trimIndent()
        )
        var threw = false
        try {
            engine.execute("PI")
        } catch (e: Exception) {
            threw = true
        }
        threw shouldBe true
    }

    "const: defined in loop body not visible outside" {
        val engine = klangScript()
        engine.execute(
            """
            for (let i = 0; i < 1; i++) {
                const once = 42
            }
            """.trimIndent()
        )
        var threw = false
        try {
            engine.execute("once")
        } catch (e: Exception) {
            threw = true
        }
        threw shouldBe true
    }

    // ============================================================
    // Scope chain: reading outer variables from inner scope
    // ============================================================

    "scope chain: inner scope reads outer let" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let base = 10
            let result = if (true) { base + 5 } else { 0 }
            result
            """.trimIndent()
        )
        (result as NumberValue).value shouldBe 15.0
    }

    "scope chain: arrow function closes over outer let" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let factor = 3
            const triple = x => x * factor
            triple(7)
            """.trimIndent()
        )
        (result as NumberValue).value shouldBe 21.0
    }

    "scope chain: closure captures variable by reference" {
        val engine = klangScript()
        val result = engine.execute(
            """
            let count = 0
            const inc = () => { count = count + 1 }
            inc()
            inc()
            count
            """.trimIndent()
        )
        (result as NumberValue).value shouldBe 2.0
    }
})
