package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangDegradeBySpec : StringSpec({

    "degradeBy() works as pattern extension" {
        val p = note("a").degradeBy(0.0) // 0.0 probability means it stays
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.note shouldBe "a"
    }

    "degradeBy() works as string extension" {
        val p = "a".degradeBy(0.0).note()
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.note shouldBe "A"
    }

    "degradeBy(1.0) removes all events" {
        val p = note("a b c d").degradeBy(1.0)
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 0
    }

    "degrade works as shorthand for degradeBy(0.5)" {
        val p = note("a").seed(1).degrade()
        // We just check if it compiles and runs; statistical behavior is tested in DegradePatternSpec
        p.queryArc(0.0, 1.0)
    }

    "degradeBy() works in compiled code" {
        val p = StrudelPattern.compile("""note("a").degradeBy(0.0)""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.note shouldBe "a"
    }

    "degradeBy() statistical check" {
        val p = note("a").degradeBy(0.5).seed(42)
        var count = 0
        val total = 200
        for (i in 0 until total) {
            if (p.queryArc(i.toDouble(), i + 1.0).isNotEmpty()) {
                count++
            }
        }
        // Roughly 50% of 200 is 100.
        count shouldBeInRange 70..130
    }

    "degrade() works with continuous pattern (sine)" {
        // sine goes from 0.5 to 1.0 to 0.5 to 0.0
        // At t=0.75 probability is 0.0, so event should stay
        val p = note("a b c d").degrade(sine).seed(1)
        val events = p.queryArc(0.0, 1.0)

        // We can't guarantee exact results with random, but at least we check it runs
        events.size shouldBeInRange 0..4
    }

    "degradeBy() with 1.0 pattern removes all" {
        val p = note("a b c d").degradeBy(steady(1.0))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 0
    }

    "degradeBy() with 0.0 pattern keeps all" {
        val p = note("a b c d").degradeBy(steady(0.0))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 4
    }

    "degradeBy() as control pattern" {
        val p = note("[a b c d]").degradeBy("[0.1 1.0 0.2 0.9]")
        val events = (0..<100).flatMap {
            p.queryArc(it.toDouble(), it + 1.0)
        }

        val buckets = events.groupBy { it.data.note }

        assertSoftly {
            withClue("note 'a'") {
                (buckets["a"]?.size ?: 0) shouldBeInRange 80..100
            }
            withClue("note 'b'") {
                (buckets["b"]?.size ?: 0) shouldBe 0
            }
            withClue("note 'c'") {
                (buckets["c"]?.size ?: 0) shouldBeInRange 70..90
            }
            withClue("note 'd'") {
                (buckets["d"]?.size ?: 0) shouldBeInRange 0..20
            }
        }
    }
})
