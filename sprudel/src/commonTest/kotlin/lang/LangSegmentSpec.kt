/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests
import io.kotest.assertions.withClue
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldNotBeNull
import io.peekandpoke.klang.sprudel.sampleAt

class LangSegmentSpec : StringSpec({

    "segment dsl interface" {
        val pat = "0"
        dslInterfaceTests(
            "pattern.segment(4)" to seq(pat).segment(4),
            "script pattern.segment(4)" to SprudelPattern.compile("""seq("$pat").segment(4)"""),
            "string.segment(4)" to pat.segment(4),
            "script string.segment(4)" to SprudelPattern.compile(""""$pat".segment(4)"""),
            "segment(4)" to seq(pat).apply(segment(4)),
            "script segment(4)" to SprudelPattern.compile("""seq("$pat").apply(segment(4))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events.size shouldBe 4
        }
    }

    "seg dsl interface" {
        val pat = "0"
        dslInterfaceTests(
            "pattern.seg(4)" to seq(pat).seg(4),
            "script pattern.seg(4)" to SprudelPattern.compile("""seq("$pat").seg(4)"""),
            "string.seg(4)" to pat.seg(4),
            "script string.seg(4)" to SprudelPattern.compile(""""$pat".seg(4)"""),
            "seg(4)" to seq(pat).apply(seg(4)),
            "script seg(4)" to SprudelPattern.compile("""seq("$pat").apply(seg(4))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events.size shouldBe 4
        }
    }

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

    // -- Point queries ------------------------------------------------------------------------------------------------

    "a point query answers with the slice containing that point | saw.segment(4).sampleAt(t)" {
        // An atom answers a point query with its whole cycle, so the segmenter must pick the slice
        // that contains the point itself. Before this row, every point query returned the first slice.
        val p = saw.segment(4)
        val ctx = SprudelPattern.QueryContext()

        for (cycle in 0 until 12) {
            for (i in 0 until 4) {
                val t = cycle + i / 4.0
                withClue("t=$t") {
                    val e = p.sampleAt(t, ctx).shouldNotBeNull()
                    e.whole.begin.toCycles() shouldBe t
                    e.whole.end.toCycles() shouldBe t + 0.25
                    e.data.value?.asDouble shouldBe (i / 4.0 plusOrMinus 1e-9)
                }
            }
        }
    }

    "a point query inside a slice answers with that slice's start value | saw.segment(4).sampleAt(0.3)" {
        val p = saw.segment(4)
        val ctx = SprudelPattern.QueryContext()

        for (cycle in 0 until 12) {
            val t = cycle + 0.3
            withClue("t=$t") {
                val e = p.sampleAt(t, ctx).shouldNotBeNull()
                e.whole.begin.toCycles() shouldBe cycle + 0.25
                e.whole.end.toCycles() shouldBe cycle + 0.5
                e.data.value?.asDouble shouldBe (0.25 plusOrMinus 1e-9)
            }
        }
    }

    "a query shorter than a cycle returns only the slices it overlaps | saw.segment(4).queryArc(c, c + 0.25)" {
        // Without the arc check the slices after the arc would come back too, every one an onset.
        val p = saw.segment(4)

        for (cycle in 0 until 12) {
            val events = p.queryArc(cycle.toDouble(), cycle + 0.25)
            withClue("cycle $cycle") {
                events shouldHaveSize 1
                events[0].whole.begin.toCycles() shouldBe cycle.toDouble()
            }
        }
    }

    "segment(n) on a discrete pattern re-births the wholes: n onsets per cycle | \"0\".segment(4).note()" {
        // Strudel's segment is struct(pure(true).fast(n)): the slice is the whole. The KDoc example
        // promises four notes; with the source's whole kept, only the first slice was an onset.
        val p = "0".segment(4).note()

        for (cycle in 0 until 12) {
            val events = p.queryArc(cycle.toDouble(), cycle + 1.0)
            withClue("cycle $cycle") {
                events shouldHaveSize 4
                events.all { it.isOnset } shouldBe true
                events.map { it.whole.begin.toCycles() } shouldBe listOf(0.0, 0.25, 0.5, 0.75).map { cycle + it }
            }
        }
    }

    "segment(n) samples a busier pattern: the notes under the slice starts play | \"c e g a b c d e\".seg(4)" {
        val p = "c e g a b c d e".seg(4).note()

        for (cycle in 0 until 12) {
            val events = p.queryArc(cycle.toDouble(), cycle + 1.0)
            withClue("cycle $cycle") {
                events shouldHaveSize 8
                events.filter { it.isOnset }.map { it.data.note } shouldBe listOf("c", "g", "b", "d")
            }
        }
    }

    "the slices are the steps: numSteps counts them, take() sees them | \"0\".seg(8).take(4)" {
        // With the atom's single step, take(4) computed end = 4 / 1 >= 1 and returned the source untouched.
        "0".seg(8).numSteps shouldBe 8.0
        sine.segment("2 4").numSteps shouldBe 6.0

        val p = "0".seg(8).take(4).note()

        for (cycle in 0 until 12) {
            val events = p.queryArc(cycle.toDouble(), cycle + 1.0)
            withClue("cycle $cycle") {
                events shouldHaveSize 4
                events.map { it.whole.begin.toCycles() } shouldBe listOf(0.0, 0.25, 0.5, 0.75).map { cycle + it }
            }
        }
    }

    "a segmented control reaches every note | note(\"c e g a\").gain(saw.segment(4))" {
        // The setter samples the control at each onset: the second note must see the second slice.
        val p = note("c e g a").gain(saw.segment(4))

        for (cycle in 0 until 12) {
            val events = p.queryArc(cycle.toDouble(), cycle + 1.0)
            withClue("cycle $cycle") {
                events shouldHaveSize 4
                events.map { it.data.gain } shouldBe listOf(0.0, 0.25, 0.5, 0.75)
            }
        }
    }
})
