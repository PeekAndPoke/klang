/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.filters

import io.peekandpoke.klang.audio_be.AudioBuffer

/**
 * Audio filter interface.
 */
interface AudioFilter {
    companion object {
        fun List<AudioFilter>.combine(): AudioFilter {
            if (isEmpty()) return NoOpAudioFilter
            if (size == 1) return this[0]

            return ChainAudioFilter(this)
        }
    }

    /**
     * A filter whose cutoff a control-rate modulator can move. Its one mover, the voice strip's filter modulator,
     * retired with the strip (phase 3 step 9); the strip's filter classes follow in their own commit.
     */
    interface Tunable {
        /**
         * The cutoff snaps to [startHz] and moves linearly (in the filter's coefficients) to [endHz]
         * over the next [frames] samples, then holds: one block's sweep.
         */
        fun sweepCutoff(startHz: Double, endHz: Double, frames: Int)
    }

    fun process(buffer: AudioBuffer, offset: Int, length: Int)
}
