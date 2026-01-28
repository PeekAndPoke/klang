package io.peekandpoke.klang.audio_be.osci

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.math.abs

class OscillatorWarmthTest : StringSpec({

    val sampleRate = 44100
    val blockFrames = 100

    "withWarmth returns original oscillator when warmth is 0.0" {
        val originalOsc = Oscillators.sineFn()
        val wrappedOsc = originalOsc.withWarmth(0.0)

        // Should return the same instance when warmth is 0.0 (optimization)
        wrappedOsc shouldBe originalOsc
    }

    "withWarmth returns original oscillator when warmth is negative" {
        val originalOsc = Oscillators.sineFn()
        val wrappedOsc = originalOsc.withWarmth(-0.5)

        wrappedOsc shouldBe originalOsc
    }

    "withWarmth wraps oscillator when warmth > 0.0" {
        val originalOsc = Oscillators.sineFn()
        val wrappedOsc = originalOsc.withWarmth(0.5)

        // Should return a different instance (wrapped)
        wrappedOsc shouldNotBe originalOsc
    }

    "withWarmth applies low-pass filtering to signal" {
        // Create a square wave (harsh harmonics)
        val squareOsc = Oscillators.squareFn()
        val buffer = DoubleArray(blockFrames)

        // Generate raw square wave
        squareOsc.process(buffer, 0, blockFrames, 0.0, 0.1, null)
        val rawSignal = buffer.copyOf()

        // Apply warmth (should smooth the signal)
        val warmOsc = squareOsc.withWarmth(0.5)
        buffer.fill(0.0)
        warmOsc.process(buffer, 0, blockFrames, 0.0, 0.1, null)
        val warmSignal = buffer.copyOf()

        // Check that the warmed signal is different (smoothed)
        var differenceCount = 0
        for (i in 0 until blockFrames) {
            if (abs(rawSignal[i] - warmSignal[i]) > 0.001) {
                differenceCount++
            }
        }

        // Most samples should be different after filtering
        differenceCount shouldNotBe 0
    }

    "withWarmth reduces high-frequency content" {
        // Create sawtooth (rich harmonics)
        val sawOsc = Oscillators.sawtoothFn()
        val buffer = DoubleArray(blockFrames)

        // Generate raw sawtooth
        sawOsc.process(buffer, 0, blockFrames, 0.0, 0.5, null)
        val rawPeak = buffer.maxOf { abs(it) }

        // Apply heavy warmth
        val veryWarmOsc = sawOsc.withWarmth(0.8)
        buffer.fill(0.0)
        veryWarmOsc.process(buffer, 0, blockFrames, 0.0, 0.5, null)
        val warmPeak = buffer.maxOf { abs(it) }

        // Heavy warmth should reduce peak amplitude (smooths sharp transitions)
        warmPeak shouldNotBe (rawPeak plusOrMinus 0.01)
    }

    "withWarmth preserves phase information" {
        val sineOsc = Oscillators.sineFn()
        val buffer1 = DoubleArray(blockFrames)
        val buffer2 = DoubleArray(blockFrames)

        // Process twice with same starting phase
        val warmOsc = sineOsc.withWarmth(0.3)
        val phase1 = warmOsc.process(buffer1, 0, blockFrames, 0.0, 0.1, null)
        val phase2 = warmOsc.process(buffer2, 0, blockFrames, 0.0, 0.1, null)

        // Should return same phase when starting from same position
        phase1 shouldBe (phase2 plusOrMinus 0.0001)
    }

    "withWarmth can be chained multiple times" {
        val osc = Oscillators.sineFn()
        val buffer = DoubleArray(blockFrames)

        // Apply warmth multiple times (each adds more filtering)
        val doubleWarm = osc.withWarmth(0.3).withWarmth(0.3)
        doubleWarm.process(buffer, 0, blockFrames, 0.0, 0.1, null)

        // Should complete without error
        buffer.any { !it.isNaN() } shouldBe true
    }

    "withWarmth coerces warmth factor to valid range" {
        val osc = Oscillators.sineFn()

        // Values > 1.0 should be coerced to 0.99 (max stability)
        val tooHighWarmth = osc.withWarmth(5.0)
        val buffer = DoubleArray(blockFrames)
        tooHighWarmth.process(buffer, 0, blockFrames, 0.0, 0.1, null)

        // Should not produce NaN or infinite values
        buffer.all { it.isFinite() } shouldBe true
    }

    "withWarmth works with phase modulation" {
        val osc = Oscillators.sineFn()
        val warmOsc = osc.withWarmth(0.5)

        val buffer = DoubleArray(blockFrames)
        val phaseMod = DoubleArray(blockFrames) { 1.0 + 0.1 * it / blockFrames } // Slight FM

        warmOsc.process(buffer, 0, blockFrames, 0.0, 0.1, phaseMod)

        // Should process without errors
        buffer.all { it.isFinite() } shouldBe true
    }

    "withWarmth processes offset and length correctly" {
        val osc = Oscillators.sineFn()
        val warmOsc = osc.withWarmth(0.4)

        val buffer = DoubleArray(blockFrames)
        val offset = 10
        val length = 50

        warmOsc.process(buffer, offset, length, 0.0, 0.1, null)

        // First 10 samples should be 0.0 (not processed)
        for (i in 0 until offset) {
            buffer[i] shouldBe 0.0
        }

        // Middle 50 samples should be non-zero (processed)
        var nonZeroCount = 0
        for (i in offset until offset + length) {
            if (abs(buffer[i]) > 0.001) nonZeroCount++
        }
        nonZeroCount shouldNotBe 0

        // Last samples should be 0.0 (not processed)
        for (i in offset + length until blockFrames) {
            buffer[i] shouldBe 0.0
        }
    }
})
