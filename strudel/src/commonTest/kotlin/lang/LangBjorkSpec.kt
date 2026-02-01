package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LangBjorkSpec : StringSpec({

    "bjork([3, 8]) works like euclid(3, 8)" {
        val p = note("a").bjork(listOf(3, 8))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 3
        events[0].part.begin.toDouble() shouldBe 0.0
        events[1].part.begin.toDouble() shouldBe 0.375
        events[2].part.begin.toDouble() shouldBe 0.75
    }

    "bjork works as top-level function" {
        val p = bjork(listOf(3, 8), note("a"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 3
    }

    "bjork works as string extension" {
        val p = "a".bjork(listOf(3, 8))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 3
    }
})
