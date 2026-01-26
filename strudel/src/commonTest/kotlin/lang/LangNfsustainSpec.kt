package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangNfsustainSpec : StringSpec({

    "nfsustain() sets StrudelVoiceData.nfsustain" {
        val p = nfsustain("0.7 0.8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.nfsustain shouldBe 0.7
        events[1].data.nfsustain shouldBe 0.8
    }

    "nfsustain() works as pattern extension" {
        val p = note("c").nfsustain("0.7")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.nfsustain shouldBe 0.7
    }

    "nfsustain() works as string extension" {
        val p = "c".nfsustain("0.7")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.nfsustain shouldBe 0.7
    }

    "nfsustain() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").nfsustain("0.7")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.nfsustain shouldBe 0.7
    }

    "nfsustain() with continuous pattern sets nfsustain correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").nfsustain(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.nfsustain shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.nfsustain shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.nfsustain shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.nfsustain shouldBe (0.0 plusOrMinus EPSILON)
    }

    "nfsustain() creates FilterEnvelope in FilterDef" {
        val data = io.peekandpoke.klang.strudel.StrudelVoiceData.empty.copy(
            notchf = 1000.0,
            nfsustain = 0.7
        )
        val voiceData = data.toVoiceData()
        val nf = voiceData.filters[0] as FilterDef.Notch

        nf.envelope shouldNotBe null
        nf.envelope?.sustain shouldBe 0.7
    }

    // Alias tests

    "nfs() is an alias for nfsustain()" {
        val p = nfs("0.85")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.nfsustain shouldBe 0.85
    }

    "nfs() works as pattern extension" {
        val p = note("c").nfs("0.85")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.nfsustain shouldBe 0.85
    }

    "nfs() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").nfs("0.85")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.nfsustain shouldBe 0.85
    }
})
