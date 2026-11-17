/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.skills

import io.peekandpoke.klang.viz.liveact.smooth
import io.peekandpoke.klang.viz.liveact.theatre.CrowdLight
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.sin

/**
 * A slow look over the crowd, at the moments its [timing] gives: the head rests on a spot in the crowd for a while,
 * from [spotLeast] to [spotMost] seconds, then moves on to another over [move] seconds (left, right, the near rows,
 * the far rows; the spots: [crowdSpots]). Which spot, and when the head moves on, the roll decides (see [spotAt]).
 * Only where the spot is in sight: with the back to the crowd, the member does not gaze at it.
 */
data class CrowdGaze(
    override val timing: SkillTiming,
    val spotLeast: Double,
    val spotMost: Double,
    val move: Double,
) : TimedSkill {
    init {
        require(move > 0) { "the head takes time to move on: $move s" }
        require(spotLeast >= move && spotMost >= spotLeast) {
            "a spot is held from $spotLeast s to $spotMost s, and no shorter than the move to it ($move s)"
        }
    }

    override val rolls: Double get() = 3000.0

    /**
     * Where the head is among [spots] spots at [t]: on its way from one spot to the next, or resting on one (its
     * share of the way 1). The show's time is cut into one spot after another, each held [spotLeast] to [spotMost]
     * seconds; the head leaves for the next spot where it begins and is there [move] seconds later. A spot is
     * drawn by the roll, and where it is the one drawn just before, the one half the spots away is taken: so the
     * head nearly always moves on. It goes by the time and the roll alone, so the same moment always gives the same
     * answer, also on its way between two spots. [roll]: the member's own chance (see [usesAt]).
     */
    fun spotAt(t: Double, roll: (Double) -> Double, spots: Int): SpotMove {
        val own = own(roll)
        val mean = (spotLeast + spotMost) / 2
        val spread = spotMost - spotLeast

        // spot k begins at k times the mean, moved by up to a quarter of the spread either way: so from one to the
        // next it is the mean, give or take half the spread
        fun begins(k: Double): Double = k * mean + (own(k + 0.25) - 0.5) * spread / 2

        fun drawn(k: Double): Int = min(spots - 1, floor(own(k + 0.5) * spots).toInt())

        fun spot(k: Double): Int {
            val now = drawn(k)

            return if (now == drawn(k - 1)) (now + spots / 2) % spots else now
        }

        var k = floor(t / mean) + 1

        while (begins(k) > t) {
            k -= 1
        }

        return SpotMove(from = spot(k - 1), to = spot(k), share = smooth((t - begins(k)) / move))
    }
}

/** The head on its way from the spot [from] to the spot [to] (their places among the spots), [share] of the way. */
data class SpotMove(val from: Int, val to: Int, val share: Double)

/**
 * The spots of a gaze over the crowd ([CrowdGaze]): where the crowd really is, a held-up light of [lights] (see
 * `CrowdPlaces`) each. Eighteen of them: in six directions from 60 degrees to the left of straight ahead of the stage
 * to 60 to the right, and in each at three distances from the stage's middle (17, 30 and 48 metres: the first rows,
 * the middle, the far rows), the light nearest to each. In that order: by direction from the audience's left to
 * its right, and in each near to far. None where there are no lights.
 */
fun crowdSpots(lights: List<CrowdLight>): List<CrowdLight> {
    if (lights.isEmpty()) {
        return emptyList()
    }

    val out = mutableListOf<CrowdLight>()

    for (degrees in listOf(-60.0, -36.0, -12.0, 12.0, 36.0, 60.0)) {
        val angle = degrees * PI / 180

        for (off in listOf(17.0, 30.0, 48.0)) {
            val x = off * sin(angle)
            val z = off * cos(angle)

            out.add(lights.minBy { (it.x - x) * (it.x - x) + (it.z - z) * (it.z - z) })
        }
    }

    return out
}
