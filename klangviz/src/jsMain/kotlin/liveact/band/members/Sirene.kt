/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.members

import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.band.dress.ClothPattern
import io.peekandpoke.klang.viz.liveact.band.figure.Gait
import io.peekandpoke.klang.viz.liveact.band.figure.Traits
import io.peekandpoke.klang.viz.liveact.band.skills.BandGlance
import io.peekandpoke.klang.viz.liveact.band.skills.CameraStare
import io.peekandpoke.klang.viz.liveact.band.skills.CrowdGaze
import io.peekandpoke.klang.viz.liveact.band.skills.EyeContact
import io.peekandpoke.klang.viz.liveact.band.skills.GhostEyes
import io.peekandpoke.klang.viz.liveact.band.skills.InwardStare
import io.peekandpoke.klang.viz.liveact.triggers.ChanceInSlots

/**
 * Sirene's sheet: the frontwoman. Between her phrases she looks about (the maintainer, 2026-11-17: "look around in
 * the crowd when she is facing the crowd, and then look 'inward' or stare at the camera"): now and then a long stare
 * at the camera ([CameraStare]), a short look at one of the band ([BandGlance]), a look at nothing ([InwardStare];
 * "inward" also meant "not looking anywhere", 2026-11-18), and most often her gaze wandering over the crowd
 * ([CrowdGaze]). Never with the trumpet at her lips.
 */
val SIRENE: Member = Member(
    player = Player.Sirene,
    dress = Dress(cloth = ClothPattern.Braid, glitter = 0.21),
    // the frontwoman: the balanced one. Every part takes the beat alike, on time, even, with little chance in it
    character = Character(
        seed = 67.0,
        // how wild: elegant, with a little wildness
        wildness = 2,
        // how her hips move as she walks: the measure of the band's, the lure (the others' are against hers)
        gait = Gait(hips = 1.0),
        traits = Traits(
            depth = 0.9, beats = listOf(1.0, 0.5, 0.8, 0.5), lag = 0.0, soft = 0.13, chance = 0.2, skip = 0.0,
            look = 0.14, tilt = 0.04, lean = 0.02,
            parts = Traits.Parts(head = 1.0, trunk = 1.0, knees = 1.0, sway = 1.0),
        ),
    ),
    bearing = null,
    // in the order of rank: she answers a look; she stares at the camera seldom and long (a chance every 23 s, a third
    // of them taken, held four to six seconds, and then not again for 30 s); she glances at one of the band (a chance
    // every 14 s, half of them taken, there a second or two); she looks at nothing (a chance every 19 s, half of them
    // taken, held three to five seconds); and most of the time between them her gaze wanders over
    // the crowd (a chance every 9 s, three in four taken, held four to six seconds, resting on a spot 1.5 to 2.5 s).
    // Her timed looks may come suddenly, but go back slowly, over 1.6 to 1.8 s, never faster than they came (the
    // maintainer, 2026-11-19: "One sudden movement is fine, but the way back then needs to be slower, as if she was
    // 'startled' for a moment and then comes back to normal"); the glance's hold grew by its longer fall, so it
    // stays there about as long as before. (The eye contact is no timed look: it falls as the whole band's does.)
    skills = listOf(
        GhostEyes,
        EyeContact,
        CameraStare(
            ChanceInSlots(
                every = 23.0,
                chance = 0.34,
                cooldown = 30.0,
                least = 4.0,
                most = 6.0,
                earliest = 0.5,
                reserve = 7.5,
                rise = 0.9,
                fall = 1.8,
            ),
        ),
        BandGlance(
            ChanceInSlots(
                every = 14.0,
                chance = 0.5,
                cooldown = 0.0,
                least = 2.2,
                most = 3.2,
                earliest = 0.5,
                reserve = 4.2,
                rise = 0.5,
                fall = 1.6,
            ),
        ),
        InwardStare(
            ChanceInSlots(
                every = 19.0,
                chance = 0.5,
                cooldown = 0.0,
                least = 3.0,
                most = 5.0,
                earliest = 0.5,
                reserve = 6.5,
                rise = 1.0,
                fall = 1.8,
            ),
        ),
        CrowdGaze(
            timing = ChanceInSlots(
                every = 9.0,
                chance = 0.75,
                cooldown = 0.0,
                least = 4.0,
                most = 6.0,
                earliest = 0.5,
                reserve = 7.5,
                rise = 0.9,
                fall = 1.8,
            ),
            spotLeast = 1.5,
            spotMost = 2.5,
            move = 0.8,
        ),
    ),
    lap = Lap.Between,
)
