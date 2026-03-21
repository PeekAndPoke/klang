package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern.Companion.compile

class LangIterCycleSpec : FunSpec({
    test("iter(2) cycles after 2 cycles") {
        val result = compile("""seq("a b").iter(2)""")!!

        // Query 4 cycles to verify cycling
        val events = result.queryArc(0.0, 4.0)

        // Cycle 0: early(0/2) - no shift
        // Cycle 1: early(1/2) - shifted by 0.5
        // Cycle 2: early(0/2) - back to no shift ← cycles!
        // Cycle 3: early(1/2) - shifted again

        // We should get events in all 4 cycles
        events shouldHaveSize 8 // 2 events per cycle

        // Check that cycles 0 and 2 have the same timing (both no shift)
        val cycle0Events =
            events.filter { it.part.begin.toDouble() < 1.0 }

        val cycle2Events =
            events.filter { it.part.begin.toDouble() >= 2.0 && it.part.begin.toDouble() < 3.0 }

        // Shift timing should match
        cycle0Events[0].part.begin.toDouble() shouldBe cycle2Events[0].part.begin.toDouble() - 2.0
        cycle0Events[1].part.begin.toDouble() shouldBe cycle2Events[1].part.begin.toDouble() - 2.0
    }

    test("iterBack(2) cycles after 2 cycles") {
        val result = compile("""seq("a b").iterBack(2)""")!!

        val events = result.queryArc(0.0, 4.0)

        // Should cycle through late(0) and late(0.5) repeatedly
        events shouldHaveSize 8

        // Check that cycles 0 and 2 have the same timing
        val cycle0Events =
            events.filter { it.part.begin.toDouble() < 1.0 }

        val cycle2Events =
            events.filter { it.part.begin.toDouble() >= 2.0 && it.part.begin.toDouble() < 3.0 }

        // Timing should repeat
        cycle0Events[0].part.begin.toDouble() shouldBe cycle2Events[0].part.begin.toDouble() - 2.0
        cycle0Events[1].part.begin.toDouble() shouldBe cycle2Events[1].part.begin.toDouble() - 2.0
    }

    test("iter(3) cycles after 3 cycles") {
        val result = compile("""seq("a b c").iter(3)""")!!

        // Query 6 cycles to verify it repeats
        val events = result.queryArc(0.0, 6.0)

        // The pattern should repeat every 3 cycles
        val cycle0to2 = events.filter { it.part.begin.toDouble() < 3.0 }
        val cycle3to5 = events.filter { it.part.begin.toDouble() >= 3.0 }

        // Should have same number of events
        cycle0to2.size shouldBe cycle3to5.size

        // Verify timing pattern repeats (shifted by 3 cycles)
        for (i in cycle0to2.indices) {
            cycle0to2[i].part.begin.toDouble() shouldBe
                    ((cycle3to5[i].part.begin.toDouble() - 3.0) plusOrMinus EPSILON)
        }
    }

    test("iter(2) with struct cycles the binary pattern") {
        val result = compile("""seq("a b").iter(2)""")!!

        // Check that the pattern repeats after 2 cycles
        val cycle0 = result.queryArc(0.0, 1.0)
        val cycle2 = result.queryArc(2.0, 3.0)

        // Should have events in both cycles
        cycle0 shouldHaveSize 2
        cycle2 shouldHaveSize 2

        // The relative timing should be the same (modulo the 2-cycle offset)
        val c0Timings = cycle0.map { it.part.begin.toDouble() }
        val c2Timings = cycle2.map { it.part.begin.toDouble() - 2.0 }

        c0Timings shouldBe c2Timings
    }
})
