package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class LangBorSpec : StringSpec({
    "bor() calculates bitwise OR" {
        val p = seq("1 4").bor("2") // 1=001, 4=100. 2=010. 1|2=3, 4|2=6
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 3
        events[1].data.value?.asInt shouldBe 6
    }

    "bor() works as top-level function" {
        val p = bor("2")
        p.queryArc(0.0, 1.0).shouldBeEmpty()
    }

    "bor() works as string extension" {
        val p = "1 4".bor("2")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 3
        events[1].data.value?.asInt shouldBe 6
    }
})
