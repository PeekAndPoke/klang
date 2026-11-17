/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.peekandpoke.klang.viz.liveact.hash01
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.sqrt

// The night sky over the open-air theatre: where the stars are, where the moon stands, and when a shooting star
// falls. Plain numbers; how it is drawn is NightSky.

/** A way to look, as a line one long from the eye: [x] to the stage's left as the band sees it, [y] up, [z] toward the audience. */
data class Heading(val x: Double, val y: Double, val z: Double) {
    /** How high above the horizon it points, in radians. */
    val height: Double get() = asin(y.coerceIn(-1.0, 1.0))

    companion object {
        /** The heading [round] radians round from the audience's side (to the left for more) and [high] radians up. */
        fun of(round: Double, high: Double): Heading = Heading(x = cos(high) * sin(round), y = sin(high), z = cos(high) * cos(round))
    }
}

/** One star: where it stands ([at]) and its own number ([seed], 0 to 1: its size, its colour, its twinkle). */
data class Star(val at: Heading, val seed: Double)

/**
 * A shooting star at one moment: it falls from [from] to [to], and is [share] of the way (0 to 1): its head is
 * there, its tail behind it.
 */
data class ShootingStar(val from: Heading, val to: Heading, val share: Double)

/** The night sky: always the same stars and the same moon; a shooting star now and then, the same ones at the same times. */
object Sky {
    /** How many stars. */
    const val STARS = 900

    /** No star stands lower than this over the horizon (radians): down there the audience's lights are. */
    const val LOWEST = 0.05

    /**
     * Where the moon stands: low over the back rows of the audience, a little to one side, so the look back from
     * behind the band has it over the crowd.
     */
    val MOON: Heading = Heading.of(round = -0.30, high = 0.168)

    /** A shooting star may fall once in this many seconds, and falls for [FALLS] seconds. */
    const val EVERY = 40.0
    const val FALLS = 0.9

    /** How many of those chances are taken. */
    const val CHANCE = 0.45

    /** Every star, evenly over the sky above [LOWEST]. */
    val stars: List<Star> by lazy {
        List(STARS) { i ->
            // evenly over the dome: every height's ring has stars by its length
            val up = sin(LOWEST) + (1 - sin(LOWEST)) * hash01(i * 1.37 + 0.11)
            val round = 2 * PI * hash01(i * 2.71 + 0.53)
            val flat = sqrt(1 - up * up)

            Star(at = Heading(x = flat * sin(round), y = up, z = flat * cos(round)), seed = hash01(i * 4.19 + 0.77))
        }
    }

    /** The shooting star that falls at [t] (seconds); null while none does, which is nearly always. */
    fun shootingStar(t: Double): ShootingStar? {
        val slot = floor(t / EVERY)

        fun roll(n: Int): Double = hash01(slot * 7.13 + n * 1.91 + 0.37)

        if (roll(0) > CHANCE) {
            return null
        }

        val since = t - (slot * EVERY + roll(1) * (EVERY - FALLS))

        if (since < 0 || since > FALLS) {
            return null
        }

        // it starts well up in the sky, anywhere round, and falls a good hand's width at arm's length, downward and
        // to one side
        val round = 2 * PI * roll(2)
        val high = 0.45 + 0.55 * roll(3)
        val aside = (if (roll(4) < 0.5) -1 else 1) * (0.10 + 0.22 * roll(5))

        return ShootingStar(
            from = Heading.of(round = round, high = high),
            to = Heading.of(round = round + aside, high = high - 0.16 - 0.10 * roll(6)),
            share = since / FALLS,
        )
    }
}
