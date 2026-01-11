package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
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

    "unison() works as string extension" {
        val p = "c".unison("4")
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
})
