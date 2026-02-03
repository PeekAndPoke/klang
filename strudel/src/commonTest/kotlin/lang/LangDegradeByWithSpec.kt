package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.strudel.StrudelPattern

class LangDegradeByWithSpec : StringSpec({

    "degradeByWith() with constant 1.0 pattern keeps all events (deterministic)" {
        // constant 1.0 > 0.5, so all events are kept
        val p = note("a").degradeByWith(steady(1.0), 0.5)
        var count = 0
        val total = 100
        for (i in 0 until total) {
            if (p.queryArc(i.toDouble(), i + 1.0).isNotEmpty()) {
                count++
            }
        }
        // Should keep all 100 events
        count shouldBe total
    }

    "degradeByWith() with constant 0.0 pattern removes all events (deterministic)" {
        // constant 0.0 is not > 0.5, so all events are removed
        val p = note("a").degradeByWith(steady(0.0), 0.5)
        var count = 0
        val total = 100
        for (i in 0 until total) {
            if (p.queryArc(i.toDouble(), i + 1.0).isNotEmpty()) {
                count++
            }
        }
        // Should remove all events
        count shouldBe 0
    }

    "degradeByWith() with threshold 1.0 removes all events" {
        // No random value can be > 1.0
        val p = note("a").degradeByWith(rand, 1.0).seed(42)
        var count = 0
        val total = 100
        for (i in 0 until total) {
            if (p.queryArc(i.toDouble(), i + 1.0).isNotEmpty()) {
                count++
            }
        }
        count shouldBe 0
    }

    "degradeByWith() with threshold 0.0 keeps all events" {
        // All random values are > 0.0 (except exact 0.0 which is extremely rare)
        val p = note("a").degradeByWith(rand, 0.0).seed(42)
        var count = 0
        val total = 100
        for (i in 0 until total) {
            if (p.queryArc(i.toDouble(), i + 1.0).isNotEmpty()) {
                count++
            }
        }
        // Should keep (almost) all events
        count shouldBeInRange 95..100
    }

    "degradeByWith() statistical check with rand and threshold 0.5" {
        // With threshold 0.5, approximately 50% of events should be kept (where rand > 0.5)
        val p = note("a").degradeByWith(rand, 0.5).seed(42)
        var count = 0
        val total = 200
        for (i in 0 until total) {
            if (p.queryArc(i.toDouble(), i + 1.0).isNotEmpty()) {
                count++
            }
        }
        // Roughly 50% of 200 is 100
        count shouldBeInRange 70..130
    }

    "degradeByWith() statistical check with rand and threshold 0.25" {
        // With threshold 0.25, approximately 75% of events should be kept (where rand > 0.25)
        val p = note("a").degradeByWith(rand, 0.25).seed(42)
        var count = 0
        val total = 200
        for (i in 0 until total) {
            if (p.queryArc(i.toDouble(), i + 1.0).isNotEmpty()) {
                count++
            }
        }
        // Roughly 75% of 200 is 150
        count shouldBeInRange 130..170
    }

    "degradeByWith() works as string extension" {
        val p = "a".degradeByWith(steady(1.0), 0.5).note()
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.note shouldBeEqualIgnoringCase "A"
    }

    "degradeByWith() works in compiled code" {
        val p = StrudelPattern.compile("""note("a").degradeByWith(steady(1.0), 0.5)""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.note shouldBeEqualIgnoringCase "a"
    }

    "degradeByWith() preserves event timing (with constant pattern)" {
        val p = note("a b c d").degradeByWith(steady(1.0), 0.5)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        events[0].part.begin.toDouble() shouldBe 0.0
        events[1].part.begin.toDouble() shouldBe 0.25
        events[2].part.begin.toDouble() shouldBe 0.5
        events[3].part.begin.toDouble() shouldBe 0.75
    }

    "degradeByWith() with dynamic x parameter (pattern)" {
        // x varies, statistical check over multiple cycles
        val p = note("a").degradeByWith(steady(0.5), sine.segment(1)).seed(42)
        var count = 0
        val total = 100
        for (i in 0 until total) {
            if (p.queryArc(i.toDouble(), i + 1.0).isNotEmpty()) {
                count++
            }
        }
        // sine varies 0.0 to 1.0, so threshold varies
        // Roughly half the time sine > 0.5 will be true, half false
        // So roughly 0.5 > random probability distribution
        // This is complex, just check reasonable range
        count shouldBeInRange 20..80
    }
})
