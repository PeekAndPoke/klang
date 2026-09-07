/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern

class LangPanchorSpec : StringSpec({

    "penv(anchor = ...) sets VoiceData.pAnchor correctly" {
        val p = note("a b").penv(anchor = "0.5 1.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.pAnchor } shouldBe listOf(0.5, 1.0)
    }

    "control pattern penv(anchor = ...) sets VoiceData.pAnchor on existing pattern" {
        val base = note("c3 e3")
        val p = base.penv(anchor = "0.1 0.2")
        val events = p.queryArc(0.0, 2.0)

        events.size shouldBe 4
        events.map { it.data.pAnchor } shouldBe listOf(0.1, 0.2, 0.1, 0.2)
    }

    "penv(anchor = ...) works as string extension" {
        val p = "c3".penv(anchor = "0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c3"
        events[0].data.pAnchor shouldBe 0.5
    }

    "penv(anchor = ...) works within compiled code" {
        val p = SprudelPattern.compile("""note("a b").penv(anchor = "0.5 1.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.pAnchor } shouldBe listOf(0.5, 1.0)
    }

})
