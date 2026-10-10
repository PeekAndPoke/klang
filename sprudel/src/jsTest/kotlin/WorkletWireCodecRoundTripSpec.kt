/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.nulls.shouldNotBeNull
import io.peekandpoke.klang.audio_bridge.AdsrCurve
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

    "fully-populated voice (every cluster, every voice door as its slots) survives the worklet round-trip" {
        // Touch every Svd* group, so toVoiceData() writes every slot key the doors translate to and every field left.
        val data = createSprudelVoiceData {
            note = "c3"; freqHz = 130.81; scale = "e minor"; gain = 0.7; velocity = 0.9; legato = 0.95
            bank = "MPC60"; sound = SoundValue.Named("supersaw"); soundIndex = 2
            ignitorParams = paramBagOf("voices" to 7.0, "spread" to 0.3, "panSpread" to 0.4)
            // The bus knobs travel as orbit slots only since Katalyst step 5b-3: every door's worth.
            katalystParams = paramBagOf(
                "reverb.wet" to 0.5, "reverb.size" to 6.0, "reverb.lowpass" to 8000.0,
                "delay.wet" to 0.3, "delay.time" to 0.25, "delay.feedback" to 0.4, "delay.cap" to 2.5,
                "compressor.threshold" to -12.0, "compressor.ratio" to 4.0, "compressor.knee" to 2.5,
                "compressor.attack" to 0.01, "compressor.release" to 0.31,
                "duck.orbit" to 0.0, "duck.attack" to 0.05, "duck.depth" to 0.5,
            )
            attack = 0.005; decay = 0.2; sustain = 0.6; release = 0.05
            attackCurve = AdsrCurve.Linear; decayCurve = AdsrCurve.Square; releaseCurve = AdsrCurve.Cube
            adsrOn = false   // non-default: `Boolean?` is the shape a dynamic codec can confuse with undefined
            cutoff = 1625.0; resonance = 1.2; lpattack = 0.01; lpdecay = 0.1; lpsustain = 0.5; lprelease = 0.2; lpenv = 1.0; lpPasses = 2.0
            hcutoff = 1350.0; hresonance = 0.8; hpattack = 0.02; hpenv = 0.7; hpPasses = 3.0
            bandf = 800.0; bandq = 1.0; bpenv = 0.5
            notchf = 500.0; nresonance = 0.7; nfenv = 0.4
            lpAttackCurve = AdsrCurve.Linear; lpDecayCurve = AdsrCurve.SCurve; lpReleaseCurve = AdsrCurve.Square
            hpAttackCurve = AdsrCurve.Cube; bpDecayCurve = AdsrCurve.InvSquare; nfReleaseCurve = AdsrCurve.Linear
            vowel = "a"; vowelMix = 0.45; vowelFloor = 0.15; body = "wood"; bodyMix = 0.4; bodyFloor = 0.25
            accelerate = 0.1; vibrato = 5.0; vibratoMod = 0.3; vibratoRangeFrom = 0.0; vibratoRangeTo = 0.9; vibratoPhase = 0.25
            pAttack = 0.01; pDecay = 0.05; pSustain = 0.5; pRelease = 0.1; pEnv = 12.0
            pAttackCurve = AdsrCurve.Square; pDecayCurve = AdsrCurve.SCurve; pReleaseCurve = AdsrCurve.InvSquare
            fmh = 2.0; fmAttack = 0.01; fmDecay = 0.1; fmSustain = 0.5; fmEnv = 0.8; fmRelease = 0.2
            distort = 0.3; distortShape = "tube"; distortOversample = 4; coarse = 2.0; coarseOversample = 2; crush = 8.0; crushOversample =
            2
            phaserRate = 0.5; phaserDepth = 0.6; phaserCenter = 1800.0; phaserSweep = 1000.0; phaserFloor = 0.3
            tremoloRate = 4.0; tremoloDepth = 0.4; tremoloShape = "sine"
            cylinder = 1; pan = 0.3
            begin = 0.0; end = 1.0; speed = 1.0; unit = "c"; loop = true; cut = 1
            solo = 1.0; cull = 0.2
        }.toVoiceData()

        // Sanity: the four voice filters and every voice door travel as `classic()` slot keys in `ignitorParams` (phase 3
        // step 8), so the map carries them through the codec too. The vowel and body fields set above cross nothing:
        // the orbit stages travel as `katalystParams` slots (the doors write them; this voice sets the fields directly).
        data.ignitorParams?.get("lpf.passes") shouldBe 2.0
        data.ignitorParams?.get("notch.env") shouldBe 0.4
        data.ignitorParams?.get("adsr.on") shouldBe 0.0
        data.ignitorParams?.get("loop") shouldBe 1.0
        data.ignitorParams?.get("fm.depth") shouldBe 0.8

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
        )
        // Each voice filter's envelope depth as written above, the literal the decoded slot must carry.
        val envDepth = mapOf("lpf" to 1.0, "hpf" to 0.6, "bpf" to 0.5, "notch" to 0.4)

        for ((name, cfg) in cases) {
            val data = createSprudelVoiceData { note = "c4"; freqHz = 261.6; sound = SoundValue.Named("saw"); cfg() }.toVoiceData()
            val decoded = roundTrip(scheduled(data))
            decoded shouldBe scheduled(data)
            // and the decoded filter is intact, as its slots
            decoded.data.ignitorParams?.get("$name.env") shouldBe envDepth.getValue(name)
        }
    }

    "decoded VoiceData carries the envelope and the lowpass with its envelope as slots" {
        val data = createSprudelVoiceData {
            note = "c4"; sound = SoundValue.Named("saw")
            attack = 0.01; release = 0.3
            cutoff = 1000.0; resonance = 1.5; lpattack = 0.02; lpenv = 0.9
        }.toVoiceData()

        val decoded = roundTrip(scheduled(data)).data

        val slots = decoded.ignitorParams.shouldNotBeNull()
        slots["adsr.attack"] shouldBe 0.01
        slots["adsr.release"] shouldBe 0.3
        slots["lpf.freq"] shouldBe 1000.0
        slots["lpf.q"] shouldBe 1.5
        slots["lpf.attack"] shouldBe 0.02
        slots["lpf.env"] shouldBe 0.9
    }
})
