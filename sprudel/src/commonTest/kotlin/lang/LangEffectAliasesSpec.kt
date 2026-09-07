/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern

class LangEffectAliasesSpec : StringSpec({

    // -- orbit alias ------------------------------------------------------------------------------------------------------

    "orbit() alias 'o' sets VoiceData.orbit correctly" {
        val p = note("c3").o("1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.cylinder shouldBe 1
    }

    "orbit() alias 'o' works as top-level function" {
        val p = note("c3").apply(o("2"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.cylinder shouldBe 2
    }

    "orbit() alias 'o' works as string extension" {
        val p = "c3".o("3")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.cylinder shouldBe 3
    }

    "orbit() alias 'o' works with control patterns" {
        val p = note("c3 e3").o("0 1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.cylinder shouldBe 0
        events[1].data.cylinder shouldBe 1
    }

    "orbit() alias 'o' works in compiled code" {
        val p = SprudelPattern.compile("""note("c3").o(1)""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 1
        events[0].data.cylinder shouldBe 1
    }
})
