package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.dslInterfaceTests

class LangBxorSpec : StringSpec({
    "bxor() calculates bitwise XOR" {
        val p = seq("3 5").bxor("1") // 3=011, 5=101. 1=001. 3^1=2, 5^1=4
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 2
        events[1].data.value?.asInt shouldBe 4
    }

    "bxor() works as top-level PatternMapper" {
        val p = seq("3 5").apply(bxor("1"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 2
        events[1].data.value?.asInt shouldBe 4
    }

    "bxor() works as string extension" {
        val p = "3 5".bxor("1")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.value?.asInt shouldBe 2
        events[1].data.value?.asInt shouldBe 4
    }

    "bxor dsl interface" {
        val pat = "12 10"
        val ctrl = "6 3"

        dslInterfaceTests(
            "pattern.bxor(ctrl)" to
                    seq(pat).bxor(ctrl),
            "script pattern.bxor(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").bxor("$ctrl")"""),
            "string.bxor(ctrl)" to
                    pat.bxor(ctrl),
            "script string.bxor(ctrl)" to
                    StrudelPattern.compile(""""$pat".bxor("$ctrl")"""),
            "bxor(ctrl)" to
                    seq(pat).apply(bxor(ctrl)),
            "script bxor(ctrl)" to
                    StrudelPattern.compile("""seq("$pat").apply(bxor("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.value?.asInt shouldBe 10  // 12 ^ 6 = 10
            events[1].data.value?.asInt shouldBe 9   // 10 ^ 3 = 9
        }
    }
})