package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.dslInterfaceTests

class LangLog2Spec : StringSpec({
    "log2() calculates base-2 logarithm" {
        val p = seq("1 2 4 8").log2()
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 4
        events[0].data.value?.asInt shouldBe 0 // log2(1) = 0
        events[1].data.value?.asInt shouldBe 1 // log2(2) = 1
        events[2].data.value?.asInt shouldBe 2 // log2(4) = 2
        events[3].data.value?.asInt shouldBe 3 // log2(8) = 3
    }

    "log2() works as top-level PatternMapper" {
        val p = seq("1 2 4 8").apply(log2())
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 4
        events[0].data.value?.asInt shouldBe 0
        events[1].data.value?.asInt shouldBe 1
        events[2].data.value?.asInt shouldBe 2
        events[3].data.value?.asInt shouldBe 3
    }

    "log2() works as string extension" {
        val p = "8".log2()
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.value?.asInt shouldBe 3
    }

    "log2 dsl interface" {
        val pat = "1 2 4 8"

        dslInterfaceTests(
            "pattern.log2()" to
                    seq(pat).log2(),
            "script pattern.log2()" to
                    StrudelPattern.compile("""seq("$pat").log2()"""),
            "string.log2()" to
                    pat.log2(),
            "script string.log2()" to
                    StrudelPattern.compile(""""$pat".log2()"""),
            "log2()" to
                    seq(pat).apply(log2()),
            "script log2()" to
                    StrudelPattern.compile("""seq("$pat").apply(log2())"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.value?.asInt shouldBe 0  // log2(1) = 0
            events[1].data.value?.asInt shouldBe 1  // log2(2) = 1
            events[2].data.value?.asInt shouldBe 2  // log2(4) = 2
            events[3].data.value?.asInt shouldBe 3  // log2(8) = 3
        }
    }
})