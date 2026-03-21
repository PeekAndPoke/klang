package io.peekandpoke.klang.strudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class RandrunPatternSpec : StringSpec({

    "RandrunPattern with static n=3 should generate shuffled sequence 0,1,2" {
        val nPattern = AtomicPattern.value(3)
        val pattern = RandrunPattern(nPattern)

        // Query first cycle
        val events = pattern.queryArc(0.0, 1.0)

        // Should have 3 events
        events.size shouldBe 3

        // Should contain values 0, 1, 2 in some order
        val values = events.mapNotNull { it.data.value?.asInt }
        values.sorted() shouldBe listOf(0, 1, 2)
    }

    "RandrunPattern with static n=5 should generate shuffled sequence 0..4" {
        val nPattern = AtomicPattern.value(5)
        val pattern = RandrunPattern(nPattern)

        val events = pattern.queryArc(0.0, 1.0)

        events.size shouldBe 5

        val values = events.mapNotNull { it.data.value?.asInt }
        values.sorted() shouldBe listOf(0, 1, 2, 3, 4)
    }

    "RandrunPattern should produce different shuffle each cycle" {
        val nPattern = AtomicPattern.value(4)
        val pattern = RandrunPattern(nPattern)

        val cycle1 = pattern.queryArc(0.0, 1.0).mapNotNull { it.data.value?.asInt }
        val cycle2 = pattern.queryArc(1.0, 2.0).mapNotNull { it.data.value?.asInt }

        // Both should contain 0..3
        cycle1.sorted() shouldBe listOf(0, 1, 2, 3)
        cycle2.sorted() shouldBe listOf(0, 1, 2, 3)

        // But the order might be different (with high probability)
        // Note: There's a 1/24 chance they're the same, but we can't assert inequality reliably
    }

    "RandrunPattern with n=0 should produce no events" {
        val nPattern = AtomicPattern.value(0)
        val pattern = RandrunPattern(nPattern)

        val events = pattern.queryArc(0.0, 1.0)

        events.size shouldBe 0
    }

    "RandrunPattern with n=1 should produce single event with value 0" {
        val nPattern = AtomicPattern.value(1)
        val pattern = RandrunPattern(nPattern)

        val events = pattern.queryArc(0.0, 1.0)

        events.size shouldBe 1
        val values = events.mapNotNull { it.data.value?.asInt }
        values shouldBe listOf(0)
    }

    "RandrunPattern with varying n should adapt sequence length" {
        // Create a pattern that alternates between n=2 and n=4
        val nPattern = SequencePattern(
            listOf(
                AtomicPattern.value(2),
                AtomicPattern.value(4)
            )
        )
        val pattern = RandrunPattern(nPattern)

        // Query full cycle - should have exactly 2 + 4 = 6 events
        val allEvents = pattern.queryArc(0.0, 1.0)
        allEvents.size shouldBe 6

        // Values should be valid shuffled indices
        val allValues = allEvents.mapNotNull { it.data.value?.asInt }
        allValues.all { it in 0..3 } shouldBe true

        // First 2 events (from n=2) should have values from [0,1]
        val first2 = allValues.take(2)
        first2.all { it in 0..1 } shouldBe true
        first2.sorted() shouldBe listOf(0, 1)

        // Last 4 events (from n=4) should have values from [0,1,2,3]
        val last4 = allValues.drop(2)
        last4.all { it in 0..3 } shouldBe true
        last4.sorted() shouldBe listOf(0, 1, 2, 3)
    }

    "RandrunPattern with negative n should produce no events" {
        val nPattern = AtomicPattern.value(-5)
        val pattern = RandrunPattern(nPattern)

        val events = pattern.queryArc(0.0, 1.0)

        events.size shouldBe 0
    }

    "RandrunPattern should maintain deterministic behavior with same seed" {
        val nPattern = AtomicPattern.value(5)
        val pattern = RandrunPattern(nPattern)

        // Query same timespan multiple times should give same results
        val result1 = pattern.queryArc(0.0, 1.0).mapNotNull { it.data.value?.asInt }
        val result2 = pattern.queryArc(0.0, 1.0).mapNotNull { it.data.value?.asInt }

        result1 shouldBe result2
    }

    "RandrunPattern preserves event timing within sequence" {
        val nPattern = AtomicPattern.value(4)
        val pattern = RandrunPattern(nPattern)

        val events = pattern.queryArc(0.0, 1.0)

        // Should have 4 events spanning the full cycle
        events.size shouldBe 4

        // Check that events are evenly distributed
        events.forEachIndexed { index, event ->
            event.part.begin.toDouble() shouldBe (index.toDouble() / 4)
            event.part.end.toDouble() shouldBe ((index + 1).toDouble() / 4)
        }
    }
})
