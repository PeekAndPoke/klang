package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class LangMulSpec : StringSpec({
    "mul() multiplies numeric values" {
        val p = seq("2 3").mul("4")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 8
        events[1].data.value?.asInt shouldBe 12
    }

    "mul() works as top-level function" {
        val p = mul("4")
        p.queryArc(0.0, 1.0).shouldBeEmpty()
    }

    "mul() works as string extension" {
        val p = "2 3".mul("4")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 8
        events[1].data.value?.asInt shouldBe 12
    }
})
