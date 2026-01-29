package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe

class LangDropSpec : StringSpec({

    "drop() skips first n steps and stretches remaining" {
        val p = note("c d e f").drop(2)

        // Should drop first 2 steps (c, d), keep last 2 (e, f) stretched to fill cycle
        val events = p.queryArc(0.0, 1.0)
        events shouldHaveSize 2
        events.map { it.data.note } shouldBe listOf("e", "f")
        // Events stretched: e occupies 0.0-0.5, f occupies 0.5-1.0
        events[0].begin.toDouble() shouldBe 0.0
        events[0].end.toDouble() shouldBe 0.5
        events[1].begin.toDouble() shouldBe 0.5
        events[1].end.toDouble() shouldBe 1.0
    }
})
