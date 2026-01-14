package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangSometimesBySpec : StringSpec({

    "sometimesBy(0.5, fn)" {
        // Base pattern: note "a"
        // Modified pattern: note "A" (via note())
        // Seed 42 ensures deterministic split
        val p = note("a").sometimesBy(0.5) { it.note() }.seed(42)

        var countModified = 0
        var countUnmodified = 0
        val total = 200

        for (i in 0 until total) {
            val events = p.queryArc(i.toDouble(), i + 1.0)
            if (events.isNotEmpty()) {
                val note = events[0].data.note
                if (note == "A") countModified++
                if (note == "a") countUnmodified++
            }
        }

        // Approx 50/50 split
        (countModified + countUnmodified) shouldBe total
        countModified shouldBeInRange 80..120
        countUnmodified shouldBeInRange 80..120
    }

    "sometimesBy(0.0, fn) (never)" {
        val p = note("a").sometimesBy(0.0) { it.note() }.seed(123)
        // Should NEVER apply modification
        val events = (0..50).flatMap { p.queryArc(it.toDouble(), it + 1.0) }
        events.all { it.data.note == "a" } shouldBe true
        events.none { it.data.note == "A" } shouldBe true
    }

    "sometimesBy(1.0, fn) (always)" {
        val p = note("a").sometimesBy(1.0) { it.note() }.seed(123)
        // Should ALWAYS apply modification
        val events = (0..50).flatMap { p.queryArc(it.toDouble(), it + 1.0) }
        events.all { it.data.note == "A" } shouldBe true
        events.none { it.data.note == "a" } shouldBe true
    }

    "sometimes(fn) (default 0.5)" {
        val p = note("a").sometimes { it.note() }.seed(99)
        var countModified = 0
        val total = 200
        for (i in 0 until total) {
            val events = p.queryArc(i.toDouble(), i + 1.0)
            if (events.firstOrNull()?.data?.note == "A") countModified++
        }
        countModified shouldBeInRange 80..120
    }

    "often(fn) (0.75)" {
        val p = note("a").often { it.note() }.seed(77)
        var countModified = 0
        val total = 200
        for (i in 0 until total) {
            val events = p.queryArc(i.toDouble(), i + 1.0)
            if (events.firstOrNull()?.data?.note == "A") countModified++
        }
        // Expect ~150 modified
        countModified shouldBeInRange 130..170
    }

    "rarely(fn) (0.25)" {
        val p = note("a").rarely { it.note() }.seed(55)
        var countModified = 0
        val total = 200
        for (i in 0 until total) {
            val events = p.queryArc(i.toDouble(), i + 1.0)
            if (events.firstOrNull()?.data?.note == "A") countModified++
        }
        // Expect ~50 modified
        countModified shouldBeInRange 30..70
    }

    "almostAlways(fn) (0.9)" {
        val p = note("a").almostAlways { it.note() }.seed(33)
        var countModified = 0
        val total = 200
        for (i in 0 until total) {
            val events = p.queryArc(i.toDouble(), i + 1.0)
            if (events.firstOrNull()?.data?.note == "A") countModified++
        }
        // Expect ~180 modified
        countModified shouldBeInRange 160..200
    }

    "almostNever(fn) (0.1)" {
        val p = note("a").almostNever { it.note() }.seed(11)
        var countModified = 0
        val total = 200
        for (i in 0 until total) {
            val events = p.queryArc(i.toDouble(), i + 1.0)
            if (events.firstOrNull()?.data?.note == "A") countModified++
        }
        // Expect ~20 modified
        countModified shouldBeInRange 5..35
    }

    "always(fn)" {
        val p = note("a").always { it.note() }
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.note shouldBe "A"
    }

    "never(fn)" {
        val p = note("a").never { it.note() }
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.note shouldBe "a"
    }

    "sometimesBy works as string extension" {
        val p = "a".sometimesBy(0.5) { it.note() }.seed(88)
        var countModified = 0
        val total = 200
        for (i in 0 until total) {
            val events = p.queryArc(i.toDouble(), i + 1.0)
            // Original "a" -> via note() -> "A"
            // Modified "a" -> via it.note() -> "A" -> via note() -> "a" (double toggle)
            // Wait, note() toggles logic?
            // "a".note() -> A
            // "a".note().note() -> a

            // Unmodified path: "a" -> note() -> "A"
            // Modified path: "a" -> note() -> "A" -> note() -> "a"
            if (events.firstOrNull()?.data?.note == "A") countModified++
        }
        countModified shouldBeInRange 80..120
    }

    "someCyclesBy() alias works (currently same behavior as sometimesBy)" {
        val p = note("a").someCyclesBy(0.5) { it.note() }.seed(42)
        var countModified = 0
        val total = 200
        for (i in 0 until total) {
            val events = p.queryArc(i.toDouble(), i + 1.0)
            if (events.firstOrNull()?.data?.note == "A") countModified++
        }
        countModified shouldBeInRange 80..120
    }

    "someCycles() alias works (default 0.5)" {
        val p = note("a").someCycles { it.note() }.seed(42)
        var countModified = 0
        val total = 200
        for (i in 0 until total) {
            val events = p.queryArc(i.toDouble(), i + 1.0)
            if (events.firstOrNull()?.data?.note == "A") countModified++
        }
        countModified shouldBeInRange 80..120
    }

    "compiled code: sometimesBy" {
        val p = StrudelPattern.compile("""note("a").sometimesBy(1.0, x => x.note())""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.note shouldBe "A"
    }
})
