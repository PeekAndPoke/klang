package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangVibratoSpec : StringSpec({

    "vibrato() sets VoiceData.vibrato rate" {
        val p = vibrato("5.0 10.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.vibrato } shouldBe listOf(5.0, 10.0)
    }

    "vib() alias sets VoiceData.vibrato rate" {
        val p = vib("5.0 10.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.vibrato } shouldBe listOf(5.0, 10.0)
    }

    "vibrato() works as pattern extension" {
        val p = note("c").vibrato("5.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.vibrato shouldBe 5.0
    }

    "vibrato() works as string extension" {
        val p = "c".vibrato("5.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.vibrato shouldBe 5.0
    }

    "vibrato() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").vibrato("5.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.vibrato shouldBe 5.0
    }

    "vibrato() with continuous pattern sets vibrato correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").vibrato(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.vibrato shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.vibrato shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.vibrato shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.vibrato shouldBe (0.0 plusOrMinus EPSILON)
    }
})
