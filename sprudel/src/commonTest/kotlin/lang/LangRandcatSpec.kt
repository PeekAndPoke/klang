/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe

class LangRandcatSpec : StringSpec({

    "randcat picks one element per cycle" {
        val p = randcat("a", "b").seed(123)
        p.queryArc(0.0, 1.0).size shouldBe 1
    }

    "randcat works as pattern extension" {
        // seq("a").randcat("b") -> choice between a and b
        val p = seq("a").randcat("b").seed(123)
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.value?.asString shouldBeIn listOf("a", "b")
    }

    "randcat works as string extension" {
        val p = "a".randcat("b").seed(123)
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.value?.asString shouldBeIn listOf("a", "b")
    }
})
