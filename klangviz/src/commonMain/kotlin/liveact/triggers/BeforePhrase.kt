/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.triggers

import io.peekandpoke.klang.viz.liveact.Timed

/**
 * By the part: before a phrase of a member's part begins (see [phraseAfter]). A phrase begins at a note after a pause
 * of at least [pause] bars ([PartPause], counted from the start of the note before it), and at the part's first note.
 */
data class BeforePhrase(val pause: Double) : Trigger {
    init {
        require(pause > 0) { "a phrase begins after a pause: $pause bars" }
    }

    /**
     * When the phrase begins that [t] (seconds into the show) lies before: the start of the next note of [plays]
     * (sorted by when they begin) after [t], where that note begins a phrase; null where it does not, or no note
     * follows. [bar]: a bar, in seconds. The part's first note begins one only where the [lead] seconds before it
     * (what is done before the phrase) begin at or after [arrives] (when the member is at their place).
     */
    fun phraseAfter(t: Double, plays: List<Timed>, bar: Double, lead: Double, arrives: Double): Double? {
        val around = PartPause.at(plays = plays, t = t)
        val next = around.until ?: return null
        val last = around.from
        val begins = if (last != null) next - last >= pause * bar else next - lead >= arrives

        return if (begins) next else null
    }
}
