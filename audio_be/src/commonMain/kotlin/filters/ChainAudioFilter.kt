/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.filters

import io.peekandpoke.klang.audio_be.AudioBuffer

/**
 * Performance optimized sequential combination of multiple [filters]
 */
class ChainAudioFilter(
    // `internal` (not private) so tests can assert the baked chain order — see VoiceFactory.
    internal val filters: List<AudioFilter>,
) : AudioFilter {
    override fun process(buffer: AudioBuffer, offset: Int, length: Int) {
        // Apply each filter in sequence to the whole buffer
        for (f in filters) {
            f.process(buffer, offset, length)
        }
    }
}
