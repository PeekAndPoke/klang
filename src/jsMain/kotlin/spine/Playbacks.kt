/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.spine

import io.peekandpoke.klang.audio_engine.KlangCyclicPlayback
import io.peekandpoke.ultra.streams.Stream
import io.peekandpoke.ultra.streams.StreamSource

/**
 * What plays in the app, next to the [Player], which keeps the engine itself (KLANG-00406, "The Spine's playbacks"):
 * the real song on its [song] deck, and the stage, what plays now for the visuals and Klang Amp ([nowPlaying]).
 * The other lanes (previews, sounds, live) come with KLANG-00406's later steps.
 */
object Playbacks {

    /** The real song that plays: one at a time, kept while one navigates. Made on first use, as it starts the engine's status. */
    val song: SongDeck by lazy { SongDeck() }

    /**
     * Snapshot of whatever is considered the "primary" playback across the app.
     *
     * Controllers ([KlangCodePlaybackCtrl]) publish into this when they start/stop so that other UI (sidebar badges,
     * headers, the song list, ...) can reactively observe what's currently on stage without subscribing to every
     * controller.
     */
    data class NowPlaying(
        val title: String?,
        val code: String,
        val rpm: Double,
        val playback: KlangCyclicPlayback,
    )

    private val _nowPlaying = StreamSource<NowPlaying?>(null)
    val nowPlaying: Stream<NowPlaying?> = _nowPlaying.readonly

    private var nowPlayingOwner: PlaybackOwner? = null

    /**
     * Publishes (or clears) the "now playing" snapshot. A clear is only honored when [owner]
     * is the current owner, so a stale controller can't wipe someone else's state.
     */
    fun publishNowPlaying(owner: PlaybackOwner, value: NowPlaying?) {
        if (value == null) {
            if (nowPlayingOwner === owner) {
                nowPlayingOwner = null
                _nowPlaying(null)
            }
        } else {
            nowPlayingOwner = owner
            _nowPlaying(value)
        }
    }

    /**
     * Opt-in "only one at a time" coordination: the current [nowPlaying] owner, when it is not [self],
     * is stopped, which clears the stage for [self].
     */
    fun requestExclusivePlayback(self: PlaybackOwner) {
        val other = nowPlayingOwner

        if (other != null && other !== self) {
            other.stop()
        }
    }

    /** Stops whatever is on the stage, from wherever it was started: a visual's stop button. */
    fun stopNowPlaying() {
        nowPlayingOwner?.stop()
    }
}
