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
import io.peekandpoke.klang.audio_bridge.PipelineValue
import io.peekandpoke.klang.audio_bridge.SoundValue

class SprudelVoiceDataSpec : StringSpec({

    "empty companion object has all fields null" {
        val empty = createSprudelVoiceData { }

        empty.note shouldBe null
        empty.freqHz shouldBe null
        empty.gain shouldBe null
        empty.attack shouldBe null
        empty.cutoff shouldBe null
        empty.resonance shouldBe null
        empty.value shouldBe null
    }

    "clone() copies every field (guards the hand-written clone against dropped/swapped fields)" {
        // Every field set to a distinct non-default value. clone() must reproduce all of them; a
        // dropped field would clone to null and a swap would mismatch — either fails data-class equals.
        val populated = populatedVoiceData(0)

        val cloned = populated.clone()

        cloned shouldBe populated
        (cloned === populated) shouldBe false
    }

    "mergeFrom() matches merge() (guards the in-place merge against the copy-based merge)" {
        // Two fully-populated instances with distinct values. With every field of `b` non-null, the
        // copy-based merge() takes all of b's values (patternId stays a's). mergeFrom() must reproduce
        // that exactly — a dropped/wrong field would diverge from the merge() oracle.
        val a = populatedVoiceData(0)
        val b = populatedVoiceData(1000)

        val viaMerge = a.merge(b)
        val viaMergeFrom = a.clone().also { it.mergeFrom(b) }

        viaMergeFrom shouldBe viaMerge
    }

    "can create SprudelVoiceData with basic fields" {
        val data = createSprudelVoiceData {
            note = "c4"
            freqHz = 440.0
            gain = 0.8
        }

        data.note shouldBe "c4"
        data.freqHz shouldBe 440.0
        data.gain shouldBe 0.8
    }

    "toVoiceData() converts flat ADSR fields to AdsrDef" {
        val data = createSprudelVoiceData {
            attack = 0.01
            decay = 0.1
            sustain = 0.7
            release = 0.3
        }

        val voiceData = data.toVoiceData()

        val adsr = voiceData.adsr as AdsrDef.Std
        adsr.attack shouldBe 0.01
        adsr.decay shouldBe 0.1
        adsr.sustain shouldBe 0.7
        adsr.release shouldBe 0.3
    }

    "toVoiceData() converts LPF flat fields to FilterDef.LowPass" {
        val data = createSprudelVoiceData {
            cutoff = 1000.0
            resonance = 1.5
        }

        val voiceData = data.toVoiceData()

        voiceData.filters.size shouldBe 1
        val lpf = voiceData.filters[0] as FilterDef.LowPass
        lpf.cutoffHz shouldBe 1000.0
        lpf.q shouldBe 1.5
    }

    "toVoiceData() converts HPF flat fields to FilterDef.HighPass" {
        val data = createSprudelVoiceData {
            hcutoff = 500.0
            hresonance = 2.0
        }

        val voiceData = data.toVoiceData()

        voiceData.filters.size shouldBe 1
        val hpf = voiceData.filters[0] as FilterDef.HighPass
        hpf.cutoffHz shouldBe 500.0
        hpf.q shouldBe 2.0
    }

    "toVoiceData() converts BPF flat fields to FilterDef.BandPass" {
        val data = createSprudelVoiceData {
            bandf = 750.0
            bandq = 1.2
        }

        val voiceData = data.toVoiceData()

        voiceData.filters.size shouldBe 1
        val bpf = voiceData.filters[0] as FilterDef.BandPass
        bpf.cutoffHz shouldBe 750.0
        bpf.q shouldBe 1.2
    }

    "toVoiceData() converts Notch flat fields to FilterDef.Notch" {
        val data = createSprudelVoiceData {
            notchf = 600.0
            nresonance = 0.8
        }

        val voiceData = data.toVoiceData()

        voiceData.filters.size shouldBe 1
        val notch = voiceData.filters[0] as FilterDef.Notch
        notch.cutoffHz shouldBe 600.0
        notch.q shouldBe 0.8
    }

    "toVoiceData() creates multiple filters with independent resonance" {
        val data = createSprudelVoiceData {
            cutoff = 1000.0
            resonance = 1.5
            hcutoff = 500.0
            hresonance = 2.0
            bandf = 750.0
            bandq = 1.2
        }

        val voiceData = data.toVoiceData()

        voiceData.filters.size shouldBe 3

        // Canonical chain order: HighPass → BandPass → LowPass (lowpass LAST).
        val hpf = voiceData.filters[0] as FilterDef.HighPass
        hpf.cutoffHz shouldBe 500.0
        hpf.q shouldBe 2.0

        val bpf = voiceData.filters[1] as FilterDef.BandPass
        bpf.cutoffHz shouldBe 750.0
        bpf.q shouldBe 1.2

        val lpf = voiceData.filters[2] as FilterDef.LowPass
        lpf.cutoffHz shouldBe 1000.0
        lpf.q shouldBe 1.5
    }

    "toVoiceData() orders the filter chain HighPass → BandPass → Notch → Formant → LowPass" {
        // Flat fields are declared LowPass-first here on purpose; toVoiceData must
        // re-order them into the canonical chain regardless of field assignment order.
        val data = createSprudelVoiceData {
            cutoff = 1000.0   // LowPass  → must end up LAST
            hcutoff = 500.0   // HighPass → must end up FIRST
            bandf = 750.0
            notchf = 600.0
            vowel = "a"       // Formant
        }

        val voiceData = data.toVoiceData()

        voiceData.filters.size shouldBe 5
        voiceData.filters[0].shouldBeInstanceOf<FilterDef.HighPass>()
        voiceData.filters[1].shouldBeInstanceOf<FilterDef.BandPass>()
        voiceData.filters[2].shouldBeInstanceOf<FilterDef.Notch>()
        voiceData.filters[3].shouldBeInstanceOf<FilterDef.Formant>()
        voiceData.filters[4].shouldBeInstanceOf<FilterDef.LowPass>()
    }

    "toVoiceData() puts LowPass last even when it is the only pair with HighPass" {
        val data = createSprudelVoiceData { cutoff = 1000.0; hcutoff = 80.0 }

        val voiceData = data.toVoiceData()

        voiceData.filters.size shouldBe 2
        voiceData.filters[0].shouldBeInstanceOf<FilterDef.HighPass>()
        voiceData.filters[1].shouldBeInstanceOf<FilterDef.LowPass>()
    }

    "toVoiceData() handles null resonance gracefully" {
        val data = createSprudelVoiceData {
            cutoff = 1000.0
            resonance = null // No resonance specified
        }

        val voiceData = data.toVoiceData()

        voiceData.filters.size shouldBe 1
        val lpf = voiceData.filters[0] as FilterDef.LowPass
        lpf.cutoffHz shouldBe 1000.0
        lpf.q shouldBe 1.0 // defaults to 1.0
    }

    "toVoiceData() maps all basic fields correctly" {
        val data = createSprudelVoiceData {
            note = "c4"
            freqHz = 440.0
            scale = "major"
            gain = 0.8
            legato = 0.9
            bank = "MPC60"
            sound = SoundValue.Named("bd")
            soundIndex = 2
            oscParams = mapOf("density" to 0.5, "panSpread" to 0.3, "detune" to 0.1, "voices" to 3.0)
            accelerate = 0.05
            vibrato = 0.2
            vibratoMod = 0.4
            distort = 0.3
            coarse = 1.0
            crush = 4.0
            cylinder = 1
            pan = 0.5
            delay = 0.3
            delayTime = 0.25
            delayFeedback = 0.5
            room = 0.7
            roomSize = 5.0
            begin = 0.0
            end = 1.0
            speed = 1.0
            loop = true
            cut = 1
        }

        val voiceData = data.toVoiceData()

        voiceData.note shouldBe "c4"
        voiceData.freqHz shouldBe 440.0
        voiceData.scale shouldBe "major"
        voiceData.gain shouldBe 0.8
        voiceData.legato shouldBe 0.9
        voiceData.bank shouldBe "MPC60"
        voiceData.sound shouldBe "bd"
        voiceData.soundIndex shouldBe 2
        voiceData.oscParams?.get("density") shouldBe 0.5
        voiceData.oscParams?.get("panSpread") shouldBe 0.3
        voiceData.oscParams?.get("detune") shouldBe 0.1
        voiceData.oscParams?.get("voices") shouldBe 3.0
        voiceData.accelerate shouldBe 0.05
        voiceData.vibrato shouldBe 0.2
        voiceData.vibratoMod shouldBe 0.4
        voiceData.distort shouldBe 0.3
        voiceData.coarse shouldBe 1.0
        voiceData.crush shouldBe 4.0
        voiceData.cylinder shouldBe 1
        voiceData.pan shouldBe 0.5
        voiceData.delay shouldBe 0.3
        voiceData.delayTime shouldBe 0.25
        voiceData.delayFeedback shouldBe 0.5
        voiceData.room shouldBe 0.7
        voiceData.roomSize shouldBe 5.0
        voiceData.begin shouldBe 0.0
        voiceData.end shouldBe 1.0
        voiceData.speed shouldBe 1.0
        voiceData.loop shouldBe true
        voiceData.cut shouldBe 1
    }

    "copy() creates new instance with updated fields" {
        val original = createSprudelVoiceData {
            note = "c4"
            gain = 0.8
        }

        val modified = original.copy(
            note = "d4",
            freqHz = 440.0
        )

        // Original unchanged
        original.note shouldBe "c4"
        original.gain shouldBe 0.8
        original.freqHz shouldBe null

        // Modified has changes
        modified.note shouldBe "d4"
        modified.gain shouldBe 0.8
        modified.freqHz shouldBe 440.0
    }
})

