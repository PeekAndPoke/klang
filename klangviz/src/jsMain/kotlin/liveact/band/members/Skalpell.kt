/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.members

import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.band.dress.ClothPattern
import io.peekandpoke.klang.viz.liveact.band.figure.Traits

/** Skalpell's sheet: slim, long hair, the virtuoso. */
val SKALPELL: Member = Member(
    player = Player.Skalpell,
    dress = Dress(cloth = ClothPattern.Spiral, glitter = 0.14, combedBack = true),
    // loose and restless: uneven, looks round a lot, skips nods
    character = Character(
        seed = 37.0,
        traits = Traits(
            depth = 0.8, beats = listOf(0.9, 0.55, 0.9, 0.55), lag = 0.02, soft = 0.13, chance = 0.6, skip = 0.18,
            look = 0.30, tilt = 0.10, lean = 0.03,
            // leans his whole trunk into it
            parts = Traits.Parts(head = 0.9, trunk = 1.5, knees = 0.7, sway = 1.2),
        ),
    ),
    bearing = Bearing(lean = -0.05, bang = 0.14, bounce = 0.025),
)
