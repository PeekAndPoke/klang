package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON

class LangEuclidishSpec : StringSpec({

    "euclidish(3, 8, 0) behaves like euclid(3, 8)" {
        // groove 0 -> standard euclidean
        // 3,8 -> 10010010 -> indices 0, 3, 6 -> times 0.0, 0.375, 0.75
        val p = note("a").euclidish(pulses = 3, steps = 8, groove = 0.0)
        val events = p.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        events.size shouldBe 3
        events[0].part.begin.toDouble() shouldBe 0.0
        events[1].part.begin.toDouble() shouldBe 0.375
        events[2].part.begin.toDouble() shouldBe 0.75
    }

    "euclidish(3, 8, 1) behaves like steady pulses" {
        // groove 1 -> evenly distributed pulses
        // 3 pulses -> 0, 1/3, 2/3
        val p = note("a").euclidish(pulses = 3, steps = 8, groove = 1.0)
        val events = p.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        events.size shouldBe 3
        events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[1].part.begin.toDouble() shouldBe (1.0 / 3.0 plusOrMinus EPSILON)
        events[2].part.begin.toDouble() shouldBe (2.0 / 3.0 plusOrMinus EPSILON)
    }

    "euclidish morphs positions with pattern groove" {
        // groove <0 1> -> first cycle euclidean, second cycle steady
        val p = note("a").euclidish(3, 8, groove = "<0 1>")

        // Cycle 1: Euclidean
        val events1 = p.queryArc(0.0, 1.0).sortedBy { it.part.begin }
        events1.size shouldBe 3
        events1[1].part.begin.toDouble() shouldBe 0.375

        // Cycle 2: Steady
        val events2 = p.queryArc(1.0, 2.0).sortedBy { it.part.begin }
        events2.size shouldBe 3
        events2[1].part.begin.toDouble() shouldBe (1.0 + 1.0 / 3.0 plusOrMinus EPSILON)
    }

    "euclidish works as top-level function" {
        val p = euclidish(pulses = 3, steps = 8, groove = 0.0, pattern = note("a"))
        p.queryArc(0.0, 1.0).size shouldBe 3
    }

    "euclidish works as string extension" {
        val p = "a".euclidish(pulses = 3, steps = 8, groove = 0.0)
        p.queryArc(0.0, 1.0).size shouldBe 3
    }

    "eish alias works" {
        val p = "a".eish(pulses = 3, steps = 8, groove = 0.0)
        p.queryArc(0.0, 1.0).size shouldBe 3
    }
})
