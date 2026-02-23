package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.dslInterfaceTests

class LangBandSpec : StringSpec({
    "band() calculates bitwise AND" {
        val p = seq("3 5").band("1") // 3=11, 5=101. 1=001. 3&1=1, 5&1=1
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 1
        events[1].data.value?.asInt shouldBe 1
    }

    "band() works as top-level PatternMapper" {
        val p = seq("3 5").apply(band("1"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 1
        events[1].data.value?.asInt shouldBe 1
    }

    "band() works as string extension" {
        val p = "3 5".band("1")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 1
        events[1].data.value?.asInt shouldBe 1
    }

    "band dsl interface" {
        val pat = "12 15"
        val ctrl = "10 6"

        dslInterfaceTests(
            "pattern.band(ctrl)" to
                    seq(pat).band(ctrl),
            "script pattern.band(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").band("$ctrl")"""),
            "string.band(ctrl)" to
                    pat.band(ctrl),
            "script string.band(ctrl)" to
                    StrudelPattern.compile(""""$pat".band("$ctrl")"""),
            "band(ctrl)" to
                    seq(pat).apply(band(ctrl)),
            "script band(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").apply(band("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.value?.asInt shouldBe 8   // 12 & 10 = 8
            events[1].data.value?.asInt shouldBe 6   // 15 & 6 = 6
        }
    }
})