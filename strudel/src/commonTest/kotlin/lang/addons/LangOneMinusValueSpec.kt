package io.peekandpoke.klang.strudel.lang.addons

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.dslInterfaceTests
import io.peekandpoke.klang.strudel.lang.apply
import io.peekandpoke.klang.strudel.lang.seq

class LangOneMinusValueSpec : StringSpec({

    "oneMinusValue dsl interface" {

        val pat = "0 1.1 -2.3"

        dslInterfaceTests(
            "pattern.oneMinusValue" to
                    seq(pat).oneMinusValue(),
            "string.oneMinusValue" to
                    pat.oneMinusValue(),
            "oneMinusValue()" to
                    seq(pat).apply(oneMinusValue),
            "script pattern.oneMinusValue" to
                    StrudelPattern.compile("""seq("$pat").oneMinusValue()"""),
            "script string.oneMinusValue" to
                    StrudelPattern.compile(""""$pat".oneMinusValue()"""),
            "script oneMinusValue()" to
                    StrudelPattern.compile("""seq("$pat").apply(oneMinusValue)"""),
        ) { _, events ->
            events.shouldHaveSize(3)
            events[0].data.value?.asDouble shouldBe 1.0
            events[1].data.value?.asDouble shouldBe -0.1
            events[2].data.value?.asDouble shouldBe 3.3
        }
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
