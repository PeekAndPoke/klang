package io.peekandpoke.klang.strudel.lang.addons

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.lang.seq

class LangArithmeticAddonsSpec : StringSpec({

    "negateValue()" {
        val p = seq(1.0, -0.5, 0.0).flipSign()
        val events = p.queryArc(0.0, 3.0)

        events.size shouldBe 9

        events[0].data.value?.asDouble shouldBe (-1.0 plusOrMinus EPSILON)
        events[1].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
        events[2].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON) // -0.0 is 0.0

        events[3].data.value?.asDouble shouldBe (-1.0 plusOrMinus EPSILON)
        events[4].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
        events[5].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON) // -0.0 is 0.0

        events[6].data.value?.asDouble shouldBe (-1.0 plusOrMinus EPSILON)
        events[7].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
        events[8].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON) // -0.0 is 0.0
    }

    "negateValue() as string extension" {
        val p = "1 -0.5".flipSign()
        val events = p.queryArc(0.0, 2.0)

        events.size shouldBe 4

        events[0].data.value?.asDouble shouldBe (-1.0 plusOrMinus EPSILON)
        events[1].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)

        events[2].data.value?.asDouble shouldBe (-1.0 plusOrMinus EPSILON)
        events[3].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
    }

    "oneMinusValue()" {
        // 1.0 - 0.2 = 0.8
        // 1.0 - 1.0 = 0.0
        // 1.0 - 0.0 = 1.0
        // 1.0 - (-0.5) = 1.5
        val p = seq(0.2, 1.0, 0.0, -0.5).oneMinusValue()
        val events = p.queryArc(0.0, 2.0)

        events.size shouldBe 8

        events[0].data.value?.asDouble shouldBe (0.8 plusOrMinus EPSILON)
        events[1].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
        events[2].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
        events[3].data.value?.asDouble shouldBe (1.5 plusOrMinus EPSILON)

        events[4].data.value?.asDouble shouldBe (0.8 plusOrMinus EPSILON)
        events[5].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
        events[6].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
        events[7].data.value?.asDouble shouldBe (1.5 plusOrMinus EPSILON)
    }

    "oneMinusValue() as string extension" {
        val p = "0.2 0.8".oneMinusValue()
        val events = p.queryArc(0.0, 2.0)

        events.size shouldBe 4

        events[0].data.value?.asDouble shouldBe (0.8 plusOrMinus EPSILON)
        events[1].data.value?.asDouble shouldBe (0.2 plusOrMinus EPSILON)

        events[2].data.value?.asDouble shouldBe (0.8 plusOrMinus EPSILON)
        events[3].data.value?.asDouble shouldBe (0.2 plusOrMinus EPSILON)
    }
})
