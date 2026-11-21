/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.scores

import io.peekandpoke.klang.viz.liveact.Score
import io.peekandpoke.klang.viz.liveact.band.Cast
import io.peekandpoke.klang.viz.liveact.band.DrumSeat
import io.peekandpoke.klang.viz.liveact.band.Entrance
import io.peekandpoke.klang.viz.liveact.band.NoteSeat
import io.peekandpoke.klang.viz.liveact.band.Place
import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.band.StagePlot
import io.peekandpoke.klang.viz.liveact.band.StringSeat
import io.peekandpoke.klang.viz.liveact.band.instruments.bass.Bass
import io.peekandpoke.klang.viz.liveact.band.instruments.guitar.Guitar
import io.peekandpoke.klang.viz.liveact.song.DrumTags
import io.peekandpoke.klang.viz.liveact.stage.Stage

/**
 * Der Schmetterling's score: the band Notstrom plays it, each on the instrument and the part named here, from the
 * places of this stage plot. The tags are the song's, as its code names them and its recording keeps them.
 *
 * Also what the app's studio plays every other song with: a song without these tags leaves those who would play
 * them standing.
 */
val DER_SCHMETTERLING: Score = Score(
    cast = Cast(
        drums = DrumSeat(
            player = Player.Wucht,
            plays = "Drums",
            // the cymbals: the hats, the crash and the ride, told apart by their sound's name, and the count-in
            kit = DrumTags(kick = "kick", snare = "snare", cymbals = listOf("hats", "countin")),
        ),
        marimba = NoteSeat(player = Player.Rune, plays = "Marimba", tag = "lead"),
        // the "Gummieimer", which sounds like a held melody: played on the trumpet
        trumpet = NoteSeat(player = Player.Sirene, plays = "Trumpet", tag = "trommel"),
        strings = listOf(
            StringSeat(
                player = Player.Beben,
                plays = "Bass",
                design = Bass.DESIGN,
                tags = listOf("bass"),
                riffStep = 0.5,
                riffLow = 28,
            ),
            StringSeat(
                player = Player.Skalpell,
                plays = "Lead guitar",
                design = Guitar.DESIGN,
                tags = listOf("guitar1"),
                riffStep = 0.25,
                riffLow = 52,
            ),
            StringSeat(
                player = Player.Klaue,
                plays = "Rhythm guitar",
                design = Guitar.DESIGN,
                // the rhythm guitar's two parts
                tags = listOf("guitar2", "guitar3"),
                riffStep = 0.125,
                riffLow = 40,
                outline = Guitar.SINGLE_CUTAWAY,
            ),
        ),
        // the marimba and the trumpet rest for long stretches of this song
        seldom = listOf(Player.Rune, Player.Sirene),
    ),
    // one plot for this band so far (the maintainer, 2026-11-17: "into a different thing, like Bühnenaufstellung ...
    // for now hardcoded")
    plot = StagePlot(
        // the walk-in: the stage's clock starts this many seconds before the song
        intro = 15.0,
        places = mapOf(
            Player.Wucht to Place(x = 0.1, z = -2.1, turn = 0.0, y = Stage.RISER),
            Player.Beben to Place(x = -2.15, z = 0.4, turn = 0.15),
            Player.Skalpell to Place(x = -3.0, z = 1.7, turn = 0.3),
            Player.Sirene to Place(x = -0.7, z = 2.2, turn = 0.1),
            Player.Rune to Place(x = 1.7, z = 0.4, turn = -0.15),
            Player.Klaue to Place(x = 3.0, z = 1.7, turn = -0.3),
        ),
        // the drummer first, from the back and up onto the riser, the frontwoman last
        entrances = linkedMapOf(
            Player.Wucht to Entrance(fromX = 0.1, fromZ = -7.5, start = -15.0, end = -9.0),
            Player.Beben to Entrance(fromX = -8.5, fromZ = 0.9, start = -13.5, end = -7.5),
            Player.Klaue to Entrance(fromX = 8.5, fromZ = 2.0, start = -12.5, end = -6.5),
            Player.Skalpell to Entrance(fromX = -8.5, fromZ = 2.1, start = -11.5, end = -5.5),
            Player.Rune to Entrance(fromX = 8.5, fromZ = 0.9, start = -10.5, end = -4.8),
            Player.Sirene to Entrance(fromX = -8.5, fromZ = 2.9, start = -8.5, end = -2.4),
        ),
    ),
)
