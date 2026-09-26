/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.AdsrCurves
import io.peekandpoke.klang.audio_bridge.AdsrDef
import io.peekandpoke.klang.audio_bridge.DistortionShapes
import io.peekandpoke.klang.audio_bridge.FilterCurvesSlots
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.audio_bridge.FilterEnvDef
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.LfoShapes
import io.peekandpoke.klang.audio_bridge.VoiceData

// ═════════════════════════════════════════════════════════════════════════════════════════════
// The typed voice fields as `classic()` slots (phase 3 step 6)
//
// Scaffolding: removed in step 8 when the sprudel doors write the slot keys.
//
// A built-in sound is a source in `classic()` since step 6 (its one home is `IgnitorRegistry.registerBuiltIn`),
// and its voice strip is off, so every stage the strip used to run from a typed `VoiceData` field now reads
// a `classic()` slot. The sprudel doors still write the typed fields until step 8 turns them into `oscp`
// aliases of these very keys; until then this file is the bridge, and deleting it is the step 8 change.
// ═════════════════════════════════════════════════════════════════════════════════════════════

/**
 * The bag a BUILT-IN voice is built from: the voice's own `oscParams` plus every typed field the voice
 * strip would have read, written under its `classic()` slot (`<door>.<param>`, the names read from the
 * `Param` objects of [IgnitorDsl.Slots], never retyped).
 *
 * The rules:
 *  - only a field that is SET and FINITE is written; an unset one stays unset, so the slot's default
 *    and the filter envelope's slot-layer depth fill decide as the strip's own defaults do
 *    (`FilterEnvDef.resolve`: `depth ?: 7` once a stage is set). A non-finite field reads as unset,
 *    the bag's rule for every number. One consequence differs from the strip on purpose: a filter
 *    envelope whose only set stage is non-finite switches no envelope on here, where the strip saw a
 *    non-null field and built one at depth 7;
 *  - a typed field WINS over a raw `oscp` of the same slot on the same event (the door wins until
 *    step 8, which makes it pattern order);
 *  - one slot per filter kind in `classic()`'s fixed order. Sprudel sends at most one of each, in that
 *    order (`SprudelVoiceData.toVoiceData`); a second filter of one kind from another producer MERGES
 *    into the same slots per field: each field it sets overwrites the earlier filter's, each it leaves
 *    unset keeps the earlier value. Body and vowel are orbit stages, not voice filters;
 *  - `crushOversample` and `coarseOversample` have no slot: `classic()`'s crush and coarse have no
 *    oversampler (the knob moved to `docs/tasks/oversampling-regions.md`).
 *
 * No map copy is made for a voice that sets none of these fields: its own bag is returned as it is
 * (only the small writer object is created).
 */
internal fun classicSlotBag(data: VoiceData): Map<String, Double>? {
    val bag = ClassicSlotBag(data.oscParams)
    val s = IgnitorDsl.Slots

    bag.put(s.crush.amount, data.crush)
    bag.put(s.coarse.amount, data.coarse)

    bag.put(s.distort.amount, data.distort)
    bag.put(s.distort.shape, data.distortShape?.let { DistortionShapes.indexOf(it) })
    bag.put(s.distort.oversample, data.distortOversample?.toDouble())

    for (def in data.filters.filters) {
        when (def) {
            is FilterDef.HighPass -> {
                bag.put(s.hpf.freq, def.freq)
                bag.put(s.hpf.q, def.q)
                bag.put(s.hpf.passes, def.passes.toDouble())
                bag.putEnvelope(def.envelope, s.hpf.env, s.hpf.attack, s.hpf.decay, s.hpf.sustain, s.hpf.release, s.hpfCurves)
            }

            is FilterDef.BandPass -> {
                bag.put(s.bpf.freq, def.freq)
                bag.put(s.bpf.q, def.q)
                bag.putEnvelope(def.envelope, s.bpf.env, s.bpf.attack, s.bpf.decay, s.bpf.sustain, s.bpf.release, s.bpfCurves)
            }

            is FilterDef.Notch -> {
                bag.put(s.notch.freq, def.freq)
                bag.put(s.notch.q, def.q)
                bag.putEnvelope(
                    def.envelope, s.notch.env, s.notch.attack, s.notch.decay, s.notch.sustain, s.notch.release, s.notchCurves,
                )
            }

            is FilterDef.LowPass -> {
                bag.put(s.lpf.freq, def.freq)
                bag.put(s.lpf.q, def.q)
                bag.put(s.lpf.passes, def.passes.toDouble())
                bag.putEnvelope(def.envelope, s.lpf.env, s.lpf.attack, s.lpf.decay, s.lpf.sustain, s.lpf.release, s.lpfCurves)
            }

            // Orbit-level Katalyst stages: the factory drops them from every voice.
            is FilterDef.Formant, is FilterDef.Body -> Unit
        }
    }

    bag.put(s.tremolo.depth, data.tremoloDepth)
    bag.put(s.tremolo.sync, data.tremoloSync)
    bag.put(s.tremolo.shape, data.tremoloShape?.let { LfoShapes.indexOf(it) })
    bag.put(s.tremolo.skew, data.tremoloSkew)
    bag.put(s.tremolo.phase, data.tremoloPhase)

    when (val adsr = data.adsr) {
        is AdsrDef.Std -> {
            bag.put(s.adsr.attack, adsr.attack)
            bag.put(s.adsr.decay, adsr.decay)
            bag.put(s.adsr.sustain, adsr.sustain)
            bag.put(s.adsr.release, adsr.release)
            bag.putCurve(s.adsrCurves.attack, adsr.attackCurve)
            bag.putCurve(s.adsrCurves.decay, adsr.decayCurve)
            bag.putCurve(s.adsrCurves.release, adsr.releaseCurve)
            bag.put(s.adsr.on, adsr.on?.let { if (it) 1.0 else 0.0 })
        }
    }

    return bag.result()
}

/** The bag being written: a copy of the voice's own bag, made at the first field that is set. */
private class ClassicSlotBag(private val own: Map<String, Double>?) {
    private var out: MutableMap<String, Double>? = null

    fun put(slot: IgnitorDsl, value: Double?) {
        if (value == null || !value.isFinite()) { // NaN-guard: a non-finite field reads as unset
            return
        }

        val target = out ?: (own?.toMutableMap() ?: mutableMapOf()).also { out = it }

        // Safe: every caller passes a slot of `IgnitorDsl.Slots`, and each of those is built by `slot()` in
        // `IgnitorDslClassic.kt`, which returns an `IgnitorDsl.Param`.
        target[(slot as IgnitorDsl.Param).name] = value
    }

    fun putCurve(slot: IgnitorDsl, curve: AdsrCurve?) {
        put(slot, curve?.let { AdsrCurves.indexOf(it) })
    }

    fun putEnvelope(
        env: FilterEnvDef?,
        depth: IgnitorDsl,
        attack: IgnitorDsl,
        decay: IgnitorDsl,
        sustain: IgnitorDsl,
        release: IgnitorDsl,
        curves: FilterCurvesSlots,
    ) {
        if (env == null) {
            return
        }

        put(depth, env.depth)
        put(attack, env.attack)
        put(decay, env.decay)
        put(sustain, env.sustain)
        put(release, env.release)
        putCurve(curves.attack, env.attackCurve)
        putCurve(curves.decay, env.decayCurve)
        putCurve(curves.release, env.releaseCurve)
    }

    fun result(): Map<String, Double>? = out ?: own
}
