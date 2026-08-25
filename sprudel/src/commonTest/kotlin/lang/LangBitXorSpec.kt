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

class LangBitXorSpec : StringSpec({
    "bitXor() calculates bitwise XOR" {
        val p = seq("3 5").bitXor("1") // 3=011, 5=101. 1=001. 3^1=2, 5^1=4
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 2
        events[1].data.value?.asInt shouldBe 4
    }

    "bitXor() works as top-level PatternMapper" {
        val p = seq("3 5").apply(bitXor("1"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 2
        events[1].data.value?.asInt shouldBe 4
    }

    "bitXor() works as string extension" {
        val p = "3 5".bitXor("1")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 2
        events[1].data.value?.asInt shouldBe 4
    }

    "bitXor dsl interface" {
        val pat = "12 10"
        val ctrl = "6 3"

        dslInterfaceTests(
            "pattern.bitXor(ctrl)" to
                    seq(pat).bitXor(ctrl),
            "script pattern.bitXor(ctrl)" to
                    SprudelPattern.compile("""seq("$pat").bitXor("$ctrl")"""),
            "string.bitXor(ctrl)" to
                    pat.bitXor(ctrl),
            "script string.bitXor(ctrl)" to
                    SprudelPattern.compile(""""$pat".bitXor("$ctrl")"""),
            "bitXor(ctrl)" to
                    seq(pat).apply(bitXor(ctrl)),
            "script bitXor(ctrl)" to
                    SprudelPattern.compile("""seq("$pat").apply(bitXor("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.value?.asInt shouldBe 10  // 12 ^ 6 = 10
            events[1].data.value?.asInt shouldBe 9   // 10 ^ 3 = 9
        }
    }
})
