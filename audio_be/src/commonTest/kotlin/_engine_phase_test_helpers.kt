/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be

/**
 * True once the stopped engine's release has begun: `Releasing`, or `Released` after it. The question the release
 * specs ask block by block, written once over [PlaybackEngine.phase].
 */
val PlaybackEngine.releaseStarted: Boolean
    get() = when (phase) {
        PlaybackEngine.Phase.Playing, PlaybackEngine.Phase.Stopped, PlaybackEngine.Phase.Disposed -> false
        PlaybackEngine.Phase.Releasing, PlaybackEngine.Phase.Released -> true
    }
