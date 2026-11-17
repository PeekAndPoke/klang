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
import io.peekandpoke.klang.viz.liveact.band.Player

private val GLANCE = BandGlance(
    SkillTiming(
        every = 14.0, chance = 0.5, least = 1.2, most = 2.2, earliest = 0.5, reserve = 3.5, rise = 0.5, fall = 0.5,
    ),
)

class BandGlanceSpec : StringSpec({

    val show = Show(seed = 0)
    val roll = { i: Double -> show.own(index = i, salt = 67.0) }

    "a glance goes to one of the others, the same one all through a use, and over time to each of them" {
        val seen = mutableMapOf<Player, Int>()
        var uses = 0

        for (slot in -5 until 400) {
            val inUse = (0 until 1400).map { k -> slot * 14.0 + k * 0.01 }
                .filter { t -> usesAt(skills = listOf(GLANCE), t = t, roll = roll).isNotEmpty() }

            if (inUse.isEmpty()) {
                continue
            }

            uses++

            val whom = inUse.map { t -> GLANCE.whom(t = t, roll = roll, who = Player.Sirene) }.toSet()

            withClue("whom in the use of slot $slot") {
                whom.size shouldBe 1
                (Player.Sirene in whom) shouldBe false
            }

            seen[whom.single()] = (seen[whom.single()] ?: 0) + 1
        }

        uses shouldBeGreaterThan 150
        seen.keys shouldBe Player.entries.filter { it != Player.Sirene }.toSet()

        for ((player, times) in seen) {
            withClue("glances at $player") {
                times shouldBeGreaterThan uses / 5 / 2
            }
        }
    }

    "never at oneself, whoever glances" {
        for (who in Player.entries) {
            val whom = (0 until 3000).map { k -> GLANCE.whom(t = k * 1.37, roll = roll, who = who) }.toSet()

            withClue("$who glances at") {
                whom shouldBe Player.entries.filter { it != who }.toSet()
            }
        }
    }
})
