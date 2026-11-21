/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.triggers

import io.peekandpoke.klang.viz.liveact.Timed
import io.peekandpoke.klang.viz.liveact.around

/**
 * A pause in a member's part, from the start of one of its notes (or hits, or strums) to the start of the next, in
 * seconds into the show: [from] is null before the part's first note, [until] null after its last. What everything
 * that goes by the part goes by (see [Trigger]); how long a pause must be is each one's own.
 */
data class PartPause(val from: Double?, val until: Double?) {

    /** Whether the pause lasts until [t] at least: no note begins before it (one just at it is allowed). */
    fun lastsUntil(t: Double): Boolean = until == null || until >= t

    companion object {
        /**
         * The pause of the part's [plays] (sorted by when they begin) that [t] lies in: a note that begins at [t] has
         * begun.
         */
        fun at(plays: List<Timed>, t: Double): PartPause {
            val (last, next) = plays.around(t)

            return PartPause(from = last?.t, until = next?.t)
        }
    }
}
