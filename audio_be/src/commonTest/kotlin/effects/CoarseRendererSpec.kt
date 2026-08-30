/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

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

    "W1: the direct path's first hold is `amount` samples, matching the oversampled bootstrap" {
        val renderer = CoarseRenderer(amount = 4.0)
        // Sample 0 NON-zero: a zero start is indistinguishable from the uninitialized
        // lastValue and let a bootstrap-dropped mutant pass (review round 1).
        val buffer = AudioBuffer(32) { (it + 1) * 0.1 }
        val input = buffer.copyOf()
        renderer.renderInPlace(buffer)

        // Grid 0,4,8,...: the old 0.0 bootstrap held input[0] for 8 samples (2 x amount).
        for (i in 0 until 32) {
            buffer[i] shouldBe input[(i / 4) * 4]
        }
    }

    "W1: a window boundary on the hold grid does not re-anchor it" {
        // The old `i == 0 && counter == 0.0` latch re-armed exactly at note-relative sample
        // `amount` for power-of-two amounts; a window starting there displaced the grid for
        // the rest of the note (live in ATruthWorthLyingFor's coarse(2)).
        val contiguous = CoarseRenderer(amount = 4.0)
        val whole = AudioBuffer(32) { (it + 1) * 0.1 }
        contiguous.renderInPlace(whole)

        val split = CoarseRenderer(amount = 4.0)
        val head = AudioBuffer(4) { (it + 1) * 0.1 }
        val tail = AudioBuffer(28) { (it + 5) * 0.1 }
        split.renderInPlace(head)
        split.renderInPlace(tail)

        for (i in 0 until 4) {
            head[i] shouldBe whole[i]
        }
        for (i in 0 until 28) {
            tail[i] shouldBe whole[i + 4]
        }
    }

    "CoarseRenderer oversampled path produces roughly the same DC level" {
        val blockFrames = 256
        val buffer = AudioBuffer(blockFrames) { 0.6 }
        val renderer = CoarseRenderer(amount = 4.0, oversampleStages = 1)
        renderer.renderInPlace(buffer)

        // DC in → sample-hold → DC at the same level (after filter warmup).
        val steady = buffer.takeLast(64).map { it }.average()
        kotlin.math.abs(steady - 0.6) shouldBeLessThan 0.05
    }
})
