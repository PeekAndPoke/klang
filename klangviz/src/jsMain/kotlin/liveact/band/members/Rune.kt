/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.members

import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.band.dress.ClothPattern
import io.peekandpoke.klang.viz.liveact.band.figure.Traits
import io.peekandpoke.klang.viz.liveact.band.skills.CrossedMallets
import io.peekandpoke.klang.viz.liveact.band.skills.EyeContact

/**
 * Rune's sheet: the riddle of the band, with Medusa's snakes in her shadow (see [Dress.shadowSnakes]). Before she
 * plays and when she is done, she crosses her mallets below her head for a moment, like a skull and bones
 * ([CrossedMallets]; the maintainer, 2026-11-18: only she has it for now).
 */
val RUNE: Member = Member(
    player = Player.Rune,
    dress = Dress(cloth = ClothPattern.Diamonds, glitter = 0.21, shadowSnakes = true),
    // composed: modest nods (a little more than at first: the maintainer, 2026-11-16), her head tilting from
    // side to side
    character = Character(
        seed = 53.0,
        // how wild: the most elegant of the band
        wildness = 1,
        traits = Traits(
            depth = 0.68, beats = listOf(1.0, 0.25, 0.6, 0.25), lag = 0.03, soft = 0.18, chance = 0.35,
            look = 0.10, tilt = 0.12, lean = 0.015,
            // upright, quiet below the neck
            parts = Traits.Parts(head = 1.0, trunk = 0.6, knees = 0.5),
        ),
    ),
    bearing = null,
    // she answers a look; before a phrase and after it her mallets cross, held a second and a half, a little more
    // than a second into the pose and out of it
    skills = listOf(EyeContact, CrossedMallets(hold = 1.5, move = 1.2)),
)
