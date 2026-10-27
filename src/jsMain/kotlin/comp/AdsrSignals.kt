/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.AdsrCurves
import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KlangPlaybackSignal
import io.peekandpoke.klang.audio_bridge.ModEnvelopeCurvesSlots
import io.peekandpoke.klang.audio_bridge.constants.MOD_ENV_CURVE
import io.peekandpoke.klang.ui.comp.KlangAdsr
import io.peekandpoke.klang.ui.comp.KlangAdsrSignal

/**
 * What a player's signal means for the curve of one envelope (KLANG-00371): the notes it schedules, one each, when
 * that envelope is on for them; a stop or a live update voids the notes still ahead. [door] names the envelope as the
 * voice data does: `adsr` (the loudness), `lpf`, `hpf`, `bpf`, `notch` (a filter's cutoff), `penv` (the pitch), `fm`.
 */
fun KlangPlaybackSignal?.toAdsrSignals(door: String): List<KlangAdsrSignal> = when (this) {
    null, is KlangPlaybackSignal.PlaybackStopped, is KlangPlaybackSignal.PatternUpdated -> listOf(KlangAdsrSignal.Clear)

    is KlangPlaybackSignal.VoicesScheduled -> voices.mapNotNull { it.adsrNote(door) }

    else -> emptyList()
}

/**
 * This voice as a note on [door]'s envelope: its start, its gate's end, the four numbers and the three stage curves it
 * plays with, each the one written or the engine's default. Null when the envelope is off for it, or [door] has none.
 */
fun KlangPlaybackSignal.VoicesScheduled.VoiceEvent.adsrNote(door: String): KlangAdsrSignal.Note? {
    val stages = envelopeStages(door) ?: return null
    val params = data.ignitorParams

    fun value(slot: IgnitorDsl): Double {
        val param = slot as IgnitorDsl.Param

        return params?.get(param.name) ?: param.default
    }

    fun written(slot: IgnitorDsl): Double? = params?.get((slot as IgnitorDsl.Param).name)

    if (!stages.isOn(::written)) {
        return null
    }

    return KlangAdsrSignal.Note(
        startSec = startTime,
        gateEndSec = endTime,
        adsr = KlangAdsr(
            attack = value(stages.attack),
            decay = value(stages.decay),
            sustain = value(stages.sustain),
            release = value(stages.release),
            attackCurve = stages.curve(stages.curves?.attack, ::value),
            decayCurve = stages.curve(stages.curves?.decay, ::value),
            releaseCurve = stages.curve(stages.curves?.release, ::value),
        ),
    )
}

/** An envelope's four stage slots, its curve slots, and when it is on: from what the voice wrote. */
private class EnvelopeStages(
    val attack: IgnitorDsl,
    val decay: IgnitorDsl,
    val sustain: IgnitorDsl,
    val release: IgnitorDsl,
    /** The stages' curve slots; null for an envelope that has none (the FM's), which bends as [fallback]. */
    val curves: CurveSlots? = null,
    /** The curve an unwritten or unknown one is: each envelope has its own default. */
    val fallback: AdsrCurve = MOD_ENV_CURVE,
    val isOn: (written: (IgnitorDsl) -> Double?) -> Boolean,
) {
    /** The curve a slot holds (an index in [AdsrCurves]); [fallback] for an envelope without curve slots. */
    fun curve(slot: IgnitorDsl?, value: (IgnitorDsl) -> Double): AdsrCurve =
        slot?.let { AdsrCurves.curveAt(index = value(it), fallback = fallback) } ?: fallback
}

/** The three curve slots of an envelope's moving stages. */
private class CurveSlots(val attack: IgnitorDsl, val decay: IgnitorDsl, val release: IgnitorDsl)

private fun envelopeStages(door: String): EnvelopeStages? {
    val slots = IgnitorDsl.Slots

    /** A filter's envelope is on with a depth written, or with a stage written and no depth (the depth then filled). */
    fun filter(
        env: IgnitorDsl,
        attack: IgnitorDsl,
        decay: IgnitorDsl,
        sustain: IgnitorDsl,
        release: IgnitorDsl,
        curves: ModEnvelopeCurvesSlots,
    ) =
        EnvelopeStages(
            attack = attack,
            decay = decay,
            sustain = sustain,
            release = release,
            curves = CurveSlots(attack = curves.attack, decay = curves.decay, release = curves.release),
        ) { written ->
            when (val depth = written(env)) {
                null -> listOf(attack, decay, sustain, release).any { written(it) != null }
                else -> depth != 0.0
            }
        }

    return when (door) {
        "adsr" -> with(slots.adsr) {
            val curves = slots.adsrCurves

            // Off only when switched off (`adsrOff()`)
            EnvelopeStages(
                attack = attack,
                decay = decay,
                sustain = sustain,
                release = release,
                curves = CurveSlots(attack = curves.attack, decay = curves.decay, release = curves.release),
                fallback = AdsrCurve.Default,
            ) { written -> written(on) != 0.0 }
        }

        "lpf" -> with(slots.lpf) { filter(env, attack, decay, sustain, release, curves = slots.lpfCurves) }
        "hpf" -> with(slots.hpf) { filter(env, attack, decay, sustain, release, curves = slots.hpfCurves) }
        "bpf" -> with(slots.bpf) { filter(env, attack, decay, sustain, release, curves = slots.bpfCurves) }
        "notch" -> with(slots.notch) { filter(env, attack, decay, sustain, release, curves = slots.notchCurves) }

        "penv" -> with(slots.penv) {
            val curves = slots.penvCurves

            // Its depth is its switch
            EnvelopeStages(
                attack = attack,
                decay = decay,
                sustain = sustain,
                release = release,
                curves = CurveSlots(attack = curves.attack, decay = curves.decay, release = curves.release),
            ) { written -> (written(semitones) ?: 0.0) != 0.0 }
        }

        "fm" -> with(slots.fm) {
            EnvelopeStages(attack = attack, decay = decay, sustain = sustain, release = release) { written ->
                (written(depth) ?: 0.0) != 0.0
            }
        }

        else -> null
    }
}
