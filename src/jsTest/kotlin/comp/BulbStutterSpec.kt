/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.comp

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.ultra.maths.Smoothing
import kotlin.random.Random

/** The start page's lamp, a bulb about to break (the maintainer, 2026-10-27): calm for a while, then a stutter. */
class BulbStutterSpec : StringSpec({

    /** The light's share at each 16 ms frame over [ms]. */
    fun BulbStutter.frames(ms: Double): List<Double> = (0 until (ms / 16).toInt()).map { next(16.0) }

    "each stutter brings its own way of smoothing, of all four kinds; none between the stutters" {
        val laws = mutableListOf<Smoothing>()
        val bulb = BulbStutter(random = Random(3), onStutter = { laws.add(it) })

        bulb.frames(BulbStutter.CALM_MS.start - 16)
        laws.size shouldBe 0

        bulb.frames(5 * 60_000.0)

        laws.size shouldBeGreaterThan 20
        laws.map { it::class }.toSet().size shouldBe 4
    }

    "a swapped law takes the next step, from where the motion is" {
        val swappable = SwappableSmoothing(Smoothing.Linear(unitsPerSecond = 1.0))
        val start = Smoothing.Motion(position = 0.5, velocity = 0.0)

        swappable.step(motion = start, goal = 1.0, elapsedMs = 100.0).position shouldBe (0.6 plusOrMinus 1e-9)

        swappable.law = Smoothing.Linear(unitsPerSecond = 2.0)
        swappable.step(motion = start, goal = 1.0, elapsedMs = 100.0).position shouldBe (0.7 plusOrMinus 1e-9)
        swappable.isSettled(motion = Smoothing.Motion(position = 0.999), goal = 1.0, near = 0.002) shouldBe true
    }

    "the first stretch is calm: the light comes up cleanly" {
        repeat(20) { seed ->
            BulbStutter(Random(seed)).frames(BulbStutter.CALM_MS.start - 16).all { it == 1.0 } shouldBe true
        }
    }

    "over a minute: stutters with dips, a few deep ones but never below half, calm stretches between" {
        val shares = BulbStutter(Random(7)).frames(60_000.0)

        // The stutters: runs of frames off full light, each short
        val runs = mutableListOf<Int>()
        var calm = 0

        shares.forEach { share ->
            if (share == 1.0) {
                calm++
            } else {
                runs.add(calm)
                calm = 0
            }

            share shouldBeGreaterThanOrEqual BulbStutter.DEEP_LEVEL.start
        }

        shares.count { it < 1.0 } shouldBeGreaterThan 0
        shares.count { it < BulbStutter.DIP_LEVEL.start } shouldBeGreaterThan 0
        shares.min() shouldBeGreaterThanOrEqual 0.5
        // Most of the minute calm
        (shares.count { it == 1.0 }.toDouble() / shares.size) shouldBeGreaterThanOrEqual 0.7
        // A calm stretch of at least the shortest one, seconds without a dip
        runs.max() shouldBeGreaterThan (BulbStutter.CALM_MS.start / 16).toInt() - 1
        (shares.count { it < 1.0 }.toDouble() / shares.size) shouldBeLessThan 0.3
    }
})
