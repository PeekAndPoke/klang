/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.utils

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * [fadeToZero], the one law of the voice's teardown fade and its cut: gain `(zeroIndex - i) * scale` clamped to
 * `[0, 1]`, exactly zero on a whole-number [zeroIndex], applied to `[startIndex, endIndex)` only.
 */
class FadeToZeroSpec : StringSpec({

    "the gain is 1 before the fade, linear inside it, exactly 0 on the zero index and after it" {
        val buffer = DoubleArray(10) { 1.0 }

        fadeToZero(buffer = buffer, startIndex = 0, endIndex = 10, zeroIndex = 8.0, scale = 0.25)

        buffer.toList() shouldBe listOf(1.0, 1.0, 1.0, 1.0, 1.0, 0.75, 0.5, 0.25, 0.0, 0.0)
        buffer[8].toRawBits() shouldBe 0.0.toRawBits()
    }

    "only the window startIndex until endIndex is touched" {
        // Indices 2 and 3 would fade (0.5, then 0) and 6 and 7 would be silent, were they inside the window.
        val buffer = DoubleArray(8) { 2.0 }

        fadeToZero(buffer = buffer, startIndex = 4, endIndex = 6, zeroIndex = 3.0, scale = 0.5)

        buffer.toList() shouldBe listOf(2.0, 2.0, 2.0, 2.0, 0.0, 0.0, 2.0, 2.0)
    }

    "a zero index past the window fades part way, one before it silences the whole window" {
        val early = DoubleArray(4) { 1.0 }
        val late = DoubleArray(4) { 1.0 }

        fadeToZero(buffer = early, startIndex = 0, endIndex = 4, zeroIndex = 100.0, scale = 0.01)
        fadeToZero(buffer = late, startIndex = 0, endIndex = 4, zeroIndex = -1.0, scale = 0.25)

        early.toList() shouldBe listOf(1.0, 0.99, 0.98, 0.97)
        late.toList() shouldBe listOf(0.0, 0.0, 0.0, 0.0)
    }

    "the gain is (zeroIndex - i) * scale in that order, bit for bit" {
        // Reassociated as zeroIndex * scale - i * scale, index 3 would be 0.7 exactly.
        val buffer = DoubleArray(10) { 1.0 }

        fadeToZero(buffer = buffer, startIndex = 0, endIndex = 10, zeroIndex = 10.0, scale = 0.1)

        buffer[3].toRawBits() shouldBe 0.7000000000000001.toRawBits()
    }
})
