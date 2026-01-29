package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe

class LangPaceSpec : StringSpec({

    "pace() adjusts speed to match target steps" {
        // Pattern with 4 steps, pace to 8 should double speed
        val p = note("c d e f").pace(8)
        val events = p.queryArc(0.0, 1.0)

        // Should have more events (2x speed = 2 cycles in 1)
        events.size shouldBeGreaterThan 4
    }

    "steps() is alias for pace()" {
        val p1 = note("c d e f").pace(8)
        val p2 = note("c d e f").steps(8)

        val events1 = p1.queryArc(0.0, 1.0)
        val events2 = p2.queryArc(0.0, 1.0)

        events1.size shouldBe events2.size
    }
})
