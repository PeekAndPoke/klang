package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON

class LangEuclidLegatoSpec : StringSpec({

    "euclidLegato(3, 8) fills gaps" {
        // Standard 3,8: 10010010. Indices: 0, 3, 6.
        // Legato durations:
        // 0 -> 3 (distance 3 steps = 3/8 = 0.375)
        // 3 -> 6 (distance 3 steps = 3/8 = 0.375)
        // 6 -> 8/0 (distance 2 steps = 2/8 = 0.25)

        val p = note("a").euclidLegato(3, 8)
        val events = p.queryArc(0.0, 1.0)

        events.forEach { println("${it.part.begin} -> ${it.part.duration}") }

        events.size shouldBe 3

        events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].part.duration.toDouble() shouldBe (0.375 plusOrMinus EPSILON)

        events[1].part.begin.toDouble() shouldBe (0.375 plusOrMinus EPSILON)
        events[1].part.duration.toDouble() shouldBe (0.375 plusOrMinus EPSILON)

        events[2].part.begin.toDouble() shouldBe (0.75 plusOrMinus EPSILON)
        events[2].part.duration.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
    }

    "euclidLegato works as top-level function" {
        val p = euclidLegato(3, 8, note("a"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 3
    }

    "euclidLegato works as string extension" {
        val p = "a".euclidLegato(3, 8)
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 3
    }
})
