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
import io.peekandpoke.klang.viz.liveact.band.skills.HeadScratch
import io.peekandpoke.klang.viz.liveact.band.skills.Slouch
import io.peekandpoke.klang.viz.liveact.band.skills.Straighten
import io.peekandpoke.klang.viz.liveact.triggers.ChanceInSlots

/**
 * Beben's sheet: tall and thin. Where his part pauses he mostly slouches ([Slouch]), long and calm, and now and
 * then scratches his head ([HeadScratch]): both fit how wild he is (see [Character.wildness]).
 */
val BEBEN: Member = Member(
    player = Player.Beben,
    dress = Dress(cloth = ClothPattern.Pinstripe, glitter = 0.14),
    // the steady one: late, mostly on one and three, always the same whatever the band does; looks about slowly
    character = Character(
        seed = 23.0,
        // how wild: more animal than not
        wildness = 6,
        // how his hips move as he walks: tall and stooped, he walks from the knees, the hips hardly go
        gait = Gait(hips = 0.25),
        traits = Traits(
            steady = 0.9, depth = 0.9, beats = listOf(1.0, 0.2, 0.7, 0.2), lag = 0.07, soft = 0.18, chance = 0.12,
            skip = 0.0, look = 0.22, tilt = 0.06, lean = 0.02,
            // from the knees and hips, the head hardly
            parts = Traits.Parts(head = 0.6, trunk = 0.8, knees = 1.0, sway = 1.3),
        ),
    ),
    bearing = Bearing(lean = 0.10, bang = 0.16, bounce = 0.045),
    // he answers a look. Where his part pauses, the steady one rests: now and then a scratch of the head (a chance
    // every 19 s, two in five taken, held one and a half to two and a half seconds), over a long slouch (a chance
    // every 12 s, seven in ten taken, held five to eight seconds)
    skills = listOf(
        EyeContact,
        HeadScratch(
            ChanceInSlots(
                every = 19.0,
                chance = 0.4,
                cooldown = 0.0,
                least = 1.5,
                most = 2.5,
                earliest = 0.5,
                reserve = 4.2,
                rise = 0.7,
                fall = 0.8,
            ),
        ),
        Slouch(
            ChanceInSlots(
                every = 12.0,
                chance = 0.7,
                cooldown = 0.0,
                least = 5.0,
                most = 8.0,
                earliest = 0.5,
                reserve = 10.1,
                rise = 1.1,
                fall = 1.2,
            ),
        ),
        // now and then he straightens up from his instrument (a chance every 21 s, a little over half of them taken):
        // up over 1.8 s, held four to nine seconds, down over 2.6 s
        Straighten(
            ChanceInSlots(
                every = 21.0,
                chance = 0.55,
                cooldown = 0.0,
                least = 6.6,
                most = 11.6,
                earliest = 0.5,
                reserve = 13.9,
                rise = 1.8,
                fall = 2.6,
            ),
        ),
    ),
)
