/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.members

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.doubles.shouldBeGreaterThanOrEqual
import io.kotest.matchers.doubles.shouldBeLessThanOrEqual
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.band.skills.ChanceSkill
import io.peekandpoke.klang.viz.liveact.band.skills.CrossedMallets
import io.peekandpoke.klang.viz.liveact.band.skills.IdleSkill
import io.peekandpoke.klang.viz.liveact.band.skills.Mannerism
import io.peekandpoke.klang.viz.liveact.band.skills.TimedSkill
import io.peekandpoke.klang.viz.liveact.band.skills.fits

/** The band's sheets: a check for the future, for whoever adds a member or edits a sheet. */
class MembersSpec : StringSpec({

    "every player has one sheet, filed under who they are, in the players' order" {
        Members.ALL.keys.toList() shouldBe Player.entries

        for (player in Player.entries) {
            Members.of(player).player shouldBe player
            Members.of(player).name shouldBe player.name
        }
    }

    "one member at most has their eyes in their shadow: the show makes one pair of them, for the first who has them" {
        Members.ALL.values.count { it.dress.shadowEyes } shouldBeLessThanOrEqual 1
    }

    "a sheet's name is the member's own, as the band calls them" {
        Members.of(Player.Sirene).name shouldBe "Sirene"
        Members.of(Player.Wucht).name shouldBe "Wucht"
    }

    "a sheet's figure is the file the figure script writes for its player" {
        Members.of(Player.Sirene).figure shouldBe "sirene.glb"
    }

    "the crossed mallets are Rune's only; every skill with a wildness fits its member's; no idle kind on two sheets" {
        // the maintainer, 2026-11-18: the crossed mallets "only Rune will get it for now"; "0 = elegant and 10 = animal
        // ... Depending on that stat we assign the skills"; "keep a good split, so that everyone has a different set"
        fun idles(player: Player): List<IdleSkill> = Members.of(player).skills.filterIsInstance<IdleSkill>()

        Player.entries.filter { p -> Members.of(p).skills.any { it is CrossedMallets } } shouldBe listOf(Player.Rune)

        for (player in Player.entries) {
            val wildness = Members.of(player).character.wildness

            withClue("the skills of $player with a wildness, as wild as $wildness") {
                Members.of(player).skills.filterIsInstance<Mannerism>().filterNot { fits(skill = it, wildness = wildness) } shouldBe
                    emptyList()
            }
        }

        val kinds = Player.entries.flatMap { p -> idles(p).map { it::class }.distinct() }

        kinds.size shouldBe kinds.distinct().size
        // (and there are idle skills on the sheets to look at)
        kinds.size shouldBeGreaterThan 1
    }

    "every member has a gait, the hips moving from 0 to 1.5 as they walk; Sirene's move the most" {
        // the maintainer, 2026-11-20: "when walking we need a setting for each char how much the hip moves"
        for (player in Player.entries) {
            val hips = Members.of(player).character.gait.hips

            withClue("how much the hips of $player move") {
                hips shouldBeGreaterThanOrEqual 0.0
                hips shouldBeLessThanOrEqual 1.5
            }
        }

        // she is the lure
        Player.entries.maxBy { Members.of(it).character.gait.hips } shouldBe Player.Sirene
    }

    "every kind of skill used by chance on the sheets takes its rolls from a place of its own" {
        // two kinds with the same place would take the same roll for the same slot, and go together
        val places = Player.entries
            .flatMap { p -> Members.of(p).skills.filterIsInstance<ChanceSkill>() }
            .associate { it::class to it.rolls }

        places.values.toSet().size shouldBe places.size
        // (and there are several kinds on the sheets to tell apart)
        places.size shouldBeGreaterThan 1
    }

    "on Sirene's sheet every timed look goes back slowly: it falls over 1.5 s at least, no faster than it rises, and still arrives" {
        // the maintainer, 2026-11-19: "One sudden movement is fine, but the way back then needs to be slower". The eye
        // contact (first on her sheet) is no timed look: it goes as the whole band's does (`eyeContact`)
        val looks = Members.of(Player.Sirene).skills.filterIsInstance<TimedSkill>()

        looks.shouldNotBeEmpty()

        for (look in looks) {
            val timing = look.timing

            withClue("${look::class.simpleName}: its fall") {
                timing.fall shouldBeGreaterThanOrEqual 1.5
                timing.fall shouldBeGreaterThanOrEqual timing.rise
            }

            withClue("${look::class.simpleName}: its shortest hold, so the look is all there before it falls") {
                timing.least shouldBeGreaterThanOrEqual timing.fall
            }
        }
    }
})
