/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.skills

import io.peekandpoke.klang.viz.liveact.Timed
import io.peekandpoke.klang.viz.liveact.smooth
import io.peekandpoke.klang.viz.liveact.triggers.PartPause

/**
 * The mallets crossed below the head for a moment, like a skull and bones: as the member gets ready to play a phrase
 * of their part, and again when it is done (the maintainer, 2026-11-18: "before she starts playing and when she is
 * done she needs to briefly cross her arms before her chest", and then: "Let her only cross the mallets below her
 * head, so that it looks like a skull and bones"). Not by chance: by the part, at every pause ([PartPause]) long
 * enough that the arms hang in it anyway (see [at]). Held all the way for [hold] seconds; [move] seconds from hanging into the pose,
 * and out of it again. How the pose looks is the player's own.
 */
data class CrossedMallets(val hold: Double = 1.5, val move: Double = 1.2) : Skill {
    init {
        require(hold >= 0 && move > 0) { "the mallets stay crossed for a while ($hold s) and take time to cross ($move s)" }
    }

    /**
     * How far the mallets are crossed at [t] (seconds into the show), 0 to 1, for a part of the notes [plays] (sorted by
     * when they begin). [rises]: how long before a note the arms begin to rise to play it, from where they hang (from
     * crossed, where they are crossed); [falls]: how long after a note they are all down from playing (crossed,
     * where they cross). How they go between playing and the rest is the pose's own.
     *
     * At a pause between two notes: crossed from the first on, so the arms come down from playing into the pose, and
     * held [hold] seconds once they are down; then to hanging over [move]. Before the second: from hanging into the
     * pose over [move], held [hold] seconds, and from there they rise to play it. Only where the arms then hang for
     * at least [move] seconds between the two: at a shorter pause, and between the notes of a phrase, never. The
     * stretch before the first note counts from [arrives] (when the member is at their place): the mallets cross there
     * only where they begin to after it. After the last note they cross once more. None where there are no notes.
     */
    fun at(t: Double, plays: List<Timed>, rises: Double, falls: Double, arrives: Double): Double {
        val pause = PartPause.at(plays = plays, t = t)
        val before = pause.from
        val after = pause.until

        if (before == null && after == null) {
            return 0.0
        }

        // where the arms hang between the two: from the end of the crossing after the one before (or from the
        // arrival) to the start of the crossing before the one after (or for good)
        val hangFrom = if (before != null) before + falls + hold + move else arrives
        val hangUntil = if (after != null) after - rises - hold - move else Double.POSITIVE_INFINITY
        val least = if (before != null && after != null) move else 0.0

        if (hangUntil - hangFrom < least) {
            return 0.0
        }

        val out = if (before != null) 1 - smooth((t - (before + falls + hold)) / move) else 0.0
        val into = if (after != null) smooth((t - hangUntil) / move) else 0.0

        return maxOf(out, into)
    }
}
