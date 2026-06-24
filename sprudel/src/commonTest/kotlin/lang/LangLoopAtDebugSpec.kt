/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.peekandpoke.klang.sprudel.SprudelPattern

/**
 * Debug test for loopAt() to understand the expected behavior
 */
class LangLoopAtDebugSpec : StringSpec({

    "loopAt(2) debug - print all events" {
        val subject = SprudelPattern.compile("""s("bd").loopAt(2)""")!!

        println("=== loopAt(2) Events ===")
        repeat(4) { cycle ->
            val cycleDbl = cycle.toDouble()
            val events = subject.queryArc(cycleDbl, cycleDbl + 1)

            println("\nCycle $cycle:")
            events.forEachIndexed { index, event ->
                println(
                    "  $index: " +
                            "part=[${event.part.begin.toDouble()}, ${event.part.end.toDouble()}) " +
                            "whole=${event.whole.let { "[${it.begin.toDouble()}, ${it.end.toDouble()})" }} " +
                            "hasOnset=${event.isOnset} " +
                            "speed=${event.data.speed}"
                )
            }
        }
    }
})
