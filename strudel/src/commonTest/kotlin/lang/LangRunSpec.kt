package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LangRunSpec : StringSpec({

    "run(4) generates 0 1 2 3" {
        val p = run(4)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        events[0].data.value?.asInt shouldBe 0
        events[1].data.value?.asInt shouldBe 1
        events[2].data.value?.asInt shouldBe 2
        events[3].data.value?.asInt shouldBe 3
    }

    "run(0) is silence" {
        val p = run(0)
        p.queryArc(0.0, 1.0).size shouldBe 0
    }

    "run used in n()" {
        val p = n(run(4))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 4
        events[0].data.soundIndex shouldBe 0
        events[3].data.soundIndex shouldBe 3
    }
})
