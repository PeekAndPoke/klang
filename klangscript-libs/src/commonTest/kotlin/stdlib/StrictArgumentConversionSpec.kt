/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.KlangScriptTypeError

/**
 * A value on a parameter it cannot be is a script type error at the call, on every platform; the refusal
 * lives in `convertToKotlin` (2026-10-06). On `main` the generated casts refused these as internal
 * ClassCastExceptions, and where no cast existed nothing did: `Ignitor.variants(1, Ign.sine())` built a tree
 * with a number child in the browser.
 */
class StrictArgumentConversionSpec : StringSpec({

    "compressor(lookahead = true) is a type error on every platform" {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")

        shouldThrow<KlangScriptTypeError> {
            engine.execute("Katalyst(k => k.compressor(lookahead = true))")
        }.message shouldBe "Cannot convert BooleanValue to Double"
    }

    "Ignitor.variants(1, Ign.sine()) is a type error on every platform" {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")

        shouldThrow<KlangScriptTypeError> {
            engine.execute("Ignitor.variants(1, Ign.sine())")
        }.message shouldBe "Cannot convert NumberValue to IgnitorDsl"
    }

    "\"abc\".startsWith(1) is a type error on every platform" {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")

        shouldThrow<KlangScriptTypeError> {
            engine.execute("\"abc\".startsWith(1)")
        }.message shouldBe "Cannot convert NumberValue to String"
    }
})
