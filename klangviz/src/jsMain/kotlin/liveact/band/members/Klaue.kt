/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.members

import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.band.dress.ClothPattern
import io.peekandpoke.klang.viz.liveact.band.figure.Traits

/** Klaue's sheet: the heavier one with the cap, less fine than [Player.Skalpell] and with the power. */
val KLAUE: Member = Member(
    player = Player.Klaue,
    dress = Dress(cloth = ClothPattern.Claws, glitter = 0.14),
    // the headbanger, but only in fits: about a quarter of the time he goes all the way, else he nods like the
    // others
    character = Character(
        seed = 41.0,
        traits = Traits(
            fits = Traits.Fits(every = 12.0, share = 0.22, calm = 0.30),
            depth = 0.85, beats = listOf(1.0, 0.85, 1.0, 0.85), lag = -0.03, soft = 0.11, chance = 0.25,
            look = 0.08, tilt = 0.05, lean = 0.02,
            // all head, planted feet
            parts = Traits.Parts(head = 1.15, trunk = 0.9, knees = 0.8, sway = 0.6),
        ),
    ),
    bearing = Bearing(lean = 0.10, bang = 0.24, bounce = 0.028),
    extras = listOf(Extra.GlanceAtCamera(every = 11.0, chance = 0.5)),
)
