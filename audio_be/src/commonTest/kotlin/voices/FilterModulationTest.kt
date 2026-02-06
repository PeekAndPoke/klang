package io.peekandpoke.klang.audio_be.voices

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.filters.AudioFilter
import io.peekandpoke.klang.audio_be.orbits.Orbits
import io.peekandpoke.klang.audio_bridge.AdsrEnvelope
import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import io.peekandpoke.klang.audio_bridge.SampleMetadata

class FilterModulationTest : StringSpec({

    val sampleRate = 44100
    val blockFrames = 100

    // Helper to create a dummy context
    fun createCtx(blockStart: Long = 0): Voice.RenderContext {
        return Voice.RenderContext(
            orbits = Orbits(blockFrames = blockFrames, sampleRate = sampleRate),
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            voiceBuffer = DoubleArray(blockFrames),
            freqModBuffer = DoubleArray(blockFrames)
        ).apply {
            this.blockStart = blockStart
        }
    }

    // Spy filter that tracks setCutoff calls
    class SpyFilter : AudioFilter, AudioFilter.Tunable {
        val cutoffHistory = mutableListOf<Double>()
        var currentCutoff = 0.0

        override fun setCutoff(cutoffHz: Double) {
            currentCutoff = cutoffHz
            cutoffHistory.add(cutoffHz)
        }

        override fun process(buffer: DoubleArray, offset: Int, length: Int) {
            // No-op
        }

        fun reset() {
            cutoffHistory.clear()
            currentCutoff = 0.0
        }
    }

    // Helper to create a dummy sample
    fun createSample(): MonoSamplePcm {
        val pcm = FloatArray(100) { 0.5f }
        return MonoSamplePcm(
            sampleRate = sampleRate,
            pcm = pcm,
            meta = SampleMetadata(loop = null, adsr = AdsrEnvelope.empty, anchor = 0.0)
        )
    }

    "filter without modulator is not modified" {
        val spyFilter = SpyFilter()
        val voice = SynthVoice(
            orbitId = 0,
            startFrame = 0,
            endFrame = 1000,
            gateEndFrame = 1000,
            gain = 1.0,
            pan = 0.5,
            accelerate = Voice.Accelerate(0.0),
            vibrato = Voice.Vibrato(0.0, 0.0),
            pitchEnvelope = null,
            filter = spyFilter,
            envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0),
            filterModulators = emptyList(), // No modulators
            delay = Voice.Delay(0.0, 0.0, 0.0),
            reverb = Voice.Reverb(0.0, 0.0),
            phaser = Voice.Phaser(0.0, 0.0, 0.0, 0.0),
            tremolo = Voice.Tremolo(0.0, 0.0, 0.0, 0.0, null),
            ducking = null,
            postGain = 1.0,
            compressor = null,
            distort = Voice.Distort(0.0),
            crush = Voice.Crush(0.0),
            coarse = Voice.Coarse(0.0),
            osc = { buffer, offset, length, phase, phaseInc, _ -> phase },
            fm = null,
            freqHz = 440.0,
            phaseInc = 0.1
        )

        val ctx = createCtx()
        voice.render(ctx)

        // No setCutoff should have been called
        spyFilter.cutoffHistory.size shouldBe 0
    }

    "filter with modulator - envelope at attack peak" {
        val spyFilter = SpyFilter()
        val baseCutoff = 1000.0
        val depth = 1.0 // 100% modulation

        val modulator = Voice.FilterModulator(
            filter = spyFilter,
            envelope = Voice.Envelope(
                attackFrames = 100.0,
                decayFrames = 0.0,
                sustainLevel = 1.0,
                releaseFrames = 0.0
            ),
            depth = depth,
            baseCutoff = baseCutoff
        )

        val voice = SynthVoice(
            orbitId = 0,
            startFrame = 0,
            endFrame = 1000,
            gateEndFrame = 1000,
            gain = 1.0,
            pan = 0.5,
            accelerate = Voice.Accelerate(0.0),
            vibrato = Voice.Vibrato(0.0, 0.0),
            pitchEnvelope = null,
            filter = spyFilter,
            envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0),
            filterModulators = listOf(modulator),
            delay = Voice.Delay(0.0, 0.0, 0.0),
            reverb = Voice.Reverb(0.0, 0.0),
            phaser = Voice.Phaser(0.0, 0.0, 0.0, 0.0),
            tremolo = Voice.Tremolo(0.0, 0.0, 0.0, 0.0, null),
            ducking = null,
            postGain = 1.0,
            compressor = null,
            distort = Voice.Distort(0.0),
            crush = Voice.Crush(0.0),
            coarse = Voice.Coarse(0.0),
            osc = { buffer, offset, length, phase, phaseInc, _ -> phase },
            fm = null,
            freqHz = 440.0,
            phaseInc = 0.1
        )

        // Render at the attack peak (frame 100)
        val ctx = createCtx(blockStart = 100)
        voice.render(ctx)

        // At peak of attack (envelope = 1.0):
        // newCutoff = baseCutoff * (1.0 + depth * 1.0) = 1000 * 2.0 = 2000
        spyFilter.cutoffHistory.size shouldBe 1
        spyFilter.currentCutoff shouldBe (2000.0 plusOrMinus 0.1)
    }

    "filter with modulator - envelope at start (attack beginning)" {
        val spyFilter = SpyFilter()
        val baseCutoff = 1000.0
        val depth = 1.0

        val modulator = Voice.FilterModulator(
            filter = spyFilter,
            envelope = Voice.Envelope(
                attackFrames = 1000.0,
                decayFrames = 0.0,
                sustainLevel = 1.0,
                releaseFrames = 0.0
            ),
            depth = depth,
            baseCutoff = baseCutoff
        )

        val voice = SynthVoice(
            orbitId = 0,
            startFrame = 0,
            endFrame = 2000,
            gateEndFrame = 2000,
            gain = 1.0,
            pan = 0.5,
            accelerate = Voice.Accelerate(0.0),
            vibrato = Voice.Vibrato(0.0, 0.0),
            pitchEnvelope = null,
            filter = spyFilter,
            envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0),
            filterModulators = listOf(modulator),
            delay = Voice.Delay(0.0, 0.0, 0.0),
            reverb = Voice.Reverb(0.0, 0.0),
            phaser = Voice.Phaser(0.0, 0.0, 0.0, 0.0),
            tremolo = Voice.Tremolo(0.0, 0.0, 0.0, 0.0, null),
            ducking = null,
            postGain = 1.0,
            compressor = null,
            distort = Voice.Distort(0.0),
            crush = Voice.Crush(0.0),
            coarse = Voice.Coarse(0.0),
            osc = { buffer, offset, length, phase, phaseInc, _ -> phase },
            fm = null,
            freqHz = 440.0,
            phaseInc = 0.1
        )

        // Render at start (frame 0)
        val ctx = createCtx(blockStart = 0)
        voice.render(ctx)

        // At start of attack (envelope = 0.0):
        // newCutoff = baseCutoff * (1.0 + depth * 0.0) = 1000 * 1.0 = 1000
        spyFilter.cutoffHistory.size shouldBe 1
        spyFilter.currentCutoff shouldBe (1000.0 plusOrMinus 0.1)
    }

    "filter with modulator - envelope at sustain" {
        val spyFilter = SpyFilter()
        val baseCutoff = 1000.0
        val depth = 0.5 // 50% modulation

        val modulator = Voice.FilterModulator(
            filter = spyFilter,
            envelope = Voice.Envelope(
                attackFrames = 100.0,
                decayFrames = 100.0,
                sustainLevel = 0.5,
                releaseFrames = 100.0
            ),
            depth = depth,
            baseCutoff = baseCutoff
        )

        val voice = SynthVoice(
            orbitId = 0,
            startFrame = 0,
            endFrame = 1000,
            gateEndFrame = 500,
            gain = 1.0,
            pan = 0.5,
            accelerate = Voice.Accelerate(0.0),
            vibrato = Voice.Vibrato(0.0, 0.0),
            pitchEnvelope = null,
            filter = spyFilter,
            envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0),
            filterModulators = listOf(modulator),
            delay = Voice.Delay(0.0, 0.0, 0.0),
            reverb = Voice.Reverb(0.0, 0.0),
            phaser = Voice.Phaser(0.0, 0.0, 0.0, 0.0),
            tremolo = Voice.Tremolo(0.0, 0.0, 0.0, 0.0, null),
            ducking = null,
            postGain = 1.0,
            compressor = null,
            distort = Voice.Distort(0.0),
            crush = Voice.Crush(0.0),
            coarse = Voice.Coarse(0.0),
            osc = { buffer, offset, length, phase, phaseInc, _ -> phase },
            fm = null,
            freqHz = 440.0,
            phaseInc = 0.1
        )

        // Render at sustain phase (frame 300 = after attack 100 + decay 100)
        val ctx = createCtx(blockStart = 300)
        voice.render(ctx)

        // At sustain (envelope = 0.5):
        // newCutoff = baseCutoff * (1.0 + depth * 0.5) = 1000 * (1.0 + 0.25) = 1250
        spyFilter.cutoffHistory.size shouldBe 1
        spyFilter.currentCutoff shouldBe (1250.0 plusOrMinus 0.1)
    }

    "multiple modulators apply independently" {
        val spyFilter1 = SpyFilter()
        val spyFilter2 = SpyFilter()
        val baseCutoff1 = 1000.0
        val baseCutoff2 = 2000.0

        val modulator1 = Voice.FilterModulator(
            filter = spyFilter1,
            envelope = Voice.Envelope(
                attackFrames = 100.0,
                decayFrames = 0.0,
                sustainLevel = 1.0,
                releaseFrames = 0.0
            ),
            depth = 1.0, // 100% modulation
            baseCutoff = baseCutoff1
        )

        val modulator2 = Voice.FilterModulator(
            filter = spyFilter2,
            envelope = Voice.Envelope(
                attackFrames = 100.0,
                decayFrames = 0.0,
                sustainLevel = 1.0,
                releaseFrames = 0.0
            ),
            depth = 0.5, // 50% modulation
            baseCutoff = baseCutoff2
        )

        val voice = SynthVoice(
            orbitId = 0,
            startFrame = 0,
            endFrame = 1000,
            gateEndFrame = 1000,
            gain = 1.0,
            pan = 0.5,
            accelerate = Voice.Accelerate(0.0),
            vibrato = Voice.Vibrato(0.0, 0.0),
            pitchEnvelope = null,
            filter = spyFilter1, // Combined filter not important for this test
            envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0),
            filterModulators = listOf(modulator1, modulator2),
            delay = Voice.Delay(0.0, 0.0, 0.0),
            reverb = Voice.Reverb(0.0, 0.0),
            phaser = Voice.Phaser(0.0, 0.0, 0.0, 0.0),
            tremolo = Voice.Tremolo(0.0, 0.0, 0.0, 0.0, null),
            ducking = null,
            postGain = 1.0,
            compressor = null,
            distort = Voice.Distort(0.0),
            crush = Voice.Crush(0.0),
            coarse = Voice.Coarse(0.0),
            osc = { buffer, offset, length, phase, phaseInc, _ -> phase },
            fm = null,
            freqHz = 440.0,
            phaseInc = 0.1
        )

        // Render at attack peak (frame 100)
        val ctx = createCtx(blockStart = 100)
        voice.render(ctx)

        // Filter 1: envelope = 1.0, depth = 1.0
        // newCutoff = 1000 * (1.0 + 1.0 * 1.0) = 2000
        spyFilter1.currentCutoff shouldBe (2000.0 plusOrMinus 0.1)

        // Filter 2: envelope = 1.0, depth = 0.5
        // newCutoff = 2000 * (1.0 + 0.5 * 1.0) = 3000
        spyFilter2.currentCutoff shouldBe (3000.0 plusOrMinus 0.1)
    }

    "modulation works in SampleVoice too" {
        val spyFilter = SpyFilter()
        val baseCutoff = 500.0
        val depth = 1.0

        val modulator = Voice.FilterModulator(
            filter = spyFilter,
            envelope = Voice.Envelope(
                attackFrames = 100.0,
                decayFrames = 0.0,
                sustainLevel = 1.0,
                releaseFrames = 0.0
            ),
            depth = depth,
            baseCutoff = baseCutoff
        )

        val voice = SampleVoice(
            orbitId = 0,
            startFrame = 0,
            endFrame = 1000,
            gateEndFrame = 1000,
            gain = 1.0,
            pan = 0.5,
            accelerate = Voice.Accelerate(0.0),
            vibrato = Voice.Vibrato(0.0, 0.0),
            pitchEnvelope = null,
            filter = spyFilter,
            envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0),
            filterModulators = listOf(modulator),
            delay = Voice.Delay(0.0, 0.0, 0.0),
            reverb = Voice.Reverb(0.0, 0.0),
            phaser = Voice.Phaser(0.0, 0.0, 0.0, 0.0),
            tremolo = Voice.Tremolo(0.0, 0.0, 0.0, 0.0, null),
            ducking = null,
            postGain = 1.0,
            compressor = null,
            distort = Voice.Distort(0.0),
            crush = Voice.Crush(0.0),
            coarse = Voice.Coarse(0.0),
            fm = null,
            samplePlayback = SampleVoice.SamplePlayback.default,
            sample = createSample(),
            rate = 1.0,
            playhead = 0.0
        )

        // Render at attack peak
        val ctx = createCtx(blockStart = 100)
        voice.render(ctx)

        // At peak (envelope = 1.0):
        // newCutoff = 500 * (1.0 + 1.0 * 1.0) = 1000
        spyFilter.cutoffHistory.size shouldBe 1
        spyFilter.currentCutoff shouldBe (1000.0 plusOrMinus 0.1)
    }

    "modulation called once per render (control rate)" {
        val spyFilter = SpyFilter()
        val modulator = Voice.FilterModulator(
            filter = spyFilter,
            envelope = Voice.Envelope(100.0, 0.0, 1.0, 0.0),
            depth = 1.0,
            baseCutoff = 1000.0
        )

        val voice = SynthVoice(
            orbitId = 0,
            startFrame = 0,
            endFrame = 1000,
            gateEndFrame = 1000,
            gain = 1.0,
            pan = 0.5,
            accelerate = Voice.Accelerate(0.0),
            vibrato = Voice.Vibrato(0.0, 0.0),
            pitchEnvelope = null,
            filter = spyFilter,
            envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0),
            filterModulators = listOf(modulator),
            delay = Voice.Delay(0.0, 0.0, 0.0),
            reverb = Voice.Reverb(0.0, 0.0),
            phaser = Voice.Phaser(0.0, 0.0, 0.0, 0.0),
            tremolo = Voice.Tremolo(0.0, 0.0, 0.0, 0.0, null),
            ducking = null,
            postGain = 1.0,
            compressor = null,
            distort = Voice.Distort(0.0),
            crush = Voice.Crush(0.0),
            coarse = Voice.Coarse(0.0),
            osc = { buffer, offset, length, phase, phaseInc, _ -> phase },
            fm = null,
            freqHz = 440.0,
            phaseInc = 0.1
        )

        val ctx = createCtx(blockStart = 0)
        voice.render(ctx)

        // Should be called exactly once per render (control rate)
        spyFilter.cutoffHistory.size shouldBe 1

        // Render again
        spyFilter.reset()
        voice.render(ctx)
        spyFilter.cutoffHistory.size shouldBe 1
    }

    "voice starting mid-block handles envelope correctly" {
        val spyFilter = SpyFilter()
        val baseCutoff = 1000.0
        val depth = 1.0

        val modulator = Voice.FilterModulator(
            filter = spyFilter,
            envelope = Voice.Envelope(
                attackFrames = 100.0,
                decayFrames = 0.0,
                sustainLevel = 1.0,
                releaseFrames = 0.0
            ),
            depth = depth,
            baseCutoff = baseCutoff
        )

        // Voice starts at frame 100
        val voice = SynthVoice(
            orbitId = 0,
            startFrame = 100,
            endFrame = 1000,
            gateEndFrame = 1000,
            gain = 1.0,
            pan = 0.5,
            accelerate = Voice.Accelerate(0.0),
            vibrato = Voice.Vibrato(0.0, 0.0),
            pitchEnvelope = null,
            filter = spyFilter,
            envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0),
            filterModulators = listOf(modulator),
            delay = Voice.Delay(0.0, 0.0, 0.0),
            reverb = Voice.Reverb(0.0, 0.0),
            phaser = Voice.Phaser(0.0, 0.0, 0.0, 0.0),
            tremolo = Voice.Tremolo(0.0, 0.0, 0.0, 0.0, null),
            ducking = null,
            postGain = 1.0,
            compressor = null,
            distort = Voice.Distort(0.0),
            crush = Voice.Crush(0.0),
            coarse = Voice.Coarse(0.0),
            osc = { buffer, offset, length, phase, phaseInc, _ -> phase },
            fm = null,
            freqHz = 440.0,
            phaseInc = 0.1
        )

        // Render at frame 50 (block starts before voice starts)
        // Voice starts at frame 100, so it's at the beginning of its envelope
        val ctx = createCtx(blockStart = 50)
        voice.render(ctx)

        // Envelope should be at start (position 0):
        // newCutoff = baseCutoff * (1.0 + depth * 0.0) = 1000
        spyFilter.cutoffHistory.size shouldBe 1
        spyFilter.currentCutoff shouldBe (1000.0 plusOrMinus 0.1)
    }

    "envelope at release phase" {
        val spyFilter = SpyFilter()
        val baseCutoff = 1000.0
        val depth = 1.0

        val modulator = Voice.FilterModulator(
            filter = spyFilter,
            envelope = Voice.Envelope(
                attackFrames = 100.0,
                decayFrames = 100.0,
                sustainLevel = 0.5,
                releaseFrames = 200.0
            ),
            depth = depth,
            baseCutoff = baseCutoff
        )

        val voice = SynthVoice(
            orbitId = 0,
            startFrame = 0,
            endFrame = 1000,
            gateEndFrame = 300, // Gate ends at 300
            gain = 1.0,
            pan = 0.5,
            accelerate = Voice.Accelerate(0.0),
            vibrato = Voice.Vibrato(0.0, 0.0),
            pitchEnvelope = null,
            filter = spyFilter,
            envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0),
            filterModulators = listOf(modulator),
            delay = Voice.Delay(0.0, 0.0, 0.0),
            reverb = Voice.Reverb(0.0, 0.0),
            phaser = Voice.Phaser(0.0, 0.0, 0.0, 0.0),
            tremolo = Voice.Tremolo(0.0, 0.0, 0.0, 0.0, null),
            ducking = null,
            postGain = 1.0,
            compressor = null,
            distort = Voice.Distort(0.0),
            crush = Voice.Crush(0.0),
            coarse = Voice.Coarse(0.0),
            osc = { buffer, offset, length, phase, phaseInc, _ -> phase },
            fm = null,
            freqHz = 440.0,
            phaseInc = 0.1
        )

        // Render at start of release (frame 300)
        val ctx1 = createCtx(blockStart = 300)
        voice.render(ctx1)

        // At start of release: envelope = sustainLevel = 0.5
        // newCutoff = 1000 * (1.0 + 1.0 * 0.5) = 1500
        spyFilter.currentCutoff shouldBe (1500.0 plusOrMinus 0.1)

        // Render halfway through release (frame 400 = gateEnd + 100 of 200 release)
        spyFilter.reset()
        val ctx2 = createCtx(blockStart = 400)
        voice.render(ctx2)

        // Halfway through release: envelope = 0.5 - (100/200 * 0.5) = 0.25
        // newCutoff = 1000 * (1.0 + 1.0 * 0.25) = 1250
        spyFilter.currentCutoff shouldBe (1250.0 plusOrMinus 1.0)
    }
})
