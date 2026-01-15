package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class LangSubSpec : StringSpec({
    "sub() subtracts amount from numeric values" {
        val p = seq("10 20").sub("5")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 5
        events[1].data.value?.asInt shouldBe 15
    }

    "sub() works as top-level function" {
        val p = sub("5")
        p.queryArc(0.0, 1.0).shouldBeEmpty()
    }

    "sub() works as string extension" {
        val p = "10 20".sub("5")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 5
        events[1].data.value?.asInt shouldBe 15
    }
})
