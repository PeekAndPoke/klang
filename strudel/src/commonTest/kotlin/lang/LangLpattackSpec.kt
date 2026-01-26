package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangLpattackSpec : StringSpec({

    "lpattack() sets StrudelVoiceData.lpattack" {
        val p = lpattack("0.05 0.1")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.lpattack shouldBe 0.05
        events[1].data.lpattack shouldBe 0.1
    }

    "lpattack() works as pattern extension" {
        val p = note("c").lpattack("0.05")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.lpattack shouldBe 0.05
    }

    "lpattack() works as string extension" {
        val p = "c".lpattack("0.05")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.lpattack shouldBe 0.05
    }

    "lpattack() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").lpattack("0.05")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.lpattack shouldBe 0.05
    }

    "lpattack() with continuous pattern sets lpattack correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").lpattack(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.lpattack shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.lpattack shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.lpattack shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.lpattack shouldBe (0.0 plusOrMinus EPSILON)
    }

    "lpattack() creates FilterEnvelope in FilterDef" {
        val data = io.peekandpoke.klang.strudel.StrudelVoiceData.empty.copy(
            cutoff = 1000.0,
            lpattack = 0.05
        )
        val voiceData = data.toVoiceData()
        val lpf = voiceData.filters[0] as FilterDef.LowPass

        lpf.envelope shouldNotBe null
        lpf.envelope?.attack shouldBe 0.05
    }

    // Alias tests

    "lpa() is an alias for lpattack()" {
        val p = lpa("0.08")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.lpattack shouldBe 0.08
    }

    "lpa() works as pattern extension" {
        val p = note("c").lpa("0.08")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.lpattack shouldBe 0.08
    }

    "lpa() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").lpa("0.08")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.lpattack shouldBe 0.08
    }
})