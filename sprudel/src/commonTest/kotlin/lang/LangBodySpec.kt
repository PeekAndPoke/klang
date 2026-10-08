/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.BodyMaterials
import io.peekandpoke.klang.audio_bridge.constants.BODY_FLOOR
import io.peekandpoke.klang.sprudel.SprudelPattern

class LangBodySpec : StringSpec({

    "body() sets the body property case-insensitively" {
        val events = note("c3").body(material = "Wood").queryArc(0.0, 1.0)
        events[0].data.body shouldBe "wood"
    }

    // The orbit's body stage reads its SLOTS (`body.material`, `body.wet`, `body.floor` in `katalystParams`, Katalyst
    // step 5b-1); the `FilterDef.Body` the voice once carried in `filters` left the wire in phase 3 step 9. So these
    // rows read the slots the door writes, and resolve the material index through the table the orbit uses.
    fun slots(pattern: SprudelPattern): Map<String, Double> =
        pattern.queryArc(0.0, 1.0)[0].data.toVoiceData().katalystParams ?: emptyMap()

    fun modes(slots: Map<String, Double>) = BodyMaterials.modesAt(slots["body.material"] ?: Double.NaN)

    "every catalogue material resolves to an 8-mode body (except 'none')" {
        BodyMaterials.names.filter { it != "none" }.forEach { material ->
            withClue(material) { modes(slots(note("c3").body(material = material)))?.size shouldBe 8 }
        }
    }

    "body(material = \"none\") is the off switch: it clears a previously set body" {
        val slots = slots(note("c3").body(material = "wood").body(material = "none"))

        slots["body.material"] shouldBe 0.0
        modes(slots) shouldBe null
    }

    "body(wet = ...) passes raw values to the wire (the [0, 1] coercion is the ENGINE's, since C4)" {
        listOf(1.5, 5.0, 100.0).forEach { mix ->
            slots(note("c3").body(material = "brass", wet = mix))["body.wet"] shouldBe mix
        }
    }

    "body params survive the grouped merge (material + wet + floor)" {
        // The wire carries the raw value; the [0, 1] coercion is the ENGINE's (ResonatorBank, C4).
        val events = note("c3").body(material = "brass", wet = 0.8, floor = 0.15).queryArc(0.0, 1.0)
        val slots = events[0].data.toVoiceData().katalystParams ?: emptyMap()

        events[0].data.body shouldBe "brass"
        slots["body.material"] shouldBe BodyMaterials.indexOf("brass")
        slots["body.wet"] shouldBe 0.8
        slots["body.floor"] shouldBe 0.15
    }

    "body() with unknown material is ignored: its index is none, and no body resolves" {
        val slots = slots(note("c3").body(material = "unobtainium"))

        slots["body.material"] shouldBe 0.0
        modes(slots) shouldBe null
    }

    "the door resolves through the shared BodyMaterials table (Katalyst step 3c parity)" {
        // The door half of the parity `KatalystSlotResolverSpec` holds the other half of: a declared Katalyst
        // chain's `body` stage reads the SAME table through `KatalystResonatorWriter`. `audio_be` does not depend on
        // `sprudel`, so the landmark mode is pinned on both sides.
        val slots = slots(note("c3").body(material = "wood", wet = 0.3))

        modes(slots) shouldBe BodyMaterials.modesFor("wood")
        modes(slots)?.get(0) shouldBe BodyMaterials.Mode(freq = 100.0, db = 3.0, q = 12.0)
        slots["body.wet"] shouldBe 0.3
        slots["body.floor"] shouldBe BODY_FLOOR
    }

    "the body travels as orbit slots and the lowpass as its classic() slots, each in its own map" {
        val voiceData = note("c3").lpf(800).body(material = "wood").queryArc(0.0, 1.0)[0].data.toVoiceData()

        voiceData.katalystParams?.get("body.material") shouldBe BodyMaterials.indexOf("wood")
        voiceData.ignitorParams?.get("lpf.freq") shouldBe 800.0
        voiceData.ignitorParams?.keys?.any { it.startsWith("body.") } shouldBe false
    }
})
