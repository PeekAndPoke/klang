package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON

class LangPureSpec : StringSpec({

    "pure() creates a value repeating every cycle" {
        val p = pure("foo")

        // Query across multiple cycles
        val events = p.queryArc(0.0, 3.0).sortedBy { it.begin }

        events.size shouldBe 3
        events[0].data.value?.asString shouldBe "foo"
        events[0].begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)

        events[1].data.value?.asString shouldBe "foo"
        events[1].begin.toDouble() shouldBe (1.0 plusOrMinus EPSILON)

        events[2].data.value?.asString shouldBe "foo"
        events[2].begin.toDouble() shouldBe (2.0 plusOrMinus EPSILON)
    }
})
