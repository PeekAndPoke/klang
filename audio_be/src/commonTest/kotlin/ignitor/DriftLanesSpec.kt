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
 * per block by `analogSpread` into a ramp every voice reads as `startOf` and `endOf`.
 *
 * Every case runs against a test-side reference model built from the DOCUMENTED draw order (one int
 * for the shared lane's seed at construction, then own lanes in index order at `ensureLanes`) and
 * the documented blend, so a reordered draw, a dropped term or a swapped weight shows as a value
 * mismatch rather than as "still drifts somehow".
 */
class DriftLanesSpec : StringSpec({

    val rate = 375
    val analog = 8.0
    val blocks = 16

    /**
     * Replays the draw order the class documents: the shared lane's seed comes off the voice stream
     * first, then [count] own lanes in index order. The shared lane is built from that seed and
     * takes nothing from the voice stream, so [rng] stays where the own lanes left it and further
     * lanes can be drawn from it.
     */
    class Reference(seed: Int, count: Int, analog: Double, rate: Int) {
        val rng = Random(seed)
        val sharedSeed = rng.nextInt()
        val own = Array(count) { AnalogDrift(analog, rate, rng) }
        val shared = AnalogDrift(analog, rate, Random(sharedSeed))
    }

    /** One block for one lane, exactly as an adopter runs it: advance the lane, read the ramp's end. */
    fun end(lanes: DriftLanes, lane: Int): Double {
        lanes.advanceLane(lane)

        return lanes.endOf(lane)
    }

    "spread 1: each lane's block end is its own AnalogDrift step bit for bit, and the lanes are all the rng pays for" {
        val rng = Random(7)
        val lanes = DriftLanes(analog, rate, rng)

        lanes.active shouldBe true
        lanes.ensureLanes(3)

        val ref = Reference(7, 3, analog, rate)

        repeat(blocks) {
            lanes.prepareBlock(1.0)

            for (n in 0 until 3) {
                end(lanes, n).toRawBits() shouldBe ref.own[n].nextMultiplier().toRawBits()
            }
        }

        rng.nextInt() shouldBe ref.rng.nextInt()
    }

    "spread 0: every lane follows the one shared walk, no own lane advances, and the walk is free" {
        val rng = Random(7)
        val lanes = DriftLanes(analog, rate, rng)

        lanes.ensureLanes(3)

        val ref = Reference(7, 3, analog, rate)

        repeat(blocks) {
            lanes.prepareBlock(0.0)

            val expected = ref.shared.nextMultiplier()
            val first = end(lanes, 0)

            first.toRawBits() shouldBe expected.toRawBits()

            for (n in 1 until 3) {
                end(lanes, n).toRawBits() shouldBe first.toRawBits()
            }
        }

        rng.nextInt() shouldBe ref.rng.nextInt()

        // Nothing advanced the own lanes meanwhile: back at spread 1 they step from their seeds.
        lanes.prepareBlock(1.0)

        for (n in 0 until 3) {
            end(lanes, n).toRawBits() shouldBe ref.own[n].nextMultiplier().toRawBits()
        }
    }

    "spread 0.25: the constant-power blend of both walks (unequal weights pin the direction)" {
        val rng = Random(7)
        val lanes = DriftLanes(analog, rate, rng)

        lanes.ensureLanes(2)

        val ref = Reference(7, 2, analog, rate)
        val wShared = sqrt(1.0 - 0.25)
        val wOwn = sqrt(0.25)

        repeat(blocks) {
            lanes.prepareBlock(0.25)

            val sharedDev = ref.shared.nextMultiplier() - 1.0

            for (n in 0 until 2) {
                val expected = 1.0 + wShared * sharedDev + wOwn * (ref.own[n].nextMultiplier() - 1.0)

                end(lanes, n) shouldBe (expected plusOrMinus 1e-12)
            }
        }
    }

    "the ramp is continuous: a block starts where the previous one ended, from the seeded state on" {
        val lanes = DriftLanes(analog, rate, Random(7))

        lanes.ensureLanes(2)

        val ref = Reference(7, 2, analog, rate)

        // Before any step an own lane sits at its seeded state, the same value at both ends
        // (prepareBlock steps the shared lane, advanceLane the own one; neither has run for it).
        lanes.prepareBlock(1.0)
        lanes.startOf(0).toRawBits() shouldBe lanes.endOf(0).toRawBits()

        // At a constant spread every block starts where the previous one ended, bit for bit (a
        // spread change re-weights the blend, so continuity is promised within a spread only).
        lanes.prepareBlock(0.25)
        lanes.advanceLane(0)

        var previousEnd = lanes.endOf(0)

        repeat(blocks) {
            lanes.prepareBlock(0.25)
            lanes.advanceLane(0)
            lanes.startOf(0).toRawBits() shouldBe previousEnd.toRawBits()
            previousEnd = lanes.endOf(0)
        }

        // And the seeded state is the lane's own: the first block's start is where the reference
        // lane sat before its first step (its block ramp, unstepped).
        val fresh = DriftLanes(analog, rate, Random(7))

        fresh.ensureLanes(1)
        fresh.prepareBlock(1.0)
        fresh.startOf(0).toRawBits() shouldBe ref.own[0].blockStart.toRawBits()
    }

    "the shared walk is the same whether the spread drops below 1 at the first block or the sixth" {
        val early = DriftLanes(analog, rate, Random(7))

        early.ensureLanes(2)

        val fromTheStart = DoubleArray(blocks) {
            early.prepareBlock(0.0)
            end(early, 0)
        }

        val lateRng = Random(7)
        val late = DriftLanes(analog, rate, lateRng)

        late.ensureLanes(2)

        repeat(5) {
            late.prepareBlock(1.0)
            end(late, 0)
        }

        lateRng.nextInt()

        for (b in 0 until blocks) {
            late.prepareBlock(0.0)
            end(late, 0).toRawBits() shouldBe fromTheStart[b].toRawBits()
        }
    }

    "a NaN spread reads as 1 (the door default), and out-of-range coerces to the ends" {
        val nanRng = Random(7)
        val nan = DriftLanes(analog, rate, nanRng)

        nan.ensureLanes(2)
        nan.prepareBlock(Double.NaN)

        val ref = Reference(7, 2, analog, rate)

        for (n in 0 until 2) {
            end(nan, n).toRawBits() shouldBe ref.own[n].nextMultiplier().toRawBits()
        }

        nanRng.nextInt() shouldBe ref.rng.nextInt()

        val high = DriftLanes(analog, rate, Random(7))

        high.ensureLanes(2)
        high.prepareBlock(4.0)
        end(high, 0).toRawBits() shouldBe Reference(7, 1, analog, rate).own[0].nextMultiplier().toRawBits()

        val low = DriftLanes(analog, rate, Random(7))

        low.ensureLanes(2)
        low.prepareBlock(-3.0)
        end(low, 0).toRawBits() shouldBe end(low, 1).toRawBits()
        (end(low, 0) != 1.0) shouldBe true
    }

    "ensureLanes grows without disturbing the lanes that already exist" {
        val lanes = DriftLanes(analog, rate, Random(7))

        lanes.ensureLanes(2)
        lanes.laneCount shouldBe 2

        val ref = Reference(7, 2, analog, rate)

        repeat(blocks) {
            lanes.prepareBlock(1.0)

            for (n in 0 until 2) {
                end(lanes, n).toRawBits() shouldBe ref.own[n].nextMultiplier().toRawBits()
            }
        }

        lanes.ensureLanes(4)
        lanes.laneCount shouldBe 4

        val grown = Array(2) { AnalogDrift(analog, rate, ref.rng) }

        repeat(blocks) {
            lanes.prepareBlock(1.0)
            end(lanes, 0).toRawBits() shouldBe ref.own[0].nextMultiplier().toRawBits()
            end(lanes, 1).toRawBits() shouldBe ref.own[1].nextMultiplier().toRawBits()
            end(lanes, 2).toRawBits() shouldBe grown[0].nextMultiplier().toRawBits()
            end(lanes, 3).toRawBits() shouldBe grown[1].nextMultiplier().toRawBits()
        }
    }

    "shrink then regrow creates FRESH lanes at the regrown indices" {
        val lanes = DriftLanes(analog, rate, Random(7))

        lanes.ensureLanes(3)

        val ref = Reference(7, 3, analog, rate)

        repeat(blocks) {
            lanes.prepareBlock(1.0)

            for (n in 0 until 3) {
                end(lanes, n).toRawBits() shouldBe ref.own[n].nextMultiplier().toRawBits()
            }
        }

        lanes.retireLanes(1)
        lanes.laneCount shouldBe 1
        lanes.ensureLanes(3)
        lanes.laneCount shouldBe 3

        // The regrown indices drew again, in index order, from where the stream stood. Bit
        // equality against a lane drawn fresh from the same point in the stream IS the oracle: a
        // resumed walk would be a different number.
        val regrown = Array(2) { AnalogDrift(analog, rate, ref.rng) }

        repeat(blocks) {
            lanes.prepareBlock(1.0)
            end(lanes, 0).toRawBits() shouldBe ref.own[0].nextMultiplier().toRawBits()
            end(lanes, 1).toRawBits() shouldBe regrown[0].nextMultiplier().toRawBits()
            end(lanes, 2).toRawBits() shouldBe regrown[1].nextMultiplier().toRawBits()
        }
    }

    "a retired index is nobody's lane: it does not advance and reads as no drift" {
        val lanes = DriftLanes(analog, rate, Random(7))

        lanes.ensureLanes(3)
        lanes.prepareBlock(1.0)
        lanes.retireLanes(1)

        // The objects are still in the array, waiting to be rebuilt; an adopter that still reads
        // a retired index gets a plain 1.0 at both ends, never a stale walk.
        lanes.advanceLane(2)
        lanes.startOf(2) shouldBe 1.0
        lanes.endOf(2) shouldBe 1.0
        (end(lanes, 0) != 1.0) shouldBe true
    }

    "analog 0: inactive, every method a no-op, the ramp exactly 1.0 and not one rng draw" {
        val rng = Random(7)
        val lanes = DriftLanes(0.0, rate, rng)

        lanes.active shouldBe false
        lanes.ensureLanes(4)
        lanes.laneCount shouldBe 0
        lanes.retireLanes(0)
        lanes.prepareBlock(0.0)
        end(lanes, 0) shouldBe 1.0
        lanes.startOf(3) shouldBe 1.0

        rng.nextInt() shouldBe Random(7).nextInt()
    }
})
