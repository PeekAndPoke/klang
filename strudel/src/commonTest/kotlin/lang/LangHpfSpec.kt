package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangHpfSpec : StringSpec({

    "hpf() sets VoiceData.hcutoff and adds FilterDef.HighPass" {
        val p = hpf("1000 500")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events[0].data.hcutoff shouldBe 1000.0
        events[1].data.hcutoff shouldBe 500.0
    }

    "hpf() works as pattern extension" {
        val p = note("c").hpf("1000")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hcutoff shouldBe 1000.0
    }

    "hpf() works as string extension" {
        val p = "c".hpf("1000")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hcutoff shouldBe 1000.0
    }

    "hpf() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").hpf("1000")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.hcutoff shouldBe 1000.0
    }

    "hpf() with continuous pattern sets cutoffHz correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").hpf(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.hcutoff shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.hcutoff shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.hcutoff shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.hcutoff shouldBe (0.0 plusOrMinus EPSILON)
    }

    // Alias tests

    "hp() is an alias for hpf()" {
        val p = hp("800")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hcutoff shouldBe 800.0
    }

    "hp() works as pattern extension" {
        val p = note("c").hp("800")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hcutoff shouldBe 800.0
    }

    "hp() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").hp("800")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.hcutoff shouldBe 800.0
    }

    "hcutoff() is an alias for hpf()" {
        val p = hcutoff("1200")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hcutoff shouldBe 1200.0
    }

    "hcutoff() works as pattern extension" {
        val p = note("c").hcutoff("1200")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.hcutoff shouldBe 1200.0
    }
})
