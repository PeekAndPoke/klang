package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.StrudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangFmsustainSpec : StringSpec({

    "fmsustain dsl interface" {
        val pat = "hh hh"
        val ctrl = "0.0 0.7"

        dslInterfaceTests(
            "pattern.fmsustain(ctrl)" to s(pat).fmsustain(ctrl),
            "script pattern.fmsustain(ctrl)" to StrudelPattern.compile("""s("$pat").fmsustain("$ctrl")"""),
            "string.fmsustain(ctrl)" to pat.fmsustain(ctrl),
            "script string.fmsustain(ctrl)" to StrudelPattern.compile(""""$pat".fmsustain("$ctrl")"""),
            "fmsustain(ctrl)" to s(pat).apply(fmsustain(ctrl)),
            "script fmsustain(ctrl)" to StrudelPattern.compile("""s("$pat").apply(fmsustain("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.fmSustain shouldBe 0.0
            events[1].data.fmSustain shouldBe 0.7
        }
    }

    "fmsus dsl interface" {
        val pat = "hh hh"
        val ctrl = "0.0 0.7"

        dslInterfaceTests(
            "pattern.fmsus(ctrl)" to s(pat).fmsus(ctrl),
            "script pattern.fmsus(ctrl)" to StrudelPattern.compile("""s("$pat").fmsus("$ctrl")"""),
            "string.fmsus(ctrl)" to pat.fmsus(ctrl),
            "script string.fmsus(ctrl)" to StrudelPattern.compile(""""$pat".fmsus("$ctrl")"""),
            "fmsus(ctrl)" to s(pat).apply(fmsus(ctrl)),
            "script fmsus(ctrl)" to StrudelPattern.compile("""s("$pat").apply(fmsus("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.fmSustain shouldBe 0.0
            events[1].data.fmSustain shouldBe 0.7
        }
    }

    "reinterpret voice data as fmSustain | seq(\"0.0 0.7\").fmsustain()" {
        val p = seq("0.0 0.7").fmsustain()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.fmSustain shouldBe 0.0
            events[1].data.fmSustain shouldBe 0.7
        }
    }

    "reinterpret voice data as fmSustain | \"0.0 0.7\".fmsustain()" {
        val p = "0.0 0.7".fmsustain()

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.fmSustain shouldBe 0.0
            events[1].data.fmSustain shouldBe 0.7
        }
    }

    "reinterpret voice data as fmSustain | seq(\"0.0 0.7\").apply(fmsustain())" {
        val p = seq("0.0 0.7").apply(fmsustain())

        val events = p.queryArc(0.0, 1.0)

        assertSoftly {
            events.size shouldBe 2
            events[0].data.fmSustain shouldBe 0.0
            events[1].data.fmSustain shouldBe 0.7
        }
    }

    "top-level fmsustain() sets VoiceData.fmSustain correctly" {
        val p = s("hh hh").apply(fmsustain("0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.fmSustain } shouldBe listOf(0.5, 1.0)
    }

    "control pattern fmsustain() sets VoiceData.fmSustain on existing pattern" {
        val base = note("c3 e3")
        val p = base.fmsustain("0.1 0.2")
        val events = p.queryArc(0.0, 2.0)

        events.size shouldBe 4
        events.map { it.data.fmSustain } shouldBe listOf(0.1, 0.2, 0.1, 0.2)
    }

    "fmsustain() works as string extension" {
        val p = "c3".fmsustain("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "c3"
        events[0].data.fmSustain shouldBe 0.5
    }

    "fmsustain() works within compiled code" {
        val p = StrudelPattern.compile("""note("a b").fmsustain("0.5 1.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.fmSustain } shouldBe listOf(0.5, 1.0)
    }

    "fmsus() alias works as pattern extension" {
        val p = note("c d").fmsus("0.4 0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.fmSustain } shouldBe listOf(0.4, 0.6)
    }

    "fmsus() alias works as string extension" {
        val p = "e3".fmsus("0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.value?.asString shouldBe "e3"
        events[0].data.fmSustain shouldBe 0.8
    }

    "fmsus() alias works within compiled code" {
        val p = StrudelPattern.compile("""note("c d").fmsus("0.2 0.9")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.fmSustain } shouldBe listOf(0.2, 0.9)
    }
})
