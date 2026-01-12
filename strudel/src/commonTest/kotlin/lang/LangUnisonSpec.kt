package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.EPSILON
import io.peekandpoke.klang.strudel.StrudelPattern

class LangUnisonSpec : StringSpec({

    "unison() sets VoiceData.voices" {
        val p = unison("4 8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.voices } shouldBe listOf(4.0, 8.0)
    }

    "uni() alias sets VoiceData.voices" {
        val p = uni("4 8")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 2
        events.map { it.data.voices } shouldBe listOf(4.0, 8.0)
    }

    "unison() works as pattern extension" {
        val p = note("c").unison("4")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.voices shouldBe 4.0
    }

    "uni() works as pattern extension" {
        val p = note("c").uni("4")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.voices shouldBe 4.0
    }

    "unison() works as string extension" {
        val p = "c".unison("4")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.voices shouldBe 4.0
    }

    "uni() works as string extension" {
        val p = "c".uni("4")
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 1
        events[0].data.voices shouldBe 4.0
    }

    "unison() works in compiled code" {
        val p = StrudelPattern.compile("""note("c").unison("4")""")
        val events = p?.queryArc(0.0, 1.0) ?: emptyList()
        events.size shouldBe 1
        events[0].data.voices shouldBe 4.0
    }

    "unison() with continuous pattern sets voices correctly" {
        // sine goes from 0.5 (at t=0) to 1.0 (at t=0.25) to 0.5 (at t=0.5) to 0.0 (at t=0.75)
        val p = note("a b c d").unison(sine)
        val events = p.queryArc(0.0, 1.0)

        events.size shouldBe 4
        // t=0.0: sine(0) = 0.5
        events[0].data.voices shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.25: sine(0.25) = 1.0
        events[1].data.voices shouldBe (1.0 plusOrMinus EPSILON)
        // t=0.5: sine(0.5) = 0.5
        events[2].data.voices shouldBe (0.5 plusOrMinus EPSILON)
        // t=0.75: sine(0.75) = 0.0
        events[3].data.voices shouldBe (0.0 plusOrMinus EPSILON)
    }
})
