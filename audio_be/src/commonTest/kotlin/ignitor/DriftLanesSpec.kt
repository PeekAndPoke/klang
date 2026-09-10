/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * The drift-lane container ([DriftLanes]): N own [AnalogDrift] lanes plus one shared lane, blended
 * per sample by `analogSpread`.
 *
 * Every case runs against a test-side reference model built from the DOCUMENTED draw order (one int
 * for the shared lane's seed at construction, then own lanes in index order at `ensureLanes`) and
 * the documented blend, so a reordered draw, a dropped term or a swapped weight shows as a value
 * mismatch rather than as "still drifts somehow".
 */
class DriftLanesSpec : StringSpec({
    val sr = 44100
    val analog = 8.0
    val frames = 16

    /**
     * Replays the draw order the class documents: the shared lane's seed comes off the voice stream
     * first, then [count] own lanes in index order. The shared lane is built from that seed and
     * takes nothing from the voice stream, so [rng] stays where the own lanes left it and further
     * lanes can be drawn from it.
     */
    class Reference(seed: Int, count: Int, analog: Double, sr: Int) {
        val rng = Random(seed)
        val sharedSeed = rng.nextInt()
        val own = Array(count) { AnalogDrift(analog, sr, rng) }
        val shared = AnalogDrift(analog, sr, Random(sharedSeed))
    }

    /**
     * One sample for one lane, exactly as an adopter's hot loop runs it: hoist the lane, the shared
     * walk and the weights, then blend. Hoisting per call is the slow way round and only a test
     * would do it; the point here is that the spec exercises the same two functions production does.
     */
    fun step(lanes: DriftLanes, lane: Int, i: Int): Double =
        driftStep(lanes.ownLane(lane), lanes.sharedWalk(), lanes.wShared, lanes.wOwn, i)

    "spread 1: each lane is its own AnalogDrift bit for bit, and the lanes are all the rng pays for" {
        val rng = Random(7)
        val lanes = DriftLanes(analog, sr, rng)

        lanes.active shouldBe true
        lanes.ensureLanes(3)
        lanes.prepareBlock(1.0, 0, frames)

        val ref = Reference(7, 3, analog, sr)

        for (i in 0 until frames) {
            for (n in 0 until 3) {
                step(lanes, n, i).toRawBits() shouldBe ref.own[n].nextMultiplier().toRawBits()
            }
        }

        // One seed plus three lanes is the whole bill: any other draw would part the two streams.
        rng.nextInt() shouldBe ref.rng.nextInt()
    }

    "spread 0: every lane follows the one shared walk, no own lane advances, and the walk is free" {
        val rng = Random(7)
        val lanes = DriftLanes(analog, sr, rng)

        lanes.ensureLanes(3)
        lanes.prepareBlock(0.0, 0, frames)

        val ref = Reference(7, 3, analog, sr)

        for (i in 0 until frames) {
            val expected = ref.shared.nextMultiplier()
            val first = step(lanes, 0, i)

            first shouldBe (expected plusOrMinus 1e-15)

            for (n in 1 until 3) {
                step(lanes, n, i).toRawBits() shouldBe first.toRawBits()
            }
        }

        // Building the shared lane took nothing from the voice stream: it runs on its own seed.
        rng.nextInt() shouldBe ref.rng.nextInt()

        // And the own lanes stayed put: back at spread 1 they hand out their FIRST multipliers.
        lanes.prepareBlock(1.0, 0, frames)

        for (n in 0 until 3) {
            step(lanes, n, 0).toRawBits() shouldBe ref.own[n].nextMultiplier().toRawBits()
        }
    }

    "spread 0.25: the constant-power blend of both walks (unequal weights pin the direction)" {
        val rng = Random(7)
        val lanes = DriftLanes(analog, sr, rng)

        lanes.ensureLanes(2)
        lanes.prepareBlock(0.25, 0, frames)

        val ref = Reference(7, 2, analog, sr)
        val wShared = sqrt(1.0 - 0.25)
        val wOwn = sqrt(0.25)

        for (i in 0 until frames) {
            val sharedDev = ref.shared.nextMultiplier() - 1.0

            for (n in 0 until 2) {
                val expected = 1.0 + wShared * sharedDev + wOwn * (ref.own[n].nextMultiplier() - 1.0)

                step(lanes, n, i) shouldBe (expected plusOrMinus 1e-12)
            }
        }
    }

    "the shared walk is the same whether the spread drops below 1 at the first block or the sixth" {
        val early = DriftLanes(analog, sr, Random(7))

        early.ensureLanes(2)
        early.prepareBlock(0.0, 0, frames)

        val fromTheStart = DoubleArray(frames) { step(early, 0, it) }

        val lateRng = Random(7)
        val late = DriftLanes(analog, sr, lateRng)

        late.ensureLanes(2)

        repeat(5) {
            late.prepareBlock(1.0, 0, frames)

            for (i in 0 until frames) {
                step(late, 0, i)
            }
        }

        // Something else in the voice draws in between (a mid-note voice would): the shared walk
        // must not care, or lowering `analogSpread` would re-roll whatever that consumer got.
        lateRng.nextInt()
        late.prepareBlock(0.0, 0, frames)

        for (i in 0 until frames) {
            step(late, 0, i).toRawBits() shouldBe fromTheStart[i].toRawBits()
        }
    }

    "prepareBlock fills from its window offset, and the walk continues into the next window" {
        val lanes = DriftLanes(analog, sr, Random(7))

        lanes.ensureLanes(1)

        // Two half-windows back to back, read through `sharedWalk()`, the array a voice loop
        // indexes into. What this pins is that the fill honours `off` and that the walk carries on
        // where the previous window left it, so sample i always gets the i-th value of one walk.
        lanes.prepareBlock(0.5, 0, 64)

        val firstHalf = lanes.sharedWalk().shouldNotBeNull().copyOfRange(0, 64)

        lanes.prepareBlock(0.5, 64, 128)

        val ref = Reference(7, 1, analog, sr)
        val walk = lanes.sharedWalk().shouldNotBeNull()

        for (i in 0 until 128) {
            val expected = ref.shared.nextMultiplier() - 1.0

            walk[i] shouldBe (expected plusOrMinus 1e-15)
        }

        // The second call wrote only its own window; the first window's values are still there.
        for (i in 0 until 64) {
            walk[i].toRawBits() shouldBe firstHalf[i].toRawBits()
        }
    }

    "a NaN spread reads as 1 (the door default), and out-of-range coerces to the ends" {
        val nanRng = Random(7)
        val nan = DriftLanes(analog, sr, nanRng)

        nan.ensureLanes(2)
        nan.prepareBlock(Double.NaN, 0, frames)

        val ref = Reference(7, 2, analog, sr)

        for (n in 0 until 2) {
            step(nan, n, 0).toRawBits() shouldBe ref.own[n].nextMultiplier().toRawBits()
        }

        nanRng.nextInt() shouldBe ref.rng.nextInt()

        val high = DriftLanes(analog, sr, Random(7))

        high.ensureLanes(2)
        high.prepareBlock(4.0, 0, frames)
        step(high, 0, 0).toRawBits() shouldBe Reference(7, 1, analog, sr).own[0].nextMultiplier().toRawBits()

        val low = DriftLanes(analog, sr, Random(7))

        low.ensureLanes(2)
        low.prepareBlock(-3.0, 0, frames)
        step(low, 0, 1).toRawBits() shouldBe step(low, 1, 1).toRawBits()
        (step(low, 0, 1) != 1.0) shouldBe true
    }

    "ensureLanes grows without disturbing the lanes that already exist" {
        val lanes = DriftLanes(analog, sr, Random(7))

        lanes.ensureLanes(2)
        lanes.laneCount shouldBe 2
        lanes.prepareBlock(1.0, 0, frames)

        val ref = Reference(7, 2, analog, sr)

        for (i in 0 until frames) {
            for (n in 0 until 2) {
                step(lanes, n, i).toRawBits() shouldBe ref.own[n].nextMultiplier().toRawBits()
            }
        }

        lanes.ensureLanes(4)
        lanes.laneCount shouldBe 4
        lanes.prepareBlock(1.0, 0, frames)

        // Lanes 2 and 3 are drawn AFTER the first two, which keep walking where they were.
        val grown = Array(2) { AnalogDrift(analog, sr, ref.rng) }

        for (i in 0 until frames) {
            step(lanes, 0, i).toRawBits() shouldBe ref.own[0].nextMultiplier().toRawBits()
            step(lanes, 1, i).toRawBits() shouldBe ref.own[1].nextMultiplier().toRawBits()
            step(lanes, 2, i).toRawBits() shouldBe grown[0].nextMultiplier().toRawBits()
            step(lanes, 3, i).toRawBits() shouldBe grown[1].nextMultiplier().toRawBits()
        }
    }

    "shrink then regrow creates FRESH lanes at the regrown indices" {
        val lanes = DriftLanes(analog, sr, Random(7))

        lanes.ensureLanes(3)
        lanes.prepareBlock(1.0, 0, frames)

        val ref = Reference(7, 3, analog, sr)

        for (i in 0 until frames) {
            for (n in 0 until 3) {
                step(lanes, n, i).toRawBits() shouldBe ref.own[n].nextMultiplier().toRawBits()
            }
        }

        lanes.retireLanes(1)
        lanes.laneCount shouldBe 1
        lanes.ensureLanes(3)
        lanes.laneCount shouldBe 3

        // The regrown indices drew again, in index order, from where the stream stood.
        val regrown = Array(2) { AnalogDrift(analog, sr, ref.rng) }

        lanes.prepareBlock(1.0, 0, frames)

        // Bit equality against a lane drawn fresh from the same point in the stream IS the oracle:
        // a resumed walk would be a different number. That a fresh lane attacks in tune is
        // AnalogDriftSpec's business, and a cents bound here could not tell the two apart anyway,
        // because over a short block the fast layer keeps EVERY lane inside its budget.
        step(lanes, 1, 0).toRawBits() shouldBe regrown[0].nextMultiplier().toRawBits()
        step(lanes, 2, 0).toRawBits() shouldBe regrown[1].nextMultiplier().toRawBits()
        step(lanes, 0, 0).toRawBits() shouldBe ref.own[0].nextMultiplier().toRawBits()

        for (i in 1 until frames) {
            step(lanes, 0, i).toRawBits() shouldBe ref.own[0].nextMultiplier().toRawBits()
            step(lanes, 1, i).toRawBits() shouldBe regrown[0].nextMultiplier().toRawBits()
            step(lanes, 2, i).toRawBits() shouldBe regrown[1].nextMultiplier().toRawBits()
        }
    }

    "a retired index hands out no lane at all" {
        val lanes = DriftLanes(analog, sr, Random(7))

        lanes.ensureLanes(3)
        lanes.prepareBlock(1.0, 0, frames)
        lanes.retireLanes(1)

        // The objects are still in the array, waiting to be rebuilt; they are nobody's lane now.
        lanes.ownLane(0) shouldNotBe null
        lanes.ownLane(1) shouldBe null
        lanes.ownLane(2) shouldBe null

        // An adopter that still steps a retired index gets a plain 1.0, never a stale walk.
        step(lanes, 2, 0) shouldBe 1.0
    }

    "analog 0: inactive, every method a no-op, step exactly 1.0 and not one rng draw" {
        val rng = Random(7)
        val lanes = DriftLanes(0.0, sr, rng)

        lanes.active shouldBe false
        lanes.ensureLanes(4)
        lanes.laneCount shouldBe 0
        lanes.retireLanes(0)
        lanes.prepareBlock(0.0, 0, frames)
        step(lanes, 0, 0) shouldBe 1.0
        step(lanes, 3, 5) shouldBe 1.0

        rng.nextInt() shouldBe Random(7).nextInt()
    }
})
