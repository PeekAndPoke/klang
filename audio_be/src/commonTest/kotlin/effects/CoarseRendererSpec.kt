package io.peekandpoke.klang.audio_be.effects

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.voices.strip.filter.CoarseRenderer
import io.peekandpoke.klang.audio_be.voices.strip.filter.renderInPlace

class CoarseRendererSpec : StringSpec({

    "CoarseRenderer with amount<=1 passes signal through unchanged" {
        val renderer = CoarseRenderer(amount = 1.0)
        val buffer = doubleArrayOf(0.5, -0.3, 0.8, -0.9)
        val original = buffer.copyOf()
        renderer.renderInPlace(buffer)
        for (i in buffer.indices) {
            buffer[i] shouldBe original[i]
        }
    }

    "CoarseRenderer direct path holds samples (classic behavior preserved)" {
        val renderer = CoarseRenderer(amount = 4.0)
        val buffer = AudioBuffer(32) { it * 0.1 }
        renderer.renderInPlace(buffer)
        // Verify some samples are held (consecutive equal values exist)
        var heldPairs = 0
        for (i in 1 until buffer.size) {
            if (buffer[i] == buffer[i - 1]) heldPairs++
        }
        (heldPairs > 0) shouldBe true
    }

    "CoarseRenderer oversampled bypass (amount<=1) unchanged" {
        val renderer = CoarseRenderer(amount = 1.0, oversampleStages = 1)
        val buffer = doubleArrayOf(0.5, -0.3, 0.8, -0.9)
        val original = buffer.copyOf()
        renderer.renderInPlace(buffer)
        for (i in buffer.indices) {
            buffer[i] shouldBe original[i]
        }
    }

    "CoarseRenderer oversampled path produces roughly the same DC level" {
        val blockFrames = 256
        val buffer = AudioBuffer(blockFrames) { 0.6 }
        val renderer = CoarseRenderer(amount = 4.0, oversampleStages = 1)
        renderer.renderInPlace(buffer)

        // DC in → sample-hold → DC at the same level (after filter warmup).
        val steady = buffer.takeLast(64).map { it.toDouble() }.average()
        kotlin.math.abs(steady - 0.6) shouldBeLessThan 0.05
    }
})
