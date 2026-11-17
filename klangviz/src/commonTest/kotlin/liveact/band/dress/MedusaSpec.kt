/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.dress

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.math.sqrt

private fun far(a: HairPoint, b: HairPoint): Double = sqrt((a.x - b.x) * (a.x - b.x) + (a.y - b.y) * (a.y - b.y) + (a.z - b.z) * (a.z - b.z))

private val HEAD = HairPoint(0.0, 0.0, 0.0)

class MedusaSpec : StringSpec({

    val hair = MedusaHair()

    "every snake is rooted inside the upper half of the head, so no gap shows, and its root never moves" {
        for (snake in 0 until hair.snakes) {
            val root = hair.at(snake = snake, joint = 0, t = 0.0)

            far(root, HEAD) shouldBe (0.5 plusOrMinus 1e-9)
            root.y shouldBeGreaterThan 0.0

            for (t in listOf(0.7, 3.0, 125.0)) {
                far(hair.at(snake = snake, joint = 0, t = t), root) shouldBeLessThan 1e-9
            }
        }
    }

    "no two snakes are rooted at one place" {
        val roots = (0 until hair.snakes).map { hair.at(snake = it, joint = 0, t = 0.0) }

        for (i in roots.indices) {
            for (j in 0 until i) {
                far(roots[i], roots[j]) shouldBeGreaterThan 0.12
            }
        }
    }

    "a snake leads out of the head, about as long as it is said to be, without a kink" {
        for (snake in 0 until hair.snakes) {
            for (t in listOf(0.0, 1.3, 77.7)) {
                val points = (0 until hair.joints).map { hair.at(snake = snake, joint = it, t = t) }
                val steps = points.zipWithNext().map { (a, b) -> far(a, b) }

                // it comes out of the head: its second half lies outside, and its tip well away
                points.drop(hair.joints / 2).all { far(it, HEAD) > 1.0 } shouldBe true
                far(points.last(), HEAD) shouldBeGreaterThan 1.8
                // the steps are of a kind: none more than three times the plain step
                steps.all { it < 3 * hair.length / (hair.joints - 1) } shouldBe true
                steps.sum() shouldBeGreaterThan 0.95 * hair.length
                steps.sum() shouldBeLessThan 2.2 * hair.length
            }
        }
    }

    "the tips writhe: they move with the time, slowly, and no two snakes alike" {
        val tip = hair.joints - 1

        for (snake in 0 until hair.snakes) {
            val moved = far(hair.at(snake = snake, joint = tip, t = 0.0), hair.at(snake = snake, joint = tip, t = 1.0))

            moved shouldBeGreaterThan 0.05
            // within a sixtieth of a second hardly at all: nothing jumps from frame to frame
            far(hair.at(snake = snake, joint = tip, t = 5.0), hair.at(snake = snake, joint = tip, t = 5.0 + 1.0 / 60)) shouldBeLessThan 0.03
        }

        // where a tip is against its own root differs from snake to snake at the same moment
        val waves = (0 until hair.snakes).map { snake ->
            val still = MedusaHair().at(snake = snake, joint = tip, t = 0.0)
            val later = hair.at(snake = snake, joint = tip, t = 0.9)

            far(still, later)
        }

        waves.distinct().size shouldBe hair.snakes
    }

    "the same moment looks the same whenever it is asked for" {
        hair.at(snake = 3, joint = 5, t = 42.5) shouldBe hair.at(snake = 3, joint = 5, t = 42.5)
    }

    "a snake goes with its own head the more the nearer the tip: not at all at the root, wholly at the tip" {
        hair.free(0) shouldBe 0.0
        hair.free(hair.joints - 1) shouldBe 1.0
        (0 until hair.joints).map { hair.free(it) }.zipWithNext().all { (a, b) -> b > a } shouldBe true
        shouldThrow<IllegalArgumentException> { hair.free(hair.joints) }
    }

    "a snake's head makes for the place it wants, unhurried, and two short moments are as one long one" {
        val head = HairPoint(0.0, 0.0, 0.0)
        val wants = HairPoint(1.0, 0.0, 0.0)
        val root = HairPoint(0.0, -1.0, 0.0)

        // no time: it stays
        pulledHead(head = head, wants = wants, root = root, reach = 5.0, dt = 0.0) shouldBe head
        pulledHead(head = head, wants = wants, root = root, reach = 5.0, dt = -1.0) shouldBe head
        // after the easing time: all but a third of the way (1/e)
        pulledHead(head = head, wants = wants, root = root, reach = 5.0, dt = 0.4, ease = 0.4).x shouldBe (0.6321 plusOrMinus 1e-4)
        // after a long time: there
        pulledHead(head = head, wants = wants, root = root, reach = 5.0, dt = 60.0).x shouldBe (1.0 plusOrMinus 1e-9)

        val once = pulledHead(head = head, wants = wants, root = root, reach = 5.0, dt = 0.2)
        val half = pulledHead(head = head, wants = wants, root = root, reach = 5.0, dt = 0.1)
        val twice = pulledHead(head = half, wants = wants, root = root, reach = 5.0, dt = 0.1)

        twice.x shouldBe (once.x plusOrMinus 1e-12)
    }

    "its body holds it: where the root moves off, it is pulled along at the end of its body" {
        // the head is where it wants to be, and the root has gone 3 to the side: the body is 2 long
        val head = HairPoint(0.0, 2.0, 0.0)
        val root = HairPoint(3.0, 0.0, 0.0)
        val pulled = pulledHead(head = head, wants = head, root = root, reach = 2.0, dt = 0.0)

        far(pulled, root) shouldBe (2.0 plusOrMinus 1e-9)
        // straight toward where it was: on the line from the root to it
        val whole = far(head, root)

        pulled.x shouldBe (3.0 - 3.0 * 2.0 / whole plusOrMinus 1e-9)
        pulled.y shouldBe (2.0 * 2.0 / whole plusOrMinus 1e-9)

        // within its body's length it is left alone
        pulledHead(head = head, wants = head, root = HairPoint(0.5, 0.5, 0.0), reach = 2.0, dt = 0.0) shouldBe head
    }

    "a moment follows the last one where time went on a little or stood; else all is set anew" {
        sinceLast(last = null, t = 3.0, most = 0.5) shouldBe null
        sinceLast(last = 3.0, t = 3.0, most = 0.5) shouldBe 0.0
        sinceLast(last = 3.0, t = 3.25, most = 0.5) shouldBe 0.25
        sinceLast(last = 3.0, t = 3.5, most = 0.5) shouldBe 0.5
        // a jump ahead, and time gone back
        sinceLast(last = 3.0, t = 3.6, most = 0.5) shouldBe null
        sinceLast(last = 3.0, t = 2.99, most = 0.5) shouldBe null
    }

    "a snake or a joint that is not there is refused" {
        shouldThrow<IllegalArgumentException> { hair.at(snake = hair.snakes, joint = 0, t = 0.0) }
        shouldThrow<IllegalArgumentException> { hair.at(snake = 0, joint = hair.joints, t = 0.0) }
        shouldThrow<IllegalArgumentException> { MedusaHair(snakes = 0) }
        shouldThrow<IllegalArgumentException> { MedusaHair(joints = 1) }
    }
})
