package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldBeEqualIgnoringCase
import io.peekandpoke.klang.strudel.math.Rational
import io.peekandpoke.klang.strudel.math.Rational.Companion.toRational

class LangSequencePSpec : StringSpec({

    "sequenceP() behaves like seq()" {
        val p1 = note("a")
        val p2 = note("b")

        val p = sequenceP(p1, p2)
        val events = p.queryArc(0.0, 1.0).sortedBy { it.begin }

        events.size shouldBe 2
        events[0].data.note shouldBeEqualIgnoringCase "a"
        events[0].begin shouldBe Rational.ZERO
        events[0].end shouldBe 0.5.toRational()

        events[1].data.note shouldBeEqualIgnoringCase "b"
        events[1].begin shouldBe 0.5.toRational()
        events[1].end shouldBe Rational.ONE
    }
})
