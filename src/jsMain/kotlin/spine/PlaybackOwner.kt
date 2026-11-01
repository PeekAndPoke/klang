/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

/**
 * What plays on the [Player] and can be stopped: the owner of [Playbacks.nowPlaying], and what
 * [Playbacks.requestExclusivePlayback] stops when another takes the stage (KLANG-00389, "Player in the Spine", step 2).
 */
interface PlaybackOwner {
    /** Stops what this owner plays and gives up the stage. */
    fun stop()
}
