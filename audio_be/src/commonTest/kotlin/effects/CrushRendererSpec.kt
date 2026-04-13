package io.peekandpoke.klang.audio_be.effects

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.voices.strip.filter.CrushRenderer
import io.peekandpoke.klang.audio_be.voices.strip.filter.renderInPlace
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.round
import kotlin.math.sin

class CrushRendererSpec : StringSpec({

    "CrushRenderer with amount=0 passes signal through unchanged" {
        val renderer = CrushRenderer(amount = 0.0)
        val buffer = floatArrayOf(0.5f, -0.3f, 0.8f, -0.9f)
        val original = buffer.copyOf()
        renderer.renderInPlace(buffer)
        for (i in buffer.indices) {
            buffer[i] shouldBe original[i]
        }
    }

    "CrushRenderer bypasses for 0 < amount < 1 (sub-2-level grid)" {
        // Regression: previously `amount = 0.5` would activate with halfLevels ≈ 0.707
        // and produce outputs of ≈ ±1.414 — a 3 dB gain bump. Now it must bypass.
        for (amount in listOf(0.01, 0.25, 0.5, 0.7, 0.99)) {
            val renderer = CrushRenderer(amount = amount)
            val buffer = floatArrayOf(0.1f, -0.3f, 0.6f, -0.9f, 0.99f, -0.99f)
            val original = buffer.copyOf()
            renderer.renderInPlace(buffer)
            for (i in buffer.indices) {
                buffer[i] shouldBe original[i]
            }
        }
    }

    "CrushRenderer never inflates magnitude for any amount in [1, 16]" {
        // Stronger property test: for any musically valid amount, no input sample
        // in [-1, 1] should produce an output with magnitude > 1.
        val inputs = floatArrayOf(0.0f, 0.1f, 0.25f, 0.5f, 0.75f, 0.9f, 0.99f, 1.0f)
        val amounts = listOf(1.0, 1.5, 2.0, 2.5, 3.0, 4.0, 5.5, 8.0, 16.0)
        for (amount in amounts) {
            for (sign in intArrayOf(1, -1)) {
                val buffer = FloatArray(inputs.size) { i -> sign * inputs[i] }
                CrushRenderer(amount = amount).renderInPlace(buffer)
                for (s in buffer) {
                    abs(s.toDouble()) shouldBeLessThan 1.0001
                }
            }
        }
    }

    "CrushRenderer quantizes to discrete levels" {
        // amount=2 → 2^2 = 4 levels; halfLevels = 2. Grid step = 0.5.
        val renderer = CrushRenderer(amount = 2.0)
        val buffer = floatArrayOf(0.1f, 0.4f, 0.6f, 0.9f, -0.2f, -0.7f)
        renderer.renderInPlace(buffer)
        // Every output must be a multiple of 0.5.
        for (s in buffer) {
            val q = s.toDouble() * 2.0
            abs(q - round(q)) shouldBeLessThan 1e-6
        }
    }

    "CrushRenderer midtread rounding: zero input → zero output" {
        // With symmetric round the quantizer must have a grid point at zero.
        val renderer = CrushRenderer(amount = 2.0)
        val buffer = FloatArray(16) { 0.0f }
        renderer.renderInPlace(buffer)
        for (s in buffer) s shouldBe 0.0f
    }

    "CrushRenderer no DC bias on a symmetric sine" {
        // A symmetric input must produce a symmetric output (no DC offset).
        // The floor-based quantizer produced ~-0.5 DC at amount=1; round must produce ~0.
        // Use an integer number of sine cycles so window-truncation doesn't pollute the mean.
        val blockFrames = 4800
        val sampleRate = 48000.0
        val cyclesInBlock = 10
        val freq = sampleRate * cyclesInBlock / blockFrames  // = 100 Hz
        val buffer = FloatArray(blockFrames) { i ->
            (sin(2.0 * PI * freq * i / sampleRate) * 0.8).toFloat()
        }
        // amount=1 is the extreme setting where the old implementation biased heavily.
        val renderer = CrushRenderer(amount = 1.0)
        renderer.renderInPlace(buffer)

        val mean = buffer.map { it.toDouble() }.average()
        abs(mean) shouldBeLessThan 1e-3
    }

    "CrushRenderer envelope ramp produces no step at voice start" {
        // Reproduce the per-note click scenario: envelope ramps 0 → 1, crusher is after.
        // With symmetric round, the first nonzero envelope sample must stay near zero,
        // not jump to a DC offset.
        val blockFrames = 1024
        val attackFrames = 480 // ~10ms at 48kHz
        val sampleRate = 48000.0
        val freq = 440.0
        val buffer = FloatArray(blockFrames) { i ->
            val env = if (i < attackFrames) i.toDouble() / attackFrames else 1.0
            (sin(2.0 * PI * freq * i / sampleRate) * env).toFloat()
        }
        val renderer = CrushRenderer(amount = 1.0)
        renderer.renderInPlace(buffer)

        // The first ~20 samples (env small) should be near zero, not pegged at -0.5.
        val earlyMax = (0 until 20).maxOf { abs(buffer[it].toDouble()) }
        earlyMax shouldBeLessThan 0.2
    }

    "CrushRenderer oversampled path preserves bypass for amount=0" {
        val renderer = CrushRenderer(amount = 0.0, oversampleStages = 1)
        val buffer = floatArrayOf(0.5f, -0.3f, 0.8f, -0.9f)
        val original = buffer.copyOf()
        renderer.renderInPlace(buffer)
        for (i in buffer.indices) {
            buffer[i] shouldBe original[i]
        }
    }

    "CrushRenderer direct path snaps output to quantization grid" {
        val sampleRate = 48000.0
        val freq = 3000.0
        val blockFrames = 512

        val bufDirect = FloatArray(blockFrames) { i ->
            (sin(2.0 * PI * freq * i / sampleRate) * 0.8).toFloat()
        }
        val direct = CrushRenderer(amount = 2.0, oversampleStages = 0)
        direct.renderInPlace(bufDirect)

        // Every direct-path sample must land exactly on a grid point (multiple of 0.5).
        for (s in bufDirect) {
            val q = s.toDouble() * 2.0
            abs(q - round(q)) shouldBeLessThan 1e-6
        }
    }

    "CrushRenderer oversampled path produces off-grid (smoothed) samples" {
        val sampleRate = 48000.0
        val freq = 3000.0
        val blockFrames = 512

        val bufOs = FloatArray(blockFrames) { i ->
            (sin(2.0 * PI * freq * i / sampleRate) * 0.8).toFloat()
        }
        val os = CrushRenderer(amount = 2.0, oversampleStages = 2) // 4x
        os.renderInPlace(bufOs)

        // The vast majority of samples after warmup should be off the 0.5-grid.
        val warmup = 20
        var offGrid = 0
        for (i in warmup until blockFrames) {
            val q = bufOs[i].toDouble() * 2.0
            if (abs(q - round(q)) > 1e-3) offGrid++
        }

        (offGrid > (blockFrames - warmup) / 2) shouldBe true
    }

    "CrushRenderer oversampled path preserves rough signal level" {
        val blockFrames = 256
        val buffer = FloatArray(blockFrames) { 0.6f }
        val os = CrushRenderer(amount = 4.0, oversampleStages = 1)
        os.renderInPlace(buffer)

        // amount=4 → levels=16, halfLevels=8, grid step=0.125
        // round(0.6 * 8)/8 = round(4.8)/8 = 5/8 = 0.625
        // After filter warmup the steady-state value should be near 0.625.
        val steady = buffer.takeLast(64).map { it.toDouble() }.average()
        abs(steady - 0.625) shouldBeLessThan 0.05
    }

    "CrushRenderer continuous `levels`: fractional amount differs from integer amount" {
        // Drop of toInt() means amount=2.5 produces a different grid than amount=2.0.
        // The old implementation floored both to levels=4, giving identical output.
        val a = FloatArray(32) { 0.35f }
        val b = FloatArray(32) { 0.35f }

        CrushRenderer(amount = 2.0).renderInPlace(a)
        CrushRenderer(amount = 2.5).renderInPlace(b)

        // At amount=2.0 (halfLevels=2): round(0.35*2)/2 = round(0.7)/2 = 1/2 = 0.5.
        // At amount=2.5 (halfLevels=2.828): round(0.35*2.828)/2.828 = round(0.99)/2.828 ≈ 0.354.
        // Outputs must differ — proving the amount axis is continuous, not step-quantized.
        (abs(a[0] - b[0]) > 0.05) shouldBe true
    }
})
