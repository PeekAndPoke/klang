/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact

import io.peekandpoke.klang.viz.liveact.sound.Music

/**
 * A running live act's change of song, asked for from outside it (KLANG-00481): hand one to the live act's props, and
 * while its band stands, [now] tells the time on the show's clock and [change] has the show go on with another song
 * without ending: the stage, the light and the band stay, the clock runs on.
 *
 * To change: read [now], choose the moment the next song begins (the time for the band to get to its new places
 * later, `Changeover.INTRO`, and the time it takes to get the song ready), make the song's music with its readings
 * told from that moment (`Song.startsAt`), and call [change] when the clock is the changeover's time before it. One
 * handle for one live act at a time.
 */
class SongChanges {
    internal var clock: (() -> Double)? = null
    internal var take: ((score: Score, music: Music, at: Double) -> Boolean)? = null

    /** The time on the show's clock, as of the last frame; null where no band stands (yet, or any more). */
    fun now(): Double? = clock?.invoke()

    /**
     * The show goes on with [score] and its [music], whose song begins [at] that moment on the show's clock: from
     * now the instruments no longer played vanish and the band goes to its new places; the new instruments appear
     * where their players have arrived; at the moment the music starts. False, and nothing is done: where no band stands, while the
     * change before is still under way, and where [at] is not after now.
     */
    fun change(score: Score, music: Music, at: Double): Boolean {
        val take = take ?: return false

        return take(score, music, at)
    }
}
