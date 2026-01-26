package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangHpsustainSpec : StringSpec({

    "hpsustain() sets StrudelVoiceData.hpsustain" {
        val p = hpsustain("0.6 0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.hpsustain shouldBe 0.6
        events[1].data.hpsustain shouldBe 0.8
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
        val p = StrudelPattern.compile("""note("c").hpsustain("0.7")""")
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
        val data = io.peekandpoke.klang.strudel.StrudelVoiceData.empty.copy(
            hcutoff = 2000.0,
            hpsustain = 0.8
        )
        val voiceData = data.toVoiceData()
        val hpf = voiceData.filters[0] as FilterDef.HighPass

        hpf.envelope shouldNotBe null
        hpf.envelope?.sustain shouldBe 0.8
    }

    // Alias tests

    "hps() is an alias for hpsustain()" {
        val p = hps("0.75")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hpsustain shouldBe 0.75
    }

    "hps() works as pattern extension" {
        val p = note("c").hps("0.75")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hpsustain shouldBe 0.75
    }

    "hps() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").hps("0.75")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.hpsustain shouldBe 0.75
    }
})
