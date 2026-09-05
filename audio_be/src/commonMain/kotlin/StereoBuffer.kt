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
}
