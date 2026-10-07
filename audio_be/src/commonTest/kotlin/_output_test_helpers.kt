/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import kotlin.math.abs

/**
 * A copy of a rendered output block, interleaved `[L0, R0, L1, R1, ...]`, so a spec can keep it past
 * the next render and compare blocks with `contentEquals`.
 */
fun StereoBuffer.interleavedCopy(): DoubleArray =
    DoubleArray(left.size * 2) { if (it % 2 == 0) left[it / 2] else right[it / 2] }

/** The loudest sample of a rendered output block, either channel. */
fun StereoBuffer.peak(): Double {
    var peak = 0.0

    for (i in left.indices) {
        peak = maxOf(peak, abs(left[i]), abs(right[i]))
    }

    return peak
}

/** True when every sample of the block, both channels, is exactly zero. */
fun StereoBuffer.isExactlySilent(): Boolean = left.all { it == 0.0 } && right.all { it == 0.0 }
