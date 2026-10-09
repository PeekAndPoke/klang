/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel

import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.AdsrCurves
import io.peekandpoke.klang.audio_bridge.BandFilterSlots
import io.peekandpoke.klang.audio_bridge.DistortionShapes
import io.peekandpoke.klang.audio_bridge.ModEnvelopeCurvesSlots
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.LfoShapes
import io.peekandpoke.klang.audio_bridge.PassFilterSlots
import io.peekandpoke.klang.audio_bridge.coercePasses

// ═════════════════════════════════════════════════════════════════════════════════════════════
// Sprudel's voice doors as slots on the wire (phase 3 step 8)
//
// The sprudel voice doors keep their typed, grouped fields (the query path's hot loop, signal-flow plan
// section 4). At the wire boundary those fields become the slot keys the instruments read: `classic()`'s
// `<door>.<param>` slots and the sample instrument's playback slots. This is the ONE place sprudel's words
// meet the engine's slot names, so the backend keeps no sprudel-specific mapping. It was the backend's
// `classicSlotBag` until step 8 moved it here, with its rules.
// ═════════════════════════════════════════════════════════════════════════════════════════════

/**
 * The key the wire carries for `crush(oversample = ...)`. `classic()`'s crush has no oversampler and places no
 * such slot, so nothing reads it (the knob waits for `docs/tasks/oversampling-regions.md`); it travels so the
 * value is not lost on the way.
 */
internal const val CRUSH_OVERSAMPLE_KEY = "crush.oversample"

/** The key for `coarse(oversample = ...)`; unread, like [CRUSH_OVERSAMPLE_KEY]. */
internal const val COARSE_OVERSAMPLE_KEY = "coarse.oversample"

/**
 * The wire's slot bag of this event: the event's own `ignitorParams` plus every typed voice door written under
 * its slot (`<door>.<param>`, the names read from the `Param` objects of [IgnitorDsl.Slots], never retyped).
 *
 * The rules (they moved here from the backend with the translation):
 *  - only a field that is SET and FINITE is written; an unset one stays unset, so the slot's default and the
 *    filter envelope's slot-layer depth fill (the engine's `filterEnvDef`) decide;
 *  - a typed door WINS over an `ignp` of the same key on the same event, whatever the order: the door's value
 *    is written after the event's own bag is copied;
 *  - a filter is written only when its cutoff is set; its `q` and `passes` ([coercePasses]) only when set, so
 *    an explicit `ignp` of either is never overwritten by a fill (the slot defaults, 0.707 and 1, are the
 *    values the wire used to carry); its envelope (depth, the four stages and the three curves) only
 *    when one of the five envelope knobs is set: exactly the `FilterDef` the wire used to carry;
 *  - the pitch envelope writes every field that is set; its switch `penv.semitones` only when set (a stage-only
 *    call switches nothing on); the vibrato the same, its switch `vibrato.semitones`; `accelerate` under its flat
 *    slot when set;
 *  - shapes and curves travel as their catalogue INDEX (`DistortionShapes`, `LfoShapes`, `AdsrCurves`), a
 *    flag as 1.0 or 0.0.
 *
 * An event that writes no voice door (every group null) returns at once with its own bag's copy (`ParamBag.toMap`),
 * the one allocation the wire boundary always had; otherwise ONE sized copy is written into (the hot path of the
 * frontend's query, measured in phase 3 step 8).
 */
