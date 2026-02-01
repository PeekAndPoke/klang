package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LangSegmentSpec : StringSpec({

    "segment(n) samples a continuous pattern n times per cycle" {
        // sine is continuous. segment(4) should create 4 discrete events per cycle
        val p = sine.segment(4)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4

        // Check timing
        events[0].part.begin.toDouble() shouldBe 0.0
        events[0].part.end.toDouble() shouldBe 0.25

        events[3].part.begin.toDouble() shouldBe 0.75
        events[3].part.end.toDouble() shouldBe 1.0
    }

    "segment(n) works on discrete patterns" {
        // seq("0") is one event (0..1). segment(4) should chop it into 4
        val p = seq("0").segment(4)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        events.forEach {
            it.data.value?.asInt shouldBe 0
        }
    }

    "seg() alias works" {
        val p = seq("0").seg(2)
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 0
    }

    "segment works as string extension" {
        val p = "0".segment(2)
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 0
    }

    "seg() alias works as string extension" {
        val p = "0".seg(2)
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 0
    }

    "segment() with discrete pattern control" {
        // segment("2 4") with control pattern "2 4" which has 2 events: [0..0.5]=2, [0.5..1]=4
        // SegmentPatternWithControl divides each control event timespan into n slices:
        // - First half [0..0.5]: 2 slices = 2 events
        // - Second half [0.5..1]: 4 slices = 4 events
        // Total: 6 events

        val p = sine.segment("2 4")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 6

        // First half should have 2 segments (each 0.25 duration)
        events[0].part.begin.toDouble() shouldBe 0.0
        events[0].part.end.toDouble() shouldBe 0.25
        events[1].part.begin.toDouble() shouldBe 0.25
        events[1].part.end.toDouble() shouldBe 0.5

        // Second half should have 4 segments (each 0.125 duration)
        events[2].part.begin.toDouble() shouldBe 0.5
        events[2].part.end.toDouble() shouldBe 0.625
        events[5].part.begin.toDouble() shouldBe 0.875
        events[5].part.end.toDouble() shouldBe 1.0
    }

    "segment() with continuous pattern control (sine)" {
        // Use sine as control for segment count
        val p = seq("0").segment(sine.range(2, 4).segment(2))
        val events = p.queryArc(0.0, 1.0)

        // Should have events with varying segment counts
        events.isNotEmpty() shouldBe true
    }

    "segment() with steady pattern produces same result as static value" {
        val p1 = sine.segment(4)
        val p2 = sine.segment(steady(4))

        val events1 = p1.queryArc(0.0, 1.0)
        val events2 = p2.queryArc(0.0, 1.0)

        events1.size shouldBe events2.size
        events1.zip(events2).forEach { (e1, e2) ->
            e1.part.begin shouldBe e2.part.begin
            e1.part.end shouldBe e2.part.end
        }
    }
})
