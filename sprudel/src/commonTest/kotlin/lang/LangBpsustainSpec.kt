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

class LangBpsustainSpec : StringSpec({

    // ---- bpsustain ----

    "bpsustain dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"
        dslInterfaceTests(
            "pattern.bpsustain(ctrl)" to seq(pat).bpsustain(ctrl),
            "script pattern.bpsustain(ctrl)" to StrudelPattern.compile("""seq("$pat").bpsustain("$ctrl")"""),
            "string.bpsustain(ctrl)" to pat.bpsustain(ctrl),
            "script string.bpsustain(ctrl)" to StrudelPattern.compile(""""$pat".bpsustain("$ctrl")"""),
            "bpsustain(ctrl)" to seq(pat).apply(bpsustain(ctrl)),
            "script bpsustain(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(bpsustain("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.bpsustain shouldBe 0.5
            events[1].data.bpsustain shouldBe 1.0
        }
    }

    "reinterpret voice data as bpsustain | seq(\"0.5 1.0\").bpsustain()" {
        val p = seq("0.5 1.0").bpsustain()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.bpsustain shouldBe 0.5
            events[1].data.bpsustain shouldBe 1.0
        }
    }

    "reinterpret voice data as bpsustain | \"0.5 1.0\".bpsustain()" {
        val p = "0.5 1.0".bpsustain()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.bpsustain shouldBe 0.5
            events[1].data.bpsustain shouldBe 1.0
        }
    }

    "reinterpret voice data as bpsustain | seq(\"0.5 1.0\").apply(bpsustain())" {
        val p = seq("0.5 1.0").apply(bpsustain())
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.bpsustain shouldBe 0.5
            events[1].data.bpsustain shouldBe 1.0
        }
    }

    "bpsustain() sets VoiceData.bpsustain" {
        val p = note("a b").apply(bpsustain("0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.bpsustain } shouldBe listOf(0.5, 1.0)
    }

    "bpsustain() works as pattern extension" {
        val p = note("c").bpsustain("0.7")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.bpsustain shouldBe 0.7
    }

    "bpsustain() works as string extension" {
        val p = "c".bpsustain("0.7")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.bpsustain shouldBe 0.7
    }

    "bpsustain() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").bpsustain("0.7")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.bpsustain shouldBe 0.7
    }

    "bpsustain() with continuous pattern sets bpsustain correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").bpsustain(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.bpsustain shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.bpsustain shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.bpsustain shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.bpsustain shouldBe (0.0 plusOrMinus EPSILON)
    }

    "bpsustain() creates FilterEnvelope in FilterDef" {
        val data = io.peekandpoke.klang.sprudel.StrudelVoiceData.empty.copy(
            bandf = 1000.0,
            bpsustain = 0.7
        )
        val voiceData = data.toVoiceData()
        val bpf = voiceData.filters[0] as FilterDef.BandPass

        bpf.envelope shouldNotBe null
        bpf.envelope?.sustain shouldBe 0.7
    }

    // ---- bps (alias) ----

    "bps dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"
        dslInterfaceTests(
            "pattern.bps(ctrl)" to seq(pat).bps(ctrl),
            "script pattern.bps(ctrl)" to StrudelPattern.compile("""seq("$pat").bps("$ctrl")"""),
            "string.bps(ctrl)" to pat.bps(ctrl),
            "script string.bps(ctrl)" to StrudelPattern.compile(""""$pat".bps("$ctrl")"""),
            "bps(ctrl)" to seq(pat).apply(bps(ctrl)),
            "script bps(ctrl)" to StrudelPattern.compile("""seq("$pat").apply(bps("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.bpsustain shouldBe 0.5
            events[1].data.bpsustain shouldBe 1.0
        }
    }

    "reinterpret voice data as bpsustain | seq(\"0.5 1.0\").bps()" {
        val p = seq("0.5 1.0").bps()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.bpsustain shouldBe 0.5
            events[1].data.bpsustain shouldBe 1.0
        }
    }

    "bps() sets VoiceData.bpsustain" {
        val p = note("a b").apply(bps("0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.bpsustain } shouldBe listOf(0.5, 1.0)
    }

    "bps() works as pattern extension" {
        val p = note("c").bps("0.85")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.bpsustain shouldBe 0.85
    }

    "bps() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").bps("0.85")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.bpsustain shouldBe 0.85
    }
})
