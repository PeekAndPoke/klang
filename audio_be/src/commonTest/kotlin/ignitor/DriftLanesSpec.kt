/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.shouldBe
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The drift-lane container ([DriftLanes]): N own [AnalogDrift] lanes plus one shared lane, blended
 * per sample by `analogSpread`.
 *
 * Every case runs against a test-side reference model built from the DOCUMENTED draw order (own
 * lanes in index order at `ensureLanes`, the shared lane at the first `prepareBlock` below spread
 * 1) and the documented blend, so a reordered draw, a dropped term or a swapped weight shows as a
 * value mismatch rather than as "still drifts somehow".
 */
class DriftLanesSpec : StringSpec({
    val sr = 44100
    val analog = 8.0
    val frames = 16

    /** Reference lanes in the draw order the class documents: [count] own lanes, in index order. */
    fun lanesOf(rng: Random, count: Int): Array<AnalogDrift> = Array(count) { AnalogDrift(analog, sr, rng) }

    "spread 1: each lane is its own AnalogDrift bit for bit, and no shared lane is ever drawn" {
        val rng = Random(7)
        val lanes = DriftLanes(analog, sr, rng)

        lanes.active shouldBe true
        lanes.ensureLanes(3)
        lanes.prepareBlock(1.0, 0, frames)

        val refRng = Random(7)
        val ref = lanesOf(refRng, 3)

        for (i in 0 until frames) {
            for (n in 0 until 3) {
                lanes.step(n, i).toRawBits() shouldBe ref[n].nextMultiplier().toRawBits()
            }
        }

        // The own lanes are the ONLY draws: a shared lane created at spread 1 would move the
        // stream on and the two positions would part.
        rng.nextInt() shouldBe refRng.nextInt()
    }

    "spread 0: every lane follows the one shared walk, and no own lane is advanced" {
        val rng = Random(7)
        val lanes = DriftLanes(analog, sr, rng)

        lanes.ensureLanes(3)
        lanes.prepareBlock(0.0, 0, frames)

        val refRng = Random(7)
        val ownRef = lanesOf(refRng, 3)
        val sharedRef = AnalogDrift(analog, sr, refRng)

        for (i in 0 until frames) {
            val expected = sharedRef.nextMultiplier()
            val first = lanes.step(0, i)

            first shouldBe (expected plusOrMinus 1e-15)

            for (n in 1 until 3) {
                lanes.step(n, i).toRawBits() shouldBe first.toRawBits()
            }
        }

        // The own lanes stayed put: back at spread 1 they hand out their FIRST multipliers.
        lanes.prepareBlock(1.0, 0, frames)

        for (n in 0 until 3) {
            lanes.step(n, 0).toRawBits() shouldBe ownRef[n].nextMultiplier().toRawBits()
        }
    }

    "spread 0.25: the constant-power blend of both walks (unequal weights pin the direction)" {
        val rng = Random(7)
        val lanes = DriftLanes(analog, sr, rng)

        lanes.ensureLanes(2)
        lanes.prepareBlock(0.25, 0, frames)

        val refRng = Random(7)
        val ownRef = lanesOf(refRng, 2)
        val sharedRef = AnalogDrift(analog, sr, refRng)
        val wShared = sqrt(1.0 - 0.25)
        val wOwn = sqrt(0.25)

        for (i in 0 until frames) {
            val sharedDev = sharedRef.nextMultiplier() - 1.0

            for (n in 0 until 2) {
                val expected = 1.0 + wShared * sharedDev + wOwn * (ownRef[n].nextMultiplier() - 1.0)

                lanes.step(n, i) shouldBe (expected plusOrMinus 1e-12)
            }
        }
    }

    "a NaN spread reads as 1 (the door default), and out-of-range coerces to the ends" {
        val nanRng = Random(7)
        val nan = DriftLanes(analog, sr, nanRng)

        nan.ensureLanes(2)
        nan.prepareBlock(Double.NaN, 0, frames)

        val refRng = Random(7)
        val ref = lanesOf(refRng, 2)

        for (n in 0 until 2) {
            nan.step(n, 0).toRawBits() shouldBe ref[n].nextMultiplier().toRawBits()
        }

        // NaN reading as 0 instead would have drawn the shared lane here.
        nanRng.nextInt() shouldBe refRng.nextInt()

        val high = DriftLanes(analog, sr, Random(7))

        high.ensureLanes(2)
        high.prepareBlock(4.0, 0, frames)
        high.step(0, 0).toRawBits() shouldBe lanesOf(Random(7), 1)[0].nextMultiplier().toRawBits()

        val low = DriftLanes(analog, sr, Random(7))

        low.ensureLanes(2)
        low.prepareBlock(-3.0, 0, frames)
        low.step(0, 1).toRawBits() shouldBe low.step(1, 1).toRawBits()
        (low.step(0, 1) != 1.0) shouldBe true
    }

    "ensureLanes grows without disturbing the lanes that already exist" {
        val rng = Random(7)
        val lanes = DriftLanes(analog, sr, rng)

        lanes.ensureLanes(2)
        lanes.laneCount shouldBe 2
        lanes.prepareBlock(1.0, 0, frames)

        val refRng = Random(7)
        val survivors = lanesOf(refRng, 2)

        for (i in 0 until frames) {
            for (n in 0 until 2) {
                lanes.step(n, i).toRawBits() shouldBe survivors[n].nextMultiplier().toRawBits()
            }
        }

        lanes.ensureLanes(4)
        lanes.laneCount shouldBe 4
        lanes.prepareBlock(1.0, 0, frames)

        // Lanes 2 and 3 are drawn AFTER the first two, which keep walking where they were.
        val grown = lanesOf(refRng, 2)

        for (i in 0 until frames) {
            lanes.step(0, i).toRawBits() shouldBe survivors[0].nextMultiplier().toRawBits()
            lanes.step(1, i).toRawBits() shouldBe survivors[1].nextMultiplier().toRawBits()
            lanes.step(2, i).toRawBits() shouldBe grown[0].nextMultiplier().toRawBits()
            lanes.step(3, i).toRawBits() shouldBe grown[1].nextMultiplier().toRawBits()
        }
    }

    "analog 0: inactive, every method a no-op, step exactly 1.0 and not one rng draw" {
        val rng = Random(7)
        val lanes = DriftLanes(0.0, sr, rng)

        lanes.active shouldBe false
        lanes.ensureLanes(4)
        lanes.laneCount shouldBe 0
        lanes.prepareBlock(0.0, 0, frames)
        lanes.step(0, 0) shouldBe 1.0
        lanes.step(3, 5) shouldBe 1.0

        rng.nextInt() shouldBe Random(7).nextInt()
    }
})
