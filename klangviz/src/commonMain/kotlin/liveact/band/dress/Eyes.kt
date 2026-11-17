/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.dress

import kotlin.math.exp
import kotlin.math.min

/**
 * How warm a player is from their playing, which colours their eyes: almost white while they rest, the more their
 * own colour the longer they play; the warmth never quite gets to 1, as they cool all the while (the maintainer,
 * 2026-11-17). What the eyes make of a warmth is [EyeLook]: they have all of their colour a little before that.
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
 * A pair of eyes at one moment: how much of their own colour they have ([own], 0 to 1; the rest is the pale,
 * almost white light of a player at rest) and how brightly they glow ([glow]).
 */
data class EyeLook(val own: Double, val glow: Double) {
    companion object {
        /** From this warmth on the eyes have all of their colour: after a lot of playing. */
        const val FULL = 0.9

        /**
         * The eyes' glow, the same whether their player rests or plays: every eye is as bright as its colour can be
         * on a screen, all the time, and only its hue says how warm its player is (dimmer while playing than at
         * rest was the wrong way round: the maintainer, 2026-11-17, "needs to be bright for everyone"). It is set
         * so that the eyes' own waver, which takes away up to a good half, never brings the colour's strongest
         * part under full. A pure red or violet still carries less light than a cyan or a green: that is the
         * screen's, and adding white to make up for it would turn the red pink.
         */
        const val REST_GLOW = 2.25
        const val GLOW = 2.25

        /** How much of what a player sets beyond the least is added: a loud note flares the eyes a little toward white. */
        const val FLARE = 0.04
        const val LEAST = 2.0

        /**
         * The eyes of a player who is [warmth] warm (0 to 1) and whose playing sets their glow to [set] this frame
         * ([LEAST] at rest, up to some 20 on a loud note). That glow is brought down to what a colour can be seen
         * in: a light that bright is white on a screen whatever its colour.
         */
        fun of(warmth: Double, set: Double): EyeLook {
            val x = (warmth / FULL).coerceIn(0.0, 1.0)
            val own = x * x * (3 - 2 * x)
            val playing = GLOW + FLARE * (set - LEAST)

            return EyeLook(own = own, glow = REST_GLOW + (playing - REST_GLOW) * own)
        }
    }
}
