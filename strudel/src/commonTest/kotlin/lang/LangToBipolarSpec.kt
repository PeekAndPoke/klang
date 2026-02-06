package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LangToBipolarSpec : StringSpec({

    "toBipolar() maps 0..1 to -1..1" {
        val p = seq("0 0.5 1").toBipolar()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 3
        events[0].data.value?.asDouble shouldBe -1.0 // 0 -> -1
        events[1].data.value?.asDouble shouldBe 0.0 // 0.5 -> 0
        events[2].data.value?.asDouble shouldBe 1.0 // 1 -> 1
    }

    "toBipolar works with pattern receiver" {
        val p = "0 1".toBipolar()
        val events = p.queryArc(0.0, 1.0)
        events[0].data.value?.asDouble shouldBe -1.0
        events[1].data.value?.asDouble shouldBe 1.0
    }
})
