/*
 * Copyright (C) 2025-2026 The Klangmotor Authors (see AUTHORS.MD)
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */

package io.peekandpoke.klang.viz.liveact.band.members

import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.shouldBe
import io.peekandpoke.klang.viz.liveact.band.Player
import io.peekandpoke.klang.viz.liveact.band.skills.CrossedMallets
import io.peekandpoke.klang.viz.liveact.band.skills.IdleSkill
import io.peekandpoke.klang.viz.liveact.band.skills.Mannerism
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
})
