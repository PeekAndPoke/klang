/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.visuals

import io.peekandpoke.klang.BuiltInSongs
import io.peekandpoke.klang.Song
import io.peekandpoke.klang.spine.KlangCodePlaybackCtrl
import io.peekandpoke.klang.spine.Playbacks

/**
 * Klangamp's deck: the playlist that plays the built-in songs on the Spine's song deck (the maintainer, 2026-10-29:
 * "a playlist of all built in songs and clicking one plays the song"), and what plays now, from here or from any page.
 */
object KlangampDeck {

    /**
     * The built-in song that plays, by its place in the list; -1 when nothing plays or it is not one of them. The song
     * playing on the deck first, by its id: it plays on, faded, while a code example holds the stage [now] (KLANG-00406,
     * D1). A display shows the list's title with the place, not [now]'s.
     */
    fun indexOf(now: Playbacks.NowPlaying?): Int {
        val onDeck = Playbacks.song.onDeck()

        if (onDeck != null && onDeck.ctrl.state().isPlaying) {
            return BuiltInSongs.songs.indexOfFirst { it.id == onDeck.songId }
        }

        return now?.let { n -> BuiltInSongs.songs.indexOfFirst { it.title == n.title } } ?: -1
    }

    /** The song Klang Amp played last; null before the first. */
    private var last: Song? = null

    /** The controller Klang Amp put on the deck last: [leave] stops it while it is still the one on. */
    private var started: KlangCodePlaybackCtrl? = null

    /** Plays the song Klang Amp played last again, or the playlist's first: a visual's play button. */
    fun playAgain() {
        play(last ?: BuiltInSongs.songs.first())
    }

    /** Puts [song] on the deck: the song before stops, also one a page plays, as the deck takes the stage. */
    fun play(song: Song) {
        last = song
        started = Playbacks.song.play(song)
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
        if (started != null && Playbacks.song.onDeck()?.ctrl === started) {
            Playbacks.song.stop()
        }

        started = null
    }
}
