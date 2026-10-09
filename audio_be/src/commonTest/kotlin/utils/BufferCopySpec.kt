/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.utils

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/**
 * [copyRangeInto], the audio path's `copyInto` without the JS `subarray` view: `this[startIndex until endIndex]`
 * lands at `destination[destinationOffset]` on, nothing else is written, values are copied bit for bit, and it
 * copies forwards.
 */
class BufferCopySpec : StringSpec({

    fun ramp(n: Int): DoubleArray = DoubleArray(n) { it.toDouble() }

    "a range lands at the destination offset, nothing else is written" {
        val target = DoubleArray(8) { -1.0 }

        ramp(8).copyRangeInto(destination = target, destinationOffset = 3, startIndex = 2, endIndex = 5)

        target.toList() shouldBe listOf(-1.0, -1.0, -1.0, 2.0, 3.0, 4.0, -1.0, -1.0)
    }

    "the same offset on both sides copies that window only" {
        val target = DoubleArray(6) { -1.0 }

        ramp(6).copyRangeInto(destination = target, destinationOffset = 1, startIndex = 1, endIndex = 4)

        target.toList() shouldBe listOf(-1.0, 1.0, 2.0, 3.0, -1.0, -1.0)
    }

    "an empty or reversed range copies nothing" {
        val target = DoubleArray(4) { 7.0 }

        ramp(4).copyRangeInto(destination = target, destinationOffset = 0, startIndex = 2, endIndex = 2)
        ramp(4).copyRangeInto(destination = target, destinationOffset = 0, startIndex = 3, endIndex = 1)

        target.toList() shouldBe listOf(7.0, 7.0, 7.0, 7.0)
    }

    "values are copied bit for bit, NaN and -0.0 included" {
        val source = doubleArrayOf(Double.NaN, -0.0, Double.NEGATIVE_INFINITY, 1e-320)
        val target = DoubleArray(4)

        source.copyRangeInto(destination = target, destinationOffset = 0, startIndex = 0, endIndex = 4)

        target.map { it.toRawBits() } shouldBe source.map { it.toRawBits() }
    }

    "inside one array it copies forwards: a move to an earlier offset is a move, to a later one it smears" {
        val toEarlier = ramp(5)
        val toLater = ramp(5)

        toEarlier.copyRangeInto(destination = toEarlier, destinationOffset = 0, startIndex = 1, endIndex = 4)
        toLater.copyRangeInto(destination = toLater, destinationOffset = 1, startIndex = 0, endIndex = 3)

        toEarlier.toList() shouldBe listOf(1.0, 2.0, 3.0, 3.0, 4.0)
        // `copyInto` would give 0, 0, 1, 2, 4 here; no caller copies to a later offset inside one array.
        toLater.toList() shouldBe listOf(0.0, 0.0, 0.0, 0.0, 4.0)
    }
})
