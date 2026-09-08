/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.RuntimeValue
import io.peekandpoke.klang.script.runtime.StringValue

/**
 * Methods called on a number literal, end to end: `2.toString()`.
 *
 * `toString` has been registered on numbers for a long time but was unreachable on a literal until the
 * lexer stopped eating the dot (2026-09-08, `docs/tasks/klangscript-number-methods.md`). The parser side
 * is pinned in `NumberLiteralMethodCallSpec`; this is the proof that the stdlib dispatch sees the call.
 * The number methods of that task (`pow`, `clamp`, `semitones`, ...) join this file when they land.
 */
class StdLibNumberMethodsTest : StringSpec({

    fun eval(code: String): RuntimeValue {
        val engine = klangScript {
            registerLibrary(KlangStdLib.create())
        }

        return engine.execute("import * from \"stdlib\"\n$code")
    }

    "toString() on a number literal" {
        listOf(
            "2.toString()" to "2",
            "2.5.toString()" to "2.5",
            "0.5.toString()" to "0.5",
            "1e3.toString()" to "1000",
            "-2.toString()" to "-2",
            "-1.5.toString()" to "-1.5",
        ).forEach { (code, expected) ->
            withClue(code) {
                eval(code).shouldBeInstanceOf<StringValue>().value shouldBe expected
            }
        }
    }

    "toString() on a literal inside a template" {
        eval("`fifth: ${'$'}{7.toString()}`").shouldBeInstanceOf<StringValue>().value shouldBe "fifth: 7"
    }
})
