package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangNfdecaySpec : StringSpec({

    "nfdecay() sets StrudelVoiceData.nfdecay" {
        val p = nfdecay("0.2 0.3")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.nfdecay shouldBe 0.2
        events[1].data.nfdecay shouldBe 0.3
    }

    "nfdecay() works as pattern extension" {
        val p = note("c").nfdecay("0.2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.nfdecay shouldBe 0.2
    }

    "nfdecay() works as string extension" {
        val p = "c".nfdecay("0.2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.nfdecay shouldBe 0.2
    }

    "nfdecay() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").nfdecay("0.2")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.nfdecay shouldBe 0.2
    }

    "nfdecay() with continuous pattern sets nfdecay correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").nfdecay(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.nfdecay shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.nfdecay shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.nfdecay shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.nfdecay shouldBe (0.0 plusOrMinus EPSILON)
    }

    "nfdecay() creates FilterEnvelope in FilterDef" {
        val data = io.peekandpoke.klang.strudel.StrudelVoiceData.empty.copy(
            notchf = 1000.0,
            nfdecay = 0.2
        )
        val voiceData = data.toVoiceData()
        val nf = voiceData.filters[0] as FilterDef.Notch

        nf.envelope shouldNotBe null
        nf.envelope?.decay shouldBe 0.2
    }

    // Alias tests

    "nfd() is an alias for nfdecay()" {
        val p = nfd("0.25")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.nfdecay shouldBe 0.25
    }

    "nfd() works as pattern extension" {
        val p = note("c").nfd("0.25")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.nfdecay shouldBe 0.25
    }

    "nfd() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").nfd("0.25")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.nfdecay shouldBe 0.25
    }
})
