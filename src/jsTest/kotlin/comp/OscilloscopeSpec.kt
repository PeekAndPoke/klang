/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import org.khronos.webgl.Float32Array

/** Samples as the analyser gives them; the values are exact in 32 bits, so they compare as written. */
private fun samples(vararg values: Float) = Float32Array(values.toTypedArray())

/**
 * The scope's trace where samples outnumber pixels: a pixel's lowest and highest go in the order the signal reached
 * them, so the trace follows the wave instead of rising from every pixel's lowest to the next one's highest.
 */
class OscilloscopeSpec : StringSpec({

    "a pixel whose signal falls gives its highest first" {
        extremesInOrder(buffer = samples(0.5f, 0.75f, 0.125f, -0.625f), start = 0, end = 4) shouldBe (0.75f to -0.625f)
    }

    "a pixel whose signal rises gives its lowest first" {
        extremesInOrder(buffer = samples(0.0f, -0.5f, 0.25f, 0.875f), start = 0, end = 4) shouldBe (-0.5f to 0.875f)
    }

    "only the pixel's own samples count" {
        extremesInOrder(buffer = samples(9f, 0.125f, 0.375f, -9f), start = 1, end = 3) shouldBe (0.125f to 0.375f)
    }
})
