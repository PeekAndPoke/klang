/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.utils

/**
 * Multiplies `buffer[startIndex until endIndex]` by a linear fade that reaches EXACT zero at the buffer index
 * [zeroIndex] and stays there: the gain at index `i` is `(zeroIndex - i) * scale`, clamped to `[0, 1]`.
 *
 * The caller chooses [scale] (one over the fade's length in frames) and [zeroIndex] (where the last frame that
 * renders sits relative to this buffer; it may lie past this block's window, or before it). Before the fade the
 * gain clamps to 1, so [startIndex] may be anywhere at or before the fade; past [zeroIndex] it clamps to 0. When
 * [zeroIndex] is a whole number, the gain on that index is `0.0 * scale`, exactly zero; the clamp at 1 absorbs a
 * 1-ulp overshoot at the entry frame.
 *
 * No range check: an index past the buffer throws on the JVM, and on JS reads `undefined` (NaN) and drops a write
 * past the end. Non-finite values are not guarded: a NaN [scale] or [zeroIndex] makes a NaN gain (NaN fails both
 * clamps), and an infinite sample at gain 0 becomes NaN (`Inf * 0.0`).
 *
 * No smoother, on purpose: a one-pole would lag and leave a non-zero last sample. The one law of the voice's
 * teardown fade (`TeardownFadeRenderer`) and the cut (`Voice.applyCutFade`).
 */
@Suppress("NOTHING_TO_INLINE")
inline fun fadeToZero(buffer: DoubleArray, startIndex: Int, endIndex: Int, zeroIndex: Double, scale: Double) {
    for (idx in startIndex until endIndex) {
        val remaining = (zeroIndex - idx) * scale
        val gain = if (remaining < 0.0) 0.0 else if (remaining > 1.0) 1.0 else remaining

        buffer[idx] = buffer[idx] * gain
    }
}
