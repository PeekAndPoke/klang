package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LangExtendSpec : StringSpec({

    "extend() is alias for fast()" {
        val p1 = note("c d e f").extend(2)
        val p2 = note("c d e f").fast(2)

        val events1 = p1.queryArc(0.0, 2.0)
        val events2 = p2.queryArc(0.0, 2.0)

        events1.size shouldBe events2.size

        // With fast(2), pattern plays twice as fast
        // "c d e f" has 4 events per cycle normally
        // fast(2) gives 8 events per cycle
        events1.size shouldBe 16  // 8 events/cycle × 2 cycles
    }
})
