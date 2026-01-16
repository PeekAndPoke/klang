package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LangZoomSpec : StringSpec({

    "zoom() selects a portion of the pattern" {
        // "0 1 2 3" -> 0 at 0.0-0.25, 1 at 0.25-0.5, 2 at 0.5-0.75, 3 at 0.75-1.0
        // zoom(0.5, 1.0) selects "2 3" (the second half) and stretches it to fill the cycle 0.0-1.0
        val p = n("0 1 2 3").zoom(0.5, 1.0)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2

        // First event should be "2", stretched to fill 0.0-0.5
        with(events[0]) {
            data.soundIndex shouldBe 2
            begin.toDouble() shouldBe 0.0
            end.toDouble() shouldBe 0.5
        }

        // Second event should be "3", stretched to fill 0.5-1.0
        with(events[1]) {
            data.soundIndex shouldBe 3
            begin.toDouble() shouldBe 0.5
            end.toDouble() shouldBe 1.0
        }
    }

    "zoom() works with string extension" {
        val p = "0 1 2 3".zoon(start = 0.0, end = 0.5)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 0
        events[1].data.value?.asInt shouldBe 1
    }

    "zoom() returns silence if start >= end" {
        val p = n("0 1").zoom(0.5, 0.5)
        p.queryArc(0.0, 1.0).size shouldBe 0
    }

    "zoom() works with smaller slices" {
        // Select 25% of the pattern (the "1") and stretch it to fill 100%
        val p = n("0 1 2 3").zoom(0.25, 0.5)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0]) {
            data.soundIndex shouldBe 1
            begin.toDouble() shouldBe 0.0
            end.toDouble() shouldBe 1.0
        }
    }

    "zoom() with discrete pattern control for start" {
        // Use pattern control for start parameter
        val p = n("0 1 2 3").zoom("0 0.5", 1.0)
        val events = p.queryArc(0.0, 1.0)

        // Should have events from both zoom windows
        events.isNotEmpty() shouldBe true
    }

    "zoom() with discrete pattern control for end" {
        // Use pattern control for end parameter
        val p = n("0 1 2 3").zoom(0.0, "0.5 1.0")
        val events = p.queryArc(0.0, 1.0)

        // Should have events from both zoom windows
        events.isNotEmpty() shouldBe true
    }

    "zoom() with pattern control for both start and end" {
        // Use pattern control for both parameters
        val p = n("0 1 2 3").zoom("0 0.25", "0.5 0.75")
        val events = p.queryArc(0.0, 1.0)

        // Should have events from pattern-controlled zoom
        events.isNotEmpty() shouldBe true
    }

    "zoom() with steady pattern produces same result as static value" {
        val p1 = n("0 1 2 3").zoom(0.25, 0.75)
        val p2 = n("0 1 2 3").zoom(steady(0.25), steady(0.75))

        val events1 = p1.queryArc(0.0, 1.0)
        val events2 = p2.queryArc(0.0, 1.0)

        events1.size shouldBe events2.size
        events1.zip(events2).forEach { (e1, e2) ->
            e1.data.soundIndex shouldBe e2.data.soundIndex
        }
    }
})
