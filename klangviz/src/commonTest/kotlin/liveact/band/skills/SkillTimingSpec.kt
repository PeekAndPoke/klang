/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.skills

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.Show
import io.peekandpoke.klang.viz.liveact.smooth
import kotlin.math.floor

/** Klaue's glance at the camera, as his sheet equips him with it. */
private val KLAUE = SkillTiming(
    every = 11.0,
    chance = 0.5,
    cooldown = 0.0,
    least = 2.2,
    most = 4.2,
    earliest = 0.8,
    reserve = 6.0,
    rise = 0.7,
    fall = 0.9,
)

/**
 * The glance at the camera as the string player worked it out before it was a skill (`StringPlayer.glanceAt` until
 * 2026-11-17), copied expression for expression: what [SkillTiming.inUse] must give with Klaue's numbers, to the bit.
 */
private fun glanceBefore(show: Show, seed: Double, t: Double): Double {
    val every = 11.0
    val chance = 0.5
    val slot = floor(t / every)

    fun roll(n: Int): Double = show.own(slot + n, seed)

    if (roll(0) > chance) {
        return 0.0
    }

    val start = slot * every + 0.8 + roll(1) * (every - 6)
    val hold = 2.2 + 2.0 * roll(2)
    val x = t - start

    return smooth(x / 0.7) * smooth((hold + 0.7 - x) / 0.9)
}

/**
 * The stretches in which [timing] is in use from [since] to [until] seconds (whole hundredths), looked at every
 * hundredth of a second.
 */
private fun uses(
    timing: SkillTiming,
    roll: (Double) -> Double,
    since: Double,
    until: Double,
): List<ClosedFloatingPointRange<Double>> {
    val out = mutableListOf<ClosedFloatingPointRange<Double>>()
    var from: Double? = null
    var last = 0.0

    for (i in (since * 100).toInt()..(until * 100).toInt()) {
        val t = i / 100.0
        val on = timing.inUse(t = t, roll = roll) > 0

        if (on && from == null) {
            from = t
        }

        if (!on && from != null) {
            out.add(from..last)
            from = null
        }

        if (on) {
            last = t
        }
    }

    return out
}

