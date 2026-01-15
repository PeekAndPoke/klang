package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe

class LangChooseInSpec : StringSpec({

    "chooseIn picks randomly" {
        val p = chooseIn("a", "b").seed(123)
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.value?.asString shouldBeIn listOf("a", "b)")
    }

    "chooseIn works as pattern extension" {
        // Uses pattern as selector
        val p = seq("0 1").chooseIn("a", "b")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asString shouldBe "a"
        events[1].data.value?.asString shouldBe "b"
    }

    "chooseIn works as string extension" {
        val p = "0 1".chooseIn("a", "b")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asString shouldBe "a"
        events[1].data.value?.asString shouldBe "b"
    }
})
