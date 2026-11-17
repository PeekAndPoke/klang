/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import kotlin.math.abs

/** The chances of a test: [taken] for whether a stretch is taken, [where] for where in it, [long] for how long. */
private fun rolls(taken: Double, where: Double, long: Double): (Int, Int) -> Double = { _, purpose ->
    when (purpose) {
        0 -> taken
        1 -> where
        else -> long
    }
}

class StraightenSpec : StringSpec({

    "a stretch whose chance is taken: slowly up, held, more slowly down, and as always for the rest of it" {
        val straighten = Straighten(every = 20.0, chance = 0.5)
        // right at the stretch's start, held the shortest time: up by 1.8 s, down from 5.8 s to 8.4 s
        val roll = rolls(taken = 0.2, where = 0.0, long = 0.0)

        straighten.at(t = 0.0, roll = roll) shouldBe 0.0
        straighten.at(t = 0.9, roll = roll) shouldBe (0.5 plusOrMinus 1e-9)
        straighten.at(t = 1.8, roll = roll) shouldBe 1.0
        straighten.at(t = 5.8, roll = roll) shouldBe 1.0
        straighten.at(t = 7.1, roll = roll) shouldBe (0.5 plusOrMinus 1e-9)
        straighten.at(t = 8.4, roll = roll) shouldBe 0.0
        straighten.at(t = 15.0, roll = roll) shouldBe 0.0
        // and the same again in the next stretch
        straighten.at(t = 21.8, roll = roll) shouldBe 1.0
    }

    "the longest time, as late in the stretch as it goes, ends with the stretch" {
        val straighten = Straighten(every = 20.0, chance = 0.5)
        val roll = rolls(taken = 0.2, where = 1.0, long = 1.0)

        // 13.4 s in all: from 6.6 s to 20 s
        straighten.at(t = 6.6, roll = roll) shouldBe 0.0
        straighten.at(t = 8.4, roll = roll) shouldBe 1.0
        straighten.at(t = 17.4, roll = roll) shouldBe 1.0
        straighten.at(t = 19.99, roll = roll) shouldBeLessThan 0.001
    }

    "how long it is held and where in the stretch it begins are two chances" {
        val straighten = Straighten(every = 20.0, chance = 0.5)
        // right at the start, held the longest: up by 1.8 s, down from 10.8 s to 13.4 s
        val early = rolls(taken = 0.2, where = 0.0, long = 1.0)

        straighten.at(t = 1.8, roll = early) shouldBe 1.0
        straighten.at(t = 10.8, roll = early) shouldBe 1.0
        straighten.at(t = 13.4, roll = early) shouldBe 0.0

        // as late as it goes, held the shortest: 8.4 s in all, from 11.6 s
        val late = rolls(taken = 0.2, where = 1.0, long = 0.0)

        straighten.at(t = 11.6, roll = late) shouldBe 0.0
        straighten.at(t = 13.4, roll = late) shouldBe 1.0
        straighten.at(t = 17.4, roll = late) shouldBe 1.0
        straighten.at(t = 19.99, roll = late) shouldBeLessThan 0.001
    }

    "a stretch whose chance is not taken stays as always" {
        val straighten = Straighten(every = 20.0, chance = 0.5)

        (0..200).maxOf { straighten.at(t = it / 10.0, roll = rolls(taken = 0.5, where = 0.0, long = 0.0)) } shouldBe 0.0
        (0..200).maxOf { straighten.at(t = it / 10.0, roll = rolls(taken = 0.9, where = 0.0, long = 0.0)) } shouldBe 0.0
    }

    "each stretch asks for its own chances" {
        val straighten = Straighten(every = 20.0, chance = 0.5)
        // only the stretch number 2 is taken
        val roll: (Int, Int) -> Double = { stretch, purpose -> if (purpose == 0 && stretch != 2) 0.9 else 0.0 }

        straighten.at(t = 1.8, roll = roll) shouldBe 0.0
        straighten.at(t = 21.8, roll = roll) shouldBe 0.0
        straighten.at(t = 41.8, roll = roll) shouldBe 1.0
    }

    "it never jumps, whatever the chances, also from one stretch to the next" {
        val straighten = Straighten(every = Straighten.LEAST, chance = 1.0)
        val roll: (Int, Int) -> Double = { stretch, purpose -> ((stretch * 7 + purpose * 3) % 10) / 9.0 }
        val steps = (0 until 12000).map {
            abs(straighten.at(t = (it + 1) / 100.0, roll = roll) - straighten.at(t = it / 100.0, roll = roll))
        }

        steps.max() shouldBeLessThan 0.01
    }

    "before the song the player stands as always" {
        // the stretch before the song's start would end with it: a second before, the player would still be sinking
        Straighten(every = 20.0, chance = 1.0).at(t = -1.0, roll = rolls(taken = 0.0, where = 1.0, long = 1.0)) shouldBe 0.0
    }

    "a stretch too short to straighten up in is refused" {
        shouldThrow<IllegalArgumentException> { Straighten(every = 13.0, chance = 0.5) }
    }
})
