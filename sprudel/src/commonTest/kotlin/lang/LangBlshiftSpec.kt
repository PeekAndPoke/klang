package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.StrudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangBlshiftSpec : StringSpec({
    "blshift() calculates bitwise left shift" {
        val p = seq("1 2").blshift("1")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 2 // 1<<1 = 2
        events[1].data.value?.asInt shouldBe 4 // 2<<1 = 4
    }

    "blshift() works as top-level PatternMapper" {
        val p = seq("1 2").apply(blshift("1"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 2
        events[1].data.value?.asInt shouldBe 4
    }

    "blshift() works as string extension" {
        val p = "1 2".blshift("1")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 2
        events[1].data.value?.asInt shouldBe 4
    }

    "blshift dsl interface" {
        val pat = "1 2"
        val ctrl = "2 3"

        dslInterfaceTests(
            "pattern.blshift(ctrl)" to
                    seq(pat).blshift(ctrl),
            "script pattern.blshift(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").blshift("$ctrl")"""),
            "string.blshift(ctrl)" to
                    pat.blshift(ctrl),
            "script string.blshift(ctrl)" to
                    StrudelPattern.compile(""""$pat".blshift("$ctrl")"""),
            "blshift(ctrl)" to
                    seq(pat).apply(blshift(ctrl)),
            "script blshift(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").apply(blshift("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.value?.asInt shouldBe 4   // 1 << 2 = 4
            events[1].data.value?.asInt shouldBe 16  // 2 << 3 = 16
        }
    }
})
