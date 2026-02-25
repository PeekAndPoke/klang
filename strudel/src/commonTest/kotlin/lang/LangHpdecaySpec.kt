package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern
import io.peekandpoke.klang.strudel.dslInterfaceTests

class LangHpdecaySpec : StringSpec({

    // ---- hpdecay ----

    "hpdecay dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"
        dslInterfaceTests(
            "pattern.hpdecay(ctrl)" to seq(pat).hpdecay(ctrl),
            "script pattern.hpdecay(ctrl)" to StrudelPattern.compile("""seq("$pat").hpdecay("$ctrl")"""),
            "string.hpdecay(ctrl)" to pat.hpdecay(ctrl),
            "script string.hpdecay(ctrl)" to StrudelPattern.compile(""""$pat".hpdecay("$ctrl")"""),
            "hpdecay(ctrl)" to seq(pat).apply(hpdecay(ctrl)),
            "script hpdecay(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(hpdecay("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.hpdecay shouldBe 0.5
            events[1].data.hpdecay shouldBe 1.0
        }
    }

    "reinterpret voice data as hpdecay | seq(\"0.5 1.0\").hpdecay()" {
        val p = seq("0.5 1.0").hpdecay()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.hpdecay shouldBe 0.5
            events[1].data.hpdecay shouldBe 1.0
        }
    }

    "reinterpret voice data as hpdecay | \"0.5 1.0\".hpdecay()" {
        val p = "0.5 1.0".hpdecay()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.hpdecay shouldBe 0.5
            events[1].data.hpdecay shouldBe 1.0
        }
    }

    "reinterpret voice data as hpdecay | seq(\"0.5 1.0\").apply(hpdecay())" {
        val p = seq("0.5 1.0").apply(hpdecay())
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.hpdecay shouldBe 0.5
            events[1].data.hpdecay shouldBe 1.0
        }
    }

    "hpdecay() sets VoiceData.hpdecay" {
        val p = note("a b").apply(hpdecay("0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.hpdecay } shouldBe listOf(0.5, 1.0)
    }

    "hpdecay() works as pattern extension" {
        val p = note("c").hpdecay("0.2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hpdecay shouldBe 0.2
    }

    "hpdecay() works as string extension" {
        val p = "c".hpdecay("0.2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hpdecay shouldBe 0.2
    }

    "hpdecay() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").hpdecay("0.2")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.hpdecay shouldBe 0.2
    }

    "hpdecay() with continuous pattern sets hpdecay correctly" {
        val p = note("a b c d").hpdecay(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        events[0].data.hpdecay shouldBe (0.5 plusOrMinus EPSILON)
        events[1].data.hpdecay shouldBe (1.0 plusOrMinus EPSILON)
        events[2].data.hpdecay shouldBe (0.5 plusOrMinus EPSILON)
        events[3].data.hpdecay shouldBe (0.0 plusOrMinus EPSILON)
    }

    "hpdecay() creates FilterEnvelope in FilterDef" {
        val data = io.peekandpoke.klang.strudel.StrudelVoiceData.empty.copy(
            hcutoff = 2000.0,
            hpdecay = 0.2
        )
        val voiceData = data.toVoiceData()
        val hpf = voiceData.filters[0] as FilterDef.HighPass

        hpf.envelope shouldNotBe null
        hpf.envelope?.decay shouldBe 0.2
    }

    // ---- hpd (alias) ----

    "hpd dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"
        dslInterfaceTests(
            "pattern.hpd(ctrl)" to seq(pat).hpd(ctrl),
            "script pattern.hpd(ctrl)" to StrudelPattern.compile("""seq("$pat").hpd("$ctrl")"""),
            "string.hpd(ctrl)" to pat.hpd(ctrl),
            "script string.hpd(ctrl)" to StrudelPattern.compile(""""$pat".hpd("$ctrl")"""),
            "hpd(ctrl)" to seq(pat).apply(hpd(ctrl)),
            "script hpd(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(hpd("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.hpdecay shouldBe 0.5
            events[1].data.hpdecay shouldBe 1.0
        }
    }

    "reinterpret voice data as hpdecay | seq(\"0.5 1.0\").hpd()" {
        val p = seq("0.5 1.0").hpd()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.hpdecay shouldBe 0.5
            events[1].data.hpdecay shouldBe 1.0
        }
    }

    "hpd() sets VoiceData.hpdecay" {
        val p = note("a b").apply(hpd("0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.hpdecay } shouldBe listOf(0.5, 1.0)
    }

    "hpd() works as pattern extension" {
        val p = note("c").hpd("0.25")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hpdecay shouldBe 0.25
    }

    "hpd() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").hpd("0.25")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.hpdecay shouldBe 0.25
    }
})