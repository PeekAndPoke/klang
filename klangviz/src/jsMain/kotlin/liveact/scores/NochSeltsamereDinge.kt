/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.scores

import io.peekandpoke.klang.viz.liveact.Score
import io.peekandpoke.klang.viz.liveact.band.Cast
import io.peekandpoke.klang.viz.liveact.band.DrumSeat
import io.peekandpoke.klang.viz.liveact.band.NoteSeat
import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.band.StringSeat
import io.peekandpoke.klang.viz.liveact.band.instruments.bass.Bass
import io.peekandpoke.klang.viz.liveact.band.instruments.guitar.Guitar
import io.peekandpoke.klang.viz.liveact.song.DrumTags

/**
 * The score of Noch seltsamere Dinge, Seltsamere Dinge rewritten for the band. Who plays what is the maintainer's
 * (2026-11-26): the glass guitar's two halves, which play through one amp, are Skalpell's (the roots) and Klaue's (the
 * leaps); Sirene has the melody (the arpeggio, on a guitar here, as in Kokon); Rune has the morse; Beben the bass.
 *
 * The morse has no instrument of its own yet: Rune plays it on the marimba until the morse key exists. Nobody plays
 * the claps or the shore.
 *
 * The places and the walk-in are Der Schmetterling's: the marimba stands where Rune stands there.
 */
val NOCH_SELTSAMERE_DINGE: Score = Score(
    cast = Cast(
        drums = DrumSeat(
            player = Player.Wucht,
            plays = "Drums",
            kit = DrumTags(kick = "kick", snare = "snare", cymbals = listOf("hats")),
        ),
        marimba = NoteSeat(player = Player.Rune, plays = "Morse, on the marimba", tag = "morse"),
        trumpet = null,
        strings = listOf(
            StringSeat(
                player = Player.Beben,
                plays = "Bass",
                design = Bass.A,
                tags = listOf("bass"),
                riffStep = 0.5,
                riffLow = 28,
            ),
            StringSeat(
                player = Player.Skalpell,
                plays = "Glass guitar, the roots",
                design = Guitar.A,
                tags = listOf("roots"),
                riffStep = 0.5,
                riffLow = 52,
            ),
            StringSeat(
                player = Player.Klaue,
                plays = "Glass guitar, the leaps",
                design = Guitar.C,
                tags = listOf("leaps"),
                riffStep = 0.25,
                riffLow = 40,
            ),
            StringSeat(
                player = Player.Sirene,
                plays = "Guitar, the arpeggio",
                design = Guitar.B,
                tags = listOf("arp"),
                riffStep = 0.25,
                riffLow = 48,
            ),
        ),
        // the morse comes late and goes early
        seldom = listOf(Player.Rune),
    ),
    plot = DER_SCHMETTERLING.plot,
    eyeContact = DER_SCHMETTERLING.eyeContact,
)
