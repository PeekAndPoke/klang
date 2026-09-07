/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.throwables.shouldThrowAny
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.string.shouldContain
import io.peekandpoke.klang.sprudel.SprudelPattern

/**
 * The single envelope doors were removed 2026-09-07: `adsr(attack = ...)` sets a slot and
 * `adsr.attack` reads it (`docs/tasks/sprudel-field-accessors.md`). Neither the member nor the
 * top-level form may dispatch.
 */
class LangRetiredEnvelopeDoorsSpec : StringSpec({
    listOf("attack", "decay", "sustain", "release").forEach { name ->
        "retired door '$name' fails as a member call" {
            val error = shouldThrowAny { SprudelPattern.compile("""note("c4").$name(0.5)""") }
            withClue("error should name the missing method") { (error.message ?: "") shouldContain name }
        }

        "retired door '$name' fails as a top-level call" {
            val error = shouldThrowAny { SprudelPattern.compile("""note("c4").apply($name(0.5))""") }
            withClue("error should name the missing function") { (error.message ?: "") shouldContain name }
        }
    }
})
