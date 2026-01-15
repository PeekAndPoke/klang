package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class LangBrshiftSpec : StringSpec({
    "brshift() calculates bitwise right shift" {
        val p = seq("2 4").brshift("1")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 1 // 2>>1 = 1
        events[1].data.value?.asInt shouldBe 2 // 4>>1 = 2
    }

    "brshift() works as top-level function" {
        val p = brshift("1")
        p.queryArc(0.0, 1.0).shouldBeEmpty()
    }

    "brshift() works as string extension" {
        val p = "2 4".brshift("1")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 1
        events[1].data.value?.asInt shouldBe 2
    }
})
