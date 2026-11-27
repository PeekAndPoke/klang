/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact

import io.peekandpoke.klang.viz.liveact.band.Cast
import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.band.StagePlot
import io.peekandpoke.klang.viz.liveact.band.members.Members
import io.peekandpoke.klang.viz.liveact.band.skills.GuitarStroll
import io.peekandpoke.klang.viz.liveact.band.skills.Skill
import io.peekandpoke.klang.viz.liveact.triggers.ChanceInSlots

/**
 * A song's score: what binds one song to the show, as plain data. The [cast] (who of the band plays which instrument
 * and which of the song's parts), the stage [plot] (where each one stands, and how they walk in), and when two of the
 * band look at each other ([eyeContact], the whole band's timing: see `contactAt`). [idles]: how many of the members'
 * idle moves happen in this song, 0 to 1 (a hand shaken out, a scratch of the head: all of them in a lively song, few
 * or none in a quiet one; the maintainer, 2026-11-23, of Kokon: "very reduced or almost fully off"). [resting]: skills
 * that nobody uses in this song though they have them on their sheet (told by equality, so the plain ones: Kokon
 * leaves out `GhostEyes`). The live act is built from a show
 * (the song, the seed, the switches) and its score: nothing else in it knows a song.
 *
 * Each song has its own; they lie in the package `scores`, apart from what reads them.
 */
data class Score(
    val cast: Cast,
    val plot: StagePlot,
    val eyeContact: ChanceInSlots,
    val idles: Double = 1.0,
    val resting: Set<Skill> = emptySet(),
) {
    /**
     * The one who strolls about the stage: the trumpet player where the song has one (she strolls while her part
     * rests, see `TrumpetPlayer`); else a string player whose sheet has the walk with the guitar ([GuitarStroll]),
     * which this song does not rest, and who stands at their place (they stroll while they play, see
     * `StringStroller`); nobody else. The band and the camera are handed it (the camera answers the one who strolls).
     */
    val strolls: Player?
        get() = cast.strolls ?: cast.strings.firstOrNull { seat ->
            GuitarStroll in Members.of(seat.player).skills && GuitarStroll !in resting && plot.places[seat.player]?.sits != true
        }?.player
}
