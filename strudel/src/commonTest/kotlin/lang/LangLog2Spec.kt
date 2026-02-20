package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class LangLog2Spec : StringSpec({
    "log2() calculates base-2 logarithm" {
        val p = seq("1 2 4 8").log2()
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 4
        events[0].data.value?.asInt shouldBe 0 // log2(1) = 0
        events[1].data.value?.asInt shouldBe 1 // log2(2) = 1
        events[2].data.value?.asInt shouldBe 2 // log2(4) = 2
        events[3].data.value?.asInt shouldBe 3 // log2(8) = 3
    }

    "log2() works as top-level function" {
        val p = log2()
        p.queryArc(0.0, 1.0).shouldBeEmpty()
    }

    "log2() works as string extension" {
        val p = "8".log2()
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.value?.asInt shouldBe 3
    }
})
