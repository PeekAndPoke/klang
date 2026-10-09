/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.sprudel.ui

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.sign
import kotlin.math.sin
import kotlin.math.sqrt

// The waveshaper transfer curve the distortion editor tools draw (`SprudelDistortEditorTool`,
// `SprudelDistortShapeEditorTool`). A display copy of the engine's shapes; it shapes no sound.

/** The output of the distortion [shape] for input [x], for the transfer-curve preview. Unknown shapes draw as `soft`. */
internal fun waveshape(x: Double, shape: String): Double = when (shape) {
    "soft" -> previewTanh(x)
    "hard" -> x.coerceIn(-1.0, 1.0)
    "gentle" -> x / (1.0 + abs(x))
    "cubic" -> {
        val c = x.coerceIn(-1.0, 1.0)
        c - c * c * c / 3.0
    }

    "diode" -> if (x >= 0.0) previewTanh(x) else previewTanh(x * 0.5)
    "fold" -> sin(x * PI / 2.0)
    "chebyshev" -> {
        val c = x.coerceIn(-1.0, 1.0)
        4.0 * c * c * c - 3.0 * c
    }

    "rectify" -> abs(previewTanh(x))
    "exp" -> sign(x) * (1.0 - exp(-abs(x)))

    "softsat" -> x / sqrt(1.0 + x * x)
    "tube" -> (previewTanh(x + 0.5) - 0.46211715726000974) * 0.6839397205857212
    "linearfold" -> {
        val shifted = x + 1.0
        val phase = shifted - 4.0 * floor(shifted * 0.25)
        1.0 - abs(phase - 2.0)
    }

    "zerosquare" -> previewTanh(x * 8.0)
    "sineshaper" -> sin(x * PI * 0.5)
    "asym" -> if (x >= 0.0) {
        val xc = if (x > 1.0) 1.0 else x
        1.5 * xc - 0.5 * xc * xc * xc
    } else {
        val xc = if (x < -1.0) 1.0 else -x
        -sqrt(xc)
    }

    "stompbox" -> if (x >= 0.0) 1.0 - exp(-x * 1.5) else -(1.0 - exp(x * 3.0))
    else -> previewTanh(x)
}

/** tanh through `exp(2x)`, as the preview has always drawn it (not `kotlin.math.tanh`). */
private fun previewTanh(x: Double): Double {
    val e2x = exp(2.0 * x)

    return (e2x - 1.0) / (e2x + 1.0)
}
