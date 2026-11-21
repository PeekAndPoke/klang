/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.members

import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.band.dress.ClothPattern
import io.peekandpoke.klang.viz.liveact.band.figure.Gait
import io.peekandpoke.klang.viz.liveact.band.figure.Traits
import io.peekandpoke.klang.viz.liveact.band.skills.BrowWipe
import io.peekandpoke.klang.viz.liveact.band.skills.CameraGlance
import io.peekandpoke.klang.viz.liveact.band.skills.CapTug
import io.peekandpoke.klang.viz.liveact.band.skills.EyeContact
import io.peekandpoke.klang.viz.liveact.band.skills.Straighten
import io.peekandpoke.klang.viz.liveact.triggers.ChanceInSlots

/**
 * Klaue's sheet: the heavier one with the cap, less fine than [Player.Skalpell] and with the power. The one who
 * glances at the camera ([CameraGlance]). Where his part pauses he tugs the peak of his cap ([CapTug]) and wipes his
 * brow with his forearm ([BrowWipe]): animal, as he is (see [Character.wildness]).
 */
val KLAUE: Member = Member(
    player = Player.Klaue,
    dress = Dress(cloth = ClothPattern.Claws, glitter = 0.14),
    // the headbanger, but only in fits: about a quarter of the time he goes all the way, else he nods like the
    // others
    character = Character(
        seed = 41.0,
        // how wild: animal
        wildness = 8,
        // how his hips move as he walks: his swagger is in the shoulders, chest out, not in the hips
        gait = Gait(hips = 0.3),
        traits = Traits(
            fits = Traits.Fits(every = 12.0, share = 0.22, calm = 0.30),
            depth = 0.85, beats = listOf(1.0, 0.85, 1.0, 0.85), lag = -0.03, soft = 0.11, chance = 0.25,
            look = 0.08, tilt = 0.05, lean = 0.02,
            // all head
            parts = Traits.Parts(head = 1.15, trunk = 0.9, knees = 0.8, sway = 0.6),
        ),
    ),
    // he dances from one leg to the other (the maintainer, 2026-11-17)
    bearing = Bearing(lean = 0.10, bang = 0.24, bounce = 0.028, step = 0.6),
    // he answers a look, and looks at the camera every now and then: a chance every eleven seconds, half of them
    // taken, each look held for two to four seconds. Where his part pauses: a tug at the cap (a chance every 10 s, a
    // little over half of them taken, held a second or two) and a wipe of the brow (every 17 s, the same share)
    skills = listOf(
        EyeContact,
        CameraGlance(
            ChanceInSlots(
                every = 11.0,
                chance = 0.5,
                cooldown = 0.0,
                least = 2.2,
                most = 4.2,
                earliest = 0.8,
                reserve = 6.0,
                rise = 0.7,
                fall = 0.9,
            ),
        ),
        CapTug(
            ChanceInSlots(
                every = 10.0,
                chance = 0.55,
                cooldown = 0.0,
                least = 1.2,
                most = 1.8,
                earliest = 0.5,
                reserve = 3.4,
                rise = 0.6,
                fall = 0.7,
            ),
        ),
        BrowWipe(
            ChanceInSlots(
                every = 17.0,
                chance = 0.55,
                cooldown = 0.0,
                least = 1.2,
                most = 1.6,
                earliest = 0.5,
                reserve = 3.2,
                rise = 0.6,
                fall = 0.7,
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
