package io.peekandpoke.klang.audio_be.exciter

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.math.abs
import kotlin.math.sqrt
import io.kotest.matchers.ints.shouldBeGreaterThan as intShouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan as intShouldBeLessThan

/**
 * Tests for Exciter combinator functions: effects, filters, envelopes, pitch mod, and FM.
 *
 * Each test creates a base oscillator, applies a combinator, generates output,
 * and verifies the expected audio behavior.
 */
class ExciterCombinatorsSpec : StringSpec({

    val sampleRate = 44100
    val defaultBlockFrames = 4410 // 100ms

    fun createCtx(blockFrames: Int = defaultBlockFrames): ExciteContext {
        return ExciteContext(
            sampleRate = sampleRate,
            voiceDurationFrames = blockFrames,
            gateEndFrame = blockFrames,
            releaseFrames = 0,
            voiceEndFrame = blockFrames,
            scratchBuffers = ScratchBuffers(blockFrames),
        ).apply {
            offset = 0
            length = blockFrames
            voiceElapsedFrames = 0
        }
    }

    fun generate(
        sig: Exciter,
        freqHz: Double = 440.0,
        blockFrames: Int = defaultBlockFrames,
        ctx: ExciteContext? = null,
    ): FloatArray {
        val c = ctx ?: createCtx(blockFrames)
        val buffer = FloatArray(blockFrames)
        sig.generate(buffer, freqHz, c)
        return buffer
    }

    fun FloatArray.rms(): Double {
        var sum = 0.0
        for (sample in this) sum += sample.toDouble() * sample.toDouble()
        return sqrt(sum / size)
    }

    fun FloatArray.peakAmplitude(): Double =
        maxOf((maxOrNull() ?: 0.0f).toDouble(), -(minOrNull() ?: 0.0f).toDouble())

    fun FloatArray.dcOffset(): Double = map { it.toDouble() }.average()

    fun FloatArray.uniqueValues(): Int = toSet().size

    // ═════════════════════════════════════════════════════════════════════════════
    // Effects: distort
    // ═════════════════════════════════════════════════════════════════════════════

    "distort(amount) - output is bounded and louder input gets more distorted" {
        val dry = generate(Exciters.sine())
        val wet = generate(Exciters.sine().distort(0.5))

        // Output should be bounded
        for (sample in wet) {
            abs(sample.toDouble()) shouldBeLessThan 1.5
        }

        // Distortion compresses dynamic range: RMS should increase relative to peak
        val dryRatio = dry.rms() / dry.peakAmplitude()
        val wetRatio = wet.rms() / wet.peakAmplitude()
        wetRatio shouldBeGreaterThan dryRatio
    }

    "distort(0.0) - passes through unchanged (bypass)" {
        val dry = generate(Exciters.sine())
        val wet = generate(Exciters.sine().distort(0.0))

        for (i in dry.indices) {
            wet[i].toDouble() shouldBe (dry[i].toDouble() plusOrMinus 0.0001)
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Effects: crush
    // ═════════════════════════════════════════════════════════════════════════════

    "crush(amount) - output is quantized (fewer unique values than input)" {
        val dry = generate(Exciters.sine())
        val wet = generate(Exciters.sine().crush(3.0))

        val dryUnique = dry.uniqueValues()
        val wetUnique = wet.uniqueValues()

        // Crushed signal should have significantly fewer unique values
        wetUnique intShouldBeLessThan dryUnique
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Effects: coarse
    // ═════════════════════════════════════════════════════════════════════════════

    "coarse(amount) - output has sample-and-hold staircase pattern" {
        val wet = generate(Exciters.sine().coarse(10.0))

        // In a sample-and-hold signal, consecutive samples are often identical
        var holdCount = 0
        for (i in 1 until wet.size) {
            if (wet[i] == wet[i - 1]) holdCount++
        }

        // With coarse(10.0), roughly 90% of samples should repeat the previous value
        val holdRatio = holdCount.toDouble() / (wet.size - 1)
        holdRatio shouldBeGreaterThan 0.8
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Effects: phaser
    // ═════════════════════════════════════════════════════════════════════════════

    "phaser(rate, depth) - output differs from dry signal" {
        val dry = generate(Exciters.sine())
        val wet = generate(Exciters.sine().phaser(rate = 2.0, depth = 0.5))

        // Phaser should modify the signal
        var differs = false
        for (i in dry.indices) {
            if (abs(dry[i] - wet[i]) > 0.001) {
                differs = true
                break
            }
        }
        differs shouldBe true
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Effects: tremolo
    // ═════════════════════════════════════════════════════════════════════════════

    "tremolo(rate, depth) - output amplitude varies (min < max)" {
        // Use a long block to capture multiple tremolo cycles
        val blockFrames = 44100 // 1 second
        val wet = generate(Exciters.sine().tremolo(rate = 4.0, depth = 1.0), blockFrames = blockFrames)

        // Compute RMS in windows to detect amplitude variation
        val windowSize = 2205 // 50ms windows
        val windowCount = blockFrames / windowSize
        val windowRms = (0 until windowCount).map { w ->
            val start = w * windowSize
            var sum = 0.0
            for (i in start until start + windowSize) {
                sum += wet[i].toDouble() * wet[i].toDouble()
            }
            sqrt(sum / windowSize)
        }

        val minRms = windowRms.min()
        val maxRms = windowRms.max()

        // Tremolo should create amplitude variation
        maxRms shouldBeGreaterThan (minRms * 1.5)
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Effects: dcBlock
    // ═════════════════════════════════════════════════════════════════════════════

    "dcBlock() - removes DC offset from signal" {
        // Create a sine with DC offset by using a custom exciter
        val dcOffsetExciter = Exciter { buffer, freqHz, ctx ->
            Exciters.sine().generate(buffer, freqHz, ctx)
            val end = ctx.offset + ctx.length
            for (i in ctx.offset until end) {
                buffer[i] = buffer[i] + 0.5f // Add DC offset
            }
        }

        val withDc = generate(dcOffsetExciter)
        val blocked = generate(dcOffsetExciter.dcBlock())

        // Original should have DC offset
        abs(withDc.dcOffset()) shouldBeGreaterThan 0.3

        // After DC block, offset should be substantially reduced
        abs(blocked.dcOffset()) shouldBeLessThan abs(withDc.dcOffset()) * 0.5
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Filters: lowpass
    // ═════════════════════════════════════════════════════════════════════════════

    "lowpass(cutoff) - attenuates high-frequency sine" {
        val highFreq = 8000.0
        val cutoff = 1000.0

        val dry = generate(Exciters.sine(), freqHz = highFreq)
        val wet = generate(Exciters.sine().lowpass(cutoff), freqHz = highFreq)

        val dryRms = dry.rms()
        val wetRms = wet.rms()

        // High-frequency signal should be significantly attenuated by lowpass
        wetRms shouldBeLessThan (dryRms * 0.3)
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Filters: highpass
    // ═════════════════════════════════════════════════════════════════════════════

    "highpass(cutoff) - attenuates low-frequency sine" {
        val lowFreq = 100.0
        val cutoff = 2000.0

        val dry = generate(Exciters.sine(), freqHz = lowFreq)
        val wet = generate(Exciters.sine().highpass(cutoff), freqHz = lowFreq)

        val dryRms = dry.rms()
        val wetRms = wet.rms()

        // Low-frequency signal should be significantly attenuated by highpass
        wetRms shouldBeLessThan (dryRms * 0.3)
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Filters: svf LOWPASS
    // ═════════════════════════════════════════════════════════════════════════════

    "svf(LOWPASS, cutoff, q) - attenuates above cutoff" {
        val highFreq = 8000.0
        val cutoff = 1000.0

        val dry = generate(Exciters.sine(), freqHz = highFreq)
        val wet = generate(Exciters.sine().svf(SvfMode.LOWPASS, cutoff, 0.707), freqHz = highFreq)

        val dryRms = dry.rms()
        val wetRms = wet.rms()

        wetRms shouldBeLessThan (dryRms * 0.3)
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Filters: svf BANDPASS
    // ═════════════════════════════════════════════════════════════════════════════

    "svf(BANDPASS, center, q) - passes center frequency, attenuates away from center" {
        val centerFreq = 1000.0
        val q = 5.0

        // Signal at center frequency should pass through
        val atCenter = generate(Exciters.sine().svf(SvfMode.BANDPASS, centerFreq, q), freqHz = centerFreq)
        val rmsAtCenter = atCenter.rms()

        // Signal far from center should be attenuated
        val offCenter = generate(Exciters.sine().svf(SvfMode.BANDPASS, centerFreq, q), freqHz = 8000.0)
        val rmsOffCenter = offCenter.rms()

        rmsAtCenter shouldBeGreaterThan (rmsOffCenter * 2.0)
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Envelopes: adsr
    // ═════════════════════════════════════════════════════════════════════════════

    "adsr(attack, decay, sustain, release) - output starts at 0, ramps up, decays to sustain" {
        val blockFrames = 44100 // 1 second
        val ctx = createCtx(blockFrames)
        val sig = Exciters.sine().adsr(
            attackSec = 0.1,   // 4410 frames
            decaySec = 0.1,    // 4410 frames
            sustainLevel = 0.5,
            releaseSec = 0.1,
        )
        val buf = generate(sig, blockFrames = blockFrames, ctx = ctx)

        // Start should be near zero (attack beginning)
        val startRms = FloatArray(441) { buf[it] }.rms()  // first 10ms
        startRms shouldBeLessThan 0.2

        // Middle of attack (~50ms) should be building up
        val midAttackRms = FloatArray(441) { buf[2205 + it] }.rms()  // 50ms in
        midAttackRms shouldBeGreaterThan startRms

        // After attack+decay (>200ms), should be at sustain level
        val sustainRms = FloatArray(441) { buf[22050 + it] }.rms()  // 500ms in
        // Sustain level is 0.5, sine RMS is ~0.707 * amplitude, so ~0.354
        sustainRms shouldBe (0.354 plusOrMinus 0.05)
    }

    "adsr with instant attack - output starts near full level immediately" {
        val blockFrames = 4410
        val sig = Exciters.sine().adsr(
            attackSec = 0.0,
            decaySec = 0.0,
            sustainLevel = 1.0,
            releaseSec = 0.0,
        )
        val buf = generate(sig, blockFrames = blockFrames)

        // With zero attack, output should be at full level from the start
        // Check RMS of first 10ms
        val startRms = FloatArray(441) { buf[it] }.rms()
        // Sine at gain 1.0 has RMS ~0.707
        startRms shouldBeGreaterThan 0.5
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Pitch Mod: vibrato
    // ═════════════════════════════════════════════════════════════════════════════

    "vibrato(rate, depth) - output differs from plain sine (frequency modulation)" {
        val dry = generate(Exciters.sine())
        val wet = generate(Exciters.sine().vibrato(rate = 5.0, depth = 0.05))

        var differs = false
        for (i in dry.indices) {
            if (abs(dry[i] - wet[i]) > 0.001) {
                differs = true
                break
            }
        }
        differs shouldBe true

        // Output should still be bounded
        wet.peakAmplitude() shouldBeLessThan 1.1
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Pitch Mod: accelerate
    // ═════════════════════════════════════════════════════════════════════════════

    "accelerate(amount) - pitch changes over time" {
        val blockFrames = 44100 // 1 second
        val wet = generate(Exciters.sine().accelerate(2.0), freqHz = 440.0, blockFrames = blockFrames)

        // Count zero crossings in first half vs second half
        fun zeroCrossingsInRange(buf: FloatArray, start: Int, end: Int): Int {
            var count = 0
            for (i in (start + 1) until end) {
                if ((buf[i - 1] >= 0.0 && buf[i] < 0.0) || (buf[i - 1] < 0.0 && buf[i] >= 0.0)) {
                    count++
                }
            }
            return count
        }

        val half = blockFrames / 2
        val crossingsFirstHalf = zeroCrossingsInRange(wet, 0, half)
        val crossingsSecondHalf = zeroCrossingsInRange(wet, half, blockFrames)

        // With positive accelerate, pitch rises over time so second half should have more crossings
        crossingsSecondHalf intShouldBeGreaterThan crossingsFirstHalf
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // FM synthesis
    // ═════════════════════════════════════════════════════════════════════════════

    "fm(modulator, ratio, depth) - output has more harmonic content than carrier alone" {
        val dry = generate(Exciters.sine())
        val wet = generate(Exciters.sine().fm(modulator = Exciters.sine(), ratio = 2.0, depth = 200.0))

        // FM synthesis creates sidebands — the waveform should differ substantially from a pure sine.
        // Compute mean absolute difference between dry and wet signals.
        var diffSum = 0.0
        for (i in dry.indices) {
            diffSum += abs(dry[i] - wet[i]).toDouble()
        }
        val meanDiff = diffSum / dry.size

        // The signals should differ significantly (not just by floating-point noise)
        meanDiff shouldBeGreaterThan 0.1

        // Output should be bounded
        wet.peakAmplitude() shouldBeLessThan 2.0
    }
})
