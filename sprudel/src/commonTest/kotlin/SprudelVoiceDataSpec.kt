package io.peekandpoke.klang.sprudel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.audio_bridge.AdsrCurve
import io.peekandpoke.klang.audio_bridge.AdsrDef
import io.peekandpoke.klang.audio_bridge.FilterDef
import io.peekandpoke.klang.audio_bridge.SoundValue
import kotlinx.serialization.json.Json

class SprudelVoiceDataSpec : StringSpec({

    "empty companion object has all fields null" {
        val empty = createSprudelVoiceData()

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
        val populated = createSprudelVoiceData(
            note = "n", freqHz = 1.0, scale = "s", chord = "c",
            gain = 2.0, legato = 3.0, velocity = 4.0, postGain = 5.0,
            bank = "b", sound = SoundValue.Named("snd"), soundIndex = 6, oscParams = mapOf("k" to 7.0),
            attack = 8.0, decay = 9.0, sustain = 10.0, release = 11.0,
            attackCurve = AdsrCurve.Linear, decayCurve = AdsrCurve.Square, releaseCurve = AdsrCurve.Cube,
            accelerate = 12.0, vibrato = 13.0, vibratoMod = 14.0,
            pAttack = 15.0, pDecay = 16.0, pRelease = 17.0, pEnv = 18.0, pCurve = 19.0, pAnchor = 20.0,
            fmh = 21.0, fmAttack = 22.0, fmDecay = 23.0, fmSustain = 24.0, fmEnv = 25.0,
            distort = 26.0, distortShape = "ds", distortOversample = 27,
            coarse = 28.0, coarseOversample = 29, crush = 30.0, crushOversample = 31,
            phaserRate = 32.0, phaserDepth = 33.0, phaserCenter = 34.0, phaserSweep = 35.0,
            tremoloSync = 36.0, tremoloDepth = 37.0, tremoloSkew = 38.0, tremoloPhase = 39.0, tremoloShape = "ts",
            duckCylinder = 40, duckAttack = 41.0, duckDepth = 42.0,
            cutoff = 43.0, resonance = 44.0, hcutoff = 45.0, hresonance = 46.0,
            bandf = 47.0, bandq = 48.0, notchf = 49.0, nresonance = 50.0,
            lpattack = 51.0, lpdecay = 52.0, lpsustain = 53.0, lprelease = 54.0, lpenv = 55.0,
            hpattack = 56.0, hpdecay = 57.0, hpsustain = 58.0, hprelease = 59.0, hpenv = 60.0,
            bpattack = 61.0, bpdecay = 62.0, bpsustain = 63.0, bprelease = 64.0, bpenv = 65.0,
            nfattack = 66.0, nfdecay = 67.0, nfsustain = 68.0, nfrelease = 69.0, nfenv = 70.0,
            cylinder = 71, pan = 72.0,
            delay = 73.0, delayTime = 74.0, delayFeedback = 75.0,
            room = 76.0, roomSize = 77.0, roomFade = 78.0, roomLp = 79.0, roomDim = 80.0,
            iResponse = "ir",
            begin = 81.0, end = 82.0, speed = 83.0, unit = "u", loop = true, cut = 84,
            loopBegin = 85.0, loopEnd = 86.0,
            vowel = "v", compressor = "comp", solo = 88.0, patternId = "pid", engine = "eng",
            value = SprudelVoiceValue.Num(87.0),
        )

        val cloned = populated.clone()

        cloned shouldBe populated
        (cloned === populated) shouldBe false
    }

    "can create SprudelVoiceData with basic fields" {
        val data = createSprudelVoiceData(
            note = "c4",
            freqHz = 440.0,
            gain = 0.8
        )

        data.note shouldBe "c4"
        data.freqHz shouldBe 440.0
        data.gain shouldBe 0.8
    }

    "toVoiceData() converts flat ADSR fields to AdsrDef" {
        val data = createSprudelVoiceData(
            attack = 0.01,
            decay = 0.1,
            sustain = 0.7,
            release = 0.3
        )

        val voiceData = data.toVoiceData()

        val adsr = voiceData.adsr as AdsrDef.Std
        adsr.attack shouldBe 0.01
        adsr.decay shouldBe 0.1
        adsr.sustain shouldBe 0.7
        adsr.release shouldBe 0.3
    }

    "toVoiceData() converts LPF flat fields to FilterDef.LowPass" {
        val data = createSprudelVoiceData(
            cutoff = 1000.0,
            resonance = 1.5
        )

        val voiceData = data.toVoiceData()

        voiceData.filters.size shouldBe 1
        val lpf = voiceData.filters[0] as FilterDef.LowPass
        lpf.cutoffHz shouldBe 1000.0
        lpf.q shouldBe 1.5
    }

    "toVoiceData() converts HPF flat fields to FilterDef.HighPass" {
        val data = createSprudelVoiceData(
            hcutoff = 500.0,
            hresonance = 2.0
        )

        val voiceData = data.toVoiceData()

        voiceData.filters.size shouldBe 1
        val hpf = voiceData.filters[0] as FilterDef.HighPass
        hpf.cutoffHz shouldBe 500.0
        hpf.q shouldBe 2.0
    }

    "toVoiceData() converts BPF flat fields to FilterDef.BandPass" {
        val data = createSprudelVoiceData(
            bandf = 750.0,
            bandq = 1.2
        )

        val voiceData = data.toVoiceData()

        voiceData.filters.size shouldBe 1
        val bpf = voiceData.filters[0] as FilterDef.BandPass
        bpf.cutoffHz shouldBe 750.0
        bpf.q shouldBe 1.2
    }

    "toVoiceData() converts Notch flat fields to FilterDef.Notch" {
        val data = createSprudelVoiceData(
            notchf = 600.0,
            nresonance = 0.8
        )

        val voiceData = data.toVoiceData()

        voiceData.filters.size shouldBe 1
        val notch = voiceData.filters[0] as FilterDef.Notch
        notch.cutoffHz shouldBe 600.0
        notch.q shouldBe 0.8
    }

    "toVoiceData() creates multiple filters with independent resonance" {
        val data = createSprudelVoiceData(
            cutoff = 1000.0,
            resonance = 1.5,
            hcutoff = 500.0,
            hresonance = 2.0,
            bandf = 750.0,
            bandq = 1.2
        )

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
        val data = createSprudelVoiceData(
            cutoff = 1000.0,   // LowPass  → must end up LAST
            hcutoff = 500.0,   // HighPass → must end up FIRST
            bandf = 750.0,
            notchf = 600.0,
            vowel = "a",       // Formant
        )

        val voiceData = data.toVoiceData()

        voiceData.filters.size shouldBe 5
        voiceData.filters[0].shouldBeInstanceOf<FilterDef.HighPass>()
        voiceData.filters[1].shouldBeInstanceOf<FilterDef.BandPass>()
        voiceData.filters[2].shouldBeInstanceOf<FilterDef.Notch>()
        voiceData.filters[3].shouldBeInstanceOf<FilterDef.Formant>()
        voiceData.filters[4].shouldBeInstanceOf<FilterDef.LowPass>()
    }

    "toVoiceData() puts LowPass last even when it is the only pair with HighPass" {
        val data = createSprudelVoiceData(cutoff = 1000.0, hcutoff = 80.0)

        val voiceData = data.toVoiceData()

        voiceData.filters.size shouldBe 2
        voiceData.filters[0].shouldBeInstanceOf<FilterDef.HighPass>()
        voiceData.filters[1].shouldBeInstanceOf<FilterDef.LowPass>()
    }

    "toVoiceData() handles null resonance gracefully" {
        val data = createSprudelVoiceData(
            cutoff = 1000.0,
            resonance = null // No resonance specified
        )

        val voiceData = data.toVoiceData()

        voiceData.filters.size shouldBe 1
        val lpf = voiceData.filters[0] as FilterDef.LowPass
        lpf.cutoffHz shouldBe 1000.0
        lpf.q shouldBe 1.0 // defaults to 1.0
    }

    "toVoiceData() maps all basic fields correctly" {
        val data = createSprudelVoiceData(
            note = "c4",
            freqHz = 440.0,
            scale = "major",
            gain = 0.8,
            legato = 0.9,
            bank = "MPC60",
            sound = SoundValue.Named("bd"),
            soundIndex = 2,
            oscParams = mapOf("density" to 0.5, "panSpread" to 0.3, "freqSpread" to 0.1, "voices" to 3.0),
            accelerate = 0.05,
            vibrato = 0.2,
            vibratoMod = 0.4,
            distort = 0.3,
            coarse = 1.0,
            crush = 4.0,
            cylinder = 1,
            pan = 0.5,
            delay = 0.3,
            delayTime = 0.25,
            delayFeedback = 0.5,
            room = 0.7,
            roomSize = 5.0,
            begin = 0.0,
            end = 1.0,
            speed = 1.0,
            loop = true,
            cut = 1
        )

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
        voiceData.oscParams?.get("freqSpread") shouldBe 0.1
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

    "SprudelVoiceData is serializable to JSON" {
        val data = createSprudelVoiceData(
            note = "c4",
            freqHz = 440.0,
            gain = 0.8,
            cutoff = 1000.0,
            resonance = 1.5
        )

        val json = Json.encodeToString(SprudelVoiceData.serializer(), data)

        json shouldNotBe null
        json shouldContain "\"note\":\"c4\""
        json shouldContain "\"freqHz\":440"
        json shouldContain "\"gain\":0.8"
        json shouldContain "\"cutoff\":1000"
        json shouldContain "\"resonance\":1.5"
    }

    "copy() creates new instance with updated fields" {
        val original = createSprudelVoiceData(
            note = "c4",
            gain = 0.8
        )

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
