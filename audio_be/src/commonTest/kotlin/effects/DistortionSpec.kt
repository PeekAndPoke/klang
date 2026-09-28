/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.effects

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeLessThan
import io.peekandpoke.klang.audio_be.AudioBuffer
import io.peekandpoke.klang.audio_be.ignitor.ConstantIgnitor
import io.peekandpoke.klang.audio_be.ignitor.fusedDistort
import io.peekandpoke.klang.audio_be.ignitor.renderThroughNode
import io.peekandpoke.klang.audio_be.parseDistortionShape
import kotlin.math.abs

/**
 * The DC blocker on the fused `Distort` node (the one `classic()` builds, `DistortionCore`), on the two asymmetric
 * shapes that are the audible reason for the unconditional DC block. Everything else lives at its core: the node's
 * law against an oracle in `StripLawCoresSpec`, the shapers' bounds, symmetry and landmark values in
 * `ShapingFuncsBoundsSpec`, the name lookup (an unknown name is soft) in `ShapeCatalogueSpec`, and the amount-0
 * bypass is the build gate's row in `IgnitorGateSpec` (the node is not built).
 */
class DistortionSpec : StringSpec({

    /** [input] through the fused distort node at [amount] and [shape], no oversampling, one window. */
    fun distorted(input: DoubleArray, amount: Double, shape: String = "soft"): DoubleArray =
        renderThroughNode(input) { it.fusedDistort(ConstantIgnitor(amount), parseDistortionShape(shape), 0) }

    "the distort node: diode shape DC blocker removes offset over time" {
        val buffer = distorted(AudioBuffer(4096) { 0.5 }, 0.8, "diode")
        val lastSample = buffer[buffer.size - 1]
        abs(lastSample) shouldBeLessThan 0.1
    }

    "the distort node: rectify shape DC blocker removes offset over time" {
        val buffer = distorted(AudioBuffer(4096) { i -> if (i % 2 == 0) 0.3 else -0.3 }, 0.5, "rectify")
        val avg = buffer.takeLast(100).map { it }.average()
        abs(avg) shouldBeLessThan 0.15
    }
})
