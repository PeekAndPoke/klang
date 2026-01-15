package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LangChoose2Spec : StringSpec({

    "choose2 maps -1..1 to choice" {
        val p = seq("-1 1").choose2("a", "b")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asString shouldBe "a"
        events[1].data.value?.asString shouldBe "b"
    }

    "choose2 as string extension" {
        val p = "-1 1".choose2("a", "b")
        val events = p.queryArc(0.0, 1.0)
        events[0].data.value?.asString shouldBe "a"
        events[1].data.value?.asString shouldBe "b"
    }
})
