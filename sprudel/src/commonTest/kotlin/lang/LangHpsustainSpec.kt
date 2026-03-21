package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.sprudel.EPSILON
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangHpsustainSpec : StringSpec({

    // ---- hpsustain ----

    "hpsustain dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"
        dslInterfaceTests(
            "pattern.hpsustain(ctrl)" to seq(pat).hpsustain(ctrl),
            "script pattern.hpsustain(ctrl)" to SprudelPattern.compile("""seq("$pat").hpsustain("$ctrl")"""),
            "string.hpsustain(ctrl)" to pat.hpsustain(ctrl),
            "script string.hpsustain(ctrl)" to SprudelPattern.compile(""""$pat".hpsustain("$ctrl")"""),
            "hpsustain(ctrl)" to seq(pat).apply(hpsustain(ctrl)),
            "script hpsustain(ctrl)" to SprudelPattern.compile("""seq("$pat").apply(hpsustain("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.hpsustain shouldBe 0.5
            events[1].data.hpsustain shouldBe 1.0
        }
    }

    "reinterpret voice data as hpsustain | seq(\"0.5 1.0\").hpsustain()" {
        val p = seq("0.5 1.0").hpsustain()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.hpsustain shouldBe 0.5
            events[1].data.hpsustain shouldBe 1.0
        }
    }

    "reinterpret voice data as hpsustain | \"0.5 1.0\".hpsustain()" {
        val p = "0.5 1.0".hpsustain()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.hpsustain shouldBe 0.5
            events[1].data.hpsustain shouldBe 1.0
        }
    }

    "reinterpret voice data as hpsustain | seq(\"0.5 1.0\").apply(hpsustain())" {
        val p = seq("0.5 1.0").apply(hpsustain())
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.hpsustain shouldBe 0.5
            events[1].data.hpsustain shouldBe 1.0
        }
    }

    "hpsustain() sets VoiceData.hpsustain" {
        val p = note("a b").apply(hpsustain("0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.hpsustain } shouldBe listOf(0.5, 1.0)
    }

    "hpsustain() works as pattern extension" {
        val p = note("c").hpsustain("0.7")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hpsustain shouldBe 0.7
    }

    "hpsustain() works as string extension" {
        val p = "c".hpsustain("0.7")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hpsustain shouldBe 0.7
    }

    "hpsustain() works in compiled code" {
        val p = SprudelPattern.compile("""note("c").hpsustain("0.7")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.hpsustain shouldBe 0.7
    }

    "hpsustain() with continuous pattern sets hpsustain correctly" {
        val p = note("a b c d").hpsustain(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        events[0].data.hpsustain shouldBe (0.5 plusOrMinus EPSILON)
        events[1].data.hpsustain shouldBe (1.0 plusOrMinus EPSILON)
        events[2].data.hpsustain shouldBe (0.5 plusOrMinus EPSILON)
        events[3].data.hpsustain shouldBe (0.0 plusOrMinus EPSILON)
    }

    "hpsustain() creates FilterEnvelope in FilterDef" {
        val data = io.peekandpoke.klang.sprudel.SprudelVoiceData.empty.copy(
            hcutoff = 2000.0,
            hpsustain = 0.8
        )
        val voiceData = data.toVoiceData()
        val hpf = voiceData.filters[0] as FilterDef.HighPass

        hpf.envelope shouldNotBe null
        hpf.envelope?.sustain shouldBe 0.8
    }

    // ---- hps (alias) ----

    "hps dsl interface" {
        val pat = "a b"
        val ctrl = "0.5 1.0"
        dslInterfaceTests(
            "pattern.hps(ctrl)" to seq(pat).hps(ctrl),
            "script pattern.hps(ctrl)" to SprudelPattern.compile("""seq("$pat").hps("$ctrl")"""),
            "string.hps(ctrl)" to pat.hps(ctrl),
            "script string.hps(ctrl)" to SprudelPattern.compile(""""$pat".hps("$ctrl")"""),
            "hps(ctrl)" to seq(pat).apply(hps(ctrl)),
            "script hps(ctrl)" to SprudelPattern.compile("""seq("$pat").apply(hps("$ctrl"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.hpsustain shouldBe 0.5
            events[1].data.hpsustain shouldBe 1.0
        }
    }

    "reinterpret voice data as hpsustain | seq(\"0.5 1.0\").hps()" {
        val p = seq("0.5 1.0").hps()
        val events = p.queryArc(0.0, 1.0)
        assertSoftly {
            events.size shouldBe 2
            events[0].data.hpsustain shouldBe 0.5
            events[1].data.hpsustain shouldBe 1.0
        }
    }

    "hps() sets VoiceData.hpsustain" {
        val p = note("a b").apply(hps("0.5 1.0"))
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 2
        events.map { it.data.hpsustain } shouldBe listOf(0.5, 1.0)
    }

    "hps() works as pattern extension" {
        val p = note("c").hps("0.75")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hpsustain shouldBe 0.75
    }

    "hps() works in compiled code" {
        val p = SprudelPattern.compile("""note("c").hps("0.75")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.hpsustain shouldBe 0.75
    }
})
