package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON

class LangTakeSpec : StringSpec({

    "take() keeps first n steps from pattern" {
        val p = note("c d e f").take(2)

        // Should keep only first 2 steps (c, d) scaled to fill the cycle
        val events1 = p.queryArc(0.0, 1.0)
        events1 shouldHaveSize 2
        events1.map { it.data.note } shouldBe listOf("c", "d")

        // Pattern repeats in next cycle
        val events2 = p.queryArc(1.0, 2.0)
        events2 shouldHaveSize 2
        events2.map { it.data.note } shouldBe listOf("c", "d")
    }

    "take() with fractional steps" {
        val p = note("c d e f").take(2.5)

        // Should keep first 2.5 steps (c, d, and half of e)
        val events = p.queryArc(0.0, 1.0)
        events shouldHaveSize 3
        events.map { it.data.note } shouldBe listOf("c", "d", "e")
        // The 'e' event should be clipped
        events[2].part.end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
    }

    "take() works as standalone function" {
        val p = take(1, note("c d e f"))

        // Should keep only first step (c)
        val events = p.queryArc(0.0, 2.0)
        events shouldHaveSize 2 // One per cycle
        events.all { it.data.note == "c" } shouldBe true
    }

    "take() works with string extension" {
        val p = "1 2 3 4".take(2)

        // String extension creates value pattern, not note pattern
        val events = p.queryArc(0.0, 1.0)
        events shouldHaveSize 2
        events.map { it.data.value?.asDouble?.toInt() } shouldBe listOf(1, 2)
    }
})
