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

class LangBitShrSpec : StringSpec({
    "bitShr() calculates bitwise right shift" {
        val p = seq("2 4").bitShr("1")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 1 // 2>>1 = 1
        events[1].data.value?.asInt shouldBe 2 // 4>>1 = 2
    }

    "bitShr() works as top-level PatternMapper" {
        val p = seq("8 12").apply(bitShr("2"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 2  // 8>>2 = 2
        events[1].data.value?.asInt shouldBe 3  // 12>>2 = 3
    }

    "bitShr() works as string extension" {
        val p = "2 4".bitShr("1")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 1
        events[1].data.value?.asInt shouldBe 2
    }

    "bitShr dsl interface" {
        val pat = "8 16"
        val ctrl = "2 3"

        dslInterfaceTests(
            "pattern.bitShr(ctrl)" to
                    seq(pat).bitShr(ctrl),
            "script pattern.bitShr(ctrl)" to
                    SprudelPattern.compile("""seq("$pat").bitShr("$ctrl")"""),
            "string.bitShr(ctrl)" to
                    pat.bitShr(ctrl),
            "script string.bitShr(ctrl)" to
                    SprudelPattern.compile(""""$pat".bitShr("$ctrl")"""),
            "bitShr(ctrl)" to
                    seq(pat).apply(bitShr(ctrl)),
            "script bitShr(ctrl)" to
                    SprudelPattern.compile("""seq("$pat").apply(bitShr("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.value?.asInt shouldBe 2  // 8 >> 2 = 2
            events[1].data.value?.asInt shouldBe 2  // 16 >> 3 = 2
        }
    }
})
