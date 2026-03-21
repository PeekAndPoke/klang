package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.sprudel.EPSILON
import io.peekandpoke.klang.sprudel.SprudelPattern

class LangDelaySpec : StringSpec({

    "delay() sets VoiceData.delay" {
        val p = note("a b").apply(delay("0.5 0.8"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.delay } shouldBe listOf(0.5, 0.8)
    }

    "delay() works as pattern extension" {
        val p = note("c").delay("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.delay shouldBe 0.5
    }

    "delay() works as string extension" {
        val p = "c".delay("0.5")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.delay shouldBe 0.5
    }

    "delay() works in compiled code" {
        val p = SprudelPattern.compile("""note("c").delay("0.5")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.delay shouldBe 0.5
    }

    "delay() with continuous pattern sets delay correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").delay(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.delay shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.delay shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.delay shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.delay shouldBe (0.0 plusOrMinus EPSILON)
    }

    // -- combined "wet:time:feedback" format -----------------------------------------------

    "delay() combined sets all three VoiceData fields" {
        val p = note("c").delay("0.5:0.25:0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            delay shouldBe 0.5
            delayTime shouldBe 0.25
            delayFeedback shouldBe 0.6
        }
    }

    "delay() combined with partial params sets only specified fields" {
        val p = note("c").delay("0.8:0.125")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            delay shouldBe 0.8
            delayTime shouldBe 0.125
            delayFeedback shouldBe null
        }
    }

    "delay() combined works as string extension" {
        val p = "c".delay("0.5:0.25:0.6")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            delay shouldBe 0.5
            delayTime shouldBe 0.25
            delayFeedback shouldBe 0.6
        }
    }

    "delay() combined works in compiled code" {
        val p = SprudelPattern.compile("""note("c").delay("0.5:0.25:0.6")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        with(events[0].data) {
            delay shouldBe 0.5
            delayTime shouldBe 0.25
            delayFeedback shouldBe 0.6
        }
    }

    "delay() combined works with mini-notation patterns" {
        val p = note("c3 e3").delay("<0.3:0.125 0.6:0.25:0.8>")
        val cycle0 = p.queryArc(0.0, 1.0)
        val cycle1 = p.queryArc(1.0, 2.0)

        assertSoftly {
            cycle0.size shouldBe 2
            cycle0[0].data.delay shouldBe 0.3
            cycle0[0].data.delayTime shouldBe 0.125

            cycle1.size shouldBe 2
            cycle1[0].data.delay shouldBe 0.6
            cycle1[0].data.delayTime shouldBe 0.25
            cycle1[0].data.delayFeedback shouldBe 0.8
        }
    }

    "delay() combined works chained with other effects" {
        val p = note("c").apply(gain(0.8).delay("0.5:0.25:0.6"))
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        with(events[0].data) {
            gain shouldBe 0.8
            delay shouldBe 0.5
            delayTime shouldBe 0.25
            delayFeedback shouldBe 0.6
        }
    }

    "delay() single value still works (backward compat)" {
        val p = note("c").delay(0.7)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.delay shouldBe 0.7
        events[0].data.delayTime shouldBe null
        events[0].data.delayFeedback shouldBe null
    }
})
