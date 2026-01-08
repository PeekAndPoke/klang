package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangSoundSpec : StringSpec({

    "top-level sound() sets VoiceData.sound correctly" {
        // Given a simple sequence of sounds within one cycle
        val p = sound("bd hh")

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then only assert the sound values in order
        events.size shouldBe 2
        events.map { it.data.sound } shouldBe listOf("bd", "hh")
    }

    "control pattern sound() sets VoiceData.sound on existing pattern" {
        // Given a base note pattern producing two events over two cycles
        val base = note("c3 e3")

        // When applying a control pattern that sets the sound per step
        val p = base.sound("tri square")

        val events = p.queryArc(0.0, 2.0)
        events.size shouldBe 4

        // Then only assert the sound values in order
        events.map { it.data.sound } shouldBe listOf("tri", "square", "tri", "square")
    }

    "sound() works within compiled code as top-level function" {
        val p = StrudelPattern.compile("""sound("bd hh")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.sound } shouldBe listOf("bd", "hh")
    }

    "sound() works within compiled code as chained-level function" {
        val p = StrudelPattern.compile("""note("a b").sound("bd hh")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events.map { it.data.sound } shouldBe listOf("bd", "hh")
    }
})
