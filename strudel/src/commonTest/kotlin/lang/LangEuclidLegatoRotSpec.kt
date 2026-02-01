package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON

class LangEuclidLegatoRotSpec : StringSpec({

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
        val events = p.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        events.size shouldBe 4

        // Wrapped part
        events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].part.end.toDouble() shouldBe (0.125 plusOrMinus EPSILON)

        // Event 1
        events[1].part.begin.toDouble() shouldBe (0.125 plusOrMinus EPSILON)
        events[1].part.end.toDouble() shouldBe (0.5 plusOrMinus EPSILON)

        // Event 2
        events[2].part.begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
        events[2].part.end.toDouble() shouldBe (0.875 plusOrMinus EPSILON)

        // Event 3 (head)
        events[3].part.begin.toDouble() shouldBe (0.875 plusOrMinus EPSILON)
        events[3].part.end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }

    "euclidLegatoRot works as top-level function" {
        val p = euclidLegatoRot(3, 8, 1, note("a"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 4
    }

    "euclidLegatoRot works as string extension" {
        val p = "a".euclidLegatoRot(3, 8, 1)
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 4
    }
})
