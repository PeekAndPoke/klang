package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangBpattackSpec : StringSpec({

    "bpattack() sets StrudelVoiceData.bpattack" {
        val p = bpattack("0.05 0.1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.bpattack shouldBe 0.05
        events[1].data.bpattack shouldBe 0.1
    }

    "bpattack() works as pattern extension" {
        val p = note("c").bpattack("0.05")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.bpattack shouldBe 0.05
    }

    "bpattack() works as string extension" {
        val p = "c".bpattack("0.05")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.bpattack shouldBe 0.05
    }

    "bpattack() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").bpattack("0.05")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.bpattack shouldBe 0.05
    }

    "bpattack() with continuous pattern sets bpattack correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").bpattack(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.bpattack shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.bpattack shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.bpattack shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.bpattack shouldBe (0.0 plusOrMinus EPSILON)
    }

    "bpattack() creates FilterEnvelope in FilterDef" {
        val data = io.peekandpoke.klang.strudel.StrudelVoiceData.empty.copy(
            bandf = 1000.0,
            bpattack = 0.05
        )
        val voiceData = data.toVoiceData()
        val bpf = voiceData.filters[0] as FilterDef.BandPass

        bpf.envelope shouldNotBe null
        bpf.envelope?.attack shouldBe 0.05
    }

    // Alias tests

    "bpa() is an alias for bpattack()" {
        val p = bpa("0.08")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.bpattack shouldBe 0.08
    }

    "bpa() works as pattern extension" {
        val p = note("c").bpa("0.08")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.bpattack shouldBe 0.08
    }

    "bpa() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").bpa("0.08")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.bpattack shouldBe 0.08
    }
})
