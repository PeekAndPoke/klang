package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangLpsustainSpec : StringSpec({

    "lpsustain() sets StrudelVoiceData.lpsustain" {
        val p = lpsustain("0.8 0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.lpsustain shouldBe 0.8
        events[1].data.lpsustain shouldBe 0.6
    }

    "lpsustain() works as pattern extension" {
        val p = note("c").lpsustain("0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.lpsustain shouldBe 0.8
    }

    "lpsustain() works as string extension" {
        val p = "c".lpsustain("0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.lpsustain shouldBe 0.8
    }

    "lpsustain() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").lpsustain("0.8")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.lpsustain shouldBe 0.8
    }

    "lpsustain() with continuous pattern sets lpsustain correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").lpsustain(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.lpsustain shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.lpsustain shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.lpsustain shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.lpsustain shouldBe (0.0 plusOrMinus EPSILON)
    }

    "lpsustain() creates FilterEnvelope in FilterDef" {
        val data = io.peekandpoke.klang.strudel.StrudelVoiceData.empty.copy(
            cutoff = 1000.0,
            lpsustain = 0.8
        )
        val voiceData = data.toVoiceData()
        val lpf = voiceData.filters[0] as FilterDef.LowPass

        lpf.envelope shouldNotBe null
        lpf.envelope?.sustain shouldBe 0.8
    }

    // Alias tests

    "lps() is an alias for lpsustain()" {
        val p = lps("0.7")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.lpsustain shouldBe 0.7
    }

    "lps() works as pattern extension" {
        val p = note("c").lps("0.7")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.lpsustain shouldBe 0.7
    }

    "lps() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").lps("0.7")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.lpsustain shouldBe 0.7
    }
})
