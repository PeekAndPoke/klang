/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.members

import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.band.dress.ClothPattern
import io.peekandpoke.klang.viz.liveact.band.figure.Gait
import io.peekandpoke.klang.viz.liveact.band.figure.Traits
import io.peekandpoke.klang.viz.liveact.band.skills.EyeContact
import io.peekandpoke.klang.viz.liveact.band.skills.ShakeOut
import io.peekandpoke.klang.viz.liveact.band.skills.Straighten
import io.peekandpoke.klang.viz.liveact.band.skills.TuneUp
import io.peekandpoke.klang.viz.liveact.triggers.ChanceInSlots

/**
 * Skalpell's sheet: slim, long hair, the virtuoso. Where his part pauses (the whole middle of the song) he tunes up
 * ([TuneUp]) and shakes out his fretting hand ([ShakeOut]): a player's habits, between elegant and animal as he is
 * (see [Character.wildness]).
 */
val SKALPELL: Member = Member(
    player = Player.Skalpell,
    dress = Dress(cloth = ClothPattern.Spiral, glitter = 0.14, combedBack = true),
    // loose and restless: uneven, looks round a lot, skips nods
    character = Character(
        seed = 37.0,
        // how wild: a mix of both
        wildness = 5,
        // how his hips move as he walks: the precise one walks upright and straight, a little hip
        gait = Gait(hips = 0.35),
        traits = Traits(
            depth = 0.8, beats = listOf(0.9, 0.55, 0.9, 0.55), lag = 0.02, soft = 0.13, chance = 0.6, skip = 0.18,
            look = 0.30, tilt = 0.10, lean = 0.03,
            // leans his whole trunk into it
            parts = Traits.Parts(head = 0.9, trunk = 1.5, knees = 0.7, sway = 1.2),
        ),
    ),
    bearing = Bearing(lean = -0.05, bang = 0.14, bounce = 0.025),
    // he answers a look. Where his part pauses: he tunes up now and then (a chance every 15 s, half of them taken,
    // held three and a half to five seconds) and shakes out his fretting hand (every 9 s, half, two to three seconds)
    skills = listOf(
        EyeContact,
        TuneUp(
            ChanceInSlots(
                every = 15.0,
                chance = 0.5,
                cooldown = 0.0,
                least = 3.5,
                most = 5.0,
                earliest = 0.5,
                reserve = 6.8,
                rise = 0.8,
                fall = 0.9,
            ),
        ),
        ShakeOut(
            ChanceInSlots(
                every = 9.0,
                chance = 0.5,
                cooldown = 0.0,
                least = 2.0,
                most = 3.0,
                earliest = 0.5,
                reserve = 4.6,
                rise = 0.6,
                fall = 0.7,
            ),
        ),
        // now and then he straightens up from his instrument: a chance every 21 s, a little over half of them taken
        Straighten(every = 21.0, chance = 0.55),
    ),
)
