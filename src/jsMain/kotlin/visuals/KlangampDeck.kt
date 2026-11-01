/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.visuals

import io.peekandpoke.klang.BuiltInSongs
import io.peekandpoke.klang.Song
import io.peekandpoke.klang.spine.Playbacks

/**
 * Klangamp's deck: the playlist that plays the built-in songs on the Spine's song deck (the maintainer, 2026-10-29:
 * "a playlist of all built in songs and clicking one plays the song"), and what plays now, from here or from any page.
 */
object KlangampDeck {

    /**
     * The seconds the playback on stage has been heard; null while nothing plays. Counted on the backend's position,
     * not the wall clock (KLANG-00399): after a stall it shows what played, not the time that passed.
     */
    fun seconds(): Int? = Playbacks.nowPlaying()?.playback?.heardSec()?.toInt()

    /** The built-in song [now] plays, by its place in the list; -1 when nothing plays or it is not one of them. */
    fun indexOf(now: Playbacks.NowPlaying?): Int =
        now?.let { n -> BuiltInSongs.songs.indexOfFirst { it.title == n.title } } ?: -1

    /** The song Klang Amp played last; null before the first. */
    private var last: Song? = null

    /** The song Klang Amp put on the deck last: [leave] stops it while it is still the one on. */
    private var started: Song? = null

    /** Plays the song Klang Amp played last again, or the playlist's first: a visual's play button. */
    fun playAgain() {
        play(last ?: BuiltInSongs.songs.first())
    }

    /** Puts [song] on the deck: the song before stops, also one a page plays, as the deck takes the stage. */
    fun play(song: Song) {
        last = song
        started = song
        Playbacks.song.play(song)
    }

    /** Stops what plays: Klangamp's song, or a page's, which gives the stage up as it would to another song. */
    fun stop() {
        Playbacks.stopNowPlaying()
        Playbacks.song.stop()
    }

    /** Plays the song [step] places on from [now]'s, round the list; the first when nothing of the list plays. */
    fun skip(now: Playbacks.NowPlaying?, step: Int) {
        val songs = BuiltInSongs.songs
        val from = indexOf(now)
        val next = if (from < 0) 0 else (from + step + songs.size) % songs.size

        play(songs[next])
    }

    /** Stops the song Klangamp started, when its visual is left; one a page started plays on. */
    fun leave() {
        if (started != null && Playbacks.song.onDeck() === started) {
            Playbacks.song.stop()
        }

        started = null
    }

    /** Seconds as m:ss. */
    fun clock(seconds: Int): String = "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"
}
