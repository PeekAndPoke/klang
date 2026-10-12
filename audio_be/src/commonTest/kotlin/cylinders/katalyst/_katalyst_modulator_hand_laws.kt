/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.cylinders.katalyst

import kotlin.math.PI
import kotlin.math.sin

// The hand laws of the Katalyst modulators, written from the shapes' definitions at the EIGHTH clock (46.875 Hz at
// 48 kHz: `dt = 2^-10` per sample, an eighth of a cycle per 128-frame block, every phase exact in binary). Shared by
// `KatalystKnobModulatorSpec` (the evaluator, step 5a) and the chain rows of step 5b, so neither is its own oracle.

/** An eighth of a cycle per 128-frame block at 48 kHz. */
internal const val EIGHTH_HZ: Double = 46.875

/** One sample's phase at [EIGHTH_HZ]: the saw's flyback, half the pulse's minimum flank. */
internal const val EIGHTH_DT: Double = 1.0 / 1024.0

internal fun handSine(p: Double): Double = sin(2.0 * PI * p)

/** Rise over `[0, 1 - dt]`, then a one-sample flyback. */
internal fun handSaw(p: Double): Double {
    val riseEnd = 1.0 - EIGHTH_DT

    return if (p < riseEnd) -1.0 + p * 2.0 / riseEnd else 1.0 - (p - riseEnd) * 2.0 / EIGHTH_DT
}

/** Duty 0.5, both flanks fully open: up over half a cycle, down over the other. */
internal fun handTri(p: Double): Double = if (p < 0.5) -1.0 + 4.0 * p else 1.0 - 4.0 * (p - 0.5)

/** A two-sample rise, high to [duty], a two-sample fall, low to the end. */
internal fun handSquare(p: Double, duty: Double): Double {
    val flank = 2.0 * EIGHTH_DT

    return when {
        p < flank -> -1.0 + p * 2.0 / flank
        p < duty -> 1.0
        p < duty + flank -> 1.0 - (p - duty) * 2.0 / flank
        else -> -1.0
    }
}
