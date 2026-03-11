package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.dslInterfaceTests

class LangBorSpec : StringSpec({
    "bor() calculates bitwise OR" {
        val p = seq("1 4").bor("2") // 1=001, 4=100. 2=010. 1|2=3, 4|2=6
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 3
        events[1].data.value?.asInt shouldBe 6
    }

    "bor() works as top-level PatternMapper" {
        val p = seq("1 4").apply(bor("2"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 3
        events[1].data.value?.asInt shouldBe 6
    }

    "bor() works as string extension" {
        val p = "1 4".bor("2")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 3
        events[1].data.value?.asInt shouldBe 6
    }

    "bor dsl interface" {
        val pat = "8 4"
        val ctrl = "2 3"

        dslInterfaceTests(
            "pattern.bor(ctrl)" to
                    seq(pat).bor(ctrl),
            "script pattern.bor(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").bor("$ctrl")"""),
            "string.bor(ctrl)" to
                    pat.bor(ctrl),
            "script string.bor(ctrl)" to
                    StrudelPattern.compile(""""$pat".bor("$ctrl")"""),
            "bor(ctrl)" to
                    seq(pat).apply(bor(ctrl)),
            "script bor(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").apply(bor("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.value?.asInt shouldBe 10  // 8 | 2 = 10
            events[1].data.value?.asInt shouldBe 7   // 4 | 3 = 7
        }
    }
})
