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
import io.peekandpoke.klang.audio_bridge.constants.BODY_FLOOR
import io.peekandpoke.klang.audio_bridge.constants.BODY_WET

/**
 * Orbit-level **body resonator** — an insert effect on the summed orbit mix.
 *
 * `body(...)` used to be a per-voice filter, which meant one 8-band SVF bank per voice (× every
 * `superimpose` copy, × every unison note). Because a body is a timbre resonator — a property of the
 * instrument/orbit, not of an individual note — it now runs **once per orbit** on the mixed stereo
 * signal. The orbit is the grouping unit: voices needing independent body go on different orbits.
 *
 * The wrapped filter is what [LowPassHighPassFilters.createBody] builds: a `ParallelMixFilter`
 * around the wet-only [ResonatorBank], so the dry/wet blend is intact. It is **mono**, so we keep
 * one instance per stereo channel (independent SVF state).
 *
 * Ownership: `Cylinder.updateFromVoice` only calls [configure] for the voice that OWNS the orbit's body
 * (via [VoiceLease], first-writer-wins while alive). Because only the owner configures, `null` (the owner
 * has no body) authoritatively turns the resonator OFF: it is NOT a no-op.
 *
 * **Every edge fades** (Katalyst step 5c-6, decided with the maintainer 2026-09-19): off fades the
 * bank to dry over `BANK_CROSSFADE_SECONDS` and only then releases it, on fades in from dry, a
 * change crossfades from what sounds now, and an owner that returns to the same body mid-fade-out
 * takes the fading bank back ([KatalystFilterSwap.resume]). [reset] and [retire] stay a HARD cut:
 * the cylinder calls them when the orbit has gone silent or goes to the shelf, and a fade that
 * survived them would resume on the orbit's next life.
 *
 * **ONE PARKING SLOT, for a config and never a bank** (Katalyst step 5c-11, 2026-09-20). The swap
 * carries two banks and no more, so a change that arrives while a fade runs cannot start: it waits
 * in [parked] as the `FilterDef` it came as, a further change REPLACES it (latest wins, so a burst
 * costs one install and not one per event), and [process] offers it again on the block the fade
 * lands. Parking the DEF and not a built pair is what makes an overtaken change free: nothing was
 * allocated for it. The 10-bank pool this replaces let up to four materials smear at once on a
 * 64th-note run, which is what the maintainer heard.
 *
 * **Intent and sound are two things.** [isEngaged] is the intent (the owner's latest word was a
 * body), which is [parked]'s when a config waits and the swap's otherwise; the bank may still be
 * sounding after it turned false. The config cache (`curBands`, `curMix`, `curFloor`, `curLeft`)
 * describes the pair the last install built. It survives a fade-out on purpose, so a returning
 * owner can take the fading bank back, and it is never trusted on its own: an unchanged config with
 * the intent off asks the swap whether that pair still sounds, and installs a fresh one when it
 * does not (question 2 of `docs/plans/effect-state-machines.md`).
 *
 * NOTE: near-verbatim twin of [KatalystFormantEffect] (only the band type, the factory fn and the
 * WET/FLOOR constants differ). The DSP is already one: both build a `ResonatorBank` (Katalyst step
 * 5c-3, 2026-09-19). The hosts stay two classes on purpose: `FilterDef.Body` and `FilterDef.Formant`
 * share no supertype that carries bands, mix and floor (only the sealed `FilterDef`), their band
 * types share nothing, the typed
 * accessors `KatalystChain.body` / `.vowel` find each stage by its class, and the test seams are
 * typed per band kind, so one class would need a generic band type, a factory parameter and a kind
 * marker to save the few dozen lines of lifecycle the twins repeat.
 */
