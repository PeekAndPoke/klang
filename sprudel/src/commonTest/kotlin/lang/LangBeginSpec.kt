package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.dslInterfaceTests

class LangBeginSpec : StringSpec({

    "begin dsl interface" {
        val pat = "a b"
        val ctrl = "0.25 0.75"
        dslInterfaceTests(
            "pattern.begin(ctrl)" to seq(pat).begin(ctrl),
            "script pattern.begin(ctrl)" to StrudelPattern.compile("""seq("$pat").begin("$ctrl")"""),
            "string.begin(ctrl)" to pat.begin(ctrl),
            "script string.begin(ctrl)" to StrudelPattern.compile(""""$pat".begin("$ctrl")"""),
            "begin(ctrl)" to seq(pat).apply(begin(ctrl)),
            "script begin(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(begin("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.begin shouldBe 0.25
            events[1].data.begin shouldBe 0.75
        }
    }

    "reinterpret voice data as begin | seq(\"0.25 0.75\").begin()" {
        val p = seq("0.25 0.75").begin()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.begin shouldBe 0.25
            events[1].data.begin shouldBe 0.75
        }
    }

    "reinterpret voice data as begin | \"0.25 0.75\".begin()" {
        val p = "0.25 0.75".begin()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.begin shouldBe 0.25
            events[1].data.begin shouldBe 0.75
        }
    }

    "reinterpret voice data as begin | seq(\"0.25 0.75\").apply(begin())" {
        val p = seq("0.25 0.75").apply(begin())
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.begin shouldBe 0.25
            events[1].data.begin shouldBe 0.75
        }
    }

    "begin() sets VoiceData.begin correctly" {
        val p = sound("hh hh").apply(begin("0.25 0.5"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.begin } shouldBe listOf(0.25, 0.5)
    }

    "control pattern begin() sets VoiceData.begin on existing pattern" {
        val base = note("c3 e3")
        val p = base.begin("0.1 0.2")
        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4
        events.map { it.data.begin } shouldBe listOf(0.1, 0.2, 0.1, 0.2)
    }

    "begin() works as string extension" {
        val p = "c3".begin("0.5")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.begin shouldBe 0.5
    }

    "begin() works within compiled code" {
        val p = StrudelPattern.compile("""note("a b").begin("0.25 0.75")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 2
        events.map { it.data.begin } shouldBe listOf(0.25, 0.75)
    }
})
