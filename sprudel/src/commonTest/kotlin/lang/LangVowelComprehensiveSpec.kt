/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.lang

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.VowelBands
import io.peekandpoke.klang.audio_bridge.constants.VOWEL_FLOOR

/**
 * The vowel door against the formant catalogue: the door writes a vowel INDEX the orbit's formant bank
 * resolves. The orbit reads the `vowel.*` slots in `katalystParams`; the `FilterDef.Formant` the voice once
 * carried in `filters` left the wire in phase 3 step 9, so these rows resolve the slot through the orbit's table.
 *
 * The name-to-index conversion itself (round trip, the complete register-by-vowel cross product, case, unknowns,
 * rounding) is `CatalogueIndexSpec` in audio_bridge. What is pinned here is the door on top of it, and the only
 * full formant banks any spec pins: four anchors.
 */
class LangVowelComprehensiveSpec : StringSpec({

    /** The formant bands the orbit resolves from this wire voice's `vowel.vowel` slot, or null (no vowel). */
    fun bandsOf(voiceData: VoiceData): List<FilterDef.Formant.Band>? =
        VowelBands.bandsAt(voiceData.katalystParams?.get("vowel.vowel") ?: Double.NaN)

    fun voiceOf(vowel: String): VoiceData = note("c3").vowel(vowel = vowel).queryArc(0.0, 1.0)[0].data.toVoiceData()

    "every catalogue name, bare or in any case, reaches the wire as its own index and resolves to a 5-band bank" {
        // Written name -> the canonical catalogue name it must land on. The oracle is the list's own position,
        // not `VowelBands.indexOf`, which is what the door calls.
        val canonical = VowelBands.names.drop(1)
        val bare = canonical.filter { it.startsWith("soprano:") }.map { it.removePrefix("soprano:") to it }
        val cased = listOf("A" to "soprano:a", "SOPRANO:A" to "soprano:a", "Bass:E" to "bass:e", "TENOR:OE" to "tenor:oe")

        for ((written, name) in canonical.map { it to it } + bare + cased) {
            withClue("$written -> $name") {
                val voiceData = voiceOf(written)

                voiceData.katalystParams?.get("vowel.vowel") shouldBe VowelBands.names.indexOf(name).toDouble()
                bandsOf(voiceData).shouldNotBeNull().size shouldBe 5
            }
        }
    }

    "four anchor banks carry their formant frequencies, one per register, an umlaut and a diphthong nucleus" {
        val anchors = mapOf(
            "soprano:a" to listOf(800.0, 1150.0, 2900.0, 3900.0, 4950.0),
            "bass:e" to listOf(400.0, 1620.0, 2400.0, 2800.0, 3100.0),
            "tenor:ü" to listOf(290.0, 1500.0, 2300.0, 3250.0, 3540.0),
            // 'au' is sung on the 'a' nucleus
            "alto:au" to listOf(660.0, 1120.0, 2750.0, 3000.0, 3350.0),
        )

        for ((name, freqs) in anchors) {
            withClue(name) {
                bandsOf(voiceOf(name)).shouldNotBeNull().map { it.freq } shouldBe freqs
            }
        }
    }

    "a sequence resolves per event, and an unknown vowel, register or empty name is off" {
        // One of each unknown kind: a bare vowel, a register, a known register with an unknown vowel, and a
        // near miss (`aa` starts like `a`), which only a lookup that matches by prefix would resolve.
        val events = note("c3 c3 c3 c3 c3 c3").vowel(vowel = "a x bass:e baritone:a soprano:x aa").queryArc(0.0, 1.0)

        events.size shouldBe 6
        withClue("a") { bandsOf(events[0].data.toVoiceData()).shouldNotBeNull() }
        withClue("x") { bandsOf(events[1].data.toVoiceData()).shouldBeNull() }
        withClue("bass:e") { bandsOf(events[2].data.toVoiceData()).shouldNotBeNull() }
        withClue("baritone:a") { bandsOf(events[3].data.toVoiceData()).shouldBeNull() }
        withClue("soprano:x") { bandsOf(events[4].data.toVoiceData()).shouldBeNull() }
        withClue("aa") { bandsOf(events[5].data.toVoiceData()).shouldBeNull() }
        withClue("empty") { bandsOf(voiceOf("")).shouldBeNull() }
    }

    "the voice path resolves through the shared VowelBands table (Katalyst step 3c parity)" {
        // The door half of the parity `KatalystSlotResolverSpec` holds the other half of: a
        // declared Katalyst chain's `vowel` stage reads the SAME table through `KatalystResonatorWriter`.
        // `audio_be` does not depend on `sprudel`, so the landmark band is pinned on both sides.
        // The door fills the floor when a call names the vowel (Katalyst step 5a-3).
        val voiceData = note("c3").vowel(vowel = "a", wet = 0.3).queryArc(0.0, 1.0)[0].data.toVoiceData()
        val bands = bandsOf(voiceData).shouldNotBeNull()

        bands shouldBe VowelBands.bandsFor("soprano:a")
        bands[0] shouldBe FilterDef.Formant.Band(freq = 800.0, db = 0.0, q = 80.0)
        voiceData.katalystParams?.get("vowel.wet") shouldBe 0.3
        voiceData.katalystParams?.get("vowel.floor") shouldBe VOWEL_FLOOR
    }
})
