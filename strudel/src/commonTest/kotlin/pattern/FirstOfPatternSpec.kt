package io.peekandpoke.klang.strudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.lang.note

class FirstOfPatternSpec : StringSpec({

    "FirstOfPattern with n=4 applies transform on first cycle only" {
        val source = note("a b c")
        val nPattern = AtomicPattern.value(4)
        val transform: (StrudelPattern) -> StrudelPattern = { it.note("x") }
        val pattern = FirstOfPattern(source, nPattern, transform)

        // Cycle 0: should be transformed (x)
        val cycle0 = pattern.queryArc(0.0, 1.0)
        cycle0.size shouldBe 3
        cycle0.all { it.data.note == "x" } shouldBe true

        // Cycle 1-3: should be original (a, b, c)
        val cycle1 = pattern.queryArc(1.0, 2.0)
        cycle1.size shouldBe 3
        val notes1 = cycle1.map { it.data.note?.lowercase() }
        notes1 shouldBe listOf("a", "b", "c")

        val cycle2 = pattern.queryArc(2.0, 3.0)
        cycle2.size shouldBe 3
        val notes2 = cycle2.map { it.data.note?.lowercase() }
        notes2 shouldBe listOf("a", "b", "c")

        val cycle3 = pattern.queryArc(3.0, 4.0)
        cycle3.size shouldBe 3
        val notes3 = cycle3.map { it.data.note?.lowercase() }
        notes3 shouldBe listOf("a", "b", "c")

        // Cycle 4: should be transformed again (x)
        val cycle4 = pattern.queryArc(4.0, 5.0)
        cycle4.size shouldBe 3
        cycle4.all { it.data.note?.lowercase() == "x" } shouldBe true
    }

    "FirstOfPattern with n=1 applies transform every cycle" {
        val source = note("a")
        val nPattern = AtomicPattern.value(1)
        val transform: (StrudelPattern) -> StrudelPattern = { it.note("x") }
        val pattern = FirstOfPattern(source, nPattern, transform)

        // Every cycle should be transformed
        val cycle0 = pattern.queryArc(0.0, 1.0)
        cycle0.size shouldBe 1
        cycle0[0].data.note shouldBe "x"

        val cycle1 = pattern.queryArc(1.0, 2.0)
        cycle1.size shouldBe 1
        cycle1[0].data.note shouldBe "x"

        val cycle2 = pattern.queryArc(2.0, 3.0)
        cycle2.size shouldBe 1
        cycle2[0].data.note shouldBe "x"
    }

    "FirstOfPattern with n=2 alternates transform and original" {
        val source = note("a b")
        val nPattern = AtomicPattern.value(2)
        val transform: (StrudelPattern) -> StrudelPattern = { it.note("x") }
        val pattern = FirstOfPattern(source, nPattern, transform)

        // Cycle 0: transformed
        val cycle0 = pattern.queryArc(0.0, 1.0)
        cycle0.size shouldBe 2
        cycle0.all { it.data.note == "x" } shouldBe true

        // Cycle 1: original
        val cycle1 = pattern.queryArc(1.0, 2.0)
        cycle1.size shouldBe 2
        val notes1 = cycle1.map { it.data.note?.lowercase() }
        notes1 shouldBe listOf("a", "b")

        // Cycle 2: transformed again
        val cycle2 = pattern.queryArc(2.0, 3.0)
        cycle2.size shouldBe 2
        cycle2.all { it.data.note == "x" } shouldBe true

        // Cycle 3: original again
        val cycle3 = pattern.queryArc(3.0, 4.0)
        cycle3.size shouldBe 2
        val notes3 = cycle3.map { it.data.note?.lowercase() }
        notes3 shouldBe listOf("a", "b")
    }

    "FirstOfPattern with varying n adapts to control pattern" {
        val source = note("a")
        // n alternates between 2 and 3
        val nPattern = SequencePattern(
            listOf(
                AtomicPattern.value(2),
                AtomicPattern.value(3)
            )
        )
        val transform: (StrudelPattern) -> StrudelPattern = { it.note("x") }
        val pattern = FirstOfPattern(source, nPattern, transform)

        // Query full cycle
        val allEvents = pattern.queryArc(0.0, 1.0)
        allEvents.size shouldBe 2

        // First half (n=2): cycle 0 should be transformed
        val event0 = allEvents[0]
        event0.data.note shouldBeEqualIgnoringCase "x"
        event0.begin.toDouble() shouldBe 0.0
        event0.end.toDouble() shouldBe 0.5

        // Second half (n=3): cycle 0 should be transformed
        val event1 = allEvents[1]
        event1.data.note shouldBeEqualIgnoringCase "x"
        event1.begin.toDouble() shouldBe 0.5
        event1.end.toDouble() shouldBe 1.0
    }

    "FirstOfPattern with n=0 produces no events" {
        val source = note("a")
        val nPattern = AtomicPattern.value(0)
        val transform: (StrudelPattern) -> StrudelPattern = { it.note("x") }
        val pattern = FirstOfPattern(source, nPattern, transform)

        val events = pattern.queryArc(0.0, 1.0)
        events.size shouldBe 0
    }

    "FirstOfPattern with negative n produces no events" {
        val source = note("a")
        val nPattern = AtomicPattern.value(-3)
        val transform: (StrudelPattern) -> StrudelPattern = { it.note("x") }
        val pattern = FirstOfPattern(source, nPattern, transform)

        val events = pattern.queryArc(0.0, 1.0)
        events.size shouldBe 0
    }

    "FirstOfPattern handles negative cycles correctly" {
        val source = note("a")
        val nPattern = AtomicPattern.value(3)
        val transform: (StrudelPattern) -> StrudelPattern = { it.note("x") }
        val pattern = FirstOfPattern(source, nPattern, transform)

        // Cycle -3 should be transformed (position 0 in group)
        val cycleM3 = pattern.queryArc(-3.0, -2.0)
        cycleM3.size shouldBe 1
        cycleM3[0].data.note shouldBeEqualIgnoringCase "x"

        // Cycle -2 should be original (position 1 in group)
        val cycleM2 = pattern.queryArc(-2.0, -1.0)
        cycleM2.size shouldBe 1
        cycleM2[0].data.note shouldBeEqualIgnoringCase "a"

        // Cycle -1 should be original (position 2 in group)
        val cycleM1 = pattern.queryArc(-1.0, 0.0)
        cycleM1.size shouldBe 1
        cycleM1[0].data.note shouldBeEqualIgnoringCase "a"

        // Cycle 0 should be transformed (position 0 in group)
        val cycle0 = pattern.queryArc(0.0, 1.0)
        cycle0.size shouldBe 1
        cycle0[0].data.note shouldBeEqualIgnoringCase "x"
    }
})
