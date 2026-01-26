package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangLpfSpec : StringSpec({

    "lpf() sets VoiceData.cutoff and adds FilterDef.LowPass" {
        val p = lpf("1000 500")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.cutoff shouldBe 1000.0
        events[1].data.cutoff shouldBe 500.0
    }

    "lpf() works as pattern extension" {
        val p = note("c").lpf("1000")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.cutoff shouldBe 1000.0
    }

    "lpf() works as string extension" {
        val p = "c".lpf("1000")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.cutoff shouldBe 1000.0
    }

    "lpf() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").lpf("1000")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.cutoff shouldBe 1000.0
    }

    "lpf() with continuous pattern sets cutoffHz correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").lpf(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.cutoff shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.cutoff shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.cutoff shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.cutoff shouldBe (0.0 plusOrMinus EPSILON)
    }

    // Alias tests

    "cutoff() is an alias for lpf()" {
        val p = cutoff("1000 500")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.cutoff shouldBe 1000.0
        events[1].data.cutoff shouldBe 500.0
    }

    "cutoff() works as pattern extension" {
        val p = note("c").cutoff("800")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.cutoff shouldBe 800.0
    }

    "cutoff() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").cutoff("800")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.cutoff shouldBe 800.0
    }

    "ctf() is an alias for lpf()" {
        val p = ctf("1200")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.cutoff shouldBe 1200.0
    }

    "ctf() works as pattern extension" {
        val p = note("c").ctf("1200")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.cutoff shouldBe 1200.0
    }

    "lp() is an alias for lpf()" {
        val p = lp("2000")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.cutoff shouldBe 2000.0
    }

    "lp() works as pattern extension" {
        val p = note("c").lp("2000")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.cutoff shouldBe 2000.0
    }
})
