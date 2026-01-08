package io.peekandpoke.klang.strudel.lang.parser

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.lang.*
import io.peekandpoke.klang.strudel.pattern.EPSILON

class WeightDebugTest : StringSpec({

    "Debug bd@2 hh sd@2 hh pattern across 1000 cycles" {
        val pattern = note("bd@2 hh sd@2 hh")

        // Expected relative durations within a single cycle:
        // Total weight = 2 + 1 + 2 + 1 = 6
        // bd: 2/6 = 1/3
        // hh: 1/6
        // sd: 2/6 = 1/3
        // hh: 1/6
        // Expected notes in order
        val expectedNotes = listOf("bd", "hh", "sd", "hh")
        val expectedDurations = listOf(1.0 / 3.0, 1.0 / 6.0, 1.0 / 3.0, 1.0 / 6.0)

        // Query multiple cycles and verify no overlaps and correct timing
        for (cycle in 0 until 1000) {
            val from = cycle.toDouble()
            val to = from + 1.0
            val events = pattern.queryArc(from, to).sortedBy { it.begin }

            events.size shouldBe 4

            var lastEnd = from

            events.forEachIndexed { index, event ->
                // Check note
                event.data.note shouldBe expectedNotes[index]

                // Check start time (should start exactly where previous ended)
                event.begin shouldBe (lastEnd plusOrMinus EPSILON)

                // Check duration
                event.dur shouldBe (expectedDurations[index] plusOrMinus EPSILON)

                lastEnd = event.end
            }

            // Ensure the last event ends exactly at the end of the cycle
            lastEnd shouldBe (to plusOrMinus EPSILON)
        }
    }

    "Pattern with equal weights: a b c" {
        val pattern = note("a b c")
        // Total weight = 3. Each = 1/3.
        val expectedNotes = listOf("a", "b", "c")
        val expectedDur = 1.0 / 3.0

        for (cycle in 0 until 100) {
            val from = cycle.toDouble()
            val to = from + 1.0
            val events = pattern.queryArc(from, to).sortedBy { it.begin }

            events.size shouldBe 3
            var lastEnd = from

            events.forEachIndexed { i, event ->
                event.data.note shouldBe expectedNotes[i]
                event.begin shouldBe (lastEnd plusOrMinus EPSILON)
                event.dur shouldBe (expectedDur plusOrMinus EPSILON)
                lastEnd = event.end
            }
            lastEnd shouldBe (to plusOrMinus EPSILON)
        }
    }

    "Weighted group: [a b]@3 c" {
        val pattern = note("[a b]@3 c")
        // Total weight = 3 + 1 = 4.
        // Group [a b] takes 3/4. Inside group, a=1/2, b=1/2 of group duration.
        // a: (3/4) * (1/2) = 3/8
        // b: (3/4) * (1/2) = 3/8
        // c: 1/4 = 2/8

        val expectedNotes = listOf("a", "b", "c")
        val expectedDurs = listOf(3.0 / 8.0, 3.0 / 8.0, 2.0 / 8.0)

        for (cycle in 0 until 100) {
            val from = cycle.toDouble()
            val to = from + 1.0
            val events = pattern.queryArc(from, to).sortedBy { it.begin }

            events.size shouldBe 3
            var lastEnd = from

            events.forEachIndexed { i, event ->
                event.data.note shouldBe expectedNotes[i]
                event.begin shouldBe (lastEnd plusOrMinus EPSILON)
                event.dur shouldBe (expectedDurs[i] plusOrMinus EPSILON)
                lastEnd = event.end
            }
            lastEnd shouldBe (to plusOrMinus EPSILON)
        }
    }

    "Fractional weights: a@1.5 b@0.5" {
        val pattern = note("a@1.5 b@0.5")
        // Total weight = 1.5 + 0.5 = 2.0
        // a: 1.5 / 2.0 = 0.75
        // b: 0.5 / 2.0 = 0.25

        val expectedNotes = listOf("a", "b")
        val expectedDurs = listOf(0.75, 0.25)

        for (cycle in 0 until 100) {
            val from = cycle.toDouble()
            val to = from + 1.0
            val events = pattern.queryArc(from, to).sortedBy { it.begin }

            events.size shouldBe 2
            var lastEnd = from

            events.forEachIndexed { i, event ->
                event.data.note shouldBe expectedNotes[i]
                event.begin shouldBe (lastEnd plusOrMinus EPSILON)
                event.dur shouldBe (expectedDurs[i] plusOrMinus EPSILON)
                lastEnd = event.end
            }
            lastEnd shouldBe (to plusOrMinus EPSILON)
        }
    }

    "Nested weights: a@2 [b c]@2" {
        val pattern = note("a@2 [b c]@2")
        // Total weight = 2 + 2 = 4
        // a: 2/4 = 0.5
        // [b c]: 2/4 = 0.5
        //   b: 0.5 * 0.5 = 0.25
        //   c: 0.5 * 0.5 = 0.25

        val expectedNotes = listOf("a", "b", "c")
        val expectedDurs = listOf(0.5, 0.25, 0.25)

        for (cycle in 0 until 100) {
            val from = cycle.toDouble()
            val to = from + 1.0
            val events = pattern.queryArc(from, to).sortedBy { it.begin }

            events.size shouldBe 3
            var lastEnd = from

            events.forEachIndexed { i, event ->
                event.data.note shouldBe expectedNotes[i]
                event.begin shouldBe (lastEnd plusOrMinus EPSILON)
                event.dur shouldBe (expectedDurs[i] plusOrMinus EPSILON)
                lastEnd = event.end
            }
            lastEnd shouldBe (to plusOrMinus EPSILON)
        }
    }

    "Stack with independent weights" {
        // stack(
        //   "a@3 b",  // layer 1: 3+1=4. a=3/4, b=1/4
        //   "c d"     // layer 2: 1+1=2. c=1/2, d=1/2
        // )
        val pattern = stack(
            note("a@3 b"),
            note("c d")
        )

        for (cycle in 0 until 100) {
            val from = cycle.toDouble()
            val to = from + 1.0
            val events = pattern.queryArc(from, to).sortedBy { it.begin }

            events.size shouldBe 4 // a, b, c, d

            // Find specific events by note name
            val a = events.find { it.data.note == "a" }!!
            val b = events.find { it.data.note == "b" }!!
            val c = events.find { it.data.note == "c" }!!
            val d = events.find { it.data.note == "d" }!!

            // Layer 1
            a.dur shouldBe (0.75 plusOrMinus EPSILON)
            b.dur shouldBe (0.25 plusOrMinus EPSILON)
            // b starts after a
            b.begin shouldBe (a.end plusOrMinus EPSILON)

            // Layer 2
            c.dur shouldBe (0.5 plusOrMinus EPSILON)
            d.dur shouldBe (0.5 plusOrMinus EPSILON)
            // d starts after c
            d.begin shouldBe (c.end plusOrMinus EPSILON)

            // Both layers start at beginning of cycle (relative to cycle start)
            (a.begin % 1.0) shouldBe (0.0 plusOrMinus EPSILON)
            (c.begin % 1.0) shouldBe (0.0 plusOrMinus EPSILON)
        }
    }

    "Weighted pattern wrapped in ControlPattern (gain): a@3 b" {
        // "a@3 b" -> a=3/4, b=1/4. Wrapped in .gain(0.5) should preserve weights.
        val pattern = note("a@3 b").gain(0.5)

        for (cycle in 0 until 100) {
            val from = cycle.toDouble()
            val to = from + 1.0
            val events = pattern.queryArc(from, to).sortedBy { it.begin }

            events.size shouldBe 2
            val a = events[0]
            val b = events[1]

            a.data.note shouldBe "a"
            a.dur shouldBe (0.75 plusOrMinus EPSILON)
            a.data.gain shouldBe (0.5 plusOrMinus EPSILON)

            b.data.note shouldBe "b"
            b.dur shouldBe (0.25 plusOrMinus EPSILON)
            b.data.gain shouldBe (0.5 plusOrMinus EPSILON)
        }
    }

    "Weighted pattern wrapped in TimeModifier (slow): a@3 b" {
        // "a@3 b" -> a=3/4, b=1/4. Wrapped in .slow(2).
        // Time is stretched by 2. Total duration becomes 2 cycles.
        // Cycle 0: 'a' starts at 0, duration 1.5 (covers whole cycle 0 and half of cycle 1).
        // But queryArc returns what's in the arc.
        // If we query cycle 0 (0..1): 'a' is present 0..1 (clipped duration).
        // If we query cycle 0..2: 'a' is 0..1.5, 'b' is 1.5..2.0.

        val pattern = note("a@3 b").slow(2)

        // Query 2 cycles at once to see full pattern
        for (startCycle in 0 until 100 step 2) {
            val from = startCycle.toDouble()
            val to = from + 2.0
            val events = pattern.queryArc(from, to).sortedBy { it.begin }

            events.size shouldBe 2
            val a = events[0]
            val b = events[1]

            a.data.note shouldBe "a"
            a.begin shouldBe (from plusOrMinus EPSILON)
            a.dur shouldBe (1.5 plusOrMinus EPSILON) // 0.75 * 2

            b.data.note shouldBe "b"
            b.begin shouldBe (from + 1.5 plusOrMinus EPSILON)
            b.dur shouldBe (0.5 plusOrMinus EPSILON) // 0.25 * 2
        }
    }

    "Weighted pattern wrapped in TimeModifier (fast): a@3 b" {
        // "a@3 b" -> a=3/4, b=1/4. Wrapped in .fast(2).
        // Time compressed by 2. Pattern plays twice in one cycle.
        // Cycle 0 (0..1):
        // 1st iteration (0.0 .. 0.5): a=0.375, b=0.125
        // 2nd iteration (0.5 .. 1.0): a=0.375, b=0.125

        val pattern = note("a@3 b").fast(2)

        for (cycle in 0 until 100) {
            val from = cycle.toDouble()
            val to = from + 1.0
            val events = pattern.queryArc(from, to).sortedBy { it.begin }

            events.size shouldBe 4 // a, b, a, b

            val a1 = events[0]
            val b1 = events[1]
            val a2 = events[2]
            val b2 = events[3]

            // 1st iteration
            a1.data.note shouldBe "a"
            a1.dur shouldBe (0.375 plusOrMinus EPSILON)
            b1.data.note shouldBe "b"
            b1.dur shouldBe (0.125 plusOrMinus EPSILON)

            // 2nd iteration
            a2.data.note shouldBe "a"
            a2.dur shouldBe (0.375 plusOrMinus EPSILON)
            b2.data.note shouldBe "b"
            b2.dur shouldBe (0.125 plusOrMinus EPSILON)
        }
    }

    "Complex nesting: (a@3 b).slow(2).gain(0.5)" {
        // Inner: a=0.75, b=0.25
        // Slow(2): a=1.5, b=0.5
        // Gain(0.5): applied to all

        val pattern = note("a@3 b").slow(2).gain(0.5)

        for (startCycle in 0 until 100 step 2) {
            val from = startCycle.toDouble()
            val to = from + 2.0
            val events = pattern.queryArc(from, to).sortedBy { it.begin }

            events.size shouldBe 2
            val a = events[0]
            val b = events[1]

            a.data.note shouldBe "a"
            a.dur shouldBe (1.5 plusOrMinus EPSILON)
            a.data.gain shouldBe (0.5 plusOrMinus EPSILON)

            b.data.note shouldBe "b"
            b.dur shouldBe (0.5 plusOrMinus EPSILON)
            b.data.gain shouldBe (0.5 plusOrMinus EPSILON)
        }
    }

    "Query partial cycle: a@3 b (from 0.0 to 0.5)" {
        // "a@3 b" -> a=0.75, b=0.25
        // Query 0.0 to 0.5 covers only 'a' (partially).
        // Since 'a' starts at 0.0 and ends at 0.75, it should be included.
        // 'b' starts at 0.75, so it should NOT be included.

        val pattern = note("a@3 b")

        for (cycle in 0 until 100) {
            val from = cycle.toDouble()
            val to = from + 0.5
            val events = pattern.queryArc(from, to).sortedBy { it.begin }

            events.size shouldBe 1
            val a = events[0]

            a.data.note shouldBe "a"
            a.begin shouldBe (from plusOrMinus EPSILON)
            // Note: queryArc typically returns events that *overlap* the arc.
            // The event's duration property describes the event itself, usually not clipped to query.
            a.dur shouldBe (0.75 plusOrMinus EPSILON)
        }
    }
})
