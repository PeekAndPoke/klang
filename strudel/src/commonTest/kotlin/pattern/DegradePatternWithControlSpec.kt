package io.peekandpoke.klang.strudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.lang.note
import io.peekandpoke.klang.strudel.lang.steady

class DegradePatternWithControlSpec : StringSpec({

    "DegradePatternWithControl samples probability from pattern (0.0)" {
        val source = note("a b c d")
        val prob = steady(0.0)
        val pattern = DegradePatternWithControl(source, prob)

        pattern.queryArc(0.0, 1.0).size shouldBe 4
    }

    "DegradePatternWithControl samples probability from pattern (1.0)" {
        val source = note("a b c d")
        val prob = steady(1.0)
        val pattern = DegradePatternWithControl(source, prob)

        pattern.queryArc(0.0, 1.0).size shouldBe 0
    }
})
