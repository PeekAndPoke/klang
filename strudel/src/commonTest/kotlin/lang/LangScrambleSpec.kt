package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class LangScrambleSpec : StringSpec({

    "scramble(4) picks 4 slices randomly" {
        val p = n("0 1 2 3").scramble(4).seed(789)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        val values = events.mapNotNull { it.data.soundIndex }

        // All values should be from the original set 0..3
        // Note: scramble allows repetition (replacement), unlike shuffle
        values.all { it in 0..3 } shouldBe true
    }

    "scramble works as string extension" {
        val p = "0 1 2 3".scramble(4).seed(999)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        val values = events.mapNotNull { it.data.value?.asInt }
        values.all { it in 0..3 } shouldBe true
    }
})
