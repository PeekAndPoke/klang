package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangLoopAtCpsSpec : StringSpec({

    "loopAtCps() with default cps" {
        // Default cps = 0.5
        val p = s("bd").loopAtCps(2, 0.5)
        val allEvents = p.queryArc(0.0, 2.0)
        allEvents.size shouldBe 2

        val events = allEvents.filter { it.isOnset }
        events.size shouldBe 1

        // Check that it's slowed by factor 2
        events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].part.end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        events[0].whole.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].whole.end.toDouble() shouldBe (2.0 plusOrMinus EPSILON)

        // Check that speed is set to (1/2) * 0.5 = 0.25
        events[0].data.speed shouldBe 0.25

        // Check that unit is set to 'c'
        events[0].data.unit shouldBe "c"
    }

    "loopAtCps() with custom cps" {
        // cps = 1.0
        val p = s("bd").loopAtCps(4, 1.0)
        val allEvents = p.queryArc(0.0, 4.0)
        allEvents.size shouldBe 4

        val events = allEvents.filter { it.isOnset }
        events.size shouldBe 1

        // Check that it's slowed by factor 4
        events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].part.end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        events[0].whole.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].whole.end.toDouble() shouldBe (4.0 plusOrMinus EPSILON)

        // Check that speed is set to (1/4) * 1.0 = 0.25
        events[0].data.speed shouldBe 0.25

        // Check that unit is set to 'c'
        events[0].data.unit shouldBe "c"
    }

    "loopAtCps() with factor 1" {
        val p = s("bd").loopAtCps(1, 0.5)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1

        // No time stretching with factor 1
        events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].part.end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)

        // Speed = (1/1) * 0.5 = 0.5
        events[0].data.speed shouldBe 0.5

        // Unit is 'c'
        events[0].data.unit shouldBe "c"
    }

    "loopatcps() lowercase alias works" {
        val p = s("bd").loopatcps(2, 1.0)
        val allEvents = p.queryArc(0.0, 2.0)
        allEvents.size shouldBe 2

        val events = allEvents.filter { it.isOnset }
        events.size shouldBe 1

        events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].part.end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        events[0].whole.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].whole.end.toDouble() shouldBe (2.0 plusOrMinus EPSILON)

        // Speed = (1/2) * 1.0 = 0.5
        events[0].data.speed shouldBe 0.5
        events[0].data.unit shouldBe "c"
    }

    "loopAtCps() preserves other data" {
        val p = s("bd").gain(0.8).loopAtCps(2, 0.5)
        val allEvents = p.queryArc(0.0, 2.0)
        allEvents.size shouldBe 2

        val events = allEvents.filter { it.isOnset }
        events.size shouldBe 1

        // Gain should be preserved
        events[0].data.gain shouldBe 0.8

        // Sound should be preserved
        events[0].data.sound shouldBe "bd"

        // Speed and unit are set
        events[0].data.speed shouldBe 0.25
        events[0].data.unit shouldBe "c"
    }

    // Compile tests

    "loopAtCps() with compile - default cps" {
        val p = StrudelPattern.compile("""s("bd").loopAtCps(2, 0.5)""")!!
        val allEvents = p.queryArc(0.0, 2.0)
        allEvents.size shouldBe 2

        val events = allEvents.filter { it.isOnset }
        events.size shouldBe 1

        events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].part.end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        events[0].whole.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].whole.end.toDouble() shouldBe (2.0 plusOrMinus EPSILON)
        events[0].data.speed shouldBe 0.25
        events[0].data.unit shouldBe "c"
    }

    "loopAtCps() with compile - custom cps" {
        val p = StrudelPattern.compile("""s("bd").loopAtCps(4, 1.0)""")!!
        val allEvents = p.queryArc(0.0, 4.0)
        allEvents.size shouldBe 4

        val events = allEvents.filter { it.isOnset }
        events.size shouldBe 1

        events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].part.end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        events[0].whole.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].whole.end.toDouble() shouldBe (4.0 plusOrMinus EPSILON)
        events[0].data.speed shouldBe 0.25
        events[0].data.unit shouldBe "c"
    }

    "loopatcps() with compile - lowercase alias" {
        val p = StrudelPattern.compile("""s("bd").loopatcps(2, 1.0)""")!!
        val events = p.queryArc(0.0, 2.0)

        events.size shouldBe 2
    }
})
