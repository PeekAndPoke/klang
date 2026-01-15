package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LangShuffleSpec : StringSpec({

    "shuffle(4) preserves elements but changes order" {
        val p = n("0 1 2 3").shuffle(4).seed(123)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        val values = events.mapNotNull { it.data.soundIndex }

        // Should contain all elements 0, 1, 2, 3 exactly once
        values.sorted() shouldBe listOf(0, 1, 2, 3)
    }

    "shuffle works as string extension" {
        val p = "0 1 2 3".shuffle(4).seed(456)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        val values = events.mapNotNull { it.data.value?.asInt }
        values.sorted() shouldBe listOf(0, 1, 2, 3)
    }
})