internal fun SprudelVoiceData.classicSlotParams(): Map<String, Double>? {
    // The door-less event, the common case (a sample hit, a bare note): its own bag's copy, as the wire always
    // carried, with no writer and no key lookups.
    if (adsr == null && lpf == null && hpf == null && bpf == null && notch == null &&
        distortion == null && tremolo == null && sample == null && pitchEnv == null && pitchMod == null
    ) {
        return ignitorParams?.toMap()
    }

    val k = ClassicSlotKeys
    val bag = ClassicSlotParams(ignitorParams)

    // The pitch envelope: every field that is set, as on the retired wire fields. `penv.semitones` is the stage's
    // switch and is written only when the pattern set it, so a stage-only call (`penv(attack = 0.1)`) leaves it at
    // its slot default 0.0, which the gate reads as off (`/dsl-design` section 4: a tail-only call never invents it).
    pitchEnv?.let { e ->
        bag.put(k.penvSemitones, e.pEnv)
        bag.put(k.penvAttack, e.pAttack)
        bag.put(k.penvDecay, e.pDecay)
        bag.put(k.penvSustain, e.pSustain)
        bag.put(k.penvRelease, e.pRelease)
        bag.putCurve(k.penvCurveAttack, e.pAttackCurve)
        bag.putCurve(k.penvCurveDecay, e.pDecayCurve)
        bag.putCurve(k.penvCurveRelease, e.pReleaseCurve)
    }

    // The vibrato (pitch pipeline step 2): the rate and the depth when set. `vibrato.semitones` is the switch, so a
    // rate-only call (`vib(4)`) leaves the depth at its slot default 0.0 and builds no vibrato, as on the strip.
    // The group's `accelerate` (step 3) is its own stage's switch, under the flat slot `accelerate`.
    pitchMod?.let { m ->
        bag.put(k.vibratoRate, m.vibrato)
        bag.put(k.vibratoSemitones, m.vibratoMod)
        bag.put(k.accelerate, m.accelerate)
    }

    distortion?.let { d ->
        bag.put(k.crushBits, d.crush)
        bag.put(k.crushOversample, d.crushOversample?.toDouble())
        bag.put(k.coarseFactor, d.coarse)
        bag.put(k.coarseOversample, d.coarseOversample?.toDouble())
        bag.put(k.distortAmount, d.distort)
        bag.put(k.distortShape, d.distortShape?.let { DistortionShapes.indexOf(it) })
        bag.put(k.distortOversample, d.distortOversample?.toDouble())
    }

    hpf?.let { bag.putFilter(it, k.hpf) }
    bpf?.let { bag.putFilter(it, k.bpf) }
    notch?.let { bag.putFilter(it, k.notch) }
    lpf?.let { bag.putFilter(it, k.lpf) }

    tremolo?.let { t ->
        bag.put(k.tremoloDepth, t.tremoloDepth)
        bag.put(k.tremoloRate, t.tremoloRate)
        bag.put(k.tremoloShape, t.tremoloShape?.let { LfoShapes.indexOf(it) })
    }

    adsr?.let { a ->
        bag.put(k.adsrAttack, a.attack)
        bag.put(k.adsrDecay, a.decay)
        bag.put(k.adsrSustain, a.sustain)
        bag.put(k.adsrRelease, a.release)
        bag.putCurve(k.adsrCurveAttack, a.attackCurve)
        bag.putCurve(k.adsrCurveDecay, a.decayCurve)
        bag.putCurve(k.adsrCurveRelease, a.releaseCurve)
        bag.put(k.adsrOn, a.on?.let { if (it) 1.0 else 0.0 })
    }

    sample?.let { p ->
        bag.put(k.begin, p.begin)
        bag.put(k.end, p.end)
        bag.put(k.speed, p.speed)
        bag.put(k.loop, p.loop?.let { if (it) 1.0 else 0.0 })
    }

    return bag.result()
}

/** The slot names of one filter kind, [passes] null for the two kinds without a cascade (`bpf`, `notch`). */
private class FilterSlotKeys(
    val freq: String,
    val q: String,
    val passes: String?,
    val env: String,
    val attack: String,
    val decay: String,
    val sustain: String,
    val release: String,
    val curveAttack: String,
    val curveDecay: String,
    val curveRelease: String,
)

/**
 * Every key this translation writes, read ONCE from the `Param` objects of [IgnitorDsl.Slots] (never retyped).
 * An object, so the names are read on first use, after `IgnitorDsl.Slots` (another module) is built.
 */
private object ClassicSlotKeys {
    private val s = IgnitorDsl.Slots

    private fun name(slot: IgnitorDsl): String = (slot as IgnitorDsl.Param).name

    private fun pass(f: PassFilterSlots, c: ModEnvelopeCurvesSlots) = FilterSlotKeys(
        name(f.freq), name(f.q), name(f.passes), name(f.env), name(f.attack), name(f.decay), name(f.sustain),
        name(f.release), name(c.attack), name(c.decay), name(c.release),
    )

