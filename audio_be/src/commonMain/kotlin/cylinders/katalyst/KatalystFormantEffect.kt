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
 * (one mono formant bank per channel) instead of per voice. The whole stage is one call,
 * [LowPassHighPassFilters.createFormant]: a [ResonatorBank] inside its dry/wet blend. Only the
 * orbit's owning voice configures it (see `Cylinder`'s VoiceLease), so `null` (owner has no vowel)
 * turns the resonator OFF; [reset] deactivates it on orbit teardown.
 *
 * Every edge fades, a change that arrives mid-fade waits in the ONE parking slot as the config it
 * came as, intent and sound are two things, and [reset] / [retire] stay a hard cut, all exactly as
 * [KatalystBodyEffect]'s KDoc spells out (Katalyst steps 5c-6 and 5c-11).
 *
 * NOTE: near-verbatim twin of [KatalystBodyEffect] (only the band type, the factory fn and the
 * WET/FLOOR constants differ); both build
 * a `ResonatorBank`, and the twin's KDoc says why the hosts stay two classes.
 */
class KatalystFormantEffect(
    private val sampleRate: Double,
    /** The frames of one render block; sizes the swap's scratch at construction (see [KatalystFilterSwap]). */
    blockFrames: Int = AudioBackendContext.RENDER_QUANTUM_FRAMES,
) : KatalystEffect {

    private var curBands: List<FilterDef.Formant.Band>? = null
    private var curMix: Double = Double.NaN
    private var curFloor: Double? = Double.NaN

    /** The left filter of the pair the last install built: what [KatalystFilterSwap.resume] looks for. */
    private var curLeft: AudioFilter? = null

    /** The ONE parking slot, the twin of [KatalystBodyEffect]'s and for its reason. */
    private var parked: FilterDef.Formant? = null
    private var hasParked: Boolean = false

    // Boxed once at construction, the twin of `KatalystBodyEffect.unsetFloor` and for its reason.
    private val unsetFloor: Double? = VOWEL_FLOOR

    // Holds the bank in service and, while a fade runs, the bank fading out; every edge fades.
    private val swap = KatalystFilterSwap(sampleRate, blockFrames)

    /** Test seam: the INTENT, the parked word first; the twin of [KatalystBodyEffect.isEngaged]. */
    internal val isEngaged: Boolean get() = if (hasParked) parked != null else swap.active

    /** Test seam: the SOUND, true while any bank may still be heard, a fade-out included. */
    internal val isSounding: Boolean get() = swap.sounding

    /** Test seam: whether a config is waiting for the fade in flight to land. */
    internal val isParked: Boolean get() = hasParked

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
     * A null fades the bank out and a change that arrives mid-fade is parked, both the twin of
     * [KatalystBodyEffect.configure].
     */
    fun configure(vowel: FilterDef.Formant?) {
        if (vowel == null) {
            if (!isEngaged) {
                return // already off, or already heading there: idempotent
            }

            if (swap.settled) {
                parked = null
                hasParked = false
                swap.clear() // the owner has no vowel: fade to dry, once
            } else {
                parked = null
                hasParked = true // the off waits for the fade in flight (latest wins)
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
            // The latest word is what is installed, so anything parked behind it is overtaken.
            parked = null
            hasParked = false

            if (swap.active) {
                return
            }

            // A return after a DIFFERENT change is not found here: the twin's comment says what
            // happens to it, and why it is continuous either way.
            val left = curLeft

            if (left != null && swap.resume(left)) {
                return
            }
        }

        if (!swap.settled) {
            // A fade is running and this change would need a third bank: park the DEF, latest wins.
            parked = vowel
            hasParked = true

            return
        }

        val left = LowPassHighPassFilters.createFormant(vowel.bands, mix, sampleRate, floor)

        swap.set(left, LowPassHighPassFilters.createFormant(vowel.bands, mix, sampleRate, floor))
        curLeft = left
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
        parked = null
        hasParked = false
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

    /** The block, then the parked config if the fade landed inside it; see [KatalystBodyEffect.process]. */
    override fun process(ctx: KatalystContext) {
        swap.process(ctx.mixBuffer, ctx.blockFrames)

        if (hasParked && swap.settled) {
            val waiting = parked

            parked = null
            hasParked = false
            configure(waiting)
        }
    }
}
