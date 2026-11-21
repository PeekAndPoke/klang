/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.members

import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.band.dress.ClothPattern
import io.peekandpoke.klang.viz.liveact.band.figure.Gait
import io.peekandpoke.klang.viz.liveact.band.figure.Traits
import io.peekandpoke.klang.viz.liveact.band.skills.CountIn
import io.peekandpoke.klang.viz.liveact.band.skills.CrossedMallets
import io.peekandpoke.klang.viz.liveact.band.skills.EyeContact
import io.peekandpoke.klang.viz.liveact.band.skills.NeckStretch
import io.peekandpoke.klang.viz.liveact.triggers.BeforePhrase
import io.peekandpoke.klang.viz.liveact.triggers.ChanceInSlots

/**
 * Rune's sheet: the riddle of the band, with Medusa's snakes in her shadow (see [Dress.shadowSnakes]). Before she
 * plays and when she is done, she crosses her mallets below her head for a moment, like a skull and bones
 * ([CrossedMallets]; the maintainer, 2026-11-18: only she has it for now). Her head counts in the last bar before a
 * phrase ([CountIn]), and where her part pauses she stretches her neck now and then ([NeckStretch]): elegant, as she
 * is (see [Character.wildness]).
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
        // how her hips move as she walks: she glides, the hips going quietly under a body that stays still
        gait = Gait(hips = 0.45),
        traits = Traits(
            depth = 0.68, beats = listOf(1.0, 0.25, 0.6, 0.25), lag = 0.03, soft = 0.18, chance = 0.35,
            look = 0.10, tilt = 0.12, lean = 0.015,
            // upright, quiet below the neck
            parts = Traits.Parts(head = 1.0, trunk = 0.6, knees = 0.5),
        ),
    ),
    bearing = null,
    // she answers a look; before a phrase and after it her mallets cross, held a second and a half, a little more
    // than a second into the pose and out of it; her head counts in the last bar before a phrase (a phrase begins
    // after a pause of two bars or more); and where her part pauses she now and then stretches her neck, one slow
    // stretch to a use (a chance every 29 s, a little under half of them taken)
    skills = listOf(
        EyeContact,
        CrossedMallets(hold = 1.5, move = 1.2),
        CountIn(BeforePhrase(pause = 2.0)),
        NeckStretch(
            ChanceInSlots(
                every = 29.0,
                chance = 0.45,
                cooldown = 0.0,
                least = 3.8,
                most = 3.8,
                earliest = 0.5,
                reserve = 5.5,
                rise = 0.7,
                fall = 0.8,
            ),
        ),
    ),
)
