/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.KlangScriptTypeError

/**
 * A boolean on a number parameter is a script type error at the call, on every platform. The signals'
 * `from` / `to` are `Number?`, read by `optArg` without a cast since 2026-10-06; the refusal lives in
 * `convertToKotlin`. On `main` the generated `as Number?` refused it as an internal ClassCastException.
 */
class StrictArgumentConversionSpec : StringSpec({

    "sine(true, 2) is a type error, not sine(1, 2)" {
        val engine = klangScript { registerLibrary(sprudelLib) }

        shouldThrow<KlangScriptTypeError> {
            engine.execute("import * from \"sprudel\"\nsine(true, 2)")
        }.message shouldBe "Cannot convert BooleanValue to Number"
    }
})
