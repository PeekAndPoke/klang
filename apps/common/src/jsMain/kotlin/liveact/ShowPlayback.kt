/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.apps.common.liveact

import io.peekandpoke.klang.audio_engine.KlangCyclicPlayback
import io.peekandpoke.klang.viz.liveact.sound.Transport
import io.peekandpoke.klang.viz.liveact.sound.TransportControls

/**
 * A song played for one show alone, as the show's [Transport]: the show's clock starts it ([play]) once the band has
 * walked in, and its time is the seconds heard of it. [begin] makes the playback and starts it; it is called once, at
 * the first [play]. The song is played live and goes on for as long as the show is seen.
 *
 * Going on, stopping and jumping, as far as a cyclic playback can: [pause] and [skip] do nothing. A cyclic playback
 * has no pause (stopping it ends it, and started again it begins at its first cycle) and cannot jump. So a click or
 * space while the song plays, and the arrows, leave it as it is: the show runs on, as a concert does.
 */
class ShowPlayback(private val begin: () -> KlangCyclicPlayback) : Transport, TransportControls {

    private var playback: KlangCyclicPlayback? = null

    /** The seconds of the song heard so far: 0 before it begins. */
    override val time: Double get() = playback?.heardSec() ?: 0.0

    /** Before it is started. */
    override val paused: Boolean get() = playback == null

    /** The song is this show's to start. */
    override val controls: TransportControls get() = this

    /** Starts the song, once: a second call (a click after the walk-in, the right arrow) changes nothing. */
    override fun play() {
        if (playback != null) {
            return
        }

        playback = begin()
    }

    /** Nothing: a cyclic playback has no pause (see the class). */
    override fun pause() {
        // the show runs on
    }

    /** Nothing: a cyclic playback cannot jump (see the class). */
    override fun skip(seconds: Double) {
        // the show runs on
    }

    /** The show is done with the song: it stops, where it plays. */
    override fun release() {
        playback?.stop()
    }
}
