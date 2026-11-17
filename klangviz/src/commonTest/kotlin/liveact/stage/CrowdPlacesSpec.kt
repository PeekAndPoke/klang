/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sqrt

/** How far a light is from the stage's middle, on the ground. */
private fun CrowdLight.off(): Double = sqrt(x * x + z * z)

class CrowdPlacesSpec : StringSpec({

    "the audience stands in half a ring around the stage, on the side the band looks to" {
        val lights = CrowdPlaces.all

        lights.size shouldBeGreaterThan 5000

        // a place is moved about by at most 0.3 m each way
        lights.minOf { it.off() } shouldBeGreaterThan CrowdPlaces.FIRST - 0.5
        lights.maxOf { it.off() } shouldBeLessThan CrowdPlaces.LAST + 0.5
        lights.minOf { it.z } shouldBeGreaterThan -0.5
    }

    "it reaches from the stage's left side around the front to its right, evenly" {
        // six slices of the half circle, by the angle off the line straight ahead of the stage
        val slices = CrowdPlaces.all.groupingBy { ((atan2(it.x, it.z) / PI + 0.5) * 6).toInt().coerceIn(0, 5) }
            .eachCount()
        val even = CrowdPlaces.all.size / 6.0

        slices.keys shouldBe setOf(0, 1, 2, 3, 4, 5)

        for (count in slices.values) {
            count.toDouble() shouldBeGreaterThan even * 0.9
            count.toDouble() shouldBeLessThan even * 1.1
        }
    }

    "the ground is level near the stage and climbs toward the back, to the sides as straight ahead" {
        val lights = CrowdPlaces.all
        // a light is held 1.95 to 2.30 m above the ground, which lies PIT below the stage
        val near = lights.filter { it.off() < CrowdPlaces.RISE - 1.0 }
        val far = lights.filter { it.off() > CrowdPlaces.LAST - 2.0 }
        val climbed = CrowdPlaces.SLOPE * (CrowdPlaces.LAST - 2.5 - CrowdPlaces.RISE)

        near.maxOf { it.y } shouldBeLessThan 2.30 - CrowdPlaces.PIT + 0.01
        far.minOf { it.y } shouldBeGreaterThan 1.95 - CrowdPlaces.PIT + climbed

        // also those far out to the side, where the old rows never came
        far.filter { it.z < 10.0 }.size shouldBeGreaterThan 20
    }
})
