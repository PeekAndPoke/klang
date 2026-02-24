package io.peekandpoke.klang.strudel.lang.addons

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.dslInterfaceTests
import io.peekandpoke.klang.strudel.lang.add
import io.peekandpoke.klang.strudel.lang.apply
import io.peekandpoke.klang.strudel.lang.seq

class LangAbsSpec : StringSpec({

    "abs dsl interface" {
        val pat = "-3 -1 0 2"

        dslInterfaceTests(
            "pattern.abs" to
                    seq(pat).abs(),
            "string.abs" to
                    pat.abs(),
            "abs()" to
                    seq(pat).apply(abs),
            "script pattern.abs" to
                    StrudelPattern.compile("""seq("$pat").abs()"""),
            "script string.abs" to
                    StrudelPattern.compile(""""$pat".abs()"""),
            "script abs()" to
                    StrudelPattern.compile("""seq("$pat").apply(abs)"""),
        ) { _, events ->
            events.shouldHaveSize(4)
            events[0].data.value?.asDouble shouldBe (3.0 plusOrMinus EPSILON)   // abs(-3) = 3
            events[1].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)   // abs(-1) = 1
            events[2].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)   // abs(0)  = 0
            events[3].data.value?.asDouble shouldBe (2.0 plusOrMinus EPSILON)   // abs(2)  = 2
        }
    }

    "abs() returns absolute values" {
        val p = seq(-3.0, -1.0, 0.0, 2.0).abs()
        val events = p.queryArc(0.0, 2.0)

        events.size shouldBe 8

        events[0].data.value?.asDouble shouldBe (3.0 plusOrMinus EPSILON)
        events[1].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
        events[2].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
        events[3].data.value?.asDouble shouldBe (2.0 plusOrMinus EPSILON)

        events[4].data.value?.asDouble shouldBe (3.0 plusOrMinus EPSILON)
        events[5].data.value?.asDouble shouldBe (1.0 plusOrMinus EPSILON)
        events[6].data.value?.asDouble shouldBe (0.0 plusOrMinus EPSILON)
        events[7].data.value?.asDouble shouldBe (2.0 plusOrMinus EPSILON)
    }

    "apply(add().abs())" {
        val p = seq("1 -2").apply(add("-4").abs())
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asDouble shouldBe (3.0 plusOrMinus EPSILON)  // abs(1-4)=abs(-3)=3
            events[1].data.value?.asDouble shouldBe (6.0 plusOrMinus EPSILON)  // abs(-2-4)=abs(-6)=6
        }
    }

    "script apply(add().abs())" {
        val p = StrudelPattern.compile("""seq("1 -2").apply(add("-4").abs())""")!!
        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.shouldHaveSize(2)
            events[0].data.value?.asDouble shouldBe (3.0 plusOrMinus EPSILON)  // abs(1-4)=abs(-3)=3
            events[1].data.value?.asDouble shouldBe (6.0 plusOrMinus EPSILON)  // abs(-2-4)=abs(-6)=6
        }
    }

    "abs() as string extension" {
        val p = "-3 2".abs()
        val events = p.queryArc(0.0, 2.0)

        events.size shouldBe 4

        events[0].data.value?.asDouble shouldBe (3.0 plusOrMinus EPSILON)
        events[1].data.value?.asDouble shouldBe (2.0 plusOrMinus EPSILON)

        events[2].data.value?.asDouble shouldBe (3.0 plusOrMinus EPSILON)
        events[3].data.value?.asDouble shouldBe (2.0 plusOrMinus EPSILON)
    }
})