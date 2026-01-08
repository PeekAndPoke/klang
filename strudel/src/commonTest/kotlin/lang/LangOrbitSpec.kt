package io.peekandpoke.klang.strudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.strudel.StrudelPattern

class LangOrbitSpec : StringSpec({

    "top-level orbit() sets VoiceData.orbit correctly" {
        // Given an orbit pattern with space-delimited values
        val p = orbit("0 1 2")

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then orbit values are set
        events.size shouldBe 3
        events[0].data.orbit shouldBe 0
        events[1].data.orbit shouldBe 1
        events[2].data.orbit shouldBe 2
    }

    "control pattern orbit() sets orbit on existing pattern" {
        // Given a base sound pattern
        val base = sound("bd hh sn")

        // When applying orbit as control pattern with space-delimited values
        val p = base.orbit("0 1 2")

        val events = p.queryArc(0.0, 1.0)
        events.size shouldBe 3

        // Then both sound and orbit values are set
        events[0].data.sound shouldBe "bd"
        events[0].data.orbit shouldBe 0
        events[1].data.sound shouldBe "hh"
        events[1].data.orbit shouldBe 1
        events[2].data.sound shouldBe "sn"
        events[2].data.orbit shouldBe 2
    }

    "orbit() with default value 0" {
        // Given orbit with 0 (default orbit)
        val p = sound("bd").orbit("0")

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then orbit is 0
        events.size shouldBe 1
        events[0].data.orbit shouldBe 0
    }

    "orbit() with multiple output buses" {
        // Given orbit with different bus numbers
        val p = sound("bd hh").orbit("0 3")

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then different orbits are assigned
        events.size shouldBe 2
        events[0].data.orbit shouldBe 0
        events[1].data.orbit shouldBe 3
    }

    "orbit() with higher bus numbers" {
        // Given orbit with higher bus numbers
        val p = sound("bd hh").orbit("5 10")

        // When querying one cycle
        val events = p.queryArc(0.0, 1.0)

        // Then higher orbit values are applied
        events.size shouldBe 2
        events[0].data.orbit shouldBe 5
        events[1].data.orbit shouldBe 10
    }

    "orbit() works within compiled code" {
        val p = StrudelPattern.compile("""orbit("0 1 2 3")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 4
        events[0].data.orbit shouldBe 0
        events[1].data.orbit shouldBe 1
        events[2].data.orbit shouldBe 2
        events[3].data.orbit shouldBe 3
    }

    "orbit() as modifier works within compiled code" {
        val p = StrudelPattern.compile("""sound("bd hh").orbit("0 2")""")

        val events = p?.queryArc(0.0, 1.0) ?: emptyList()

        events.size shouldBe 2
        events[0].data.sound shouldBe "bd"
        events[0].data.orbit shouldBe 0
        events[1].data.sound shouldBe "hh"
        events[1].data.orbit shouldBe 2
    }
})
