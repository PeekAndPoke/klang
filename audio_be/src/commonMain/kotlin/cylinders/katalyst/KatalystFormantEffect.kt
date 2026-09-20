/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.AudioBackendContext
import io.peekandpoke.klang.audio_be.filters.AudioFilter
import io.peekandpoke.klang.audio_be.filters.LowPassHighPassFilters
import io.peekandpoke.klang.audio_be.filters.ResonatorBank
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.audio_bridge.constants.VOWEL_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.VOWEL_WET

/**
 * Orbit-level **vowel / formant** resonator — the [KatalystBodyEffect] counterpart for `vowel(...)`.
 *
 * Like body, a vowel is a timbre shaper of the whole orbit, so it runs once on the summed stereo mix
 * (one mono formant bank per channel) instead of per voice. It builds the bank and its blend
 * itself, [LowPassHighPassFilters.formantBank] and [LowPassHighPassFilters.wrapFormant], because
 * a morph needs the bank; [LowPassHighPassFilters.createFormant] is those two composed and is now
 * reference DSP for the specs. Only the
 * orbit's owning voice configures it (see `Cylinder`'s VoiceLease), so `null` (owner has no vowel) turns
 * the resonator OFF; [reset] deactivates it on orbit teardown.
 *
 * Every edge fades, intent and sound are two things, and [reset] / [retire] stay a hard cut, all
 * exactly as [KatalystBodyEffect]'s KDoc spells out (Katalyst step 5c-6). A VOWEL change morphs
 * the bank in service while [MORPH] holds (Katalyst step 5c-10): the five formants travel, formant
 * n to formant n, which is the change this morph was proposed for, a vowel sweeping like a mouth.
 *
 * NOTE: near-verbatim twin of [KatalystBodyEffect] (only the band type, the factory fn and the
 * WET/FLOOR constants differ); both build
 * a `ResonatorBank`, and the twin's KDoc says why the hosts stay two classes.
 */
class KatalystFormantEffect(
    private val sampleRate: Double,
    /** The frames of one render block; sizes the swap's scratch at construction (see [KatalystFilterSwap]). */
    blockFrames: Int = AudioBackendContext.RENDER_QUANTUM_FRAMES,
    /** Whether a vowel change morphs the bank in service; see [MORPH]. */
    private val morph: Boolean = MORPH,
) : KatalystEffect {

    companion object {
        /**
         * The twin of [KatalystBodyEffect.MORPH], per stage because the choice is per stage: `true`
         * morphs the bank in service on a vowel change, `false` crossfades two banks. Both paths
         * are live for the maintainer's listening comparison and the loser is deleted with the
         * choice; the full note is on the twin.
         */
        const val MORPH: Boolean = true
    }

    private var curBands: List<FilterDef.Formant.Band>? = null
    private var curMix: Double = Double.NaN
    private var curFloor: Double? = Double.NaN

    /** The left filter of the pair the last install built: what [KatalystFilterSwap.resume] looks for. */
    private var curLeft: AudioFilter? = null

    /** The two banks inside that pair, the things a morph travels; the twin's KDoc says why. */
    private var curBankL: ResonatorBank? = null
    private var curBankR: ResonatorBank? = null

    /** The morph target's three arrays, allocated once; the twin of the body's, for its reason. */
    private val morphFreq = DoubleArray(ResonatorBank.MORPH_CAPACITY)
    private val morphQ = DoubleArray(ResonatorBank.MORPH_CAPACITY)
    private val morphGain = DoubleArray(ResonatorBank.MORPH_CAPACITY)

    // Boxed once at construction, the twin of `KatalystBodyEffect.unsetFloor` and for its reason.
    private val unsetFloor: Double? = VOWEL_FLOOR

    // Holds the bank in service and, while a fade runs, the banks fading out; every edge fades.
    private val swap = KatalystFilterSwap(sampleRate, blockFrames)

    /** Test seam: the INTENT, true while the owner asks for a vowel (a bank may still fade out after). */
    internal val isEngaged: Boolean get() = swap.active

    /** Test seam: the SOUND, true while any bank may still be heard, a fade-out included. */
    internal val isSounding: Boolean get() = swap.sounding

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
     * A null fades the bank out, the twin of [KatalystBodyEffect.configure].
     */
    fun configure(vowel: FilterDef.Formant?) {
        if (vowel == null) {
            if (swap.active) {
                swap.clear() // the owner has no vowel: fade to dry, once
            }

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

        // The twin of the body's rebuild rule, the returning owner included.
        val unchanged = vowel.bands == curBands && mix == curMix && floor == curFloor

        if (unchanged) {
            if (swap.active) {
                return
            }

            // A return after a DIFFERENT change is not found here: the twin's comment says what
            // each mode does with it, and why both are continuous.
            val left = curLeft

            if (left != null && swap.resume(left)) {
                return
            }
        }

        // A change of the VOWEL alone, on the pair the swap still converges on, TRAVELS: the twin
        // of the body's morph and for its reasons (Katalyst 5c-10), the formants pairing by
        // position. A `wet`/`floor` change, or a pair that is not the target, crossfades as before.
        if (morph && mix == curMix && floor == curFloor && vowel.bands.size <= morphFreq.size) {
            val bankL = curBankL
            val bankR = curBankR
            val inService = curLeft

            if (bankL != null && bankR != null && inService != null && swap.isTarget(inService)) {
                val count = vowel.bands.size

                for (i in 0 until count) {
                    val band = vowel.bands[i]

                    morphFreq[i] = band.freq
                    morphQ[i] = band.q
                    morphGain[i] = LowPassHighPassFilters.vowelGain(band)
                }

                val tookL = bankL.morphTo(morphFreq, morphQ, morphGain, count)
                val tookR = bankR.morphTo(morphFreq, morphQ, morphGain, count)

                if (tookL && tookR) {
                    curBands = vowel.bands

                    return
                }
            }
        }

        val bankL = LowPassHighPassFilters.formantBank(vowel.bands, sampleRate)
        val bankR = LowPassHighPassFilters.formantBank(vowel.bands, sampleRate)
        val left = LowPassHighPassFilters.wrapFormant(bankL, mix, floor)

        swap.set(left, LowPassHighPassFilters.wrapFormant(bankR, mix, floor))
        curLeft = left
        curBankL = bankL
        curBankR = bankR
        curBands = vowel.bands
        curMix = mix
        curFloor = floor
    }

    /** A HARD cut, the twin of [KatalystBodyEffect.reset]. */
    override fun reset() {
        curBands = null
        curMix = Double.NaN
        curFloor = Double.NaN
        curLeft = null
        curBankL = null
        curBankR = null
        swap.reset()
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
