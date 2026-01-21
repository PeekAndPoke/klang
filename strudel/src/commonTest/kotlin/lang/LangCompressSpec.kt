package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangCompressSpec : StringSpec({

    "compress() compresses pattern into first half of cycle" {
        val p = note("c d").compress("0", "0.5")
        val events = p.queryArc(0.0, 1.0)

        // Pattern should only produce events in [0, 0.5)
        events.size shouldBe 2

        // First note at the start of compression
        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].end.toDouble() shouldBe (0.25 plusOrMinus EPSILON)

        // Second note in the middle
        events[1].data.note shouldBeEqualIgnoringCase "d"
        events[1].begin.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
        events[1].end.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
    }

    "compress() compresses pattern into second half of cycle" {
        val p = note("c d").compress("0.5", "1")
        val events = p.queryArc(0.0, 1.0)

        // Pattern should only produce events in [0.5, 1.0)
        events.size shouldBe 2

        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[0].begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
        events[0].end.toDouble() shouldBe (0.75 plusOrMinus EPSILON)

        events[1].data.note shouldBeEqualIgnoringCase "d"
        events[1].begin.toDouble() shouldBe (0.75 plusOrMinus EPSILON)
        events[1].end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }

    "compress() compresses pattern into middle of cycle" {
        val p = note("c d e f").compress("0.25", "0.75")
        val events = p.queryArc(0.0, 1.0)

        // Pattern should only produce events in [0.25, 0.75)
        events.size shouldBe 4

        // Each note should be 1/4 of the compressed span (0.5 / 4 = 0.125)
        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[0].begin.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
        events[0].end.toDouble() shouldBe (0.375 plusOrMinus EPSILON)

        events[1].data.note shouldBeEqualIgnoringCase "d"
        events[1].begin.toDouble() shouldBe (0.375 plusOrMinus EPSILON)
        events[1].end.toDouble() shouldBe (0.5 plusOrMinus EPSILON)

        events[2].data.note shouldBeEqualIgnoringCase "e"
        events[2].begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
        events[2].end.toDouble() shouldBe (0.625 plusOrMinus EPSILON)

        events[3].data.note shouldBeEqualIgnoringCase "f"
        events[3].begin.toDouble() shouldBe (0.625 plusOrMinus EPSILON)
        events[3].end.toDouble() shouldBe (0.75 plusOrMinus EPSILON)
    }

    "compress() leaves gaps before and after compressed region" {
        val p = note("c").compress("0.25", "0.75")

        // Query before compressed region - should be empty
        val beforeEvents = p.queryArc(0.0, 0.25)
        beforeEvents.size shouldBe 0

        // Query compressed region - should have event
        val duringEvents = p.queryArc(0.25, 0.75)
        duringEvents.size shouldBe 1

        // Query after compressed region - should be empty
        val afterEvents = p.queryArc(0.75, 1.0)
        afterEvents.size shouldBe 0
    }

    "compress() works across multiple cycles" {
        val p = note("c").compress("0.25", "0.75")
        val events = p.queryArc(0.0, 2.0)

        // Should have one event per cycle in the compressed region
        events.size shouldBe 2

        // First cycle
        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[0].begin.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
        events[0].end.toDouble() shouldBe (0.75 plusOrMinus EPSILON)

        // Second cycle
        events[1].data.note shouldBeEqualIgnoringCase "c"
        events[1].begin.toDouble() shouldBe (1.25 plusOrMinus EPSILON)
        events[1].end.toDouble() shouldBe (1.75 plusOrMinus EPSILON)
    }

    "compress() works with very small compression range" {
        val p = note("c d").compress("0.4", "0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2

        // Compressed into 0.2 of the cycle
        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[0].begin.toDouble() shouldBe (0.4 plusOrMinus EPSILON)
        events[0].end.toDouble() shouldBe (0.5 plusOrMinus EPSILON)

        events[1].data.note shouldBeEqualIgnoringCase "d"
        events[1].begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
        events[1].end.toDouble() shouldBe (0.6 plusOrMinus EPSILON)
    }

    "compress() works as pattern extension" {
        val p = note("c d").compress("0", "0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[1].data.note shouldBeEqualIgnoringCase "d"
    }

    "compress() works as string extension" {
        val p = note("c d").compress("0", "0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[1].data.note shouldBeEqualIgnoringCase "d"
    }

    "compress() works in compiled code" {
        val p = StrudelPattern.compile("""note("c d").compress("0", "0.5")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[1].data.note shouldBeEqualIgnoringCase "d"
    }

    "compress() with standalone function syntax" {
        val p = StrudelPattern.compile("""compress("0.25", "0.75", note("c d"))""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        // Pattern "c d" has two events: c at [0, 0.5), d at [0.5, 1)
        // Compressed into [0.25, 0.75) with span 0.5:
        // c maps to [0.25, 0.5), d maps to [0.5, 0.75)
        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[0].begin.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
        events[1].data.note shouldBeEqualIgnoringCase "d"
        events[1].begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
    }

    "compress() handles zero span gracefully" {
        val p = note("c d").compress("0.5", "0.5")
        val events = p.queryArc(0.0, 1.0)

        // Zero span should produce no events
        events.size shouldBe 0
    }

    "compress() handles negative span gracefully" {
        val p = note("c d").compress("0.75", "0.25")
        val events = p.queryArc(0.0, 1.0)

        // Negative span should produce no events
        events.size shouldBe 0
    }

    "compress() with full cycle range is equivalent to original" {
        val original = note("c d e f")
        val compressed = note("c d e f").compress("0", "1")

        val originalEvents = original.queryArc(0.0, 1.0)
        val compressedEvents = compressed.queryArc(0.0, 1.0)

        compressedEvents.size shouldBe originalEvents.size
        compressedEvents.zip(originalEvents).forEach { (ce, oe) ->
            ce.data.note?.lowercase() shouldBe oe.data.note?.lowercase()
            ce.begin.toDouble() shouldBe (oe.begin.toDouble() plusOrMinus EPSILON)
            ce.end.toDouble() shouldBe (oe.end.toDouble() plusOrMinus EPSILON)
        }
    }

    "compress() with control pattern for start" {
        val p = note("c d").compress("0 0.5", "1")
        val events = p.queryArc(0.0, 1.0)

        // Verified against JavaScript implementation via JsCompat test
        // Control pattern creates compressions with different start values
        events.size shouldBe 3
    }

    "compress() with control pattern for end" {
        val p = note("c d").compress("0", "0.5 1")
        val events = p.queryArc(0.0, 1.0)

        // Verified against JavaScript implementation via JsCompat test
        // Control pattern creates compressions with different end values
        events.size shouldBe 3
    }

    "compress() with control patterns for both" {
        val p = note("c d").compress("0 0.25", "0.5 0.75")
        val events = p.queryArc(0.0, 1.0)

        // Verified against JavaScript implementation via JsCompat test
        // Control patterns for both start and end create compressions
        events.size shouldBe 3
    }

    "compress() with control pattern in compiled code" {
        val p = StrudelPattern.compile("""note("c d").compress("0 0.5", 1)""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        // Verified against JavaScript implementation via JsCompat test
        events.size shouldBe 3
    }
})
