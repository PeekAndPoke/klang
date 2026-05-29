package io.peekandpoke.klang.sprudel

import io.peekandpoke.klang.common.math.CycleTime
import io.peekandpoke.klang.common.math.Rational
import kotlin.math.abs

/**
 * Test convenience: many specs assert event time positions against [Rational] literals
 * (e.g. `event.part.begin shouldBe (1.0 / 4.0).toRational()`). After the migration to fixed-point
 * [CycleTime], those positions are `CycleTime`, so this more-specific `shouldBe` overload compares
 * them as cycle values (with a tiny tolerance for subdivisions that don't land exactly on the tick
 * grid). Kotlin overload resolution prefers this over kotest's generic `shouldBe`.
 */
infix fun CycleTime.shouldBe(expected: Rational) {
    val actualCycles = this.toCycles()
    val expectedCycles = expected.toDouble()
    if (abs(actualCycles - expectedCycles) > 1e-9) {
        throw AssertionError("expected CycleTime ≈ $expected ($expectedCycles cycles) but was $this")
    }
}
