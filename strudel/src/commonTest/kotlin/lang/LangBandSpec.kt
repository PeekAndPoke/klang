package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LangBandSpec : StringSpec({
    "band() calculates bitwise AND" {
        val p = n("3 5").band("1") // 3=11, 5=101. 1=001. 3&1=1, 5&1=1
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 1
        events[1].data.value?.asInt shouldBe 1
    }

    "band() works as top-level function" {
        val p = band("1", n("3 5"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 1
        events[1].data.value?.asInt shouldBe 1
    }

    "band() works as string extension" {
        val p = "3 5".band("1")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 1
        events[1].data.value?.asInt shouldBe 1
    }
})
