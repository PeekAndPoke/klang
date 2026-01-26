package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangBpdecaySpec : StringSpec({

    "bpdecay() sets StrudelVoiceData.bpdecay" {
        val p = bpdecay("0.2 0.3")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.bpdecay shouldBe 0.2
        events[1].data.bpdecay shouldBe 0.3
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
        val data = io.peekandpoke.klang.strudel.StrudelVoiceData.empty.copy(
            bandf = 1000.0,
            bpdecay = 0.2
        )
        val voiceData = data.toVoiceData()
        val bpf = voiceData.filters[0] as FilterDef.BandPass

        bpf.envelope shouldNotBe null
        bpf.envelope?.decay shouldBe 0.2
    }

    // Alias tests

    "bpd() is an alias for bpdecay()" {
        val p = bpd("0.25")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.bpdecay shouldBe 0.25
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
