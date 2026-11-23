/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact

import io.peekandpoke.klang.viz.liveact.song.Song
import io.peekandpoke.klang.viz.liveact.sound.Energy
import io.peekandpoke.klang.viz.liveact.sound.Music

/**
 * The switches a show can be given, as a page's address gives them (`?seed=7&t=62.1&still`): what is frozen, what
 * is left out, where the camera stands. All of them are for trying things out and for stills; a show without any
 * is the show as it is meant. Each is read through its entry in [Switch], the list of them all.
 */
data class Switches(private val values: Map<String, String> = emptyMap()) {
    fun has(switch: Switch): Boolean = switch.key in values

    operator fun get(switch: Switch): String? = values[switch.key]

    fun double(switch: Switch): Double? = values[switch.key]?.toDoubleOrNull()

    /** Three numbers with commas between them (`cam=1,2,3`); null where there are not three. */
    fun triple(switch: Switch): Triple<Double, Double, Double>? {
        val parts = values[switch.key]?.split(",")?.mapNotNull { it.toDoubleOrNull() } ?: return null

        return if (parts.size == 3) Triple(parts[0], parts[1], parts[2]) else null
    }

    companion object {
        /** The switches of a query string (`?a=1&b`, with or without the question mark). */
        fun parse(query: String): Switches {
            val pairs = query.removePrefix("?").split("&").filter { it.isNotEmpty() }.associate { part ->
                part.substringBefore("=") to part.substringAfter("=", missingDelimiterValue = "")
            }

            return Switches(pairs)
        }
    }
}

/**
 * What one performance is given: its [seed], the [music] it plays to, and the [switches].
 *
 * A performance can go from one song to the next without ending ([take]; KLANG-00481, the maintainer, 2026-11-23:
 * "I would not cut between the scenes"): the show's clock runs on, and the next song begins at a moment on it
 * ([songStart]). So the song, the tempo and the bar are the ones of the song that plays now; whoever keeps one of
 * them keeps the song's of the moment it was asked.
 *
 * Every chance in the choreography is drawn under the seed ([own]), so each seed is another performance (who nods
 * how at which moment, the wandering, the fits, the looks, the strolls, the lights' turns, the camera) and the same
 * seed the same one again. The playing itself never depends on it.
 */
class Show(
    val seed: Int,
    music: Music = Music.none,
    val switches: Switches = Switches(),
) {
    /** The music the show plays to now. */
    var music: Music = music
        private set

    /** When the song that plays now began, on the show's clock (seconds): 0 for the first. */
    var songStart: Double = 0.0
        private set

    /** The song, as far ahead as it is known; null where there is none (the band plays test notes). */
    val song: Song? get() = music.song

    /** Beats a minute. */
    val bpm: Double get() = song?.bpm ?: 120.0

    /** A bar of four beats, in seconds. */
    val bar: Double get() = 240.0 / bpm

    /** The beat at [t] on the show's clock, counted from where the song that plays now began. */
    fun beatAt(t: Double): Double = (t - songStart) * bpm / 60

    /**
     * Goes on with [next], whose song begins at [at] on the show's clock (its events are told in the clock's time
     * already: see [Song.startsAt]).
     */
    fun take(next: Music, at: Double) {
        music = next
        // NaN-guard: a moment of no number is the clock's start
        songStart = if (at != at) 0.0 else at
    }

    /** The time the show is frozen at (`?t=`), for stills; null where it runs. */
    val frozen: Double? = switches.double(Switch.Time)

    /**
     * The sound's energy right now: set every frame, before anyone moves. The nod is as strong as the sound: nearly
     * none in a quiet passage, deep when the band is loud, with an extra dip on a burst.
     */
    var energy: Energy = Energy.even

    /** The sound's energy at [t] seconds. */
    fun energyAt(t: Double): Energy = music.energyAt(t)

    /** When the events with any of [tags] start, sorted; none without a song. */
    fun times(vararg tags: String): List<Double> = song?.times(tags.toList()).orEmpty()

    /** A number from 0 to 1 that belongs to [index] and [salt], always the same one under this show's seed. */
    fun own(index: Double, salt: Double): Double = hash01(index * 12.9898 + salt * 78.233 + seed * 0.6180339)

    fun own(index: Int, salt: Double): Double = own(index.toDouble(), salt)

    /** A smooth wandering value from -1 to 1 along [x]: a new random aim at every whole number, eased between them. */
    fun drift(x: Double, salt: Double): Double = valueNoise(x) { i -> own(i, salt) } * 2 - 1
}
