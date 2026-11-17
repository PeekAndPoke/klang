/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.members

import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.band.dress.ClothPattern
import io.peekandpoke.klang.viz.liveact.band.figure.Traits

/** Beben's sheet: tall and thin. */
val BEBEN: Member = Member(
    player = Player.Beben,
    dress = Dress(cloth = ClothPattern.Pinstripe, glitter = 0.14),
    // the steady one: late, mostly on one and three, always the same whatever the band does; looks about slowly
    character = Character(
        seed = 23.0,
        traits = Traits(
            steady = 0.9, depth = 0.9, beats = listOf(1.0, 0.2, 0.7, 0.2), lag = 0.07, soft = 0.18, chance = 0.12,
            skip = 0.0, look = 0.22, tilt = 0.06, lean = 0.02,
            // from the knees and hips, the head hardly
            parts = Traits.Parts(head = 0.6, trunk = 0.8, knees = 1.0, sway = 1.3),
        ),
    ),
    bearing = Bearing(lean = 0.10, bang = 0.16, bounce = 0.045),
)
