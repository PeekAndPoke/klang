package io.peekandpoke.klang.strudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.lang.*
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

class SequencePatternWeightSpec : StringSpec({

    "Debug bd@2 hh sd@2 hh pattern across 1000 cycles" {
        val pattern = note("bd@2 hh sd@2 hh")

        // Expected relative durations within a single cycle:
        // Total weight = 2 + 1 + 2 + 1 = 6
        // bd: 2/6 = 1/3
        // hh: 1/6
        // sd: 2/6 = 1/3
        // hh: 1/6
        val expectedNotes = listOf("bd", "hh", "sd", "hh")
        val expectedDurations = listOf(1.0 / 3.0, 1.0 / 6.0, 1.0 / 3.0, 1.0 / 6.0)

        // Query multiple cycles and verify no overlaps and correct timing
        for (cycle in 0 until 1000) {
            val from = cycle.toRational()
            val to = from + 1.0
            val events = pattern.queryArc(from, to).sortedBy { it.begin }

            events.size shouldBe 4

            var lastEnd = from

            events.forEachIndexed { index, event ->
                event.data.note shouldBe expectedNotes[index]
                event.begin.toDouble() shouldBe (lastEnd.toDouble() plusOrMinus EPSILON)
                event.dur.toDouble() shouldBe (expectedDurations[index] plusOrMinus EPSILON)
                lastEnd = event.end
            }

            lastEnd.toDouble() shouldBe (to.toDouble() plusOrMinus EPSILON)
        }
    }

    "Pattern with equal weights: a b c" {
        val pattern = note("a b c")
        val expectedNotes = listOf("a", "b", "c")
        val expectedDur = 1.0 / 3.0

        for (cycle in 0 until 100) {
            val from = cycle.toRational()
            val to = from + 1.0
            val events = pattern.queryArc(from, to).sortedBy { it.begin }

            events.size shouldBe 3
            var lastEnd = from

            events.forEachIndexed { i, event ->
                event.data.note shouldBe expectedNotes[i]
                event.begin.toDouble() shouldBe (lastEnd.toDouble() plusOrMinus EPSILON)
                event.dur.toDouble() shouldBe (expectedDur plusOrMinus EPSILON)
                lastEnd = event.end
            }
            lastEnd.toDouble() shouldBe (to.toDouble() plusOrMinus EPSILON)
        }
    }

    "Weighted group: [a b]@3 c" {
        val pattern = note("[a b]@3 c")
        // Total weight = 3 + 1 = 4.
        // Group [a b] takes 3/4. Inside group, a=1/2, b=1/2.
        val expectedNotes = listOf("a", "b", "c")
        val expectedDurs = listOf(3.0 / 8.0, 3.0 / 8.0, 2.0 / 8.0)

        for (cycle in 0 until 100) {
            val from = cycle.toRational()
            val to = from + 1.0
            val events = pattern.queryArc(from, to).sortedBy { it.begin }

            events.size shouldBe 3
            var lastEnd = from

            events.forEachIndexed { i, event ->
                event.data.note shouldBe expectedNotes[i]
                event.begin.toDouble() shouldBe (lastEnd.toDouble() plusOrMinus EPSILON)
                event.dur.toDouble() shouldBe (expectedDurs[i] plusOrMinus EPSILON)
                lastEnd = event.end
            }
            lastEnd.toDouble() shouldBe (to.toDouble() plusOrMinus EPSILON)
        }
    }

    "Fractional weights: a@1.5 b@0.5" {
        val pattern = note("a@1.5 b@0.5")
        // Total weight = 2.0. a=0.75, b=0.25
        val expectedNotes = listOf("a", "b")
        val expectedDurs = listOf(0.75, 0.25)

        for (cycle in 0 until 100) {
            val from = cycle.toRational()
            val to = from + 1.0
            val events = pattern.queryArc(from, to).sortedBy { it.begin }

            events.size shouldBe 2
            var lastEnd = from

            events.forEachIndexed { i, event ->
                event.data.note shouldBe expectedNotes[i]
                event.begin.toDouble() shouldBe (lastEnd.toDouble() plusOrMinus EPSILON)
                event.dur.toDouble() shouldBe (expectedDurs[i] plusOrMinus EPSILON)
                lastEnd = event.end
            }
            lastEnd.toDouble() shouldBe (to.toDouble() plusOrMinus EPSILON)
        }
    }

    "Nested weights: a@2 [b c]@2" {
        val pattern = note("a@2 [b c]@2")
        // Total weight = 4. a=0.5, [b c]=0.5 -> b=0.25, c=0.25
        val expectedNotes = listOf("a", "b", "c")
        val expectedDurs = listOf(0.5, 0.25, 0.25)

        for (cycle in 0 until 100) {
            val from = cycle.toRational()
            val to = from + 1.0
            val events = pattern.queryArc(from, to).sortedBy { it.begin }

            events.size shouldBe 3
            var lastEnd = from

            events.forEachIndexed { i, event ->
                event.data.note shouldBe expectedNotes[i]
                event.begin.toDouble() shouldBe (lastEnd.toDouble() plusOrMinus EPSILON)
                event.dur.toDouble() shouldBe (expectedDurs[i] plusOrMinus EPSILON)
                lastEnd = event.end
            }
            lastEnd.toDouble() shouldBe (to.toDouble() plusOrMinus EPSILON)
        }
    }

    "Stack with independent weights" {
        val pattern = stack(
            note("a@3 b"),
            note("c d")
        )

        for (cycle in 0 until 100) {
            val from = cycle.toRational()
            val to = from + 1.0
            val events = pattern.queryArc(from, to).sortedBy { it.begin }

            events.size shouldBe 4 // a, b, c, d

            val a = events.find { it.data.note == "a" }!!
            val b = events.find { it.data.note == "b" }!!
            val c = events.find { it.data.note == "c" }!!
            val d = events.find { it.data.note == "d" }!!

            // Layer 1 (a@3 b -> 3/4, 1/4)
            a.dur.toDouble() shouldBe (0.75 plusOrMinus EPSILON)
            b.dur.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
            b.begin.toDouble() shouldBe (a.end.toDouble() plusOrMinus EPSILON)

            // Layer 2 (c d -> 1/2, 1/2)
            c.dur.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
            d.dur.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
            d.begin.toDouble() shouldBe (c.end.toDouble() plusOrMinus EPSILON)

            (a.begin % 1.0).toDouble() shouldBe (0.0 plusOrMinus EPSILON)
            (c.begin % 1.0).toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        }
    }

    "Weighted pattern wrapped in ControlPattern (gain): a@3 b" {
        val pattern = note("a@3 b").gain(0.5)

        for (cycle in 0 until 100) {
            val from = cycle.toDouble()
            val to = from + 1.0
            val events = pattern.queryArc(from, to).sortedBy { it.begin }

            events.size shouldBe 2
            val a = events[0]
            val b = events[1]

            a.data.note shouldBe "a"
            a.dur.toDouble() shouldBe (0.75 plusOrMinus EPSILON)
            a.data.gain shouldBe (0.5 plusOrMinus EPSILON)

            b.data.note shouldBe "b"
            b.dur.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
            b.data.gain shouldBe (0.5 plusOrMinus EPSILON)
        }
    }

    "Weighted pattern wrapped in TimeModifier (slow): a@3 b" {
        val pattern = note("a@3 b").slow(2)

        for (startCycle in 0 until 100 step 2) {
            val from = startCycle.toDouble()
            val to = from + 2.0
            val events = pattern.queryArc(from, to).sortedBy { it.begin }

            events.size shouldBe 2
            val a = events[0]
            val b = events[1]

            a.data.note shouldBe "a"
            a.begin.toDouble() shouldBe (from plusOrMinus EPSILON)
            a.dur.toDouble() shouldBe (1.5 plusOrMinus EPSILON)

            b.data.note shouldBe "b"
            b.begin.toDouble() shouldBe (from + 1.5 plusOrMinus EPSILON)
            b.dur.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
        }
    }

    "Weighted pattern wrapped in TimeModifier (fast): a@3 b" {
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

            a1.data.note shouldBe "a"
            a1.dur.toDouble() shouldBe (0.375 plusOrMinus EPSILON)
            b1.data.note shouldBe "b"
            b1.dur.toDouble() shouldBe (0.125 plusOrMinus EPSILON)

            a2.data.note shouldBe "a"
            a2.dur.toDouble() shouldBe (0.375 plusOrMinus EPSILON)
            b2.data.note shouldBe "b"
            b2.dur.toDouble() shouldBe (0.125 plusOrMinus EPSILON)
        }
    }

    "Complex nesting: (a@3 b).slow(2).gain(0.5)" {
        val pattern = note("a@3 b").slow(2).gain(0.5)

        for (startCycle in 0 until 100 step 2) {
            val from = startCycle.toDouble()
            val to = from + 2.0
            val events = pattern.queryArc(from, to).sortedBy { it.begin }

            events.size shouldBe 2
            val a = events[0]
            val b = events[1]

            a.data.note shouldBe "a"
            a.dur.toDouble() shouldBe (1.5 plusOrMinus EPSILON)
            a.data.gain shouldBe (0.5 plusOrMinus EPSILON)

            b.data.note shouldBe "b"
            b.dur.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
            b.data.gain shouldBe (0.5 plusOrMinus EPSILON)
        }
    }

    "Query partial cycle: a@3 b (from 0.0 to 0.5)" {
        val pattern = note("a@3 b")

        for (cycle in 0 until 100) {
            val from = cycle.toDouble()
            val to = from + 0.5
            val events = pattern.queryArc(from, to).sortedBy { it.begin }

            events.size shouldBe 1
            val a = events[0]

            a.data.note shouldBe "a"
            a.begin.toDouble() shouldBe (from plusOrMinus EPSILON)
            a.dur.toDouble() shouldBe (0.75 plusOrMinus EPSILON)
        }
    }
})
