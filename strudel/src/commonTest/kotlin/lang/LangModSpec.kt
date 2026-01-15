package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class LangModSpec : StringSpec({
    "mod() calculates modulo" {
        val p = seq("10 11").mod("3")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 1
        events[1].data.value?.asInt shouldBe 2
    }

    "mod() works as top-level function" {
        val p = mod("3")
        p.queryArc(0.0, 1.0).shouldBeEmpty()
    }

    "mod() works as string extension" {
        val p = "10 11".mod("3")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 1
        events[1].data.value?.asInt shouldBe 2
    }
})
