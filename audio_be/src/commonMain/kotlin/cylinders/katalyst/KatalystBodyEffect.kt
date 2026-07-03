/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import io.peekandpoke.klang.audio_be.filters.AudioFilter
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
 * Config is routed from a voice via [configure] in `Cylinder.updateFromVoice`. A voice WITHOUT body
 * passes `null` and must NOT switch the orbit's body off — hence `null` is a no-op. The cylinder calls
 * [reset] when it fully deactivates so a reused orbit reconfigures cleanly.
 *
 * NOTE: near-verbatim twin of [KatalystFormantEffect] (only the band type + factory fn differ). Left
 * un-deduped on purpose — both will fold into a single generic resonator once the Katalyst DSL lands.
 */
class KatalystBodyEffect(
    private val sampleRate: Double,
) : KatalystEffect {

    // `left`/`right` non-null == active; no separate flag needed.
    private var curBands: List<FilterDef.Body.Mode>? = null
    private var curMix: Double = Double.NaN
    private var left: AudioFilter? = null
    private var right: AudioFilter? = null

    /** Configure from a voice's body. `null` leaves the current config untouched (see class kdoc). */
    fun configure(body: FilterDef.Body?) {
        if (body == null) return
        // Rebuild only when the material/mix actually changes — effectively once per orbit, since
        // body.bands/mix are query-time constants (no per-block modulation).
        if (body.bands != curBands || body.mix != curMix) {
            left = LowPassHighPassFilters.createBody(body.bands, body.mix, sampleRate)
            right = LowPassHighPassFilters.createBody(body.bands, body.mix, sampleRate)
            curBands = body.bands
            curMix = body.mix
        }
    }

    fun reset() {
        curBands = null
        curMix = Double.NaN
        left = null
        right = null
    }

    override fun process(ctx: KatalystContext) {
        val l = left ?: return
        val r = right ?: return
        l.process(ctx.mixBuffer.left, 0, ctx.blockFrames)
        r.process(ctx.mixBuffer.right, 0, ctx.blockFrames)
    }
}
