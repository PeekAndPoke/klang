/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.peekandpoke.klang.viz.liveact.hash01
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.sin

// Where the audience's lights are: they stand before the stage in loose rows, below the stage's edge and a few
// metres off it, the ground rising toward the back as in a stadium. How they move is the crowd's shader (Crowd).

/**
 * Where they stand: from [FIRST] m before the stage's middle to [LAST], [WIDE] to each side; from [RISE] on the ground
 * climbs ([SLOPE] m a metre). The stage stands [PIT] m above the ground they stand on, so a held-up light is about
 * level with the stage's floor.
 */
object CrowdPlaces {
    const val FIRST = 16.0
    const val LAST = 62.0
    const val WIDE = 28.0
    const val RISE = 26.0
    const val SLOPE = 0.25
    const val PIT = 1.4

    /**
     * Every held-up light: loose rows, a place about every metre, moved about, and not everyone holds a light up.
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
                    y = 1.95 + 0.35 * hash01(n * 4.7) - PIT + SLOPE * max(0.0, at - RISE),
                    z = at,
                    seed = hash01(n * 5.9),
                    group = hash01(gx * 12.9898 + gz * 78.233 + 0.5),
                )
            )
        }

        var z = FIRST

        while (z < LAST) {
            var x = -WIDE

            while (x < WIDE) {
                place(x, z)
                x += 0.7
            }

            z += 0.85
        }

        return lights
    }
}

/**
 * One held-up light: where it is ([x], [y], [z], in metres), its own number ([seed], 0 to 1) and its group's
 * ([group], 0 to 1): a group moves as one.
 */
data class CrowdLight(val x: Double, val y: Double, val z: Double, val seed: Double, val group: Double)
