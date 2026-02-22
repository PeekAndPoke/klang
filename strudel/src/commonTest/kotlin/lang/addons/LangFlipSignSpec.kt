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

class LangFlipSignSpec : StringSpec({

    "flipSign dsl interface" {

        val pat = "0 1.1 -2.3"

        dslInterfaceTests(
            "pattern.flipSign" to
                    seq(pat).flipSign(),
            "string.flipSign" to
                    pat.flipSign(),
            "flipSign()" to
                    seq(pat).apply(flipSign),
            "script pattern.flipSign" to
                    StrudelPattern.compile("""seq("$pat").flipSign()"""),
            "script string.flipSign" to
                    StrudelPattern.compile(""""$pat".flipSign()"""),
            "script flipSign()" to
                    StrudelPattern.compile("""seq("$pat").apply(flipSign)"""),
        ) { _, events ->
            events.shouldHaveSize(3)
            events[0].data.value?.asDouble shouldBe 0.0
            events[1].data.value?.asDouble shouldBe -1.1
            events[2].data.value?.asDouble shouldBe 2.3
        }
    }

    "flipSign()" {
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

    "flipSign() as string extension" {
        val p = "1 -0.5".flipSign()
        val events = p.queryArc(0.0, 2.0)

        events.size shouldBe 4

        events[0].data.value?.asDouble shouldBe (-1.0 plusOrMinus EPSILON)
        events[1].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)

        events[2].data.value?.asDouble shouldBe (-1.0 plusOrMinus EPSILON)
        events[3].data.value?.asDouble shouldBe (0.5 plusOrMinus EPSILON)
    }
})
