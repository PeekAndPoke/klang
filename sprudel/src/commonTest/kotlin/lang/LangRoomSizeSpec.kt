/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.EPSILON
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangRoomSizeSpec : StringSpec({

    "room(size = ...) dsl interface" {
        dslInterfaceTests(
            "pattern.room(size = amount)" to note("c").room(size = 4.0),
            "script pattern.room(size = amount)" to SprudelPattern.compile("""note("c").room(size = 4)"""),
            "string.room(size = amount)" to "c".room(size = 4.0),
            "script string.room(size = amount)" to SprudelPattern.compile(""""c".room(size = 4)"""),
            "room(size = amount)" to note("c").apply(room(size = 4.0)),
            "script room(size = amount)" to SprudelPattern.compile("""note("c").apply(room(size = 4))"""),
        ) { _, events -> events.shouldNotBeEmpty() }
    }

    "room(size = ...) sets VoiceData.roomSize" {
        val p = note("a b").room(size = "2.0 4.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.roomSize } shouldBe listOf(2.0, 4.0)
    }

    "room(size = ...) works as pattern extension" {
        val p = note("c").room(size = "2.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.roomSize shouldBe 2.0
    }

    "room(size = ...) works as string extension" {
        val p = "c".room(size = "2.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.roomSize shouldBe 2.0
    }

    "room(size = ...) works in compiled code" {
        val p = SprudelPattern.compile("""note("c").room(size = "2.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.roomSize shouldBe 2.0
    }

    "room(size = ...) with continuous pattern sets roomSize correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").room(size = sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.roomSize shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.roomSize shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.roomSize shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.roomSize shouldBe (0.0 plusOrMinus EPSILON)
    }
})
