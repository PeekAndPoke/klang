package io.peekandpoke.klang.strudel

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import io.peekandpoke.klang.audio_bridge.FilterDef
import kotlinx.serialization.json.Json

class StrudelVoiceDataSpec : StringSpec({

    "empty companion object has all fields null" {
        val empty = StrudelVoiceData.empty

        empty.note shouldBe null
        empty.freqHz shouldBe null
        empty.gain shouldBe null
        empty.attack shouldBe null
        empty.cutoff shouldBe null
        empty.resonance shouldBe null
        empty.value shouldBe null
    }

    "can create StrudelVoiceData with basic fields" {
        val data = StrudelVoiceData.empty.copy(
            note = "c4",
            freqHz = 440.0,
            gain = 0.8
        )

        data.note shouldBe "c4"
        data.freqHz shouldBe 440.0
        data.gain shouldBe 0.8
    }

    "toVoiceData() converts flat ADSR fields to AdsrEnvelope" {
        val data = StrudelVoiceData.empty.copy(
            attack = 0.01,
            decay = 0.1,
            sustain = 0.7,
            release = 0.3
        )

        val voiceData = data.toVoiceData()

        voiceData.adsr.attack shouldBe 0.01
        voiceData.adsr.decay shouldBe 0.1
        voiceData.adsr.sustain shouldBe 0.7
        voiceData.adsr.release shouldBe 0.3
    }

    "toVoiceData() converts LPF flat fields to FilterDef.LowPass" {
        val data = StrudelVoiceData.empty.copy(
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
        val data = StrudelVoiceData.empty.copy(
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
        val data = StrudelVoiceData.empty.copy(
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
        val data = StrudelVoiceData.empty.copy(
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
        val data = StrudelVoiceData.empty.copy(
            cutoff = 1000.0,
            resonance = 1.5,
            hcutoff = 500.0,
            hresonance = 2.0,
            bandf = 750.0,
            bandq = 1.2
        )

        val voiceData = data.toVoiceData()

        voiceData.filters.size shouldBe 3

        val lpf = voiceData.filters[0] as FilterDef.LowPass
        lpf.cutoffHz shouldBe 1000.0
        lpf.q shouldBe 1.5

        val hpf = voiceData.filters[1] as FilterDef.HighPass
        hpf.cutoffHz shouldBe 500.0
        hpf.q shouldBe 2.0

        val bpf = voiceData.filters[2] as FilterDef.BandPass
        bpf.cutoffHz shouldBe 750.0
        bpf.q shouldBe 1.2
    }

    "toVoiceData() handles null resonance gracefully" {
        val data = StrudelVoiceData.empty.copy(
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
        val data = StrudelVoiceData.empty.copy(
            note = "c4",
            freqHz = 440.0,
            scale = "major",
            gain = 0.8,
            legato = 0.9,
            bank = "MPC60",
            sound = "bd",
            soundIndex = 2,
            density = 0.5,
            panSpread = 0.3,
            freqSpread = 0.1,
            voices = 3.0,
            accelerate = 0.05,
            vibrato = 0.2,
            vibratoMod = 0.4,
            distort = 0.3,
            coarse = 1.0,
            crush = 4.0,
            orbit = 1,
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
        voiceData.density shouldBe 0.5
        voiceData.panSpread shouldBe 0.3
        voiceData.freqSpread shouldBe 0.1
        voiceData.voices shouldBe 3.0
        voiceData.accelerate shouldBe 0.05
        voiceData.vibrato shouldBe 0.2
        voiceData.vibratoMod shouldBe 0.4
        voiceData.distort shouldBe 0.3
        voiceData.coarse shouldBe 1.0
        voiceData.crush shouldBe 4.0
        voiceData.orbit shouldBe 1
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

    "StrudelVoiceData is serializable to JSON" {
        val data = StrudelVoiceData.empty.copy(
            note = "c4",
            freqHz = 440.0,
            gain = 0.8,
            cutoff = 1000.0,
            resonance = 1.5
        )

        val json = Json.encodeToString(StrudelVoiceData.serializer(), data)

        json shouldNotBe null
        json shouldContain "\"note\":\"c4\""
        json shouldContain "\"freqHz\":440"
        json shouldContain "\"gain\":0.8"
        json shouldContain "\"cutoff\":1000"
        json shouldContain "\"resonance\":1.5"
    }

    "copy() creates new instance with updated fields" {
        val original = StrudelVoiceData.empty.copy(
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
