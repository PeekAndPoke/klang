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

class LangBitShlSpec : StringSpec({
    "bitShl() calculates bitwise left shift" {
        val p = seq("1 2").bitShl("1")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 2 // 1<<1 = 2
        events[1].data.value?.asInt shouldBe 4 // 2<<1 = 4
    }

    "bitShl() works as top-level PatternMapper" {
        val p = seq("1 2").apply(bitShl("1"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 2
        events[1].data.value?.asInt shouldBe 4
    }

    "bitShl() works as string extension" {
        val p = "1 2".bitShl("1")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 2
        events[1].data.value?.asInt shouldBe 4
    }

    "bitShl dsl interface" {
        val pat = "1 2"
        val ctrl = "2 3"

        dslInterfaceTests(
            "pattern.bitShl(ctrl)" to
                    seq(pat).bitShl(ctrl),
            "script pattern.bitShl(ctrl)" to
                    SprudelPattern.compile("""seq("$pat").bitShl("$ctrl")"""),
            "string.bitShl(ctrl)" to
                    pat.bitShl(ctrl),
            "script string.bitShl(ctrl)" to
                    SprudelPattern.compile(""""$pat".bitShl("$ctrl")"""),
            "bitShl(ctrl)" to
                    seq(pat).apply(bitShl(ctrl)),
            "script bitShl(ctrl)" to
                    SprudelPattern.compile("""seq("$pat").apply(bitShl("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.value?.asInt shouldBe 4   // 1 << 2 = 4
            events[1].data.value?.asInt shouldBe 16  // 2 << 3 = 16
        }
    }
})
