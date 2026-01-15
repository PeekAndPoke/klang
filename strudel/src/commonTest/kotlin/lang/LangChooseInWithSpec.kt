package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LangChooseInWithSpec : StringSpec({

    "chooseInWith selects based on pattern value" {
        val p = chooseInWith(seq("0 1"), listOf("a", "b"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asString shouldBe "a"
        events[1].data.value?.asString shouldBe "b"
    }

    "chooseInWith as pattern extension" {
        val p = seq("0 1").chooseInWith("a", "b")
        val events = p.queryArc(0.0, 1.0)
        events[0].data.value?.asString shouldBe "a"
        events[1].data.value?.asString shouldBe "b"
    }

    "chooseInWith as string extension" {
        val p = "0 1".chooseInWith("a", "b")
        val events = p.queryArc(0.0, 1.0)
        events[0].data.value?.asString shouldBe "a"
        events[1].data.value?.asString shouldBe "b"
    }
})
