package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LangWChooseSpec : StringSpec({

    "wchoose respects weights" {
        val p = wchoose(listOf("a", 10), listOf("b", 0)).seed(123)
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "a"
    }

    "wchoose pattern extension uses pattern as selector with weights" {
        // pattern 0 -> first item ("a", 10)
        val p = seq("0").wchoose(listOf("a", 10), listOf("b", 1))
        val events = p.queryArc(0.0, 1.0)
        events[0].data.value?.asString shouldBe "a"
    }

    "wchoose works as string extension" {
        val p = "0".wchoose(listOf("a", 10), listOf("b", 1))
        val events = p.queryArc(0.0, 1.0)
        events[0].data.value?.asString shouldBe "a"
    }
})
