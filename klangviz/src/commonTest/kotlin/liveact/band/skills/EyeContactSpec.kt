/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.skills

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.doubles.plusOrMinus
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.triggers.ChanceInSlots
import kotlin.math.floor

/** The eye contact's timing as Der Schmetterling's score has it. */
private val TIMING = ChanceInSlots(
    every = 11.0,
    chance = 0.4,
    cooldown = 0.0,
    least = 2.85,
    most = 3.75,
    earliest = 0.2,
    reserve = 9.8,
    rise = 4.3,
    fall = 2.15,
)

/**
 * The band's chance for a test: [timing] for the timing's own draws (the whole numbers), [who] for which of the two
 * starts and [late] for how late the other answers (the draws between them).
 */
private fun rolls(timing: Double = 0.0, who: Double = 0.0, late: Double = 0.0): (Double) -> Double = { index ->
    when (index - floor(index)) {
        0.5 -> who
        0.75 -> late
        0.0 -> timing
        else -> 0.0
    }
}

class EyeContactSpec : StringSpec({

    val pairs = listOf(Player.Beben to Player.Wucht, Player.Sirene to Player.Rune)

    fun contact(
        t: Double,
        roll: (Double) -> Double,
        herNotes: List<Double> = emptyList(),
        with: List<Pair<Player, Player>> = pairs,
    ): ContactInUse? = contactAt(t = t, timing = TIMING, roll = roll, pairs = with, her = Player.Sirene, herNotes = herNotes)

    "a use: the first glances, the other answers late, they share the look with a nod, and both are back by its end" {
        // every draw 0: the slot from 0 s, its use from 0.2 s to 7.35 s; both look from 4.5 s to 5.2 s; the other
        // answers one second after the glance, which so comes at 2.2 s
        val roll = rolls()

        withClue("before the glance") {
            contact(t = 2.0, roll = roll).shouldNotBeNull().firstTurn shouldBe 0.0
        }

        withClue("as their eyes meet") {
            val meet = contact(t = 4.75, roll = roll).shouldNotBeNull()

            meet.first shouldBe Player.Beben
            meet.second shouldBe Player.Wucht
            meet.firstTurn shouldBe (0.62 plusOrMinus 1e-9)
            meet.secondTurn shouldBe (0.58 plusOrMinus 1e-9)
            meet.firstNod shouldBe 1.0
        }

        withClue("back by the end of the use, and none after it") {
            val end = contact(t = 7.34, roll = roll).shouldNotBeNull()

            end.firstTurn shouldBe 0.0
            end.secondTurn shouldBe 0.0
            contact(t = 7.4, roll = roll).shouldBeNull()
        }
    }

    "which of the two starts and how late the other answers go by the slot's own draws" {
        val turned = contact(t = 4.75, roll = rolls(who = 0.9, late = 1.0)).shouldNotBeNull()

        turned.first shouldBe Player.Wucht
        turned.second shouldBe Player.Beben

        // three seconds late: the glance comes as the use begins
        contact(t = 0.2, roll = rolls(late = 1.0)).shouldNotBeNull().firstTurn shouldBe 0.0
        contact(t = 0.8, roll = rolls(late = 1.0)).shouldNotBeNull().firstTurn shouldBe (0.5 * 0.5 plusOrMinus 1e-9)
    }

    "none where the slot's chance is not taken, and none on the walk-in" {
        (0..1100).map { contact(t = it / 100.0, roll = rolls(timing = 0.9)) }.filterNotNull() shouldBe emptyList()

        // the slot before the song is taken, its use from -10.8 s: not seen
        TIMING.spanAt(t = -6.0, roll = rolls()).shouldNotBeNull()
        contact(t = -6.0, roll = rolls()).shouldBeNull()
    }

    "not with her who plays the trumpet where her notes come within the use or three seconds either side of it" {
        val hers = listOf(Player.Sirene to Player.Rune)

        contact(t = 4.75, roll = rolls(), herNotes = listOf(10.3), with = hers).shouldBeNull()
        contact(t = 4.75, roll = rolls(), herNotes = listOf(-2.7), with = hers).shouldBeNull()
        contact(t = 4.75, roll = rolls(), herNotes = listOf(-2.9, 10.4), with = hers).shouldNotBeNull()

        withClue("a pair without her goes on") {
            contact(t = 4.75, roll = rolls(), herNotes = listOf(5.0)).shouldNotBeNull()
        }
    }

    "each of the two turns only with the skill: one without it does not answer, the other still turns" {
        val glance = CameraGlance(
            ChanceInSlots(
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
