/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact

import io.peekandpoke.klang.viz.liveact.song.Song

/**
 * The music as the live act knows it. This is all it asks of whoever plays: a recording beside its audio, or the
 * engine playing a song live. The live act depends on nothing else of them.
 *
 * It needs the song ahead of the sound, not only what sounds now: a stick goes up before its hit, the frontwoman
 * raises her arm seconds before a phrase and strolls where she has nothing to play for half a minute, the camera
 * goes to who will play next. A recording knows all of itself; a song played live is asked ahead (a pattern can be
 * asked for its events in any stretch of time).
 */
interface Music {
    /** Where the music comes from, in a word or two, for the line at the top: "recording", "live". */
    val source: String

    /** The song's events, as far ahead as they are known; null where there is no song (the band plays test notes). */
    val song: Song?

    /** The sound's energy at [t] seconds of the song. */
    fun energyAt(t: Double): Energy

    /**
     * Starts to listen: what moves the music and tells where it is, for this one listener; null where nothing
     * sounds (the show then runs by its own clock). Every call gives a listener of its own, which says when it is
     * done ([Transport.release]). It is called only when a show starts to listen, so it may cost something (an
     * audio file is fetched).
     */
    fun listen(): Transport?

    companion object {
        /** No music at all: the band plays its test notes at an even level, by the show's own clock. */
        val none: Music = RecordedMusic(song = null, loudness = null)
    }
}

/** Where the music is, for one listener; and, where the listener may move it, the [controls] to do so. */
interface Transport {
    /** Where the music is, in seconds of the song. */
    val time: Double

    /** Whether it stands still. */
    val paused: Boolean

    /**
     * Going on, stopping and jumping, where the music is this listener's to move (a recording played for it alone).
     * Null where the music is someone else's (the app plays a song): the listener then only follows it. It takes
     * the music's time as it is, starts and stops nothing, and makes no show of a start.
     */
    val controls: TransportControls?

    /**
     * This listener is done: it is not used after. What that means for the music is the music's own: a recording
     * played for this listener alone stops and is let go; music the app plays goes on.
     */
    fun release()
}

/** What moves music that is the listener's own. */
interface TransportControls {
    /** Goes on from where it is. */
    fun play()

    /** Stands still where it is. */
    fun pause()

    /** Jumps by [seconds], back or ahead; never before the song's start. */
    fun skip(seconds: Double)
}

/**
 * A recording as [Music]: one render of a [song], the [loudness] measured from its audio, and [makeTransport] for
 * what plays that audio: asked each time a show starts to listen, so each listener plays the recording for itself.
 */
class RecordedMusic(
    override val song: Song?,
    private val loudness: Loudness?,
    private val makeTransport: () -> Transport? = { null },
) : Music {
    override val source: String = if (song != null) "recording" else "test notes"

    override fun listen(): Transport? = makeTransport()

    override fun energyAt(t: Double): Energy = loudness?.at(t) ?: Energy.even
}
