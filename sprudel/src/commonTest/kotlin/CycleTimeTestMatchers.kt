/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel

import io.peekandpoke.klang.common.math.CycleTime
import kotlin.math.abs

/**
 * Test convenience: many specs assert event time positions in cycles (e.g.
 * `event.part.begin shouldBe (1.0 / 4.0)`). After the migration to fixed-point [CycleTime], those
 * positions are `CycleTime`, so this more-specific `shouldBe` overload compares them against a
 * cycle value (with a tiny tolerance for subdivisions that don't land exactly on the tick grid).
 * Kotlin overload resolution prefers this over kotest's generic `shouldBe`.
 */
infix fun CycleTime.shouldBe(expectedCycles: Double) {
    val actualCycles = this.toCycles()
    if (abs(actualCycles - expectedCycles) > 1e-9) {
        throw AssertionError("expected CycleTime ≈ $expectedCycles cycles but was $this")
    }
}
