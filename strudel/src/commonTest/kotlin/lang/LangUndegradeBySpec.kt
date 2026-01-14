package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangUndegradeBySpec : StringSpec({

    "undegradeBy() works as pattern extension" {
        val p = note("a").undegradeBy(0.0) // 0.0 probability of removal means it stays
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.note shouldBe "a"
    }

    "undegradeBy() works as string extension" {
        val p = "a".undegradeBy(0.0).note()
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.note shouldBe "A"
    }

    "undegradeBy(1.0) removes all events" {
        val p = note("a b c d").undegradeBy(1.0)
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 0
    }

    "undegradeBy() works in compiled code" {
        val p = StrudelPattern.compile("""note("a").undegradeBy(0.0)""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.note shouldBe "a"
    }

    "undegradeBy() statistical check" {
        val p = note("a").undegradeBy(0.5).seed(42)
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

    "undegradeBy() with 1.0 pattern removes all" {
        val p = note("a b c d").undegradeBy(steady(1.0))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 0
    }

    "undegradeBy() with 0.0 pattern keeps all" {
        val p = note("a b c d").undegradeBy(steady(0.0))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 4
    }

    "undegradeBy() complementarity with degradeBy()" {
        // degradeBy(0.2) keeps if r > 0.2 (approx 80%)
        // undegradeBy(0.8) keeps if r <= 0.2 (approx 20%)
        // Together they should cover all events uniquely if seeded same way

        val seed = 12345
        val base = note("a")

        // Apply seed LAST so that DegradePattern runs inside the seeded context
        val p1 = base.degradeBy(0.2).seed(seed)
        val p2 = base.undegradeBy(0.8).seed(seed)

        var count1 = 0
        var count2 = 0
        var countBoth = 0
        var countNone = 0

        val total = 100
        for (i in 0 until total) {
            val e1 = p1.queryArc(i.toDouble(), i + 1.0).isNotEmpty()
            val e2 = p2.queryArc(i.toDouble(), i + 1.0).isNotEmpty()

            if (e1) count1++
            if (e2) count2++
            if (e1 && e2) countBoth++
            if (!e1 && !e2) countNone++
        }

        assertSoftly {
            countBoth shouldBe 0 // Should strictly never overlap
            countNone shouldBe 0 // Should strictly cover everything
            (count1 + count2) shouldBe total

            // Check roughly distribution
            count1 shouldBeInRange 70..90 // Expect 80
            count2 shouldBeInRange 10..30 // Expect 20
        }
    }

    "undegradeBy() as control pattern" {
        val p = note("[a b c d]").undegradeBy("[0.9 0.0 0.8 0.1]")
        // undegradeBy(0.9) -> keep r < 0.1 (10% kept)
        // undegradeBy(0.0) -> keep r < 1.0 (100% kept)
        // undegradeBy(0.8) -> keep r < 0.2 (20% kept)
        // undegradeBy(0.1) -> keep r < 0.9 (90% kept)

        val events = (0..<100).flatMap {
            p.queryArc(it.toDouble(), it + 1.0)
        }

        val buckets = events.groupBy { it.data.note }

        println(buckets.mapValues { (_, v) -> v.size })

        assertSoftly {
            withClue("note 'a' (prob keep ~0.1)") {
                (buckets["a"]?.size ?: 0) shouldBeInRange 0..25
            }
            withClue("note 'b' (prob keep 1.0)") {
                (buckets["b"]?.size ?: 0) shouldBeInRange 90..100
            }
            withClue("note 'c' (prob keep ~0.2)") {
                (buckets["c"]?.size ?: 0) shouldBeInRange 10..30
            }
            withClue("note 'd' (prob keep ~0.9)") {
                (buckets["d"]?.size ?: 0) shouldBeInRange 80..100
            }
        }
    }

    "undegrade() works as shorthand for undegradeBy(0.5)" {
        val p = note("a").seed(1).undegrade()
        // Statistical check for 0.5 probability (roughly)
        var count = 0
        val total = 200
        for (i in 0 until total) {
            if (p.queryArc(i.toDouble(), i + 1.0).isNotEmpty()) {
                count++
            }
        }
        count shouldBeInRange 70..130
    }

    "undegrade() string extension works" {
        val p = "a".undegrade().note().seed(1)
        // Just ensuring it compiles and runs without error, producing roughly 50%
        var count = 0
        val total = 200
        for (i in 0 until total) {
            if (p.queryArc(i.toDouble(), i + 1.0).isNotEmpty()) {
                count++
            }
        }
        count shouldBeInRange 70..130
    }
})
