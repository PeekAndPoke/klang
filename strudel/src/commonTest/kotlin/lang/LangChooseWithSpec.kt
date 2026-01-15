package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LangChooseWithSpec : StringSpec({

    "chooseWith(pat, list) selects based on pattern value" {
        // Selector: <0 1>
        val selector = seq("0 1")
        val choices = listOf("a", "b")
        val p = chooseWith(selector, choices)

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asString shouldBe "a"
        events[1].data.value?.asString shouldBe "b"
    }

    "chooseWith works as pattern extension" {
        val p = seq("0 1").chooseWith("a", "b")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asString shouldBe "a"
        events[1].data.value?.asString shouldBe "b"
    }

    "chooseWith works as string extension" {
        val p = "0 1".chooseWith("a", "b")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asString shouldBe "a"
        events[1].data.value?.asString shouldBe "b"
    }
})
