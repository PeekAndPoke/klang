/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.common.math

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * Pins the fixed-point [CycleTime] primitive semantics — especially the cases the migration review
 * flagged: negative-time floor/frac/mod, exact tuplet snapping, and round-half-up (not banker's).
 */
class CycleTimeSpec : StringSpec({

    val T = CycleTime.T // 110100480.0

    "T is 2^20 * 3 * 5 * 7" {
        T shouldBe 1048576.0 * 3 * 5 * 7
        T shouldBe 110100480.0
    }

    // --- Exact arithmetic (no rounding) ---

    "plus / minus / times are exact integer tick arithmetic" {
        (CycleTime.ofCycleIndex(3) - CycleTime.ofCycleIndex(1)) shouldBe CycleTime.ofCycleIndex(2)
        (CycleTime.HALF + CycleTime.HALF) shouldBe CycleTime.ONE
        (CycleTime.QUARTER * 4) shouldBe CycleTime.ONE
    }

    // --- Tuplet snapping: round-half-up keeps thirds exact (plain floor would be one tick low) ---

    "ofSubdivision tiles exactly for divisors of T (triplets, quintuplets, septuplets)" {
        CycleTime.ofSubdivision(1, 3).ticks shouldBe 36700160.0     // T/3 exact
        CycleTime.ofSubdivision(3, 3) shouldBe CycleTime.ONE         // 3 thirds == 1 cycle
        CycleTime.ofSubdivision(5, 5) shouldBe CycleTime.ONE
        CycleTime.ofSubdivision(7, 7) shouldBe CycleTime.ONE
    }

    "ofCycles(1/3) rounds to the nearest tick (36700160), not truncated (36700159)" {
        // 1.0/3.0 is just below a true third; plain floor would give 36700159.
        CycleTime.ofCycles(1.0 / 3.0).ticks shouldBe 36700160.0
    }

    "ofCycles never produces -0.0" {
        CycleTime.ofCycles(-0.0).ticks shouldBe 0.0
        (CycleTime.ofCycles(-0.0) == CycleTime.ZERO) shouldBe true
    }

    // --- Negative-time semantics (early()/rev() can reach negative cycle positions) ---

    "cycleIndex floors toward -infinity for negative times" {
        CycleTime.ofCycles(-0.25).cycleIndex() shouldBe -1
        CycleTime.ofCycleIndex(-1).cycleIndex() shouldBe -1
        CycleTime.ofCycles(0.25).cycleIndex() shouldBe 0
    }

    "floorToCycle / ceilToCycle for negative times" {
        CycleTime.ofCycles(-0.25).floorToCycle() shouldBe CycleTime.ofCycleIndex(-1)
        CycleTime.ofCycles(-0.25).ceilToCycle() shouldBe CycleTime.ZERO
        CycleTime.ofCycleIndex(-1).ceilToCycle() shouldBe CycleTime.ofCycleIndex(-1) // already on boundary
    }

    "fracOfCycle is always in [0,1) including negatives" {
        CycleTime.ofCycles(-0.25).fracOfCycle().toCycles() shouldBe 0.75
        CycleTime.ofCycles(0.25).fracOfCycle().toCycles() shouldBe 0.25
        CycleTime.ofCycleIndex(-2).fracOfCycle() shouldBe CycleTime.ZERO
    }

    "modCycles wraps to a non-negative result" {
        // -0.25 cycle mod 1 cycle == 0.75 cycle
        CycleTime.ofCycles(-0.25).modCycles(CycleTime.ONE).toCycles() shouldBe 0.75
        CycleTime.ofCycles(2.5).modCycles(CycleTime.ONE).toCycles() shouldBe 0.5
    }
})
