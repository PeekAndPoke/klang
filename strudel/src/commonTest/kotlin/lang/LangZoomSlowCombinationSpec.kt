package io.peekandpoke.klang.strudel.lang

import io.kotest.assertions.assertSoftly
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON

class LangZoomSlowCombinationSpec : StringSpec({

    "zoom(0, 0.5) alone - should stretch first half to full cycle" {
        val subject = s("bd sd ht lt").zoom(0.0, 0.5)

        val events = subject.queryArc(0.0, 1.0).filter { it.isOnset }

        println("zoom(0, 0.5) alone:")
        events.forEachIndexed { i, e ->
            println("  Event $i: sound=${e.data.sound}, part=[${e.part.begin}, ${e.part.end}]")
        }

        // Should have "bd" and "sd" stretched to fill full cycle
        assertSoftly {
            events.size shouldBe 2
            events[0].data.sound shouldBe "bd"
            events[1].data.sound shouldBe "sd"

            // Each event should be 0.5 cycles long (stretched from 0.25)
            events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
            events[0].part.end.toDouble() shouldBe (0.5 plusOrMinus EPSILON)

            events[1].part.begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
            events[1].part.end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        }
    }

    "slow(0.5) alone - should compress to half cycle and repeat" {
        val subject = s("bd sd").slow(0.5)

        val events = subject.queryArc(0.0, 1.0).filter { it.isOnset }

        println("\nslow(0.5) alone:")
        events.forEachIndexed { i, e ->
            println("  Event $i: sound=${e.data.sound}, part=[${e.part.begin}, ${e.part.end}]")
        }

        // Should have "bd sd" compressed to 0.5 cycles, repeating twice
        assertSoftly {
            events.size shouldBe 4  // bd, sd, bd, sd
            events[0].data.sound shouldBe "bd"
            events[1].data.sound shouldBe "sd"
            events[2].data.sound shouldBe "bd"
            events[3].data.sound shouldBe "sd"

            // Each event should be 0.25 cycles long
            events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
            events[0].part.end.toDouble() shouldBe (0.25 plusOrMinus EPSILON)

            events[1].part.begin.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
            events[1].part.end.toDouble() shouldBe (0.5 plusOrMinus EPSILON)

            events[2].part.begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
            events[2].part.end.toDouble() shouldBe (0.75 plusOrMinus EPSILON)

            events[3].part.begin.toDouble() shouldBe (0.75 plusOrMinus EPSILON)
            events[3].part.end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        }
    }

    "zoom(0, 0.5).slow(0.5) combined - should zoom then compress" {
        val subject = s("bd sd ht lt").zoom(0.0, 0.5).slow(0.5)

        val events = subject.queryArc(0.0, 1.0).filter { it.isOnset }

        println("\nzoom(0, 0.5).slow(0.5) combined:")
        events.forEachIndexed { i, e ->
            println("  Event $i: sound=${e.data.sound}, part=[${e.part.begin}, ${e.part.end}]")
        }

        // Should have:
        // 1. zoom takes "bd sd" (first half) and stretches to full cycle
        // 2. slow compresses that to half cycle, making it repeat
        // Result: "bd sd bd sd" each 0.25 cycles long
        assertSoftly {
            events.size shouldBe 4  // bd, sd, bd, sd
            events[0].data.sound shouldBe "bd"
            events[1].data.sound shouldBe "sd"
            events[2].data.sound shouldBe "bd"
            events[3].data.sound shouldBe "sd"

            // Each event should be 0.25 cycles long
            events[0].part.begin.toDouble() shouldBe (0.0 plusOrMinus EPSILON)
            events[0].part.end.toDouble() shouldBe (0.25 plusOrMinus EPSILON)

            events[1].part.begin.toDouble() shouldBe (0.25 plusOrMinus EPSILON)
            events[1].part.end.toDouble() shouldBe (0.5 plusOrMinus EPSILON)

            events[2].part.begin.toDouble() shouldBe (0.5 plusOrMinus EPSILON)
            events[2].part.end.toDouble() shouldBe (0.75 plusOrMinus EPSILON)

            events[3].part.begin.toDouble() shouldBe (0.75 plusOrMinus EPSILON)
            events[3].part.end.toDouble() shouldBe (1.0 plusOrMinus EPSILON)
        }
    }
})
