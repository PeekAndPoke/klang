package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.dslInterfaceTests

class LangUnitSpec : StringSpec({

    "unit dsl interface" {
        val pat = "a b"
        val ctrl = "c s"
        dslInterfaceTests(
            "pattern.unit(ctrl)" to seq(pat).unit(ctrl),
            "script pattern.unit(ctrl)" to StrudelPattern.compile("""seq("$pat").unit("$ctrl")"""),
            "string.unit(ctrl)" to pat.unit(ctrl),
            "script string.unit(ctrl)" to StrudelPattern.compile(""""$pat".unit("$ctrl")"""),
            "unit(ctrl)" to seq(pat).apply(unit(ctrl)),
            "script unit(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(unit("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.unit shouldBe "c"
            events[1].data.unit shouldBe "s"
        }
    }

    "unit() sets VoiceData.unit correctly" {
        val p = sound("hh hh").apply(unit("c s"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.unit } shouldBe listOf("c", "s")
    }

    "control pattern unit() sets VoiceData.unit on existing pattern" {
        val base = note("c3 e3")
        val p = base.unit("c s")
        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4
        events.map { it.data.unit } shouldBe listOf("c", "s", "c", "s")
    }

    "unit() works as string extension" {
        val p = "c3".unit("c")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.unit shouldBe "c"
    }

    "unit() works within compiled code" {
        val p = StrudelPattern.compile("""note("a b").unit("c s")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 2
        events.map { it.data.unit } shouldBe listOf("c", "s")
    }
})
