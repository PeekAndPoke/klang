/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.skills

import io.peekandpoke.klang.viz.liveact.Timed
import io.peekandpoke.klang.viz.liveact.triggers.BeforePhrase
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.round

/**
 * Counting in: in the last bar before a phrase of the member's part begins, the head counts its four beats, a small
 * clear nod on each, the last one beat before the phrase. Not by chance: by the part, before a phrase ([timing], see
 * [at]), whose pause is no less than [LEAST_PAUSE] bars.
 */
data class CountIn(val timing: BeforePhrase = BeforePhrase(pause = 2.0)) : Mannerism {
    init {
        require(timing.pause >= LEAST_PAUSE) {
            "a phrase begins after a pause of at least the bar counted in and the half beat before it: ${timing.pause} bars"
        }
    }

    override val wildness: Int get() = 3

    /**
     * How far the head is down in a nod of the count at [t] (seconds into the show), 0 to 1, near each of the four
     * beats of the last bar before a phrase of [plays] (sorted by when they begin) begins: a bell round each beat, 1
     * on the beat, about a third a fifth of a beat off it, nearly nothing half a beat off; 0 elsewhere. [beat]: a
     * beat, in seconds. The bar before the first note counts only where it begins at or after [arrives] (when the
     * member is at their place).
     */
    fun at(t: Double, plays: List<Timed>, beat: Double, arrives: Double): Double {
        val bar = 4 * beat
        // none between the notes of a phrase, and not where the counted bar would begin before the member is there
        val next = timing.phraseAfter(t = t, plays = plays, bar = bar, lead = bar, arrives = arrives) ?: return 0.0
        val from = next - bar

        if (t < from - beat / 2) {
            return 0.0
        }

        // the nearest of the four beats of the bar, and how far off it
        val k = round((t - from) / beat).coerceIn(0.0, 3.0)
        val off = abs(t - (from + k * beat)) / (beat * NOD)

        return exp(-off * off)
    }

    companion object {
        /**
         * The shortest pause before a phrase, in bars: the bar counted in and the half beat before it, where the
         * first nod begins. A shorter pause would put the last note of the phrase before in them, and the count would
         * jump in.
         */
        const val LEAST_PAUSE = 1.125

        /** How wide a nod is, as a share of a beat: so far either side of its beat it is down to about a third. */
        private const val NOD = 0.2
    }
}
