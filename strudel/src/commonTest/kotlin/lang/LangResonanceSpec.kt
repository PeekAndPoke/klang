package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangResonanceSpec : StringSpec({

    "resonance() sets VoiceData.resonance" {
        val p = resonance("5 10")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.resonance shouldBe 5.0
        events[1].data.resonance shouldBe 10.0
    }

    "res() alias works" {
        val p = res("5")
        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 1
        events[0].data.resonance shouldBe 5.0
    }

    "resonance() updates existing filters" {
        // Apply LPF first (default Q=1.0), then update resonance to 5.0
        val p = note("c").lpf("1000").resonance("5.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.resonance shouldBe 5.0
    }

    "resonance() works as string extension" {
        val p = "c".resonance("5.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.resonance shouldBe 5.0
    }

    "resonance() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").resonance("5.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.resonance shouldBe 5.0
    }

    "resonance() with continuous pattern sets resonance correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").resonance(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.resonance shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.resonance shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.resonance shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.resonance shouldBe (0.0 plusOrMinus EPSILON)
    }

    "lpq() is an alias for resonance()" {
        val p = lpq("8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.resonance shouldBe 8.0
    }

    "lpq() works as pattern extension" {
        val p = note("c").lpq("8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.resonance shouldBe 8.0
    }

    "lpq() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").lpq("8")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.resonance shouldBe 8.0
    }
})
