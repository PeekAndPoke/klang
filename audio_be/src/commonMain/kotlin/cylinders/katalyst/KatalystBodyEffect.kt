/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.filters.LowPassHighPassFilters
import io.peekandpoke.klang.audio_bridge.FilterDef

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

    // Holds the current (+ briefly the previous) stereo bank; crossfades on swap to declick live changes.
    private val swap = KatalystFilterSwap(sampleRate)

    /** Configure from the OWNER voice's body. `null` (owner has no body) turns the resonator off. */
    fun configure(body: FilterDef.Body?) {
        if (body == null) {
            if (swap.active) reset() // owner has no body → turn off, once
            return
        }
        // Rebuild only when the material/mix/floor actually changes — with ownership this is once per
        // owner change (a live owner re-offers the same config every block, which short-circuits here).
        // The swap crossfades from the old bank so the change doesn't click.
        if (body.bands != curBands || body.mix != curMix || body.floor != curFloor) {
            swap.set(
                LowPassHighPassFilters.createBody(body.bands, body.mix, sampleRate, body.floor),
                LowPassHighPassFilters.createBody(body.bands, body.mix, sampleRate, body.floor),
            )
            curBands = body.bands
            curMix = body.mix
            curFloor = body.floor
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
