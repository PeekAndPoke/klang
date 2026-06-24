/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_fe.samples

import io.peekandpoke.klang.audio_bridge.MonoSamplePcm

interface AudioDecoder {
    suspend fun decodeMonoFloatPcm(audioBytes: ByteArray): MonoSamplePcm?
}

/**
 * Null impl
 */
@Suppress("unused")
class NullAudioDecoder : AudioDecoder {
    override suspend fun decodeMonoFloatPcm(audioBytes: ByteArray): MonoSamplePcm? {
        return null
    }
}
