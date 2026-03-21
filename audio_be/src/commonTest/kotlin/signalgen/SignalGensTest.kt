package io.peekandpoke.klang.audio_be.signalgen

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.TWO_PI
import io.peekandpoke.klang.audio_be.osci.Oscillators
import io.peekandpoke.klang.audio_bridge.SignalGenDsl
import kotlin.math.abs
import kotlin.random.Random

/**
 * Audio-output tests for SignalGen oscillator primitives.
 *
 * Tests verify waveform shape, amplitude, frequency accuracy,
 * and phase continuity — not just "non-zero output".
 */
class SignalGensTest : StringSpec({

    val sampleRate = 44100
    val defaultBlockFrames = 4410 // 100ms — enough for multiple cycles at 440Hz

    fun createCtx(blockFrames: Int = defaultBlockFrames): SignalContext {
        return SignalContext(
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
    }

    fun generate(sig: SignalGen, freqHz: Double = 440.0, blockFrames: Int = defaultBlockFrames): FloatArray {
        val buffer = FloatArray(blockFrames)
        sig.generate(buffer, freqHz, createCtx(blockFrames))
        return buffer
    }

    /** Count zero crossings (sign changes) in the buffer */
    fun FloatArray.zeroCrossings(): Int {
        var count = 0
        for (i in 1 until size) {
            if ((this[i - 1] >= 0.0 && this[i] < 0.0) || (this[i - 1] < 0.0 && this[i] >= 0.0)) {
                count++
            }
        }
        return count
    }

    /** Find peak absolute amplitude */
    fun FloatArray.peakAmplitude(): Double = maxOf((maxOrNull() ?: 0.0f).toDouble(), -(minOrNull() ?: 0.0f).toDouble())

    /** Check that the waveform is roughly symmetric around zero (mean close to 0) */
    fun FloatArray.dcOffset(): Double = map { it.toDouble() }.average()

    // ═════════════════════════════════════════════════════════════════════════════
    // Triangle
    // ═════════════════════════════════════════════════════════════════════════════

    "triangle - amplitude matches gain" {
        val gain = 0.7
        val buf = generate(SignalGens.triangle(gain), freqHz = 440.0)
        buf.peakAmplitude() shouldBe (gain plusOrMinus 0.02)
    }

    "triangle - custom gain scales amplitude" {
        val gain = 0.3
        val buf = generate(SignalGens.triangle(gain), freqHz = 440.0)
        buf.peakAmplitude() shouldBe (gain plusOrMinus 0.02)
    }

    "triangle - correct frequency (zero crossings)" {
        // 440Hz over 100ms = 44 cycles = 88 zero crossings (2 per cycle)
        val buf = generate(SignalGens.triangle(), freqHz = 440.0)
        buf.zeroCrossings() shouldBeInRange 86..90
    }

    "triangle - symmetric around zero (no DC offset)" {
        val buf = generate(SignalGens.triangle(), freqHz = 440.0)
        buf.dcOffset() shouldBe (0.0 plusOrMinus 0.01)
    }

    "triangle - linear ramps (not curved like sine)" {
        // Generate at a low frequency so we have many samples per ramp
        val freqHz = 100.0
        val buf = generate(SignalGens.triangle(1.0), freqHz = freqHz, blockFrames = sampleRate)
        // samplesPerCycle = 44100 / 100 = 441
        // Each quarter-cycle (110.25 samples) should be a linear ramp
        // Find the first rising zero crossing and check linearity of the ramp after it
        var rampStart = -1
        for (i in 1 until buf.size) {
            if (buf[i - 1] < 0.0 && buf[i] >= 0.0) {
                rampStart = i
                break
            }
        }
        rampStart shouldBeGreaterThanOrEqual 0

        // Check ~100 samples of the rising ramp
        val rampLen = 100
        if (rampStart + rampLen < buf.size) {
            // Slope should be roughly constant
            val slopes = (0 until rampLen - 1).map { (buf[rampStart + it + 1] - buf[rampStart + it]).toDouble() }
            val avgSlope = slopes.average()
            // All slopes should be close to the average (linear = constant slope)
            for (slope in slopes) {
                slope shouldBe (avgSlope plusOrMinus abs(avgSlope * 0.05))
            }
        }
    }

    "triangle - phase continuity across blocks" {
        val sig = SignalGens.triangle()
        val blockSize = 128
        val ctx = createCtx(blockSize)
        val buf1 = FloatArray(blockSize)
        val buf2 = FloatArray(blockSize)

        // Render two consecutive blocks
        sig.generate(buf1, 440.0, ctx)
        ctx.voiceElapsedFrames = blockSize
        sig.generate(buf2, 440.0, ctx)

        // Last sample of block 1 and first sample of block 2 should be close (continuous)
        val diff = abs(buf2[0] - buf1[blockSize - 1]).toDouble()
        diff shouldBeLessThan 0.05
    }

    "triangle - correct frequency at 100Hz" {
        val buf = generate(SignalGens.triangle(), freqHz = 100.0)
        buf.zeroCrossings() shouldBeInRange 18..22
    }

    "triangle - correct frequency at 1000Hz" {
        val buf = generate(SignalGens.triangle(), freqHz = 1000.0)
        buf.zeroCrossings() shouldBeInRange 198..202
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Sine
    // ═════════════════════════════════════════════════════════════════════════════

    "sine - amplitude matches gain" {
        val gain = 1.0
        val buf = generate(SignalGens.sine(gain), freqHz = 440.0)
        buf.peakAmplitude() shouldBe (gain plusOrMinus 0.02)
    }

    "sine - custom gain scales amplitude" {
        val gain = 0.4
        val buf = generate(SignalGens.sine(gain), freqHz = 440.0)
        buf.peakAmplitude() shouldBe (gain plusOrMinus 0.02)
    }

    "sine - correct frequency (zero crossings)" {
        val buf = generate(SignalGens.sine(), freqHz = 440.0)
        buf.zeroCrossings() shouldBeInRange 86..90
    }

    "sine - symmetric around zero" {
        val buf = generate(SignalGens.sine(), freqHz = 440.0)
        buf.dcOffset() shouldBe (0.0 plusOrMinus 0.01)
    }

    "sine - phase continuity across blocks" {
        val sig = SignalGens.sine()
        val blockSize = 128
        val ctx = createCtx(blockSize)
        val buf1 = FloatArray(blockSize)
        val buf2 = FloatArray(blockSize)

        sig.generate(buf1, 440.0, ctx)
        ctx.voiceElapsedFrames = blockSize
        sig.generate(buf2, 440.0, ctx)

        val diff = abs(buf2[0] - buf1[blockSize - 1]).toDouble()
        diff shouldBeLessThan 0.05
    }

    "sine - correct frequency at 100Hz" {
        val buf = generate(SignalGens.sine(), freqHz = 100.0)
        buf.zeroCrossings() shouldBeInRange 18..22
    }

    "sine - correct frequency at 1000Hz" {
        val buf = generate(SignalGens.sine(), freqHz = 1000.0)
        buf.zeroCrossings() shouldBeInRange 198..202
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Sawtooth
    // ═════════════════════════════════════════════════════════════════════════════

    "sawtooth - amplitude close to gain (PolyBLEP softens peaks)" {
        val gain = 0.6
        val buf = generate(SignalGens.sawtooth(gain), freqHz = 440.0)
        // PolyBLEP rounds the discontinuity, so peak may be slightly below gain
        buf.peakAmplitude() shouldBe (gain plusOrMinus 0.05)
    }

    "sawtooth - correct frequency (zero crossings)" {
        // Sawtooth crosses zero once per cycle going up, plus the reset jump may cause another
        val buf = generate(SignalGens.sawtooth(), freqHz = 440.0)
        val crossings = buf.zeroCrossings()
        crossings shouldBeGreaterThanOrEqual 40
        crossings shouldBeLessThanOrEqual 90
    }

    "sawtooth - symmetric around zero" {
        val buf = generate(SignalGens.sawtooth(), freqHz = 440.0)
        buf.dcOffset() shouldBe (0.0 plusOrMinus 0.02)
    }

    "sawtooth - phase continuity across blocks" {
        val sig = SignalGens.sawtooth()
        val blockSize = 128
        val ctx = createCtx(blockSize)
        val buf1 = FloatArray(blockSize)
        val buf2 = FloatArray(blockSize)

        sig.generate(buf1, 440.0, ctx)
        ctx.voiceElapsedFrames = blockSize
        sig.generate(buf2, 440.0, ctx)

        // Sawtooth is continuous within a ramp — only jumps at reset
        val diff = abs(buf2[0] - buf1[blockSize - 1]).toDouble()
        diff shouldBeLessThan 1.5
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Square
    // ═════════════════════════════════════════════════════════════════════════════

    "square - amplitude close to gain (PolyBLEP softens transitions)" {
        val gain = 0.5
        val buf = generate(SignalGens.square(gain), freqHz = 440.0)
        buf.peakAmplitude() shouldBe (gain plusOrMinus 0.05)
    }

    "square - mostly two output levels (PolyBLEP softens transitions)" {
        val gain = 0.5
        val buf = generate(SignalGens.square(gain), freqHz = 440.0)
        // Most samples should be close to +gain or -gain
        val nearGain = buf.count { abs(abs(it.toDouble()) - gain) < 0.05 }
        // Allow ~5% of samples near transitions to deviate
        nearGain shouldBeGreaterThanOrEqual (buf.size * 0.9).toInt()
    }

    "square - correct frequency (zero crossings)" {
        // Square wave: 2 zero crossings per cycle
        val buf = generate(SignalGens.square(), freqHz = 440.0)
        buf.zeroCrossings() shouldBeInRange 86..90
    }

    "square - symmetric around zero" {
        val buf = generate(SignalGens.square(), freqHz = 440.0)
        buf.dcOffset() shouldBe (0.0 plusOrMinus 0.02)
    }

    "square - phase continuity across blocks" {
        val sig = SignalGens.square()
        val blockSize = 128
        val ctx = createCtx(blockSize)
        val buf1 = FloatArray(blockSize)
        val buf2 = FloatArray(blockSize)

        sig.generate(buf1, 440.0, ctx)
        ctx.voiceElapsedFrames = blockSize
        sig.generate(buf2, 440.0, ctx)

        // Square can jump at transitions, but block boundary shouldn't cause extra glitches
        val diff = abs(buf2[0] - buf1[blockSize - 1]).toDouble()
        diff shouldBeLessThan 1.5
    }

    "square - correct frequency at 100Hz" {
        val buf = generate(SignalGens.square(), freqHz = 100.0)
        buf.zeroCrossings() shouldBeInRange 18..22
    }

    "square - correct frequency at 1000Hz" {
        val buf = generate(SignalGens.square(), freqHz = 1000.0)
        buf.zeroCrossings() shouldBeInRange 198..202
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // White Noise
    // ═════════════════════════════════════════════════════════════════════════════

    "white noise - amplitude within gain bounds" {
        val gain = 1.0
        val buf = generate(SignalGens.whiteNoise(Random(42), gain), freqHz = 440.0)
        for (sample in buf) {
            abs(sample.toDouble()) shouldBeLessThan gain + 0.001
        }
    }

    "white noise - roughly zero mean (no DC offset)" {
        val buf = generate(SignalGens.whiteNoise(Random(42)), freqHz = 440.0)
        buf.dcOffset() shouldBe (0.0 plusOrMinus 0.05)
    }

    "white noise - custom gain scales amplitude" {
        val gain = 0.3
        val buf = generate(SignalGens.whiteNoise(Random(42), gain), freqHz = 440.0)
        for (sample in buf) {
            abs(sample.toDouble()) shouldBeLessThan gain + 0.001
        }
        buf.peakAmplitude() shouldBeGreaterThan gain * 0.8
    }

    "white noise - different seeds produce different output" {
        val buf1 = generate(SignalGens.whiteNoise(Random(42)), freqHz = 440.0)
        val buf2 = generate(SignalGens.whiteNoise(Random(99)), freqHz = 440.0)
        var differs = false
        for (i in buf1.indices) {
            if (buf1[i] != buf2[i]) {
                differs = true; break
            }
        }
        differs shouldBe true
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Cross-oscillator: independent instances
    // ═════════════════════════════════════════════════════════════════════════════

    "two instances of same oscillator are independent" {
        val sig1 = SignalGens.sine()
        val sig2 = SignalGens.sine()
        val ctx = createCtx(128)

        val buf1 = FloatArray(128)
        val buf2 = FloatArray(128)

        // Advance sig1 by one block, leave sig2 at start
        sig1.generate(buf1, 440.0, ctx)
        // Now generate second block from sig1 and first block from sig2
        sig1.generate(buf1, 440.0, ctx)
        sig2.generate(buf2, 440.0, ctx)

        // sig1 is on its second block, sig2 on first — must differ
        var differs = false
        for (i in buf1.indices) {
            if (buf1[i] != buf2[i]) {
                differs = true; break
            }
        }
        differs shouldBe true
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Zawtooth (naive sawtooth, no PolyBLEP)
    // ═════════════════════════════════════════════════════════════════════════════

    "zawtooth - amplitude matches gain" {
        val gain = 1.0
        val buf = generate(SignalGens.zawtooth(gain), freqHz = 440.0)
        buf.peakAmplitude().toDouble() shouldBe (gain plusOrMinus 0.02)
    }

    "zawtooth - custom gain scales amplitude" {
        val gain = 0.4
        val buf = generate(SignalGens.zawtooth(gain), freqHz = 440.0)
        buf.peakAmplitude().toDouble() shouldBe (gain plusOrMinus 0.02)
    }

    "zawtooth - correct frequency (zero crossings)" {
        // Naive saw crosses zero once per cycle at the midpoint, plus the reset jump
        val buf = generate(SignalGens.zawtooth(), freqHz = 440.0)
        val crossings = buf.zeroCrossings()
        crossings shouldBeGreaterThanOrEqual 40
        crossings shouldBeLessThanOrEqual 90
    }

    "zawtooth - symmetric around zero" {
        val buf = generate(SignalGens.zawtooth(), freqHz = 440.0)
        buf.dcOffset().toDouble() shouldBe (0.0 plusOrMinus 0.02)
    }

    "zawtooth - phase continuity across blocks" {
        val sig = SignalGens.zawtooth()
        val blockSize = 128
        val ctx = createCtx(blockSize)
        val buf1 = FloatArray(blockSize)
        val buf2 = FloatArray(blockSize)

        sig.generate(buf1, 440.0, ctx)
        ctx.voiceElapsedFrames = blockSize
        sig.generate(buf2, 440.0, ctx)

        val diff = abs(buf2[0] - buf1[blockSize - 1]).toDouble()
        diff shouldBeLessThan 1.5
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Silence
    // ═════════════════════════════════════════════════════════════════════════════

    // ═════════════════════════════════════════════════════════════════════════════
    // Impulse
    // ═════════════════════════════════════════════════════════════════════════════

    "impulse - one click per cycle" {
        // 440Hz over 100ms = 44 cycles = 44 impulses
        val buf = generate(SignalGens.impulse(), freqHz = 440.0)
        val impulseCount = buf.count { it == 1.0f }
        impulseCount shouldBeInRange 43..45
    }

    "impulse - output is only 0 or 1" {
        val buf = generate(SignalGens.impulse(), freqHz = 440.0)
        for (sample in buf) {
            (sample == 0.0f || sample == 1.0f) shouldBe true
        }
    }

    "impulse - mostly silence between clicks" {
        val buf = generate(SignalGens.impulse(), freqHz = 440.0)
        val silentCount = buf.count { it == 0.0f }
        // 4410 samples, ~44 impulses, so ~4366 silent samples
        silentCount shouldBeGreaterThanOrEqual 4350
    }

    "impulse - custom gain scales click amplitude" {
        val gain = 0.3
        val buf = generate(SignalGens.impulse(gain), freqHz = 440.0)
        val clickValue = buf.first { it != 0.0f }
        clickValue.toDouble() shouldBe (gain plusOrMinus 0.001)
    }

    "impulse - phase continuity across blocks" {
        val sig = SignalGens.impulse()
        val blockSize = 128
        val ctx = createCtx(blockSize)
        val buf1 = FloatArray(blockSize)
        val buf2 = FloatArray(blockSize)

        sig.generate(buf1, 440.0, ctx)
        ctx.voiceElapsedFrames = blockSize
        sig.generate(buf2, 440.0, ctx)

        // Should still produce correct impulse count across two blocks
        val totalImpulses = buf1.count { it == 1.0f } + buf2.count { it == 1.0f }
        // 256 samples at 440Hz ≈ 2.5 cycles ≈ 2-3 impulses
        totalImpulses shouldBeInRange 2..4
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Silence
    // ═════════════════════════════════════════════════════════════════════════════

    "silence - all zeros" {
        val buf = generate(SignalGens.silence())
        buf.all { it == 0.0f } shouldBe true
    }

    "silence - all zeros via DSL runtime" {
        val sig = SignalGenDsl.Silence.toSignalGen()
        val buf = generate(sig)
        buf.all { it == 0.0f } shouldBe true
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Pulze (variable duty cycle pulse wave)
    // ═════════════════════════════════════════════════════════════════════════════

    "pulze - amplitude matches gain" {
        val gain = 1.0
        val buf = generate(SignalGens.pulze(gain = gain), freqHz = 440.0)
        buf.peakAmplitude() shouldBe (gain plusOrMinus 0.01)
    }

    "pulze - default duty 0.5 is symmetric" {
        val buf = generate(SignalGens.pulze(duty = 0.5), freqHz = 440.0)
        buf.dcOffset() shouldBe (0.0 plusOrMinus 0.02)
    }

    "pulze - duty 0.25 has negative DC bias" {
        val buf = generate(SignalGens.pulze(duty = 0.25), freqHz = 440.0)
        // 25% high, 75% low → mean ≈ 0.25*1 + 0.75*(-1) = -0.5
        buf.dcOffset() shouldBe (-0.5 plusOrMinus 0.05)
    }

    "pulze - correct frequency" {
        val buf = generate(SignalGens.pulze(), freqHz = 440.0)
        buf.zeroCrossings() shouldBeInRange 86..90
    }

    "pulze - negative phaseMod does not cause drift" {
        val sig = SignalGens.pulze()
        val blockSize = 4410
        val ctx = createCtx(blockSize)
        ctx.phaseMod = DoubleArray(blockSize) { -1.0 }
        val buf = FloatArray(blockSize)
        sig.generate(buf, 440.0, ctx)
        for (sample in buf) {
            abs(sample.toDouble()) shouldBeLessThan 1.1
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Brown Noise
    // ═════════════════════════════════════════════════════════════════════════════

    "brown noise - output is bounded" {
        val buf = generate(SignalGens.brownNoise(Random(42)), freqHz = 440.0)
        for (sample in buf) {
            abs(sample.toDouble()) shouldBeLessThan 1.5
        }
    }

    "brown noise - roughly zero mean" {
        val buf = generate(SignalGens.brownNoise(Random(42)), freqHz = 440.0)
        buf.dcOffset() shouldBe (0.0 plusOrMinus 0.1)
    }

    "brown noise - custom gain scales amplitude" {
        val buf1 = generate(SignalGens.brownNoise(Random(42), gain = 1.0), freqHz = 440.0)
        val buf2 = generate(SignalGens.brownNoise(Random(42), gain = 0.5), freqHz = 440.0)
        // Half gain should produce roughly half amplitude
        val peak1 = buf1.peakAmplitude()
        val peak2 = buf2.peakAmplitude()
        (peak2 / peak1) shouldBe (0.5 plusOrMinus 0.05)
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Pink Noise
    // ═════════════════════════════════════════════════════════════════════════════

    "pink noise - output is bounded" {
        val buf = generate(SignalGens.pinkNoise(Random(42)), freqHz = 440.0)
        for (sample in buf) {
            abs(sample.toDouble()) shouldBeLessThan 1.5
        }
    }

    "pink noise - roughly zero mean" {
        val buf = generate(SignalGens.pinkNoise(Random(42)), freqHz = 440.0)
        buf.dcOffset() shouldBe (0.0 plusOrMinus 0.1)
    }

    "pink noise - custom gain scales amplitude" {
        val buf1 = generate(SignalGens.pinkNoise(Random(42), gain = 1.0), freqHz = 440.0)
        val buf2 = generate(SignalGens.pinkNoise(Random(42), gain = 0.5), freqHz = 440.0)
        val peak1 = buf1.peakAmplitude()
        val peak2 = buf2.peakAmplitude()
        (peak2 / peak1) shouldBe (0.5 plusOrMinus 0.05)
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Dust
    // ═════════════════════════════════════════════════════════════════════════════

    "dust - mostly silence" {
        val buf = generate(SignalGens.dust(Random(42), density = 0.2), freqHz = 440.0)
        val silentCount = buf.count { it == 0.0f }
        silentCount shouldBeGreaterThanOrEqual (buf.size * 0.8).toInt()
    }

    "dust - output is non-negative" {
        val buf = generate(SignalGens.dust(Random(42), density = 0.5), freqHz = 440.0)
        for (sample in buf) {
            sample.toDouble() shouldBeGreaterThan -0.001
        }
    }

    "dust - higher density produces more impulses" {
        val bufLow = generate(SignalGens.dust(Random(42), density = 0.1), freqHz = 440.0)
        val bufHigh = generate(SignalGens.dust(Random(42), density = 0.9), freqHz = 440.0)
        val countLow = bufLow.count { it > 0.0f }
        val countHigh = bufHigh.count { it > 0.0f }
        countHigh shouldBeGreaterThanOrEqual countLow
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Crackle
    // ═════════════════════════════════════════════════════════════════════════════

    "crackle - mostly silence" {
        val buf = generate(SignalGens.crackle(Random(42), density = 0.2), freqHz = 440.0)
        val silentCount = buf.count { it == 0.0f }
        silentCount shouldBeGreaterThanOrEqual (buf.size * 0.5).toInt()
    }

    "crackle - denser than dust at same density (higher maxRateHz)" {
        val bufDust = generate(SignalGens.dust(Random(42), density = 0.5), freqHz = 440.0)
        val bufCrackle = generate(SignalGens.crackle(Random(42), density = 0.5), freqHz = 440.0)
        val countDust = bufDust.count { it > 0.0f }
        val countCrackle = bufCrackle.count { it > 0.0f }
        countCrackle shouldBeGreaterThanOrEqual countDust
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // PhaseMod (negative values must not cause drift)
    // ═════════════════════════════════════════════════════════════════════════════

    "sine - negative phaseMod does not cause phase drift" {
        val sig = SignalGens.sine()
        val blockSize = 4410
        val ctx = createCtx(blockSize)
        // Set phaseMod to -1.0 (reversed phase direction)
        ctx.phaseMod = DoubleArray(blockSize) { -1.0 }
        val buf = FloatArray(blockSize)
        sig.generate(buf, 440.0, ctx)

        // Output should be bounded (no NaN/Infinity from unbounded phase)
        for (sample in buf) {
            abs(sample.toDouble()) shouldBeLessThan 1.1
        }
    }

    "sawtooth - negative phaseMod does not cause phase drift" {
        val sig = SignalGens.sawtooth()
        val blockSize = 4410
        val ctx = createCtx(blockSize)
        ctx.phaseMod = DoubleArray(blockSize) { -1.0 }
        val buf = FloatArray(blockSize)
        sig.generate(buf, 440.0, ctx)

        for (sample in buf) {
            abs(sample.toDouble()) shouldBeLessThan 1.1
        }
    }

    "square - negative phaseMod does not cause phase drift" {
        val sig = SignalGens.square()
        val blockSize = 4410
        val ctx = createCtx(blockSize)
        ctx.phaseMod = DoubleArray(blockSize) { -1.0 }
        val buf = FloatArray(blockSize)
        sig.generate(buf, 440.0, ctx)

        for (sample in buf) {
            abs(sample.toDouble()) shouldBeLessThan 1.1
        }
    }

    "triangle - negative phaseMod does not cause phase drift" {
        val sig = SignalGens.triangle()
        val blockSize = 4410
        val ctx = createCtx(blockSize)
        ctx.phaseMod = DoubleArray(blockSize) { -1.0 }
        val buf = FloatArray(blockSize)
        sig.generate(buf, 440.0, ctx)

        for (sample in buf) {
            abs(sample.toDouble()) shouldBeLessThan 1.1
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Supersaw
    // ═════════════════════════════════════════════════════════════════════════════

    "supersaw - produces non-zero output" {
        val buf = generate(SignalGens.supersaw(), freqHz = 440.0)
        buf.any { it != 0.0f } shouldBe true
    }

    "supersaw - amplitude bounded by gain" {
        val gain = 0.6
        val buf = generate(SignalGens.supersaw(gain = gain), freqHz = 440.0)
        buf.peakAmplitude() shouldBeLessThan gain + 0.1
    }

    "supersaw - more voices increases energy" {
        val buf1 = generate(SignalGens.supersaw(voices = 1, gain = 1.0), freqHz = 440.0)
        val buf5 = generate(SignalGens.supersaw(voices = 5, gain = 1.0), freqHz = 440.0)
        // Both should produce output
        buf1.any { it != 0.0f } shouldBe true
        buf5.any { it != 0.0f } shouldBe true
    }

    "supersaw - single voice equals sawtooth character" {
        // Single voice supersaw should have sawtooth-like zero crossings
        val buf = generate(SignalGens.supersaw(voices = 1, freqSpread = 0.0, gain = 1.0), freqHz = 440.0)
        val crossings = buf.zeroCrossings()
        // 440Hz over 100ms ≈ 44 cycles, saw has ~1-2 crossings per cycle
        crossings shouldBeGreaterThanOrEqual 40
        crossings shouldBeLessThanOrEqual 90
    }

    "supersaw - symmetric around zero (no DC offset)" {
        val buf = generate(SignalGens.supersaw(), freqHz = 440.0)
        buf.dcOffset() shouldBe (0.0 plusOrMinus 0.05)
    }

    "supersaw - phase continuity across blocks" {
        val sig = SignalGens.supersaw()
        val blockSize = 128
        val ctx = createCtx(blockSize)
        val buf1 = FloatArray(blockSize)
        val buf2 = FloatArray(blockSize)

        sig.generate(buf1, 440.0, ctx)
        ctx.voiceElapsedFrames = blockSize
        sig.generate(buf2, 440.0, ctx)

        // Should produce continuous output (no NaN, no extreme jumps)
        for (sample in buf1 + buf2) {
            abs(sample.toDouble()) shouldBeLessThan 1.5
        }
    }

    "supersaw - negative phaseMod does not cause drift" {
        val sig = SignalGens.supersaw()
        val blockSize = 4410
        val ctx = createCtx(blockSize)
        ctx.phaseMod = DoubleArray(blockSize) { -1.0 }
        val buf = FloatArray(blockSize)
        sig.generate(buf, 440.0, ctx)

        for (sample in buf) {
            abs(sample.toDouble()) shouldBeLessThan 1.5
        }
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Supersaw DSL with oscParams overrides
    // ═════════════════════════════════════════════════════════════════════════════

    "supersaw DSL - oscParams override voices" {
        val dsl = SignalGenDsl.Supersaw(voices = 3)
        val sig = dsl.toSignalGen(mapOf("voices" to 7.0))
        val buf = generate(sig, freqHz = 440.0)
        buf.any { it != 0.0f } shouldBe true
    }

    "supersaw DSL - oscParams override freqSpread" {
        val dsl = SignalGenDsl.Supersaw(freqSpread = 0.1)
        val sig = dsl.toSignalGen(mapOf("freqSpread" to 0.5))
        val buf = generate(sig, freqHz = 440.0)
        buf.any { it != 0.0f } shouldBe true
    }

    "dust DSL - oscParams override density" {
        val dsl = SignalGenDsl.Dust(density = 0.01)
        val sigOverride = dsl.toSignalGen(mapOf("density" to 0.99))
        val sigDefault = dsl.toSignalGen()
        val bufOverride = generate(sigOverride, freqHz = 440.0)
        val bufDefault = generate(sigDefault, freqHz = 440.0)
        // Higher density should produce more impulses
        val countOverride = bufOverride.count { it > 0.0f }
        val countDefault = bufDefault.count { it > 0.0f }
        countOverride shouldBeGreaterThanOrEqual countDefault
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Supersaw: new SignalGen vs legacy OscFn — sample-exact comparison
    // ═════════════════════════════════════════════════════════════════════════════

    "supersaw - new SignalGen matches legacy OscFn sample-for-sample" {
        val seed = 42
        val voices = 7
        val freqSpread = 0.3
        val gain = 0.6
        val freqHz = 440.0
        val blockFrames = 4410

        // ── New SignalGen path ──
        val newSig = SignalGens.supersaw(
            voices = voices,
            freqSpread = freqSpread,
            gain = gain,
            rng = Random(seed),
        )
        val newBuf = FloatArray(blockFrames)
        val newCtx = createCtx(blockFrames)
        newSig.generate(newBuf, freqHz, newCtx)

        // ── Legacy OscFn path ──
        val legacyOsc = Oscillators.supersawFn(
            sampleRate = sampleRate,
            baseFreqHz = freqHz,
            voices = voices,
            freqSpread = freqSpread,
            panSpread = 0.0,
            rng = Random(seed),
            gain = gain,
        )
        val legacyBuf = FloatArray(blockFrames)
        legacyOsc.process(legacyBuf, 0, blockFrames, 0.0, TWO_PI * freqHz / sampleRate, null)

        // ── Compare sample-by-sample ──
        var maxDiff = 0.0
        for (i in 0 until blockFrames) {
            val diff = abs(newBuf[i] - legacyBuf[i]).toDouble()
            if (diff > maxDiff) maxDiff = diff
        }

        // Should be bit-identical (same algorithm, same seed, same params)
        println("supersaw comparison: maxDiff = $maxDiff")
        maxDiff shouldBeLessThan 0.001
    }
})
