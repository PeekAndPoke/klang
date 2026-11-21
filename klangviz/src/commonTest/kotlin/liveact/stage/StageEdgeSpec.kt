/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.stage

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.theatre.CrowdPlaces
import kotlin.math.sqrt

class StageEdgeSpec : StringSpec({

    "the front arc runs 2.5 m before the audience's first row, straight ahead and to the sides" {
        StageEdge.HALF_Z shouldBe CrowdPlaces.FRONT - 2.5
        StageEdge.HALF_X shouldBe CrowdPlaces.OPEN_SIDE - 2.5
        StageEdge.distance(x = 0.0, z = StageEdge.HALF_Z) shouldBe (0.0 plusOrMinus 1e-9)
        StageEdge.distance(x = StageEdge.HALF_X, z = 0.0) shouldBe (0.0 plusOrMinus 1e-9)
    }

    "inside above 0, outside below, in metres: exact to the back and along the arc's axes" {
        StageEdge.distance(x = 0.0, z = StageEdge.BACK + 1.0) shouldBe (1.0 plusOrMinus 1e-9)
        StageEdge.distance(x = 0.0, z = StageEdge.BACK - 0.5) shouldBe (-0.5 plusOrMinus 1e-9)
        StageEdge.distance(x = 0.0, z = StageEdge.HALF_Z - 0.3) shouldBe (0.3 plusOrMinus 0.01)
        StageEdge.distance(x = StageEdge.HALF_X + 0.4, z = 0.0) shouldBe (-0.4 plusOrMinus 0.02)
        StageEdge.inside(x = 0.0, z = 0.0) shouldBe true
        StageEdge.inside(x = 0.0, z = StageEdge.HALF_Z + 0.1) shouldBe false
    }

    "at the very middle, without a division by nought: the back is the nearest edge there, 8 m off" {
        StageEdge.distance(x = 0.0, z = 0.0) shouldBe (-StageEdge.BACK plusOrMinus 1e-9)
        StageEdge.distance(x = 0.0, z = 6.0).isFinite() shouldBe true
    }

    "on a slant the estimate is close near the edge: a point on the arc is about 0, one 0.3 m inward about 0.3" {
        val angle = 0.7
        val x = StageEdge.HALF_X * kotlin.math.sin(angle)
        val z = StageEdge.HALF_Z * kotlin.math.cos(angle)
        // the oval's inward normal there
        val nx = x / (StageEdge.HALF_X * StageEdge.HALF_X)
        val nz = z / (StageEdge.HALF_Z * StageEdge.HALF_Z)
        val n = sqrt(nx * nx + nz * nz)

        StageEdge.distance(x = x, z = z) shouldBe (0.0 plusOrMinus 1e-9)
        StageEdge.distance(x = x - 0.3 * nx / n, z = z - 0.3 * nz / n) shouldBe (0.3 plusOrMinus 0.02)
    }

    "the band stands on the stage, the audience off it" {
        // Der Schmetterling's stage plot's places, the drummer and the outermost guitarists (DER_SCHMETTERLING)
        listOf(0.1 to -2.1, -3.0 to 1.7, 3.0 to 1.7, -0.7 to 2.2).forEach { (x, z) ->
            StageEdge.distance(x = x, z = z) shouldBeGreaterThan 3.0
        }
        CrowdPlaces.all.forEach { light -> StageEdge.distance(x = light.x, z = light.z) shouldBeLessThan -1.0 }
    }
})
