@file:Suppress("LocalVariableName")

package io.peekandpoke.klang.strudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.lang.note

class LastOfPatternSpec : StringSpec({

    "LastOfPattern with n=4 applies transform on last cycle only" {
        val source = note("a b c")
        val nPattern = AtomicPattern.value(4)
        val transform: (StrudelPattern) -> StrudelPattern = { it.note("x") }
        val pattern = LastOfPattern(source, nPattern, transform)

        // Cycle 0-2: should be original (a, b, c)
        val cycle0 = pattern.queryArc(0.0, 1.0)
        cycle0.size shouldBe 3
        val notes0 = cycle0.map { it.data.note?.lowercase() }
        notes0 shouldBe listOf("a", "b", "c")

        val cycle1 = pattern.queryArc(1.0, 2.0)
        cycle1.size shouldBe 3
        val notes1 = cycle1.map { it.data.note?.lowercase() }
        notes1 shouldBe listOf("a", "b", "c")

        val cycle2 = pattern.queryArc(2.0, 3.0)
        cycle2.size shouldBe 3
        val notes2 = cycle2.map { it.data.note?.lowercase() }
        notes2 shouldBe listOf("a", "b", "c")

        // Cycle 3: should be transformed (x)
        val cycle3 = pattern.queryArc(3.0, 4.0)
        cycle3.size shouldBe 3
        cycle3.all { it.data.note?.lowercase() == "x" } shouldBe true

        // Cycle 4-6: should be original again
        val cycle4 = pattern.queryArc(4.0, 5.0)
        cycle4.size shouldBe 3
        val notes4 = cycle4.map { it.data.note?.lowercase() }
        notes4 shouldBe listOf("a", "b", "c")

        // Cycle 7: should be transformed again (x)
        val cycle7 = pattern.queryArc(7.0, 8.0)
        cycle7.size shouldBe 3
        cycle7.all { it.data.note?.lowercase() == "x" } shouldBe true
    }

    "LastOfPattern with n=1 applies transform every cycle" {
        val source = note("a")
        val nPattern = AtomicPattern.value(1)
        val transform: (StrudelPattern) -> StrudelPattern = { it.note("x") }
        val pattern = LastOfPattern(source, nPattern, transform)

        // Every cycle should be transformed
        val cycle0 = pattern.queryArc(0.0, 1.0)
        cycle0.size shouldBe 1
        cycle0[0].data.note shouldBeEqualIgnoringCase "x"

        val cycle1 = pattern.queryArc(1.0, 2.0)
        cycle1.size shouldBe 1
        cycle1[0].data.note shouldBeEqualIgnoringCase "x"

        val cycle2 = pattern.queryArc(2.0, 3.0)
        cycle2.size shouldBe 1
        cycle2[0].data.note shouldBeEqualIgnoringCase "x"
    }

    "LastOfPattern with n=2 alternates original and transform" {
        val source = note("a b")
        val nPattern = AtomicPattern.value(2)
        val transform: (StrudelPattern) -> StrudelPattern = { it.note("x") }
        val pattern = LastOfPattern(source, nPattern, transform)

        // Cycle 0: original
        val cycle0 = pattern.queryArc(0.0, 1.0)
        cycle0.size shouldBe 2
        val notes0 = cycle0.map { it.data.note?.lowercase() }
        notes0 shouldBe listOf("a", "b")

        // Cycle 1: transformed
        val cycle1 = pattern.queryArc(1.0, 2.0)
        cycle1.size shouldBe 2
        cycle1.all { it.data.note?.lowercase() == "x" } shouldBe true

        // Cycle 2: original again
        val cycle2 = pattern.queryArc(2.0, 3.0)
        cycle2.size shouldBe 2
        val notes2 = cycle2.map { it.data.note?.lowercase() }
        notes2 shouldBe listOf("a", "b")

        // Cycle 3: transformed again
        val cycle3 = pattern.queryArc(3.0, 4.0)
        cycle3.size shouldBe 2
        cycle3.all { it.data.note?.lowercase() == "x" } shouldBe true
    }

    "LastOfPattern with n=3 applies transform on cycle 2, 5, 8, etc." {
        val source = note("a")
        val nPattern = AtomicPattern.value(3)
        val transform: (StrudelPattern) -> StrudelPattern = { it.note("x") }
        val pattern = LastOfPattern(source, nPattern, transform)

        // Cycle 0: original (position 0)
        val cycle0 = pattern.queryArc(0.0, 1.0)
        cycle0[0].data.note shouldBeEqualIgnoringCase "a"

        // Cycle 1: original (position 1)
        val cycle1 = pattern.queryArc(1.0, 2.0)
        cycle1[0].data.note shouldBeEqualIgnoringCase "a"

        // Cycle 2: transformed (position 2, last of group)
        val cycle2 = pattern.queryArc(2.0, 3.0)
        cycle2[0].data.note shouldBeEqualIgnoringCase "x"

        // Cycle 3: original (position 0)
        val cycle3 = pattern.queryArc(3.0, 4.0)
        cycle3[0].data.note shouldBeEqualIgnoringCase "a"

        // Cycle 4: original (position 1)
        val cycle4 = pattern.queryArc(4.0, 5.0)
        cycle4[0].data.note shouldBeEqualIgnoringCase "a"

        // Cycle 5: transformed (position 2, last of group)
        val cycle5 = pattern.queryArc(5.0, 6.0)
        cycle5[0].data.note shouldBeEqualIgnoringCase "x"
    }

    "LastOfPattern with static n values across different cycles" {
        val source = note("a")
        val transform: (StrudelPattern) -> StrudelPattern = { it.note("x") }

        // Test with n=2
        val pattern2 = LastOfPattern(source, AtomicPattern.value(2), transform)

        // With n=2: cycle 0 position 0 (original), cycle 1 position 1/last (transformed)
        val cycle0_n2 = pattern2.queryArc(0.0, 1.0)
        cycle0_n2[0].data.note shouldBeEqualIgnoringCase "a"

        val cycle1_n2 = pattern2.queryArc(1.0, 2.0)
        cycle1_n2[0].data.note shouldBeEqualIgnoringCase "x"

        // Test with n=3
        val pattern3 = LastOfPattern(source, AtomicPattern.value(3), transform)

        // With n=3: cycles 0,1 original, cycle 2 transformed
        val cycle0_n3 = pattern3.queryArc(0.0, 1.0)
        cycle0_n3[0].data.note shouldBeEqualIgnoringCase "a"

        val cycle1_n3 = pattern3.queryArc(1.0, 2.0)
        cycle1_n3[0].data.note shouldBeEqualIgnoringCase "a"

        val cycle2_n3 = pattern3.queryArc(2.0, 3.0)
        cycle2_n3[0].data.note shouldBeEqualIgnoringCase "x"
    }

    "LastOfPattern with n=0 produces no events" {
        val source = note("a")
        val nPattern = AtomicPattern.value(0)
        val transform: (StrudelPattern) -> StrudelPattern = { it.note("x") }
        val pattern = LastOfPattern(source, nPattern, transform)

        val events = pattern.queryArc(0.0, 1.0)
        events.size shouldBe 0
    }

    "LastOfPattern with negative n produces no events" {
        val source = note("a")
        val nPattern = AtomicPattern.value(-3)
        val transform: (StrudelPattern) -> StrudelPattern = { it.note("x") }
        val pattern = LastOfPattern(source, nPattern, transform)

        val events = pattern.queryArc(0.0, 1.0)
        events.size shouldBe 0
    }

    "LastOfPattern handles negative cycles correctly" {
        val source = note("a")
        val nPattern = AtomicPattern.value(3)
        val transform: (StrudelPattern) -> StrudelPattern = { it.note("x") }
        val pattern = LastOfPattern(source, nPattern, transform)

        // Cycle -1 should be transformed (position 2, last of group)
        val cycleM1 = pattern.queryArc(-1.0, 0.0)
        cycleM1.size shouldBe 1
        cycleM1[0].data.note shouldBeEqualIgnoringCase "x"

        // Cycle -2 should be original (position 1 in group)
        val cycleM2 = pattern.queryArc(-2.0, -1.0)
        cycleM2.size shouldBe 1
        cycleM2[0].data.note shouldBeEqualIgnoringCase "a"

        // Cycle -3 should be original (position 0 in group)
        val cycleM3 = pattern.queryArc(-3.0, -2.0)
        cycleM3.size shouldBe 1
        cycleM3[0].data.note shouldBeEqualIgnoringCase "a"

        // Cycle 0 should be original (position 0 in group)
        val cycle0 = pattern.queryArc(0.0, 1.0)
        cycle0.size shouldBe 1
        cycle0[0].data.note shouldBeEqualIgnoringCase "a"

        // Cycle 2 should be transformed (position 2, last of group)
        val cycle2 = pattern.queryArc(2.0, 3.0)
        cycle2.size shouldBe 1
        cycle2[0].data.note shouldBeEqualIgnoringCase "x"
    }
})
