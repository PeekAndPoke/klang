package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainOnly
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.shouldBe

class LangSometimesBySpec : StringSpec({

    "sometimesBy(0.5, fn)" {
        val p = note("a")
            .sometimesBy(0.5) { it.scale("C4") }
            .seed(42)

        var countModified = 0
        val total = 200
        for (i in 0 until total) {
            val events = p.queryArc(i.toDouble(), i + 1.0)
            if (events.isNotEmpty() && events[0].data.scale == "C4") countModified++
        }
        countModified shouldBeInRange 80..120
    }

    "sometimesBy(0.0, fn) (never)" {
        val p = note("a")
            .sometimesBy(0.0) { it.scale("C4") }
            .seed(123)

        val events = (0..50).flatMap { p.queryArc(it.toDouble(), it + 1.0) }

        events.map { it.data.scale } shouldContainOnly listOf(null)
    }

    "sometimesBy(1.0, fn) (always)" {
        val p = note("a")
            .sometimesBy(1.0) { it.scale("C4") }
            .seed(123)

        val events = (0..50).flatMap { p.queryArc(it.toDouble(), it + 1.0) }
        events.map { it.data.scale } shouldContainOnly listOf("C4")
    }

    "sometimesBy with control pattern string" {
        // "0.1 0.9": first half 10% prob (mostly 'a'), second half 90% prob (mostly 'A')
        val p = note("a*2")
            .sometimesBy("0.1 0.9") { it.scale("C4") }
            .seed(55)

        var firstHalfMod = 0
        var secondHalfMod = 0
        val total = 200

        for (i in 0 until total) {
            val events = p.queryArc(i.toDouble(), i + 1.0)
            // Expect 2 events
            if (events.size == 2) {
                if (events[0].data.scale == "C4") firstHalfMod++
                if (events[1].data.scale == "C4") secondHalfMod++
            }
        }

        withClue("First half (0.1 prob)") {
            firstHalfMod shouldBeInRange 10..30
        }
        withClue("Second half (0.9 prob)") {
            secondHalfMod shouldBeInRange 170..190
        }
    }

    "sometimes(fn) (default 0.5)" {
        val p = note("a")
            .sometimes { it.scale("C4") }
            .seed(99)

        var countModified = 0
        val total = 200
        for (i in 0 until total) {
            val events = p.queryArc(i.toDouble(), i + 1.0)
            if (events.firstOrNull()?.data?.scale == "C4") countModified++
        }
        countModified shouldBeInRange 80..120
    }

    "often(fn) (0.75)" {
        val p = note("a")
            .often { it.scale("C4") }
            .seed(77)

        var countModified = 0
        val total = 200
        for (i in 0 until total) {
            val events = p.queryArc(i.toDouble(), i + 1.0)
            if (events.firstOrNull()?.data?.scale == "C4") countModified++
        }
        countModified shouldBeInRange 130..170
    }

    "rarely(fn) (0.25)" {
        val p = note("a")
            .rarely { it.scale("C4") }
            .seed(55)

        var countModified = 0
        val total = 200
        for (i in 0 until total) {
            val events = p.queryArc(i.toDouble(), i + 1.0)
            if (events.firstOrNull()?.data?.scale == "C4") countModified++
        }
        countModified shouldBeInRange 30..70
    }

    "almostAlways(fn) (0.9)" {
        val p = note("a")
            .almostAlways { it.scale("C4") }
            .seed(33)

        var countModified = 0
        val total = 200
        for (i in 0 until total) {
            val events = p.queryArc(i.toDouble(), i + 1.0)
            if (events.firstOrNull()?.data?.scale == "C4") countModified++
        }
        countModified shouldBeInRange 160..200
    }

    "almostNever(fn) (0.1)" {
        val p = note("a")
            .almostNever { it.scale("C4") }
            .seed(11)

        var countModified = 0
        val total = 200
        for (i in 0 until total) {
            val events = p.queryArc(i.toDouble(), i + 1.0)
            if (events.firstOrNull()?.data?.scale == "C4") countModified++
        }
        countModified shouldBeInRange 5..35
    }

    "always(fn)" {
        val p = note("a")
            .always { it.scale("C4") }

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.scale shouldBe "C4"
    }

    "never(fn)" {
        val p = note("a")
            .never { it.scale("C4") }
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.scale shouldBe null
    }
})
