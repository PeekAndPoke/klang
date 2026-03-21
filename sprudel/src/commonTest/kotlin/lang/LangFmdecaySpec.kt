package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.dslInterfaceTests

class LangFmdecaySpec : StringSpec({

    "fmdecay dsl interface" {
        val pat = "hh hh"
        val ctrl = "0.1 0.5"

        dslInterfaceTests(
            "pattern.fmdecay(ctrl)" to s(pat).fmdecay(ctrl),
            "script pattern.fmdecay(ctrl)" to StrudelPattern.compile("""s("$pat").fmdecay("$ctrl")"""),
            "string.fmdecay(ctrl)" to pat.fmdecay(ctrl),
            "script string.fmdecay(ctrl)" to StrudelPattern.compile(""""$pat".fmdecay("$ctrl")"""),
            "fmdecay(ctrl)" to s(pat).apply(fmdecay(ctrl)),
            "script fmdecay(ctrl)" to StrudelPattern.compile("""s("$pat").apply(fmdecay("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.fmDecay shouldBe 0.1
            events[1].data.fmDecay shouldBe 0.5
        }
    }

    "fmdec dsl interface" {
        val pat = "hh hh"
        val ctrl = "0.1 0.5"

        dslInterfaceTests(
            "pattern.fmdec(ctrl)" to s(pat).fmdec(ctrl),
            "script pattern.fmdec(ctrl)" to StrudelPattern.compile("""s("$pat").fmdec("$ctrl")"""),
            "string.fmdec(ctrl)" to pat.fmdec(ctrl),
            "script string.fmdec(ctrl)" to StrudelPattern.compile(""""$pat".fmdec("$ctrl")"""),
            "fmdec(ctrl)" to s(pat).apply(fmdec(ctrl)),
            "script fmdec(ctrl)" to StrudelPattern.compile("""s("$pat").apply(fmdec("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.fmDecay shouldBe 0.1
            events[1].data.fmDecay shouldBe 0.5
        }
    }

    "reinterpret voice data as fmDecay | seq(\"0.1 0.5\").fmdecay()" {
        val p = seq("0.1 0.5").fmdecay()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.fmDecay shouldBe 0.1
            events[1].data.fmDecay shouldBe 0.5
        }
    }

    "reinterpret voice data as fmDecay | \"0.1 0.5\".fmdecay()" {
        val p = "0.1 0.5".fmdecay()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.fmDecay shouldBe 0.1
            events[1].data.fmDecay shouldBe 0.5
        }
    }

    "reinterpret voice data as fmDecay | seq(\"0.1 0.5\").apply(fmdecay())" {
        val p = seq("0.1 0.5").apply(fmdecay())

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.fmDecay shouldBe 0.1
            events[1].data.fmDecay shouldBe 0.5
        }
    }

    "top-level fmdecay() sets VoiceData.fmDecay correctly" {
        val p = s("hh hh").apply(fmdecay("0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.fmDecay } shouldBe listOf(0.5, 1.0)
    }

    "control pattern fmdecay() sets VoiceData.fmDecay on existing pattern" {
        val base = note("c3 e3")
        val p = base.fmdecay("0.1 0.2")
        val events = p.queryArc(0.0, 2.0)

        events.size shouldBe 4
        events.map { it.data.fmDecay } shouldBe listOf(0.1, 0.2, 0.1, 0.2)
    }

    "fmdecay() works as string extension" {
        val p = "c3".fmdecay("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c3"
        events[0].data.fmDecay shouldBe 0.5
    }

    "fmdecay() works within compiled code" {
        val p = StrudelPattern.compile("""note("a b").fmdecay("0.5 1.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.fmDecay } shouldBe listOf(0.5, 1.0)
    }

    "fmdec() alias works as pattern extension" {
        val p = note("c d").fmdec("0.4 0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.fmDecay } shouldBe listOf(0.4, 0.6)
    }

    "fmdec() alias works as string extension" {
        val p = "e3".fmdec("0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "e3"
        events[0].data.fmDecay shouldBe 0.8
    }

    "fmdec() alias works within compiled code" {
        val p = StrudelPattern.compile("""note("c d").fmdec("0.2 0.9")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.fmDecay } shouldBe listOf(0.2, 0.9)
    }
})
