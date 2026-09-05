/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.runtime

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.peekandpoke.klang.script.KlangScriptEngine
import io.peekandpoke.klang.script.klangScriptEngine

/**
 * The `invoke` operator: a native OBJECT becomes callable when its type registers a method
 * named `invoke` (`docs/tasks/klangscript-native-object-operators.md`, revision 2026-09-05).
 * The call takes the spec-aware path of a member call, so named args, defaults and the
 * trailing-lambda rule apply. Consumers: `Master(...)`, `Pipeline(...)`, the field accessors.
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

    "an object without invoke is not callable, and the error says how to make it so" {
        val err = shouldThrow<KlangScriptTypeError> { engine().execute("Mute()") }
        err.message shouldContain "Cannot call non-function value"
        err.message shouldContain "'invoke'"
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
