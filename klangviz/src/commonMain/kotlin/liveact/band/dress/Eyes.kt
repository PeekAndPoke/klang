/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.dress

import kotlin.math.exp
import kotlin.math.min

/**
 * How warm a player is from their playing, which colours their eyes: their own colour while they rest, the whiter
 * the longer they play; the warmth never quite gets to 1, as they cool all the while (the maintainer, 2026-11-17).
 * What the eyes make of a warmth is [EyeLook]: they are as white as they get a little before that.
 *
 * A player is playing from each note they begin for [hold] seconds on (or till the next one). The warmth is how
 * much of the last while they played, the nearer past counting more: what was [cools] seconds ago counts a third of
 * what is now. So it rises as long as they play (to two thirds after [cools] seconds, to nine tenths after more than
 * twice that) and falls as soon as they stop; how many notes a second they play does not matter. It goes by the
 * notes and the time alone, so any moment shows the same eyes whenever it is shown.
 */
class EyeWarmth(val cools: Double = 7.0, val hold: Double = 2.0) {

    init {
        require(cools > 0) { "The eyes cool in some time: $cools" }
        require(hold > 0) { "A note is played for some time: $hold" }
    }

    /** The warmth at [t] (seconds), 0 to 1, of who begins a note at each of [notes] (sorted). */
    fun at(notes: List<Double>, t: Double): Double {
        // the last note begun by now
        var lo = 0
        var hi = notes.size

        while (lo < hi) {
            val mid = (lo + hi) shr 1

            if (notes[mid] <= t) {
                lo = mid + 1
            } else {
                hi = mid
            }
        }

        var warmth = 0.0
        // back from now: what is counted so far begins here
        var counted = t
        var i = lo - 1

        while (i >= 0 && t - notes[i] < LONG * cools) {
            val from = notes[i]
            val to = min(from + hold, counted)

            if (to > from) {
                warmth += exp(-(t - to) / cools) - exp(-(t - from) / cools)
            }

            counted = from
            i--
        }

        return warmth.coerceIn(0.0, 1.0)
    }

    private companion object {
        /** What lies further back than this many cooling times counts for nothing any more. */
        const val LONG = 6.0
    }
}

/**
 * A pair of eyes at one moment: how much of their own colour they have ([own], 0 to 1; the rest is a pale, almost
 * white light) and how brightly they glow ([glow]).
 */
data class EyeLook(val own: Double, val glow: Double) {
    companion object {
        /** From this warmth on the eyes are as white as they get: after a lot of playing. */
        const val FULL = 0.9

        /**
         * How much of their own colour the eyes have while their player rests, and how much is left once they are
         * hot: a subtle tint at rest (the maintainer, 2026-11-17: "the eye colors need to all be subtler ...
         * meaning brighter all of them"), almost white in action ("idle color ... in action almost white").
         */
        const val TINT = 0.85
        const val HOT = 0.08

        /**
         * The eyes' glow at rest and in action. At rest it is what shows a colour best: the colour's strongest part
         * about full, not far over it, since a light driven harder goes white on a screen whatever its colour. In
         * action it is higher: the eyes are white then anyway, and bright.
         */
        const val REST_GLOW = 1.5
        const val GLOW = 2.25

        /** How much of what a player sets beyond the least is added: a loud note flares the eyes a little. */
        const val FLARE = 0.04
        const val LEAST = 2.0

        /**
         * The eyes of a player who is [warmth] warm (0 to 1) and whose playing sets their glow to [set] this frame
         * ([LEAST] at rest, up to some 20 on a loud note): their own colour, subtly, while they rest, and whiter the
         * warmer they are, as what glows goes white when it is hot. That glow is brought down to what a colour can
         * be seen in: a light that bright is white on a screen whatever its colour.
         */
        fun of(warmth: Double, set: Double): EyeLook {
            val x = (warmth / FULL).coerceIn(0.0, 1.0)
            val heat = x * x * (3 - 2 * x)
            val playing = GLOW + FLARE * (set - LEAST)

            return EyeLook(own = TINT + (HOT - TINT) * heat, glow = REST_GLOW + (playing - REST_GLOW) * heat)
        }
    }
}
