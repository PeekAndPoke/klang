package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangNfreleaseSpec : StringSpec({

    "nfrelease() sets StrudelVoiceData.nfrelease" {
        val p = nfrelease("0.4 0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.nfrelease shouldBe 0.4
        events[1].data.nfrelease shouldBe 0.5
    }

    "nfrelease() works as pattern extension" {
        val p = note("c").nfrelease("0.4")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.nfrelease shouldBe 0.4
    }

    "nfrelease() works as string extension" {
        val p = "c".nfrelease("0.4")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.nfrelease shouldBe 0.4
    }

    "nfrelease() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").nfrelease("0.4")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.nfrelease shouldBe 0.4
    }

    "nfrelease() with continuous pattern sets nfrelease correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").nfrelease(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.nfrelease shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.nfrelease shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.nfrelease shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.nfrelease shouldBe (0.0 plusOrMinus EPSILON)
    }

    "nfrelease() creates FilterEnvelope in FilterDef" {
        val data = io.peekandpoke.klang.strudel.StrudelVoiceData.empty.copy(
            notchf = 1000.0,
            nfrelease = 0.4
        )
        val voiceData = data.toVoiceData()
        val nf = voiceData.filters[0] as FilterDef.Notch

        nf.envelope shouldNotBe null
        nf.envelope?.release shouldBe 0.4
    }

    // Alias tests

    "nfr() is an alias for nfrelease()" {
        val p = nfr("0.45")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.nfrelease shouldBe 0.45
    }

    "nfr() works as pattern extension" {
        val p = note("c").nfr("0.45")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.nfrelease shouldBe 0.45
    }

    "nfr() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").nfr("0.45")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.nfrelease shouldBe 0.45
    }
})
