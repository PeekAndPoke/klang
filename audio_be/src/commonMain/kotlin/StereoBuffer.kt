/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

/**
 * A stereo buffer.
 */
class StereoBuffer(blockFrames: Int) {
    val left = AudioBuffer(blockFrames)
    val right = AudioBuffer(blockFrames)

    // No init { clear() }: a DoubleArray is zero on both runtimes (JVM `new double[n]`,
    // JS Float64Array), so the fill was a second full pass over every buffer ever constructed.
    // For a rented delay ring that pass ran on the audio thread at the moment a note needed it
    // (review round 1 on the resource warehouse).

    fun clear() {
        left.fill(0.0)
        right.fill(0.0)
    }

    fun fill(value: AudioSample) {
        left.fill(value)
        right.fill(value)
    }

    /**
     * Adds the first [frames] frames of [source] into this buffer, per channel: `left[i] += source.left[i]`, the
     * same for the right. A bus summing another bus into itself (the orbits into the fusion mix, an engine's own
     * bus into the output, a draining chain's ring-out into the orbit's mix). Allocates nothing.
     *
     * No range check: a [frames] past either buffer throws on the JVM, and on JS reads `undefined` (the sum is
     * NaN) and drops a write past the end. Values are added as they are: a non-finite sample in [source] makes the
     * target's sample non-finite.
     */
    @Suppress("NOTHING_TO_INLINE")
    inline fun addFrom(source: StereoBuffer, frames: Int) {
        val targetLeft = left
        val targetRight = right
        val sourceLeft = source.left
        val sourceRight = source.right

        for (i in 0 until frames) {
            targetLeft[i] = targetLeft[i] + sourceLeft[i]
            targetRight[i] = targetRight[i] + sourceRight[i]
        }
    }
}
