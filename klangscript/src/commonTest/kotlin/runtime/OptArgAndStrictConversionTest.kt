/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.runtime

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.common.SourceLocation

/**
 * Two runtime pieces of the one-call registration (2026-10-06), on every platform:
 *
 *  - `optArg`, the reader of a nullable optional argument whose Kotlin default is `null`: absent and
 *    explicit `null` both give null, a given value converts, and a wrong type fails exactly as
 *    `convertArgToKotlin` does (same message, same location).
 *  - `convertToKotlin` refuses a number, a boolean, an array or an object on a target it cannot be, as a
 *    script type error. Before, the raw value passed through and only a cast in the generated code stopped
 *    it (an internal ClassCastException); where no cast existed, nothing did on JS.
 */
class OptArgAndStrictConversionTest : StringSpec({

    val loc = SourceLocation(source = "test", startLine = 3, startColumn = 7, endLine = 3, endColumn = 12)

    // ---- optArg ----

    "optArg: an omitted argument is null" {
        optArg(listOf(NumberValue(1.0)), 1, Double::class, loc) shouldBe null
        optArg(emptyList(), 0, Any::class, loc) shouldBe null
    }

    "optArg: an explicit null is null" {
        optArg(listOf(NullValue), 0, Double::class, loc) shouldBe null
    }

    "optArg: a given argument converts like convertArgToKotlin" {
        val args = listOf(NumberValue(1.0), NumberValue(2.5))

        optArg(args, 1, Double::class, loc) shouldBe 2.5
        optArg(args, 1, Double::class, loc) shouldBe
            convertArgToKotlin(fn = "f", args = args, index = 1, cls = Double::class, nullable = true, loc = loc)
    }

    "optArg: a wrong type fails with the message and location of convertArgToKotlin" {
        val args = listOf(StringValue("x"))

        val viaOptArg = shouldThrow<KlangScriptTypeError> { optArg(args, 0, Double::class, loc) }
        val viaConvert = shouldThrow<KlangScriptTypeError> {
            convertArgToKotlin(fn = "f", args = args, index = 0, cls = Double::class, nullable = true, loc = loc)
        }

        viaOptArg.message shouldBe viaConvert.message
        viaOptArg.location shouldBe loc
        viaConvert.location shouldBe loc
    }

    // ---- convertToKotlin: no silent pass-through ----

    "a boolean on a number target is a script type error" {
        shouldThrow<KlangScriptTypeError> { BooleanValue(true).convertToKotlin(Double::class, loc) }.let {
            it.message shouldBe "Cannot convert BooleanValue to Double"
            it.location shouldBe loc
        }
        shouldThrow<KlangScriptTypeError> { BooleanValue(true).convertToKotlin(Number::class, loc) }
            .message shouldBe "Cannot convert BooleanValue to Number"
    }

    "an array on a number target is a script type error" {
        shouldThrow<KlangScriptTypeError> { ArrayValue(mutableListOf(NumberValue(1.0))).convertToKotlin(Double::class, loc) }
            .message shouldBe "Cannot convert ArrayValue to Double"
    }

    "an object on a number target is a script type error" {
        shouldThrow<KlangScriptTypeError> { ObjectValue(mutableMapOf()).convertToKotlin(Double::class, loc) }
            .message shouldBe "Cannot convert ObjectValue to Double"
    }

    "a number on a non-numeric target is a script type error" {
        shouldThrow<KlangScriptTypeError> { NumberValue(5.0).convertToKotlin(String::class, loc) }.let {
            it.message shouldBe "Cannot convert NumberValue to String"
            it.location shouldBe loc
        }
        shouldThrow<KlangScriptTypeError> { NumberValue(1.0).convertToKotlin(Boolean::class, loc) }
            .message shouldBe "Cannot convert NumberValue to Boolean"
        shouldThrow<KlangScriptTypeError> { optArg(listOf(NumberValue(5.0)), 0, String::class, loc) }
            .message shouldBe "Cannot convert NumberValue to String"
    }

    "a number still reaches every numeric target and Any" {
        NumberValue(2.5).convertToKotlin(Double::class, loc) shouldBe 2.5
        NumberValue(2.0).convertToKotlin(Int::class, loc) shouldBe 2
        NumberValue(2.5).convertToKotlin(Number::class, loc) shouldBe 2.5
        NumberValue(2.5).convertToKotlin(Any::class, loc) shouldBe 2.5
    }

    "a boolean, an array and an object still reach the targets they can be" {
        BooleanValue(true).convertToKotlin(Boolean::class, loc) shouldBe true
        BooleanValue(false).convertToKotlin(Any::class, loc) shouldBe false
        ArrayValue(mutableListOf(NumberValue(1.0))).convertToKotlin(List::class, loc) shouldBe listOf(1.0)
        ArrayValue(mutableListOf(NumberValue(1.0))).convertToKotlin(Any::class, loc) shouldBe listOf(1.0)
        ObjectValue(mutableMapOf("a" to NumberValue(1.0))).convertToKotlin(Map::class, loc) shouldBe mapOf("a" to 1.0)
    }
})
