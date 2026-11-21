/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.skills

import io.peekandpoke.klang.viz.liveact.triggers.ChanceInSlots

/**
 * Straightening up: a member who stands bent over an instrument straightens up now and then. The back comes up, the
 * chest opens, the head lifts and looks out over the audience; a while later the member sinks back over the
 * instrument (the maintainer, 2026-11-17: "Currently they are always hunched over"). Slowly up over the [timing]'s
 * rise, held, and more slowly down over its fall. Only a string player's pose shows it so far.
 */
data class Straighten(override val timing: ChanceInSlots) : ChanceSkill {
    override val rolls: Double get() = 15000.0

    /**
     * How straight the member stands at [t] (seconds into the show), from 0 (as always) to 1 (fully straight), as the
     * [timing] gives it (see [ChanceInSlots.inUse]). [roll]: the member's own chance; the skill takes its rolls from
     * its own place in it ([rolls]). Before the song the member stands as always: a use ends within its slot, so none
     * is cut off where the song begins.
     */
    fun at(t: Double, roll: (Double) -> Double): Double {
        if (t < 0) {
            return 0.0
        }

        return timing.inUse(t = t, roll = own(roll))
    }
}
