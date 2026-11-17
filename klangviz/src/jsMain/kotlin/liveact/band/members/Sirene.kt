/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.members

import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.band.dress.ClothPattern
import io.peekandpoke.klang.viz.liveact.band.figure.Traits

/** Sirene's sheet: the frontwoman. */
object Sirene : Member(
    name = "Sirene",
    player = Player.Sirene,
    dress = Dress(cloth = ClothPattern.Braid, glitter = 0.21),
    // the frontman: the balanced one. Every part takes the beat alike, on time, even, with little chance in it
    character = Character(
        seed = 67.0,
        traits = Traits(
            depth = 0.9, beats = listOf(1.0, 0.5, 0.8, 0.5), lag = 0.0, soft = 0.13, chance = 0.2, skip = 0.0,
            look = 0.14, tilt = 0.04, lean = 0.02,
            parts = Traits.Parts(head = 1.0, trunk = 1.0, knees = 1.0, sway = 1.0),
        ),
    ),
    bearing = null,
)
