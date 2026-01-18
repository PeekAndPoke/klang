package io.peekandpoke.klang.audio_be.voices

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.filters.AudioFilter
import io.peekandpoke.klang.audio_be.orbits.Orbits
import io.peekandpoke.klang.audio_bridge.AdsrEnvelope
import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import io.peekandpoke.klang.audio_bridge.SampleMetadata

class SampleVoiceRenderTest : StringSpec({

    val sampleRate = 44100
    val blockFrames = 100

    // Helper to create a dummy context
    fun createCtx(): Voice.RenderContext {
        return Voice.RenderContext(
            orbits = Orbits(blockFrames = blockFrames, sampleRate = sampleRate),
            sampleRate = sampleRate,
            blockFrames = blockFrames,
            voiceBuffer = DoubleArray(blockFrames),
            freqModBuffer = DoubleArray(blockFrames)
        )
    }

    // Helper to create a dummy sample (ramp from 0.0 to 1.0)
    fun createSample(size: Int): MonoSamplePcm {
        val pcm = FloatArray(size) { it.toFloat() / (size - 1) }
        return MonoSamplePcm(
            sampleRate = sampleRate,
            pcm = pcm,
            meta = SampleMetadata(loop = null, adsr = AdsrEnvelope.empty, anchor = 0.0)
        )
    }

    // Helper to create a dummy filter that does nothing
    val dummyFilter = object : AudioFilter {
        override fun process(buffer: DoubleArray, offset: Int, length: Int) {}
    }

    // Helper to create a basic SampleVoice
    fun createVoice(
        sample: MonoSamplePcm,
        playback: SampleVoice.SamplePlayback = SampleVoice.SamplePlayback.default,
        startFrame: Long = 0,
        playhead: Double = 0.0,
        rate: Double = 1.0,
    ): SampleVoice {
        return SampleVoice(
            orbitId = 0,
            startFrame = startFrame,
            endFrame = startFrame + 1000,
            gateEndFrame = startFrame + 1000,
            gain = 1.0,
            pan = 0.0,
            accelerate = Voice.Accelerate(0.0),
            vibrato = Voice.Vibrato(0.0, 0.0),
            filter = dummyFilter,
            envelope = Voice.Envelope(0.0, 0.0, 1.0, 0.0, 1.0), // Always on
            delay = Voice.Delay(0.0, 0.0, 0.0),
            reverb = Voice.Reverb(0.0, 0.0),
            distort = Voice.Distort(0.0),
            crush = Voice.Crush(0.0),
            coarse = Voice.Coarse(0.0),
            samplePlayback = playback,
            sample = sample,
            rate = rate,
            playhead = playhead
        )
    }

//    "render basic playback" {
//        val sampleSize = 10
//        val sample = createSample(sampleSize) // 0.0, 0.11, 0.22, ... 1.0
//        val voice = createVoice(sample)
//        val ctx = createCtx()
//
//        // Render first block
//        voice.render(ctx)
//
//        // Check output: should match sample data for first 10 frames
//        // Since rate is 1.0, we expect exact sample values followed by 0 (since sample ended)
//        for (i in 0 until sampleSize) {
//            val expected = sample.pcm[i].toDouble()
//            ctx.voiceBuffer[i] shouldBe (expected plusOrMinus 0.0001)
//        }
//        // After sample ends, should be 0
//        ctx.voiceBuffer[sampleSize] shouldBe 0.0
//    }

    "render with rate > 1 (faster)" {
        val sampleSize = 10
        val sample = createSample(sampleSize)
        val rate = 2.0
        val voice = createVoice(sample, rate = rate)
        val ctx = createCtx()

        voice.render(ctx)

        // rate 2.0 means we skip every other sample (interpolated)
        // Indices: 0, 2, 4, 6, 8
        for (i in 0 until 5) {
            val expected = sample.pcm[i * 2].toDouble()
            ctx.voiceBuffer[i] shouldBe (expected plusOrMinus 0.0001)
        }
        ctx.voiceBuffer[5] shouldBe 0.0
    }

    "render loop (explicit)" {
        val sampleSize = 10
        val sample = createSample(sampleSize)
        val playback = SampleVoice.SamplePlayback(
            cut = null,
            explicitLooping = true,
            explicitLoopStart = 0.0,
            explicitLoopEnd = 5.0, // Loop first half (0..4)
            stopFrame = Double.MAX_VALUE
        )
        val voice = createVoice(sample, playback = playback)
        val ctx = createCtx()

        voice.render(ctx)

        // Should play 0, 1, 2, 3, 4, then wrap to 0, 1, 2, 3, 4...
        // 0.0, 0.11, 0.22, 0.33, 0.44
        val values = (0 until 10).map { ctx.voiceBuffer[it] }
        val expectedSegment = (0 until 5).map { sample.pcm[it].toDouble() }

        values.subList(0, 5) shouldBe expectedSegment // First pass
        values.subList(5, 10) shouldBe expectedSegment // Loop pass
    }

//    "render stopFrame (end)" {
//        val sampleSize = 10
//        val sample = createSample(sampleSize)
//        val playback = SampleVoice.SamplePlayback(
//            cut = null,
//            explicitLooping = false,
//            explicitLoopStart = -1.0,
//            explicitLoopEnd = -1.0,
//            stopFrame = 5.0 // Stop at index 5 (halfway)
//        )
//        val voice = createVoice(sample, playback = playback)
//        val ctx = createCtx()
//
//        voice.render(ctx)
//
//        // Should play 0, 1, 2, 3, 4
//        // At index 5, playhead >= stopFrame, so it should output 0
//        for (i in 0 until 5) {
//            ctx.voiceBuffer[i] shouldNotBe 0.0
//        }
//        for (i in 5 until 10) {
//            ctx.voiceBuffer[i] shouldBe 0.0
//        }
//    }
})

