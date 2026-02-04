package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec

class LangLingerDebugSpec : StringSpec({

    "debug linger(0.5)" {
        println("\n=== Testing linger(0.5) ===")
        val subject = s("bd sd ht lt").linger(0.5)

        // Query cycle 0
        val events = subject.queryArc(0.0, 1.0).filter { it.isOnset }

        println("Number of events: ${events.size}")
        events.forEachIndexed { index, event ->
            println("Event $index: sound=${event.data.sound}, part=[${event.part.begin}, ${event.part.end}], whole=[${event.whole.begin}, ${event.whole.end}]")
        }

        println("\nExpected: 4 events (bd, sd, bd, sd) each 0.25 cycles")
        println("Actual: ${events.size} events")
    }

    "debug zoom(0, 0.5) alone" {
        println("\n=== Testing zoom(0, 0.5) alone ===")
        val subject = s("bd sd ht lt").zoom(0.0, 0.5)

        val events = subject.queryArc(0.0, 1.0).filter { it.isOnset }

        println("Number of events: ${events.size}")
        events.forEachIndexed { index, event ->
            println("Event $index: sound=${event.data.sound}, part=[${event.part.begin}, ${event.part.end}]")
        }
    }

    "debug zoom(0, 0.5).slow(0.5)" {
        println("\n=== Testing zoom(0, 0.5).slow(0.5) ===")
        val subject = s("bd sd ht lt").zoom(0.0, 0.5).slow(0.5)

        val events = subject.queryArc(0.0, 1.0).filter { it.isOnset }

        println("Number of events: ${events.size}")
        events.forEachIndexed { index, event ->
            println("Event $index: sound=${event.data.sound}, part=[${event.part.begin}, ${event.part.end}]")
        }
    }
})
