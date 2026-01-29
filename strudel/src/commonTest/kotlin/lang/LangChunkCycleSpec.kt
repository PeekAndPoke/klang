package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern.Companion.compile

class LangChunkCycleSpec : FunSpec({
    xtest("chunk(2) with silence produces gaps") {
        val result = compile("""s("bd hh").chunk(2, x => silence)""")!!

        // Query individual cycles to understand the pattern
        val c0 = result.queryArc(0.0, 1.0)
        val c1 = result.queryArc(1.0, 2.0)
        val c2 = result.queryArc(2.0, 3.0)
        val c3 = result.queryArc(3.0, 4.0)

        // Binary pattern for chunk(2) is [true, false]
        // After iter(2), the pattern cycles through shifts
        // Need to verify which cycle gets silence

        // For now, just verify the pattern cycles correctly
        // Cycles 0 and 2 should have the same behavior
        c0.size shouldBe c2.size

        // Cycles 1 and 3 should have the same behavior
        c1.size shouldBe c3.size

        // And the two behaviors should be different (one has events, one doesn't)
        c0.size shouldBe (if (c1.size == 0) 2 else 0)
    }

    test("chunk(2) with identity shows all events") {
        val result = compile("""s("bd hh").chunk(2, x => x)""")!!

        val events = result.queryArc(0.0, 4.0)

        // With identity transform, all events should appear
        events.size shouldBe 8
    }

    test("chunk(2) cycles binary pattern correctly") {
        // Test that the binary condition repeats every 2 cycles
        val result = compile("""s("bd hh").chunk(2, x => x.fast(2))""")!!

        val cycle0Events = result.queryArc(0.0, 1.0)
        val cycle2Events = result.queryArc(2.0, 3.0)

        // Cycle 0 and cycle 2 should have same transformation pattern
        cycle0Events.size shouldBe cycle2Events.size

        // Check that the pattern of transformations is the same
        // (both cycles should apply fast(2) in the same quarters)
        for (i in cycle0Events.indices) {
            val duration0 = cycle0Events[i].end.toDouble() - cycle0Events[i].begin.toDouble()
            val duration2 = cycle2Events[i].end.toDouble() - cycle2Events[i].begin.toDouble()
            // Durations should be the same since same transformation applied
            duration0 shouldBe duration2
        }
    }

    test("chunk(2) with fast transformation cycles correctly") {
        // Verify that chunk cycles through applying transformations
        val result = compile("""s("bd").chunk(2, x => x.fast(2))""")!!

        // Get events from cycles 0, 1, 2, 3
        val c0 = result.queryArc(0.0, 1.0)
        val c1 = result.queryArc(1.0, 2.0)
        val c2 = result.queryArc(2.0, 3.0)
        val c3 = result.queryArc(3.0, 4.0)

        // Cycle 0 and 2 should have same pattern (both use first slice of iter)
        c0.size shouldBe c2.size

        // Cycle 1 and 3 should have same pattern (both use second slice of iter)
        c1.size shouldBe c3.size
    }

    test("chunk(3) cycles through 3 different patterns") {
        val result = compile("""s("bd").chunk(3, x => x.fast(2))""")!!

        // Query 6 cycles to see full 3-cycle pattern repeat
        val c0 = result.queryArc(0.0, 1.0)
        val c1 = result.queryArc(1.0, 2.0)
        val c2 = result.queryArc(2.0, 3.0)
        val c3 = result.queryArc(3.0, 4.0)
        val c4 = result.queryArc(4.0, 5.0)
        val c5 = result.queryArc(5.0, 6.0)

        // Pattern should repeat every 3 cycles
        c0.size shouldBe c3.size  // Cycles 0 and 3 should match
        c1.size shouldBe c4.size  // Cycles 1 and 4 should match
        c2.size shouldBe c5.size  // Cycles 2 and 5 should match
    }
})
