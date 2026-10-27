/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.audio_bridge.IgnitorDsl
import io.peekandpoke.klang.audio_bridge.KlangPlaybackSignal
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
 * This voice as a note on [door]'s envelope: its start, its gate's end and the four numbers it plays with, each the
 * one written or the engine's default. Null when the envelope is off for it, or [door] has none.
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
        ),
    )
}

/** An envelope's four stage slots, and when it is on: from what the voice wrote. */
private class EnvelopeStages(
    val attack: IgnitorDsl,
    val decay: IgnitorDsl,
    val sustain: IgnitorDsl,
    val release: IgnitorDsl,
    val isOn: (written: (IgnitorDsl) -> Double?) -> Boolean,
)

private fun envelopeStages(door: String): EnvelopeStages? {
    val slots = IgnitorDsl.Slots

    /** A filter's envelope is on with a depth written, or with a stage written and no depth (the depth then filled). */
    fun filter(env: IgnitorDsl, attack: IgnitorDsl, decay: IgnitorDsl, sustain: IgnitorDsl, release: IgnitorDsl) =
        EnvelopeStages(attack = attack, decay = decay, sustain = sustain, release = release) { written ->
            when (val depth = written(env)) {
                null -> listOf(attack, decay, sustain, release).any { written(it) != null }
                else -> depth != 0.0
            }
        }

    return when (door) {
        "adsr" -> with(slots.adsr) {
            // Off only when switched off (`adsrOff()`)
            EnvelopeStages(attack = attack, decay = decay, sustain = sustain, release = release) { written -> written(on) != 0.0 }
        }

        "lpf" -> with(slots.lpf) { filter(env, attack, decay, sustain, release) }
        "hpf" -> with(slots.hpf) { filter(env, attack, decay, sustain, release) }
        "bpf" -> with(slots.bpf) { filter(env, attack, decay, sustain, release) }
        "notch" -> with(slots.notch) { filter(env, attack, decay, sustain, release) }

        "penv" -> with(slots.penv) {
            // Its depth is its switch
            EnvelopeStages(attack = attack, decay = decay, sustain = sustain, release = release) { written ->
                (written(semitones) ?: 0.0) != 0.0
            }
        }

        "fm" -> with(slots.fm) {
            EnvelopeStages(attack = attack, decay = decay, sustain = sustain, release = release) { written ->
                (written(depth) ?: 0.0) != 0.0
            }
        }

        else -> null
    }
}
