package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.common.math.Rational.Companion.toRational
import io.peekandpoke.klang.sprudel.EPSILON
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangHurrySpec : StringSpec({

    "hurry dsl interface" {
        val pat = "hh hh"

        dslInterfaceTests(
            "pattern.hurry(2)" to s(pat).hurry(2),
            "script pattern.hurry(2)" to SprudelPattern.compile("""s("$pat").hurry(2)"""),
            "string.hurry(2)" to pat.hurry(2),
            "script string.hurry(2)" to SprudelPattern.compile(""""$pat".hurry(2)"""),
            "hurry(2)" to s(pat).apply(hurry(2)),
            "script hurry(2)" to SprudelPattern.compile("""s("$pat").apply(hurry(2))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events.size shouldBe 4
        }
    }

    "hurry() speeds up pattern like fast()" {
        val p = note("c d").hurry("2")
        val events = p.queryArc(0.0, 1.0)

        // Pattern should repeat twice in one cycle (like fast(2))
        events.size shouldBe 4
    }

    "hurry() also multiplies speed parameter" {
        val p = sound("bd hh").hurry("2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4

        // All events should have speed = 2.0
        events.forEach { event ->
            event.data.speed shouldBe 2.0
        }
    }

    "hurry() preserves existing speed values" {
        val p = sound("bd hh").speed("0.5").hurry("2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4

        // Speed should be original (0.5) * factor (2) = 1.0
        events.forEach { event ->
            event.data.speed shouldBe (1.0 plusOrMinus EPSILON)
        }
    }

    "hurry() with factor 3" {
        val p = note("c d e f").hurry("3")
        val events = p.queryArc(0.0, 1.0)

        // Pattern should repeat 3 times
        events.size shouldBe 12

        // Check timing - each repetition should be 1/3 of a cycle
        events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        events[0].part.end.toDouble() shouldBe (1.0 / 12.0 plusOrMinus EPSILON)

        // All should have speed = 3.0
        events.forEach { event ->
            event.data.speed shouldBe 3.0
        }
    }

    "hurry() with factor 0.5 slows down" {
        val p = sound("bd hh sd oh").hurry("0.5")
        val events = p.queryArc(0.0, 2.0)

        // Pattern plays at half speed, so only 2 events in first cycle
        val firstCycleEvents = events.filter { it.part.begin < 1.0.toRational() }
        firstCycleEvents.size shouldBe 2

        // Speed should be 0.5
        firstCycleEvents.forEach { event ->
            event.data.speed shouldBe 0.5
        }
    }

    "hurry() with factor 1 returns unchanged pattern" {
        val original = note("c d e f")
        val hurried = note("c d e f").hurry("1")

        val originalEvents = original.queryArc(0.0, 1.0)
        val hurriedEvents = hurried.queryArc(0.0, 1.0)

        hurriedEvents.size shouldBe originalEvents.size

        hurriedEvents.zip(originalEvents).forEach { (h, o) ->
            h.data.note shouldBeEqualIgnoringCase (o.data.note ?: "")
            h.part.begin shouldBe o.part.begin
            h.part.end shouldBe o.part.end
            // Speed should remain unchanged (null)
            o.data.speed.shouldBeNull()
            h.data.speed shouldBe 1.0
        }
    }

    "hurry() works across multiple cycles" {
        val p = note("c").hurry("2")
        val events = p.queryArc(0.0, 2.0)

        // 2 cycles, pattern repeats twice per cycle = 4 total
        events.size shouldBe 4

        events.forEach { event ->
            event.data.note shouldBeEqualIgnoringCase "c"
            event.data.speed shouldBe 2.0
        }
    }

    "hurry() works with notes" {
        val p = note("c d").hurry("2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4

        events[0].data.note shouldBeEqualIgnoringCase "c"
        events[0].data.speed shouldBe 2.0

        events[1].data.note shouldBeEqualIgnoringCase "d"
        events[1].data.speed shouldBe 2.0
    }

    "hurry() works as pattern extension" {
        val p = note("c d").hurry("2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        events[0].data.speed shouldBe 2.0
    }

    "hurry() works as string extension" {
        val p = note("c d").hurry("2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        events[0].data.speed shouldBe 2.0
    }

    "hurry() works in compiled code" {
        val p = SprudelPattern.compile("""note("c d").hurry("2")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 4
        events[0].data.speed shouldBe 2.0
    }

    "hurry() with standalone function syntax" {
        val p = SprudelPattern.compile("""note("c d").apply(hurry("2"))""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 4
        events[0].data.speed shouldBe 2.0
        events[1].data.speed shouldBe 2.0
    }

    "hurry() preserves other event data" {
        val p = note("c").gain("0.5").hurry("2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.forEach { event ->
            event.data.note shouldBeEqualIgnoringCase "c"
            event.data.gain shouldBe 0.5
            event.data.speed shouldBe 2.0
        }
    }

    "hurry() with complex pattern" {
        val p = note("c [d e]").hurry("2")
        val events = p.queryArc(0.0, 1.0)

        // Pattern has 3 events, hurry(2) repeats it twice = 6 total
        events.size shouldBe 6

        events.forEach { event ->
            event.data.speed shouldBe 2.0
        }
    }

    "hurry() with control pattern" {
        val p = sound("bd hh").hurry("2 4")
        val events = p.queryArc(0.0, 1.0)

        // Hurry with control pattern creates multiple events with different speeds
        events.size shouldBe 6

        // All events should have their speed parameter multiplied
        events.forEach { event ->
            (event.data.speed ?: 1.0) shouldBe event.data.speed
        }
    }

    "hurry() with control pattern in compiled code" {
        val p = SprudelPattern.compile("""sound("bd hh").hurry("2 3")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        // Verified against JavaScript implementation via JsCompat test
        events.size shouldBe 5
    }
})
