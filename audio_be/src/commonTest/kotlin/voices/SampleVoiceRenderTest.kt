package io.peekandpoke.klang.audio_be.voices

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createContext
import io.peekandpoke.klang.audio_be.voices.VoiceTestHelpers.createSampleVoice
import io.peekandpoke.klang.audio_bridge.AdsrEnvelope
import io.peekandpoke.klang.audio_bridge.MonoSamplePcm
import io.peekandpoke.klang.audio_bridge.SampleMetadata

class SampleVoiceRenderTest : StringSpec({

    // Helper to create a dummy sample (ramp from 0.0 to 1.0)
    fun createSample(size: Int): MonoSamplePcm {
        val pcm = AudioBuffer(size) { it.toDouble() / (size - 1) }
        return MonoSamplePcm(
            sampleRate = 44100,
            pcm = pcm,
            meta = SampleMetadata(
                loop = null,
                adsr = AdsrEnvelope.empty,
                anchor = 0.0,
            )
        )
    }

    "render with rate > 1 (faster)" {
        val sampleSize = 10
        val sample = createSample(sampleSize)
        val rate = 2.0
        val voice = createSampleVoice(sample = sample, rate = rate)
        val ctx = createContext()

        voice.render(ctx)

        // rate 2.0 means we skip every other sample (interpolated)
        // Indices: 0, 2, 4, 6, 8
        for (i in 0 until 5) {
            val expected = sample.pcm[i * 2].toDouble()
            ctx.voiceBuffer[i].toDouble() shouldBe (expected plusOrMinus 0.0001)
        }
        ctx.voiceBuffer[5] shouldBe 0.0
    }

    "render loop (explicit)" {
        val sampleSize = 10
        val sample = createSample(sampleSize)
        val voice = createSampleVoice(
            sample = sample,
            isLooping = true,
            loopStart = 0.0,
            loopEnd = 5.0, // Loop first half (0..4)
            stopFrame = Double.MAX_VALUE,
        )
        val ctx = createContext()

        voice.render(ctx)

        // Should play 0, 1, 2, 3, 4, then wrap to 0, 1, 2, 3, 4...
        // 0.0, 0.11, 0.22, 0.33, 0.44
        val values = (0 until 10).map { ctx.voiceBuffer[it].toDouble() }
        val expectedSegment = (0 until 5).map { sample.pcm[it].toDouble() }

        values.subList(0, 5) shouldBe expectedSegment // First pass
        values.subList(5, 10) shouldBe expectedSegment // Loop pass
    }
})
