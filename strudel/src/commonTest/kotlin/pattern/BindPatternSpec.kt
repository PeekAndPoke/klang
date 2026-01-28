package io.peekandpoke.klang.strudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.lang.note
import io.peekandpoke.klang.strudel.lang.seq
import io.peekandpoke.klang.strudel.lang.silence
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

/**
 * Tests for BindPattern - Generic pattern for bind/innerJoin operations.
 */
class BindPatternSpec : StringSpec({

    "BindPattern should delegate weight, steps, and cycle duration to outer pattern" {
        val outer = seq("a", "b", "c")
        val inner = note("x")

        val pattern = BindPattern(outer) { inner }

        pattern.weight shouldBe outer.weight
        pattern.steps shouldBe outer.steps
        pattern.estimateCycleDuration() shouldBe outer.estimateCycleDuration()
    }

    "BindPattern should query outer pattern and generate inner patterns" {
        val outer = seq("a", "b")
        val lookup = mapOf(
            "a" to note("c3"),
            "b" to note("d3")
        )

        val pattern = BindPattern(outer) { event ->
            val key = event.data.value?.asString ?: ""
            lookup[key]
        }

        val events = pattern.queryArc(0.0, 1.0)
        events shouldHaveSize 2
        events[0].data.note shouldBe "c3"
        events[1].data.note shouldBe "d3"
    }

    "BindPattern should clip inner events to outer event boundaries" {
        val outer = seq("a", "b")
        val inner = seq("x", "y", "z", "w")  // 4 events in 1 cycle

        val pattern = BindPattern(outer) { inner }

        val events = pattern.queryArc(0.0, 1.0)
        events shouldHaveSize 4  // Each outer event contains 2 inner events

        // First outer event (0.0 - 0.5) should have 2 inner events clipped to it
        events[0].begin shouldBe 0.toRational()
        events[0].end shouldBe (1.0 / 4.0).toRational()

        events[1].begin shouldBe (1.0 / 4.0).toRational()
        events[1].end shouldBe (1.0 / 2.0).toRational()

        // Second outer event (0.5 - 1.0) should have 2 inner events clipped to it
        events[2].begin shouldBe (1.0 / 2.0).toRational()
        events[2].end shouldBe (3.0 / 4.0).toRational()

        events[3].begin shouldBe (3.0 / 4.0).toRational()
        events[3].end shouldBe 1.toRational()
    }

    "BindPattern should handle null transform results (skip events)" {
        val outer = seq("a", "b", "c")
        val lookup = mapOf(
            "a" to note("x"),
            // "b" is missing - should be skipped
            "c" to note("z")
        )

        val pattern = BindPattern(outer) { event ->
            val key = event.data.value?.asString ?: ""
            lookup[key]  // Returns null for "b"
        }

        val events = pattern.queryArc(0.0, 1.0)
        events shouldHaveSize 2  // Only "a" and "c" produce events
        events[0].data.note shouldBe "x"
        events[1].data.note shouldBe "z"
    }

    "BindPattern should respect query arc boundaries" {
        val outer = seq("a", "b", "c", "d")
        val inner = note("x")

        val pattern = BindPattern(outer) { inner }

        // Query only middle portion
        val events = pattern.queryArc(0.25, 0.75)
        events shouldHaveSize 2  // Should get events "b" and "c"

        events[0].begin shouldBe 0.25.toRational()
        events[1].begin shouldBe 0.5.toRational()
    }

    "BindPattern should handle empty outer pattern" {
        val outer = silence
        val inner = note("x")

        val pattern = BindPattern(outer) { inner }

        val events = pattern.queryArc(0.0, 1.0)
        events shouldHaveSize 0
    }

    "BindPattern should pass query context to inner patterns" {
        val outer = note("a")
        val inner = note("x")

        val pattern = BindPattern(outer) { inner }

        val ctx = StrudelPattern.QueryContext().update {
            set(StrudelPattern.QueryContext.randomSeed, 12345L)
        }

        val events = pattern.queryArcContextual(0.0, 1.0, ctx)
        events shouldHaveSize 1
        // Inner pattern received the context and could use it
    }

    "BindPattern should work with overlapping query arcs" {
        val outer = seq("a", "b")
        val inner = note("x")

        val pattern = BindPattern(outer) { inner }

        // Query overlapping the first event
        val events = pattern.queryArc(0.25, 0.75)
        events shouldHaveSize 2

        // Event should be clipped to outer boundaries
        events[0].begin shouldBe 0.0.toRational()
        events[0].end shouldBe 0.5.toRational()
        events[0].data.note shouldBeEqualIgnoringCase "x"

        // Second event clipped to outer boundaries
        events[1].begin shouldBe 0.5.toRational()
        events[1].end shouldBe 1.0.toRational()
        events[1].data.note shouldBeEqualIgnoringCase "x"
    }

    "BindPattern should handle different inner patterns for different outer events" {
        val outer = seq("a", "b", "c")
        val patterns = mapOf(
            "a" to seq("x", "y"),
            "b" to seq("1", "2", "3"),
            "c" to note("z")
        )

        val pattern = BindPattern(outer) { event ->
            val key = event.data.value?.asString ?: ""
            patterns[key]
        }

        val events = pattern.queryArc(0.0, 1.0)
        // "a" (0..1/3) overlaps "x" (0..1/2) -> clipped "x"
        // "b" (1/3..2/3) overlaps "2" (1/3..2/3) -> "2"
        // "c" (2/3..1) overlaps "z" (0..1) -> clipped "z"
        events shouldHaveSize 3

        events[0].data.value?.asString shouldBe "x"
        events[1].data.value?.asString shouldBe "2"
        events[2].data.note shouldBe "z"
    }
})
