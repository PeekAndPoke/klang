/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.members

import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.band.dress.ClothPattern
import io.peekandpoke.klang.viz.liveact.band.figure.Traits
import io.peekandpoke.klang.viz.liveact.band.skills.EyeContact
import io.peekandpoke.klang.viz.liveact.band.skills.NeckScratch
import io.peekandpoke.klang.viz.liveact.band.skills.SkillTiming
import io.peekandpoke.klang.viz.liveact.band.skills.StickTwirl

/**
 * Wucht's sheet: the muscular one. Where his part pauses he twirls a stick, showing off ([StickTwirl]), and scratches
 * the back of his neck with the butt of the other ([NeckScratch]): the most animal of the band (see
 * [Character.wildness]).
 */
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
    // he answers a look. Where his part pauses: a stick twirled (a chance every 17 s, a little over half of them
    // taken, held two and a half to three and a half seconds) and the neck scratched (every 22 s, the same share,
    // two seconds or so)
    skills = listOf(
        EyeContact,
        StickTwirl(
            SkillTiming(
                every = 17.0,
                chance = 0.55,
                cooldown = 0.0,
                least = 2.5,
                most = 3.5,
                earliest = 0.5,
                reserve = 5.1,
                rise = 0.6,
                fall = 0.7,
            ),
        ),
        NeckScratch(
            SkillTiming(
                every = 22.0,
                chance = 0.55,
                cooldown = 0.0,
                least = 1.8,
                most = 2.6,
                earliest = 0.5,
                reserve = 4.3,
                rise = 0.7,
                fall = 0.8,
            ),
        ),
    ),
)
