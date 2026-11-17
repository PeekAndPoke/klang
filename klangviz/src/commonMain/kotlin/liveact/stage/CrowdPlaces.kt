/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.peekandpoke.klang.viz.liveact.hash01
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

// Where the audience's lights are: as in a giant open-air theatre, in half a circle around the stage, from its left
// side around the front to its right. They stand in loose rows, each row an arc, below the stage's edge and a few
// metres off it, the ground rising toward the back. How they move is the crowd's shader (Crowd).

/**
 * Where they stand: in half a ring around the stage's middle, on the side the band looks to (z from 0 up), from
 * [FIRST] m off it to [LAST]; from [RISE] m off on the ground climbs ([SLOPE] m a metre). The stage stands [PIT] m
 * above the ground they stand on, so a held-up light is about level with the stage's floor.
 *
 * The first row is as near as the cameras allow: the widest way a camera goes round the band is some 9.7 m from the
 * stage's middle, and the total stands 9.5 m before it. (16 m at first left a wide empty ring before the stage: the
 * maintainer, 2026-11-17, "the crowd could be closer".)
 */
object CrowdPlaces {
    const val FIRST = 11.5
    const val LAST = 62.0
    const val RISE = 26.0
    const val SLOPE = 0.25
    const val PIT = 1.4

    /**
     * Every held-up light: loose rows, a place about every [APART] m along a row and the rows [ROWS] m apart, moved
     * about, and not everyone holds a light up.
     * Always the same ones, whatever the seed.
     */
    val all: List<CrowdLight> by lazy { build() }

    private fun build(): List<CrowdLight> {
        val lights = mutableListOf<CrowdLight>()

        /** The light at about [x], [z], if someone holds one up there. */
        fun place(x: Double, z: Double) {
            val n = lights.size + z * 131.0 + x * 17.0

            if (hash01(n * 1.7 + 0.3) > 0.88) {
                return
            }

            val at = z + 0.6 * (hash01(n * 2.3) - 0.5)
            // the group: friends stand together, some four metres across; the groups' edges are not straight
            val gx = floor((x + 1.5 * sin(at * 0.9)) / 4.2)
            val gz = floor((at + 1.2 * sin(x * 0.8)) / 3.4)

            lights.add(
                CrowdLight(
                    x = x + 0.6 * (hash01(n * 3.1) - 0.5),
                    y = 1.95 + 0.35 * hash01(n * 4.7) - PIT + SLOPE * max(0.0, sqrt(x * x + at * at) - RISE),
                    z = at,
                    seed = hash01(n * 5.9),
                    group = hash01(gx * 12.9898 + gz * 78.233 + 0.5),
                )
            )
        }

        var off = FIRST

        while (off < LAST) {
            // the row: half a circle, [off] m from the stage's middle
            val places = floor(PI * off / APART).toInt()

            for (i in 0 until places) {
                val angle = PI * ((i + 0.5) / places - 0.5)

                place(x = off * sin(angle), z = off * cos(angle))
            }

            off += ROWS
        }

        return lights
    }

    // (half as many again as at first, when a place was 0.7 m wide and the rows 0.85 m apart; the maintainer,
    // 2026-11-17: "say 50% more people in the crowd")

    /** A place about every so many metres along a row. */
    private const val APART = 0.57

    /** The rows are this far apart, in metres. */
    private const val ROWS = 0.69
}

/**
 * One held-up light: where it is ([x], [y], [z], in metres), its own number ([seed], 0 to 1) and its group's
 * ([group], 0 to 1): a group moves as one.
 */
data class CrowdLight(val x: Double, val y: Double, val z: Double, val seed: Double, val group: Double)
