/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.peekandpoke.klang.BuiltInSongs
import io.peekandpoke.klang.Player
import io.peekandpoke.klang.Song
import kotlin.js.Date

/**
 * Klangamp's deck: the one player its playlists play the built-in songs on (the maintainer, 2026-10-29: "a playlist of
 * all built in songs and clicking one plays the song"), and what plays now, from here or from any page.
 */
object KlangampDeck {

    /**
     * The playlist's one controller, made once: a controller per song would keep its subscriptions for good. It takes
     * the stage, so a song playing on a page stops when a song here starts.
     */
    private val ctrl: KlangCodePlaybackCtrl by lazy { KlangCodePlaybackCtrl.builder().exclusive().build() }

    /** When the playback now on stage started; null while nothing plays. */
    private var startedMs: Double? = null

    private var lastPlayback: Any? = null

    /** Called by whoever shows the time, on each change of what plays: the start of a new playback is noted. */
    fun noteNowPlaying(now: Player.NowPlaying?) {
        if (now?.playback != lastPlayback) {
            startedMs = if (now == null) null else Date.now()
            lastPlayback = now?.playback
        }
    }

    /** The seconds the playback on stage has played at [nowMs]; null while nothing plays. */
    fun secondsAt(nowMs: Double): Int? = startedMs?.let { ((nowMs - it) / 1000).toInt().coerceAtLeast(0) }

    /** The built-in song [now] plays, by its place in the list; -1 when nothing plays or it is not one of them. */
    fun indexOf(now: Player.NowPlaying?): Int = now?.let { n -> BuiltInSongs.songs.indexOfFirst { it.title == n.title } } ?: -1

    fun play(song: Song) {
        ctrl.stop()
        ctrl.setCode(song.code)
        ctrl.setRpm(song.rpm)
        ctrl.setTitle(song.title)
        ctrl.play()
    }

    /** Stops what plays: Klangamp's song, or a page's, which gives the stage up as it would to another song. */
    fun stop() {
        Player.requestExclusivePlayback(self = ctrl) { other -> (other as? KlangCodePlaybackCtrl)?.stop() }
        ctrl.stop()
    }

    /** Plays the song [step] places on from [now]'s, round the list; the first when nothing of the list plays. */
    fun skip(now: Player.NowPlaying?, step: Int) {
        val songs = BuiltInSongs.songs
        val from = indexOf(now)
        val next = if (from < 0) 0 else (from + step + songs.size) % songs.size

        play(songs[next])
    }

    /** Stops the song Klangamp started, when its visual is left; one a page started plays on. */
    fun leave() {
        ctrl.stop()
    }

    /** Seconds as m:ss. */
    fun clock(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
