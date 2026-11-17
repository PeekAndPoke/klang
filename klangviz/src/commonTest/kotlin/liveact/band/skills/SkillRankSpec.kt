/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.skills

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.Show

class SkillRankSpec : StringSpec({

    fun timing(every: Double) = SkillTiming(
        every = every, chance = 1.0, least = 2.0, most = 3.0, earliest = 0.5, reserve = 5.0, rise = 0.5, fall = 0.5,
    )

    val often = CrowdGaze(timing = timing(every = 6.0), spotLeast = 1.5, spotMost = 2.5, move = 0.8)
    val seldom = CameraStare(timing(every = 17.0))
    val show = Show(seed = 11)
    val roll = { i: Double -> show.own(i, 23.0) }
    val times = (0 until 6000).map { it * 0.05 }

    "every skill in use, in the sheet's order (the rank), and the eye contact is not asked" {
        var both = 0
        var second = 0

        for (t in times) {
            val a = often.timing.inUse(t = t, roll = { i -> roll(i + 3000.0) })
            val b = seldom.timing.inUse(t = t, roll = { i -> roll(i + 1000.0) })
            val expected = listOfNotNull(
                SkillInUse(skill = often, share = a).takeIf { a > 0 },
                SkillInUse(skill = seldom, share = b).takeIf { b > 0 },
            )

            withClue("the skills in use at $t s") {
                usesAt(skills = listOf(EyeContact, often, seldom), t = t, roll = roll) shouldBe expected
            }

            if (a > 0 && b > 0) {
                both += 1
                // the other way round on the sheet, the other one comes first
                usesAt(skills = listOf(seldom, EyeContact, often), t = t, roll = roll) shouldBe expected.reversed()
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
        usesAt(skills = listOf(EyeContact), t = 3.0, roll = roll) shouldBe emptyList()
        usesAt(skills = emptyList(), t = 3.0, roll = roll) shouldBe emptyList()
    }

    "each kind takes its own rolls: the glance at the camera the member's own, unmoved; no two kinds the same" {
        val every = timing(every = 11.0).copy(chance = 0.5)
        val kinds: List<TimedSkill> = listOf(
            CameraGlance(every),
            CameraStare(every),
            BandGlance(every),
            CrowdGaze(timing = every, spotLeast = 1.5, spotMost = 2.5, move = 0.8),
        )
        val long = (0 until 30000).map { it * 0.1 }

        // the glance at the camera rolls as before it was a skill: the member's chance itself
        withClue("the glance at the camera") {
            long.map { t -> usesAt(skills = listOf(kinds[0]), t = t, roll = roll).firstOrNull()?.share ?: 0.0 } shouldBe
                long.map { t -> every.inUse(t = t, roll = roll) }
        }

        // with the same timing, any two kinds are in use at other moments: their chances are not the same ones (with
        // the same ones they would be in use at the same moments, all of them)
        val inUse = kinds.map { kind -> long.map { t -> usesAt(skills = listOf(kind), t = t, roll = roll).isNotEmpty() } }

        for (i in kinds.indices) {
            for (j in i + 1 until kinds.size) {
                val apart = long.indices.count { k -> inUse[i][k] != inUse[j][k] }

                withClue("${kinds[i]::class.simpleName} and ${kinds[j]::class.simpleName} in use at other moments") {
                    apart shouldBeGreaterThan 3000
                }
            }
        }

        // (and each is in use a fair part of the time, not never)
        for (each in inUse) {
            each.count { it } shouldBeGreaterThan 2000
            each.count { it } shouldBeLessThan 15000
        }
    }
})
