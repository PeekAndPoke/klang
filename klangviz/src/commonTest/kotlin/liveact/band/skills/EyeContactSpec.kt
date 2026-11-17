/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.skills

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.band.Player

class EyeContactSpec : StringSpec({

    "each of the two turns only with the skill: one without it does not answer, the other still turns" {
        val glance = CameraGlance(
            SkillTiming(
                every = 11.0, chance = 0.5, least = 2.2, most = 4.2, earliest = 0.8, reserve = 6.0, rise = 0.7, fall = 0.9,
            ),
        )
        val sheets = mapOf(
            Player.Wucht to listOf(EyeContact),
            Player.Klaue to listOf(EyeContact, glance),
            Player.Rune to emptyList(),
            Player.Sirene to listOf(glance),
        )

        fun skills(player: Player): List<Skill> = sheets.getValue(player)

        withClue("who turns in an eye contact") {
            turning(first = Player.Klaue, second = Player.Wucht, skills = ::skills) shouldBe
                listOf(Player.Klaue, Player.Wucht)
            turning(first = Player.Wucht, second = Player.Klaue, skills = ::skills) shouldBe
                listOf(Player.Wucht, Player.Klaue)
            turning(first = Player.Wucht, second = Player.Rune, skills = ::skills) shouldBe listOf(Player.Wucht)
            turning(first = Player.Rune, second = Player.Klaue, skills = ::skills) shouldBe listOf(Player.Klaue)
            turning(first = Player.Sirene, second = Player.Rune, skills = ::skills) shouldBe emptyList()
        }
    }
})
