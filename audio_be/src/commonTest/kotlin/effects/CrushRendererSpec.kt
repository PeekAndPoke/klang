package io.peekandpoke.klang.audio_be.effects

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.voices.strip.filter.CrushRenderer
import io.peekandpoke.klang.audio_be.voices.strip.filter.renderInPlace
import kotlin.math.PI
import kotlin.math.abs
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

    "CrushRenderer quantizes to discrete levels" {
        // amount=2 → 2^2 = 4 levels; halfLevels = 2
        // floor(x * 2) / 2 produces steps of 0.5
        val renderer = CrushRenderer(amount = 2.0)
        val buffer = floatArrayOf(0.1f, 0.4f, 0.6f, 0.9f, -0.2f, -0.7f)
        renderer.renderInPlace(buffer)
        // Each output must be a multiple of 0.5
        for (s in buffer) {
            val q = s.toDouble() * 2.0
            abs(q - kotlin.math.floor(q)) shouldBeLessThan 1e-6
        }
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
        // halfLevels = 2 at amount=2 → grid step 0.5
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
            abs(q - kotlin.math.round(q)) shouldBeLessThan 1e-6
        }
    }

    "CrushRenderer oversampled path produces off-grid (smoothed) samples" {
        // Same setup — but oversampled output should NOT be snapped to the grid,
        // because the decimation FIR smooths the quantization steps.
        val sampleRate = 48000.0
        val freq = 3000.0
        val blockFrames = 512

        val bufOs = FloatArray(blockFrames) { i ->
            (sin(2.0 * PI * freq * i / sampleRate) * 0.8).toFloat()
        }
        val os = CrushRenderer(amount = 2.0, oversampleStages = 2) // 4x
        os.renderInPlace(bufOs)

        // Count samples that land OFF the 0.5-grid by more than a tiny epsilon.
        // Skip warmup samples (FIR group delay).
        val warmup = 20
        var offGrid = 0
        for (i in warmup until blockFrames) {
            val q = bufOs[i].toDouble() * 2.0
            if (abs(q - kotlin.math.round(q)) > 1e-3) offGrid++
        }

        // The vast majority of oversampled samples should be off-grid — proving
        // the decimation filter actually smoothed the quantization steps.
        (offGrid > (blockFrames - warmup) / 2) shouldBe true
    }

    "CrushRenderer oversampled path preserves rough signal level" {
        val blockFrames = 256
        val buffer = FloatArray(blockFrames) { 0.6f }
        val os = CrushRenderer(amount = 4.0, oversampleStages = 1)
        os.renderInPlace(buffer)

        // 4 bits → 16 levels → halfLevels=8 → step=0.125
        // floor(0.6 * 8)/8 = floor(4.8)/8 = 4/8 = 0.5
        // After filter warmup the steady-state value should be near 0.5.
        val steady = buffer.takeLast(64).map { it.toDouble() }.average()
        abs(steady - 0.5) shouldBeLessThan 0.05
    }
})
