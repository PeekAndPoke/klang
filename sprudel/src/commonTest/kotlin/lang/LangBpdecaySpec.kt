package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.sprudel.EPSILON
import io.peekandpoke.klang.sprudel.StrudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangBpdecaySpec : StringSpec({

    // ---- bpdecay ----

    "bpdecay dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"
        dslInterfaceTests(
            "pattern.bpdecay(ctrl)" to seq(pat).bpdecay(ctrl),
            "script pattern.bpdecay(ctrl)" to StrudelPattern.compile("""seq("$pat").bpdecay("$ctrl")"""),
            "string.bpdecay(ctrl)" to pat.bpdecay(ctrl),
            "script string.bpdecay(ctrl)" to StrudelPattern.compile(""""$pat".bpdecay("$ctrl")"""),
            "bpdecay(ctrl)" to seq(pat).apply(bpdecay(ctrl)),
            "script bpdecay(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(bpdecay("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.bpdecay shouldBe 0.5
            events[1].data.bpdecay shouldBe 1.0
        }
    }

    "reinterpret voice data as bpdecay | seq(\"0.5 1.0\").bpdecay()" {
        val p = seq("0.5 1.0").bpdecay()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.bpdecay shouldBe 0.5
            events[1].data.bpdecay shouldBe 1.0
        }
    }

    "reinterpret voice data as bpdecay | \"0.5 1.0\".bpdecay()" {
        val p = "0.5 1.0".bpdecay()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.bpdecay shouldBe 0.5
            events[1].data.bpdecay shouldBe 1.0
        }
    }

    "reinterpret voice data as bpdecay | seq(\"0.5 1.0\").apply(bpdecay())" {
        val p = seq("0.5 1.0").apply(bpdecay())
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.bpdecay shouldBe 0.5
            events[1].data.bpdecay shouldBe 1.0
        }
    }

    "bpdecay() sets VoiceData.bpdecay" {
        val p = note("a b").apply(bpdecay("0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.bpdecay } shouldBe listOf(0.5, 1.0)
    }

    "bpdecay() works as pattern extension" {
        val p = note("c").bpdecay("0.2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.bpdecay shouldBe 0.2
    }

    "bpdecay() works as string extension" {
        val p = "c".bpdecay("0.2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.bpdecay shouldBe 0.2
    }

    "bpdecay() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").bpdecay("0.2")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.bpdecay shouldBe 0.2
    }

    "bpdecay() with continuous pattern sets bpdecay correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").bpdecay(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.bpdecay shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.bpdecay shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.bpdecay shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.bpdecay shouldBe (0.0 plusOrMinus EPSILON)
    }

    "bpdecay() creates FilterEnvelope in FilterDef" {
        val data = io.peekandpoke.klang.sprudel.StrudelVoiceData.empty.copy(
            bandf = 1000.0,
            bpdecay = 0.2
        )
        val voiceData = data.toVoiceData()
        val bpf = voiceData.filters[0] as FilterDef.BandPass

        bpf.envelope shouldNotBe null
        bpf.envelope?.decay shouldBe 0.2
    }

    // ---- bpd (alias) ----

    "bpd dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"
        dslInterfaceTests(
            "pattern.bpd(ctrl)" to seq(pat).bpd(ctrl),
            "script pattern.bpd(ctrl)" to StrudelPattern.compile("""seq("$pat").bpd("$ctrl")"""),
            "string.bpd(ctrl)" to pat.bpd(ctrl),
            "script string.bpd(ctrl)" to StrudelPattern.compile(""""$pat".bpd("$ctrl")"""),
            "bpd(ctrl)" to seq(pat).apply(bpd(ctrl)),
            "script bpd(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(bpd("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.bpdecay shouldBe 0.5
            events[1].data.bpdecay shouldBe 1.0
        }
    }

    "reinterpret voice data as bpdecay | seq(\"0.5 1.0\").bpd()" {
        val p = seq("0.5 1.0").bpd()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.bpdecay shouldBe 0.5
            events[1].data.bpdecay shouldBe 1.0
        }
    }

    "bpd() sets VoiceData.bpdecay" {
        val p = note("a b").apply(bpd("0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.bpdecay } shouldBe listOf(0.5, 1.0)
    }

    "bpd() works as pattern extension" {
        val p = note("c").bpd("0.25")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.bpdecay shouldBe 0.25
    }

    "bpd() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").bpd("0.25")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.bpdecay shouldBe 0.25
    }
})
