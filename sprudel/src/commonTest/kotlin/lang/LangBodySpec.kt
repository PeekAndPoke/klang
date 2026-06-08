package io.peekandpoke.klang.sprudel.lang

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.sprudel.SprudelPattern
import io.peekandpoke.klang.sprudel.dslInterfaceTests

class LangBodySpec : StringSpec({

    "body dsl interface" {
        val pat = "c3"
        val material = "wood"

        dslInterfaceTests(
            "pattern.body(m)" to note(pat).body(material),
            "script pattern.body(m)" to SprudelPattern.compile("""note("$pat").body("$material")"""),
            "string.body(m)" to pat.body(material),
            "script string.body(m)" to SprudelPattern.compile(""""$pat".body("$material")"""),
            "body(m)" to note(pat).apply(body(material)),
            "script body(m)" to SprudelPattern.compile("""note("$pat").apply(body("$material"))"""),
        ) { _, events ->
            events.shouldNotBeEmpty()
            events[0].data.body shouldBe "wood"
        }
    }

    "body() sets the body property case-insensitively" {
        val events = note("c3").body("Wood").queryArc(0.0, 1.0)
        events[0].data.body shouldBe "wood"
    }

    "body() works across a sequence" {
        val events = note("c3 e3").body("wood tube").queryArc(0.0, 1.0)
        events.size shouldBe 2
        events[0].data.body shouldBe "wood"
        events[1].data.body shouldBe "tube"
    }

    "body() converts to FilterDef.Body in toVoiceData() with default mix" {
        val events = note("c3").body("wood").queryArc(0.0, 1.0)
        val voiceData = events[0].data.toVoiceData()

        voiceData.filters.filters.size shouldBe 1
        val bodyFilter = voiceData.filters.filters[0] as FilterDef.Body
        bodyFilter.bands.size shouldBe 8
        bodyFilter.mix shouldBe 0.5
    }

    "body() supports all materials (wood, tube, glass, membrane)" {
        listOf("wood", "tube", "glass", "membrane").forEach { material ->
            val events = note("c3").body(material).queryArc(0.0, 1.0)
            val voiceData = events[0].data.toVoiceData()

            voiceData.filters.filters.size shouldBe 1
            (voiceData.filters.filters[0] is FilterDef.Body) shouldBe true
        }
    }

    "bodyMix() overrides the dry/wet mix" {
        val events = note("c3").body("tube").bodyMix(0.6).queryArc(0.0, 1.0)
        val voiceData = events[0].data.toVoiceData()

        val bodyFilter = voiceData.filters.filters[0] as FilterDef.Body
        bodyFilter.mix shouldBe 0.6
    }

    "body() with unknown material is ignored" {
        val events = note("c3").body("unobtainium").queryArc(0.0, 1.0)
        val voiceData = events[0].data.toVoiceData()

        // No body filter is created for an unknown material — fail soft.
        voiceData.filters.filters.size shouldBe 0
    }

    "body sits before the lowpass in the canonical filter order" {
        val events = note("c3").lpf(800).body("wood").queryArc(0.0, 1.0)
        val filters = events[0].data.toVoiceData().filters.filters

        filters.size shouldBe 2
        (filters[0] is FilterDef.Body) shouldBe true
        (filters[1] is FilterDef.LowPass) shouldBe true
    }
})
