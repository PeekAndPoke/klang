/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.AdsrDef
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.audio_bridge.ScheduledVoice
import io.peekandpoke.klang.audio_bridge.SoundValue
import io.peekandpoke.klang.audio_bridge.VoiceData
import io.peekandpoke.klang.audio_bridge.wire.decode_ScheduledVoice
import io.peekandpoke.klang.audio_bridge.wire.encode_ScheduledVoice

/**
 * Round-trip guard for the **JS-Worklet wire contract** (JS-only — the KSP codec uses `dynamic`).
 *
 * What actually crosses to the audio worklet is a [ScheduledVoice] whose `data` is a [VoiceData] — produced
 * by [SprudelVoiceData.toVoiceData]. `SprudelVoiceData` itself (grouped into `Svd*` sub-objects) never
 * crosses the boundary, so its storage shape can't affect the worklet. These tests pin that: a fully-
 * populated voice survives `encode_ScheduledVoice` → `decode_ScheduledVoice` unchanged through the **actual
 * runtime codec** (replacing the old kotlinx-JSON proxy — the worklet never used kotlinx). Data classes give
 * structural `==`.
 */
class WorkletWireCodecRoundTripSpec : StringSpec({

    fun roundTrip(sv: ScheduledVoice): ScheduledVoice = decode_ScheduledVoice(encode_ScheduledVoice(sv))

    fun scheduled(data: VoiceData) = ScheduledVoice(
        playbackId = "pb-1",
        data = data,
        startTime = 1.25,
        gateEndTime = 2.5,
        playbackStartTime = 0.5,
    )

    "fully-populated voice (every cluster + all filter types) survives the worklet round-trip" {
        // Touch every Svd* group so toVoiceData() emits adsr + all 5 filters + every scalar effect field.
        val data = createSprudelVoiceData {
            note = "c3"; freqHz = 130.81; scale = "e minor"; gain = 0.7; velocity = 0.9; postGain = 0.8; legato = 0.95
            bank = "MPC60"; sound = SoundValue.Named("supersaw"); soundIndex = 2
            oscParams = mapOf("voices" to 7.0, "freqSpread" to 0.3, "panSpread" to 0.4)
            attack = 0.005; decay = 0.2; sustain = 0.6; release = 0.05
            attackCurve = AdsrCurve.Linear; decayCurve = AdsrCurve.Square; releaseCurve = AdsrCurve.Cube
            cutoff = 1625.0; resonance = 1.2; lpattack = 0.01; lpdecay = 0.1; lpsustain = 0.5; lprelease = 0.2; lpenv = 1.0
            hcutoff = 1350.0; hresonance = 0.8; hpattack = 0.02; hpenv = 0.7
            bandf = 800.0; bandq = 1.0; bpenv = 0.5
            notchf = 500.0; nresonance = 0.7; nfenv = 0.4
            vowel = "a"; vowelMix = 0.45; body = "wood"; bodyMix = 0.4
            accelerate = 0.1; vibrato = 5.0; vibratoMod = 0.3
            pAttack = 0.01; pDecay = 0.05; pRelease = 0.1; pEnv = 12.0; pCurve = 1.0; pAnchor = 0.5
            fmh = 2.0; fmAttack = 0.01; fmDecay = 0.1; fmSustain = 0.5; fmEnv = 0.8
            distort = 0.3; distortShape = "tube"; distortOversample = 4; coarse = 2.0; coarseOversample = 2; crush = 8.0; crushOversample =
            2
            phaserRate = 0.5; phaserDepth = 0.6; phaserCenter = 1800.0; phaserSweep = 1000.0
            tremoloSync = 4.0; tremoloDepth = 0.4; tremoloSkew = 0.5; tremoloPhase = 0.0; tremoloShape = "sine"
            duckCylinder = 0; duckAttack = 0.05; duckDepth = 0.5
            cylinder = 1; pan = 0.3
            delay = 0.3; delayTime = 0.25; delayFeedback = 0.4
            room = 0.5; roomSize = 0.8; roomFade = 0.3; roomLp = 8000.0; roomDim = 2000.0; iResponse = "hall"
            begin = 0.0; end = 1.0; speed = 1.0; unit = "c"; loop = true; cut = 1; loopBegin = 0.1; loopEnd = 0.9
            compressor = "0.3:4:0.1:0.01:0.1"; solo = 1.0; engine = "pedal"
        }.toVoiceData()

        // Sanity: the conversion produced the full canonical filter chain (HP → BP → Notch → Formant → Body → LP).
        data.filters.size shouldBe 6

        val original = scheduled(data)
        val decoded = roundTrip(original)

        decoded shouldBe original
    }

    "minimal leaf voice (mostly null) survives the worklet round-trip" {
        val data = createSprudelVoiceData {
            note = "a4"; freqHz = 440.0; gain = 0.8; sound = SoundValue.Named("sine")
        }.toVoiceData()

        val original = scheduled(data)
        roundTrip(original) shouldBe original
    }

    "each filter type + its envelope survives the round-trip individually" {
        val cases = listOf<Pair<String, SprudelVoiceData.() -> Unit>>(
            "lpf" to { cutoff = 1000.0; resonance = 1.5; lpattack = 0.01; lpenv = 1.0 },
            "hpf" to { hcutoff = 500.0; hresonance = 2.0; hpdecay = 0.1; hpenv = 0.6 },
            "bpf" to { bandf = 750.0; bandq = 1.2; bpsustain = 0.5; bpenv = 0.5 },
            "notch" to { notchf = 600.0; nresonance = 0.8; nfrelease = 0.2; nfenv = 0.4 },
            "formant" to { vowel = "o" },
            "body" to { body = "glass"; bodyMix = 0.5 },
        )
        for ((_, cfg) in cases) {
            val data = createSprudelVoiceData { note = "c4"; freqHz = 261.6; sound = SoundValue.Named("saw"); cfg() }.toVoiceData()
            val decoded = roundTrip(scheduled(data))
            decoded shouldBe scheduled(data)
            // and the decoded filter chain is intact
            decoded.data.filters.size shouldBe 1
        }
    }

    "decoded VoiceData reconstructs grouped sub-objects (AdsrDef + FilterDef.LowPass envelope)" {
        val data = createSprudelVoiceData {
            note = "c4"; sound = SoundValue.Named("saw")
            attack = 0.01; release = 0.3
            cutoff = 1000.0; resonance = 1.5; lpattack = 0.02; lpenv = 0.9
        }.toVoiceData()

        val decoded = roundTrip(scheduled(data)).data

        val adsr = decoded.adsr.shouldBeInstanceOf<AdsrDef.Std>()
        adsr.attack shouldBe 0.01
        adsr.release shouldBe 0.3

        val lpf = decoded.filters[0].shouldBeInstanceOf<FilterDef.LowPass>()
        lpf.cutoffHz shouldBe 1000.0
        lpf.q shouldBe 1.5
        lpf.envelope?.attack shouldBe 0.02
        lpf.envelope?.depth shouldBe 0.9
    }
})
