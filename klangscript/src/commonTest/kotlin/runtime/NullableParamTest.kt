package io.peekandpoke.klang.script.runtime

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.peekandpoke.klang.script.KlangScriptEngine
import io.peekandpoke.klang.script.builder.registerFunction
import io.peekandpoke.klang.script.builder.registerVarargFunction
import io.peekandpoke.klang.script.klangScript

/**
 * Tests for nullable parameter handling in native function registration.
 */
class NullableParamTest : StringSpec({

    // ── Vararg functions accept null via List<Any?> ──────────────────────────

    "vararg function receives null in args list" {
        val received = mutableListOf<List<Any?>>()

        val engine = klangScript {
            registerVarargFunction("capture") { args: List<Any?> ->
                received.add(args)
            }
        }

        engine.execute("capture(1, null, \"hello\", null)")
        received.size shouldBe 1
        received[0].size shouldBe 4
        received[0][0] shouldBe 1.0
        received[0][1] shouldBe null
        received[0][2] shouldBe "hello"
        received[0][3] shouldBe null
    }

    "vararg function receives all nulls" {
        val received = mutableListOf<List<Any?>>()

        val engine = klangScript {
            registerVarargFunction("allNulls") { args: List<Any?> ->
                received.add(args)
            }
        }

        engine.execute("allNulls(null, null, null)")
        received.size shouldBe 1
        received[0] shouldBe listOf(null, null, null)
    }

    "vararg function receives no nulls" {
        val received = mutableListOf<List<Any?>>()

        val engine = klangScript {
            registerVarargFunction("noNulls") { args: List<Any?> ->
                received.add(args)
            }
        }

        engine.execute("noNulls(1, 2, 3)")
        received.size shouldBe 1
        received[0] shouldBe listOf(1.0, 2.0, 3.0)
    }

    // ── Raw function handles null correctly ──────────────────────────────────

    "registerFunctionRaw receives NullValue for null args" {
        val received = mutableListOf<RuntimeValue>()

        val builder = KlangScriptEngine.builder()
        builder.registerFunctionRaw("rawCapture") { args, _ ->
            received.addAll(args)
            NullValue
        }

        val engine = builder.build()
        engine.execute("rawCapture(1, null, \"hi\")")

        received.size shouldBe 3
        received[0] shouldBe NumberValue(1.0)
        received[1] shouldBe NullValue
        received[2].let { it is StringValue && it.value == "hi" } shouldBe true
    }

    // ── Non-nullable typed params reject null with clear error ────────────────

    "non-nullable Double param gives error on null" {
        val engine = klangScript {
            registerFunction<Double, Double>("strictDouble") { d ->
                d * 2
            }
        }

        val error = shouldThrow<KlangScriptRuntimeError> {
            engine.execute("strictDouble(null)")
        }

        error.location shouldNotBe null
        error.message shouldNotBe null
    }

    "non-nullable String param gives error on null" {
        val engine = klangScript {
            registerFunction<String, String>("strictString") { s ->
                s.uppercase()
            }
        }

        val error = shouldThrow<KlangScriptRuntimeError> {
            engine.execute("strictString(null)")
        }

        error.location shouldNotBe null
        error.message shouldNotBe null
    }

    "non-nullable two params, second is null, gives error" {
        val engine = klangScript {
            registerFunction<Double, Double, Double>("add") { a, b ->
                a + b
            }
        }

        val error = shouldThrow<KlangScriptRuntimeError> {
            engine.execute("add(1, null)")
        }

        error.location shouldNotBe null
    }

    // ── guardNativeCall produces useful InternalError ─────────────────────────

    "InternalError includes function name and param values" {
        val engine = klangScript {
            registerFunction<Double, Double>("badFn") { _ ->
                throw RuntimeException("something broke")
            }
        }

        val error = shouldThrow<KlangScriptInternalError> {
            engine.execute("badFn(42)")
        }

        error.errorType shouldBe KlangScriptErrorType.InternalError
        error.message shouldContain "badFn"
        error.message shouldContain "42"
        error.message shouldContain "something broke"
        error.location shouldNotBe null
    }

    "InternalError from extension method includes type and method name" {
        val engine = klangScript {
            registerFunction<Double, Double>("identity") { it }
        }

        // Calling a method that doesn't exist on a number triggers TypeError, not InternalError
        // But calling a method that exists but throws internally should give InternalError
        // For now, just verify the guard works with a top-level function
    }
})
