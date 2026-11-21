/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.skills

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.shouldBeGreaterThan
import io.kotest.matchers.doubles.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.Show
import io.peekandpoke.klang.viz.liveact.triggers.ChanceInSlots
import kotlin.math.abs

/** Straightening up as the string players' sheets equip it. */
private val STRAIGHTEN = Straighten(
    ChanceInSlots(
        every = 21.0,
        chance = 0.55,
        cooldown = 0.0,
        least = 6.6,
        most = 11.6,
        earliest = 0.5,
        reserve = 13.9,
        rise = 1.8,
        fall = 2.6,
    ),
)

class StraightenSpec : StringSpec({

    "how straight a member stands is what the timing gives, with the skill's own rolls" {
        val show = Show(seed = 3)
        val roll = { index: Double -> show.own(index, 23.0) }
        val own = { index: Double -> roll(index + 15000.0) }

        (0..6000).map { STRAIGHTEN.at(t = it / 10.0, roll = roll) } shouldBe
            (0..6000).map { STRAIGHTEN.timing.inUse(t = it / 10.0, roll = own) }
        (0..6000).maxOf { STRAIGHTEN.at(t = it / 10.0, roll = roll) } shouldBeGreaterThan 0.99
    }

    "before the song the member stands as always, and nothing is cut off where it begins" {
        // every chance taken: a use in every slot, also in the one before the song
        val always = { _: Double -> 0.0 }

        STRAIGHTEN.timing.inUse(t = -15.0, roll = always) shouldBeGreaterThan 0.5
        (-300..0).maxOf { STRAIGHTEN.at(t = it / 10.0, roll = always) } shouldBe 0.0

        val steps = (0 until 300).map {
            abs(STRAIGHTEN.at(t = (it + 1) / 100.0, roll = always) - STRAIGHTEN.at(t = it / 100.0, roll = always))
        }

        steps.max() shouldBeLessThan 0.01
    }
})
