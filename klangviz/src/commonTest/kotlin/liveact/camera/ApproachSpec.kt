/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.camera

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.band.instruments.trumpet.StrollRoute

/** Someone walking from ([x0], [z0]) by ([vx], [vz]) metres a second for [seconds], a frame every 20 ms; the camera at (0, 10). */
private fun walk(x0: Double, z0: Double, vx: Double, vz: Double, seconds: Double, before: Approach? = null, from: Double = 0.0): Approach {
    var approach = before
    val frames = (seconds * 50).toInt()

    for (i in 0..frames) {
        val t = i / 50.0

        approach = Approaching.next(before = approach, t = from + t, x = x0 + vx * t, z = z0 + vz * t, camX = 0.0, camZ = 10.0)
    }

    return checkNotNull(approach)
}

class ApproachSpec : StringSpec({

    "who walks straight at the camera has come as far as they walked" {
        val approach = walk(x0 = 0.0, z0 = 0.0, vx = 0.0, vz = 0.8, seconds = 2.0)

        approach.come shouldBe (1.6 plusOrMinus 1e-6)
        approach.arrived shouldBe true
    }

    "a short step at the camera is not enough" {
        walk(x0 = 0.0, z0 = 0.0, vx = 0.0, vz = 0.8, seconds = 1.0).arrived shouldBe false
    }

    "who walks across the stage has come nowhere, however long" {
        walk(x0 = -3.0, z0 = 0.0, vx = 0.8, vz = 0.0, seconds = 6.0).come shouldBe 0.0
    }

    "who walks away from the camera has come nowhere" {
        walk(x0 = 0.0, z0 = 3.0, vx = 0.0, vz = -0.8, seconds = 3.0).come shouldBe 0.0
    }

    "who creeps at the camera has come nowhere" {
        walk(x0 = 0.0, z0 = 0.0, vx = 0.0, vz = 0.1, seconds = 20.0).come shouldBe 0.0
    }

    "a camera that comes toward someone standing still is no walk of theirs" {
        var approach: Approach? = null

        // the total moving in again after the ghosts: from 16 m to 10 m at 0.7 m a second
        for (i in 0..428) {
            val t = i / 50.0

            approach = Approaching.next(before = approach, t = t, x = 0.0, z = 0.0, camX = 0.0, camZ = 16.0 - 0.7 * t)
        }

        checkNotNull(approach).come shouldBe 0.0
    }

    "the same moment asked again keeps what they have come" {
        val came = walk(x0 = 0.0, z0 = 0.0, vx = 0.0, vz = 0.8, seconds = 2.0)
        val again = Approaching.next(before = came, t = came.t, x = came.x, z = came.z, camX = 0.0, camZ = 10.0)

        again.come shouldBe came.come
        // and the walk goes on from there
        Approaching.next(before = again, t = came.t + 0.02, x = came.x, z = came.z + 0.016, camX = 0.0, camZ = 10.0).come shouldBe
            (came.come + 0.016 plusOrMinus 1e-9)
    }

    "standing still begins it anew" {
        val came = walk(x0 = 0.0, z0 = 0.0, vx = 0.0, vz = 0.8, seconds = 2.0)
        val stood = walk(x0 = 0.0, z0 = 1.6, vx = 0.0, vz = 0.0, seconds = 0.2, before = came, from = 2.02)

        stood.come shouldBe 0.0
    }

    "a jump in time begins it anew" {
        val came = walk(x0 = 0.0, z0 = 0.0, vx = 0.0, vz = 0.8, seconds = 2.0)

        Approaching.next(before = came, t = 9.0, x = 0.0, z = 2.0, camX = 0.0, camZ = 10.0).come shouldBe 0.0
        Approaching.next(before = came, t = 1.0, x = 0.0, z = 2.0, camX = 0.0, camZ = 10.0).come shouldBe 0.0
    }

    "on a real stroll every walk up to the camera is found, and nearly nothing else" {
        // her place some 0.7 m left of the middle and 7.2 m before the total's camera; one of her sizes 0.98 m
        val size = 0.98
        val camX = 0.71 * size
        val camZ = 7.2

        for (seed in listOf(1, 7, 42)) {
            val route = StrollRoute(seed = seed, restTurn = 0.1, closest = StrollRoute.Offset(x = 0.71, z = 5.94))
            val found = mutableMapOf<Int, Boolean>()
            var approach: Approach? = null
            var was = false

            for (i in 0..(1200 * 50)) {
                val x = i / 50.0
                val at = route.at(x)

                approach = Approaching.next(before = approach, t = x, x = at.x * size, z = at.z * size, camX = camX, camZ = camZ)

                if (approach.arrived && !was) {
                    found[route.roundAt(x)] = true
                }

                was = approach.arrived
            }

            val rounds = 0..route.roundAt(1190.0)
            val toCamera = rounds.filter { route.roundOf(it).kind in listOf(StrollRoute.Kind.Camera, StrollRoute.Kind.Close) }

            toCamera.filter { it !in found }.shouldBe(emptyList())
            // a plain round may hold one straight step at the camera now and then: fine, but it stays the exception
            (found.keys - toCamera.toSet()).size * 4 shouldBeLessThan rounds.count()
        }
    }

    "what is no number begins it anew" {
        val came = walk(x0 = 0.0, z0 = 0.0, vx = 0.0, vz = 0.8, seconds = 2.0)

        Approaching.next(before = came, t = Double.NaN, x = 0.0, z = 2.0, camX = 0.0, camZ = 10.0).come shouldBe 0.0
        Approaching.next(before = came, t = 2.02, x = Double.NaN, z = 2.0, camX = 0.0, camZ = 10.0).come shouldBe 0.0
    }
})
