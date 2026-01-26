package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangDistortSpec : StringSpec({

    "distort() sets VoiceData.distort" {
        val p = distort("0.5 10.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.distort } shouldBe listOf(0.5, 10.0)
    }

    "distort() works as pattern extension" {
        val p = note("c").distort("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.distort shouldBe 0.5
    }

    "distort() works as string extension" {
        val p = "c".distort("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.distort shouldBe 0.5
    }

    "distort() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").distort("0.5")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.distort shouldBe 0.5
    }

    "distort() with continuous pattern sets distort correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").distort(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.distort shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.distort shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.distort shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.distort shouldBe (0.0 plusOrMinus EPSILON)
    }

    // Alias tests

    "dist() is an alias for distort()" {
        val p = dist("3.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.distort shouldBe 3.0
    }

    "dist() works as pattern extension" {
        val p = note("c").dist("3.0")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.distort shouldBe 3.0
    }

    "dist() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").dist("3.0")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.distort shouldBe 3.0
    }
})
