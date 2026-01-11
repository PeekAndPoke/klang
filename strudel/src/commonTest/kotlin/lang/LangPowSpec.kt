package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LangPowSpec : StringSpec({
    "pow() calculates power" {
        val p = n("2 3").pow("3")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 8
        events[1].data.value?.asInt shouldBe 27
    }

    "pow() works as top-level function" {
        val p = pow("3", n("2 3"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 8
        events[1].data.value?.asInt shouldBe 27
    }

    "pow() works as string extension" {
        val p = "2 3".pow("3")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 8
        events[1].data.value?.asInt shouldBe 27
    }
})
