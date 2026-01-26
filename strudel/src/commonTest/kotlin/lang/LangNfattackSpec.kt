package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangNfattackSpec : StringSpec({

    "nfattack() sets StrudelVoiceData.nfattack" {
        val p = nfattack("0.05 0.1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.nfattack shouldBe 0.05
        events[1].data.nfattack shouldBe 0.1
    }

    "nfattack() works as pattern extension" {
        val p = note("c").nfattack("0.05")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.nfattack shouldBe 0.05
    }

    "nfattack() works as string extension" {
        val p = "c".nfattack("0.05")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.nfattack shouldBe 0.05
    }

    "nfattack() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").nfattack("0.05")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.nfattack shouldBe 0.05
    }

    "nfattack() with continuous pattern sets nfattack correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").nfattack(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.nfattack shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.nfattack shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.nfattack shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.nfattack shouldBe (0.0 plusOrMinus EPSILON)
    }

    "nfattack() creates FilterEnvelope in FilterDef" {
        val data = io.peekandpoke.klang.strudel.StrudelVoiceData.empty.copy(
            notchf = 1000.0,
            nfattack = 0.05
        )
        val voiceData = data.toVoiceData()
        val nf = voiceData.filters[0] as FilterDef.Notch

        nf.envelope shouldNotBe null
        nf.envelope?.attack shouldBe 0.05
    }

    // Alias tests

    "nfa() is an alias for nfattack()" {
        val p = nfa("0.08")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.nfattack shouldBe 0.08
    }

    "nfa() works as pattern extension" {
        val p = note("c").nfa("0.08")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.nfattack shouldBe 0.08
    }

    "nfa() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").nfa("0.08")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.nfattack shouldBe 0.08
    }
})
