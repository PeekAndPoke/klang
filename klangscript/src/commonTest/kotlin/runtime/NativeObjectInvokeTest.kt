/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.runtime

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.peekandpoke.klang.script.KlangScriptEngine
import io.peekandpoke.klang.script.klangScriptEngine

/**
 * The call operator: a native OBJECT becomes callable when its type registers a method under the
 * internal symbol `__invoke__` (`docs/tasks/klangscript-native-object-operators.md`).
 * The call takes the spec-aware path of a member call, so named args, defaults and the
 * trailing-lambda rule apply. Consumers: `Katalyst(...)`, the field accessors.
 */
/** A native object whose type registers `invoke`. */
private object Doubler

/** A native object whose type registers NO `invoke`. */
private object Mute

class NativeObjectInvokeTest : StringSpec({

    fun engine(): KlangScriptEngine = klangScriptEngine {
        // Doubler(n = 1, configure: ((Double) -> Any)? = null): doubles n, optionally post-processed
        registerObject("Doubler", Doubler)
        registerExtensionMethodWithSpecs(
            receiver = Doubler::class,
            name = NativeOperatorNames.INVOKE,
            paramSpecs = listOf(
                ParamSpec("n", Double::class, isOptional = true, default = { NumberValue(1.0) }),
                ParamSpec("configure", Function1::class, isOptional = true, isNullable = true, default = { NullValue }),
            ),
        ) { _, args, loc ->
            val doubled = args[0].convertToKotlin(Double::class, loc) * 2
            val configure = args[1]
            if (configure is NullValue) {
                NumberValue(doubled)
            } else {
                @Suppress("UNCHECKED_CAST")
                val fn = configure.convertToKotlin(Function1::class, loc) as (Any?) -> Any?
                NumberValue((fn(doubled) as Number).toDouble())
            }
        }
        // A plain method next to the call form
        registerExtensionMethodWithSpecs(
            receiver = Doubler::class,
            name = "triple",
            paramSpecs = listOf(ParamSpec("n", Double::class)),
        ) { _, args, loc -> NumberValue(args[0].convertToKotlin(Double::class, loc) * 3) }
        // A built-in type with an operator symbol registered next to a plain method
        registerExtensionMethodWithSpecs(receiver = StringValue::class, name = NativeOperatorNames.INVOKE, paramSpecs = emptyList()) { _, _, _ ->
            StringValue("reached")
        }
        registerExtensionMethodWithSpecs(receiver = StringValue::class, name = "shout", paramSpecs = emptyList()) { rcv, _, _ ->
            StringValue((rcv as StringValue).value.uppercase())
        }
        // Mute: an object with NO invoke
        registerObject("Mute", Mute)
    }

    fun num(code: String): Double = (engine().execute(code) as NumberValue).value

    "an object with an invoke method is callable with no arguments (defaults apply)" {
        num("Doubler()") shouldBe 2.0
    }

    "positional argument" {
        num("Doubler(21)") shouldBe 42.0
    }

    "named argument" {
        num("Doubler(n = 5)") shouldBe 10.0
    }

    "a sole trailing lambda floats into the function-typed slot" {
        num("Doubler(x => x + 0.5)") shouldBe 2.5
    }

    "scalar plus lambda" {
        num("Doubler(4, x => x * 10)") shouldBe 80.0
    }

    "the object is still a value: it can be passed around and called later" {
        num("let d = Doubler\nd(3)") shouldBe 6.0
    }

    "an object without invoke is not callable, and the error speaks to the script user" {
        val err = shouldThrow<KlangScriptTypeError> { engine().execute("Mute()") }
        err.message shouldBe "'Mute' cannot be called: it is not a function."
    }

    "the internal symbol is no member a script can reach: Doubler.__invoke__(3) is no method" {
        val err = shouldThrow<KlangScriptTypeError> { engine().execute("Doubler.__invoke__(3)") }
        err.message shouldContain "has no method '__invoke__'"
        num("Doubler.triple(3)") shouldBe 9.0
    }

    "on a built-in type too, an operator symbol is no member: \"a\".__invoke__() is no method" {
        (engine().execute("\"a\".shout()") as StringValue).value shouldBe "A"
        val err = shouldThrow<KlangScriptTypeError> { engine().execute("\"a\".__invoke__()") }
        err.message shouldContain "has no method '__invoke__'"
    }

    "a typo's list of available methods leaves the internal symbol out" {
        val err = shouldThrow<KlangScriptTypeError> { engine().execute("Doubler.tripel(3)") }
        err.message shouldContain "triple"
        err.message shouldNotContain "__invoke__"
    }

    "operator symbols are the names of the form __x__" {
        NativeOperatorNames.isOperatorName("__invoke__") shouldBe true
        NativeOperatorNames.isOperatorName("__plus__") shouldBe true
        NativeOperatorNames.isOperatorName("invoke") shouldBe false
        NativeOperatorNames.isOperatorName("__") shouldBe false
        NativeOperatorNames.isOperatorName("____") shouldBe false
        NativeOperatorNames.isOperatorName("__x") shouldBe false
    }

    "the call form registers under the internal symbol __invoke__" {
        NativeOperatorNames.INVOKE shouldBe "__invoke__"
    }

    "an error at a callable object's call names the call the user wrote, never the internal symbol" {
        val err = shouldThrow<KlangScriptArgumentError> { engine().execute("Doubler(m = 5)") }
        err.functionName shouldBe "Doubler"
        err.message shouldContain "unknown parameter 'm'"
        err.format() shouldNotContain "invoke"
    }

    "the call form is no member a script can reach by Kotlin's word: Doubler.invoke(3) does not call it" {
        shouldThrow<KlangScriptRuntimeError> { engine().execute("Doubler.invoke(3)") }
    }

    "a plain method on the object still works next to invoke" {
        val e = klangScriptEngine {
            registerObject("Doubler", Doubler) {
                registerProperty("two") { 2.0 }
            }
        }
        (e.execute("Doubler.two") as NumberValue).value shouldBe 2.0
    }
})
