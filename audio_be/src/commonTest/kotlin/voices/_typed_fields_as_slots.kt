/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.peekandpoke.klang.audio_be.ignitor.IgnitorRegistry
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.AdsrCurves
import io.peekandpoke.klang.audio_bridge.AdsrDef
import io.peekandpoke.klang.audio_bridge.DistortionShapes
import io.peekandpoke.klang.audio_bridge.FilterCurvesSlots
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.audio_bridge.FilterDefs
import io.peekandpoke.klang.audio_bridge.FilterEnvDef
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.LfoShapes
import io.peekandpoke.klang.audio_bridge.VoiceData

/**
 * TEST ONLY. This voice with its typed voice-door fields MOVED into the slot bag, the form a producer sends since
 * phase 3 step 8 (sprudel's `toVoiceData` writes the slot keys, `classicSlotParams`; the engine reads only those).
 * A spec that states its settings as typed fields renders a built-in, a sample or an authored `classic()`
 * instrument through this; the strip side of an old-path/new-path fixture keeps the typed fields, which only the
 * voice strip reads, until step 9 retires both.
 *
 * The same rules as sprudel's translation: only a set and finite field is written; the typed value wins over a
 * bag entry of the same key; a filter kind writes its `freq`, `q` and (low/high) `passes`, and its envelope keys
 * only when it has one; names travel as their catalogue index, flags as 1.0 or 0.0. The moved fields are nulled.
 */
fun VoiceData.withClassicSlots(): VoiceData {
    val out = oscParams?.toMutableMap() ?: mutableMapOf()
    val s = IgnitorDsl.Slots

    fun put(slot: IgnitorDsl, value: Double?) {
        if (value != null && value.isFinite()) {
            out[(slot as IgnitorDsl.Param).name] = value
        }
    }

    fun putCurve(slot: IgnitorDsl, curve: AdsrCurve?) = put(slot, curve?.let { AdsrCurves.indexOf(it) })

    fun putEnv(env: FilterEnvDef?, depth: IgnitorDsl, a: IgnitorDsl, d: IgnitorDsl, su: IgnitorDsl, r: IgnitorDsl, c: FilterCurvesSlots) {
        if (env == null) {
            return
        }

        put(depth, env.depth)
        put(a, env.attack)
        put(d, env.decay)
        put(su, env.sustain)
        put(r, env.release)
        putCurve(c.attack, env.attackCurve)
        putCurve(c.decay, env.decayCurve)
        putCurve(c.release, env.releaseCurve)
    }

    put(s.crush.amount, crush)
    put(s.coarse.amount, coarse)
    put(s.distort.amount, distort)
    put(s.distort.shape, distortShape?.let { DistortionShapes.indexOf(it) })
    put(s.distort.oversample, distortOversample?.toDouble())

    for (def in filters.filters) {
        when (def) {
            is FilterDef.HighPass -> {
                put(s.hpf.freq, def.freq)
                put(s.hpf.q, def.q)
                put(s.hpf.passes, def.passes.toDouble())
                putEnv(def.envelope, s.hpf.env, s.hpf.attack, s.hpf.decay, s.hpf.sustain, s.hpf.release, s.hpfCurves)
            }

            is FilterDef.BandPass -> {
                put(s.bpf.freq, def.freq)
                put(s.bpf.q, def.q)
                putEnv(def.envelope, s.bpf.env, s.bpf.attack, s.bpf.decay, s.bpf.sustain, s.bpf.release, s.bpfCurves)
            }

            is FilterDef.Notch -> {
                put(s.notch.freq, def.freq)
                put(s.notch.q, def.q)
                putEnv(def.envelope, s.notch.env, s.notch.attack, s.notch.decay, s.notch.sustain, s.notch.release, s.notchCurves)
            }

            is FilterDef.LowPass -> {
                put(s.lpf.freq, def.freq)
                put(s.lpf.q, def.q)
                put(s.lpf.passes, def.passes.toDouble())
                putEnv(def.envelope, s.lpf.env, s.lpf.attack, s.lpf.decay, s.lpf.sustain, s.lpf.release, s.lpfCurves)
            }

            is FilterDef.Formant, is FilterDef.Body -> Unit
        }
    }

    put(s.tremolo.depth, tremoloDepth)
    put(s.tremolo.sync, tremoloSync)
    put(s.tremolo.shape, tremoloShape?.let { LfoShapes.indexOf(it) })
    put(s.tremolo.skew, tremoloSkew)
    put(s.tremolo.phase, tremoloPhase)

    when (val adsr = adsr) {
        is AdsrDef.Std -> {
            put(s.adsr.attack, adsr.attack)
            put(s.adsr.decay, adsr.decay)
            put(s.adsr.sustain, adsr.sustain)
            put(s.adsr.release, adsr.release)
            putCurve(s.adsrCurves.attack, adsr.attackCurve)
            putCurve(s.adsrCurves.decay, adsr.decayCurve)
            putCurve(s.adsrCurves.release, adsr.releaseCurve)
            put(s.adsr.on, adsr.on?.let { if (it) 1.0 else 0.0 })
        }
    }

    put(s.sample.begin, begin)
    put(s.sample.end, end)
    put(s.sample.speed, speed)
    put(s.sample.loop, loop?.let { if (it) 1.0 else 0.0 })

    return copy(
        oscParams = out.takeIf { it.isNotEmpty() },
        filters = FilterDefs(filters.filters.filter { it is FilterDef.Formant || it is FilterDef.Body }),
        adsr = AdsrDef.empty,
        crush = null,
        crushOversample = null,
        coarse = null,
        coarseOversample = null,
        distort = null,
        distortShape = null,
        distortOversample = null,
        tremoloDepth = null,
        tremoloSync = null,
        tremoloShape = null,
        tremoloSkew = null,
        tremoloPhase = null,
        begin = null,
        end = null,
        speed = null,
        loop = null,
    )
}

/**
 * TEST ONLY. This voice as a producer sends it to the path [registry] picks for its sound: a TREE voice (a
 * built-in, an authored instrument that ends in `classic()`, a sample) reads its settings as slots
 * ([withClassicSlots]); a voice on the strip (an authored instrument without `classic()`) keeps the typed
 * fields, the old path's oracle in the strip-against-tree fixtures, until step 9 retires the strip.
 */
fun VoiceData.forPath(registry: IgnitorRegistry): VoiceData =
    if (registry.contains(sound) && !registry.endsInClassic(sound)) this else withClassicSlots()
