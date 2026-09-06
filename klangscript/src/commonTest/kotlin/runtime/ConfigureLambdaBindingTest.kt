/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.runtime

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.script.KlangScriptEngine
import io.peekandpoke.klang.script.klangScriptEngine

/**
 * End-to-end binding of a script lambda into a native's trailing function-typed parameter,
 * through the real interpreter path (`positionalArgsForNative` → `resolveByParamSpec`).
 *
 * The natives here stand in for the DSL doors of `docs/tasks/dsl-configure-lambdas.md`:
 * `door(freq = 440, configure = null)` mirrors `Osc.sine(freq, configure)`.
 */
class ConfigureLambdaBindingTest : StringSpec({

    fun engine(): KlangScriptEngine = klangScriptEngine {
        // door(freq: Double = 440, configure: ((Double) -> Any)? = null): applies configure to freq
        registerFunctionWithSpecs(
            name = "door",
            paramSpecs = listOf(
                ParamSpec(name = "freq", kotlinType = Double::class, isOptional = true, default = { NumberValue(440.0) }),
                ParamSpec(
                    name = "configure", kotlinType = Function1::class,
                    isOptional = true, isNullable = true, default = { NullValue },
                ),
            ),
        ) { args, loc ->
            val freq = args[0].convertToKotlin(Double::class, loc)
            val configure = args[1]
            if (configure is NullValue) {
                NumberValue(freq)
            } else {
                @Suppress("UNCHECKED_CAST")
                val fn = configure.convertToKotlin(Function1::class, loc) as (Any?) -> Any?
                NumberValue((fn(freq) as Number).toDouble())
            }
        }

        // tenfold(): a native RETURNING a Kotlin lambda, the way sprudel's rev()/fast() return a
        // PatternMapperFn. wrapAsRuntimeValue carries it as a NativeObjectValue.
        registerFunctionWithSpecs(name = "tenfold", paramSpecs = emptyList()) { _, _ ->
            val fn: (Any?) -> Any? = { x -> (x as Double) * 10 }
            wrapAsRuntimeValue(fn)
        }

        // pick(a: fn? = null, b: fn? = null): reports which slot the lambda landed in
        registerFunctionWithSpecs(
            name = "pick",
            paramSpecs = listOf(
                ParamSpec("a", Function1::class, isOptional = true, isNullable = true, default = { NullValue }),
                ParamSpec("b", Function1::class, isOptional = true, isNullable = true, default = { NullValue }),
            ),
        ) { args, _ ->
            StringValue(
                when {
                    args[0] !is NullValue -> "a"
                    args[1] !is NullValue -> "b"
                    else -> "none"
                }
            )
        }

        // ambiguous(n: Double = 0, a: fn? = null, b: fn? = null): two trailing function slots
        registerFunctionWithSpecs(
            name = "ambiguous",
            paramSpecs = listOf(
                ParamSpec("n", Double::class, isOptional = true, default = { NumberValue(0.0) }),
                ParamSpec("a", Function1::class, isOptional = true, isNullable = true, default = { NullValue }),
                ParamSpec("b", Function1::class, isOptional = true, isNullable = true, default = { NullValue }),
            ),
        ) { args, loc ->
            NumberValue(args[0].convertToKotlin(Double::class, loc))
        }
    }

    fun num(code: String): Double = (engine().execute(code) as NumberValue).value
    fun str(code: String): String = (engine().execute(code) as StringValue).value

    "no lambda: the door returns its default" {
        num("door()") shouldBe 440.0
    }

    "positional scalar only" {
        num("door(100)") shouldBe 100.0
    }

    "a sole positional lambda floats to the trailing configure slot, freq takes its default" {
        num("door(x => x * 2)") shouldBe 880.0
    }

    "scalar plus lambda bind in order" {
        num("door(100, x => x * 2)") shouldBe 200.0
    }

    "named configure binds without floating" {
        num("door(configure = x => x * 3)") shouldBe 1320.0
    }

    "a 0-param lambda in a 1-param slot is adapted to the slot's arity, not rejected" {
        num("door(() => 42)") shouldBe 42.0
    }

    "a lambda with more params than the slot binds the extras to null, never to an outer variable" {
        // Without null padding `y` would be left unbound and resolve to the outer `let y = 5`.
        val code = """
            let y = 5
            door((x, y) => y == null ? x + 1 : -1)
        """.trimIndent()
        num(code) shouldBe 441.0
    }

    "a Kotlin lambda returned by a native (held as a native object) floats like a script lambda" {
        num("door(tenfold())") shouldBe 4400.0
    }

    "block-bodied lambda with return" {
        num("door(x => { return x + 1 })") shouldBe 441.0
    }

    "a lambda that is not the last argument does not float and fails on the scalar slot" {
        val err = shouldThrow<KlangScriptTypeError> { engine().execute("door(x => x, 100)") }
        err.message shouldBe "expected Double, got a function"
    }

    "a lambda on a function-typed slot stays there" {
        str("pick(x => x)") shouldBe "a"
    }

    "named binding picks the named function slot" {
        str("pick(b = x => x)") shouldBe "b"
    }

    "two trailing function slots are ambiguous: the lambda stays on the scalar slot and fails" {
        shouldThrow<KlangScriptTypeError> { engine().execute("ambiguous(x => x)") }
    }

    "ambiguity is resolved by naming" {
        num("ambiguous(n = 5, a = x => x)") shouldBe 5.0
    }
})