/**
 * Builds a [SprudelVoiceData] with EVERY field set to a distinct non-null value, offset by [seed] so
 * two instances can be made fully distinct. Used to guard `clone()` and `mergeFrom()` completeness:
 * every field is exercised, so a dropped or swapped field fails data-class equality.
 */
private fun populatedVoiceData(seed: Int): SprudelVoiceData {
    val b = seed.toDouble()
    return createSprudelVoiceData {
        note = "note$seed"; freqHz = b + 1; scale = "scale$seed"; chord = "chord$seed"
        gain = b + 2; legato = b + 3; velocity = b + 4; postGain = b + 5
        bank = "bank$seed"; sound = SoundValue.Named("snd$seed"); soundIndex = seed + 6
        oscParams = mapOf("k$seed" to b + 7)
        attack = b + 8; decay = b + 9; sustain = b + 10; release = b + 11
        attackCurve = AdsrCurve.Linear; decayCurve = AdsrCurve.Square; releaseCurve = AdsrCurve.Cube
        accelerate = b + 12; vibrato = b + 13; vibratoMod = b + 14
        pAttack = b + 15; pDecay = b + 16; pRelease = b + 17; pEnv = b + 18; pCurve = b + 19; pAnchor = b + 20
        fmh = b + 21; fmAttack = b + 22; fmDecay = b + 23; fmSustain = b + 24; fmEnv = b + 25
        distort = b + 26; distortShape = "ds$seed"; distortOversample = seed + 27
        coarse = b + 28; coarseOversample = seed + 29; crush = b + 30; crushOversample = seed + 31
        phaserRate = b + 32; phaserDepth = b + 33; phaserCenter = b + 34; phaserSweep = b + 35
        tremoloSync = b + 36; tremoloDepth = b + 37; tremoloSkew = b + 38; tremoloPhase = b + 39
        tremoloShape = "ts$seed"
        duckCylinder = seed + 40; duckAttack = b + 41; duckDepth = b + 42
        cutoff = b + 43; resonance = b + 44; hcutoff = b + 45; hresonance = b + 46
        bandf = b + 47; bandq = b + 48; notchf = b + 49; nresonance = b + 50
        lpattack = b + 51; lpdecay = b + 52; lpsustain = b + 53; lprelease = b + 54; lpenv = b + 55
        hpattack = b + 56; hpdecay = b + 57; hpsustain = b + 58; hprelease = b + 59; hpenv = b + 60
        bpattack = b + 61; bpdecay = b + 62; bpsustain = b + 63; bprelease = b + 64; bpenv = b + 65
        nfattack = b + 66; nfdecay = b + 67; nfsustain = b + 68; nfrelease = b + 69; nfenv = b + 70
        cylinder = seed + 71; pan = b + 72
        delay = b + 73; delayTime = b + 74; delayFeedback = b + 75
        room = b + 76; roomSize = b + 77; roomFade = b + 78; roomLp = b + 79; roomDim = b + 80
        iResponse = "ir$seed"
        begin = b + 81; end = b + 82; speed = b + 83; unit = "u$seed"; loop = true; cut = seed + 84
        loopBegin = b + 85; loopEnd = b + 86
        vowel = "v$seed"; compressor = "comp$seed"; solo = b + 88; patternId = "pid$seed"; pipeline = PipelineValue.Named("eng$seed")
        value = SprudelVoiceValue.Num(b + 87)
    }
}
