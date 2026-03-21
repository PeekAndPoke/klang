package io.peekandpoke.klang.strudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON

class EuclideanMorphPatternSpec : StringSpec({

    "calculateMorphedArcs(3, 8, 0.0) matches standard Euclidean" {
        // Euclidean(3,8) positions: 0, 3, 6 (steps) -> 0.0, 0.375, 0.75 (time)
        // Step duration: 1/8 = 0.125
        val arcs = EuclideanMorphPattern.calculateMorphedArcs(3, 8, 0.0)

        arcs.size shouldBe 3
        arcs[0].first.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        arcs[0].second.toDouble() shouldBe (0.125 plusOrMinus EPSILON)

        arcs[1].first.toDouble() shouldBe (0.375 plusOrMinus EPSILON)
        arcs[1].second.toDouble() shouldBe (0.5 plusOrMinus EPSILON)

        arcs[2].first.toDouble() shouldBe (0.75 plusOrMinus EPSILON)
        arcs[2].second.toDouble() shouldBe (0.875 plusOrMinus EPSILON)
    }

    "calculateMorphedArcs(3, 8, 1.0) matches steady pulse" {
        // Steady(3) positions: 0, 1, 2 (indices) -> 0.0, 0.333..., 0.666... (time)
        // Step duration remains 1/8 = 0.125
        val arcs = EuclideanMorphPattern.calculateMorphedArcs(3, 8, 1.0)

        arcs.size shouldBe 3
        arcs[0].first.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
        arcs[0].second.toDouble() shouldBe (0.125 plusOrMinus EPSILON)

        arcs[1].first.toDouble() shouldBe (1.0 / 3.0 plusOrMinus EPSILON)
        arcs[1].second.toDouble() shouldBe (1.0 / 3.0 + 0.125 plusOrMinus EPSILON)

        arcs[2].first.toDouble() shouldBe (2.0 / 3.0 plusOrMinus EPSILON)
        arcs[2].second.toDouble() shouldBe (2.0 / 3.0 + 0.125 plusOrMinus EPSILON)
    }

    "calculateMorphedArcs(3, 8, 0.5) matches midpoint" {
        // Midpoint between 0.375 (Euclidean) and 0.333... (Steady) for second pulse
        val arcs = EuclideanMorphPattern.calculateMorphedArcs(3, 8, 0.5)

        arcs.size shouldBe 3

        // First pulse starts at 0 for both, so stays at 0
        arcs[0].first.toDouble() shouldBe (0.0 plusOrMinus EPSILON)

        // Second pulse: Avg(0.375, 0.333...) = 0.354166...
        val expectedStart2 = (0.375 + 1.0 / 3.0) / 2.0
        arcs[1].first.toDouble() shouldBe (expectedStart2 plusOrMinus EPSILON)

        // Third pulse: Avg(0.75, 0.666...) = 0.708333...
        val expectedStart3 = (0.75 + 2.0 / 3.0) / 2.0
        arcs[2].first.toDouble() shouldBe (expectedStart3 plusOrMinus EPSILON)
    }
})
