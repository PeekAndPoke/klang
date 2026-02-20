package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.strudel.StrudelPattern

class LangUndegradeByWithSpec : StringSpec({

    "undegradeByWith() with constant 0.0 pattern removes all events (deterministic)" {
        // constant 0.0 is not >= (1 - 0.5) = 0.5, so all events are removed
        val p = note("a").undegradeByWith(steady(0.0), 0.5)
        var count = 0
        val total = 100
        for (i in 0 until total) {
            if (p.queryArc(i.toDouble(), i + 1.0).isNotEmpty()) {
                count++
            }
        }
        count shouldBe 0
    }

    "undegradeByWith() with constant 1.0 pattern keeps all events (deterministic)" {
        // constant 1.0 >= (1 - 0.5) = 0.5, so all events are kept
        val p = note("a").undegradeByWith(steady(1.0), 0.5)
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

    "undegradeByWith() with threshold 1.0 keeps all events" {
        // All random values are <= 1.0
        val p = note("a").undegradeByWith(rand, 1.0).seed(42)
        var count = 0
        val total = 100
        for (i in 0 until total) {
            if (p.queryArc(i.toDouble(), i + 1.0).isNotEmpty()) {
                count++
            }
        }
        // Should keep all events
        count shouldBe total
    }

    "undegradeByWith() with threshold 0.0 removes most events" {
        // Only exact 0.0 random values are <= 0.0 (extremely rare)
        val p = note("a").undegradeByWith(rand, 0.0).seed(42)
        var count = 0
        val total = 100
        for (i in 0 until total) {
            if (p.queryArc(i.toDouble(), i + 1.0).isNotEmpty()) {
                count++
            }
        }
        // Should remove (almost) all events
        count shouldBeInRange 0..5
    }

    "undegradeByWith() statistical check with rand and threshold 0.5" {
        // With threshold 0.5, approximately 50% of events should be kept (where rand <= 0.5)
        val p = note("a").undegradeByWith(rand, 0.5).seed(42)
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

    "undegradeByWith() statistical check with rand and threshold 0.75" {
        // With threshold 0.75, approximately 75% of events should be kept (where rand <= 0.75)
        val p = note("a").undegradeByWith(rand, 0.75).seed(42)
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

    "undegradeByWith() is inverse of degradeByWith()" {
        // degradeByWith keeps where v > x
        // undegradeByWith keeps where v <= x
        // Together they should cover all events exactly once
        val seed = 42
        val p1 = note("a").degradeByWith(rand, 0.5).seed(seed)
        val p2 = note("a").undegradeByWith(rand, 0.5).seed(seed)

        var count1 = 0
        var count2 = 0
        val total = 100
        for (i in 0 until total) {
            val e1 = p1.queryArc(i.toDouble(), i + 1.0)
            val e2 = p2.queryArc(i.toDouble(), i + 1.0)
            if (e1.isNotEmpty()) count1++
            if (e2.isNotEmpty()) count2++
        }

        // They should be complementary: count1 + count2 ≈ total
        (count1 + count2) shouldBeInRange 90..110
    }

    "undegradeByWith() works as string extension" {
        val p = "a".undegradeByWith(steady(1.0), 0.5).note()
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.note shouldBeEqualIgnoringCase "A"
    }

    "undegradeByWith() works in compiled code" {
        val p = StrudelPattern.compile("""note("a").undegradeByWith(steady(1.0), 0.5)""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.note shouldBeEqualIgnoringCase "a"
    }

    "undegradeByWith() preserves event timing (with constant pattern)" {
        val p = note("a b c d").undegradeByWith(steady(1.0), 0.5)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        events[0].part.begin.toDouble() shouldBe 0.0
        events[1].part.begin.toDouble() shouldBe 0.25
        events[2].part.begin.toDouble() shouldBe 0.5
        events[3].part.begin.toDouble() shouldBe 0.75
    }

    "undegradeByWith() with dynamic x parameter (pattern)" {
        // x varies, statistical check over multiple cycles
        val p = note("a").undegradeByWith(steady(0.5), sine.slow(4))
        var count = 0
        val total = 100
        for (i in 0 until total) {
            if (p.queryArc(i.toDouble(), i + 1.0).isNotEmpty()) {
                count++
            }
        }
        // sine varies 0.0 to 1.0, so threshold varies
        // Complex distribution, just check reasonable range
        count shouldBeInRange 20..80
    }
})
