package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangLpenvSpec : StringSpec({

    "lpenv() sets StrudelVoiceData.lpenv" {
        val p = lpenv("0.5 0.7")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.lpenv shouldBe 0.5
        events[1].data.lpenv shouldBe 0.7
    }

    "lpenv() works as pattern extension" {
        val p = note("c").lpenv("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.lpenv shouldBe 0.5
    }

    "lpenv() works as string extension" {
        val p = "c".lpenv("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.lpenv shouldBe 0.5
    }

    "lpenv() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").lpenv("0.5")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.lpenv shouldBe 0.5
    }

    "lpenv() with continuous pattern sets lpenv correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").lpenv(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.lpenv shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.lpenv shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.lpenv shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.lpenv shouldBe (0.0 plusOrMinus EPSILON)
    }

    "lpenv() creates FilterEnvelope in FilterDef" {
        val data = io.peekandpoke.klang.strudel.StrudelVoiceData.empty.copy(
            cutoff = 1000.0,
            lpenv = 0.7
        )
        val voiceData = data.toVoiceData()
        val lpf = voiceData.filters[0] as FilterDef.LowPass

        lpf.envelope shouldNotBe null
        lpf.envelope?.depth shouldBe 0.7
    }

    // Alias tests

    "lpe() is an alias for lpenv()" {
        val p = lpe("0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.lpenv shouldBe 0.6
    }

    "lpe() works as pattern extension" {
        val p = note("c").lpe("0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.lpenv shouldBe 0.6
    }

    "lpe() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").lpe("0.6")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.lpenv shouldBe 0.6
    }
})
