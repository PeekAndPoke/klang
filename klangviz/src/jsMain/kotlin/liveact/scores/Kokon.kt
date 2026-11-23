/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.scores

import io.peekandpoke.klang.viz.liveact.Score
import io.peekandpoke.klang.viz.liveact.band.Cast
import io.peekandpoke.klang.viz.liveact.band.DrumSeat
import io.peekandpoke.klang.viz.liveact.band.Entrance
import io.peekandpoke.klang.viz.liveact.band.Place
import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.band.StagePlot
import io.peekandpoke.klang.viz.liveact.band.StringSeat
import io.peekandpoke.klang.viz.liveact.band.instruments.bass.Bass
import io.peekandpoke.klang.viz.liveact.band.instruments.guitar.Guitar
import io.peekandpoke.klang.viz.liveact.song.DrumTags
import io.peekandpoke.klang.viz.liveact.stage.Stage

/**
 * Kokon's score (KLANG-00481): guitars, a bass and drums, no marimba and no trumpet, so nobody strolls. The tags are
 * the song's (every voice carries one with its own name). Who plays what is the maintainer's (2026-11-23): Sirene
 * has a guitar here; the dialogue of the melody above the wall ("soar") and the cocoon's thread ("spin") is hers and
 * Rune's in turns; the wall's two parts ("wings", "chug"), which sound together, are Skalpell's and Klaue's; and
 * nobody plays the heartbeat ("beat"). The rest is this score's own choice: Sirene has the melody wherever it is
 * (sing, soar, chime), Rune the thread and the last chord, Skalpell the swells, Klaue the landing's chord.
 *
 * The bass and the drums play only in the second run of the heavy block: Beben and Wucht wait for it.
 *
 * The places (the maintainer's choice of three sketches, 2026-11-23: "layout b"): a half circle open to the audience,
 * the drummer on his riser at its back, Sirene and Rune side by side in its middle before the riser (their dialogue
 * in the middle), Skalpell and Klaue at its tips, Beben beside the riser. All but the drummer sit cross-legged on the
 * floor, the instrument in the lap; the drummer has his stool.
 */
val KOKON: Score = Score(
    cast = Cast(
        drums = DrumSeat(
            player = Player.Wucht,
            plays = "Drums",
            kit = DrumTags(kick = "kick", snare = "snare", cymbals = listOf("hats")),
        ),
        marimba = null,
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
                plays = "Guitar, the wings",
                design = Guitar.A,
                tags = listOf("wings", "swell"),
                riffStep = 0.25,
                riffLow = 52,
            ),
            StringSeat(
                player = Player.Klaue,
                plays = "Guitar, the chug",
                design = Guitar.C,
                tags = listOf("chug", "strike"),
                riffStep = 0.125,
                riffLow = 40,
            ),
            StringSeat(
                player = Player.Sirene,
                plays = "Guitar, the melody",
                design = Guitar.B,
                tags = listOf("sing", "soar", "chime"),
                riffStep = 0.5,
                riffLow = 64,
            ),
            StringSeat(
                player = Player.Rune,
                plays = "Guitar, the thread",
                design = Guitar.B,
                tags = listOf("spin", "strum"),
                riffStep = 0.25,
                riffLow = 57,
            ),
        ),
        // the bass and the drums wait for the second run of the heavy block
        seldom = listOf(Player.Wucht, Player.Beben),
    ),
    plot = StagePlot(
        intro = 15.0,
        places = mapOf(
            Player.Wucht to Place(x = 0.1, z = -2.1, turn = 0.0, y = Stage.RISER),
            Player.Beben to Place(x = -2.1, z = -0.7, turn = 0.4, sits = true),
            Player.Skalpell to Place(x = -2.4, z = 1.2, turn = 0.9, sits = true),
            Player.Sirene to Place(x = -0.75, z = 0.35, turn = 0.15, sits = true),
            Player.Rune to Place(x = 0.75, z = 0.35, turn = -0.15, sits = true),
            Player.Klaue to Place(x = 2.4, z = 1.2, turn = -0.9, sits = true),
        ),
        entrances = linkedMapOf(
            Player.Wucht to Entrance(fromX = 0.1, fromZ = -7.5, start = -15.0, end = -9.0),
            Player.Beben to Entrance(fromX = -8.5, fromZ = -0.2, start = -13.5, end = -7.5),
            Player.Klaue to Entrance(fromX = 8.5, fromZ = 1.6, start = -12.5, end = -6.5),
            Player.Skalpell to Entrance(fromX = -8.5, fromZ = 1.6, start = -11.5, end = -5.5),
            Player.Rune to Entrance(fromX = 8.5, fromZ = 0.6, start = -10.5, end = -4.8),
            Player.Sirene to Entrance(fromX = -8.5, fromZ = 0.6, start = -8.5, end = -2.4),
        ),
    ),
    // as in Der Schmetterling, until Kokon asks for its own
    eyeContact = DER_SCHMETTERLING.eyeContact,
)
