/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.filters.LowPassHighPassFilters
import io.peekandpoke.klang.audio_bridge.FilterDef

/**
 * Orbit-level **vowel / formant** resonator — the [KatalystBodyEffect] counterpart for `vowel(...)`.
 *
 * Like body, a vowel is a timbre shaper of the whole orbit, so it runs once on the summed stereo mix
 * (one mono [LowPassHighPassFilters.createFormant] instance per channel) instead of per voice. Only the
 * orbit's owning voice configures it (see `Cylinder`'s VoiceLease), so `null` (owner has no vowel) turns
 * the resonator OFF; [reset] deactivates it on orbit teardown.
 *
 * NOTE: near-verbatim twin of [KatalystBodyEffect] (only the band type + factory fn differ). Left
 * un-deduped on purpose — both will fold into a single generic resonator once the Katalyst DSL lands.
 */
class KatalystFormantEffect(
    private val sampleRate: Double,
) : KatalystEffect {

    private var curBands: List<FilterDef.Formant.Band>? = null
    private var curMix: Double = Double.NaN
    private var curFloor: Double? = Double.NaN

    // Holds the current (+ briefly the previous) stereo bank; crossfades on swap to declick live changes.
    private val swap = KatalystFilterSwap(sampleRate)

    /** Configure from the OWNER voice's vowel. `null` (owner has no vowel) turns the resonator off. */
    fun configure(vowel: FilterDef.Formant?) {
        if (vowel == null) {
            if (swap.active) reset() // owner has no vowel → turn off, once
            return
        }
        // The swap crossfades from the old bank so a live vowel/mix/floor change doesn't click.
        if (vowel.bands != curBands || vowel.mix != curMix || vowel.floor != curFloor) {
            swap.set(
                LowPassHighPassFilters.createFormant(vowel.bands, vowel.mix, sampleRate, vowel.floor),
                LowPassHighPassFilters.createFormant(vowel.bands, vowel.mix, sampleRate, vowel.floor),
            )
            curBands = vowel.bands
            curMix = vowel.mix
            curFloor = vowel.floor
        }
    }

    fun reset() {
        curBands = null
        curMix = Double.NaN
        curFloor = Double.NaN
        swap.clear()
    }

    override fun process(ctx: KatalystContext) {
        swap.process(ctx.mixBuffer, ctx.blockFrames)
    }
}
