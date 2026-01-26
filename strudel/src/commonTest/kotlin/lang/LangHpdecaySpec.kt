package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangHpdecaySpec : StringSpec({

    "hpdecay() sets StrudelVoiceData.hpdecay" {
        val p = hpdecay("0.2 0.3")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.hpdecay shouldBe 0.2
        events[1].data.hpdecay shouldBe 0.3
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

    // Alias tests

    "hpd() is an alias for hpdecay()" {
        val p = hpd("0.25")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hpdecay shouldBe 0.25
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
