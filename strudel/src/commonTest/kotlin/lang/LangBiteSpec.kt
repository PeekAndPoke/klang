package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LangBiteSpec : StringSpec({

    "bite(4, '0 1 2 3') reconstructs the original pattern" {
        // Slicing into 4 and playing 0, 1, 2, 3 in order should be identity
        val p = n("0 1 2 3").bite(4, "0 1 2 3")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        events.map { it.data.soundIndex } shouldBe listOf(0, 1, 2, 3)
    }

    "bite(4, '3 2 1 0') reverses the pattern" {
        val p = n("0 1 2 3").bite(4, "3 2 1 0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        events.map { it.data.soundIndex } shouldBe listOf(3, 2, 1, 0)
    }

    "bite works with single pattern index" {
        // indices: <0 1>
        val p = n("10 20").bite(2, seq("0"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.soundIndex shouldBe 10
    }

    "bite works with pattern indices" {
        // indices: <0 1>
        val p = n("10 20").bite(2, seq("0 1"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.soundIndex shouldBe 10
        events[1].data.soundIndex shouldBe 20
    }

    "bite handles wrapping indices" {
        // Index 2 on a 2-slice pattern should wrap to 0
        val p = n("10 20").bite(2, "2")
        val allEvents = p.queryArc(0.0, 1.0)
        val events = allEvents.filter { it.isOnset }

        events.size shouldBe 1
        events[0].data.soundIndex shouldBe 10
    }

    "bite handles negative indices" {
        // Index -1 on a 2-slice pattern should wrap to 1 (last element)
        val p = n("10 20").bite(2, "-1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.soundIndex shouldBe 20
    }

    "bite works as string extension" {
        val p = "0 1 2 3".bite(4, "3 2 1 0")
        val events = p.queryArc(0.0, 1.0)
        events.map { it.data.value?.asInt } shouldBe listOf(3, 2, 1, 0)
    }

    "bite() with pattern control for n" {
        // n varies between 2 and 4 slices
        val p = n("0 1 2 3").bite("2 4", "0 1")
        val events = p.queryArc(0.0, 1.0)

        // Should have events from pattern-controlled slicing
        events.isNotEmpty() shouldBe true
    }

    "bite() with steady pattern for n produces same result as static" {
        val p1 = n("0 1 2 3").bite(4, "0 1 2 3")
        val p2 = n("0 1 2 3").bite(steady(4), "0 1 2 3")

        val events1 = p1.queryArc(0.0, 1.0)
        val events2 = p2.queryArc(0.0, 1.0)

        events1.size shouldBe events2.size
        events1.zip(events2).forEach { (e1, e2) ->
            e1.data.soundIndex shouldBe e2.data.soundIndex
        }
    }

    "bite() with continuous pattern control" {
        // Use a continuous pattern for n
        val p = n("0 1 2 3").bite(sine.range(2, 4).segment(2), "0 1")
        val events = p.queryArc(0.0, 1.0)

        // Should have events with varying slice counts
        events.isNotEmpty() shouldBe true
    }
})
