/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.runtime

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.script.klangScriptEngine

/**
 * Member access reaches the registered extensions of every value kind the same way.
 *
 * The interpreter looked up extensions for numbers, strings and arrays only; a boolean (and every other
 * kind) ended at "Cannot access property 'toString' on non-object value: true", although the stdlib
 * registers `toString` on booleans (`docs/tasks-archive/2026-10/20261007-boolean-member-access.md`, 2026-10-06). Each row registers
 * one method on one runtime value kind and calls it through a member access.
 */
class MemberAccessValueKindsSpec : StringSpec({

    fun engine() = klangScriptEngine {
        registerExtensionMethod(NumberValue::class, "kind") { _, _, _ -> StringValue("number") }
        registerExtensionMethod(StringValue::class, "kind") { _, _, _ -> StringValue("string") }
        registerExtensionMethod(BooleanValue::class, "kind") { self, _, _ -> StringValue("boolean ${self.value}") }
        registerExtensionMethod(NullValue::class, "kind") { _, _, _ -> StringValue("null") }
        registerExtensionMethod(ArrayValue::class, "kind") { _, _, _ -> StringValue("array") }
        registerExtensionMethod(FunctionValue::class, "kind") { _, _, _ -> StringValue("function") }
        registerExtensionMethod(NativeFunctionValue::class, "kind") { _, _, _ -> StringValue("native function") }
        registerExtensionMethod(BoundNativeMethod::class, "kind") { _, _, _ -> StringValue("bound method") }
        registerExtensionMethod(ObjectValue::class, "kind") { _, _, _ -> StringValue("never reached") }
        registerFunctionRaw("nativeFn") { _, _ -> NullValue }
    }

    "every value kind reaches its registered extension method through member access" {
        listOf(
            "1.kind()" to "number",
            "\"s\".kind()" to "string",
            "true.kind()" to "boolean true",
            "false.kind()" to "boolean false",
            "(1 < 2).kind()" to "boolean true",
            "let b = false\nb.kind()" to "boolean false",
            "null.kind()" to "null",
            "[1, 2].kind()" to "array",
            "((x) => x).kind()" to "function",
            "nativeFn.kind()" to "native function",
            "1.kind.kind()" to "bound method",
        ).forEach { (code, expected) ->
            withClue(code) {
                engine().execute(code).shouldBeInstanceOf<StringValue>().value shouldBe expected
            }
        }
    }

    "a script object keeps plain property access: its own properties are its members" {
        engine().execute("let o = { kind: \"own\" }\no.kind").shouldBeInstanceOf<StringValue>().value shouldBe "own"
        engine().execute("let o = { a: 1 }\no.kind") shouldBe NullValue
    }

    "an unknown method on a kind with extensions names the type by its script name and suggests the nearest name" {
        listOf(
            "true.kinf()" to "Boolean",
            "1.kinf()" to "Number",
            "\"s\".kinf()" to "String",
            "[1].kinf()" to "Array",
            "null.kinf()" to "null",
            "((x) => x).kinf()" to "Function",
            "nativeFn.kinf()" to "Function",
        ).forEach { (code, typeName) ->
            withClue(code) {
                val error = shouldThrow<KlangScriptTypeError> {
                    engine().execute(code)
                }

                error.message shouldContain "Type '$typeName' has no method 'kinf'"
                error.message shouldContain "'kind'"
            }
        }
    }

    "a kind with no extensions registered keeps the generic error" {
        val error = shouldThrow<KlangScriptTypeError> {
            klangScriptEngine().execute("true.toString()")
        }

        error.message shouldContain "Cannot access property 'toString' on non-object value: true"
    }
})