class KatalystBodyEffect(
    private val sampleRate: Double,
    /** The frames of one render block; sizes the swap's scratch at construction (see [KatalystFilterSwap]). */
    blockFrames: Int = AudioBackendContext.RENDER_QUANTUM_FRAMES,
) : KatalystEffect {

    private var curBands: List<FilterDef.Body.Mode>? = null
    private var curMix: Double = Double.NaN
    private var curFloor: Double? = Double.NaN

    /** The left filter of the pair the last install built: what [KatalystFilterSwap.resume] looks for. */
    private var curLeft: AudioFilter? = null

    /**
     * The ONE parking slot: the config of a change that arrived while a fade was running, or `null`
     * for a parked OFF. [hasParked] is what tells those two apart, so the slot holds a real word
     * and not an absence (see the class KDoc).
     */
    private var parked: FilterDef.Body? = null
    private var hasParked: Boolean = false

    // The substitute for a non-finite floor, boxed ONCE at construction: the `Double?` the
    // comparison and the factory both take would otherwise box the constant on every block that
    // carries an unset floor, which is exactly the case the guard in `configure` is there for.
    private val unsetFloor: Double? = BODY_FLOOR

    // Holds the bank in service and, while a fade runs, the bank fading out; every edge fades.
    private val swap = KatalystFilterSwap(sampleRate, blockFrames)

    /**
     * Test seam: the INTENT, true while the owner asks for a body (a bank may still fade out after).
     * A parked config is the LATER word, so it answers first.
     */
    internal val isEngaged: Boolean get() = if (hasParked) parked != null else swap.active

    /** Test seam: the SOUND, true while any bank may still be heard, a fade-out included. */
    internal val isSounding: Boolean get() = swap.sounding

    /** Test seam: whether a config is waiting for the fade in flight to land. */
    internal val isParked: Boolean get() = hasParked

    /**
     * Test seams: WHAT is installed, not just whether anything is ([isEngaged]).
     *
     * On a declared chain all three arrive as slots, the material as an INDEX into a shared
     * catalogue, so a wrong lookup or a knob wired to the wrong argument installs a real bank of
     * the wrong box and reads as "engaged" either way. Nothing else can see which one it is.
     *
     * They read the values the bank was BUILT from, so an unset knob reads as its constant and
     * never as the non-finite marker that arrived, and a PARKED change is not in them yet.
     */
    internal val installedBands: List<FilterDef.Body.Mode>? get() = curBands

    internal val installedMix: Double get() = curMix

    internal val installedFloor: Double? get() = curFloor

    /**
     * Configure from the OWNER voice's body, or from a declared chain's slots. `null` (nobody asks
     * for a body) turns the resonator off.
     *
     * A non-finite `mix` or `floor` is UNSET and takes [BODY_WET] / [BODY_FLOOR], the rule
     * `KatalystSlots.bodyDef` applies to a declared chain's slots, applied here so the BORN-WITH
     * chain answers the same. The voice path can carry one: `body(material = "wood", wet = "NaN")` parses to a
     * NaN and `SprudelVoiceData.toVoiceData` only guards a null.
     *
     * A null fades the bank out (see the class KDoc); a second null while it fades is free. Closed
     * here: the open question of 2026-09-18 (off was a hard cut while every change crossfaded).
     *
     * While a fade runs the word is PARKED instead of acted on, except for the one word that needs
     * no new bank: a return to the config that is fading out, which turns that fade around.
     */
    fun configure(body: FilterDef.Body?) {
        if (body == null) {
            if (!isEngaged) {
                return // already off, or already heading there: idempotent
            }

            if (swap.settled) {
                parked = null
                hasParked = false
                swap.clear() // the owner has no body: fade to dry, once
            } else {
                parked = null
                hasParked = true // the off waits for the fade in flight (latest wins)
            }

            return
        }

        // **No production caller can hand this a non-finite value any more** (Katalyst step 5b-1,
        // 2026-09-19): the one caller is `KatalystSlots.bodyDef`, which substitutes BODY_WET and the
        // matching floor one layer up, on every chain. The guard stays as this stage's contract for
        // a DIRECT caller, which is what the effect specs are, and because the failure it prevents
        // is a per-block allocation on the audio thread rather than a wrong number.
        // NaN-guards, and they sit BEFORE the comparison on purpose: a NaN is never equal to
        // itself, so an unguarded non-finite mix made the test below true on EVERY block and
        // rebuilt two filter banks per block on the audio thread, restarting a crossfade
        // that then never completed. Being NULLABLE does not save the floor: a `Double?` pair of
        // NaNs answers "not equal" on both targets we ship, measured 2026-09-18 (JVM, and
        // Kotlin/JS in Chrome headless). The guard does not rest on that measurement, it removes
        // the question: a substituted value is finite, so the comparison settles whatever a
        // runtime makes of NaN.
        // Substituted, compared and stored, so the value the filters are built from is the value
        // the next block compares against. A null floor stays null: `createBody` reads it as
        // BODY_FLOOR, the same filter written the other way round.
        val mix = if (body.mix.isFinite()) body.mix else BODY_WET
        val rawFloor = body.floor
        val floor = if (rawFloor == null || rawFloor.isFinite()) rawFloor else unsetFloor

        // Rebuild only when the material/mix/floor actually changes: with ownership this is once per
        // owner change (a live owner re-offers the same config every block, which short-circuits here).
        val unchanged = body.bands == curBands && mix == curMix && floor == curFloor

        if (unchanged) {
            // The owner's latest word is the config already installed, so anything parked behind it
            // is overtaken (latest wins) whether or not a fade is still running.
            parked = null
            hasParked = false

            if (swap.active) {
                return
            }

            // The owner is back with the body it had: take the fading bank back where it stands.
            // Only while it still sounds; once it has faded out the cache describes a released
            // bank, and the identical body installs afresh below. A return after a DIFFERENT
            // change (A, then B, then A again within one fade) is not found here, because the
            // cache holds B: a fresh A then fades in while the warm A fades out, both continuous.
            // Searching the outgoing bank by config is complexity the safety net does not need
            // (decided in the 5c-6 review).
            val left = curLeft

            if (left != null && swap.resume(left)) {
                return
            }
        }

        if (!swap.settled) {
            // A fade is running and this change would need a third bank: park the DEF, latest wins.
            parked = body
            hasParked = true

            return
        }

        val left = LowPassHighPassFilters.createBody(body.bands, mix, sampleRate, floor)

        swap.set(left, LowPassHighPassFilters.createBody(body.bands, mix, sampleRate, floor))
        curLeft = left
        curBands = body.bands
        curMix = mix
        curFloor = floor
    }

    /** A HARD cut (orbit deactivation, chain swap, the shelf): the bank goes at once, the cache with it. */
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
     * False, and NOT because this stage is stateless: the SVF integrators and the swap's
     * crossfade do hold state. It is because everything they hold lands in `ctx.mixBuffer`, and
     * `Cylinder.isMixBufferSilent()` scans that buffer AFTER the chain has run, so the
     * deactivation gate already sees this stage's residue without having to ask it. A future stage
     * whose output does NOT reach the mix before that scan (a send-return, a chorus ring) must
     * answer true while it rings, as the delay and the reverb do.
     */
    override fun hasTail(): Boolean = false

    /** Rents nothing, so retiring is the clean slate. */
    override fun retire() {
        reset()
    }

    /**
     * The block, and then the parked config if the fade landed inside it.
     *
     * AFTER the swap and not before: the fade lands at the end of a block, so offering here starts
     * the parked change on the very next block instead of one later. It runs from `process` and not
     * from the next [configure] because a chain whose owner has died is still processed and no
     * longer configured, and a change parked at that moment would otherwise never arrive.
     */
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
