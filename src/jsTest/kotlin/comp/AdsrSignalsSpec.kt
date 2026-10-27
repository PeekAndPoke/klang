/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.AdsrCurves
import io.peekandpoke.klang.audio_bridge.KlangPlaybackSignal
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.constants.FILTER_ENV_RELEASE_SEC
import io.peekandpoke.klang.audio_bridge.constants.VOICE_ADSR_DECAY_SEC
import io.peekandpoke.klang.common.infra.klangMapOf
import io.peekandpoke.klang.ui.comp.KlangAdsr
import io.peekandpoke.klang.ui.comp.KlangAdsrSignal

/** A voice with these instrument values, from 10 s to 10.5 s. */
private fun voice(vararg params: Pair<String, Double>) = KlangPlaybackSignal.VoicesScheduled.VoiceEvent(
    startTime = 10.0,
    endTime = 10.5,
    data = VoiceData.empty.copy(ignitorParams = klangMapOf(*params)),
    sourceLocations = null,
)

/** Each stage in the engine's default curve, the exponential, as every envelope bends when its code writes none. */
private fun KlangAdsr.bentAsTheEngine() = copy(
    attackCurve = AdsrCurve.Exponential,
    decayCurve = AdsrCurve.Exponential,
    releaseCurve = AdsrCurve.Exponential,
)

/** A player's voice events as notes on one envelope's curve (KLANG-00371): which envelope, when on, with what. */
class AdsrSignalsSpec : StringSpec({

    "the loudness: its start, its gate's end, the numbers written, the engine's default for one not written" {
        val note = voice("adsr.attack" to 0.3, "adsr.sustain" to 1.0, "adsr.release" to 0.05).adsrNote(door = "adsr")

        note shouldBe KlangAdsrSignal.Note(
            startSec = 10.0,
            gateEndSec = 10.5,
            adsr = KlangAdsr(attack = 0.3, decay = VOICE_ADSR_DECAY_SEC, sustain = 1.0, release = 0.05).bentAsTheEngine(),
        )
    }

    "its curves: the ones written, the envelope's default for the others; the FM's, which has none, its default" {
        val square = AdsrCurves.indexOf(AdsrCurve.Square)
        val scurve = AdsrCurves.indexOf(AdsrCurve.SCurve)

        voice("adsrCurves.attack" to square).adsrNote(door = "adsr")?.adsr?.let { adsr ->
            listOf(adsr.attackCurve, adsr.decayCurve, adsr.releaseCurve)
        } shouldBe listOf(AdsrCurve.Square, AdsrCurve.Exponential, AdsrCurve.Exponential)

        voice("lpf.env" to 24.0, "lpfCurves.release" to scurve).adsrNote(door = "lpf")?.adsr?.releaseCurve shouldBe AdsrCurve.SCurve
        voice("lpf.env" to 24.0, "lpfCurves.release" to scurve).adsrNote(door = "lpf")?.adsr?.attackCurve shouldBe AdsrCurve.Exponential
        voice("penv.semitones" to 12.0, "penvCurves.decay" to square).adsrNote(door = "penv")?.adsr?.decayCurve shouldBe AdsrCurve.Square
        voice("fm.depth" to 3.0).adsrNote(door = "fm")?.adsr?.attackCurve shouldBe AdsrCurve.Exponential
    }

    "the loudness switched off (adsrOff) is no note" {
        voice("adsr.attack" to 0.3, "adsr.on" to 0.0).adsrNote(door = "adsr") shouldBe null
    }

    "a filter's envelope: off with nothing written, on with a stage written, off with a depth of 0" {
        voice("lpf.freq" to 400.0).adsrNote(door = "lpf") shouldBe null

        voice("lpf.freq" to 400.0, "lpf.attack" to 0.2, "lpf.decay" to 0.2, "lpf.sustain" to 0.8).adsrNote(door = "lpf")?.adsr shouldBe
            KlangAdsr(attack = 0.2, decay = 0.2, sustain = 0.8, release = FILTER_ENV_RELEASE_SEC).bentAsTheEngine()

        voice("lpf.env" to 0.0, "lpf.attack" to 0.2).adsrNote(door = "lpf") shouldBe null
        (voice("lpf.env" to 36.0).adsrNote(door = "lpf") != null) shouldBe true
    }

    "the other filters' envelopes as the lowpass's; the pitch's and the FM's on with their depth" {
        val scurve = AdsrCurves.indexOf(AdsrCurve.SCurve)

        for (door in listOf("hpf", "bpf", "notch")) {
            voice("$door.freq" to 800.0).adsrNote(door = door) shouldBe null
            voice("$door.env" to 24.0, "${door}Curves.release" to scurve).adsrNote(door = door)?.adsr?.releaseCurve shouldBe
                AdsrCurve.SCurve
            voice("$door.attack" to 0.2).adsrNote(door = door)?.adsr?.attack shouldBe 0.2
            voice("$door.env" to 0.0, "$door.attack" to 0.2).adsrNote(door = door) shouldBe null
        }

        voice("penv.attack" to 0.2).adsrNote(door = "penv") shouldBe null
        voice("penv.semitones" to 12.0, "penv.attack" to 0.2).adsrNote(door = "penv")?.adsr?.attack shouldBe 0.2

        voice("fm.attack" to 0.2).adsrNote(door = "fm") shouldBe null
        voice("fm.depth" to 3.0, "fm.attack" to 0.2).adsrNote(door = "fm")?.adsr?.attack shouldBe 0.2
    }

    "each envelope reads its own door; one without an envelope gives nothing" {
        val both = voice("adsr.attack" to 0.01, "lpf.env" to 24.0, "lpf.attack" to 0.5)

        both.adsrNote(door = "adsr")?.adsr?.attack shouldBe 0.01
        both.adsrNote(door = "lpf")?.adsr?.attack shouldBe 0.5
        both.adsrNote(door = "crush") shouldBe null
    }

    "a stop, a live update and no signal void the notes ahead; scheduled voices are notes; the rest is nothing" {
        val clear = listOf(KlangAdsrSignal.Clear)

        null.toAdsrSignals(door = "adsr") shouldContainExactly clear
        KlangPlaybackSignal.PlaybackStopped.toAdsrSignals(door = "adsr") shouldContainExactly clear
        KlangPlaybackSignal.PatternUpdated.toAdsrSignals(door = "adsr") shouldContainExactly clear
        KlangPlaybackSignal.PlaybackStarted.toAdsrSignals(door = "adsr") shouldContainExactly emptyList()

        val scheduled = KlangPlaybackSignal.VoicesScheduled(voices = listOf(voice(), voice("adsr.on" to 0.0), voice()))

        scheduled.toAdsrSignals(door = "adsr").size shouldBe 2
    }
})
