/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.voices

import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.AdsrCurves
import io.peekandpoke.klang.audio_bridge.DistortionShapes
import io.peekandpoke.klang.audio_bridge.FilterCurvesSlots
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.LfoShapes
import io.peekandpoke.klang.audio_bridge.VoiceData

/**
 * TEST ONLY. The voice-door settings a spec states in typed form: the shape `VoiceData` carried as typed fields until
 * phase 3 step 9 cut them (a voice door travels as `classic()` slot keys in `oscParams` since step 8). Specs that
 * read more clearly as "a lowpass at 900 with an envelope" than as a list of slot keys state their settings here and
 * send them through [withClassicSlots], which writes the same key names, units and on/off rules as sprudel's
 * `classicSlotParams`. Three differences are deliberate and inaudible: the rig always writes `passes` (default 1.0)
 * where sprudel writes it only when set, it writes the filter envelope curves whenever a [DoorFilterEnv] is present
 * (curves alone never trigger the depth fill), and it omits the `crush.oversample` / `coarse.oversample` keys, which
 * nothing reads.
 */
data class DoorFields(
    val filters: List<DoorFilter> = emptyList(),
    val adsr: DoorAdsr? = null,
    val crush: Double? = null,
    val coarse: Double? = null,
    val distort: Double? = null,
    val distortShape: String? = null,
    val distortOversample: Int? = null,
    val tremoloDepth: Double? = null,
    val tremoloSync: Double? = null,
    val tremoloShape: String? = null,
    val begin: Double? = null,
    val end: Double? = null,
    val speed: Double? = null,
    val loop: Boolean? = null,
)

/** TEST ONLY. The voice envelope of a [DoorFields]: the four stages, their curves, and the `adsr.on` switch. */
data class DoorAdsr(
    val attack: Double? = null,
    val decay: Double? = null,
    val sustain: Double? = null,
    val release: Double? = null,
    val attackCurve: AdsrCurve? = null,
    val decayCurve: AdsrCurve? = null,
    val releaseCurve: AdsrCurve? = null,
    val on: Boolean? = null,
)

/** TEST ONLY. A filter's cutoff envelope in a [DoorFilter]: the depth in semitones, the four stages, the curves. */
data class DoorFilterEnv(
    val attack: Double? = null,
    val decay: Double? = null,
    val sustain: Double? = null,
    val release: Double? = null,
    val depth: Double? = null,
    val attackCurve: AdsrCurve? = null,
    val decayCurve: AdsrCurve? = null,
    val releaseCurve: AdsrCurve? = null,
)

/** TEST ONLY. One of `classic()`'s four voice filters, stated in typed form. */
sealed interface DoorFilter {
    data class LowPass(val freq: Double, val q: Double?, val envelope: DoorFilterEnv? = null, val passes: Int = 1) : DoorFilter
    data class HighPass(val freq: Double, val q: Double?, val envelope: DoorFilterEnv? = null, val passes: Int = 1) : DoorFilter
    data class BandPass(val freq: Double, val q: Double?, val envelope: DoorFilterEnv? = null) : DoorFilter
    data class Notch(val freq: Double, val q: Double?, val envelope: DoorFilterEnv? = null) : DoorFilter
}

/**
 * TEST ONLY. This voice with [doors] written into its slot bag as `classic()` slot keys. Only a set and finite value
 * is written; a door value wins over a bag entry of the same key (so set the voice's own `oscParams` BEFORE this
 * call); a filter writes its `freq`, `q` and (low/high) `passes`, and its envelope keys only when it has one; names
 * travel as their catalogue index, flags as 1.0 or 0.0.
 */
fun VoiceData.withClassicSlots(doors: DoorFields): VoiceData {
    val out = oscParams?.toMutableMap() ?: mutableMapOf()
    val s = IgnitorDsl.Slots

    fun put(slot: IgnitorDsl, value: Double?) {
        if (value != null && value.isFinite()) {
            out[(slot as IgnitorDsl.Param).name] = value
        }
    }

    fun putCurve(slot: IgnitorDsl, curve: AdsrCurve?) = put(slot, curve?.let { AdsrCurves.indexOf(it) })

    fun putEnv(env: DoorFilterEnv?, depth: IgnitorDsl, a: IgnitorDsl, d: IgnitorDsl, su: IgnitorDsl, r: IgnitorDsl, c: FilterCurvesSlots) {
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

    put(s.crush.amount, doors.crush)
    put(s.coarse.amount, doors.coarse)
    put(s.distort.amount, doors.distort)
    put(s.distort.shape, doors.distortShape?.let { DistortionShapes.indexOf(it) })
    put(s.distort.oversample, doors.distortOversample?.toDouble())

    for (def in doors.filters) {
        when (def) {
            is DoorFilter.HighPass -> {
                put(s.hpf.freq, def.freq)
                put(s.hpf.q, def.q)
                put(s.hpf.passes, def.passes.toDouble())
                putEnv(def.envelope, s.hpf.env, s.hpf.attack, s.hpf.decay, s.hpf.sustain, s.hpf.release, s.hpfCurves)
            }

            is DoorFilter.BandPass -> {
                put(s.bpf.freq, def.freq)
                put(s.bpf.q, def.q)
                putEnv(def.envelope, s.bpf.env, s.bpf.attack, s.bpf.decay, s.bpf.sustain, s.bpf.release, s.bpfCurves)
            }

            is DoorFilter.Notch -> {
                put(s.notch.freq, def.freq)
                put(s.notch.q, def.q)
                putEnv(def.envelope, s.notch.env, s.notch.attack, s.notch.decay, s.notch.sustain, s.notch.release, s.notchCurves)
            }

            is DoorFilter.LowPass -> {
                put(s.lpf.freq, def.freq)
                put(s.lpf.q, def.q)
                put(s.lpf.passes, def.passes.toDouble())
                putEnv(def.envelope, s.lpf.env, s.lpf.attack, s.lpf.decay, s.lpf.sustain, s.lpf.release, s.lpfCurves)
            }
        }
    }

    put(s.tremolo.depth, doors.tremoloDepth)
    put(s.tremolo.sync, doors.tremoloSync)
    put(s.tremolo.shape, doors.tremoloShape?.let { LfoShapes.indexOf(it) })

    doors.adsr?.let { adsr ->
        put(s.adsr.attack, adsr.attack)
        put(s.adsr.decay, adsr.decay)
        put(s.adsr.sustain, adsr.sustain)
        put(s.adsr.release, adsr.release)
        putCurve(s.adsrCurves.attack, adsr.attackCurve)
        putCurve(s.adsrCurves.decay, adsr.decayCurve)
        putCurve(s.adsrCurves.release, adsr.releaseCurve)
        put(s.adsr.on, adsr.on?.let { if (it) 1.0 else 0.0 })
    }

    put(s.sample.begin, doors.begin)
    put(s.sample.end, doors.end)
    put(s.sample.speed, doors.speed)
    put(s.sample.loop, doors.loop?.let { if (it) 1.0 else 0.0 })

    return copy(oscParams = out.takeIf { it.isNotEmpty() })
}
