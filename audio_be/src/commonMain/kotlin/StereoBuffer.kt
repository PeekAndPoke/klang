/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

/**
 * A stereo buffer.
 */
class StereoBuffer(blockFrames: Int) {
    val left = AudioBuffer(blockFrames)
    val right = AudioBuffer(blockFrames)

    init {
        clear()
    }

    fun clear() {
        left.fill(0.0)
        right.fill(0.0)
    }

    fun fill(value: AudioSample) {
        left.fill(value)
        right.fill(value)
    }
}
