/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.skills

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.Show

class SkillRankSpec : StringSpec({

    fun timing(every: Double) = SkillTiming(
        every = every, chance = 1.0, least = 2.0, most = 3.0, earliest = 0.5, reserve = 5.0, rise = 0.5, fall = 0.5,
    )

    val often = CameraGlance(timing(every = 6.0))
    val seldom = CameraGlance(timing(every = 17.0))
    val show = Show(seed = 11)
    val roll = { i: Double -> show.own(i, 23.0) }
    val times = (0 until 6000).map { it * 0.05 }

    "the first skill on the sheet that is in use has the head, and the eye contact is not asked" {
        var both = 0
        var second = 0

        for (t in times) {
            val a = often.timing.inUse(t = t, roll = roll)
            val b = seldom.timing.inUse(t = t, roll = roll)
            val expected = when {
                a > 0 -> SkillInUse(skill = often, share = a)
                b > 0 -> SkillInUse(skill = seldom, share = b)
                else -> null
            }

            withClue("the rank at $t s") {
                firstInUse(skills = listOf(EyeContact, often, seldom), t = t, roll = roll) shouldBe expected
            }

            if (a > 0 && b > 0) {
                both += 1
                // the other way round on the sheet, the other one has it
                firstInUse(skills = listOf(seldom, EyeContact, often), t = t, roll = roll) shouldBe SkillInUse(skill = seldom, share = b)
            }

            if (a <= 0 && b > 0) {
                second += 1
            }
        }

        // both cases were among the times looked at
        both shouldBeGreaterThan 50
        second shouldBeGreaterThan 50
    }

    "a sheet without a timed skill asks for nothing" {
        firstInUse(skills = listOf(EyeContact), t = 3.0, roll = roll) shouldBe null
        firstInUse(skills = emptyList(), t = 3.0, roll = roll) shouldBe null
    }
})
