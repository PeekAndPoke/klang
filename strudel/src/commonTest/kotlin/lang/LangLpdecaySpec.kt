package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangLpdecaySpec : StringSpec({

    "lpdecay() sets StrudelVoiceData.lpdecay" {
        val p = lpdecay("0.2 0.3")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.lpdecay shouldBe 0.2
        events[1].data.lpdecay shouldBe 0.3
    }

    "lpdecay() works as pattern extension" {
        val p = note("c").lpdecay("0.2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.lpdecay shouldBe 0.2
    }

    "lpdecay() works as string extension" {
        val p = "c".lpdecay("0.2")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.lpdecay shouldBe 0.2
    }

    "lpdecay() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").lpdecay("0.2")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.lpdecay shouldBe 0.2
    }

    "lpdecay() with continuous pattern sets lpdecay correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").lpdecay(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.lpdecay shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.lpdecay shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.lpdecay shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.lpdecay shouldBe (0.0 plusOrMinus EPSILON)
    }

    "lpdecay() creates FilterEnvelope in FilterDef" {
        val data = io.peekandpoke.klang.strudel.StrudelVoiceData.empty.copy(
            cutoff = 1000.0,
            lpdecay = 0.2
        )
        val voiceData = data.toVoiceData()
        val lpf = voiceData.filters[0] as FilterDef.LowPass

        lpf.envelope shouldNotBe null
        lpf.envelope?.decay shouldBe 0.2
    }

    // Alias tests

    "lpd() is an alias for lpdecay()" {
        val p = lpd("0.25")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.lpdecay shouldBe 0.25
    }

    "lpd() works as pattern extension" {
        val p = note("c").lpd("0.25")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.lpdecay shouldBe 0.25
    }

    "lpd() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").lpd("0.25")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.lpdecay shouldBe 0.25
    }
})
