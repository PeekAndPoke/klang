/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.camera

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.shouldBe
import kotlin.math.PI
import kotlin.math.hypot

private val BACK = Ground(x = 0.0, z = 1.0)

class ClearSpec : StringSpec({

    "a camera that is clear of everyone stays where it is" {
        val camera = Ground(x = 2.0, z = 3.0)

        clearOf(camera = camera, others = listOf(Ground(0.0, 0.0), Ground(3.0, 3.0)), clear = 1.0, back = BACK) shouldBe camera
        clearOf(camera = camera, others = emptyList(), clear = 1.0, back = BACK) shouldBe camera
        // exactly at the clearing: not moved
        clearOf(camera = Ground(1.0, 0.0), others = listOf(Ground(0.0, 0.0)), clear = 1.0, back = BACK) shouldBe Ground(1.0, 0.0)
    }

    "one who is too near pushes it straight away from themselves, out to the clearing" {
        val moved = clearOf(camera = Ground(x = 3.3, z = 2.1), others = listOf(Ground(3.0, 1.7)), clear = 1.0, back = BACK)

        // 0.5 m off along (0.6, 0.8): out to 1 m along the same line
        moved.x shouldBe (3.6 plusOrMinus 1e-9)
        moved.z shouldBe (2.5 plusOrMinus 1e-9)
    }

    "it gives way smoothly to someone beside it: no jump as they pass half a metre off" {
        // (asked with places that stand still in the show; this says the push itself has no step in it)
        val places = (0..200).map { step ->
            clearOf(camera = Ground(0.0, 0.0), others = listOf(Ground(x = -2.0 + step * 0.02, z = 0.5)), clear = 1.0, back = BACK)
        }

        places.zipWithNext().all { (a, b) -> hypot(b.x - a.x, b.z - a.z) < 0.05 } shouldBe true
        places.first() shouldBe Ground(0.0, 0.0)
        places.last() shouldBe Ground(0.0, 0.0)
        // right beside it: pushed straight off, half a metre further
        places[100].x shouldBe (0.0 plusOrMinus 1e-9)
        places[100].z shouldBe (-0.5 plusOrMinus 1e-9)
    }

    "one who stands right on the camera's place pushes it back, away from what it looks at" {
        clearOf(camera = Ground(1.0, 1.0), others = listOf(Ground(1.0, 1.0)), clear = 0.8, back = Ground(x = 3.0, z = 4.0)).let {
            it.x shouldBe (1.48 plusOrMinus 1e-9)
            it.z shouldBe (1.64 plusOrMinus 1e-9)
        }
    }

    "a look is cleared in its player's own space: set there, pushed on the stage, given back there" {
        // the player stands at (3, 1), turned a quarter round: ahead of them is the stage's -x, their left is the
        // stage's +z
        val home = Standing(at = Ground(3.0, 1.0), turn = -PI / 2)
        // a camera 2 m ahead of them: on the stage at (1, 1). Someone stands at (1.0, 0.4): 0.6 m off, toward the
        // player's right
        val moved = clearLook(camera = Ground(0.0, 2.0), target = Ground(0.0, 0.0), home = home, others = listOf(Ground(1.0, 0.4)), clear = 1.0)

        // pushed straight away from them, to 1 m: on the stage (1.0, 1.4), which is 2 m ahead of the player and 0.4 m
        // to their left
        moved.x shouldBe (0.4 plusOrMinus 1e-9)
        moved.z shouldBe (2.0 plusOrMinus 1e-9)

        // nobody near: the look stays as it is set, to the last digit, however the player stands turned
        val turned = Standing(at = Ground(x = 3.0, z = 1.0), turn = 0.3)

        clearLook(camera = Ground(x = 0.7, z = 2.4), target = Ground(0.0, 0.0), home = turned, others = listOf(Ground(9.0, 9.0)), clear = 1.0) shouldBe
            Ground(x = 0.7, z = 2.4)
    }

    "pushed away from one into another, it ends clear of both" {
        val others = listOf(Ground(0.0, 0.0), Ground(1.5, 0.0))
        val moved = clearOf(camera = Ground(0.6, 0.1), others = others, clear = 1.0, back = BACK)

        others.forEach { hypot(moved.x - it.x, moved.z - it.z) shouldBeGreaterThanOrEqual 1.0 - 0.02 }
    }
})