class SkillTimingSpec : StringSpec({

    "a chance not taken gives nothing all through its slot" {
        // every roll 0.9: more than the chance of one in two, so no chance is taken
        val never = { _: Double -> 0.9 }

        withClue("a chance not taken") {
            (0 until 3300).maxOf { KLAUE.inUse(t = it / 100.0, roll = never) } shouldBe 0.0
        }
    }

    "a use rises, holds and falls: eased in over its rise, held, eased out over its fall, within its slot" {
        // every roll one half: the use of slot 0 begins at 0.8 + 0.5 * 5 = 3.3 s and holds 2.2 + 0.5 * 2 = 3.2 s,
        // so it is all there from 4.0 s, falls from 6.3 s and is over at 7.2 s
        val half = { _: Double -> 0.5 }

        fun at(t: Double): Double = KLAUE.inUse(t = t, roll = half)

        withClue("the use of slot 0, every roll one half") {
            listOf(0.0, 3.0, 3.3, 7.2, 8.0, 10.99).map(::at) shouldBe listOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0)
            at(3.65) shouldBe (0.5 plusOrMinus 1e-9)
            listOf(4.0, 5.0, 6.3).map(::at) shouldBe listOf(1.0, 1.0, 1.0)
            at(6.75) shouldBe (0.5 plusOrMinus 1e-9)

            // up without a step back, then down
            val rising = (330..400).map { at(it / 100.0) }
            val falling = (630..720).map { at(it / 100.0) }

            rising shouldBe rising.sorted()
            falling shouldBe falling.sortedDescending()
        }
    }

    "the same time gives the same answer, whatever was asked before" {
        val show = Show(seed = 7)
        val roll = { i: Double -> show.own(i, 41.0) }
        val times = (0 until 4000).map { it * 0.137 - 40.0 }
        val forward = times.map { KLAUE.inUse(t = it, roll = roll) }
        val backward = times.reversed().map { KLAUE.inUse(t = it, roll = roll) }.reversed()

        backward shouldBe forward
        forward.count { it > 0 } shouldBeGreaterThan 100
    }

    "no two uses nearer than the cool-down: a chance is held back where the use of the one before would end within it" {
        // every chance taken by its roll (so without a cool-down every slot has its use), and a long cool-down
        val show = Show(seed = 3)
        val roll = { i: Double -> show.own(i, 41.0) }
        val eager = KLAUE.copy(chance = 1.0)
        val free = uses(timing = eager, roll = roll, since = -11.0, until = 2000.0)
        val rested = uses(timing = eager.copy(cooldown = 9.0), roll = roll, since = -11.0, until = 2000.0)

        withClue("uses nearer than the cool-down") {
            rested.zipWithNext { a, b -> b.start - a.endInclusive }.min() shouldBeGreaterThanOrEqual 9.0
        }

        // held back are exactly the chances whose use would begin within the cool-down after the use of the chance
        // before (nine seconds reach back one slot only). That one counts whether it was used or held back itself:
        // so the answer needs no memory. The first use looked at has no chance before it here
        val kept = free.zipWithNext().filter { (a, b) -> b.start - a.endInclusive >= 9.0 }.map { (_, b) -> b }

        withClue("not held back where the use before would end within the cool-down") {
            rested.filter { it.start > free.first().endInclusive } shouldBe kept
        }

        free.zipWithNext { a, b -> b.start - a.endInclusive }.min() shouldBeLessThan 9.0
        rested.size shouldBeLessThan free.size
        rested.size shouldBeGreaterThan 20
    }

    "a cool-down longer than a slot looks back over every slot it reaches" {
        // a third of the chances taken by their roll: with every one taken, each would be held back by the one before
        val show = Show(seed = 9)
        val roll = { i: Double -> show.own(i, 41.0) }
        val eager = KLAUE.copy(chance = 0.33)
        val free = uses(timing = eager, roll = roll, since = -11.0, until = 6000.0)
        val rested = uses(timing = eager.copy(cooldown = 25.0), roll = roll, since = -11.0, until = 6000.0)

        withClue("uses nearer than the long cool-down") {
            rested.zipWithNext { a, b -> b.start - a.endInclusive }.min() shouldBeGreaterThanOrEqual 25.0
        }

        // kept are exactly the chances with no earlier one, however many slots back, whose use would end within the
        // cool-down before theirs begins; the first ones looked at have chances before them that are not in the list
        val known = free.first().endInclusive + 25.0
        val kept = free.filter { use -> free.none { it.start < use.start && use.start - it.endInclusive < 25.0 } }

        withClue("not held back by a use two or more slots before") {
            rested.filter { it.start > known } shouldBe kept.filter { it.start > known }
        }

        rested.size shouldBeGreaterThan 20
        rested.size shouldBeLessThan free.size
        // some are held back from two slots before only: the one right before them was not taken
        free.count { use ->
            free.none { it.start < use.start && use.start - it.endInclusive < 11.0 } &&
                    free.any { it.start < use.start && use.start - it.endInclusive < 25.0 }
        } shouldBeGreaterThan 5
    }

    "a cool-down shorter than any two uses can come together, 0 among them, is as none" {
        // (two uses are at least 0.3 + 0.8 s apart: the end of one slot's latest, and the next slot's earliest);
        // compared with the glance as it was before it was a skill, which had no cool-down
        val show = Show(seed = 5)
        val roll = { i: Double -> show.own(i, 41.0) }
        val times = (0 until 20000).map { it * 0.021 - 20.0 }
        val without = times.map { glanceBefore(show = show, seed = 41.0, t = it) }

        for (cooldown in listOf(0.0, 1.0)) {
            val timing = KLAUE.copy(cooldown = cooldown)

            withClue("with a cool-down of $cooldown s") {
                times.map { timing.inUse(t = it, roll = roll) } shouldBe without
            }
        }

        without.count { it > 0 } shouldBeGreaterThan 1000
    }

    "with Klaue's numbers it gives exactly what the glance at the camera gave before it was a skill, to the bit" {
        val mismatches = mutableListOf<String>()
        var looked = 0
        var full = 0

        for (seed in 0 until 12) {
            val show = Show(seed = seed)

            for (salt in listOf(41.0, 4.0, 67.0)) {
                for (i in 0 until 12000) {
                    val t = i * 0.0371 - 25.0
                    val want = glanceBefore(show = show, seed = salt, t = t)
                    val got = KLAUE.inUse(t = t, roll = { index -> show.own(index, salt) })

                    if (got.toRawBits() != want.toRawBits()) {
                        mismatches.add("seed $seed, salt $salt, t $t: $got, before $want")
                    }

                    if (want > 0) {
                        looked++
                    }

                    if (want == 1.0) {
                        full++
                    }
                }
            }
        }

        withClue("not as the glance before it was a skill") {
            mismatches.take(5) shouldBe emptyList()
        }

        // (and the glance is really in what was compared: looks begun, and held all the way)
        looked shouldBeGreaterThan 10000
        full shouldBeGreaterThan 5000
    }
})
