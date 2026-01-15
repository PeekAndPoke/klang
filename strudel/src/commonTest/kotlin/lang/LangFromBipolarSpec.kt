package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LangFromBipolarSpec : StringSpec({

    "fromBipolar() maps -1..1 to 0..1" {
        val p = seq("-1 0 1").fromBipolar()
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 3
        events[0].data.value?.asDouble shouldBe 0.0 // -1 -> 0
        events[1].data.value?.asDouble shouldBe 0.5 // 0 -> 0.5
        events[2].data.value?.asDouble shouldBe 1.0 // 1 -> 1
    }

    "fromBipolar works with pattern receiver" {
        val p = "-1 1".fromBipolar()
        val events = p.queryArc(0.0, 1.0)
        events[0].data.value?.asDouble shouldBe 0.0
        events[1].data.value?.asDouble shouldBe 1.0
    }
})
