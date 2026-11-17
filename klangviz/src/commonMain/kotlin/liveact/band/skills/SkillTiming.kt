/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.skills

import io.peekandpoke.klang.viz.liveact.smooth
import kotlin.math.floor

/**
 * When a timed skill is used, and how much: what every timed skill is equipped with (see [inUse]).
 *
 * The show's time is cut into slots of [every] seconds, and each slot is one chance, of which [chance] (0 to 1) are
 * taken. A use begins at the earliest [earliest] seconds into its slot; the starts spread over the slot but for
 * [reserve] seconds of it (the earliest part, and room at the end for the longest use), so every use ends within
 * its slot. From its start to the end of its fall a use lasts its hold plus [rise], with the hold drawn from [least]
 * to [most] seconds: it rises over its first [rise] seconds and falls over its last [fall], eased in and out.
 *
 * After a use the skill rests [cooldown] seconds: a chance whose use would begin sooner after the end of an earlier
 * one is not taken. An earlier chance counts if its own roll took it, whether or not its use was then held back by
 * a cool-down of its own: so the answer needs nothing but the time and the seed, and never looks back further than
 * the cool-down. The price: a chance held back holds back the next one too where that comes within the cool-down,
 * so a cool-down near the length of a slot leaves fewer uses than one after each rest. With a cool-down of 0 no
 * earlier slot is looked at.
 */
data class SkillTiming(
    val every: Double,
    val chance: Double,
    val cooldown: Double = 0.0,
    val least: Double,
    val most: Double,
    val earliest: Double,
    val reserve: Double,
    val rise: Double,
    val fall: Double,
) {
    init {
        require(every > 0 && rise > 0 && fall > 0) { "a slot, a rise and a fall take time: $every s, $rise s, $fall s" }
        require(chance in 0.0..1.0) { "a chance is from 0 to 1: $chance" }
        require(least >= 0 && most >= least) { "a use holds from its least to its most: $least s to $most s" }
        require(earliest >= 0 && cooldown >= 0) { "a skill's earliest start and its cool-down are never negative" }
        require(cooldown.isFinite()) { "a cool-down ends" }
        require(reserve <= every) { "the reserve ($reserve s) is more than a slot ($every s)" }
        require(reserve >= earliest + most + rise) {
            "a use of up to ${most + rise} s, begun from $earliest s on, does not fit in the reserve ($reserve s)"
        }
    }

    /**
     * The slot [t] (seconds into the show) lies in, a whole number: each slot has one chance, and a use ends within
     * its slot, so the slot names the use (what a use chooses, it chooses by its slot).
     */
    fun slotOf(t: Double): Double = floor(t / every)

    /** One use: when it [start]s and how long it [hold]s (see [SkillTiming]). */
    private class Use(val start: Double, val hold: Double)

    /**
     * The use of [slot] (a whole number), where its roll takes the chance; null where it does not. [roll]: the
     * show's chance for an index.
     */
    private fun useOf(slot: Double, roll: (Double) -> Double): Use? {
        fun roll(n: Int): Double = roll(slot + n)

        if (roll(0) > chance) {
            return null
        }

        val start = slot * every + earliest + roll(1) * (every - reserve)
        val hold = least + (most - least) * roll(2)

        return Use(start = start, hold = hold)
    }

    /**
     * How much the skill is in use at [t] (seconds into the show), 0 to 1: 0 outside a use, eased up to 1 and down
     * again within one. [roll] gives a number from 0 to 1 for an index, always the same one for the same index: the
     * show's seeded chance under the member's own salt (`Show.own`). It goes by [t] and [roll] alone, so any moment
     * of the show gives the same answer whenever it is asked for.
     */
    fun inUse(t: Double, roll: (Double) -> Double): Double {
        val slot = slotOf(t)
        val use = useOf(slot = slot, roll = roll) ?: return 0.0
        var earlier = slot - 1

        // an earlier use that ends within the cool-down before this one begins holds it back (a use ends within
        // its slot, so a slot that ended a cool-down before this start holds nothing back)
        while ((earlier + 1) * every + cooldown > use.start) {
            val before = useOf(slot = earlier, roll = roll)

            if (before != null && use.start - (before.start + before.hold + rise) < cooldown) {
                return 0.0
            }

            earlier -= 1
        }

        val x = t - use.start

        return smooth(x / rise) * smooth((use.hold + rise - x) / fall)
    }
}
