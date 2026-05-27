package io.peekandpoke.klang.audio_be.filters

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.kotest.matchers.types.shouldBeInstanceOf
import io.peekandpoke.klang.audio_be.AudioBuffer
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class LowPassHighPassFiltersSpec : StringSpec({

    val sampleRate = 44100.0
    val blockFrames = 4096

    /** Generate a sine wave at [freq] Hz into a AudioBuffer of [length] samples. */
    fun sine(freq: Double, length: Int, amplitude: Double = 1.0): AudioBuffer {
        return AudioBuffer(length) { i ->
            (amplitude * sin(2.0 * PI * freq * i / sampleRate))
        }
    }

    /** RMS of an entire AudioBuffer. */
    fun rms(buf: AudioBuffer): Double {
        if (buf.isEmpty()) return 0.0
        val sumSq = buf.fold(0.0) { acc, v -> acc + v * v }
        return sqrt(sumSq / buf.size)
    }

    // -----------------------------------------------------------------------
    // OnePoleLPF
    // -----------------------------------------------------------------------

    "OnePoleLPF - high-frequency sine is attenuated" {
        val filter = LowPassHighPassFilters.OnePoleLPF(cutoffHz = 1000.0, sampleRate = sampleRate)
        val buf = sine(freq = 10000.0, length = blockFrames)
        val inputRms = rms(buf)

        filter.process(buf, 0, buf.size)
        val outputRms = rms(buf)

        // 10 kHz signal through a 1 kHz LPF should be heavily attenuated
        outputRms shouldBeLessThan (inputRms * 0.3)
    }

    "OnePoleLPF - low-frequency sine passes through" {
        val filter = LowPassHighPassFilters.OnePoleLPF(cutoffHz = 5000.0, sampleRate = sampleRate)
        val buf = sine(freq = 100.0, length = blockFrames)
        val inputRms = rms(buf)

        filter.process(buf, 0, buf.size)
        val outputRms = rms(buf)

        // 100 Hz signal through a 5 kHz LPF should pass with minimal loss
        outputRms shouldBeGreaterThan (inputRms * 0.9)
    }

    "OnePoleLPF - setCutoff changes behavior" {
        val filter = LowPassHighPassFilters.OnePoleLPF(cutoffHz = 20000.0, sampleRate = sampleRate)

        // With a very high cutoff, 5 kHz passes easily
        val buf1 = sine(freq = 5000.0, length = blockFrames)
        filter.process(buf1, 0, buf1.size)
        val rmsHighCutoff = rms(buf1)

        // Lower the cutoff dramatically
        filter.setCutoff(200.0)

        // Need a fresh filter state pass — process multiple blocks to settle
        repeat(3) {
            val settle = sine(freq = 5000.0, length = blockFrames)
            filter.process(settle, 0, settle.size)
        }
        val buf2 = sine(freq = 5000.0, length = blockFrames)
        filter.process(buf2, 0, buf2.size)
        val rmsLowCutoff = rms(buf2)

        // With 200 Hz cutoff, 5 kHz should be much more attenuated
        rmsLowCutoff shouldBeLessThan (rmsHighCutoff * 0.5)
    }

    "OnePoleLPF - zero-length buffer does not crash" {
        val filter = LowPassHighPassFilters.OnePoleLPF(cutoffHz = 1000.0, sampleRate = sampleRate)
        val buf = AudioBuffer(0)
        filter.process(buf, 0, 0)
        // No exception means success
    }

    "OnePoleLPF - processes with offset correctly" {
        val filter = LowPassHighPassFilters.OnePoleLPF(cutoffHz = 1000.0, sampleRate = sampleRate)
        val buf = AudioBuffer(blockFrames * 2)
        // Fill second half with a high-freq sine
        for (i in blockFrames until blockFrames * 2) {
            buf[i] = sin(2.0 * PI * 10000.0 * i / sampleRate)
        }
        // Process only the second half
        filter.process(buf, blockFrames, blockFrames)
        // First half should remain zeros
        for (i in 0 until blockFrames) {
            buf[i] shouldBe 0.0
        }
    }

    // -----------------------------------------------------------------------
    // OnePoleHPF
    // -----------------------------------------------------------------------

    "OnePoleHPF - low-frequency content is attenuated" {
        val filter = LowPassHighPassFilters.OnePoleHPF(cutoffHz = 5000.0, sampleRate = sampleRate)
        val buf = sine(freq = 100.0, length = blockFrames)
        val inputRms = rms(buf)

        filter.process(buf, 0, buf.size)
        val outputRms = rms(buf)

        // 100 Hz through a 5 kHz HPF should be heavily attenuated
        outputRms shouldBeLessThan (inputRms * 0.3)
    }

    "OnePoleHPF - high-frequency content passes through" {
        val filter = LowPassHighPassFilters.OnePoleHPF(cutoffHz = 200.0, sampleRate = sampleRate)
        val buf = sine(freq = 10000.0, length = blockFrames)
        val inputRms = rms(buf)

        filter.process(buf, 0, buf.size)
        val outputRms = rms(buf)

        // 10 kHz through a 200 Hz HPF should pass with minimal loss
        outputRms shouldBeGreaterThan (inputRms * 0.85)
    }

    "OnePoleHPF - setCutoff changes behavior" {
        val filter = LowPassHighPassFilters.OnePoleHPF(cutoffHz = 5.0, sampleRate = sampleRate)

        // With very low cutoff, 500 Hz passes
        val buf1 = sine(freq = 500.0, length = blockFrames)
        filter.process(buf1, 0, buf1.size)
        val rmsLowCutoff = rms(buf1)

        // Raise cutoff well above the signal
        filter.setCutoff(15000.0)

        repeat(3) {
            val settle = sine(freq = 500.0, length = blockFrames)
            filter.process(settle, 0, settle.size)
        }
        val buf2 = sine(freq = 500.0, length = blockFrames)
        filter.process(buf2, 0, buf2.size)
        val rmsHighCutoff = rms(buf2)

        // With 15 kHz cutoff, 500 Hz should be more attenuated
        rmsHighCutoff shouldBeLessThan (rmsLowCutoff * 0.5)
    }

    "OnePoleHPF - zero-length buffer does not crash" {
        val filter = LowPassHighPassFilters.OnePoleHPF(cutoffHz = 1000.0, sampleRate = sampleRate)
        val buf = AudioBuffer(0)
        filter.process(buf, 0, 0)
    }

    // -----------------------------------------------------------------------
    // SvfLPF
    // -----------------------------------------------------------------------

    "SvfLPF - attenuates above cutoff" {
        val filter = LowPassHighPassFilters.SvfLPF(cutoffHz = 1000.0, q = 1.0, sampleRate = sampleRate)
        val buf = sine(freq = 10000.0, length = blockFrames)
        val inputRms = rms(buf)

        filter.process(buf, 0, buf.size)
        val outputRms = rms(buf)

        outputRms shouldBeLessThan (inputRms * 0.15)
    }

    "SvfLPF - passes below cutoff" {
        val filter = LowPassHighPassFilters.SvfLPF(cutoffHz = 5000.0, q = 1.0, sampleRate = sampleRate)
        val buf = sine(freq = 100.0, length = blockFrames)
        val inputRms = rms(buf)

        filter.process(buf, 0, buf.size)
        val outputRms = rms(buf)

        outputRms shouldBeGreaterThan (inputRms * 0.85)
    }

    "SvfLPF - setCutoff updates behavior" {
        val filter = LowPassHighPassFilters.SvfLPF(cutoffHz = 20000.0, q = 1.0, sampleRate = sampleRate)

        val buf1 = sine(freq = 5000.0, length = blockFrames)
        filter.process(buf1, 0, buf1.size)
        val rmsWide = rms(buf1)

        filter.setCutoff(200.0)
        repeat(3) {
            val settle = sine(freq = 5000.0, length = blockFrames)
            filter.process(settle, 0, settle.size)
        }
        val buf2 = sine(freq = 5000.0, length = blockFrames)
        filter.process(buf2, 0, buf2.size)
        val rmsNarrow = rms(buf2)

        rmsNarrow shouldBeLessThan (rmsWide * 0.3)
    }

    "SvfLPF - cutoff at 5 Hz edge case" {
        val filter = LowPassHighPassFilters.SvfLPF(cutoffHz = 5.0, q = 1.0, sampleRate = sampleRate)
        val buf = sine(freq = 1000.0, length = blockFrames)
        filter.process(buf, 0, buf.size)
        // Should heavily attenuate 1 kHz at 5 Hz cutoff
        rms(buf) shouldBeLessThan 0.01
    }

    "SvfLPF - cutoff near Nyquist" {
        val nyquist = sampleRate / 2.0
        val filter = LowPassHighPassFilters.SvfLPF(cutoffHz = nyquist - 10.0, q = 1.0, sampleRate = sampleRate)
        val buf = sine(freq = 1000.0, length = blockFrames)
        val inputRms = rms(buf)
        filter.process(buf, 0, buf.size)
        // Near Nyquist cutoff should pass everything below it
        rms(buf) shouldBeGreaterThan (inputRms * 0.8)
    }

    "SvfLPF - Q at 0.1 does not crash" {
        val filter = LowPassHighPassFilters.SvfLPF(cutoffHz = 1000.0, q = 0.1, sampleRate = sampleRate)
        val buf = sine(freq = 500.0, length = blockFrames)
        filter.process(buf, 0, buf.size)
        // Just verifying no NaN or Inf
        buf.none { it.isNaN() || it.isInfinite() } shouldBe true
    }

    "SvfLPF - Q at 50.0 does not crash" {
        val filter = LowPassHighPassFilters.SvfLPF(cutoffHz = 1000.0, q = 50.0, sampleRate = sampleRate)
        val buf = sine(freq = 500.0, length = blockFrames)
        filter.process(buf, 0, buf.size)
        buf.none { it.isNaN() || it.isInfinite() } shouldBe true
    }

    "SvfLPF - zero-length buffer does not crash" {
        val filter = LowPassHighPassFilters.SvfLPF(cutoffHz = 1000.0, q = 1.0, sampleRate = sampleRate)
        filter.process(AudioBuffer(0), 0, 0)
    }

    // -----------------------------------------------------------------------
    // SvfHPF
    // -----------------------------------------------------------------------

    "SvfHPF - attenuates below cutoff" {
        val filter = LowPassHighPassFilters.SvfHPF(cutoffHz = 5000.0, q = 1.0, sampleRate = sampleRate)
        val buf = sine(freq = 100.0, length = blockFrames)
        val inputRms = rms(buf)

        filter.process(buf, 0, buf.size)
        val outputRms = rms(buf)

        outputRms shouldBeLessThan (inputRms * 0.1)
    }

    "SvfHPF - passes above cutoff" {
        val filter = LowPassHighPassFilters.SvfHPF(cutoffHz = 200.0, q = 1.0, sampleRate = sampleRate)
        val buf = sine(freq = 10000.0, length = blockFrames)
        val inputRms = rms(buf)

        filter.process(buf, 0, buf.size)
        val outputRms = rms(buf)

        outputRms shouldBeGreaterThan (inputRms * 0.85)
    }

    "SvfHPF - setCutoff updates behavior" {
        val filter = LowPassHighPassFilters.SvfHPF(cutoffHz = 5.0, q = 1.0, sampleRate = sampleRate)

        val buf1 = sine(freq = 500.0, length = blockFrames)
        filter.process(buf1, 0, buf1.size)
        val rmsLowCutoff = rms(buf1)

        filter.setCutoff(15000.0)
        repeat(3) {
            val settle = sine(freq = 500.0, length = blockFrames)
            filter.process(settle, 0, settle.size)
        }
        val buf2 = sine(freq = 500.0, length = blockFrames)
        filter.process(buf2, 0, buf2.size)
        val rmsHighCutoff = rms(buf2)

        rmsHighCutoff shouldBeLessThan (rmsLowCutoff * 0.3)
    }

    "SvfHPF - cutoff at 5 Hz edge case" {
        val filter = LowPassHighPassFilters.SvfHPF(cutoffHz = 5.0, q = 1.0, sampleRate = sampleRate)
        val buf = sine(freq = 1000.0, length = blockFrames)
        val inputRms = rms(buf)
        filter.process(buf, 0, buf.size)
        // 5 Hz HPF should pass 1 kHz
        rms(buf) shouldBeGreaterThan (inputRms * 0.8)
    }

    "SvfHPF - cutoff near Nyquist" {
        val nyquist = sampleRate / 2.0
        val filter = LowPassHighPassFilters.SvfHPF(cutoffHz = nyquist - 10.0, q = 1.0, sampleRate = sampleRate)
        val buf = sine(freq = 1000.0, length = blockFrames)
        filter.process(buf, 0, buf.size)
        // Near Nyquist cutoff should attenuate 1 kHz
        rms(buf) shouldBeLessThan 0.05
    }

    "SvfHPF - Q extremes do not crash" {
        for (q in listOf(0.1, 50.0)) {
            val filter = LowPassHighPassFilters.SvfHPF(cutoffHz = 1000.0, q = q, sampleRate = sampleRate)
            val buf = sine(freq = 500.0, length = blockFrames)
            filter.process(buf, 0, buf.size)
            buf.none { it.isNaN() || it.isInfinite() } shouldBe true
        }
    }

    // -----------------------------------------------------------------------
    // SvfBPF
    // -----------------------------------------------------------------------

    "SvfBPF - passes signal near center frequency" {
        val filter = LowPassHighPassFilters.SvfBPF(cutoffHz = 1000.0, q = 1.0, sampleRate = sampleRate)
        val buf = sine(freq = 1000.0, length = blockFrames)
        val inputRms = rms(buf)

        filter.process(buf, 0, buf.size)
        val outputRms = rms(buf)

        // Signal at center frequency should pass with reasonable level
        outputRms shouldBeGreaterThan (inputRms * 0.3)
    }

    "SvfBPF - attenuates signal far below center" {
        val filter = LowPassHighPassFilters.SvfBPF(cutoffHz = 5000.0, q = 2.0, sampleRate = sampleRate)
        val buf = sine(freq = 100.0, length = blockFrames)
        val inputRms = rms(buf)

        filter.process(buf, 0, buf.size)
        val outputRms = rms(buf)

        outputRms shouldBeLessThan (inputRms * 0.15)
    }

    "SvfBPF - attenuates signal far above center" {
        val filter = LowPassHighPassFilters.SvfBPF(cutoffHz = 500.0, q = 2.0, sampleRate = sampleRate)
        val buf = sine(freq = 15000.0, length = blockFrames)
        val inputRms = rms(buf)

        filter.process(buf, 0, buf.size)
        val outputRms = rms(buf)

        outputRms shouldBeLessThan (inputRms * 0.15)
    }

    "SvfBPF - setCutoff updates behavior" {
        val filter = LowPassHighPassFilters.SvfBPF(cutoffHz = 1000.0, q = 2.0, sampleRate = sampleRate)

        // Center at 1 kHz - 1 kHz signal passes well
        val buf1 = sine(freq = 1000.0, length = blockFrames)
        filter.process(buf1, 0, buf1.size)
        val rmsCenter = rms(buf1)

        // Move center far away from 1 kHz
        filter.setCutoff(15000.0)
        repeat(3) {
            val settle = sine(freq = 1000.0, length = blockFrames)
            filter.process(settle, 0, settle.size)
        }
        val buf2 = sine(freq = 1000.0, length = blockFrames)
        filter.process(buf2, 0, buf2.size)
        val rmsOffCenter = rms(buf2)

        rmsOffCenter shouldBeLessThan (rmsCenter * 0.5)
    }

    "SvfBPF - Q extremes do not crash" {
        for (q in listOf(0.1, 50.0)) {
            val filter = LowPassHighPassFilters.SvfBPF(cutoffHz = 1000.0, q = q, sampleRate = sampleRate)
            val buf = sine(freq = 1000.0, length = blockFrames)
            filter.process(buf, 0, buf.size)
            buf.none { it.isNaN() || it.isInfinite() } shouldBe true
        }
    }

    // -----------------------------------------------------------------------
    // SvfNotch
    // -----------------------------------------------------------------------

    "SvfNotch - attenuates signal at notch frequency" {
        val filter = LowPassHighPassFilters.SvfNotch(cutoffHz = 1000.0, q = 5.0, sampleRate = sampleRate)
        val buf = sine(freq = 1000.0, length = blockFrames)
        val inputRms = rms(buf)

        filter.process(buf, 0, buf.size)
        val outputRms = rms(buf)

        // Signal at the notch should be significantly attenuated
        outputRms shouldBeLessThan (inputRms * 0.3)
    }

    "SvfNotch - passes signal far from notch frequency" {
        val filter = LowPassHighPassFilters.SvfNotch(cutoffHz = 5000.0, q = 2.0, sampleRate = sampleRate)
        val buf = sine(freq = 200.0, length = blockFrames)
        val inputRms = rms(buf)

        filter.process(buf, 0, buf.size)
        val outputRms = rms(buf)

        // 200 Hz should pass through a 5 kHz notch with little attenuation
        outputRms shouldBeGreaterThan (inputRms * 0.8)
    }

    "SvfNotch - setCutoff updates behavior" {
        val filter = LowPassHighPassFilters.SvfNotch(cutoffHz = 1000.0, q = 5.0, sampleRate = sampleRate)

        // Notch at 1 kHz - 1 kHz is attenuated
        val buf1 = sine(freq = 1000.0, length = blockFrames)
        filter.process(buf1, 0, buf1.size)
        val rmsAtNotch = rms(buf1)

        // Move notch away from 1 kHz
        filter.setCutoff(10000.0)
        repeat(3) {
            val settle = sine(freq = 1000.0, length = blockFrames)
            filter.process(settle, 0, settle.size)
        }
        val buf2 = sine(freq = 1000.0, length = blockFrames)
        filter.process(buf2, 0, buf2.size)
        val rmsAwayFromNotch = rms(buf2)

        // After moving the notch away, 1 kHz should pass better
        rmsAwayFromNotch shouldBeGreaterThan (rmsAtNotch * 2.0)
    }

    "SvfNotch - Q extremes do not crash" {
        for (q in listOf(0.1, 50.0)) {
            val filter = LowPassHighPassFilters.SvfNotch(cutoffHz = 1000.0, q = q, sampleRate = sampleRate)
            val buf = sine(freq = 1000.0, length = blockFrames)
            filter.process(buf, 0, buf.size)
            buf.none { it.isNaN() || it.isInfinite() } shouldBe true
        }
    }

    // -----------------------------------------------------------------------
    // Factory methods
    // -----------------------------------------------------------------------

    "createLPF with null Q returns OnePoleLPF" {
        val filter = LowPassHighPassFilters.createLPF(cutoffHz = 1000.0, q = null, sampleRate = sampleRate)
        filter.shouldBeInstanceOf<LowPassHighPassFilters.OnePoleLPF>()
    }

    "createLPF with non-null Q returns SvfLPF" {
        val filter = LowPassHighPassFilters.createLPF(cutoffHz = 1000.0, q = 2.0, sampleRate = sampleRate)
        filter.shouldBeInstanceOf<LowPassHighPassFilters.SvfLPF>()
    }

    "createHPF with null Q returns OnePoleHPF" {
        val filter = LowPassHighPassFilters.createHPF(cutoffHz = 1000.0, q = null, sampleRate = sampleRate)
        filter.shouldBeInstanceOf<LowPassHighPassFilters.OnePoleHPF>()
    }

    "createHPF with non-null Q returns SvfHPF" {
        val filter = LowPassHighPassFilters.createHPF(cutoffHz = 1000.0, q = 2.0, sampleRate = sampleRate)
        filter.shouldBeInstanceOf<LowPassHighPassFilters.SvfHPF>()
    }

    "createBPF with null Q returns SvfBPF with default Q of 1.0" {
        val filter = LowPassHighPassFilters.createBPF(cutoffHz = 1000.0, q = null, sampleRate = sampleRate)
        filter.shouldBeInstanceOf<LowPassHighPassFilters.SvfBPF>()
    }

    "createBPF with non-null Q returns SvfBPF" {
        val filter = LowPassHighPassFilters.createBPF(cutoffHz = 1000.0, q = 5.0, sampleRate = sampleRate)
        filter.shouldBeInstanceOf<LowPassHighPassFilters.SvfBPF>()
    }

    "createNotch with null Q returns SvfNotch with default Q of 1.0" {
        val filter = LowPassHighPassFilters.createNotch(cutoffHz = 1000.0, q = null, sampleRate = sampleRate)
        filter.shouldBeInstanceOf<LowPassHighPassFilters.SvfNotch>()
    }

    "createNotch with non-null Q returns SvfNotch" {
        val filter = LowPassHighPassFilters.createNotch(cutoffHz = 1000.0, q = 5.0, sampleRate = sampleRate)
        filter.shouldBeInstanceOf<LowPassHighPassFilters.SvfNotch>()
    }

    // -----------------------------------------------------------------------
    // All filters implement AudioFilter and Tunable
    // -----------------------------------------------------------------------

    "all filter types implement AudioFilter" {
        val filters = listOf(
            LowPassHighPassFilters.createLPF(1000.0, null, sampleRate),
            LowPassHighPassFilters.createLPF(1000.0, 1.0, sampleRate),
            LowPassHighPassFilters.createHPF(1000.0, null, sampleRate),
            LowPassHighPassFilters.createHPF(1000.0, 1.0, sampleRate),
            LowPassHighPassFilters.createBPF(1000.0, 1.0, sampleRate),
            LowPassHighPassFilters.createNotch(1000.0, 1.0, sampleRate),
        )
        for (f in filters) {
            f.shouldBeInstanceOf<AudioFilter>()
        }
    }

    "all filter types implement AudioFilter.Tunable" {
        val filters = listOf(
            LowPassHighPassFilters.createLPF(1000.0, null, sampleRate),
            LowPassHighPassFilters.createLPF(1000.0, 1.0, sampleRate),
            LowPassHighPassFilters.createHPF(1000.0, null, sampleRate),
            LowPassHighPassFilters.createHPF(1000.0, 1.0, sampleRate),
            LowPassHighPassFilters.createBPF(1000.0, 1.0, sampleRate),
            LowPassHighPassFilters.createNotch(1000.0, 1.0, sampleRate),
        )
        for (f in filters) {
            f.shouldBeInstanceOf<AudioFilter.Tunable>()
        }
    }

    // -----------------------------------------------------------------------
    // Round 3 (2026-04-29): NaN injection, parity, topology identity
    // -----------------------------------------------------------------------

    "SVF — NaN cutoff is guarded (state stays finite)" {
        // Same Round-1-class regression guard as for OnePoleLPF/HPF: a non-finite
        // cutoff must not poison the IIR state. `bilinearK` falls back to 1000 Hz.
        val filter = LowPassHighPassFilters.SvfLPF(cutoffHz = Double.NaN, q = 1.0, sampleRate = sampleRate)
        val buf = sine(freq = 440.0, length = blockFrames)
        filter.process(buf, 0, buf.size)
        for (v in buf) {
            v.isFinite() shouldBe true
        }
        // setCutoff with NaN must also recover.
        filter.setCutoff(Double.NaN)
        val buf2 = sine(freq = 440.0, length = blockFrames)
        filter.process(buf2, 0, buf2.size)
        for (v in buf2) {
            v.isFinite() shouldBe true
        }
    }

    "SVF — class form and Ignitor form produce equivalent steady-state output" {
        // Parity guard so the two implementations don't drift apart in future rounds.
        // Both share `computeSvfCoeffs`; with identical input + cutoff + Q the output
        // should match within tight float tolerance after the transient has decayed.
        val cutoff = 800.0
        val q = 1.5
        val classFilter = LowPassHighPassFilters.SvfLPF(cutoff, q, sampleRate)

        // Build the Ignitor side via direct kernel application — recreate the same
        // per-sample math by feeding identical input through a fresh state.
        val coefs = SvfCoeffs()
        computeSvfCoeffs(cutoff, q, sampleRate, coefs)
        var ic1eq = 0.0
        var ic2eq = 0.0

        val classOut = sine(freq = 440.0, length = blockFrames)
        val refOut = AudioBuffer(blockFrames) { classOut[it] }
        classFilter.process(classOut, 0, blockFrames)

        for (i in 0 until blockFrames) {
            val v0 = refOut[i]
            val v3 = v0 - ic2eq
            val v1 = coefs.a1 * ic1eq + coefs.a2 * v3
            val v2 = ic2eq + coefs.a2 * ic1eq + coefs.a3 * v3
            ic1eq = 2.0 * v1 - ic1eq
            ic2eq = 2.0 * v2 - ic2eq
            refOut[i] = v2 // LPF tap
        }

        // Skip the first 64 samples (transient); compare the steady-state region.
        var maxAbsDiff = 0.0
        for (i in 64 until blockFrames) {
            val d = kotlin.math.abs(classOut[i] - refOut[i])
            if (d > maxAbsDiff) maxAbsDiff = d
        }
        maxAbsDiff shouldBeLessThan 1e-9
    }

    "SVF — topology identity: notch[n] == lp[n] + hp[n]" {
        // Algebraic invariant from the TPT-SVF state-update: notch tap (`v0 − k·v1`)
        // equals LPF tap (`v2`) plus HPF tap (`v0 − k·v1 − v2`). Catches output-tap
        // regressions during the dedup refactor.
        val cutoff = 1500.0
        val q = 0.7
        val lp = LowPassHighPassFilters.SvfLPF(cutoff, q, sampleRate)
        val hp = LowPassHighPassFilters.SvfHPF(cutoff, q, sampleRate)
        val notch = LowPassHighPassFilters.SvfNotch(cutoff, q, sampleRate)

        val src = sine(freq = 800.0, length = blockFrames)
        val lpBuf = AudioBuffer(blockFrames) { src[it] }
        val hpBuf = AudioBuffer(blockFrames) { src[it] }
        val notchBuf = AudioBuffer(blockFrames) { src[it] }

        lp.process(lpBuf, 0, blockFrames)
        hp.process(hpBuf, 0, blockFrames)
        notch.process(notchBuf, 0, blockFrames)

        var maxAbsDiff = 0.0
        for (i in 0 until blockFrames) {
            val sum = lpBuf[i] + hpBuf[i]
            val d = kotlin.math.abs(sum - notchBuf[i])
            if (d > maxAbsDiff) maxAbsDiff = d
        }
        maxAbsDiff shouldBeLessThan 1e-12
    }

    // -----------------------------------------------------------------------
    // SvfLPF — feedback saturation (Phase 7)
    //
    // When `analog > 0`, the bandpass intermediate `v1` is run through
    // `ClippingFuncs.fastTanh` before being stored in `ic1eq`. Tames the
    // mathematical-pole-pair resonance, generates harmonics on saturation,
    // and makes velocity feel real through the filter.
    // -----------------------------------------------------------------------

    "SvfLPF analog=0 - bit-identical linear path" {
        // analog=0 path must be byte-for-byte equal to a clean SVF — no surprise
        // changes for patches that don't opt in.
        val ref = LowPassHighPassFilters.SvfLPF(800.0, q = 5.0, sampleRate = sampleRate, analog = 0.0)
        val sat0 = LowPassHighPassFilters.SvfLPF(800.0, q = 5.0, sampleRate = sampleRate, analog = 0.0)

        val refBuf = sine(freq = 800.0, length = 1024, amplitude = 0.8)
        val satBuf = AudioBuffer(1024) { i -> refBuf[i] }

        ref.process(refBuf, 0, refBuf.size)
        sat0.process(satBuf, 0, satBuf.size)

        for (i in 0 until 1024) {
            satBuf[i] shouldBe refBuf[i]
        }
    }

    "SvfLPF analog>0 - bit-identical to analog=0 (saturation currently disabled)" {
        // Filter saturation is currently a no-op — analog>0 produces the same output as
        // analog=0. The infrastructure remains for the future "warmth on output" /
        // proper-ladder-filter approach. See plastic-pipe-hunt.md backlog.
        val linear = LowPassHighPassFilters.SvfLPF(800.0, q = 5.0, sampleRate = sampleRate, analog = 0.0)
        val withAnalog = LowPassHighPassFilters.SvfLPF(800.0, q = 5.0, sampleRate = sampleRate, analog = 5.0)

        val linBuf = sine(freq = 800.0, length = blockFrames, amplitude = 1.0)
        val anaBuf = AudioBuffer(blockFrames) { i -> linBuf[i] }

        linear.process(linBuf, 0, linBuf.size)
        withAnalog.process(anaBuf, 0, anaBuf.size)

        for (i in 0 until blockFrames) {
            anaBuf[i] shouldBe linBuf[i]
        }
    }

    "SvfHPF analog=0 - bit-identical linear path" {
        val ref = LowPassHighPassFilters.SvfHPF(800.0, q = 5.0, sampleRate = sampleRate, analog = 0.0)
        val sat0 = LowPassHighPassFilters.SvfHPF(800.0, q = 5.0, sampleRate = sampleRate, analog = 0.0)

        val refBuf = sine(freq = 800.0, length = 1024, amplitude = 0.8)
        val satBuf = AudioBuffer(1024) { i -> refBuf[i] }

        ref.process(refBuf, 0, refBuf.size)
        sat0.process(satBuf, 0, satBuf.size)

        for (i in 0 until 1024) {
            satBuf[i] shouldBe refBuf[i]
        }
    }

    "SvfLPF analog>0 - no DC or sub-bass accumulation under hot resonance (DC-purity diagnostic)" {
        // Diagnostic for the "tanh in feedback loop pumps DC" risk: feed a perfectly
        // symmetric sine through a saturated, high-Q LPF. Symmetric tanh of a symmetric
        // input should produce zero DC and only odd harmonics (3k, 5k, …) above the input
        // frequency. Strong DC or sub-bass would indicate either an asymmetry bug in the
        // saturation curve or DC pumping in the feedback loop — neither of which should
        // happen with our `fastTanh` (symmetric) on the BPF intermediate `v1` (which has
        // zero DC gain by construction).
        val filter = LowPassHighPassFilters.SvfLPF(2000.0, q = 5.0, sampleRate = sampleRate, analog = 5.0)
        val buf = sine(freq = 1000.0, length = blockFrames, amplitude = 1.0)
        filter.process(buf, 0, buf.size)

        // Skip first half (filter settling time, especially at high Q)
        val settledStart = blockFrames / 2
        val settledLen = blockFrames - settledStart

        // 1) DC test: mean of settled output should be ≈ 0. The z⁻¹-delayed feedback
        // topology can leave a tiny transient DC bias before fully settling — well
        // below the user-audible threshold (~−40 dB ≈ 0.01).
        var sum = 0.0
        for (i in settledStart until blockFrames) sum += buf[i]
        val dc = sum / settledLen
        kotlin.math.abs(dc) shouldBeLessThan 0.01

        // 2) Sub-bass test: filter the output through a probe LPF at 100 Hz to isolate
        // sub-100 Hz energy. With a 1 kHz fundamental input and only symmetric saturation,
        // there should be no significant energy down there.
        val probe = LowPassHighPassFilters.OnePoleLPF(100.0, sampleRate)
        val probeBuf = AudioBuffer(blockFrames) { i -> buf[i] }
        probe.process(probeBuf, 0, probeBuf.size)
        val subBassRms = rms(AudioBuffer(settledLen) { i -> probeBuf[settledStart + i] })
        // Input signal RMS is 1/√2 ≈ 0.707 (sine at amplitude 1.0). Sub-bass should be
        // way under 5% of that.
        subBassRms shouldBeLessThan 0.05
    }

    "SvfHPF analog>0 - low frequencies are still cut (regression test for HPF complementarity bug)" {
        // Regression test: the first saturated SvfHPF implementation used `v1Sat` directly
        // in the output formula (`v0 - k*v1Sat - v2`), which broke LP+HP=input complementarity
        // and caused the HPF to pass low frequencies and create a notch at cutoff. Fix was to
        // saturate only the integrator-1 feedback and keep the output formula linear.
        val filter = LowPassHighPassFilters.SvfHPF(2500.0, q = 1.0, sampleRate = sampleRate, analog = 3.0)
        val buf = sine(freq = 200.0, length = blockFrames, amplitude = 1.0)
        val inputRms = rms(buf)

        filter.process(buf, 0, buf.size)
        val outputRms = rms(buf)

        // 200 Hz signal through a 2500 Hz HPF — should be heavily attenuated regardless of saturation.
        outputRms shouldBeLessThan (inputRms * 0.3)
    }

    "SvfHPF analog>0 - bit-identical to analog=0 (saturation currently disabled)" {
        val linear = LowPassHighPassFilters.SvfHPF(800.0, q = 5.0, sampleRate = sampleRate, analog = 0.0)
        val withAnalog = LowPassHighPassFilters.SvfHPF(800.0, q = 5.0, sampleRate = sampleRate, analog = 5.0)

        val linBuf = sine(freq = 800.0, length = blockFrames, amplitude = 1.0)
        val anaBuf = AudioBuffer(blockFrames) { i -> linBuf[i] }

        linear.process(linBuf, 0, linBuf.size)
        withAnalog.process(anaBuf, 0, anaBuf.size)

        for (i in 0 until blockFrames) {
            anaBuf[i] shouldBe linBuf[i]
        }
    }

    "SvfLPF analog>0 - low-amplitude signal stays linear (saturation disabled)" {
        // Trivially passes since analog>0 is currently a no-op. Keeps the test around
        // for when saturation gets re-introduced via a new topology.
        val linear = LowPassHighPassFilters.SvfLPF(2000.0, q = 1.0, sampleRate = sampleRate, analog = 0.0)
        val withAnalog = LowPassHighPassFilters.SvfLPF(2000.0, q = 1.0, sampleRate = sampleRate, analog = 3.0)

        val linBuf = sine(freq = 500.0, length = blockFrames, amplitude = 0.001)
        val anaBuf = AudioBuffer(blockFrames) { i -> linBuf[i] }

        linear.process(linBuf, 0, linBuf.size)
        withAnalog.process(anaBuf, 0, anaBuf.size)

        val linRms = rms(AudioBuffer(blockFrames - 1024) { i -> linBuf[1024 + i] })
        val anaRms = rms(AudioBuffer(blockFrames - 1024) { i -> anaBuf[1024 + i] })
        val ratio = anaRms / linRms
        ratio shouldBeGreaterThan 0.99
        ratio shouldBeLessThan 1.01
    }

    // -----------------------------------------------------------------------
    // Per-voice cutoff offset (Step 2)
    //
    // Filters take an optional `cutoffOffsetMul` that multiplies the cutoff at
    // both construction and runtime setCutoff. Two filters with the same patch
    // but different offsets should behave differently.
    // -----------------------------------------------------------------------

    "SvfLPF cutoffOffsetMul=1.0 - no change vs unset" {
        val unset = LowPassHighPassFilters.SvfLPF(1000.0, q = 2.0, sampleRate = sampleRate)
        val unity = LowPassHighPassFilters.SvfLPF(1000.0, q = 2.0, sampleRate = sampleRate, cutoffOffsetMul = 1.0)

        val a = sine(freq = 1000.0, length = 1024, amplitude = 0.5)
        val b = AudioBuffer(1024) { i -> a[i] }

        unset.process(a, 0, a.size)
        unity.process(b, 0, b.size)

        for (i in 0 until 1024) {
            b[i] shouldBe a[i]
        }
    }

    "SvfLPF cutoffOffsetMul shifts the cutoff" {
        // Offset = 1.05 means effective cutoff is 5% higher (≈ 84 cents).
        // For a Q=2 LPF at 1000 Hz fed a 1000 Hz sine, the offset filter should
        // pass MORE signal (the sine is now below cutoff).
        val nominal = LowPassHighPassFilters.SvfLPF(1000.0, q = 2.0, sampleRate = sampleRate, cutoffOffsetMul = 1.0)
        val shifted = LowPassHighPassFilters.SvfLPF(1000.0, q = 2.0, sampleRate = sampleRate, cutoffOffsetMul = 1.05)

        val a = sine(freq = 1000.0, length = blockFrames, amplitude = 0.5)
        val b = AudioBuffer(blockFrames) { i -> a[i] }
        val inputRms = rms(a)

        nominal.process(a, 0, a.size)
        shifted.process(b, 0, b.size)

        val nominalRms = rms(a)
        val shiftedRms = rms(b)

        // The shifted-up filter passes more of the 1 kHz signal.
        shiftedRms shouldBeGreaterThan nominalRms
        // Both should be passing real signal — sanity.
        shiftedRms shouldBeGreaterThan (inputRms * 0.1)
    }

    // -----------------------------------------------------------------------
    // Coefficient ramp on setCutoff (Step 4)
    //
    // setCutoff sets up a 32-sample linear transition from current coefs to new
    // coefs. Masks block-boundary discontinuities when FilterModRenderer updates
    // the cutoff per block during envelope sweeps. Construction snaps directly
    // (no ramp on note-on).
    // -----------------------------------------------------------------------

    "SvfLPF setCutoff - output transitions smoothly across a cutoff jump" {
        // Construct at low cutoff, settle, then jump to high cutoff via setCutoff.
        // The output must not contain a discontinuity (large sample-to-sample jump)
        // — the coefficient ramp should distribute the change over ~32 samples.
        val filter = LowPassHighPassFilters.SvfLPF(500.0, q = 1.0, sampleRate = sampleRate)
        val buf = sine(freq = 2000.0, length = blockFrames, amplitude = 0.5)

        // Settle the filter at low cutoff (first half of buffer)
        filter.process(buf, 0, 256)

        // Snapshot the last output value before the cutoff change
        val sampleBeforeJump = buf[255]

        // Now jump the cutoff to 8 kHz — big change, would normally cause a click
        filter.setCutoff(8000.0)

        // Process one more sample at a time, looking for any single-sample discontinuity
        // larger than reasonable
        var maxSingleSampleJump = 0.0
        var prevOutput = sampleBeforeJump
        for (i in 256 until 320) {  // 64 samples covering the transition
            // Re-process a single sample (process a single-sample chunk).
            // We can't truly do that because process() is block-based, so we use
            // a single-sample slice approach.
            val singleBuf = AudioBuffer(1) { _ -> buf[i] }
            filter.process(singleBuf, 0, 1)
            val cur = singleBuf[0]
            val jump = kotlin.math.abs(cur - prevOutput)
            if (jump > maxSingleSampleJump) maxSingleSampleJump = jump
            prevOutput = cur
        }

        // With smoothing, no single-sample jump should be huge.
        // The input is a 2 kHz sine at amplitude 0.5, so adjacent-sample diff is
        // bounded by 2π·2000/44100·0.5 ≈ 0.14. We allow up to 3x that as headroom
        // for the actual signal motion.
        maxSingleSampleJump shouldBeLessThan 0.5
    }

    "SvfLPF - construction snaps to target cutoff (no ramp on first sample)" {
        // The constructor uses setCutoffSnap so the first process() call should
        // produce output as if the filter has been at the target cutoff forever.
        // Specifically: filter the same input two ways and check that filtering
        // works immediately, not gradually.
        val filter = LowPassHighPassFilters.SvfLPF(500.0, q = 1.0, sampleRate = sampleRate)

        // Send a 5 kHz signal — should be heavily attenuated even from sample 0.
        val buf = sine(freq = 5000.0, length = 128, amplitude = 1.0)
        val inputRms = rms(buf)
        filter.process(buf, 0, buf.size)

        // If construction had ramped from coefs=0 to target, the first 32 samples
        // would pass through unfiltered (coefs near zero ≈ passthrough at low cutoff).
        // Snap means full attenuation immediately.
        val firstChunkRms = rms(AudioBuffer(32) { i -> buf[i] })
        firstChunkRms shouldBeLessThan (inputRms * 0.5)
    }

    "SvfLPF - static cutoff has zero transition overhead" {
        // After construction with no setCutoff calls, processing many blocks
        // should NOT exhibit any per-sample ramp behavior. This is mostly a
        // smoke test that setCutoffSnap properly zeroed the increments.
        val filter = LowPassHighPassFilters.SvfLPF(1000.0, q = 1.0, sampleRate = sampleRate)

        // Send DC — coefs should be static.
        val buf = AudioBuffer(blockFrames) { _ -> 1.0 }
        filter.process(buf, 0, buf.size)

        // After settling, output should be very close to 1.0 (DC gain of LPF = 1)
        var maxDelta = 0.0
        for (i in 2048 until blockFrames) {
            val d = kotlin.math.abs(buf[i] - 1.0)
            if (d > maxDelta) maxDelta = d
        }
        maxDelta shouldBeLessThan 1e-6
    }
})
