/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.filters.LowPassHighPassFilters
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.audio_bridge.constants.VOWEL_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.VOWEL_WET

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

    // Boxed once at construction, the twin of `KatalystBodyEffect.unsetFloor` and for its reason.
    private val unsetFloor: Double? = VOWEL_FLOOR

    // Holds the current (+ briefly the previous) stereo bank; crossfades on swap to declick live changes.
    private val swap = KatalystFilterSwap(sampleRate)

    /** Test seam: true while a formant bank is installed — the owner has a vowel. */
    internal val isEngaged: Boolean get() = swap.active

    /**
     * Test seams: WHAT is installed, the twins of `KatalystBodyEffect.installedBands` and friends,
     * there for the same reason. On a declared chain the vowel arrives as an INDEX, and the wrong
     * index sings the wrong vowel while still reading as engaged. They read the values the bank was
     * BUILT from, so an unset knob reads as its constant.
     */
    internal val installedBands: List<FilterDef.Formant.Band>? get() = curBands

    internal val installedMix: Double get() = curMix

    internal val installedFloor: Double? get() = curFloor

    /**
     * Configure from the OWNER voice's vowel, or from a declared chain's slots. `null` (nobody asks
     * for a vowel) turns the formant bank off.
     *
     * A non-finite `mix` or `floor` is UNSET and takes [VOWEL_WET] / [VOWEL_FLOOR], the twin of the
     * substitution [KatalystBodyEffect.configure] makes and of the one `KatalystSlots.vowelDef`
     * makes for a declared chain's slots, so all three paths install the same bank.
     *
     * **Open question, recorded 2026-09-18 (round 1 of Katalyst step 5a-2), not a regression:** the
     * same hard CUT on off that `KatalystBodyEffect.configure` records, for the same reason and
     * with the same shape of fix (a `KatalystFilterSwap.fadeOut()` over the usual 12 ms). The two
     * stages share the swap IMPLEMENTATION (each owns its own instance), so whichever one gets the
     * fix, both do.
     */
    fun configure(vowel: FilterDef.Formant?) {
        if (vowel == null) {
            if (swap.active) reset() // owner has no vowel → turn off, once
            return
        }

        // No production caller can hand this a non-finite value any more (Katalyst step 5b-1): the
        // one caller is `KatalystSlots.vowelDef`, which substitutes VOWEL_WET and the floor one
        // layer up, on every chain. Kept as the stage's contract for a DIRECT caller, which is what
        // the effect specs are; the twin's KDoc carries the full note.
        // NaN-guards, before the comparison for the reason [KatalystBodyEffect.configure] spells
        // out: a NaN is never equal to itself, so an unguarded non-finite mix rebuilt the whole
        // bank on every block and restarted a crossfade that never completed, and being nullable
        // does not save the floor from the same comparison (measured on both targets, see the
        // body's guard). A null floor stays null,
        // which `createFormant` reads as VOWEL_FLOOR.
        val mix = if (vowel.mix.isFinite()) vowel.mix else VOWEL_WET
        val rawFloor = vowel.floor
        val floor = if (rawFloor == null || rawFloor.isFinite()) rawFloor else unsetFloor

        // The swap crossfades from the old bank so a live vowel/mix/floor change doesn't click.
        if (vowel.bands != curBands || mix != curMix || floor != curFloor) {
            swap.set(
                LowPassHighPassFilters.createFormant(vowel.bands, mix, sampleRate, floor),
                LowPassHighPassFilters.createFormant(vowel.bands, mix, sampleRate, floor),
            )
            curBands = vowel.bands
            curMix = mix
            curFloor = floor
        }
    }

    override fun reset() {
        curBands = null
        curMix = Double.NaN
        curFloor = Double.NaN
        swap.clear()
    }

    /**
     * False for the same reason as the body's: the formant bank's integrators and the swap
     * crossfade hold state, but all of it goes into `ctx.mixBuffer`, which
     * `Cylinder.isMixBufferSilent()` scans after the chain runs. See [KatalystBodyEffect.hasTail].
     */
    override fun hasTail(): Boolean = false

    /** Rents nothing, so retiring is the clean slate. */
    override fun retire() {
        reset()
    }

    override fun process(ctx: KatalystContext) {
        swap.process(ctx.mixBuffer, ctx.blockFrames)
    }
}
