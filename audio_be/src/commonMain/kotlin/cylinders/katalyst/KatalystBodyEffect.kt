/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.filters.LowPassHighPassFilters
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
 * The wrapped filter is the same [LowPassHighPassFilters.createBody] (a `ParallelMixFilter` around a
 * wet-only `BodyFilter`, so the dry/wet blend is intact). It is **mono**, so we keep one instance per
 * stereo channel (independent SVF state).
 *
 * Ownership: `Cylinder.updateFromVoice` only calls [configure] for the voice that OWNS the orbit's body
 * (via [VoiceLease] — first-writer-wins while alive). Because only the owner configures, `null` (the owner
 * has no body) authoritatively turns the resonator OFF — it is NOT a no-op. The cylinder calls [reset] when
 * it fully deactivates so a reused orbit reconfigures cleanly.
 *
 * NOTE: near-verbatim twin of [KatalystFormantEffect] (only the band type + factory fn differ). Left
 * un-deduped on purpose — both will fold into a single generic resonator once the Katalyst DSL lands.
 */
class KatalystBodyEffect(
    private val sampleRate: Double,
) : KatalystEffect {

    private var curBands: List<FilterDef.Body.Mode>? = null
    private var curMix: Double = Double.NaN
    private var curFloor: Double? = Double.NaN

    // The substitute for a non-finite floor, boxed ONCE at construction: the `Double?` the
    // comparison and the factory both take would otherwise box the constant on every block that
    // carries an unset floor, which is exactly the case the guard in `configure` is there for.
    private val unsetFloor: Double? = BODY_FLOOR

    // Holds the current (+ briefly the previous) stereo bank; crossfades on swap to declick live changes.
    private val swap = KatalystFilterSwap(sampleRate)

    /** Test seam: true while a resonator bank is installed — the owner has a body. */
    internal val isEngaged: Boolean get() = swap.active

    /**
     * Test seams: WHAT is installed, not just whether anything is ([isEngaged]).
     *
     * On a declared chain all three arrive as slots, the material as an INDEX into a shared
     * catalogue, so a wrong lookup or a knob wired to the wrong argument installs a real bank of
     * the wrong box and reads as "engaged" either way. Nothing else can see which one it is.
     *
     * They read the values the bank was BUILT from, so an unset knob reads as its constant and
     * never as the non-finite marker that arrived.
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
     * chain answers the same. The voice path can carry one: `body("wood", wet = "NaN")` parses to a
     * NaN and `SprudelVoiceData.toVoiceData` only guards a null.
     *
     * **Open question, recorded 2026-09-18 (round 1 of Katalyst step 5a-2), not a regression:**
     * turning the stage OFF is a hard CUT ([reset] clears the swap), while every material, mix or
     * floor CHANGE crossfades. The asymmetry pre-dates the Katalyst DSL and sits at the same moment
     * an owner handover already hits on the voice path, so nothing got worse; it is simply visible
     * now that a `.katp` can switch a declared stage off mid-phrase. The shape it wants is a
     * `KatalystFilterSwap.fadeOut()` (ramp the installed bank's wet to zero over the same 12 ms and
     * clear when the ramp ends) so that off is as declick as every other change; the maintainer
     * records it in `docs/tasks/katalyst-dsl.md`.
     */
    fun configure(body: FilterDef.Body?) {
        if (body == null) {
            if (swap.active) reset() // owner has no body → turn off, once
            return
        }

        // **No production caller can hand this a non-finite value any more** (Katalyst step 5b-1,
        // 2026-09-19): the one caller is `KatalystSlots.bodyDef`, which substitutes BODY_WET and the
        // matching floor one layer up, on every chain. The guard stays as this stage's contract for
        // a DIRECT caller, which is what the effect specs are, and because the failure it prevents
        // is a per-block allocation on the audio thread rather than a wrong number.
        // NaN-guards, and they sit BEFORE the comparison on purpose: a NaN is never equal to
        // itself, so an unguarded non-finite mix made the test below true on EVERY block and
        // rebuilt two filter banks per block on the audio thread, restarting a 12 ms crossfade
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

        // Rebuild only when the material/mix/floor actually changes — with ownership this is once per
        // owner change (a live owner re-offers the same config every block, which short-circuits here).
        // The swap crossfades from the old bank so the change doesn't click.
        if (body.bands != curBands || mix != curMix || floor != curFloor) {
            swap.set(
                LowPassHighPassFilters.createBody(body.bands, mix, sampleRate, floor),
                LowPassHighPassFilters.createBody(body.bands, mix, sampleRate, floor),
            )
            curBands = body.bands
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

    override fun process(ctx: KatalystContext) {
        swap.process(ctx.mixBuffer, ctx.blockFrames)
    }
}
