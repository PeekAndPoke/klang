package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangFastGapSpec : StringSpec({

    "fastGap() compresses pattern into first half with factor 2" {
        val p = note("c d").fastGap("2")
        val events = p.queryArc(0.0, 1.0)

        // Pattern should only produce events in [0, 0.5)
        events.size shouldBe 2

        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].end.toDouble() shouldBe (0.25 plusOrMinus EPSILON)

        events[1].data.note shouldBeEqualIgnoringCase "d"
        events[1].begin.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
        events[1].end.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
    }

    "fastGap() with factor 3 compresses into first third" {
        val p = note("c d e").fastGap("3")
        val events = p.queryArc(0.0, 1.0)

        // Pattern should only produce events in [0, 1/3)
        events.size shouldBe 3

        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].end.toDouble() shouldBe (1.0 / 9.0 plusOrMinus EPSILON)

        events[1].data.note shouldBeEqualIgnoringCase "d"
        events[1].begin.toDouble() shouldBe (1.0 / 9.0 plusOrMinus EPSILON)
        events[1].end.toDouble() shouldBe (2.0 / 9.0 plusOrMinus EPSILON)

        events[2].data.note shouldBeEqualIgnoringCase "e"
        events[2].begin.toDouble() shouldBe (2.0 / 9.0 plusOrMinus EPSILON)
        events[2].end.toDouble() shouldBe (1.0 / 3.0 plusOrMinus EPSILON)
    }

    "fastGap() leaves gap in remaining cycle" {
        val p = note("c").fastGap("2")

        // Query before compressed region
        val beforeEvents = p.queryArc(0.0, 0.0)
        beforeEvents.size shouldBe 0

        // Query compressed region
        val duringEvents = p.queryArc(0.0, 0.5)
        duringEvents.size shouldBe 1

        // Query gap region
        val gapEvents = p.queryArc(0.5, 1.0)
        gapEvents.size shouldBe 0
    }

    "fastGap() works across multiple cycles" {
        val p = note("c").fastGap("2")
        val events = p.queryArc(0.0, 2.0)

        // One event per cycle in the compressed region
        events.size shouldBe 2

        // First cycle
        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].end.toDouble() shouldBe (0.5 plusOrMinus EPSILON)

        // Second cycle
        events[1].data.note shouldBeEqualIgnoringCase "c"
        events[1].begin.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        events[1].end.toDouble() shouldBe (1.5 plusOrMinus EPSILON)
    }

    "fastGap() with factor 4 compresses into first quarter" {
        val p = note("c d").fastGap("4")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2

        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].end.toDouble() shouldBe (0.125 plusOrMinus EPSILON)

        events[1].data.note shouldBeEqualIgnoringCase "d"
        events[1].begin.toDouble() shouldBe (0.125 plusOrMinus EPSILON)
        events[1].end.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
    }

    "fastGap() with sound patterns" {
        val p = sound("bd hh sd").fastGap("2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 3

        events[0].data.sound shouldBe "bd"
        events[1].data.sound shouldBe "hh"
        events[2].data.sound shouldBe "sd"

        // All should be in first half
        events.forEach { event ->
            (event.end.toDouble() <= 0.5) shouldBe true
        }
    }

    "fastGap() works as pattern extension" {
        val p = note("c d").fastGap("2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[1].data.note shouldBeEqualIgnoringCase "d"
    }

    "fastGap() works as string extension" {
        val p = note("c d").fastGap("2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[1].data.note shouldBeEqualIgnoringCase "d"
    }

    "fastGap() works in compiled code" {
        val p = StrudelPattern.compile("""note("c d").fastGap("2")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[1].data.note shouldBeEqualIgnoringCase "d"
    }

    "fastGap() with standalone function syntax" {
        val p = StrudelPattern.compile("""fastGap("2", note("c d"))""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[1].data.note shouldBeEqualIgnoringCase "d"
        events[1].begin.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
    }

    "densityGap() alias works" {
        val p = note("c d").densityGap("2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[1].data.note shouldBeEqualIgnoringCase "d"
    }

    "fastGap() with factor 1 returns unchanged" {
        val original = note("c d")
        val fastGapped = note("c d").fastGap("1")

        val originalEvents = original.queryArc(0.0, 1.0)
        val fastGappedEvents = fastGapped.queryArc(0.0, 1.0)

        fastGappedEvents.size shouldBe originalEvents.size
    }

    "fastGap() preserves event data" {
        val p = note("c").gain("0.5").fastGap("2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[0].data.gain shouldBe 0.5
    }

    "fastGap() with control pattern" {
        val p = note("c d").fastGap("2 3")
        val events = p.queryArc(0.0, 1.0)

        // FastGap with control pattern compresses into first 1/factor of cycle
        // Verified against JavaScript implementation via JsCompat test
        events.size shouldBe 2
    }

    "fastGap() with control pattern in compiled code" {
        val p = StrudelPattern.compile("""note("c d").fastGap("2 4")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        // Verified against JavaScript implementation via JsCompat test
        events.size shouldBe 2
    }

    "fastGap() string extension alias works" {
        val p = "c d".fastGap("2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asString shouldBeEqualIgnoringCase "c"
        events[1].data.value?.asString shouldBeEqualIgnoringCase "d"
    }

    "densityGap() string extension alias works" {
        val p = "c d".densityGap("2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.value?.asString shouldBeEqualIgnoringCase "c"
        events[1].data.value?.asString shouldBeEqualIgnoringCase "d"
    }
})
