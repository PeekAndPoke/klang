package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.string.shouldBeEqualIgnoringCase

class LangJuxBySpec : StringSpec({

    "juxBy() allows adjustable stereo width" {
        // Width 0.5 -> Left: -0.5, Right: 0.5
        val p = note("c").juxBy(0.5) { it.note("e") }
        val events = p.queryArc(0.0, 1.0)

        events shouldHaveSize 2

        val left = events.find { it.data.pan == 0.25 }
        left?.data?.note shouldBeEqualIgnoringCase "c"

        val right = events.find { it.data.pan == 0.75 }
        right?.data?.note shouldBeEqualIgnoringCase "e"
    }
})
