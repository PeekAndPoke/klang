package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LangEuclidRotSpec : StringSpec({

    "euclidRot(3, 8, 1) rotates the rhythm" {
        // 3,8 is 10010010
        // Rotating by 1 (right shift in Strudel logic usually) -> 01001001
        // Indices: 1, 4, 7
        // Times: 0.125, 0.5, 0.875

        val p = note("a").euclidRot(3, 8, 1)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 3
        events[0].begin.toDouble() shouldBe 0.125
        events[1].begin.toDouble() shouldBe 0.5
        events[2].begin.toDouble() shouldBe 0.875
    }

    "euclidRot works as top-level function" {
        val p = euclidRot(3, 8, 1, note("a"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 3
        events[0].begin.toDouble() shouldBe 0.125
    }

    "euclidRot works as string extension" {
        val p = "a".euclidRot(3, 8, 1)
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 3
        events[0].begin.toDouble() shouldBe 0.125
    }
})
