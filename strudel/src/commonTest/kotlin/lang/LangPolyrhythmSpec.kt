package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.math.Rational

class LangPolyrhythmSpec : StringSpec({

    "polyrhythm() aliases stack()" {
        // Just checking basic stack behavior since it's an alias
        val p = polyrhythm("a", "b")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        // Both play full cycle
        events.all { it.dur == Rational.ONE } shouldBe true
    }
})
