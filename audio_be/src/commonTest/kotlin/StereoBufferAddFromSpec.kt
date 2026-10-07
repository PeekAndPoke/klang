/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

/** [StereoBuffer.addFrom]: the first `frames` frames of a source bus added into this one, left into left, right into
 * right. */
class StereoBufferAddFromSpec : StringSpec({

    fun bus(left: List<Double>, right: List<Double>): StereoBuffer = StereoBuffer(left.size).also { b ->
        left.forEachIndexed { i, v -> b.left[i] = v }
        right.forEachIndexed { i, v -> b.right[i] = v }
    }

    "each channel is added into its own channel, the source is left alone" {
        val target = bus(left = listOf(1.0, 2.0, 3.0), right = listOf(10.0, 20.0, 30.0))
        val source = bus(left = listOf(0.5, 0.25, -1.0), right = listOf(-5.0, 100.0, 0.0))

        target.addFrom(source = source, frames = 3)

        target.left.toList() shouldBe listOf(1.5, 2.25, 2.0)
        target.right.toList() shouldBe listOf(5.0, 120.0, 30.0)
        source.left.toList() shouldBe listOf(0.5, 0.25, -1.0)
        source.right.toList() shouldBe listOf(-5.0, 100.0, 0.0)
    }

    "only the first frames are added, the rest of the target is untouched" {
        val target = bus(left = listOf(1.0, 1.0, 1.0, 1.0), right = listOf(2.0, 2.0, 2.0, 2.0))
        val source = bus(left = listOf(1.0, 1.0, 1.0, 1.0), right = listOf(1.0, 1.0, 1.0, 1.0))

        target.addFrom(source = source, frames = 2)

        target.left.toList() shouldBe listOf(2.0, 2.0, 1.0, 1.0)
        target.right.toList() shouldBe listOf(3.0, 3.0, 2.0, 2.0)
    }
})
