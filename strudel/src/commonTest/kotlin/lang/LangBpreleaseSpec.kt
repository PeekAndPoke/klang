package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangBpreleaseSpec : StringSpec({

    "bprelease() sets StrudelVoiceData.bprelease" {
        val p = bprelease("0.4 0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.bprelease shouldBe 0.4
        events[1].data.bprelease shouldBe 0.5
    }

    "bprelease() works as pattern extension" {
        val p = note("c").bprelease("0.4")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.bprelease shouldBe 0.4
    }

    "bprelease() works as string extension" {
        val p = "c".bprelease("0.4")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.bprelease shouldBe 0.4
    }

    "bprelease() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").bprelease("0.4")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.bprelease shouldBe 0.4
    }

    "bprelease() with continuous pattern sets bprelease correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").bprelease(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.bprelease shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.bprelease shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.bprelease shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.bprelease shouldBe (0.0 plusOrMinus EPSILON)
    }

    "bprelease() creates FilterEnvelope in FilterDef" {
        val data = io.peekandpoke.klang.strudel.StrudelVoiceData.empty.copy(
            bandf = 1000.0,
            bprelease = 0.4
        )
        val voiceData = data.toVoiceData()
        val bpf = voiceData.filters[0] as FilterDef.BandPass

        bpf.envelope shouldNotBe null
        bpf.envelope?.release shouldBe 0.4
    }

    // Alias tests

    "bpr() is an alias for bprelease()" {
        val p = bpr("0.45")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.bprelease shouldBe 0.45
    }

    "bpr() works as pattern extension" {
        val p = note("c").bpr("0.45")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.bprelease shouldBe 0.45
    }

    "bpr() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").bpr("0.45")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.bprelease shouldBe 0.45
    }
})
