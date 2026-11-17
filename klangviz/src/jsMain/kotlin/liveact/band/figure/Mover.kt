/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.figure

import io.peekandpoke.klang.viz.liveact.Show
import io.peekandpoke.klang.viz.liveact.X_AXIS
import io.peekandpoke.klang.viz.liveact.Y_AXIS
import io.peekandpoke.klang.viz.liveact.Z_AXIS
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

// How a player moves with the music apart from playing: the nod and the wandering.

/**
 * A player's character: how they move with the music apart from playing. Each player has one of their own, so no
 * two move alike, and a little chance in everything, so nobody moves like a machine.
 */
data class Traits(
    /** How deep the nod is. */
    val depth: Double = 1.0,
    /** How much each of the bar's four beats counts. */
    val beats: List<Double> = listOf(1.0, 0.5, 0.8, 0.5),
    /** Late (lazy) or early (eager), in beats. */
    val lag: Double = 0.0,
    /** How round the nod is, in beats (more is smoother). */
    val soft: Double = 0.10,
    /** How much a nod's depth and timing vary. */
    val chance: Double = 0.3,
    /** How often a nod is left out, 0 to 1. */
    val skip: Double = 0.0,
    /** How far the head turns, the head tilts and the trunk leans as they wander, in radians. */
    val look: Double = 0.10,
    val tilt: Double = 0.05,
    val lean: Double = 0.02,
    /** How little the sound's energy changes the movement: 0 follows it fully, 1 is always the same. */
    val steady: Double = 0.0,
    /** The head goes all the way only in fits; null: always. */
    val fits: Fits? = null,
    /** Which parts of the body take the beat, each a factor. */
    val parts: Parts = Parts(),
) {
    /** [every]: how long a mood lasts, in beats. [share]: how much of the time is a fit, 0 to 1. [calm]: the head's share between fits. */
    data class Fits(val every: Double, val share: Double, val calm: Double)

    /** [head]: the nod itself. [trunk]: leaning into it. [knees]: giving on the beat. [sway]: the hips going from side to side. */
    data class Parts(val head: Double = 1.0, val trunk: Double = 1.0, val knees: Double = 1.0, val sway: Double = 1.0)
}

/**
 * How one player moves with the music, apart from playing: the nod and a slow wandering of head and trunk, by
 * their [traits], with the [show]'s chance under [seed] (each player's own).
 */
class Mover(private val show: Show, private val seed: Double, val traits: Traits = Traits()) {

    /** The parts' shares just now: the head's changes with the fits ([nod] sets it). */
    var parts: Traits.Parts = traits.parts
        private set

    /** One beat's nod at [beat]: round on both sides, down a little faster than up. */
    private fun one(index: Int, beat: Double): Double {
        val c = traits
        val mine = show.own(index, seed)

        if (show.own(index, seed + 7) < c.skip) {
            return 0.0
        }

        val weight = c.beats[((index % 4) + 4) % 4] * (1 - c.chance * 0.6 + c.chance * 1.2 * mine)
        val phase = beat - index - c.lag - (show.own(index, seed + 3) - 0.5) * 0.12 * c.chance

        return weight * exp(-(phase * phase) / (if (phase < 0) 0.030 else 0.110))
    }

    /**
     * The nod at [beat] (in beats), 0 to about 1, as strong as the sound's energy over the last second and a half:
     * it never follows a single loud moment.
     */
    fun nod(beat: Double): Double {
        val c = traits

        c.fits?.let { fits ->
            // a slow mood, wandering between -1 and 1: above its threshold it is a fit, eased in and out
            val mood = show.drift(beat / fits.every, seed + 21)
            val fit = min(1.0, max(0.0, (mood - (1 - 2 * fits.share)) / 0.3))

            parts = parts.copy(head = traits.parts.head * (fits.calm + (1 - fits.calm) * fit * fit * (3 - 2 * fit)))
        }

        // rounded: the mean over a short stretch round this moment, so nothing in it is abrupt
        var sum = 0.0

        for (k in -2..2) {
            val b = beat + k * c.soft / 2
            val index = floor(b).toInt()

            sum += (3 - abs(k)) * max(one(index - 1, b), max(one(index, b), one(index + 1, b)))
        }

        return c.depth * (sum / 9) * (0.12 + 1.05 * strength())
    }

    /** How strong the movement is right now: the sound's energy, or for a steady player mostly a fixed amount. */
    fun strength(): Double {
        val live = min(1.25, max(0.0, (show.energy.slow - 0.12) / 0.5))

        return live + (0.85 - live) * traits.steady
    }

    /**
     * What a sudden burst of sound adds, for the body only (trunk and knees): the head never jerks with it (the
     * maintainer, 2026-11-15: "the body can move like this but the nods are too much").
     */
    fun burst(): Double = traits.depth * 0.25 * show.energy.burst * strength() * (1 - traits.steady)

    /** The slow wandering at [t] (seconds): the head looks about and tilts, the trunk leans; applied on top of the pose. */
    fun apply(rig: Rig, t: Double) {
        val c = traits
        val alive = 0.5 + 0.5 * min(1.0, show.energy.slow / 0.6)

        rig.turn("head", Y_AXIS, c.look * alive * show.drift(t * 0.23, seed + 11))
        rig.turn("head", Z_AXIS, c.tilt * alive * show.drift(t * 0.31, seed + 12))
        rig.turn("chest", Z_AXIS, c.lean * alive * show.drift(t * 0.13, seed + 13))
        rig.turn("chest", X_AXIS, c.lean * 0.6 * alive * show.drift(t * 0.17, seed + 14))
    }
}
