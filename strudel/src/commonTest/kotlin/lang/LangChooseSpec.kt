package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeIn
import io.kotest.matchers.shouldBe

class LangChooseSpec : StringSpec({

    "choose() picks randomly" {
        val p = choose("a", "b").seed(123)
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.value?.asString shouldBeIn listOf("a", "b")
    }

    "chooseOut alias works" {
        val p = chooseOut("a", "b").seed(123)
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
    }

    "choose as pattern extension uses pattern as selector" {
        val p = seq("0 1").choose("a", "b")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asString shouldBe "a"
        events[1].data.value?.asString shouldBe "b"
    }

    "choose as string extension uses pattern as selector" {
        val p = "0 1".choose("a", "b")
        val events = p.queryArc(0.0, 1.0)
        events[0].data.value?.asString shouldBe "a"
        events[1].data.value?.asString shouldBe "b"
    }
})
