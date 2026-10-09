/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.stdlib

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.script.klangScript
import io.peekandpoke.klang.script.runtime.KlangScriptTypeError
import io.peekandpoke.klang.script.runtime.toObjectOrNull

/**
 * A value on a parameter it cannot be is a script type error at the call, on every platform; the refusal
 * lives in `convertToKotlin` (2026-10-06). On `main` the generated casts refused these as internal
 * ClassCastExceptions, and where no cast existed nothing did: `Ignitor.variants(1, Ign.sine())` built a tree
 * with a raw number child in the browser. Since Q23 (maintainer, 2026-10-09) a number child of `variants` is a
 * constant, converted at the door like every value knob; any other child is still a type error.
 */
class StrictArgumentConversionSpec : StringSpec({

    "compressor(lookahead = true) is a type error on every platform" {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")

        shouldThrow<KlangScriptTypeError> {
            engine.execute("Katalyst(k => k.compressor(lookahead = true))")
        }.message shouldBe "Cannot convert BooleanValue to Double"
    }

    "Ignitor.variants(1, Ign.sine()): a number child is a constant, on both doors" {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")

        val expected = IgnitorDsl.Variants(listOf(IgnitorDsl.Constant(1.0), IgnitorDsl.Sine(freq = IgnitorDsl.Freq)))

        engine.execute("Ignitor.variants(1, Ign.sine())").toObjectOrNull<IgnitorDsl>() shouldBe expected
        KlangScriptIgnitor.variants(1, KlangScriptIgnitor.sine()) shouldBe expected
    }

    "door parity: Ign.variants(400, 1200, 3000) builds the same constants on both doors" {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")

        val script = engine.execute("Ign.variants(400, 1200, 3000)").toObjectOrNull<IgnitorDsl>()
        val kotlin = KlangScriptIgnitor.variants(400, 1200, 3000)

        script shouldBe kotlin
        kotlin shouldBe IgnitorDsl.Variants(listOf(400.0, 1200.0, 3000.0).map { IgnitorDsl.Constant(it) })
    }

    "Ignitor.variants(\"a\", Ign.sine()): a child that is neither a number nor a sound is still a type error" {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")

        shouldThrow<KlangScriptTypeError> {
            engine.execute("""Ignitor.variants("a", Ign.sine())""")
        }.message shouldBe "expected a sound or a number, got String"
    }

    "Ignitor.variants(null) and a null variable child are the same typed error, not an internal one" {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")

        shouldThrow<KlangScriptTypeError> {
            engine.execute("Ign.variants(null)")
        }.message shouldBe "expected a sound or a number, got null"

        shouldThrow<KlangScriptTypeError> {
            engine.execute("let x = null\nIgn.variants(1, x)")
        }.message shouldBe "expected a sound or a number, got null"
    }

    "\"abc\".startsWith(1) is a type error on every platform" {
        val engine = klangScript()
        engine.execute("""import * from "stdlib"""")

        shouldThrow<KlangScriptTypeError> {
            engine.execute("\"abc\".startsWith(1)")
        }.message shouldBe "Cannot convert NumberValue to String"
    }
})
