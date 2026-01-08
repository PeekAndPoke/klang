package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangVibratoModSpec : StringSpec({

    "top-level vibratoMod() sets VoiceData.vibratoMod correctly" {
        val p = vibratoMod("2 4.5")

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.vibratoMod } shouldBe listOf(2.0, 4.5)
    }

    "control pattern vibratoMod() sets VoiceData.vibratoMod on existing pattern" {
        val base = note("c3 e3")
        val p = base.vibratoMod("1 3")

        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4
        events.map { it.data.vibratoMod } shouldBe listOf(1.0, 3.0, 1.0, 3.0)
    }

    "alias vibmod behaves like vibratoMod" {
        val pTop = vibmod("0.5 2.5")
        val eTop = pTop.queryArc(0.0, 1.0)
        eTop.size shouldBe 2
        eTop.map { it.data.vibratoMod } shouldBe listOf(0.5, 2.5)

        val base = note("c3 e3")
        val pCtrl = base.vibmod("0.75 1.25")
        val eCtrl = pCtrl.queryArc(0.0, 2.0)
        eCtrl.size shouldBe 4
        eCtrl.map { it.data.vibratoMod } shouldBe listOf(0.75, 1.25, 0.75, 1.25)
    }

    "vibratoMod() works within compiled code as top-level function" {
        val p = StrudelPattern.compile("""vibratoMod("2 4.5")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.vibratoMod } shouldBe listOf(2.0, 4.5)
    }

    "vibratoMod() works within compiled code as chained-level function" {
        val p = StrudelPattern.compile("""note("a b").vibratoMod("2 4.5")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.vibratoMod } shouldBe listOf(2.0, 4.5)
    }
})
