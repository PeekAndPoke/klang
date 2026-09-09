/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.runtime

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.common.SourceLocation

/**
 * [sourceLocationOf] is the single probe the KSP-generated registration code and the native
 * builders use to find where a receiver or an argument was written. Only the two literal-backed
 * runtime values carry one.
 *
 * The number branch was invisible to every suite in the repository when this probe was still
 * pasted into the generated text, so it gets an assertion of its own here.
 */
class SourceLocationOfSpec : StringSpec({

    val loc = SourceLocation(source = "test", startLine = 3, startColumn = 5, endLine = 3, endColumn = 9)

    "a string literal reports its own location" {
        sourceLocationOf(StringValue("bd", location = loc)) shouldBe loc
    }

    "a number literal reports its own location" {
        sourceLocationOf(NumberValue(0.5, location = loc)) shouldBe loc
    }

    "a literal that carries no location reports none" {
        sourceLocationOf(StringValue("bd")) shouldBe null
        sourceLocationOf(NumberValue(0.5)) shouldBe null
    }

    "a computed value has nowhere to point" {
        sourceLocationOf(BooleanValue(true)) shouldBe null
        sourceLocationOf(NullValue) shouldBe null
        sourceLocationOf(ArrayValue(mutableListOf())) shouldBe null
    }

    "a value that is not a runtime value at all reports none" {
        sourceLocationOf(null) shouldBe null
        sourceLocationOf("bd") shouldBe null
        sourceLocationOf(0.5) shouldBe null
    }
})
