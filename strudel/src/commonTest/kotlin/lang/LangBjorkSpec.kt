package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase

class LangBjorkSpec : StringSpec({

    "bjork([3, 8]) works like euclid(3, 8)" {
        val p = note("a").bjork(pulses = 3, steps = 8)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 3
        events[0].part.begin.toDouble() shouldBe 0.0
        events[1].part.begin.toDouble() shouldBe 0.375
        events[2].part.begin.toDouble() shouldBe 0.75
    }

    "bjork works as top-level function" {
        val p = bjork(pulses = 3, steps = 8, rotation = 0, pattern = note("a"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 3

        events[0].data.note shouldBeEqualIgnoringCase "a"
        events[1].data.note shouldBeEqualIgnoringCase "a"
        events[2].data.note shouldBeEqualIgnoringCase "a"
    }

    "bjork works as string extension" {
        val p = "a".bjork(pulses = 3, steps = 8)
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 3
    }
})
