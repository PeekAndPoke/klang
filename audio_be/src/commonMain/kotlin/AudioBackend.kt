/*
 * Copyright (C) 2025-2026 The Klang Audio Motör Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

import io.peekandpoke.klang.audio_bridge.infra.KlangCommLink
import kotlinx.coroutines.CoroutineScope

interface AudioBackend {
    class Config(
        val commLink: KlangCommLink.BackendEndpoint,
        val sampleRate: Int,
        val blockSize: Int,
    )

    /**
     * Access to visualization data.
     * Returns null if backend doesn't support visualization.
     */
    val analyzer: AudioAnalyzer? get() = null

    suspend fun run(scope: CoroutineScope)
}
