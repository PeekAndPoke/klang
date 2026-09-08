/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.peekandpoke.klang.script.builder.registerFunction
import io.peekandpoke.klang.script.builder.registerLibrary
import io.peekandpoke.klang.script.builder.registerType
import io.peekandpoke.klang.script.runtime.KlangScriptArgumentError

/**
 * A native extension method that takes no parameter refuses an argument.
 *
 * The bridges for one to five parameters check the argument count, and the parameter specs catch a
 * surplus; the zero-parameter bridge had neither, so `3.14159.round(2)` returned 3 and the 2 was
 * dropped without a word (found in the round-3 review of the number methods, 2026-09-08).
 */
class ExtensionMethodAritySpec : StringSpec({

    class Box(val value: Double)

    val lib = klangScriptLibrary("boxes") {
        registerType<Box> {
            registerMethod("twice") { Box(value * 2) }
        }
        registerFunction("box") { value: Double -> Box(value) }
        registerFunction("unbox") { box: Box -> box.value }
    }

    fun engine() = klangScriptEngine {
        registerLibrary(lib)
    }

    "a zero-parameter method still works without arguments" {
        engine().execute("import * from \"boxes\"\nunbox(box(5).twice())").toDisplayString() shouldBe "10"
    }

    "a zero-parameter method refuses an argument" {
        val error = shouldThrow<KlangScriptArgumentError> {
            engine().execute("import * from \"boxes\"\nunbox(box(5).twice(3))")
        }

        error.message shouldContain "twice"
        error.message shouldContain "expected 0 arguments but got 1"
    }
})