    private fun band(f: BandFilterSlots, c: ModEnvelopeCurvesSlots) = FilterSlotKeys(
        name(f.freq), name(f.q), null, name(f.env), name(f.attack), name(f.decay), name(f.sustain),
        name(f.release), name(c.attack), name(c.decay), name(c.release),
    )

    val vibratoRate = name(s.vibrato.rate)
    val vibratoSemitones = name(s.vibrato.semitones)
    val accelerate = name(s.accelerate)

    val penvSemitones = name(s.penv.semitones)
    val penvAttack = name(s.penv.attack)
    val penvDecay = name(s.penv.decay)
    val penvSustain = name(s.penv.sustain)
    val penvRelease = name(s.penv.release)
    val penvCurveAttack = name(s.penvCurves.attack)
    val penvCurveDecay = name(s.penvCurves.decay)
    val penvCurveRelease = name(s.penvCurves.release)

    val crushBits = name(s.crush.bits)
    val crushOversample = CRUSH_OVERSAMPLE_KEY
    val coarseFactor = name(s.coarse.factor)
    val coarseOversample = COARSE_OVERSAMPLE_KEY
    val distortAmount = name(s.distort.amount)
    val distortShape = name(s.distort.shape)
    val distortOversample = name(s.distort.oversample)

    val hpf = pass(s.hpf, s.hpfCurves)
    val bpf = band(s.bpf, s.bpfCurves)
    val notch = band(s.notch, s.notchCurves)
    val lpf = pass(s.lpf, s.lpfCurves)

    val tremoloDepth = name(s.tremolo.depth)
    val tremoloRate = name(s.tremolo.rate)
    val tremoloShape = name(s.tremolo.shape)

    val adsrAttack = name(s.adsr.attack)
    val adsrDecay = name(s.adsr.decay)
    val adsrSustain = name(s.adsr.sustain)
    val adsrRelease = name(s.adsr.release)
    val adsrOn = name(s.adsr.on)
    val adsrCurveAttack = name(s.adsrCurves.attack)
    val adsrCurveDecay = name(s.adsrCurves.decay)
    val adsrCurveRelease = name(s.adsrCurves.release)

    val begin = name(s.sample.begin)
    val end = name(s.sample.end)
    val speed = name(s.sample.speed)
    val loop = name(s.sample.loop)
}

/** The bag being written: a copy of the event's own bag, sized once, made at the first field that is set. */
private class ClassicSlotParams(private val own: ParamBag?) {
    private var out: MutableMap<String, Double>? = null

    fun put(name: String, value: Double?) {
        if (value == null || !value.isFinite()) { // NaN-guard: a non-finite field reads as unset
            return
        }

        val target = out
            ?: (own?.toMutableMap(EXPECTED_SLOTS) ?: LinkedHashMap<String, Double>(EXPECTED_SLOTS)).also { out = it }

        target[name] = value
    }

    fun putCurve(name: String, curve: AdsrCurve?) {
        if (curve != null) {
            put(name, AdsrCurves.indexOf(curve))
        }
    }

    fun putFilter(filter: SvdFilter, keys: FilterSlotKeys) {
        val cutoff = filter.cutoff ?: return

        put(keys.freq, cutoff)
        put(keys.q, filter.resonance)

        val passes = filter.passes

        if (keys.passes != null && passes != null) {
            put(keys.passes, coercePasses(passes).toDouble())
        }

        val hasEnvelope = filter.attack != null || filter.decay != null || filter.sustain != null ||
            filter.release != null || filter.env != null

        if (!hasEnvelope) {
            return
        }

        put(keys.env, filter.env)
        put(keys.attack, filter.attack)
        put(keys.decay, filter.decay)
        put(keys.sustain, filter.sustain)
        put(keys.release, filter.release)
        putCurve(keys.curveAttack, filter.attackCurve)
        putCurve(keys.curveDecay, filter.decayCurve)
        putCurve(keys.curveRelease, filter.releaseCurve)
    }

    fun result(): Map<String, Double>? = out ?: own?.toMap()

    private companion object {
        /** Room for the slots a richly written event adds, so the copy rarely grows while it is written. */
        const val EXPECTED_SLOTS = 32
    }
}
