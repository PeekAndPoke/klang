/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.audio_be.ignitor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.random.Random

/**
 * An [AnalogDrift] is built, then seeded (tidy-up step 10): the oscillators build their lane with the voice and seed
 * it at the voice's first block, and [DriftLanes] re-seeds a retired lane instead of building a new one. Both rest on
 * one promise: [AnalogDrift.seed] leaves the lane exactly as a freshly built-and-seeded one, whatever it held, and
 * draws exactly what the build-and-seed constructor draws.
 */
class AnalogDriftSeedSpec : StringSpec({

    val rate = 375
    val steps = 64

    /** The lane's state before its first step ([AnalogDrift.blockStart], [AnalogDrift.blockEnd]), then its next [steps] block ends. */
    fun walk(lane: AnalogDrift): DoubleArray {
        val out = DoubleArray(steps + 2)

        out[0] = lane.blockStart
        out[1] = lane.blockEnd

        for (i in 0 until steps) {
            lane.beginBlock()
            out[i + 2] = lane.blockEnd
        }

        return out
    }

    /** Both walks, bit for bit. */
    fun sameWalk(a: AnalogDrift, b: AnalogDrift) {
        a.active shouldBe b.active

        val wa = walk(a)
        val wb = walk(b)

        for (i in wa.indices) {
            wa[i].toRawBits() shouldBe wb[i].toRawBits()
        }
    }

    "an unseeded lane is inactive and sits at 1 at both ends" {
        val lane = AnalogDrift()

        lane.active shouldBe false
        lane.blockStart shouldBe 1.0
        lane.blockEnd shouldBe 1.0
    }

    "seeding a USED lane leaves it exactly a fresh one: same walk, same draws" {
        // The used lane: another depth, another stream, walked a while (both layers away from their seeds).
        val used = AnalogDrift(analog = 3.0, stepRate = rate, rng = Random(5))

        repeat(200) {
            used.beginBlock()
        }

        val usedStream = Random(11)
        val freshStream = Random(11)

        used.seed(analog = 8.0, stepRate = rate, rng = usedStream)

        val fresh = AnalogDrift(analog = 8.0, stepRate = rate, rng = freshStream)

        sameWalk(used, fresh)
        usedStream.nextInt() shouldBe freshStream.nextInt()
    }

    "seeding at depth 0 deactivates a used lane, and still draws what a fresh lane at 0 draws" {
        val used = AnalogDrift(analog = 8.0, stepRate = rate, rng = Random(5))

        repeat(20) {
            used.beginBlock()
        }

        val usedStream = Random(11)
        val freshStream = Random(11)

        used.seed(analog = 0.0, stepRate = rate, rng = usedStream)

        val fresh = AnalogDrift(analog = 0.0, stepRate = rate, rng = freshStream)

        used.active shouldBe false
        sameWalk(used, fresh)
        usedStream.nextInt() shouldBe freshStream.nextInt()
    }
})
