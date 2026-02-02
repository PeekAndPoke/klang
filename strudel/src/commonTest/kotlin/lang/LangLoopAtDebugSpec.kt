package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.peekandpoke.klang.strudel.StrudelPattern

/**
 * Debug test for loopAt() to understand the expected behavior
 */
class LangLoopAtDebugSpec : StringSpec({

    "loopAt(2) debug - print all events" {
        val subject = StrudelPattern.compile("""s("bd").loopAt(2)""")!!

        println("=== loopAt(2) Events ===")
        repeat(4) { cycle ->
            val cycleDbl = cycle.toDouble()
            val events = subject.queryArc(cycleDbl, cycleDbl + 1)

            println("\nCycle $cycle:")
            events.forEachIndexed { index, event ->
                println(
                    "  $index: " +
                            "part=[${event.part.begin.toDouble()}, ${event.part.end.toDouble()}) " +
                            "whole=${event.whole?.let { "[${it.begin.toDouble()}, ${it.end.toDouble()})" } ?: "null"} " +
                            "hasOnset=${event.hasOnset()} " +
                            "speed=${event.data.speed}"
                )
            }
        }
    }
})
