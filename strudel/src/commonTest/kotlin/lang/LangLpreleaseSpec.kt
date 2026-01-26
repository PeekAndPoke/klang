package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangLpreleaseSpec : StringSpec({

    "lprelease() sets StrudelVoiceData.lprelease" {
        val p = lprelease("0.5 0.7")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.lprelease shouldBe 0.5
        events[1].data.lprelease shouldBe 0.7
    }

    "lprelease() works as pattern extension" {
        val p = note("c").lprelease("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.lprelease shouldBe 0.5
    }

    "lprelease() works as string extension" {
        val p = "c".lprelease("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.lprelease shouldBe 0.5
    }

    "lprelease() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").lprelease("0.5")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.lprelease shouldBe 0.5
    }

    "lprelease() with continuous pattern sets lprelease correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").lprelease(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.lprelease shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.lprelease shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.lprelease shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.lprelease shouldBe (0.0 plusOrMinus EPSILON)
    }

    "lprelease() creates FilterEnvelope in FilterDef" {
        val data = io.peekandpoke.klang.strudel.StrudelVoiceData.empty.copy(
            cutoff = 1000.0,
            lprelease = 0.5
        )
        val voiceData = data.toVoiceData()
        val lpf = voiceData.filters[0] as FilterDef.LowPass

        lpf.envelope shouldNotBe null
        lpf.envelope?.release shouldBe 0.5
    }

    // Alias tests

    "lpr() is an alias for lprelease()" {
        val p = lpr("0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.lprelease shouldBe 0.6
    }

    "lpr() works as pattern extension" {
        val p = note("c").lpr("0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.lprelease shouldBe 0.6
    }

    "lpr() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").lpr("0.6")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.lprelease shouldBe 0.6
    }
})
