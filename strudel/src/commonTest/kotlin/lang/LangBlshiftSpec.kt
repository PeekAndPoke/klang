package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe

class LangBlshiftSpec : StringSpec({
    "blshift() calculates bitwise left shift" {
        val p = seq("1 2").blshift("1")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 2 // 1<<1 = 2
        events[1].data.value?.asInt shouldBe 4 // 2<<1 = 4
    }

    "blshift() works as top-level function" {
        val p = blshift("1")
        p.queryArc(0.0, 1.0).shouldBeEmpty()
    }

    "blshift() works as string extension" {
        val p = "1 2".blshift("1")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 2
        events[1].data.value?.asInt shouldBe 4
    }
})
