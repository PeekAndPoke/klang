package io.peekandpoke.klang.strudel.pattern

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.math.Rational

class GapPatternSpec : StringSpec({

    "GapPattern returns no events and preserves steps" {
        val pattern = GapPattern(Rational(3))
        pattern.numSteps shouldBe Rational(3)
        pattern.queryArc(0.0, 1.0).size shouldBe 0
    }
})
