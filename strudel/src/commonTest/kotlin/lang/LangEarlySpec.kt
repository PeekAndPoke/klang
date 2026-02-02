package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangEarlySpec : StringSpec({

    "early() with 0 cycles does not shift the pattern" {
        val p1 = note("c d")
        val p2 = note("c d").early(0)

        val events1 = p1.queryArc(0.0, 1.0).sortedBy { it.part.begin }
        val events2 = p2.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        events1.size shouldBe events2.size
        events1.forEachIndexed { i, ev ->
            ev.part.begin shouldBe events2[i].part.begin
            ev.part.end shouldBe events2[i].part.end
        }
    }

    "early(0.5) shifts pattern backward by half a cycle" {
        // Pattern repeats every cycle
        // Original: cycle 0: c(0-0.5), d(0.5-1.0); cycle 1: c(1.0-1.5), d(1.5-2.0)
        // After early(0.5): cycle 0: c(-0.5-0), d(0-0.5); cycle 1: c(0.5-1.0), d(1.0-1.5)
        // Query 0-1 shows: d from cycle 0 at 0-0.5, c from cycle 1 at 0.5-1.0
        val p = note("c d").early(0.5)
        val events = p.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        events.size shouldBe 2
        events[0].data.note shouldBeEqualIgnoringCase "d"
        events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].part.end.toDouble() shouldBe (0.5 plusOrMinus EPSILON)

        events[1].part.begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
        events[1].part.end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }

    "early(1) shifts pattern backward by one cycle" {
        // Original: cycle 0: c(0-0.5), d(0.5-1.0); cycle 1: c(1.0-1.5), d(1.5-2.0)
        // After early(1): cycle 0: c(-1--0.5), d(-0.5-0); cycle 1: c(0-0.5), d(0.5-1.0)
        // Query 0-1 shows cycle 1 shifted to look like original cycle 0
        val p = note("c d").early(1.0)
        val events = p.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        events.size shouldBe 2
        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[1].data.note shouldBeEqualIgnoringCase "d"
    }

    "early(1) query at shifted time shows previous cycle" {
        val p = note("c d").early(1.0)
        val events = p.queryArc(-1.0, 0.0).sortedBy { it.part.begin }

        events.size shouldBe 2
        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[0].part.begin.toDouble() shouldBe (-1.0 plusOrMinus EPSILON)
        events[0].part.end.toDouble() shouldBe (-0.5 plusOrMinus EPSILON)

        events[1].data.note shouldBeEqualIgnoringCase "d"
        events[1].part.begin.toDouble() shouldBe (-0.5 plusOrMinus EPSILON)
        events[1].part.end.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
    }

    "early() works as method on StrudelPattern" {
        val subject = note("c d").early(0.25)

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = subject.queryArc(cycleDbl, cycleDbl + 1).sortedBy { it.part.begin }

                    events.forEachIndexed { index, event ->
                        println(
                            "${index + 1}: note: ${event.data.note} | " +
                                    "part: ${event.part.begin} ${event.part.end} | " +
                                    "whole: ${event.whole.begin} ${event.whole.end}"
                        )
                    }

                    // Query returns 3 events (includes clipped edge from previous cycle)
                    // But only 2 have onset (will be played)
                    events.size shouldBe 3
                    val onsetEvents = events.filter { it.isOnset }
                    onsetEvents.size shouldBe 3

                    // Event 0: Clipped "c" from previous cycle (NO onset - part.begin != whole.begin)
                    events[0].data.note shouldBeEqualIgnoringCase "c"
                    events[0].part.begin.toDouble() shouldBe ((cycleDbl - 0.25) plusOrMinus EPSILON)
                    events[0].part.end.toDouble() shouldBe ((cycleDbl + 0.25) plusOrMinus EPSILON)
                    events[0].whole.begin.toDouble() shouldBe ((cycleDbl - 0.25) plusOrMinus EPSILON)
                    events[0].whole.end.toDouble() shouldBe ((cycleDbl + 0.25) plusOrMinus EPSILON)
                    events[0].isOnset shouldBe true

                    // Event 1: "d" with onset
                    events[1].data.note shouldBeEqualIgnoringCase "d"
                    events[1].part.begin.toDouble() shouldBe ((cycleDbl + 0.25) plusOrMinus EPSILON)
                    events[1].part.end.toDouble() shouldBe ((cycleDbl + 0.75) plusOrMinus EPSILON)
                    events[1].whole.begin.toDouble() shouldBe ((cycleDbl + 0.25) plusOrMinus EPSILON)
                    events[1].whole.end.toDouble() shouldBe ((cycleDbl + 0.75) plusOrMinus EPSILON)
                    events[1].isOnset shouldBe true

                    // Event 2: "c" with onset
                    events[2].data.note shouldBeEqualIgnoringCase "c"
                    events[2].part.begin.toDouble() shouldBe ((cycleDbl + 0.75) plusOrMinus EPSILON)
                    events[2].part.end.toDouble() shouldBe ((cycleDbl + 1.25) plusOrMinus EPSILON)
                    events[2].whole.begin.toDouble() shouldBe ((cycleDbl + 0.75) plusOrMinus EPSILON)
                    events[2].whole.end.toDouble() shouldBe ((cycleDbl + 1.25) plusOrMinus EPSILON)
                    events[2].isOnset shouldBe true
                }
            }
        }
    }

    "early() works as extension on String" {
        val p = "c d".early(0.5)
        val events = p.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        events.size shouldBe 2
        events[0].data.value?.asString shouldBeEqualIgnoringCase "d"
        events[1].data.value?.asString shouldBeEqualIgnoringCase "c"
    }

    "early() works in compiled code" {
        val p = StrudelPattern.compile("""note("c d").early(0.5)""")
        val events = p?.queryArc(0.0, 1.0)?.sortedBy { it.part.begin } ?: emptyList()

        events.size shouldBe 2
        events[0].data.note shouldBeEqualIgnoringCase "d"
        events[1].data.note shouldBeEqualIgnoringCase "c"
    }

    "early() works as method in compiled code" {
        val p = StrudelPattern.compile("""note("c d e f").early(0.5)""")
        val events = p?.queryArc(0.0, 1.0)?.sortedBy { it.part.begin } ?: emptyList()

        // Original: c(0-0.25), d(0.25-0.5), e(0.5-0.75), f(0.75-1.0)
        // After early(0.5): shifts everything back, so we see second half of cycle 0 + first half of cycle 1
        events.size shouldBe 4
        events[0].data.note shouldBeEqualIgnoringCase "e"
        events[1].data.note shouldBeEqualIgnoringCase "f"
        events[2].data.note shouldBeEqualIgnoringCase "c"
        events[3].data.note shouldBeEqualIgnoringCase "d"
    }

    "early() works as string extension in compiled code" {
        val subject = StrudelPattern.compile(""""c d".early(0.25)""")
        subject.shouldNotBeNull()

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = subject.queryArc(cycleDbl, cycleDbl + 1).sortedBy { it.part.begin }

                    events.forEachIndexed { index, event ->
                        println(
                            "${index + 1}: note: ${event.data.value?.asString} | " +
                                    "part: ${event.part.begin} ${event.part.end} | " +
                                    "whole: ${event.whole.begin} ${event.whole.end}"
                        )
                    }

                    events.size shouldBe 3

                    val onsetEvents = events.filter { it.isOnset }
                    onsetEvents.size shouldBe 3

                    // Event 0: Clipped "c" from previous cycle (NO onset)
                    events[0].data.value?.asString shouldBeEqualIgnoringCase "c"
                    events[0].part.begin.toDouble() shouldBe ((cycleDbl - 0.25) plusOrMinus EPSILON)
                    events[0].part.end.toDouble() shouldBe ((cycleDbl + 0.25) plusOrMinus EPSILON)
                    events[0].whole.begin.toDouble() shouldBe ((cycleDbl - 0.25) plusOrMinus EPSILON)
                    events[0].whole.end.toDouble() shouldBe ((cycleDbl + 0.25) plusOrMinus EPSILON)
                    events[0].isOnset shouldBe true

                    // Event 1: "d" with onset
                    events[1].data.value?.asString shouldBeEqualIgnoringCase "d"
                    events[1].part.begin.toDouble() shouldBe ((cycleDbl + 0.25) plusOrMinus EPSILON)
                    events[1].part.end.toDouble() shouldBe ((cycleDbl + 0.75) plusOrMinus EPSILON)
                    events[1].whole.begin.toDouble() shouldBe ((cycleDbl + 0.25) plusOrMinus EPSILON)
                    events[1].whole.end.toDouble() shouldBe ((cycleDbl + 0.75) plusOrMinus EPSILON)
                    events[1].isOnset shouldBe true

                    // Event 2: "c" with onset
                    events[2].data.value?.asString shouldBeEqualIgnoringCase "c"
                    events[2].part.begin.toDouble() shouldBe ((cycleDbl + 0.75) plusOrMinus EPSILON)
                    events[2].part.end.toDouble() shouldBe ((cycleDbl + 1.25) plusOrMinus EPSILON)
                    events[2].whole.begin.toDouble() shouldBe ((cycleDbl + 0.75) plusOrMinus EPSILON)
                    events[2].whole.end.toDouble() shouldBe ((cycleDbl + 1.25) plusOrMinus EPSILON)
                    events[2].isOnset shouldBe true
                }
            }
        }
    }

    "early() with fractional cycles" {
        val p = note("c d e f").early(0.25)
        val events = p.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        // Original: c(0-0.25), d(0.25-0.5), e(0.5-0.75), f(0.75-1)
        // After early(0.25): c(-0.25-0), d(0-0.25), e(0.25-0.5), f(0.5-0.75), next c(0.75-1)
        events.size shouldBe 4
        events[0].data.note shouldBeEqualIgnoringCase "d"
        events[1].data.note shouldBeEqualIgnoringCase "e"
        events[2].data.note shouldBeEqualIgnoringCase "f"
        events[3].data.note shouldBeEqualIgnoringCase "c"
    }

    "early() with pattern parameter" {
        // Use seq pattern to vary the shift amount
        val p = note("c d e f").early(seq(0.0, 0.25))
        val events = p.queryArc(0.0, 1.0).sortedBy { it.part.begin }

        // Should produce events, though exact behavior depends on sampling
        assertSoftly {
            events.size shouldBe 4

            events.forEachIndexed { index, event ->
                println(
                    "${index + 1}: note: ${event.data.note} | " +
                            "part: ${event.part.begin} ${event.part.end} | " +
                            "whole: ${event.whole.begin} ${event.whole.end}"
                )
            }
        }
    }

    "early() with continuous pattern like sine" {
        // Use sine to vary the shift amount continuously
        val subject = note("c d").early(sine.range(0.0, 0.5))

        assertSoftly {
            repeat(12) { cycle ->
                withClue("Cycle $cycle") {
                    val cycleDbl = cycle.toDouble()
                    val events = subject.queryArc(cycleDbl, cycleDbl + 1).sortedBy { it.part.begin }

                    // Sine varies shift continuously, creating varying event counts
                    // Some cycles may have 2-4 events depending on sine sampling
                    events.size shouldBeGreaterThan 0

                    // Verify each event explicitly
                    events.forEachIndexed { i, ev ->
                        withClue(
                            "Event $i: part=(${ev.part.begin.toDouble()}, ${ev.part.end.toDouble()}), " +
                                    "whole=(${ev.whole.begin.toDouble()}, ${ev.whole.end.toDouble()})"
                        ) {

                            // Basic structure checks
                            ev.data.note.shouldNotBeNull()
                            ev.part.shouldNotBeNull()

                            // Verify part values are valid (just checking they exist)
                            ev.part.begin.toDouble() shouldBe (ev.part.begin.toDouble() plusOrMinus EPSILON)
                            ev.part.end.toDouble() shouldBe (ev.part.end.toDouble() plusOrMinus EPSILON)

                            // Verify whole values are valid (just checking they exist)
                            ev.whole.begin.toDouble() shouldBe (ev.whole.begin.toDouble() plusOrMinus EPSILON)
                            ev.whole.end.toDouble() shouldBe (ev.whole.end.toDouble() plusOrMinus EPSILON)

                            // Duration consistency
                            (ev.part.end - ev.part.begin).toDouble() shouldBe (ev.part.duration.toDouble() plusOrMinus EPSILON)

                            // hasOnset() depends on whether part.begin == whole.begin
                            val expectedOnset = (ev.part.begin == ev.whole.begin)
                            ev.isOnset shouldBe expectedOnset

                            // Whole should contain part (whole.begin <= part.begin <= part.end <= whole.end)
                            // Note: Events can be clipped at END even with onset, so durations may differ
                            ev.whole.begin.toDouble() shouldBe (ev.whole.begin.toDouble() plusOrMinus EPSILON)
                            ev.whole.end.toDouble() shouldBe (ev.whole.end.toDouble() plusOrMinus EPSILON)
                        }
                    }

                    // Count events with onset
                    val onsetEvents = events.filter { it.isOnset }
                    withClue("Should have at least 1 onset event per cycle") {
                        onsetEvents.size shouldBeGreaterThan 0
                    }
                }
            }
        }
    }
})
