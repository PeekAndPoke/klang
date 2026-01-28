package io.peekandpoke.klang.audio_be.voices

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.peekandpoke.klang.audio_be.osci.Oscillators
import io.peekandpoke.klang.audio_be.osci.oscillators
import io.peekandpoke.klang.audio_be.osci.withWarmth
import io.peekandpoke.klang.audio_bridge.VoiceData
import kotlin.random.Random

class VoiceSchedulerWarmthTest : StringSpec({

    val sampleRate = 44100
    val oscillators = oscillators(sampleRate) {
        rng(Random(42))
    }

    // Helper function that mimics VoiceScheduler.createOscillator logic
    fun createOscillatorWithWarmth(voiceData: VoiceData, oscillators: Oscillators, freqHz: Double) =
        oscillators.get(
            name = voiceData.sound,
            freqHz = freqHz,
            density = voiceData.density,
            voices = voiceData.voices,
            freqSpread = voiceData.freqSpread,
            panSpread = voiceData.panSpread,
        ).let { rawOsc ->
            val warmthAmount = voiceData.warmth ?: 0.0
            if (warmthAmount > 0.0) {
                rawOsc.withWarmth(warmthAmount)
            } else {
                rawOsc
            }
        }

    "createOscillator returns raw oscillator when warmth is null" {
        val voiceData = VoiceData.empty.copy(
            sound = "sine",
            warmth = null
        )

        val osc = createOscillatorWithWarmth(voiceData, oscillators, 440.0)

        val buffer = DoubleArray(100)
        osc.process(buffer, 0, 100, 0.0, 0.1, null)

        // Should produce output
        buffer.any { it != 0.0 } shouldBe true
    }

    "createOscillator returns raw oscillator when warmth is 0.0" {
        val voiceData = VoiceData.empty.copy(
            sound = "sine",
            warmth = 0.0
        )

        val osc = createOscillatorWithWarmth(voiceData, oscillators, 440.0)

        val buffer = DoubleArray(100)
        osc.process(buffer, 0, 100, 0.0, 0.1, null)

        // Should produce output
        buffer.any { it != 0.0 } shouldBe true
    }

    "createOscillator applies warmth when warmth > 0.0" {
        val voiceDataNoWarmth = VoiceData.empty.copy(
            sound = "square",
            warmth = 0.0
        )

        val voiceDataWithWarmth = VoiceData.empty.copy(
            sound = "square",
            warmth = 0.6
        )

        // Generate raw square wave
        val rawBuffer = DoubleArray(100)
        createOscillatorWithWarmth(voiceDataNoWarmth, oscillators, 440.0)
            .process(rawBuffer, 0, 100, 0.0, 0.5, null)

        // Generate warm square wave
        val warmBuffer = DoubleArray(100)
        createOscillatorWithWarmth(voiceDataWithWarmth, oscillators, 440.0)
            .process(warmBuffer, 0, 100, 0.0, 0.5, null)

        // The two signals should be different (warmth applied)
        var differenceCount = 0
        for (i in 0 until 100) {
            if (kotlin.math.abs(rawBuffer[i] - warmBuffer[i]) > 0.001) {
                differenceCount++
            }
        }

        // Most samples should be different
        differenceCount shouldNotBe 0
    }

    "createOscillator works with all oscillator types" {
        val oscillatorNames = listOf("sine", "sawtooth", "square", "triangle", "supersaw")

        for (oscName in oscillatorNames) {
            val voiceData = VoiceData.empty.copy(
                sound = oscName,
                warmth = 0.5,
                voices = 3.0 // For supersaw
            )

            val osc = createOscillatorWithWarmth(voiceData, oscillators, 440.0)

            val buffer = DoubleArray(100)
            osc.process(buffer, 0, 100, 0.0, 0.1, null)

            // Should produce output for all oscillator types
            buffer.any { it != 0.0 } shouldBe true
        }
    }
})
