/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.members

import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.band.dress.ClothPattern
import io.peekandpoke.klang.viz.liveact.band.figure.Traits
import io.peekandpoke.klang.viz.liveact.band.skills.EyeContact

/** Wucht's sheet: the muscular one. */
val WUCHT: Member = Member(
    player = Player.Wucht,
    dress = Dress(cloth = ClothPattern.Tiger, glitter = 0.14),
    // the engine of the band: on every beat, steady, a touch ahead
    character = Character(
        seed = 11.0,
        // how wild: the most animal of the band
        wildness = 9,
        traits = Traits(
            depth = 1.0, beats = listOf(1.0, 0.7, 0.9, 0.7), lag = -0.02, soft = 0.10, chance = 0.15,
            look = 0.05, tilt = 0.03, lean = 0.01,
            // from the trunk: his whole upper body goes
            parts = Traits.Parts(head = 1.0, trunk = 1.3, knees = 0.8),
        ),
    ),
    bearing = null,
    skills = listOf(EyeContact),
)
