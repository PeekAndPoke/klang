package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class LangRepeatCyclesSpec : StringSpec({

    "repeatCycles() repeats each cycle n times" {
        // For a static pattern like note("c"), repeatCycles acts as identity
        val staticPattern = note("c").repeatCycles(2)

        // Should continue producing events (not stop after 2 cycles)
        val cycle0 = staticPattern.queryArc(0.0, 1.0)
        cycle0 shouldHaveSize 1

        val cycle1 = staticPattern.queryArc(1.0, 2.0)
        cycle1 shouldHaveSize 1

        val cycle2 = staticPattern.queryArc(2.0, 3.0)
        cycle2 shouldHaveSize 1 // Continues indefinitely for static patterns

        val cycle10 = staticPattern.queryArc(10.0, 11.0)
        cycle10 shouldHaveSize 1 // Still producing events
    }

    "repeatCycles(2) with cycle-varying pattern over 10 cycles" {
        // <[a b] [c d]> alternates: cycle 0=[a,b], cycle 1=[c,d], cycle 2=[a,b], etc.
        // With repeatCycles(2), each cycle should repeat twice:
        // cycles 0,1=[a,b], cycles 2,3=[c,d], cycles 4,5=[a,b], etc.
        val pattern = note("<[a b] [c d]>").repeatCycles(2)

        // Check each cycle from 0 to 9
        for (cycle in 0..128) {
            val events = pattern.queryArc(cycle.toDouble(), (cycle + 1).toDouble())

            println("\n=== Cycle $cycle ===")
            events.forEachIndexed { idx, event ->
                val noteValue = event.data.note ?: "null"
                println("Event $idx: note=$noteValue, begin=${event.part.begin}, end=${event.part.end}")
            }

            // Each cycle should have 2 notes
            events shouldHaveSize 2

            // Determine expected notes based on cycle number
            // Source cycle = floor(cycle / 2), which alternates: 0,0,1,1,2,2,3,3,4,4
            // Source pattern alternates: cycle 0=[a,b], cycle 1=[c,d], cycle 2=[a,b]...
            val sourceCycle = cycle / 2
            val expectedNotes = if (sourceCycle % 2 == 0) listOf("a", "b") else listOf("c", "d")

            // Check the note values
            events[0].data.note shouldBe expectedNotes[0]
            events[1].data.note shouldBe expectedNotes[1]

            // Check timing within the cycle
            events[0].part.begin.toDouble() shouldBe cycle.toDouble()
            events[0].part.end.toDouble() shouldBe cycle + 0.5

            events[1].part.begin.toDouble() shouldBe cycle + 0.5
            events[1].part.end.toDouble() shouldBe cycle + 1.0
        }
    }
})
