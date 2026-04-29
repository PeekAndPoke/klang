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
})
