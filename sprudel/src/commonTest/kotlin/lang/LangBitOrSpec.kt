/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangBitOrSpec : StringSpec({
    "bitOr() calculates bitwise OR" {
        val p = seq("1 4").bitOr("2") // 1=001, 4=100. 2=010. 1|2=3, 4|2=6
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 3
        events[1].data.value?.asInt shouldBe 6
    }

    "bitOr() works as top-level PatternMapper" {
        val p = seq("1 4").apply(bitOr("2"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 3
        events[1].data.value?.asInt shouldBe 6
    }

    "bitOr() works as string extension" {
        val p = "1 4".bitOr("2")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 3
        events[1].data.value?.asInt shouldBe 6
    }

    "bitOr dsl interface" {
        val pat = "8 4"
        val ctrl = "2 3"

        dslInterfaceTests(
            "pattern.bitOr(ctrl)" to
                    seq(pat).bitOr(ctrl),
            "script pattern.bitOr(ctrl)" to
                    SprudelPattern.compile("""seq("$pat").bitOr("$ctrl")"""),
            "string.bitOr(ctrl)" to
                    pat.bitOr(ctrl),
            "script string.bitOr(ctrl)" to
                    SprudelPattern.compile(""""$pat".bitOr("$ctrl")"""),
            "bitOr(ctrl)" to
                    seq(pat).apply(bitOr(ctrl)),
            "script bitOr(ctrl)" to
                    SprudelPattern.compile("""seq("$pat").apply(bitOr("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.value?.asInt shouldBe 10  // 8 | 2 = 10
            events[1].data.value?.asInt shouldBe 7   // 4 | 3 = 7
        }
    }
})
