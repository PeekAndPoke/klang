/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.apps.common.liveact

import io.peekandpoke.klang.audio_engine.KlangCyclicPlayback
import io.peekandpoke.klang.viz.liveact.sound.Transport
import io.peekandpoke.klang.viz.liveact.sound.TransportControls

/**
 * A [playback] someone else plays (the studio's), as a show's [Transport]: the show follows its time and moves
 * nothing. There is no walk-in then: the stage's time is the music's from the moment the show is there.
 */
class FollowedPlayback(private val playback: KlangCyclicPlayback) : Transport {
    /** The seconds of the song heard so far: its place, as long as the tempo stays. */
    override val time: Double get() = playback.heardSec() ?: 0.0

    override val paused: Boolean get() = playback.heardSec() == null

    override val controls: TransportControls? = null

    override fun release() {
        // the music goes on: it is not the show's
    }
}
