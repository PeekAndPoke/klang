/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.AdsrCurves
import io.peekandpoke.klang.audio_bridge.AdsrDef
import io.peekandpoke.klang.audio_bridge.IgnitorDsl

/**
 * A sample's own envelope as the DEFAULTS of the sample instrument's `adsr.*` slots (phase 3 step 7).
 *
 * A sample may carry an envelope in its metadata (a SoundFont zone carries a transparent one: attack 0,
 * decay 0, sustain 1, release 0.05; a plain wav carries none). It sits between the pattern and the voice
 * default, exactly where `AdsrDef.mergeWith` has always put it: the pattern's value wins, the sample's fills
 * what the pattern left unset, the slot's default (`VOICE_ADSR_*`) fills the rest. So every `adsr.*` slot of
 * [bag] that holds no FINITE value takes the meta value, when the meta sets one; a finite value is never
 * overwritten. A non-finite value reads as unset, the bag's rule for every number.
 *
 * Returns [bag] itself when [meta] is null or fills nothing (no copy); otherwise a copy with the fills.
 */
internal fun withSampleEnvelopeDefaults(bag: Map<String, Double>?, meta: AdsrDef?): Map<String, Double>? {
    if (meta == null) {
        return bag
    }

    val defaults = SampleEnvelopeDefaults(bag)

    when (meta) {
        is AdsrDef.Std -> {
            val s = IgnitorDsl.Slots

            defaults.fill(s.adsr.attack, meta.attack)
            defaults.fill(s.adsr.decay, meta.decay)
            defaults.fill(s.adsr.sustain, meta.sustain)
            defaults.fill(s.adsr.release, meta.release)
            defaults.fillCurve(s.adsrCurves.attack, meta.attackCurve)
            defaults.fillCurve(s.adsrCurves.decay, meta.decayCurve)
            defaults.fillCurve(s.adsrCurves.release, meta.releaseCurve)
            defaults.fill(s.adsr.on, meta.on?.let { if (it) 1.0 else 0.0 })
        }
    }

    return defaults.result()
}

/** The bag being filled: a copy of [own], made at the first fill. */
private class SampleEnvelopeDefaults(private val own: Map<String, Double>?) {
    private var out: MutableMap<String, Double>? = null

    fun fill(slot: IgnitorDsl, value: Double?) {
        if (value == null || !value.isFinite()) { // NaN-guard: a non-finite default fills nothing
            return
        }

        // Safe: every caller passes a slot of `IgnitorDsl.Slots`, each an `IgnitorDsl.Param` (`IgnitorDslClassic.kt`).
        val name = (slot as IgnitorDsl.Param).name
        val current = (out ?: own)?.get(name)

        if (current != null && current.isFinite()) {
            return
        }

        val target = out ?: (own?.toMutableMap() ?: mutableMapOf()).also { out = it }

        target[name] = value
    }

    fun fillCurve(slot: IgnitorDsl, curve: AdsrCurve?) {
        fill(slot, curve?.let { AdsrCurves.indexOf(it) })
    }

    fun result(): Map<String, Double>? = out ?: own
}
