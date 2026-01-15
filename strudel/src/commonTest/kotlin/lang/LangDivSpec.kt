package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class LangDivSpec : StringSpec({
    "div() divides numeric values" {
        val p = seq("10 20").div("2")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 5
        events[1].data.value?.asInt shouldBe 10
    }

    "div() works as top-level function" {
        val p = div("2")
        p.queryArc(0.0, 1.0).shouldBeEmpty()
    }

    "div() works as string extension" {
        val p = "10 20".div("2")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 5
        events[1].data.value?.asInt shouldBe 10
    }
})
