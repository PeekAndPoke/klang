package io.peekandpoke.klang.script.runtime

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.script.ast.ArrowFunctionBody
import io.peekandpoke.klang.script.ast.NumberLiteral
import io.peekandpoke.klang.script.klangScript

/**
 * Tests for NativeInterop conversion utilities:
 *   - wrapAsRuntimeValue()
 *   - convertToKotlin() for ArrayValue and NativeFunctionValue
 *   - convertArgToKotlin()
 *   - checkArgsSize()
 *   - FunctionValue.convertFunctionToKotlin() — actual argument passing
 */
class NativeInteropConversionTest : StringSpec({

    // ---- wrapAsRuntimeValue ----

    "wrapAsRuntimeValue: null -> NullValue" {
        wrapAsRuntimeValue(null) shouldBe NullValue
    }

    "wrapAsRuntimeValue: String -> StringValue" {
        wrapAsRuntimeValue("hello") shouldBe StringValue("hello")
    }

    "wrapAsRuntimeValue: Double -> NumberValue" {
        wrapAsRuntimeValue(3.14) shouldBe NumberValue(3.14)
    }

    "wrapAsRuntimeValue: Int -> NumberValue" {
        wrapAsRuntimeValue(42) shouldBe NumberValue(42.0)
    }

    "wrapAsRuntimeValue: Boolean -> BooleanValue" {
        wrapAsRuntimeValue(true) shouldBe BooleanValue(true)
        wrapAsRuntimeValue(false) shouldBe BooleanValue(false)
    }

    "wrapAsRuntimeValue: existing RuntimeValue passes through unchanged" {
        val str = StringValue("unchanged")
        wrapAsRuntimeValue(str) shouldBe str

        val num = NumberValue(99.0)
        wrapAsRuntimeValue(num) shouldBe num

        wrapAsRuntimeValue(NullValue) shouldBe NullValue
    }

    "wrapAsRuntimeValue: arbitrary native object -> NativeObjectValue" {
        data class Foo(val x: Int)

        val obj = Foo(42)
        val wrapped = wrapAsRuntimeValue(obj)
        wrapped.shouldBeInstanceOf<NativeObjectValue<*>>()
        (wrapped as NativeObjectValue<*>).value shouldBe obj
    }

    // ---- convertToKotlin for ArrayValue ----

    "ArrayValue: convertToKotlin -> DoubleArray" {
        val arr = ArrayValue(mutableListOf(NumberValue(1.0), NumberValue(2.0), NumberValue(3.0)))
        val result = arr.convertToKotlin(DoubleArray::class)
        result.toList() shouldBe listOf(1.0, 2.0, 3.0)
    }

    "ArrayValue: convertToKotlin -> IntArray" {
        val arr = ArrayValue(mutableListOf(NumberValue(10.0), NumberValue(20.0), NumberValue(30.0)))
        val result = arr.convertToKotlin(IntArray::class)
        result.toList() shouldBe listOf(10, 20, 30)
    }

    "ArrayValue: convertToKotlin -> FloatArray" {
        val arr = ArrayValue(mutableListOf(NumberValue(1.5), NumberValue(2.5)))
        val result = arr.convertToKotlin(FloatArray::class)
        result.toList() shouldBe listOf(1.5f, 2.5f)
    }

    "ArrayValue: convertToKotlin -> List" {
        val arr = ArrayValue(mutableListOf(NumberValue(1.0), NumberValue(2.0)))
        val result = arr.convertToKotlin(List::class)
        result shouldBe listOf(1.0, 2.0)
    }

    "ArrayValue: convertToKotlin -> Array" {
        val arr = ArrayValue(mutableListOf(NumberValue(1.0), StringValue("x")))
        val result = arr.convertToKotlin(Array::class)
        result.toList() shouldBe listOf(1.0, "x")
    }

    "ArrayValue: convertToKotlin -> List with null elements from NullValue" {
        val arr = ArrayValue(mutableListOf(NumberValue(1.0), NullValue, NumberValue(3.0)))
        val result = arr.convertToKotlin(List::class)
        result shouldBe listOf(1.0, null, 3.0)
    }

    // ---- convertToKotlin for NativeFunctionValue ----

    "NativeFunctionValue: convertToKotlin returns a no-arg callable" {
        val nativeFn = NativeFunctionValue("test") { _, _ -> NumberValue(42.0) }
        // The NativeFunctionValue case in convertToKotlin always returns a { -> ... } lambda,
        // regardless of the target class. The lambda calls the native function and unwraps the result.
        @Suppress("UNCHECKED_CAST")
        val fn = nativeFn.convertToKotlin(Any::class) as (() -> Any?)
        fn() shouldBe 42.0
    }

    // ---- convertToKotlin: TypeError for impossible conversions ----

    "convertToKotlin: throws TypeError when value cannot be converted to target class" {
        val str = StringValue("hello")
        shouldThrow<TypeError> {
            // String cannot be converted to Int — goes through else branch, cls.isInstance fails
            str.convertToKotlin(Int::class)
        }
    }

    // ---- convertArgToKotlin ----

    "convertArgToKotlin: extracts and converts argument by index" {
        val args = listOf(NumberValue(42.0), BooleanValue(true))
        convertArgToKotlin("testFn", args, 0, Double::class) shouldBe 42.0
        convertArgToKotlin("testFn", args, 1, Boolean::class) shouldBe true
    }

    "convertArgToKotlin: throws ArgumentError for out-of-bounds index" {
        val args = listOf(NumberValue(1.0))
        shouldThrow<ArgumentError> {
            convertArgToKotlin("testFn", args, 5, Double::class)
        }
    }

    // ---- checkArgsSize ----

    "checkArgsSize: does not throw when args count meets expectation" {
        val args = listOf(NumberValue(1.0), NumberValue(2.0))
        checkArgsSize("myFn", args, 2) // should not throw
        checkArgsSize("myFn", args, 1) // more args than required is also fine
    }

    "checkArgsSize: throws ArgumentError when too few arguments" {
        val args = listOf(NumberValue(1.0))
        val error = shouldThrow<ArgumentError> {
            checkArgsSize("myFn", args, 3)
        }
        error.expected shouldBe 3
        error.actual shouldBe 1
    }

    // ---- Arrow function: actual argument values reach the body ----

    "FunctionValue from script: numeric arguments used in expression body" {
        var capturedFn: FunctionValue? = null
        val engine = klangScript {
            registerFunctionRaw("withCallback") { args, _ ->
                capturedFn = args[0] as? FunctionValue
                NullValue
            }
        }
        engine.execute("withCallback((a, b) => a + b)")

        val fn: (Any?, Any?) -> Any? = capturedFn!!.convertFunctionToKotlin()
        // Arguments are wrapped via wrapAsRuntimeValue, so 3.0 and 4.0 become NumberValues
        fn(3.0, 4.0) shouldBe 7.0
    }

    "FunctionValue from script: string argument passes through identity function" {
        var capturedFn: FunctionValue? = null
        val engine = klangScript {
            registerFunctionRaw("withCallback") { args, _ ->
                capturedFn = args[0] as? FunctionValue
                NullValue
            }
        }
        // (a) => a just returns its argument — verifies that a Kotlin String is
        // wrapped as StringValue, bound to the parameter, and unwrapped on return
        engine.execute("withCallback((a) => a)")

        val fn: (Any?) -> Any? = capturedFn!!.convertFunctionToKotlin()
        fn("hello world") shouldBe "hello world"
    }

    "FunctionValue from script: string + string concatenation in body" {
        var capturedFn: FunctionValue? = null
        val engine = klangScript {
            registerFunctionRaw("withCallback") { args, _ ->
                capturedFn = args[0] as? FunctionValue
                NullValue
            }
        }
        // string + string is supported; number + string is not (use template literals)
        engine.execute("withCallback((a, b) => a + b)")

        val fn: (Any?, Any?) -> Any? = capturedFn!!.convertFunctionToKotlin()
        fn("hello", " world") shouldBe "hello world"
    }

    "FunctionValue from script: block body with return — arguments reach the body" {
        var capturedFn: FunctionValue? = null
        val engine = klangScript {
            registerFunctionRaw("withCallback") { args, _ ->
                capturedFn = args[0] as? FunctionValue
                NullValue
            }
        }
        engine.execute(
            """
            withCallback((x) => {
                let doubled = x * 2
                return doubled + 1
            })
            """.trimIndent()
        )

        val fn: (Any?) -> Any? = capturedFn!!.convertFunctionToKotlin()
        fn(5.0) shouldBe 11.0
    }

    "FunctionValue from script: block body with no return yields null" {
        var capturedFn: FunctionValue? = null
        val engine = klangScript {
            registerFunctionRaw("withCallback") { args, _ ->
                capturedFn = args[0] as? FunctionValue
                NullValue
            }
        }
        engine.execute(
            """
            withCallback(() => {
                let x = 42
            })
            """.trimIndent()
        )

        val fn: () -> Any? = capturedFn!!.convertFunctionToKotlin()
        // Block body with no return — callFunction returns NullValue, .value is null
        fn() shouldBe null
    }

    "FunctionValue from script: closure captures outer variable" {
        var capturedFn: FunctionValue? = null
        val engine = klangScript {
            registerFunctionRaw("withCallback") { args, _ ->
                capturedFn = args[0] as? FunctionValue
                NullValue
            }
        }
        engine.execute(
            """
            let base = 100
            withCallback((n) => base + n)
            """.trimIndent()
        )

        val fn: (Any?) -> Any? = capturedFn!!.convertFunctionToKotlin()
        fn(5.0) shouldBe 105.0
    }

    // ---- convertFunctionToKotlin: error for >10 parameters ----

    "FunctionValue: convertFunctionToKotlin throws TypeError for more than 10 parameters" {
        val fv = FunctionValue(
            parameters = List(11) { "a$it" },
            body = ArrowFunctionBody.ExpressionBody(NumberLiteral(0.0)),
            closureEnv = Environment(),
            engine = klangScript()
        )
        shouldThrow<TypeError> {
            fv.convertFunctionToKotlin<Any>()
        }
    }
})
