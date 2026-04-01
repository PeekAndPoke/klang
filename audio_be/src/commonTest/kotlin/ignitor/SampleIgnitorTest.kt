package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe

class SampleIgnitorTest : StringSpec({

    val sampleRate = 44100

    fun createCtx(blockFrames: Int = 128) = IgniteContext(
        sampleRate = sampleRate,
        voiceDurationFrames = sampleRate,
        gateEndFrame = sampleRate,
        releaseFrames = 4410,
        voiceEndFrame = sampleRate + 4410,
        scratchBuffers = ScratchBuffers(blockFrames),
    ).apply {
        offset = 0
        length = blockFrames
        voiceElapsedFrames = 0
    }

    "one-shot playback produces interpolated samples" {
        // Simple ramp: 0.0, 0.25, 0.5, 0.75, 1.0
        val pcm = floatArrayOf(0.0f, 0.25f, 0.5f, 0.75f, 1.0f)

        val gen = SampleIgnitor(
            pcm = pcm,
            rate = 1.0,
            playhead = 0.0,
            loopStart = -1.0,
            loopEnd = -1.0,
            isLooping = false,
            stopFrame = Double.MAX_VALUE,
        )

        val buffer = FloatArray(5)
        val ctx = createCtx(5)
        ctx.length = 5
        gen.generate(buffer, 440.0, ctx)

        // At rate=1.0, playhead lands exactly on integer indices
        buffer[0].toDouble() shouldBe (0.0 plusOrMinus 0.001)
        buffer[1].toDouble() shouldBe (0.25 plusOrMinus 0.001)
        buffer[2].toDouble() shouldBe (0.5 plusOrMinus 0.001)
        buffer[3].toDouble() shouldBe (0.75 plusOrMinus 0.001)
        // Last sample: base=4 >= pcmMax(4), so output is 0
        buffer[4] shouldBe 0.0f
    }

    "half-speed playback interpolates between samples" {
        val pcm = floatArrayOf(0.0f, 1.0f, 0.0f)

        val gen = SampleIgnitor(
            pcm = pcm,
            rate = 0.5,
            playhead = 0.0,
            loopStart = -1.0,
            loopEnd = -1.0,
            isLooping = false,
            stopFrame = Double.MAX_VALUE,
        )

        val buffer = FloatArray(4)
        val ctx = createCtx(4)
        ctx.length = 4
        gen.generate(buffer, 440.0, ctx)

        // playhead: 0.0, 0.5, 1.0, 1.5
        buffer[0].toDouble() shouldBe (0.0 plusOrMinus 0.001)   // pcm[0]
        buffer[1].toDouble() shouldBe (0.5 plusOrMinus 0.001)   // interpolate 0.0 and 1.0
        buffer[2].toDouble() shouldBe (1.0 plusOrMinus 0.001)   // pcm[1]
        buffer[3].toDouble() shouldBe (0.5 plusOrMinus 0.001)   // interpolate 1.0 and 0.0
    }

    "looping wraps playhead correctly" {
        // 4 samples, loop from index 1 to 3
        val pcm = floatArrayOf(0.0f, 0.5f, 1.0f, 0.5f)

        val gen = SampleIgnitor(
            pcm = pcm,
            rate = 1.0,
            playhead = 0.0,
            loopStart = 1.0,
            loopEnd = 3.0,
            isLooping = true,
            stopFrame = Double.MAX_VALUE,
        )

        val buffer = FloatArray(6)
        val ctx = createCtx(6)
        ctx.length = 6
        gen.generate(buffer, 440.0, ctx)

        // playhead: 0, 1, 2, 3->wraps to 1, 2, 3->wraps to 1
        buffer[0].toDouble() shouldBe (0.0 plusOrMinus 0.001)   // pcm[0]
        buffer[1].toDouble() shouldBe (0.5 plusOrMinus 0.001)   // pcm[1]
        buffer[2].toDouble() shouldBe (1.0 plusOrMinus 0.001)   // pcm[2]
        buffer[3].toDouble() shouldBe (0.5 plusOrMinus 0.001)   // wraps to 1 -> pcm[1]
        buffer[4].toDouble() shouldBe (1.0 plusOrMinus 0.001)   // pcm[2]
        buffer[5].toDouble() shouldBe (0.5 plusOrMinus 0.001)   // wraps to 1 -> pcm[1]
    }

    "stopFrame truncates output to silence" {
        val pcm = floatArrayOf(1.0f, 1.0f, 1.0f, 1.0f, 1.0f)

        val gen = SampleIgnitor(
            pcm = pcm,
            rate = 1.0,
            playhead = 0.0,
            loopStart = -1.0,
            loopEnd = -1.0,
            isLooping = false,
            stopFrame = 2.0,
        )

        val buffer = FloatArray(4)
        val ctx = createCtx(4)
        ctx.length = 4
        gen.generate(buffer, 440.0, ctx)

        // First two samples play, rest are silenced by stopFrame
        buffer[0] shouldBe 1.0f
        buffer[1] shouldBe 1.0f
        buffer[2] shouldBe 0.0f
        buffer[3] shouldBe 0.0f
    }

    "phaseMod scales playback rate per sample" {
        val pcm = floatArrayOf(0.0f, 0.5f, 1.0f, 0.5f, 0.0f)

        val gen = SampleIgnitor(
            pcm = pcm,
            rate = 1.0,
            playhead = 0.0,
            loopStart = -1.0,
            loopEnd = -1.0,
            isLooping = false,
            stopFrame = Double.MAX_VALUE,
        )

        // Double speed via phaseMod
        val phaseMod = DoubleArray(4) { 2.0 }

        val buffer = FloatArray(4)
        val ctx = createCtx(4)
        ctx.length = 4
        ctx.phaseMod = phaseMod
        gen.generate(buffer, 440.0, ctx)

        // playhead advances by rate*phaseMod = 1.0*2.0 = 2.0 per sample
        // playhead: 0.0, 2.0, 4.0(>=pcmMax), ...
        buffer[0].toDouble() shouldBe (0.0 plusOrMinus 0.001)   // pcm[0]
        buffer[1].toDouble() shouldBe (1.0 plusOrMinus 0.001)   // pcm[2]
        buffer[2] shouldBe 0.0f                                  // past end
        buffer[3] shouldBe 0.0f                                  // past end
    }

    "empty pcm returns silence" {
        val gen = SampleIgnitor(
            pcm = floatArrayOf(),
            rate = 1.0,
            playhead = 0.0,
            loopStart = -1.0,
            loopEnd = -1.0,
            isLooping = false,
            stopFrame = Double.MAX_VALUE,
        )

        val buffer = FloatArray(4) { 999.0f } // prefill to detect changes
        val ctx = createCtx(4)
        ctx.length = 4
        gen.generate(buffer, 440.0, ctx)

        // pcm.size - 1 = -1, so base >= pcmMax for all => silence
        buffer.all { it == 0.0f } shouldBe true
    }

    "negative playhead outputs silence" {
        val pcm = floatArrayOf(0.5f, 1.0f, 0.5f)

        val gen = SampleIgnitor(
            pcm = pcm,
            rate = 1.0,
            playhead = -2.0,
            loopStart = -1.0,
            loopEnd = -1.0,
            isLooping = false,
            stopFrame = Double.MAX_VALUE,
        )

        val buffer = FloatArray(4) { 999.0f }
        val ctx = createCtx(4)
        ctx.length = 4
        gen.generate(buffer, 440.0, ctx)

        // playhead: -2.0, -1.0, 0.0, 1.0
        buffer[0] shouldBe 0.0f    // negative -> silence
        buffer[1] shouldBe 0.0f    // negative -> silence
        buffer[2].toDouble() shouldBe (0.5 plusOrMinus 0.001)  // pcm[0]
        buffer[3].toDouble() shouldBe (1.0 plusOrMinus 0.001)  // pcm[1]
    }

    "fractional negative playhead outputs silence, not extrapolation" {
        val pcm = floatArrayOf(0.5f, 1.0f, 0.5f)

        val gen = SampleIgnitor(
            pcm = pcm,
            rate = 0.5,
            playhead = -0.5,
            loopStart = -1.0,
            loopEnd = -1.0,
            isLooping = false,
            stopFrame = Double.MAX_VALUE,
        )

        val buffer = FloatArray(3) { 999.0f }
        val ctx = createCtx(3)
        ctx.length = 3
        gen.generate(buffer, 440.0, ctx)

        // playhead: -0.5, 0.0, 0.5
        buffer[0] shouldBe 0.0f    // fractional negative -> silence (not extrapolation)
        buffer[1].toDouble() shouldBe (0.5 plusOrMinus 0.001)   // pcm[0]
        buffer[2].toDouble() shouldBe (0.75 plusOrMinus 0.001)  // interpolate pcm[0] and pcm[1]
    }

    "loop wrap handles large rate overshoots" {
        // Loop from 0 to 4, but rate=10 jumps way past loopEnd
        val pcm = floatArrayOf(0.0f, 0.25f, 0.5f, 0.75f, 1.0f, 0.0f, 0.0f, 0.0f)

        val gen = SampleIgnitor(
            pcm = pcm,
            rate = 10.0,
            playhead = 0.0,
            loopStart = 0.0,
            loopEnd = 4.0,
            isLooping = true,
            stopFrame = Double.MAX_VALUE,
        )

        val buffer = FloatArray(3)
        val ctx = createCtx(3)
        ctx.length = 3
        gen.generate(buffer, 440.0, ctx)

        // playhead: 0.0 -> advance 10 -> 10.0 -> wrap to 10%4=2.0 -> advance 10 -> 12.0 -> wrap to 12%4=0.0
        buffer[0].toDouble() shouldBe (0.0 plusOrMinus 0.001)   // ph=0 -> pcm[0]
        buffer[1].toDouble() shouldBe (0.5 plusOrMinus 0.001)   // ph=10 wraps to 2 -> pcm[2]
        buffer[2].toDouble() shouldBe (0.0 plusOrMinus 0.001)   // ph=12 wraps to 0 -> pcm[0]
    }

    "freqHz is ignored" {
        val pcm = floatArrayOf(0.0f, 1.0f, 0.0f)

        val gen1 = SampleIgnitor(pcm, 1.0, 0.0, -1.0, -1.0, false, Double.MAX_VALUE)
        val gen2 = SampleIgnitor(pcm, 1.0, 0.0, -1.0, -1.0, false, Double.MAX_VALUE)

        val buf1 = FloatArray(3)
        val buf2 = FloatArray(3)
        val ctx1 = createCtx(3).apply { length = 3 }
        val ctx2 = createCtx(3).apply { length = 3 }

        gen1.generate(buf1, 440.0, ctx1)
        gen2.generate(buf2, 880.0, ctx2)

        // Output should be identical regardless of freqHz
        for (i in buf1.indices) {
            buf1[i] shouldBe buf2[i]
        }
    }
})
