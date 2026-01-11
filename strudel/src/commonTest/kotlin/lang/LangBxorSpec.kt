package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LangBxorSpec : StringSpec({
    "bxor() calculates bitwise XOR" {
        val p = n("3 5").bxor("1") // 3=011, 5=101. 1=001. 3^1=2, 5^1=4
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 2
        events[1].data.value?.asInt shouldBe 4
    }

    "bxor() works as top-level function" {
        val p = bxor("1", n("3 5"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 2
        events[1].data.value?.asInt shouldBe 4
    }

    "bxor() works as string extension" {
        val p = "3 5".bxor("1")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 2
        events[1].data.value?.asInt shouldBe 4
    }
})
