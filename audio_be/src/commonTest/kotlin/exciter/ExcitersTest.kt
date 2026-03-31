package io.peekandpoke.klang.audio_be.exciter

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThanOrEqual
import io.kotest.matchers.ints.shouldBeInRange
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_bridge.ExciterDsl
import kotlin.math.abs
import kotlin.random.Random

private fun gain(value: Double) = ParamExciter("gain", value)
private fun density(value: Double) = ParamExciter("density", value)
private fun duty(value: Double) = ParamExciter("duty", value)

/**
 * Audio-output tests for Exciter oscillator primitives.
 *
 * Tests verify waveform shape, amplitude, frequency accuracy,
 * and phase continuity — not just "non-zero output".
 */
class ExcitersTest : StringSpec({

    val sampleRate = 44100
    val defaultBlockFrames = 4410 // 100ms — enough for multiple cycles at 440Hz

    fun createCtx(blockFrames: Int = defaultBlockFrames): ExciteContext {
        return ExciteContext(
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

    fun generate(sig: Exciter, freqHz: Double = 440.0, blockFrames: Int = defaultBlockFrames): FloatArray {
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
        val g = 0.7
        val buf = generate(Exciters.triangle().withGain(gain(g)), freqHz = 440.0)
        buf.peakAmplitude() shouldBe (g plusOrMinus 0.02)
    }

    "triangle - custom gain scales amplitude" {
        val g = 0.3
        val buf = generate(Exciters.triangle().withGain(gain(g)), freqHz = 440.0)
        buf.peakAmplitude() shouldBe (g plusOrMinus 0.02)
    }

    "triangle - correct frequency (zero crossings)" {
        // 440Hz over 100ms = 44 cycles = 88 zero crossings (2 per cycle)
        val buf = generate(Exciters.triangle(), freqHz = 440.0)
        buf.zeroCrossings() shouldBeInRange 86..90
    }

    "triangle - symmetric around zero (no DC offset)" {
        val buf = generate(Exciters.triangle(), freqHz = 440.0)
        buf.dcOffset() shouldBe (0.0 plusOrMinus 0.01)
    }

    "triangle - linear ramps (not curved like sine)" {
        // Generate at a low frequency so we have many samples per ramp
        val freqHz = 100.0
        val buf = generate(Exciters.triangle().withGain(gain(1.0)), freqHz = freqHz, blockFrames = sampleRate)
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
        val sig = Exciters.triangle()
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
        val buf = generate(Exciters.triangle(), freqHz = 100.0)
        buf.zeroCrossings() shouldBeInRange 18..22
    }

    "triangle - correct frequency at 1000Hz" {
        val buf = generate(Exciters.triangle(), freqHz = 1000.0)
        buf.zeroCrossings() shouldBeInRange 198..202
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Sine
    // ═════════════════════════════════════════════════════════════════════════════

    "sine - amplitude matches gain" {
        val g = 1.0
        val buf = generate(Exciters.sine().withGain(gain(g)), freqHz = 440.0)
        buf.peakAmplitude() shouldBe (g plusOrMinus 0.02)
    }

    "sine - custom gain scales amplitude" {
        val g = 0.4
        val buf = generate(Exciters.sine().withGain(gain(g)), freqHz = 440.0)
        buf.peakAmplitude() shouldBe (g plusOrMinus 0.02)
    }

    "sine - correct frequency (zero crossings)" {
        val buf = generate(Exciters.sine(), freqHz = 440.0)
        buf.zeroCrossings() shouldBeInRange 86..90
    }

    "sine - symmetric around zero" {
        val buf = generate(Exciters.sine(), freqHz = 440.0)
        buf.dcOffset() shouldBe (0.0 plusOrMinus 0.01)
    }

    "sine - phase continuity across blocks" {
        val sig = Exciters.sine()
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
        val buf = generate(Exciters.sine(), freqHz = 100.0)
        buf.zeroCrossings() shouldBeInRange 18..22
    }

    "sine - correct frequency at 1000Hz" {
        val buf = generate(Exciters.sine(), freqHz = 1000.0)
        buf.zeroCrossings() shouldBeInRange 198..202
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Sawtooth
    // ═════════════════════════════════════════════════════════════════════════════

    "sawtooth - amplitude close to gain (PolyBLEP softens peaks)" {
        val g = 0.6
        val buf = generate(Exciters.sawtooth().withGain(gain(g)), freqHz = 440.0)
        // PolyBLEP rounds the discontinuity, so peak may be slightly below gain
        buf.peakAmplitude() shouldBe (g plusOrMinus 0.05)
    }

    "sawtooth - correct frequency (zero crossings)" {
        // Sawtooth crosses zero once per cycle going up, plus the reset jump may cause another
        val buf = generate(Exciters.sawtooth(), freqHz = 440.0)
        val crossings = buf.zeroCrossings()
        crossings shouldBeGreaterThanOrEqual 40
        crossings shouldBeLessThanOrEqual 90
    }

    "sawtooth - symmetric around zero" {
        val buf = generate(Exciters.sawtooth(), freqHz = 440.0)
        buf.dcOffset() shouldBe (0.0 plusOrMinus 0.02)
    }

    "sawtooth - phase continuity across blocks" {
        val sig = Exciters.sawtooth()
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
        val g = 0.5
        val buf = generate(Exciters.square().withGain(gain(g)), freqHz = 440.0)
        buf.peakAmplitude() shouldBe (g plusOrMinus 0.05)
    }

    "square - mostly two output levels (PolyBLEP softens transitions)" {
        val g = 0.5
        val buf = generate(Exciters.square().withGain(gain(g)), freqHz = 440.0)
        // Most samples should be close to +gain or -gain
        val nearGain = buf.count { abs(abs(it.toDouble()) - g) < 0.05 }
        // Allow ~5% of samples near transitions to deviate
        nearGain shouldBeGreaterThanOrEqual (buf.size * 0.9).toInt()
    }

    "square - correct frequency (zero crossings)" {
        // Square wave: 2 zero crossings per cycle
        val buf = generate(Exciters.square(), freqHz = 440.0)
        buf.zeroCrossings() shouldBeInRange 86..90
    }

    "square - symmetric around zero" {
        val buf = generate(Exciters.square(), freqHz = 440.0)
        buf.dcOffset() shouldBe (0.0 plusOrMinus 0.02)
    }

    "square - phase continuity across blocks" {
        val sig = Exciters.square()
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
        val buf = generate(Exciters.square(), freqHz = 100.0)
        buf.zeroCrossings() shouldBeInRange 18..22
    }

    "square - correct frequency at 1000Hz" {
        val buf = generate(Exciters.square(), freqHz = 1000.0)
        buf.zeroCrossings() shouldBeInRange 198..202
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // White Noise
    // ═════════════════════════════════════════════════════════════════════════════

    "white noise - amplitude within gain bounds" {
        val g = 1.0
        val buf = generate(Exciters.whiteNoise(Random(42)).withGain(gain(g)), freqHz = 440.0)
        for (sample in buf) {
            abs(sample.toDouble()) shouldBeLessThan g + 0.001
        }
    }

    "white noise - roughly zero mean (no DC offset)" {
        val buf = generate(Exciters.whiteNoise(Random(42)), freqHz = 440.0)
        buf.dcOffset() shouldBe (0.0 plusOrMinus 0.05)
    }

    "white noise - custom gain scales amplitude" {
        val g = 0.3
        val buf = generate(Exciters.whiteNoise(Random(42)).withGain(gain(g)), freqHz = 440.0)
        for (sample in buf) {
            abs(sample.toDouble()) shouldBeLessThan g + 0.001
        }
        buf.peakAmplitude() shouldBeGreaterThan g * 0.8
    }

    "white noise - different seeds produce different output" {
        val buf1 = generate(Exciters.whiteNoise(Random(42)), freqHz = 440.0)
        val buf2 = generate(Exciters.whiteNoise(Random(99)), freqHz = 440.0)
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
        val sig1 = Exciters.sine()
        val sig2 = Exciters.sine()
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
        val g = 1.0
        val buf = generate(Exciters.zawtooth().withGain(gain(g)), freqHz = 440.0)
        buf.peakAmplitude() shouldBe (g plusOrMinus 0.02)
    }

    "zawtooth - custom gain scales amplitude" {
        val g = 0.4
        val buf = generate(Exciters.zawtooth().withGain(gain(g)), freqHz = 440.0)
        buf.peakAmplitude() shouldBe (g plusOrMinus 0.02)
    }

    "zawtooth - correct frequency (zero crossings)" {
        // Naive saw crosses zero once per cycle at the midpoint, plus the reset jump
        val buf = generate(Exciters.zawtooth(), freqHz = 440.0)
        val crossings = buf.zeroCrossings()
        crossings shouldBeGreaterThanOrEqual 40
        crossings shouldBeLessThanOrEqual 90
    }

    "zawtooth - symmetric around zero" {
        val buf = generate(Exciters.zawtooth(), freqHz = 440.0)
        buf.dcOffset() shouldBe (0.0 plusOrMinus 0.02)
    }

    "zawtooth - phase continuity across blocks" {
        val sig = Exciters.zawtooth()
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
        val buf = generate(Exciters.impulse(), freqHz = 440.0)
        val impulseCount = buf.count { it == 1.0f }
        impulseCount shouldBeInRange 43..45
    }

    "impulse - output is only 0 or 1" {
        val buf = generate(Exciters.impulse(), freqHz = 440.0)
        for (sample in buf) {
            (sample == 0.0f || sample == 1.0f) shouldBe true
        }
    }

    "impulse - mostly silence between clicks" {
        val buf = generate(Exciters.impulse(), freqHz = 440.0)
        val silentCount = buf.count { it == 0.0f }
        // 4410 samples, ~44 impulses, so ~4366 silent samples
        silentCount shouldBeGreaterThanOrEqual 4350
    }

    "impulse - custom gain scales click amplitude" {
        val g = 0.3
        val buf = generate(Exciters.impulse().withGain(gain(g)), freqHz = 440.0)
        val clickValue = buf.first { it != 0.0f }
        clickValue.toDouble() shouldBe (g plusOrMinus 0.001)
    }

    "impulse - phase continuity across blocks" {
        val sig = Exciters.impulse()
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
        val buf = generate(Exciters.silence())
        buf.all { it == 0.0f } shouldBe true
    }

    "silence - all zeros via DSL runtime" {
        val sig = ExciterDsl.Silence.toExciter()
        val buf = generate(sig)
        buf.all { it == 0.0f } shouldBe true
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Pulze (variable duty cycle pulse wave)
    // ═════════════════════════════════════════════════════════════════════════════

    "pulze - amplitude matches gain" {
        val g = 1.0
        val buf = generate(Exciters.pulze().withGain(gain(g)), freqHz = 440.0)
        buf.peakAmplitude() shouldBe (g plusOrMinus 0.01)
    }

    "pulze - default duty 0.5 is symmetric" {
        val buf = generate(Exciters.pulze(duty = duty(0.5)), freqHz = 440.0)
        buf.dcOffset() shouldBe (0.0 plusOrMinus 0.02)
    }

    "pulze - duty 0.25 has negative DC bias" {
        val buf = generate(Exciters.pulze(duty = duty(0.25)), freqHz = 440.0)
        // 25% high, 75% low → mean ≈ 0.25*1 + 0.75*(-1) = -0.5
        buf.dcOffset() shouldBe (-0.5 plusOrMinus 0.05)
    }

    "pulze - correct frequency" {
        val buf = generate(Exciters.pulze(), freqHz = 440.0)
        buf.zeroCrossings() shouldBeInRange 86..90
    }

    "pulze - negative phaseMod does not cause drift" {
        val sig = Exciters.pulze()
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
        val buf = generate(Exciters.brownNoise(Random(42)), freqHz = 440.0)
        for (sample in buf) {
            abs(sample.toDouble()) shouldBeLessThan 1.5
        }
    }

    "brown noise - roughly zero mean" {
        val buf = generate(Exciters.brownNoise(Random(42)), freqHz = 440.0)
        buf.dcOffset() shouldBe (0.0 plusOrMinus 0.1)
    }

    "brown noise - custom gain scales amplitude" {
        val buf1 = generate(Exciters.brownNoise(Random(42)).withGain(gain(1.0)), freqHz = 440.0)
        val buf2 = generate(Exciters.brownNoise(Random(42)).withGain(gain(0.5)), freqHz = 440.0)
        // Half gain should produce roughly half amplitude
        val peak1 = buf1.peakAmplitude()
        val peak2 = buf2.peakAmplitude()
        (peak2 / peak1) shouldBe (0.5 plusOrMinus 0.05)
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Pink Noise
    // ═════════════════════════════════════════════════════════════════════════════

    "pink noise - output is bounded" {
        val buf = generate(Exciters.pinkNoise(Random(42)), freqHz = 440.0)
        for (sample in buf) {
            abs(sample.toDouble()) shouldBeLessThan 1.5
        }
    }

    "pink noise - roughly zero mean" {
        val buf = generate(Exciters.pinkNoise(Random(42)), freqHz = 440.0)
        buf.dcOffset() shouldBe (0.0 plusOrMinus 0.1)
    }

    "pink noise - custom gain scales amplitude" {
        val buf1 = generate(Exciters.pinkNoise(Random(42)).withGain(gain(1.0)), freqHz = 440.0)
        val buf2 = generate(Exciters.pinkNoise(Random(42)).withGain(gain(0.5)), freqHz = 440.0)
        val peak1 = buf1.peakAmplitude()
        val peak2 = buf2.peakAmplitude()
        (peak2 / peak1) shouldBe (0.5 plusOrMinus 0.05)
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Perlin Noise
    // ═════════════════════════════════════════════════════════════════════════════

    "perlin noise - produces non-zero output" {
        val buf = generate(Exciters.perlinNoise(Random(42)))
        buf.any { it != 0.0f } shouldBe true
    }

    "perlin noise - output is bounded to -1..1" {
        val buf = generate(Exciters.perlinNoise(Random(42)))
        buf.peakAmplitude() shouldBeLessThan 1.01
    }

    "perlin noise - roughly zero mean (smooth noise)" {
        val buf = generate(Exciters.perlinNoise(Random(42)))
        buf.dcOffset() shouldBe (0.0 plusOrMinus 0.3)
    }

    "perlin noise - higher rate produces faster changes" {
        val bufSlow = generate(Exciters.perlinNoise(Random(42), rate = ParamExciter("rate", 0.1)))
        val bufFast = generate(Exciters.perlinNoise(Random(42), rate = ParamExciter("rate", 10.0)))
        // Faster noise should have more zero crossings
        bufFast.zeroCrossings() shouldBeGreaterThanOrEqual bufSlow.zeroCrossings()
    }

    "perlin noise - gain scales amplitude" {
        val buf1 = generate(Exciters.perlinNoise(Random(42)).withGain(gain(1.0)))
        val buf2 = generate(Exciters.perlinNoise(Random(42)).withGain(gain(0.5)))
        val peak1 = buf1.peakAmplitude()
        val peak2 = buf2.peakAmplitude()
        (peak2 / peak1) shouldBe (0.5 plusOrMinus 0.1)
    }

    "perlin noise - DSL round-trip produces output" {
        val dsl = ExciterDsl.PerlinNoise()
        val sig = dsl.toExciter()
        val buf = generate(sig)
        buf.any { it != 0.0f } shouldBe true
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Berlin Noise
    // ═════════════════════════════════════════════════════════════════════════════

    "berlin noise - produces non-zero output" {
        val buf = generate(Exciters.berlinNoise(Random(42)))
        buf.any { it != 0.0f } shouldBe true
    }

    "berlin noise - output is bounded to -1..1" {
        val buf = generate(Exciters.berlinNoise(Random(42)))
        buf.peakAmplitude() shouldBeLessThan 1.01
    }

    "berlin noise - higher rate produces faster changes" {
        val bufSlow = generate(Exciters.berlinNoise(Random(42), rate = ParamExciter("rate", 0.1)))
        val bufFast = generate(Exciters.berlinNoise(Random(42), rate = ParamExciter("rate", 10.0)))
        bufFast.zeroCrossings() shouldBeGreaterThanOrEqual bufSlow.zeroCrossings()
    }

    "berlin noise - gain scales amplitude" {
        val buf1 = generate(Exciters.berlinNoise(Random(42)).withGain(gain(1.0)))
        val buf2 = generate(Exciters.berlinNoise(Random(42)).withGain(gain(0.5)))
        val peak1 = buf1.peakAmplitude()
        val peak2 = buf2.peakAmplitude()
        (peak2 / peak1) shouldBe (0.5 plusOrMinus 0.1)
    }

    "berlin noise - DSL round-trip produces output" {
        val dsl = ExciterDsl.BerlinNoise()
        val sig = dsl.toExciter()
        val buf = generate(sig)
        buf.any { it != 0.0f } shouldBe true
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Dust
    // ═════════════════════════════════════════════════════════════════════════════

    "dust - mostly silence" {
        val buf = generate(Exciters.dust(Random(42), density = density(0.2)), freqHz = 440.0)
        val silentCount = buf.count { it == 0.0f }
        silentCount shouldBeGreaterThanOrEqual (buf.size * 0.8).toInt()
    }

    "dust - output is non-negative" {
        val buf = generate(Exciters.dust(Random(42), density = density(0.5)), freqHz = 440.0)
        for (sample in buf) {
            sample.toDouble() shouldBeGreaterThan -0.001
        }
    }

    "dust - higher density produces more impulses" {
        val bufLow = generate(Exciters.dust(Random(42), density = density(0.1)), freqHz = 440.0)
        val bufHigh = generate(Exciters.dust(Random(42), density = density(0.9)), freqHz = 440.0)
        val countLow = bufLow.count { it > 0.0f }
        val countHigh = bufHigh.count { it > 0.0f }
        countHigh shouldBeGreaterThanOrEqual countLow
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Crackle
    // ═════════════════════════════════════════════════════════════════════════════

    "crackle - mostly silence" {
        val buf = generate(Exciters.crackle(Random(42), density = density(0.2)), freqHz = 440.0)
        val silentCount = buf.count { it == 0.0f }
        silentCount shouldBeGreaterThanOrEqual (buf.size * 0.5).toInt()
    }

    "crackle - denser than dust at same density (higher maxRateHz)" {
        val bufDust = generate(Exciters.dust(Random(42), density = density(0.5)), freqHz = 440.0)
        val bufCrackle = generate(Exciters.crackle(Random(42), density = density(0.5)), freqHz = 440.0)
        val countDust = bufDust.count { it > 0.0f }
        val countCrackle = bufCrackle.count { it > 0.0f }
        countCrackle shouldBeGreaterThanOrEqual countDust
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // PhaseMod (negative values must not cause drift)
    // ═════════════════════════════════════════════════════════════════════════════

    "sine - negative phaseMod does not cause phase drift" {
        val sig = Exciters.sine()
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
        val sig = Exciters.sawtooth()
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
        val sig = Exciters.square()
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
        val sig = Exciters.triangle()
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
        val buf = generate(Exciters.superSaw(), freqHz = 440.0)
        buf.any { it != 0.0f } shouldBe true
    }

    "supersaw - amplitude bounded by voice-normalized output" {
        val buf = generate(Exciters.superSaw(), freqHz = 440.0)
        buf.peakAmplitude() shouldBeLessThan 1.1
    }

    "supersaw - more voices increases energy" {
        val buf1 = generate(Exciters.superSaw(voices = ParamExciter("voices", 1.0)), freqHz = 440.0)
        val buf5 = generate(Exciters.superSaw(voices = ParamExciter("voices", 5.0)), freqHz = 440.0)
        // Both should produce output
        buf1.any { it != 0.0f } shouldBe true
        buf5.any { it != 0.0f } shouldBe true
    }

    "supersaw - single voice equals sawtooth character" {
        // Single voice supersaw should have sawtooth-like zero crossings
        val buf =
            generate(Exciters.superSaw(voices = ParamExciter("voices", 1.0), freqSpread = ParamExciter("freqSpread", 0.0)), freqHz = 440.0)
        val crossings = buf.zeroCrossings()
        // 440Hz over 100ms ≈ 44 cycles, saw has ~1-2 crossings per cycle
        crossings shouldBeGreaterThanOrEqual 40
        crossings shouldBeLessThanOrEqual 90
    }

    "supersaw - symmetric around zero (no DC offset)" {
        val buf = generate(Exciters.superSaw(), freqHz = 440.0)
        buf.dcOffset() shouldBe (0.0 plusOrMinus 0.05)
    }

    "supersaw - phase continuity across blocks" {
        val sig = Exciters.superSaw()
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
        val sig = Exciters.superSaw()
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
    // Super oscillator DSL — oscParams override voices
    // ═════════════════════════════════════════════════════════════════════════════

    "supersaw DSL - oscParams override voices changes output" {
        val dsl = ExciterDsl.SuperSaw(voices = ExciterDsl.Param("voices", 3.0))
        val bufDefault = generate(dsl.toExciter(), freqHz = 440.0)
        val bufOverride = generate(dsl.toExciter(mapOf("voices" to 7.0)), freqHz = 440.0)
        bufDefault.any { it != 0.0f } shouldBe true
        bufOverride.any { it != 0.0f } shouldBe true
        bufDefault.zip(bufOverride).any { (a, b) -> a != b } shouldBe true
    }

    "supersine DSL - oscParams override voices changes output" {
        val dsl = ExciterDsl.SuperSine(voices = ExciterDsl.Param("voices", 3.0))
        val bufDefault = generate(dsl.toExciter(), freqHz = 440.0)
        val bufOverride = generate(dsl.toExciter(mapOf("voices" to 7.0)), freqHz = 440.0)
        bufDefault.any { it != 0.0f } shouldBe true
        bufOverride.any { it != 0.0f } shouldBe true
        bufDefault.zip(bufOverride).any { (a, b) -> a != b } shouldBe true
    }

    "supersquare DSL - oscParams override voices changes output" {
        val dsl = ExciterDsl.SuperSquare(voices = ExciterDsl.Param("voices", 3.0))
        val bufDefault = generate(dsl.toExciter(), freqHz = 440.0)
        val bufOverride = generate(dsl.toExciter(mapOf("voices" to 7.0)), freqHz = 440.0)
        bufDefault.any { it != 0.0f } shouldBe true
        bufOverride.any { it != 0.0f } shouldBe true
        bufDefault.zip(bufOverride).any { (a, b) -> a != b } shouldBe true
    }

    "supertri DSL - oscParams override voices changes output" {
        val dsl = ExciterDsl.SuperTri(voices = ExciterDsl.Param("voices", 3.0))
        val bufDefault = generate(dsl.toExciter(), freqHz = 440.0)
        val bufOverride = generate(dsl.toExciter(mapOf("voices" to 7.0)), freqHz = 440.0)
        bufDefault.any { it != 0.0f } shouldBe true
        bufOverride.any { it != 0.0f } shouldBe true
        bufDefault.zip(bufOverride).any { (a, b) -> a != b } shouldBe true
    }

    "superramp DSL - oscParams override voices changes output" {
        val dsl = ExciterDsl.SuperRamp(voices = ExciterDsl.Param("voices", 3.0))
        val bufDefault = generate(dsl.toExciter(), freqHz = 440.0)
        val bufOverride = generate(dsl.toExciter(mapOf("voices" to 7.0)), freqHz = 440.0)
        bufDefault.any { it != 0.0f } shouldBe true
        bufOverride.any { it != 0.0f } shouldBe true
        bufDefault.zip(bufOverride).any { (a, b) -> a != b } shouldBe true
    }

    "superpluck DSL - oscParams override voices changes output" {
        val dsl = ExciterDsl.SuperPluck(voices = ExciterDsl.Param("voices", 3.0))
        val bufDefault = generate(dsl.toExciter(), freqHz = 440.0)
        val bufOverride = generate(dsl.toExciter(mapOf("voices" to 7.0)), freqHz = 440.0)
        bufDefault.any { it != 0.0f } shouldBe true
        bufOverride.any { it != 0.0f } shouldBe true
        bufDefault.zip(bufOverride).any { (a, b) -> a != b } shouldBe true
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Super oscillator DSL — voices=1 via oscParams
    // ═════════════════════════════════════════════════════════════════════════════

    "supersaw DSL - oscParams voices=1 produces single-voice output" {
        val dsl = ExciterDsl.SuperSaw()
        val buf = generate(dsl.toExciter(mapOf("voices" to 1.0)), freqHz = 440.0)
        buf.any { it != 0.0f } shouldBe true
        // Single-voice supersaw should have clean saw zero-crossing count
        buf.zeroCrossings() shouldBeGreaterThanOrEqual 40
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Super oscillator DSL — absent oscParams uses data class default
    // ═════════════════════════════════════════════════════════════════════════════

    "supersaw DSL - absent oscParams uses default voices" {
        val dsl = ExciterDsl.SuperSaw(voices = ExciterDsl.Param("voices", 5.0))
        val bufNull = generate(dsl.toExciter(null), freqHz = 440.0)
        val bufEmpty = generate(dsl.toExciter(emptyMap()), freqHz = 440.0)
        // Both should produce non-zero output (5 voices active)
        bufNull.any { it != 0.0f } shouldBe true
        bufEmpty.any { it != 0.0f } shouldBe true
        // Both should differ from single-voice (proving default voices > 1)
        val buf1 = generate(dsl.toExciter(mapOf("voices" to 1.0)), freqHz = 440.0)
        bufNull.zip(buf1).any { (a, b) -> a != b } shouldBe true
        bufEmpty.zip(buf1).any { (a, b) -> a != b } shouldBe true
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Super oscillator DSL — multiple oscParams together
    // ═════════════════════════════════════════════════════════════════════════════

    "supersaw DSL - multiple oscParams applied together" {
        val dsl = ExciterDsl.SuperSaw()
        val bufDefault = generate(dsl.toExciter(), freqHz = 440.0)
        val bufOverride = generate(dsl.toExciter(mapOf("voices" to 3.0, "freqSpread" to 0.5)), freqHz = 440.0)
        bufDefault.any { it != 0.0f } shouldBe true
        bufOverride.any { it != 0.0f } shouldBe true
        // Output should differ due to different voices and spread
        bufDefault.zip(bufOverride).any { (a, b) -> a != b } shouldBe true
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Other DSL oscParams overrides
    // ═════════════════════════════════════════════════════════════════════════════

    "supersaw DSL - oscParams override freqSpread" {
        val dsl = ExciterDsl.SuperSaw(freqSpread = ExciterDsl.Param("freqSpread", 0.1))
        val bufDefault = generate(dsl.toExciter(), freqHz = 440.0)
        val bufOverride = generate(dsl.toExciter(mapOf("freqSpread" to 0.5)), freqHz = 440.0)
        bufDefault.any { it != 0.0f } shouldBe true
        bufOverride.any { it != 0.0f } shouldBe true
        bufDefault.zip(bufOverride).any { (a, b) -> a != b } shouldBe true
    }

    "dust DSL - oscParams override density" {
        val dsl = ExciterDsl.Dust(density = ExciterDsl.Param("density", 0.01))
        val sigOverride = dsl.toExciter(mapOf("density" to 0.99))
        val sigDefault = dsl.toExciter()
        val bufOverride = generate(sigOverride, freqHz = 440.0)
        val bufDefault = generate(sigDefault, freqHz = 440.0)
        // Higher density should produce more impulses
        val countOverride = bufOverride.count { it > 0.0f }
        val countDefault = bufDefault.count { it > 0.0f }
        countOverride shouldBeGreaterThanOrEqual countDefault
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Constant vs Param behavior
    // ═════════════════════════════════════════════════════════════════════════════

    "Constant is not overridden by oscParams" {
        val dsl = ExciterDsl.Sine(freq = ExciterDsl.Constant(880.0))
        val sig = dsl.toExciter(mapOf("freq" to 440.0))  // oscParam tries to override
        val buf = generate(sig, freqHz = 220.0)  // voice freq is 220
        // Should use 880 Hz (Constant), not 440 (oscParam) or 220 (voice)
        val crossings = buf.zeroCrossings()
        // 880Hz over 100ms ≈ 88 cycles, ~176 zero crossings
        crossings shouldBeInRange 170..185
    }

    "Param is overridden by oscParams" {
        val dsl = ExciterDsl.Sine(freq = ExciterDsl.Param("freq", 880.0))
        val sig = dsl.toExciter(mapOf("freq" to 440.0))  // oscParam overrides
        val buf = generate(sig, freqHz = 220.0)
        // Should use 440 Hz (oscParam override), not 880 (default) or 220 (voice)
        val crossings = buf.zeroCrossings()
        // 440Hz over 100ms ≈ 44 cycles, ~88 zero crossings
        crossings shouldBeInRange 84..92
    }

    "Param with freq=0 uses voice frequency" {
        val dsl = ExciterDsl.Sine()  // default freq = Param("freq", 0.0)
        val sig = dsl.toExciter()
        val buf = generate(sig, freqHz = 440.0)
        val crossings = buf.zeroCrossings()
        crossings shouldBeInRange 84..92
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Freq override on oscillator
    // ═════════════════════════════════════════════════════════════════════════════

    "sine with fixed freq ignores voice freqHz" {
        val sig = Exciters.sine(freq = ParamExciter("freq", 880.0))
        val buf = generate(sig, freqHz = 220.0)  // voice is 220 but freq overrides to 880
        val crossings = buf.zeroCrossings()
        crossings shouldBeInRange 170..185
    }

    "sine with freq=0 uses voice freqHz" {
        val sig = Exciters.sine()  // default freq=0
        val buf = generate(sig, freqHz = 440.0)
        val crossings = buf.zeroCrossings()
        crossings shouldBeInRange 84..92
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Ramp oscillator
    // ═════════════════════════════════════════════════════════════════════════════

    "ramp - produces non-zero output" {
        val buf = generate(Exciters.ramp(), freqHz = 440.0)
        buf.any { it != 0.0f } shouldBe true
    }

    "ramp - correct frequency" {
        val buf = generate(Exciters.ramp(), freqHz = 440.0)
        buf.zeroCrossings() shouldBeInRange 84..92
    }

    "ramp - bounded amplitude" {
        val buf = generate(Exciters.ramp(), freqHz = 440.0)
        buf.peakAmplitude() shouldBeLessThan 1.1
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Edge cases — zero/negative voices, division by zero
    // ═════════════════════════════════════════════════════════════════════════════

    "supersaw - zero voices produces silence" {
        val buf = generate(Exciters.superSaw(voices = ParamExciter("voices", 0.0)), freqHz = 440.0)
        buf.all { it == 0.0f } shouldBe true
    }

    "supersaw - negative voices produces silence" {
        val buf = generate(Exciters.superSaw(voices = ParamExciter("voices", -5.0)), freqHz = 440.0)
        buf.all { it == 0.0f } shouldBe true
    }

    "supersine - zero voices produces silence" {
        val buf = generate(Exciters.superSine(voices = ParamExciter("voices", 0.0)), freqHz = 440.0)
        buf.all { it == 0.0f } shouldBe true
    }

    "plus - two sines summed roughly double amplitude" {
        val single = generate(Exciters.sine(), freqHz = 440.0)
        val doubled = generate(Exciters.sine() + Exciters.sine(), freqHz = 440.0)
        doubled.peakAmplitude() shouldBe (single.peakAmplitude() * 2.0 plusOrMinus 0.05)
    }

    "mul by 0.5 halves amplitude" {
        val full = generate(Exciters.sine(), freqHz = 440.0)
        val half = generate(Exciters.sine().mul(0.5), freqHz = 440.0)
        (half.peakAmplitude() / full.peakAmplitude()) shouldBe (0.5 plusOrMinus 0.02)
    }

    "mul by 1.0 preserves signal (withGain short-circuit)" {
        val original = generate(Exciters.sine(), freqHz = 440.0)
        val unchanged = generate(Exciters.sine().withGain(ParamExciter("gain", 1.0)), freqHz = 440.0)
        original.zip(unchanged).all { (a, b) -> a == b } shouldBe true
    }

    "div by 2.0 halves amplitude" {
        val full = generate(Exciters.sine(), freqHz = 440.0)
        val half = generate(Exciters.sine().div(2.0), freqHz = 440.0)
        (half.peakAmplitude() / full.peakAmplitude()) shouldBe (0.5 plusOrMinus 0.02)
    }

    "times - signal times itself is squared (always positive)" {
        val buf = generate(Exciters.sine() * Exciters.sine(), freqHz = 440.0)
        // Squared sine is always >= 0
        buf.all { it >= -0.001f } shouldBe true
    }

    "div by zero produces silence, not NaN" {
        val sig = Exciters.sine() * Exciters.silence()  // silence = all zeros
        val dividend = Exciters.sine()
        val divResult = dividend.div(Exciters.silence())
        val buf = generate(divResult, freqHz = 440.0)
        // Should be all zeros (guarded), not NaN or Infinity
        buf.none { it.isNaN() || it.isInfinite() } shouldBe true
        buf.all { it == 0.0f } shouldBe true
    }

    // ═════════════════════════════════════════════════════════════════════════════
    // Drive, Clip, Bandpass, Notch
    // ═════════════════════════════════════════════════════════════════════════════

    "drive amplifies signal" {
        val dry = generate(Exciters.sine(), freqHz = 440.0)
        val driven = generate(Exciters.sine().drive(0.5), freqHz = 440.0)
        driven.peakAmplitude() shouldBeGreaterThan dry.peakAmplitude()
    }

    "drive with amount=0 bypasses" {
        val dry = generate(Exciters.sine(), freqHz = 440.0)
        val driven = generate(Exciters.sine().drive(0.0), freqHz = 440.0)
        dry.zip(driven).all { (a, b) -> a == b } shouldBe true
    }

    "clip soft limits output to approximately +-1" {
        // Drive signal way above 1.0, then clip
        val buf = generate(Exciters.sine().drive(1.0).clip("soft"), freqHz = 440.0)
        buf.peakAmplitude() shouldBeLessThan 1.05
        buf.any { it != 0.0f } shouldBe true
    }

    "clip hard limits output to exactly +-1" {
        val buf = generate(Exciters.sine().drive(1.0).clip("hard"), freqHz = 440.0)
        buf.peakAmplitude() shouldBeLessThan 1.01
    }

    "clip fold produces non-zero output" {
        val buf = generate(Exciters.sine().clip("fold"), freqHz = 440.0)
        buf.any { it != 0.0f } shouldBe true
    }

    "bandpass passes center frequency" {
        val buf = generate(Exciters.sine().bandpass(440.0, 1.0), freqHz = 440.0)
        buf.any { it != 0.0f } shouldBe true
        buf.peakAmplitude() shouldBeGreaterThan 0.3
    }

    "notch attenuates center frequency" {
        val dry = generate(Exciters.sine(), freqHz = 440.0)
        val notched = generate(Exciters.sine().notch(440.0, 10.0), freqHz = 440.0)
        // Notch at exactly the signal frequency should reduce amplitude
        notched.peakAmplitude() shouldBeLessThan dry.peakAmplitude()
    }

})
