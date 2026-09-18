/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.BodyMaterials
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

    "every catalogue material resolves to an 8-mode body (except 'none')" {
        BodyMaterials.names.filter { it != "none" }.forEach { material ->
            val voiceData = note("c3").body(material).queryArc(0.0, 1.0)[0].data.toVoiceData()

            withClue(material) {
                voiceData.filters.filters.size shouldBe 1
                (voiceData.filters.filters[0] as FilterDef.Body).bands.size shouldBe 8
            }
        }
    }

    "body(\"none\") resets — clears a previously set body" {
        val voiceData = note("c3").body("wood").body("none").queryArc(0.0, 1.0)[0].data.toVoiceData()
        voiceData.filters.filters.size shouldBe 0
    }

    "body(wet = ...) overrides the dry/wet mix" {
        val events = note("c3").body(material = "tube", wet = 0.6).queryArc(0.0, 1.0)
        val voiceData = events[0].data.toVoiceData()

        val bodyFilter = voiceData.filters.filters[0] as FilterDef.Body
        bodyFilter.mix shouldBe 0.6
    }

    "body(wet = ...) passes raw values to the wire (the [0, 1] coercion is the ENGINE's, since C4)" {
        listOf(1.5, 5.0, 100.0).forEach { mix ->
            val events = note("c3").body(material = "brass", wet = mix).queryArc(0.0, 1.0)
            val bodyFilter = events[0].data.toVoiceData().filters.filters[0] as FilterDef.Body
            bodyFilter.mix shouldBe mix
        }
    }

    "body(floor = ...) is null by default (engine default) and settable" {
        val defaulted = note("c3").body("wood").queryArc(0.0, 1.0)[0]
            .data.toVoiceData().filters.filters[0] as FilterDef.Body
        defaulted.floor shouldBe null

        val overridden = note("c3").body(material = "wood", floor = 0.2).queryArc(0.0, 1.0)[0]
            .data.toVoiceData().filters.filters[0] as FilterDef.Body
        overridden.floor shouldBe 0.2
    }

    "body params survive the grouped merge (material + wet + floor)" {
        // The wire carries the raw value; the [0, 1] coercion is the ENGINE's
        // (ParallelMixFilter, C4) — the surface knob is documented as [0, 1].
        val events = note("c3").body(material = "brass", wet = 0.8, floor = 0.15).queryArc(0.0, 1.0)
        val bodyFilter = events[0].data.toVoiceData().filters.filters[0] as FilterDef.Body
        events[0].data.body shouldBe "brass"
        bodyFilter.mix shouldBe 0.8
        bodyFilter.floor shouldBe 0.15
    }

    "body() with unknown material is ignored" {
        val events = note("c3").body("unobtainium").queryArc(0.0, 1.0)
        val voiceData = events[0].data.toVoiceData()

        // No body filter is created for an unknown material — fail soft.
        voiceData.filters.filters.size shouldBe 0
    }

    "the voice path resolves through the shared BodyMaterials table (Katalyst step 3c parity)" {
        // The voice half of the parity `KatalystSlotResolverSpec` holds the other half of: a
        // declared Katalyst chain's `body` stage reads the SAME table through `KatalystSlots`.
        // `audio_be` does not depend on `sprudel`, so the landmark mode is pinned on both sides.
        // The one difference between the paths is the floor FILL: a voice leaves `floor = null`,
        // which the engine reads as its default, while a declared stage writes that default out.
        val bodyFilter = note("c3").body(material = "wood", wet = 0.3).queryArc(0.0, 1.0)[0]
            .data.toVoiceData().filters.filters[0] as FilterDef.Body

        bodyFilter.bands shouldBe BodyMaterials.modesFor("wood")
        bodyFilter.bands[0] shouldBe FilterDef.Body.Mode(freq = 100.0, db = 3.0, q = 12.0)
        bodyFilter.mix shouldBe 0.3
        bodyFilter.floor shouldBe null
    }

    "body sits before the lowpass in the canonical filter order" {
        val events = note("c3").lpf(800).body("wood").queryArc(0.0, 1.0)
        val filters = events[0].data.toVoiceData().filters.filters

        filters.size shouldBe 2
        (filters[0] is FilterDef.Body) shouldBe true
        (filters[1] is FilterDef.LowPass) shouldBe true
    }
})
