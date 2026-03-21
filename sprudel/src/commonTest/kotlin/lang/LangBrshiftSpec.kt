package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.dslInterfaceTests

class LangBrshiftSpec : StringSpec({
    "brshift() calculates bitwise right shift" {
        val p = seq("2 4").brshift("1")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 1 // 2>>1 = 1
        events[1].data.value?.asInt shouldBe 2 // 4>>1 = 2
    }

    "brshift() works as top-level PatternMapper" {
        val p = seq("8 12").apply(brshift("2"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 2  // 8>>2 = 2
        events[1].data.value?.asInt shouldBe 3  // 12>>2 = 3
    }

    "brshift() works as string extension" {
        val p = "2 4".brshift("1")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 1
        events[1].data.value?.asInt shouldBe 2
    }

    "brshift dsl interface" {
        val pat = "8 16"
        val ctrl = "2 3"

        dslInterfaceTests(
            "pattern.brshift(ctrl)" to
                    seq(pat).brshift(ctrl),
            "script pattern.brshift(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").brshift("$ctrl")"""),
            "string.brshift(ctrl)" to
                    pat.brshift(ctrl),
            "script string.brshift(ctrl)" to
                    StrudelPattern.compile(""""$pat".brshift("$ctrl")"""),
            "brshift(ctrl)" to
                    seq(pat).apply(brshift(ctrl)),
            "script brshift(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").apply(brshift("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.value?.asInt shouldBe 2  // 8 >> 2 = 2
            events[1].data.value?.asInt shouldBe 2  // 16 >> 3 = 2
        }
    }
})
