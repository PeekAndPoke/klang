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

class LangBitAndSpec : StringSpec({
    "bitAnd() calculates bitwise AND" {
        val p = seq("3 5").bitAnd("1") // 3=11, 5=101. 1=001. 3&1=1, 5&1=1
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 1
        events[1].data.value?.asInt shouldBe 1
    }

    "bitAnd() works as top-level PatternMapper" {
        val p = seq("3 5").apply(bitAnd("1"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 1
        events[1].data.value?.asInt shouldBe 1
    }

    "bitAnd() works as string extension" {
        val p = "3 5".bitAnd("1")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 1
        events[1].data.value?.asInt shouldBe 1
    }

    "bitAnd dsl interface" {
        val pat = "12 15"
        val ctrl = "10 6"

        dslInterfaceTests(
            "pattern.bitAnd(ctrl)" to
                    seq(pat).bitAnd(ctrl),
            "script pattern.bitAnd(ctrl)" to
                    SprudelPattern.compile("""seq("$pat").bitAnd("$ctrl")"""),
            "string.bitAnd(ctrl)" to
                    pat.bitAnd(ctrl),
            "script string.bitAnd(ctrl)" to
                    SprudelPattern.compile(""""$pat".bitAnd("$ctrl")"""),
            "bitAnd(ctrl)" to
                    seq(pat).apply(bitAnd(ctrl)),
            "script bitAnd(ctrl)" to
                    SprudelPattern.compile("""seq("$pat").apply(bitAnd("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.value?.asInt shouldBe 8   // 12 & 10 = 8
            events[1].data.value?.asInt shouldBe 6   // 15 & 6 = 6
        }
    }
})
