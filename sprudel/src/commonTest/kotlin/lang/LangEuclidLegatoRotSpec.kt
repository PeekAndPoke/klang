package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.EPSILON
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangEuclidLegatoRotSpec : StringSpec({

    "euclidLegatoRot dsl interface" {
        val pat = "hh"
        dslInterfaceTests(
            "pattern.euclidLegatoRot(3, 8, 1)" to s(pat).euclidLegatoRot(3, 8, 1),
            "script pattern.euclidLegatoRot(3, 8, 1)" to SprudelPattern.compile("""s("$pat").euclidLegatoRot(3, 8, 1)"""),
            "string.euclidLegatoRot(3, 8, 1)" to pat.euclidLegatoRot(3, 8, 1),
            "script string.euclidLegatoRot(3, 8, 1)" to SprudelPattern.compile(""""$pat".euclidLegatoRot(3, 8, 1)"""),
            "euclidLegatoRot(3, 8, 1)" to s(pat).apply(euclidLegatoRot(3, 8, 1)),
            "script euclidLegatoRot(3, 8, 1)" to SprudelPattern.compile("""s("$pat").apply(euclidLegatoRot(3, 8, 1))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events.size shouldBe 4  // 3 onsets + 1 non-onset (wrapped tail from previous cycle)
        }
    }

    "euclidLegatoRot(3, 8, 1) rotates and fills gaps" {
        // Base (3,8): 10010010. Indices: 0, 3, 6.
        // Legato durations unrotated:
        // 0 -> 3: dist 3 (0.375) -> 0.0 to 0.375
        // 3 -> 6: dist 3 (0.375) -> 0.375 to 0.75
        // 6 -> 8: dist 2 (0.25)  -> 0.75 to 1.0

        // Rotated by 1 (late 0.125):
        // Event 1: 0.125 to 0.5
        // Event 2: 0.5 to 0.875
        // Event 3: 0.875 to 1.125 (Crosses boundary!)

        // Resulting events in 0..1 cycle:
        // 1. 0.0   to 0.125 (Wrapped tail of Event 3 from previous cycle)
        // 2. 0.125 to 0.5   (Event 1)
        // 3. 0.5   to 0.875 (Event 2)
        // 4. 0.875 to 1.0   (Head of Event 3)

        val p = note("a").euclidLegatoRot(3, 8, 1)
        val allEvents = p.queryArc(0.0, 1.0)
        val events = allEvents.filter { it.isOnset }

        events.size shouldBe 3

        // Wrapped part
        events[0].whole.begin.toDouble() shouldBe (1.0 / 8.0 plusOrMinus EPSILON)
        events[0].whole.end.toDouble() shouldBe (1.0 / 2.0 plusOrMinus EPSILON)

        // Event 1
        events[1].whole.begin.toDouble() shouldBe (1.0 / 2.0 plusOrMinus EPSILON)
        events[1].whole.end.toDouble() shouldBe (7.0 / 8.0 plusOrMinus EPSILON)

        // Event 2
        events[2].whole.begin.toDouble() shouldBe (7.0 / 8.0 plusOrMinus EPSILON)
        events[2].whole.end.toDouble() shouldBe (9.0 / 8.0 plusOrMinus EPSILON)
    }

    "euclidLegatoRot works as top-level function" {
        val p = euclidLegatoRot(3, 8, 1, note("a"))
        val allEvents = p.queryArc(0.0, 1.0)
        val events = allEvents.filter { it.isOnset }

        events.size shouldBe 3
    }

    "euclidLegatoRot works as string extension" {
        val p = "a".euclidLegatoRot(3, 8, 1)
        val allEvents = p.queryArc(0.0, 1.0)
        val events = allEvents.filter { it.isOnset }

        events.size shouldBe 3
    }
})
