package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangFocusSpec : StringSpec({

    "focus() compresses pattern into first half of cycle" {
        val p = note("c d e f").focus("0", "0.5")
        val events = p.queryArc(0.0, 1.0)

        // focus(0, 0.5) compresses the entire pattern into [0, 0.5)
        // Using early(0).fast(2).late(0) transformation
        events.size shouldBe 8  // 2 full cycles of 4 notes each

        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].end.toDouble() shouldBe (0.125 plusOrMinus EPSILON)

        events[1].data.note shouldBeEqualIgnoringCase "d"
        events[1].begin.toDouble() shouldBe (0.125 plusOrMinus EPSILON)
        events[1].end.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
    }

    "focus() compresses pattern into second half of cycle" {
        val p = note("c d e f").focus("0.5", "1")
        val events = p.queryArc(0.0, 1.0)

        // Pattern is compressed 2x, creating 2 cycles in [0, 1)
        events.size shouldBe 8

        // The "focused" events in [0.5, 1) range
        events[4].data.note shouldBeEqualIgnoringCase "c"
        events[4].begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
        events[4].end.toDouble() shouldBe (0.625 plusOrMinus EPSILON)

        events[5].data.note shouldBeEqualIgnoringCase "d"
        events[5].begin.toDouble() shouldBe (0.625 plusOrMinus EPSILON)
        events[5].end.toDouble() shouldBe (0.75 plusOrMinus EPSILON)
    }

    "focus() compresses pattern into middle of cycle" {
        val p = note("c d e f").focus("0.25", "0.75")
        val events = p.queryArc(0.0, 1.0)

        // Pattern is compressed 2x and produces 8 events across [0, 1)
        events.size shouldBe 8

        // Check first event (wraps around from previous cycle)
        events[0].data.note shouldBeEqualIgnoringCase "e"
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].end.toDouble() shouldBe (0.125 plusOrMinus EPSILON)

        // The focused range [0.25, 0.75) contains a full cycle starting with c
        events[2].data.note shouldBeEqualIgnoringCase "c"
        events[2].begin.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
    }

    "focus() compresses pattern with gaps before and after" {
        val p = note("c d e f").focus("0.25", "0.75")

        // Query before focused region - has wrap-around events
        val beforeEvents = p.queryArc(0.0, 0.25)
        beforeEvents.size shouldBe 2  // Wrapped events from the end of pattern

        // Query focused region - should have compressed pattern
        val duringEvents = p.queryArc(0.25, 0.75)
        duringEvents.size shouldBe 4  // One full cycle in the focused range

        // Query after focused region - has wrap-around events
        val afterEvents = p.queryArc(0.75, 1.0)
        afterEvents.size shouldBe 2  // Wrapped events from the beginning of next pattern
    }

    "focus() works across multiple cycles" {
        val p = note("c d e f").focus("0.25", "0.75")
        val events = p.queryArc(0.0, 2.0)

        // Pattern is compressed, 8 events per cycle
        events.size shouldBe 16

        // Events repeat the same pattern each cycle
        val firstCycle = events.take(8)
        val secondCycle = events.drop(8)

        firstCycle[0].data.note shouldBeEqualIgnoringCase "e"
        firstCycle[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)

        secondCycle[0].data.note shouldBeEqualIgnoringCase "e"
        secondCycle[0].begin.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }

    "focus() works with very small range" {
        val p = note("c d e f").focus("0.4", "0.6")
        val events = p.queryArc(0.0, 1.0)

        // Compressed into 0.2 of the cycle (factor = 1/0.2 = 5)
        // Pattern is 5x faster, producing 20 events in [0, 1)
        events.size shouldBe 20  // 5 cycles * 4 notes

        // First event starts near 0 (wrap-around)
        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus 0.001)
        events[0].end.toDouble() shouldBe (0.05 plusOrMinus 0.001)
    }

    "focus() works as pattern extension" {
        val p = note("c d e f").focus("0", "0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 8  // 2 full cycles compressed
        events[0].data.note shouldBeEqualIgnoringCase "c"
    }

    "focus() works as string extension" {
        val p = note("c d e f").focus("0", "0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 8  // 2 full cycles compressed
        events[0].data.note shouldBeEqualIgnoringCase "c"
    }

    "focus() works in compiled code" {
        val p = StrudelPattern.compile("""note("c d e f").focus("0", "0.5")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 8  // 2 full cycles compressed
        events[0].data.note shouldBeEqualIgnoringCase "c"
    }

    "focus() with standalone function syntax" {
        val p = StrudelPattern.compile("""focus("0.25", "0.75", note("c d e f"))""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 8
        // First event is 'e' due to wrap-around
        events[0].data.note shouldBeEqualIgnoringCase "e"
    }

    "focus() handles zero span gracefully" {
        val p = note("c d e f").focus("0.5", "0.5")
        val events = p.queryArc(0.0, 1.0)

        // Zero span should produce no events
        events.size shouldBe 0
    }

    "focus() handles negative span gracefully" {
        val p = note("c d e f").focus("0.75", "0.25")
        val events = p.queryArc(0.0, 1.0)

        // Negative span should produce no events
        events.size shouldBe 0
    }

    "focus() with full cycle range is equivalent to original" {
        val original = note("c d e f")
        val focused = note("c d e f").focus("0", "1")

        val originalEvents = original.queryArc(0.0, 1.0)
        val focusedEvents = focused.queryArc(0.0, 1.0)

        focusedEvents.size shouldBe originalEvents.size
        focusedEvents.zip(originalEvents).forEach { (fe, oe) ->
            fe.data.note?.lowercase() shouldBe oe.data.note?.lowercase()
            fe.begin.toDouble() shouldBe (oe.begin.toDouble() plusOrMinus EPSILON)
            fe.end.toDouble() shouldBe (oe.end.toDouble() plusOrMinus EPSILON)
        }
    }

    "focus() with control pattern for start" {
        val p = note("c d e f").focus("0 0.5", "1")
        val events = p.queryArc(0.0, 1.0)

        // Verified against JavaScript implementation via JsCompat test
        events.size shouldBe 6
    }

    "focus() with control pattern for end" {
        val p = note("c d e f").focus("0", "0.5 1")
        val events = p.queryArc(0.0, 1.0)

        // Verified against JavaScript implementation via JsCompat test
        events.size shouldBe 6
    }

    "focus() with control patterns for both" {
        val p = note("c d e f").focus("0 0.25", "0.5 0.75")
        val events = p.queryArc(0.0, 1.0)

        // Verified against JavaScript implementation via JsCompat test
        events.size shouldBe 8
    }

    "focus() with control pattern in compiled code" {
        val p = StrudelPattern.compile("""note("c d e f").focus("0 0.5", 1)""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        // Verified against JavaScript implementation via JsCompat test
        events.size shouldBe 6
    }

    "focus() compresses single note into range" {
        val p = note("c").focus("0.1", "0.9")
        val events = p.queryArc(0.0, 1.0)

        // Compresses the pattern (factor = 1/0.8 = 1.25) producing 3 events in [0, 1)
        // due to wrap-around
        events.size shouldBe 3
        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[1].data.note shouldBeEqualIgnoringCase "c"
        events[1].begin.toDouble() shouldBe (0.1 plusOrMinus EPSILON)
        events[1].end.toDouble() shouldBe (0.9 plusOrMinus EPSILON)
    }
})
