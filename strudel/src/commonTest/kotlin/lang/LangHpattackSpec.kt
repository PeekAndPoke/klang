package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangHpattackSpec : StringSpec({

    "hpattack() sets StrudelVoiceData.hpattack" {
        val p = hpattack("0.05 0.1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.hpattack shouldBe 0.05
        events[1].data.hpattack shouldBe 0.1
    }

    "hpattack() works as pattern extension" {
        val p = note("c").hpattack("0.05")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hpattack shouldBe 0.05
    }

    "hpattack() works as string extension" {
        val p = "c".hpattack("0.05")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hpattack shouldBe 0.05
    }

    "hpattack() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").hpattack("0.05")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.hpattack shouldBe 0.05
    }

    "hpattack() with continuous pattern sets hpattack correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").hpattack(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.hpattack shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.hpattack shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.hpattack shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.hpattack shouldBe (0.0 plusOrMinus EPSILON)
    }

    "hpattack() creates FilterEnvelope in FilterDef" {
        val data = io.peekandpoke.klang.strudel.StrudelVoiceData.empty.copy(
            hcutoff = 2000.0,
            hpattack = 0.05
        )
        val voiceData = data.toVoiceData()
        val hpf = voiceData.filters[0] as FilterDef.HighPass

        hpf.envelope shouldNotBe null
        hpf.envelope?.attack shouldBe 0.05
    }

    // Alias tests

    "hpa() is an alias for hpattack()" {
        val p = hpa("0.08")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hpattack shouldBe 0.08
    }

    "hpa() works as pattern extension" {
        val p = note("c").hpa("0.08")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hpattack shouldBe 0.08
    }

    "hpa() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").hpa("0.08")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.hpattack shouldBe 0.08
    }
})
