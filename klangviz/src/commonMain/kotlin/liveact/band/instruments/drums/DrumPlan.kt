/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.instruments.drums

import io.peekandpoke.klang.viz.liveact.song.DrumHit
import io.peekandpoke.klang.viz.liveact.song.Limb

// The drummer's hits by limb: which limb hits what is the song's (Song.drumHits); here each limb gets its list, and
// keeps its last hit where the song forgets, so its stick stays where that hit left it. Plain, so it has a spec
// (KLANG-00451). Where each hit lands on the kit, the drummer works out.

/**
 * The drummer's plan: each limb's hits in order ([byLimb], every limb has a list), each list beginning with the
 * limb's last hit forgotten where the song has forgotten its past.
 */
data class DrumPlan(val byLimb: Map<Limb, List<DrumHit>>) {
    /**
     * What each limb carries on to the next plan, where the song now begins at [from] (the time of the first hit it
     * still knows; infinity where it knows none): its last hit before that, of this plan's list (which begins with
     * what it carried before). A limb that hit nothing yet carries nothing.
     */
    fun carryOn(from: Double): Map<Limb, DrumHit> =
        byLimb.mapNotNull { (limb, list) -> list.lastOrNull { it.t < from }?.let { limb to it } }.toMap()
}

/**
 * Each limb's list of [hits] (sorted by time, as far as the song knows them; each with its limb, as `Song.drumHits`
 * decides), going on from [before]: each limb's list begins with its hit in [before], where it has one, as the stick
 * stays where that hit left it, as it would with the whole song.
 */
fun planDrums(hits: List<DrumHit>, before: Map<Limb, DrumHit>): DrumPlan {
    val byLimb = Limb.entries.associateWith { limb -> listOfNotNull(before[limb]).toMutableList() }

    for (h in hits) {
        byLimb.getValue(h.limb).add(h)
    }

    return DrumPlan(byLimb = byLimb)
}
