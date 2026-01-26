package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangBpenvSpec : StringSpec({

    "bpenv() sets StrudelVoiceData.bpenv" {
        val p = bpenv("0.5 0.7")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.bpenv shouldBe 0.5
        events[1].data.bpenv shouldBe 0.7
    }

    "bpenv() works as pattern extension" {
        val p = note("c").bpenv("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.bpenv shouldBe 0.5
    }

    "bpenv() works as string extension" {
        val p = "c".bpenv("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.bpenv shouldBe 0.5
    }

    "bpenv() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").bpenv("0.5")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.bpenv shouldBe 0.5
    }

    "bpenv() with continuous pattern sets bpenv correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").bpenv(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.bpenv shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.bpenv shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.bpenv shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.bpenv shouldBe (0.0 plusOrMinus EPSILON)
    }

    "bpenv() creates FilterEnvelope in FilterDef" {
        val data = io.peekandpoke.klang.strudel.StrudelVoiceData.empty.copy(
            bandf = 1000.0,
            bpenv = 0.5
        )
        val voiceData = data.toVoiceData()
        val bpf = voiceData.filters[0] as FilterDef.BandPass

        bpf.envelope shouldNotBe null
        bpf.envelope?.depth shouldBe 0.5
    }

    // Alias tests

    "bpe() is an alias for bpenv()" {
        val p = bpe("0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.bpenv shouldBe 0.6
    }

    "bpe() works as pattern extension" {
        val p = note("c").bpe("0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.bpenv shouldBe 0.6
    }

    "bpe() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").bpe("0.6")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.bpenv shouldBe 0.6
    }
})
