/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.sprudel.SprudelPattern

/**
 * A CONTRACT row in the sense of the signal-flow plan §12, not a migration fixture: a non-finite
 * `wet` is UNSET on every surface, so `body("wood", wet = "NaN")` has to sound exactly like
 * `body("wood")`.
 *
 * Born from a defect found in review on 2026-09-18. `"NaN".toDoubleOrNull()` is a NaN, so a pattern
 * can put one on the wire; `SprudelVoiceData.toVoiceData` guards a null `bodyMix` and passes a
 * non-finite one straight through; and `KatalystBodyEffect.configure` decided whether to rebuild
 * the bank with `body.mix != curMix`, which is TRUE FOREVER for a NaN against a NaN. So the orbit
 * allocated two filter banks per block on the audio thread and restarted a 12 ms crossfade that
 * never completed, while the wet/dry law read the non-finite amount as a fully dry 0.0 and the
 * body was not even audible. The DECLARED path never had it (`KatalystSlots.bodyDef` substitutes
 * BODY_WET), which is why this row renders the BORN-WITH chain: no `.katalyst(...)` anywhere.
 *
 * Minimal on purpose (plan §12: no full songs as tests): one orbit, one synth chord, one door.
 * The renderer of `:jvmTest` has no sample bank, so the source is a supersaw and never an `s("bd")`.
 *
 * The VOWEL half of the same rule is stage level, in `KatalystFormantEffectSpec`: the two effects
 * are un-deduped twins, so one render row for the pair is enough to prove the born-with path.
 */
class KatalystBodyNonFiniteWetSpec : StringSpec({

    val voices = """note("c3 e3 g3 b3").s("supersaw")"""

    val materialOnly = """$voices.body(material = "wood").orbit(1)"""
    val nonFiniteWet = """$voices.body(material = "wood", wet = "NaN").orbit(1)"""

    "the song really does put a NON-FINITE wet on the wire, or the row below is about nothing" {
        // The premise, read off the compiled pattern. If `wet = "NaN"` ever stopped parsing to a
        // NaN (a parse change, a door change), the render row would compare a song against itself
        // and pass while guarding nothing at all.
        val pattern = SprudelPattern.compile(nonFiniteWet) ?: error("the song did not compile")
        val events = pattern.queryArc(0.0, 1.0)

        events.isNotEmpty() shouldBe true

        val body = events.first().toVoiceData().filters.getByType<FilterDef.Body>().shouldNotBeNull()

        withClue("the mix the born-with chain is configured from") { body.mix.isFinite() shouldBe false }
    }

    "a non-finite body wet renders exactly like the material-only call" {
        val plain = renderSong(materialOnly)
        val nan = renderSong(nonFiniteWet)

        nan.size shouldBe plain.size

        // Loud, or an all-silent pair would pass this row without saying anything.
        val peak = peakOf(plain)

        withClue("the song actually made sound") { peak shouldBeGreaterThan 3000 }
        withClue("max |non-finite wet - material only| in 16-bit counts, peak $peak") {
            maxDiff(plain, nan) shouldBe 0
        }
    }

    "the row has teeth: wood at the engine's own wet really is audible here" {
        // The engagement control. Without it, a body dropped on BOTH sides would make the row above
        // pass on two renders that agree about nothing.
        val withBody = renderSong(materialOnly)
        val withoutBody = renderSong("""$voices.orbit(1)""")

        withBody.size shouldBe withoutBody.size

        val diff = maxDiff(withBody, withoutBody)

        withClue("wood at BODY_WET has to move the samples, max diff was $diff") {
            diff shouldBeGreaterThan 100
        }
    }
})
