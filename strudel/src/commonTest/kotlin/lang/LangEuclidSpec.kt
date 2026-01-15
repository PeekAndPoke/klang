package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LangEuclidSpec : StringSpec({

    "euclid(3, 8) produces correct rhythm" {
        // Standard Euclidean rhythm for 3,8 is 10010010
        // Time steps: 0/8, 1/8, ... 7/8
        // Active steps: 0, 3, 6
        // Times: 0.0, 0.375, 0.75

        val p = note("a").euclid(3, 8)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 3
        events[0].begin.toDouble() shouldBe 0.0
        events[1].begin.toDouble() shouldBe 0.375
        events[2].begin.toDouble() shouldBe 0.75
    }

    "euclid work as top-level function" {
        val p = euclid(3, 8, note("a"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 3
    }

    "euclid works as string extension" {
        val p = "a".euclid(3, 8)
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 3
    }
})
