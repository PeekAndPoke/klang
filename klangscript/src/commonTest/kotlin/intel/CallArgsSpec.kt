/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.script.intel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/** A whole-call tool's argument list as text (KLANG-00374): read into one text per parameter, written back. */
class CallArgsSpec : StringSpec({

    val params = listOf("attack", "decay", "sustain", "release")

    "positional texts take their places; named ones (all named) theirs; an unknown name and an overflow are dropped" {
        alignArgsToParams(params, listOf("0.01", "0.2")) shouldBe listOf("0.01", "0.2", null, null)
        alignArgsToParams(params, listOf("sustain = 0.5", "attack=0.01", "nope = 1")) shouldBe listOf("0.01", null, "0.5", null)
        alignArgsToParams(listOf("a"), listOf("1", "2")) shouldBe listOf("1")
        alignArgsToParams(params, listOf("x => x", "a == b")) shouldBe listOf("x => x", "a == b", null, null)
    }

    "written back: a filled start positional, a gap makes all named, nothing written for nothing; blanks are gaps" {
        serializeCallArgs(params, listOf("0.01", "0.2", null, null)) shouldBe "0.01, 0.2"
        serializeCallArgs(params, listOf(null, "0.3", null, " ")) shouldBe "decay = 0.3"
        serializeCallArgs(params, listOf("0.01", null, "0.5", null)) shouldBe "attack = 0.01, sustain = 0.5"
        serializeCallArgs(params, listOf(null, null, null, null)) shouldBe ""
    }
})
