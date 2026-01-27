package io.peekandpoke.klang.strudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.lang.note
import io.peekandpoke.klang.strudel.lang.seq
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational
import io.peekandpoke.klang.strudel.withSteps
import io.peekandpoke.klang.strudel.withWeight

/**
 * Tests for PropertyOverridePattern - Generic pattern for property overrides.
 */
class PropertyOverridePatternSpec : StringSpec({

    "PropertyOverridePattern should override weight only" {
        val source = seq("a", "b", "c")
        val originalWeight = source.weight

        val pattern = PropertyOverridePattern(
            source = source,
            weightOverride = 2.5
        )

        pattern.weight shouldBe 2.5
        pattern.steps shouldBe source.steps
        pattern.estimateCycleDuration() shouldBe source.estimateCycleDuration()

        // Events should be unchanged
        val events = pattern.queryArc(0.0, 1.0)
        val sourceEvents = source.queryArc(0.0, 1.0)
        events shouldHaveSize sourceEvents.size
    }

    "PropertyOverridePattern should override steps only" {
        val source = note("a")
        val newSteps = 4.toRational()

        val pattern = PropertyOverridePattern(
            source = source,
            stepsOverride = newSteps
        )

        pattern.weight shouldBe source.weight
        pattern.steps shouldBe newSteps
        pattern.estimateCycleDuration() shouldBe source.estimateCycleDuration()
    }

    "PropertyOverridePattern should override cycle duration only" {
        val source = note("a")
        val newDuration = 2.toRational()

        val pattern = PropertyOverridePattern(
            source = source,
            cycleDurationOverride = newDuration
        )

        pattern.weight shouldBe source.weight
        pattern.steps shouldBe source.steps
        pattern.estimateCycleDuration() shouldBe newDuration
    }

    "PropertyOverridePattern should override multiple properties" {
        val source = seq("a", "b")
        val newWeight = 3.0
        val newSteps = 8.toRational()

        val pattern = PropertyOverridePattern(
            source = source,
            weightOverride = newWeight,
            stepsOverride = newSteps
        )

        pattern.weight shouldBe newWeight
        pattern.steps shouldBe newSteps
        pattern.estimateCycleDuration() shouldBe source.estimateCycleDuration()
    }

    "PropertyOverridePattern should not modify event data or timing" {
        val source = seq("a", "b", "c")

        val pattern = PropertyOverridePattern(
            source = source,
            weightOverride = 2.0
        )

        val sourceEvents = source.queryArc(0.0, 1.0)
        val patternEvents = pattern.queryArc(0.0, 1.0)

        patternEvents shouldHaveSize sourceEvents.size
        patternEvents.forEachIndexed { index, event ->
            event.begin shouldBe sourceEvents[index].begin
            event.end shouldBe sourceEvents[index].end
            event.data.note shouldBe sourceEvents[index].data.note
        }
    }

    "PropertyOverridePattern should work with null overrides (passthrough)" {
        val source = seq("a", "b")

        val pattern = PropertyOverridePattern(
            source = source,
            weightOverride = null,
            stepsOverride = null,
            cycleDurationOverride = null
        )

        pattern.weight shouldBe source.weight
        pattern.steps shouldBe source.steps
        pattern.estimateCycleDuration() shouldBe source.estimateCycleDuration()
    }

    "withWeight extension should create PropertyOverridePattern" {
        val source = note("a")
        val newWeight = 5.0

        val pattern = source.withWeight(newWeight)

        pattern.weight shouldBe newWeight
        pattern.steps shouldBe source.steps

        // Should be a PropertyOverridePattern
        (pattern is PropertyOverridePattern) shouldBe true
    }

    "withSteps extension should create PropertyOverridePattern" {
        val source = seq("a", "b", "c")
        val newSteps = 12.toRational()

        val pattern = source.withSteps(newSteps)

        pattern.weight shouldBe source.weight
        pattern.steps shouldBe newSteps

        // Should be a PropertyOverridePattern
        (pattern is PropertyOverridePattern) shouldBe true
    }

    "PropertyOverridePattern should be chainable" {
        val source = note("a")

        val pattern = source
            .withWeight(2.0)
            .withSteps(4.toRational())

        pattern.weight shouldBe 2.0
        pattern.steps shouldBe 4.toRational()
    }

    "PropertyOverridePattern should work with different source patterns" {
        // Test with various source patterns
        val sources = listOf(
            note("a"),
            seq("a", "b", "c"),
            seq("1", "2", "3", "4")
        )

        sources.forEach { source ->
            val pattern = source.withWeight(3.5)

            pattern.weight shouldBe 3.5
            pattern.steps shouldBe source.steps

            val events = pattern.queryArc(0.0, 1.0)
            events shouldHaveSize source.queryArc(0.0, 1.0).size
        }
    }

    "PropertyOverridePattern should preserve pattern behavior across multiple queries" {
        val source = seq("a", "b", "c", "d")
        val pattern = source.withWeight(2.5).withSteps(8.toRational())

        // Query multiple times
        val events1 = pattern.queryArc(0.0, 1.0)
        val events2 = pattern.queryArc(0.0, 1.0)
        val events3 = pattern.queryArc(0.5, 1.5)

        // Properties should remain consistent
        pattern.weight shouldBe 2.5
        pattern.steps shouldBe 8.toRational()

        events1 shouldHaveSize 4
        events2 shouldHaveSize 4
    }

    "PropertyOverridePattern should work in sequences (weight usage)" {
        // Weight is used for proportional time in sequences
        val a = note("a").withWeight(1.0)
        val b = note("b").withWeight(2.0)
        val c = note("c").withWeight(3.0)

        a.weight shouldBe 1.0
        b.weight shouldBe 2.0
        c.weight shouldBe 3.0

        // In a sequence, "b" would get 2x the time of "a", and "c" would get 3x
        // Total time: 1+2+3 = 6
        // "a" gets 1/6, "b" gets 2/6, "c" gets 3/6
    }

    "PropertyOverridePattern should preserve source steps when overriding weight" {
        val source = seq("a", "b", "c")
        val originalSteps = source.steps

        val pattern = source.withWeight(10.0)

        pattern.steps shouldBe originalSteps
        pattern.weight shouldBe 10.0
    }

    "PropertyOverridePattern should preserve source weight when overriding steps" {
        val source = seq("a", "b")
        val originalWeight = source.weight

        val pattern = source.withSteps(16.toRational())

        pattern.weight shouldBe originalWeight
        pattern.steps shouldBe 16.toRational()
    }

    "PropertyOverridePattern should handle zero weight" {
        val source = note("a")
        val pattern = source.withWeight(0.0)

        pattern.weight shouldBe 0.0
        // Events should still be queryable
        val events = pattern.queryArc(0.0, 1.0)
        events shouldHaveSize 1
    }

    "PropertyOverridePattern should handle negative weight" {
        val source = note("a")
        val pattern = source.withWeight(-1.0)

        pattern.weight shouldBe -1.0
        // Events should still be queryable (weight is just metadata)
        val events = pattern.queryArc(0.0, 1.0)
        events shouldHaveSize 1
    }
})
