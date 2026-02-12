package io.peekandpoke.klang.script

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.script.runtime.BooleanValue
import io.peekandpoke.klang.script.runtime.NumberValue

/**
 * Tests for short-circuit evaluation in boolean logic operators
 *
 * Short-circuit evaluation is critical for:
 * - Performance (avoid unnecessary computations)
 * - Safety (avoid errors in conditional expressions)
 *
 * Covers:
 * - AND operator: if left is falsy, right is not evaluated
 * - OR operator: if left is truthy, right is not evaluated
 * - Verification that right side IS evaluated when needed
 */
class ShortCircuitTest : StringSpec({

    // ============================================================
    // AND Short-Circuit Tests
    // ============================================================

    "AND should not evaluate right if left is falsy" {
        var rightEvaluated = false

        val engine = klangScript {
            registerFunctionRaw("sideEffect") { _, _ ->
                rightEvaluated = true
                NumberValue(42.0)
            }
        }

        val result = engine.execute("false && sideEffect()")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe false
        rightEvaluated shouldBe false  // Right side never evaluated!
    }

    "AND should not evaluate right if left is 0" {
        var rightEvaluated = false

        val engine = klangScript {
            registerFunctionRaw("sideEffect") { _, _ ->
                rightEvaluated = true
                NumberValue(42.0)
            }
        }

        val result = engine.execute("0 && sideEffect()")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe false
        rightEvaluated shouldBe false
    }

    "AND should not evaluate right if left is null" {
        var rightEvaluated = false

        val engine = klangScript {
            registerFunctionRaw("sideEffect") { _, _ ->
                rightEvaluated = true
                NumberValue(42.0)
            }
        }

        val result = engine.execute("null && sideEffect()")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe false
        rightEvaluated shouldBe false
    }

    "AND should not evaluate right if left is empty string" {
        var rightEvaluated = false

        val engine = klangScript {
            registerFunctionRaw("sideEffect") { _, _ ->
                rightEvaluated = true
                NumberValue(42.0)
            }
        }

        val result = engine.execute("\"\" && sideEffect()")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe false
        rightEvaluated shouldBe false
    }

    "AND should evaluate right if left is truthy" {
        var rightEvaluated = false

        val engine = klangScript {
            registerFunctionRaw("sideEffect") { _, _ ->
                rightEvaluated = true
                NumberValue(42.0)
            }
        }

        val result = engine.execute("true && sideEffect()")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
        rightEvaluated shouldBe true  // Right side WAS evaluated
    }

    "AND should evaluate right if left is non-zero number" {
        var rightEvaluated = false

        val engine = klangScript {
            registerFunctionRaw("sideEffect") { _, _ ->
                rightEvaluated = true
                BooleanValue(false)
            }
        }

        val result = engine.execute("5 && sideEffect()")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe false
        rightEvaluated shouldBe true
    }

    // ============================================================
    // OR Short-Circuit Tests
    // ============================================================

    "OR should not evaluate right if left is truthy" {
        var rightEvaluated = false

        val engine = klangScript {
            registerFunctionRaw("sideEffect") { _, _ ->
                rightEvaluated = true
                NumberValue(42.0)
            }
        }

        val result = engine.execute("true || sideEffect()")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
        rightEvaluated shouldBe false  // Right side never evaluated!
    }

    "OR should not evaluate right if left is non-zero number" {
        var rightEvaluated = false

        val engine = klangScript {
            registerFunctionRaw("sideEffect") { _, _ ->
                rightEvaluated = true
                NumberValue(42.0)
            }
        }

        val result = engine.execute("5 || sideEffect()")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
        rightEvaluated shouldBe false
    }

    "OR should not evaluate right if left is non-empty string" {
        var rightEvaluated = false

        val engine = klangScript {
            registerFunctionRaw("sideEffect") { _, _ ->
                rightEvaluated = true
                NumberValue(42.0)
            }
        }

        val result = engine.execute("\"hello\" || sideEffect()")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
        rightEvaluated shouldBe false
    }

    "OR should evaluate right if left is falsy" {
        var rightEvaluated = false

        val engine = klangScript {
            registerFunctionRaw("sideEffect") { _, _ ->
                rightEvaluated = true
                NumberValue(42.0)
            }
        }

        val result = engine.execute("false || sideEffect()")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
        rightEvaluated shouldBe true  // Right side WAS evaluated
    }

    "OR should evaluate right if left is 0" {
        var rightEvaluated = false

        val engine = klangScript {
            registerFunctionRaw("sideEffect") { _, _ ->
                rightEvaluated = true
                BooleanValue(true)
            }
        }

        val result = engine.execute("0 || sideEffect()")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
        rightEvaluated shouldBe true
    }

    // ============================================================
    // Practical Use Cases
    // ============================================================

    "safe division using short-circuit" {
        val engine = klangScript()
        // b != 0 && a / b should only divide if b is not zero
        val result1 = engine.execute("let a = 10\nlet b = 0\nb != 0 && a / b")
        result1.shouldBeInstanceOf<BooleanValue>()
        (result1 as BooleanValue).value shouldBe false  // No division error!

        val result2 = engine.execute("let a = 10\nlet b = 2\nb != 0 && a / b")
        result2.shouldBeInstanceOf<BooleanValue>()
        (result2 as BooleanValue).value shouldBe true  // 5.0 is truthy
    }

    "default value using short-circuit" {
        var defaultCalled = false

        val engine = klangScript {
            registerFunctionRaw("getValue") { _, _ -> NumberValue(0.0) }
            registerFunctionRaw("getDefault") { _, _ ->
                defaultCalled = true
                NumberValue(42.0)
            }
        }

        val result = engine.execute("getValue() || getDefault()")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
        defaultCalled shouldBe true  // Default was called because getValue() returned 0
    }

    "chained short-circuit: false || false || computeExpensive()" {
        var expensiveCalled = false

        val engine = klangScript {
            registerFunctionRaw("computeExpensive") { _, _ ->
                expensiveCalled = true
                NumberValue(42.0)
            }
        }

        val result = engine.execute("false || false || computeExpensive()")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
        expensiveCalled shouldBe true  // Must evaluate because all previous are false
    }

    "chained short-circuit: false || true || computeExpensive()" {
        var expensiveCalled = false

        val engine = klangScript {
            registerFunctionRaw("computeExpensive") { _, _ ->
                expensiveCalled = true
                NumberValue(42.0)
            }
        }

        val result = engine.execute("false || true || computeExpensive()")
        result.shouldBeInstanceOf<BooleanValue>()
        (result as BooleanValue).value shouldBe true
        expensiveCalled shouldBe false  // Never called because true short-circuits
    }
})
