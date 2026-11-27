/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band

import io.peekandpoke.klang.viz.liveact.band.instruments.strings.BodyOutline
import io.peekandpoke.klang.viz.liveact.band.instruments.strings.StringDesign
import io.peekandpoke.klang.viz.liveact.song.DrumTags

// The cast: who of the band plays which instrument, and which of the song's parts. A song's score holds one (see
// Score); the band, the camera, the lights and the eye contact are handed what they need of it.

/** Who sits at the drums: the [player], what they play in words ([plays]), and which of the song's tags are the [kit]'s. */
data class DrumSeat(val player: Player, val plays: String, val kit: DrumTags)

/**
 * Who plays a tune on an instrument that plays one note at a time (the marimba, the trumpet): the [player], what they
 * play in words ([plays]), and the song's [tag] for it.
 */
data class NoteSeat(val player: Player, val plays: String, val tag: String)

/**
 * Who plays a strung instrument: the [player], what they play in words ([plays]), the instrument ([design]), the
 * song's [tags] they play, the instrument's body where theirs is not the instrument's own ([outline]), and the test
 * riff in their place where there is no song (the demo's `?test`): a note every [riffStep] seconds from the midi note
 * [riffLow] up (see `testRiff`).
 */
data class StringSeat(
    val player: Player,
    val plays: String,
    val design: StringDesign,
    val tags: List<String>,
    val riffStep: Double,
    val riffLow: Int,
    val outline: BodyOutline? = null,
)

/**
 * Who of the band plays what in one song: the [drums], the [marimba] and the [trumpet] where the song has them (null
 * where it has none: KLANG-00481, Kokon is guitars, a bass and drums), and the [strings] (in the order they are moved
 * each frame). Every player has one seat. [seldom]: those whose part rests for long stretches, in the
 * order the camera favours them when they do play (see `Rounds`); that they play seldom is the song's arrangement.
 */
data class Cast(
    val drums: DrumSeat,
    val marimba: NoteSeat?,
    val trumpet: NoteSeat?,
    val strings: List<StringSeat>,
    val seldom: List<Player>,
) {
    /**
     * The one who strolls about the stage while their part rests: the trumpet player (see `TrumpetPlayer`); nobody
     * in a song without a trumpet. Who strolls in a song, also with a guitar, the score says (`Score.strolls`).
     */
    val strolls: Player? get() = trumpet?.player

    /** Who has a seat, in the order of the seats: the drums, the marimba, the trumpet, the strings. */
    val players: List<Player>
        get() = listOfNotNull(drums.player, marimba?.player, trumpet?.player) + strings.map { it.player }

    /**
     * Who plays the same instrument in this song and in [next], and so keeps it in their hands at a change of song:
     * the drummer where both have the same one, and a strung player whose instrument is of the same design and
     * outline in both.
     */
    fun keeps(next: Cast): Set<Player> {
        val strung = next.strings
            .filter { seat -> strings.any { it.player == seat.player && it.design === seat.design && it.outline == seat.outline } }
            .map { it.player }

        return (strung + listOfNotNull(drums.player.takeIf { it == next.drums.player })).toSet()
    }

    /** The song's tags [player] is seen to play by (the camera and the eyes go by them); none where they have no seat. */
    fun tagsOf(player: Player): List<String> = when (player) {
        drums.player -> drums.kit.beat
        marimba?.player -> listOfNotNull(marimba?.tag)
        trumpet?.player -> listOfNotNull(trumpet?.tag)
        else -> strings.firstOrNull { it.player == player }?.tags.orEmpty()
    }

    /** What [player] plays, in words (the page "Meet the band" says it); null where they have no seat. */
    fun playsOf(player: Player): String? = when (player) {
        drums.player -> drums.plays
        marimba?.player -> marimba?.plays
        trumpet?.player -> trumpet?.plays
        else -> strings.firstOrNull { it.player == player }?.plays
    }
}
