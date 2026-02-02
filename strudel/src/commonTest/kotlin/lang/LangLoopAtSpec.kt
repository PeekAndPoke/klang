package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON

class LangLoopAtSpec : StringSpec({

    "loopAt() sets unit to 'c'" {
        val p = s("bd").loopAt(2)
        val allEvents = p.queryArc(0.0, 2.0)
        allEvents.size shouldBe 2

        val events = allEvents.filter { it.isOnset }
        events.size shouldBe 1

        // Check that unit is set to 'c'
        events[0].data.unit shouldBe "c"

        // Check that speed is set correctly: 1 / (2 * factor) = 1 / (2 * 2) = 0.25
        events[0].data.speed shouldBe 0.25

        // Check that it's slowed by factor 2
        events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].part.end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        events[0].whole.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].whole.end.toDouble() shouldBe (2.0 plusOrMinus EPSILON)
    }

    "loopAt(0.5) sets unit to 'c'" {
        val p = s("bd").loopAt(0.5)
        val events = p.queryArc(0.0, 0.5)

        events.size shouldBe 1

        // Check that unit is set to 'c'
        events[0].data.unit shouldBe "c"

        // Speed: 1 / (2 * 0.5) = 1.0
        events[0].data.speed shouldBe 1.0

        // Slowed by factor 0.5
        events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].part.end.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
    }

    "loopAt(1) sets unit to 'c'" {
        val p = s("bd").loopAt(1)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1

        // Check that unit is set to 'c'
        events[0].data.unit shouldBe "c"

        // Speed: 1 / (2 * 1) = 0.5
        events[0].data.speed shouldBe 0.5

        // No time stretching with factor 1
        events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].part.end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }

    "loopAt(4) sets unit to 'c'" {
        val p = s("bd").loopAt(4)
        val allEvents = p.queryArc(0.0, 4.0)
        allEvents.size shouldBe 4

        val events = allEvents.filter { it.isOnset }
        events.size shouldBe 1

        // Check that unit is set to 'c'
        events[0].data.unit shouldBe "c"

        // Speed: 1 / (2 * 4) = 0.125
        events[0].data.speed shouldBe 0.125

        // Slowed by factor 4
        events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].part.end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        events[0].whole.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].whole.end.toDouble() shouldBe (4.0 plusOrMinus EPSILON)
    }
})
